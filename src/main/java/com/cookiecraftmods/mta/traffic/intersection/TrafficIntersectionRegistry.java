package com.cookiecraftmods.mta.traffic.intersection;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraph;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrNodeKey;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRouteSegment;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TrafficIntersectionRegistry {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<TrafficIntersectionDefinition>>() { }.getType();
	private static final Map<String, TrafficIntersectionDefinition> DEFINITIONS = new LinkedHashMap<>();
	private static final double STOP_LOOKAHEAD_METERS = 48.0D;
	private static final double STOP_BUFFER_METERS = 5.0D;
	private static boolean initialized;
	private static MinecraftServer currentServer;

	private TrafficIntersectionRegistry() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			currentServer = server;
			load(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			save(server);
			currentServer = null;
			DEFINITIONS.clear();
		});
		initialized = true;
	}

	public static Collection<TrafficIntersectionDefinition> getDefinitions() {
		return List.copyOf(DEFINITIONS.values());
	}

	public static TrafficIntersectionDefinition createIntersection(ServerLevel level, BlockPos firstCorner, BlockPos secondCorner) {
		final String dimensionId = level.dimension().location().toString();
		final long minX = Math.min(firstCorner.getX(), secondCorner.getX());
		final long minY = Math.min(firstCorner.getY(), secondCorner.getY());
		final long minZ = Math.min(firstCorner.getZ(), secondCorner.getZ());
		final long maxX = Math.max(firstCorner.getX(), secondCorner.getX());
		final long maxY = Math.max(firstCorner.getY(), secondCorner.getY());
		final long maxZ = Math.max(firstCorner.getZ(), secondCorner.getZ());
		final String id = dimensionId + "|intersection|" + firstCorner.asLong() + "|" + secondCorner.asLong();
		final TrafficIntersectionDefinition definition = new TrafficIntersectionDefinition(id, null, dimensionId, minX, minY, minZ, maxX, maxY, maxZ, true, true, 100, List.of(), List.of(), List.of());
		DEFINITIONS.put(id, definition);
		save(level.getServer());
		return definition;
	}

	public static boolean applyDashboardUpdate(String intersectionId, String action, int delta, String value) {
		if ("delete".equals(action)) {
			final boolean removed = DEFINITIONS.remove(intersectionId) != null;
			if (removed && currentServer != null) {
				save(currentServer);
			}
			return removed;
		}

		final TrafficIntersectionDefinition definition = DEFINITIONS.get(intersectionId);
		if (definition == null) {
			return false;
		}

		final TrafficIntersectionDefinition updated = switch (action) {
			case "name" -> copyWithName(definition, value);
			case "enabled" -> copy(definition, !definition.isEnabled(), definition.autoDetectNodes(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), definition.nodes());
			case "auto_detect" -> copy(definition, definition.enabled(), !definition.effectiveAutoDetectNodes(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), definition.nodes());
			case "phase_duration" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), clamp(definition.effectivePhaseDurationTicks() + delta, 20, 2400), definition.phaseOrder(), updateGroupDuration(dashboardGroups(definition), value, delta), definition.nodes());
			case "node_type" -> copy(definition, definition.enabled(), false, definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), updateNearestNode(definition, value, delta, NodeUpdateMode.TYPE));
			case "node_number" -> copy(definition, definition.enabled(), false, definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), updateNearestNode(definition, value, delta, NodeUpdateMode.NUMBER));
			case "phase_toggle" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.phaseDurationTicks(), togglePhase(definition.phaseOrder(), delta), definition.groups(), definition.nodes());
			case "phase_add" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.phaseDurationTicks(), addPhase(definition.phaseOrder(), delta), addNodeToGroup(dashboardGroups(definition), value, delta), definition.nodes());
			case "phase_assign" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.phaseDurationTicks(), addPhase(definition.phaseOrder(), delta), assignNodeToGroup(dashboardGroups(definition), value, delta), definition.nodes());
			case "phase_remove" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.phaseDurationTicks(), removePhase(definition.phaseOrder(), delta), removeGroupOrNode(dashboardGroups(definition), value, delta), definition.nodes());
			case "phase_move" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.phaseDurationTicks(), movePhase(definition.phaseOrder(), value, delta), moveGroup(dashboardGroups(definition), value, delta), definition.nodes());
			case "group_add" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.phaseDurationTicks(), definition.phaseOrder(), addGroup(dashboardGroups(definition)), definition.nodes());
			default -> definition;
		};
		DEFINITIONS.put(intersectionId, updated);
		if (currentServer != null) {
			save(currentServer);
		}
		return true;
	}

	public static void refreshNodes(String dimensionId, MtrGraph graph) {
		if (graph == null || graph.isEmpty()) {
			return;
		}

		boolean changed = false;
		for (TrafficIntersectionDefinition definition : List.copyOf(DEFINITIONS.values())) {
			if (!definition.dimensionId().equals(dimensionId) || !definition.effectiveAutoDetectNodes()) {
				continue;
			}

			final List<TrafficIntersectionNode> detectedNodes = detectBoundaryNodes(definition, graph);
			if (!detectedNodes.equals(definition.nodes())) {
				DEFINITIONS.put(definition.id(), copyWithNodes(definition, detectedNodes));
				changed = true;
			}
		}

		if (changed && currentServer != null) {
			save(currentServer);
		}
	}

	public static void applySignalSpeedLimits(Collection<TrafficVehicle> vehicles, Map<TrafficVehicle, Double> allowedSpeeds, long serverTick) {
		if (DEFINITIONS.isEmpty()) {
			return;
		}

		for (TrafficVehicle vehicle : vehicles) {
			for (TrafficIntersectionDefinition definition : DEFINITIONS.values()) {
				if (!definition.isEnabled() || definition.nodes().isEmpty()) {
					continue;
				}

				final Optional<Double> distanceToRedEntry = distanceToRedEntry(definition, vehicle, serverTick);
				if (distanceToRedEntry.isEmpty()) {
					continue;
				}

				final double distanceToStop = distanceToRedEntry.get() - STOP_BUFFER_METERS;
				if (distanceToStop <= 0.0D) {
					allowedSpeeds.put(vehicle, 0.0D);
				} else if (distanceToStop <= STOP_LOOKAHEAD_METERS) {
					final double brakingMetersPerSecondSquared = vehicle.definition().effectiveBrakingMetersPerSecondSquared();
					final double maxSpeedKph = Math.sqrt(2.0D * brakingMetersPerSecondSquared * distanceToStop) * 3.6D;
					allowedSpeeds.put(vehicle, Math.min(allowedSpeeds.getOrDefault(vehicle, 0.0D), maxSpeedKph));
				}
			}
		}
	}

	private static Optional<Double> distanceToRedEntry(TrafficIntersectionDefinition definition, TrafficVehicle vehicle, long serverTick) {
		final List<TrafficRouteSegment> segments = vehicle.route().segments();
		if (segments.isEmpty() || vehicle.segmentIndex() < 0 || vehicle.segmentIndex() >= segments.size()) {
			return Optional.empty();
		}

		double distanceFromVehicle = -vehicle.distanceOnSegmentMeters();
		for (int i = vehicle.segmentIndex(); i < segments.size(); i++) {
			final TrafficRouteSegment segment = segments.get(i);
			distanceFromVehicle += segment.lengthMeters();
			if (distanceFromVehicle > STOP_LOOKAHEAD_METERS + STOP_BUFFER_METERS) {
				return Optional.empty();
			}
			if (!isEntering(definition, segment)) {
				continue;
			}

			final Optional<Integer> inNumber = inNumberForEntry(definition, segment);
			if (inNumber.isPresent() && !activeInNumbers(definition, serverTick).contains(inNumber.get())) {
				return Optional.of(Math.max(0.0D, distanceFromVehicle));
			}
		}
		return Optional.empty();
	}

	private static List<TrafficIntersectionNode> detectBoundaryNodes(TrafficIntersectionDefinition definition, MtrGraph graph) {
		final Map<String, TrafficIntersectionNode> nodesByKey = new LinkedHashMap<>();
		for (MtrGraphEdge edge : graph.edges()) {
			final boolean fromInside = contains(definition, edge.from());
			final boolean toInside = contains(definition, edge.to());
			if (!fromInside && toInside) {
				putNode(nodesByKey, edge.to(), TrafficIntersectionNodeType.IN);
			} else if (fromInside && !toInside) {
				putNode(nodesByKey, edge.from(), TrafficIntersectionNodeType.OUT);
			}
		}

		final List<TrafficIntersectionNode> inNodes = nodesByKey.values().stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
			.sorted(nodeComparator())
			.toList();
		final List<TrafficIntersectionNode> outNodes = nodesByKey.values().stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.OUT)
			.sorted(nodeComparator())
			.toList();
		final List<TrafficIntersectionNode> numberedNodes = new ArrayList<>(inNodes.size() + outNodes.size());
		for (int i = 0; i < inNodes.size(); i++) {
			final TrafficIntersectionNode node = inNodes.get(i);
			numberedNodes.add(new TrafficIntersectionNode(node.x(), node.y(), node.z(), node.type(), i + 1));
		}
		for (int i = 0; i < outNodes.size(); i++) {
			final TrafficIntersectionNode node = outNodes.get(i);
			numberedNodes.add(new TrafficIntersectionNode(node.x(), node.y(), node.z(), node.type(), i + 1));
		}
		return numberedNodes;
	}

	private static void putNode(Map<String, TrafficIntersectionNode> nodesByKey, MtrNodeKey node, TrafficIntersectionNodeType type) {
		nodesByKey.put(type + "|" + node.x() + "|" + node.y() + "|" + node.z(), new TrafficIntersectionNode(node.x(), node.y(), node.z(), type, 0));
	}

	private static Optional<Integer> inNumberForEntry(TrafficIntersectionDefinition definition, TrafficRouteSegment segment) {
		return definition.nodes().stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
			.filter(node -> node.x() == segment.endX() && node.y() == segment.endY() && node.z() == segment.endZ())
			.map(TrafficIntersectionNode::number)
			.findFirst();
	}

	private static List<Integer> activeInNumbers(TrafficIntersectionDefinition definition, long serverTick) {
		final List<Integer> inNumbers = definition.nodes().stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
			.map(TrafficIntersectionNode::number)
			.distinct()
			.sorted()
			.toList();
		final List<TrafficIntersectionGroup> groups = effectiveGroups(definition, inNumbers);
		if (!groups.isEmpty()) {
			final List<TrafficIntersectionGroup> validGroups = groups.stream()
				.map(group -> new TrafficIntersectionGroup(group.name(), group.effectiveGreenDurationTicks(), group.nodeNumbers().stream().filter(inNumbers::contains).distinct().toList()))
				.filter(group -> !group.nodeNumbers().isEmpty())
				.toList();
			if (validGroups.isEmpty()) {
				return inNumbers;
			}

			long cycleTicks = 0;
			for (TrafficIntersectionGroup group : validGroups) {
				cycleTicks += group.effectiveGreenDurationTicks();
			}
			long tickInCycle = cycleTicks <= 0 ? 0 : serverTick % cycleTicks;
			for (TrafficIntersectionGroup group : validGroups) {
				tickInCycle -= group.effectiveGreenDurationTicks();
				if (tickInCycle < 0) {
					return group.nodeNumbers();
				}
			}
		}
		final List<Integer> phaseOrder = definition.phaseOrder().isEmpty() ? inNumbers : definition.phaseOrder().stream().filter(inNumbers::contains).toList();
		if (phaseOrder.isEmpty()) {
			return List.of();
		}

		final int phase = (int) ((serverTick / definition.effectivePhaseDurationTicks()) % phaseOrder.size());
		return List.of(phaseOrder.get(phase));
	}

	private static List<TrafficIntersectionGroup> effectiveGroups(TrafficIntersectionDefinition definition, List<Integer> inNumbers) {
		if (!definition.groups().isEmpty()) {
			return definition.groups();
		}
		if (definition.phaseOrder().isEmpty()) {
			return List.of();
		}
		return definition.phaseOrder().stream()
			.filter(inNumbers::contains)
			.map(number -> new TrafficIntersectionGroup("Group " + number, definition.effectivePhaseDurationTicks(), List.of(number)))
			.toList();
	}

	private static boolean isEntering(TrafficIntersectionDefinition definition, TrafficRouteSegment segment) {
		return !definition.contains(segment.startX(), segment.startY(), segment.startZ()) && definition.contains(segment.endX(), segment.endY(), segment.endZ());
	}

	private static boolean isInside(TrafficIntersectionDefinition definition, TrafficRouteSegment segment) {
		return definition.contains(segment.startX(), segment.startY(), segment.startZ()) && definition.contains(segment.endX(), segment.endY(), segment.endZ());
	}

	private static boolean contains(TrafficIntersectionDefinition definition, MtrNodeKey node) {
		return definition.contains(node.x(), node.y(), node.z());
	}

	private static Comparator<TrafficIntersectionNode> nodeComparator() {
		return Comparator.comparingLong(TrafficIntersectionNode::x).thenComparingLong(TrafficIntersectionNode::z).thenComparingLong(TrafficIntersectionNode::y);
	}

	private static TrafficIntersectionDefinition copyWithNodes(TrafficIntersectionDefinition definition, List<TrafficIntersectionNode> nodes) {
		return new TrafficIntersectionDefinition(definition.id(), definition.name(), definition.dimensionId(), definition.minX(), definition.minY(), definition.minZ(), definition.maxX(), definition.maxY(), definition.maxZ(), definition.enabled(), definition.autoDetectNodes(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), nodes);
	}

	private static TrafficIntersectionDefinition copy(TrafficIntersectionDefinition definition, Boolean enabled, Boolean autoDetectNodes, Integer phaseDurationTicks, List<Integer> phaseOrder, List<TrafficIntersectionGroup> groups, List<TrafficIntersectionNode> nodes) {
		return new TrafficIntersectionDefinition(definition.id(), definition.name(), definition.dimensionId(), definition.minX(), definition.minY(), definition.minZ(), definition.maxX(), definition.maxY(), definition.maxZ(), enabled, autoDetectNodes, phaseDurationTicks, phaseOrder, groups, nodes);
	}

	private static TrafficIntersectionDefinition copyWithName(TrafficIntersectionDefinition definition, String name) {
		final String trimmedName = name == null ? null : name.trim();
		return new TrafficIntersectionDefinition(definition.id(), trimmedName == null || trimmedName.isBlank() ? null : trimmedName, definition.dimensionId(), definition.minX(), definition.minY(), definition.minZ(), definition.maxX(), definition.maxY(), definition.maxZ(), definition.enabled(), definition.autoDetectNodes(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), definition.nodes());
	}

	private static List<TrafficIntersectionNode> updateNearestNode(TrafficIntersectionDefinition definition, String encodedNode, int delta, NodeUpdateMode mode) {
		final MtrNodeKey targetNode = decodeNode(encodedNode);
		if (targetNode == null) {
			return definition.nodes();
		}

		final List<TrafficIntersectionNode> updatedNodes = new ArrayList<>(definition.nodes().size());
		for (TrafficIntersectionNode node : definition.nodes()) {
			if (node.x() == targetNode.x() && node.y() == targetNode.y() && node.z() == targetNode.z()) {
				if (mode == NodeUpdateMode.TYPE) {
					final TrafficIntersectionNodeType type = node.type() == TrafficIntersectionNodeType.IN ? TrafficIntersectionNodeType.OUT : TrafficIntersectionNodeType.IN;
					updatedNodes.add(new TrafficIntersectionNode(node.x(), node.y(), node.z(), type, node.number()));
				} else {
					updatedNodes.add(new TrafficIntersectionNode(node.x(), node.y(), node.z(), node.type(), Math.max(1, node.number() + delta)));
				}
			} else {
				updatedNodes.add(node);
			}
		}
		return updatedNodes;
	}

	private static List<Integer> togglePhase(List<Integer> phaseOrder, int number) {
		final List<Integer> updatedPhaseOrder = new ArrayList<>(phaseOrder);
		if (updatedPhaseOrder.contains(number)) {
			updatedPhaseOrder.remove(Integer.valueOf(number));
		} else {
			updatedPhaseOrder.add(number);
		}
		return updatedPhaseOrder;
	}

	private static List<Integer> addPhase(List<Integer> phaseOrder, int number) {
		final List<Integer> updatedPhaseOrder = new ArrayList<>(phaseOrder);
		updatedPhaseOrder.add(Math.max(1, number));
		return updatedPhaseOrder;
	}

	private static List<Integer> removePhase(List<Integer> phaseOrder, int number) {
		final List<Integer> updatedPhaseOrder = new ArrayList<>(phaseOrder);
		updatedPhaseOrder.remove(Integer.valueOf(number));
		return updatedPhaseOrder;
	}

	private static List<Integer> movePhase(List<Integer> phaseOrder, String rawIndex, int delta) {
		final List<Integer> updatedPhaseOrder = new ArrayList<>(phaseOrder);
		if (updatedPhaseOrder.size() < 2 || rawIndex == null || rawIndex.isBlank()) {
			return updatedPhaseOrder;
		}

		try {
			final int index = Integer.parseInt(rawIndex);
			final int newIndex = clamp(index + delta, 0, updatedPhaseOrder.size() - 1);
			if (index < 0 || index >= updatedPhaseOrder.size() || index == newIndex) {
				return updatedPhaseOrder;
			}
			final Integer phase = updatedPhaseOrder.remove(index);
			updatedPhaseOrder.add(newIndex, phase);
		} catch (NumberFormatException ignored) {
		}
		return updatedPhaseOrder;
	}

	private static List<TrafficIntersectionGroup> addGroup(List<TrafficIntersectionGroup> groups) {
		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups);
		updatedGroups.add(new TrafficIntersectionGroup("Group " + (updatedGroups.size() + 1), 100, List.of()));
		return updatedGroups;
	}

	private static List<TrafficIntersectionGroup> dashboardGroups(TrafficIntersectionDefinition definition) {
		if (!definition.groups().isEmpty()) {
			return definition.groups();
		}

		final List<Integer> inNumbers = definition.nodes().stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
			.map(TrafficIntersectionNode::number)
			.distinct()
			.sorted()
			.toList();
		final List<Integer> phaseOrder = definition.phaseOrder().isEmpty() ? inNumbers : definition.phaseOrder().stream().filter(inNumbers::contains).toList();
		return phaseOrder.stream()
			.map(number -> new TrafficIntersectionGroup("Group " + number, definition.effectivePhaseDurationTicks(), List.of(number)))
			.toList();
	}

	private static List<TrafficIntersectionGroup> addNodeToGroup(List<TrafficIntersectionGroup> groups, String rawIndex, int nodeNumber) {
		final int groupIndex = parseIndex(rawIndex);
		if (groupIndex < 0 || groupIndex >= groups.size() || nodeNumber <= 0) {
			return groups;
		}

		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups.size());
		for (int i = 0; i < groups.size(); i++) {
			final TrafficIntersectionGroup group = groups.get(i);
			if (i == groupIndex) {
				final List<Integer> nodeNumbers = new ArrayList<>(group.nodeNumbers());
				if (!nodeNumbers.contains(nodeNumber)) {
					nodeNumbers.add(nodeNumber);
				}
				updatedGroups.add(new TrafficIntersectionGroup(group.name(), group.effectiveGreenDurationTicks(), nodeNumbers));
			} else {
				updatedGroups.add(group);
			}
		}
		return updatedGroups;
	}

	private static List<TrafficIntersectionGroup> assignNodeToGroup(List<TrafficIntersectionGroup> groups, String rawIndex, int nodeNumber) {
		final int groupIndex = parseIndex(rawIndex);
		if (groupIndex < 0 || groupIndex >= groups.size() || nodeNumber <= 0) {
			return groups;
		}

		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups.size());
		for (int i = 0; i < groups.size(); i++) {
			final TrafficIntersectionGroup group = groups.get(i);
			final List<Integer> nodeNumbers = new ArrayList<>(group.nodeNumbers());
			nodeNumbers.remove(Integer.valueOf(nodeNumber));
			if (i == groupIndex && !nodeNumbers.contains(nodeNumber)) {
				nodeNumbers.add(nodeNumber);
			}
			updatedGroups.add(new TrafficIntersectionGroup(group.name(), group.effectiveGreenDurationTicks(), nodeNumbers));
		}
		return updatedGroups;
	}

	private static List<TrafficIntersectionGroup> removeGroupOrNode(List<TrafficIntersectionGroup> groups, String rawIndex, int nodeNumber) {
		final int groupIndex = parseIndex(rawIndex);
		if (groupIndex < 0 || groupIndex >= groups.size()) {
			return groups;
		}

		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups);
		if (nodeNumber <= 0) {
			updatedGroups.remove(groupIndex);
			return updatedGroups;
		}

		final TrafficIntersectionGroup group = updatedGroups.get(groupIndex);
		final List<Integer> nodeNumbers = new ArrayList<>(group.nodeNumbers());
		nodeNumbers.remove(Integer.valueOf(nodeNumber));
		updatedGroups.set(groupIndex, new TrafficIntersectionGroup(group.name(), group.effectiveGreenDurationTicks(), nodeNumbers));
		return updatedGroups;
	}

	private static List<TrafficIntersectionGroup> moveGroup(List<TrafficIntersectionGroup> groups, String rawIndex, int delta) {
		final int index = parseIndex(rawIndex);
		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups);
		if (updatedGroups.size() < 2 || index < 0 || index >= updatedGroups.size()) {
			return updatedGroups;
		}

		final int newIndex = clamp(index + delta, 0, updatedGroups.size() - 1);
		if (newIndex != index) {
			final TrafficIntersectionGroup group = updatedGroups.remove(index);
			updatedGroups.add(newIndex, group);
		}
		return updatedGroups;
	}

	private static List<TrafficIntersectionGroup> updateGroupDuration(List<TrafficIntersectionGroup> groups, String rawIndex, int delta) {
		final int groupIndex = parseIndex(rawIndex);
		if (groupIndex < 0 || groupIndex >= groups.size()) {
			return groups;
		}

		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups);
		final TrafficIntersectionGroup group = updatedGroups.get(groupIndex);
		updatedGroups.set(groupIndex, new TrafficIntersectionGroup(group.name(), clamp(group.effectiveGreenDurationTicks() + delta, 20, 2400), group.nodeNumbers()));
		return updatedGroups;
	}

	private static int parseIndex(String rawIndex) {
		if (rawIndex == null || rawIndex.isBlank()) {
			return -1;
		}
		try {
			return Integer.parseInt(rawIndex);
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	private static MtrNodeKey decodeNode(String encodedNode) {
		if (encodedNode == null || encodedNode.isBlank()) {
			return null;
		}

		final String[] parts = encodedNode.split(",");
		if (parts.length != 3) {
			return null;
		}

		try {
			return new MtrNodeKey(Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private enum NodeUpdateMode {
		TYPE,
		NUMBER
	}

	private static void load(MinecraftServer server) {
		DEFINITIONS.clear();
		final Path path = savePath(server);
		if (!Files.exists(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			final List<TrafficIntersectionDefinition> definitions = GSON.fromJson(reader, LIST_TYPE);
			if (definitions != null) {
				for (TrafficIntersectionDefinition definition : definitions) {
					DEFINITIONS.put(definition.id(), definition);
				}
			}
		} catch (Exception e) {
			MTRTrafficAddon.LOGGER.error("Failed to load traffic intersections", e);
		}
	}

	private static void save(MinecraftServer server) {
		try {
			final Path path = savePath(server);
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(new ArrayList<>(DEFINITIONS.values()), writer);
			}
		} catch (Exception e) {
			MTRTrafficAddon.LOGGER.error("Failed to save traffic intersections", e);
		}
	}

	private static Path savePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MTRTrafficAddon.MOD_ID).resolve("traffic_intersections.json");
	}
}
