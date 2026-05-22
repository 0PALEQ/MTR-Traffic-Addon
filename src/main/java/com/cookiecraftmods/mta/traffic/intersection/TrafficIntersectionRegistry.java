package com.cookiecraftmods.mta.traffic.intersection;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.TrafficManager;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraph;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrNodeKey;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightSignalState;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TrafficIntersectionRegistry {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<TrafficIntersectionDefinition>>() { }.getType();
	private static final Map<String, TrafficIntersectionDefinition> DEFINITIONS = new LinkedHashMap<>();
	private static final double STOP_LOOKAHEAD_METERS = 48.0D;
	private static final double STOP_BUFFER_METERS = 5.0D;
	private static final double AUTO_DEMAND_LOOKAHEAD_METERS = STOP_LOOKAHEAD_METERS + STOP_BUFFER_METERS;
	private static final int CLEARANCE_TICKS = 200;
	private static final int AUTO_SWITCH_DELAY_TICKS = 60;
	private static final int AUTO_YELLOW_TICKS = 60;
	private static final int MIN_GREEN_TICKS = 300;
	private static final long AUTO_SIGNAL_FAIL_OPEN_STALE_MILLIS = 1500L;
	private static final Map<String, AutoSignalState> AUTO_SIGNAL_STATES = new HashMap<>();
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
			AUTO_SIGNAL_STATES.clear();
		});
		initialized = true;
	}

	public static Collection<TrafficIntersectionDefinition> getDefinitions() {
		return List.copyOf(DEFINITIONS.values());
	}

	public static Optional<TrafficIntersectionDefinition> getDefinition(String intersectionId) {
		return Optional.ofNullable(DEFINITIONS.get(intersectionId));
	}

	public static Optional<TrafficLightSignalState> signalState(String intersectionId, int nodeNumber, long serverTick) {
		final TrafficIntersectionDefinition definition = DEFINITIONS.get(intersectionId);
		if (definition == null || !definition.isEnabled() || definition.nodes().isEmpty() || nodeNumber <= 0) {
			return Optional.empty();
		}
		final boolean knownNode = definition.nodes().stream().anyMatch(node -> node.type() == TrafficIntersectionNodeType.IN && node.number() == nodeNumber);
		if (!knownNode) {
			return Optional.empty();
		}
		if (activeInNumbers(definition, serverTick).contains(nodeNumber)) {
			return Optional.of(TrafficLightSignalState.GREEN);
		}
		if (yellowInNumbers(definition, serverTick).contains(nodeNumber)) {
			return Optional.of(TrafficLightSignalState.YELLOW);
		}
		return Optional.of(TrafficLightSignalState.RED);
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
		final TrafficIntersectionDefinition definition = new TrafficIntersectionDefinition(id, null, dimensionId, minX, minY, minZ, maxX, maxY, maxZ, true, true, TrafficIntersectionSignalMode.MANUAL, MIN_GREEN_TICKS, List.of(), List.of(), List.of());
		DEFINITIONS.put(id, definition);
		save(level.getServer());
		return definition;
	}

	public static boolean applyDashboardUpdate(String intersectionId, String action, int delta, String value) {
		if ("delete".equals(action)) {
			final boolean removed = DEFINITIONS.remove(intersectionId) != null;
			AUTO_SIGNAL_STATES.remove(intersectionId);
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
			case "enabled" -> copy(definition, !definition.isEnabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), definition.nodes());
			case "signal_mode" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.effectiveSignalMode() == TrafficIntersectionSignalMode.AUTO ? TrafficIntersectionSignalMode.MANUAL : TrafficIntersectionSignalMode.AUTO, definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), definition.nodes());
			case "auto_detect", "find_nodes" -> copy(definition, definition.enabled(), true, definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), definition.nodes());
			case "node_add" -> copy(definition, definition.enabled(), false, definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), addManualNode(definition, value));
			case "node_delete" -> deleteNode(definition, value);
			case "phase_duration" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), clamp(definition.effectivePhaseDurationTicks() + delta, MIN_GREEN_TICKS, 2400), definition.phaseOrder(), updateGroupDuration(dashboardGroups(definition), value, delta), definition.nodes());
			case "node_type" -> copy(definition, definition.enabled(), false, definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), updateNearestNode(definition, value, delta, NodeUpdateMode.TYPE));
			case "node_number" -> copy(definition, definition.enabled(), false, definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), updateNearestNode(definition, value, delta, NodeUpdateMode.NUMBER));
			case "phase_toggle" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), togglePhase(definition.phaseOrder(), delta), definition.groups(), definition.nodes());
			case "phase_add" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), addPhase(definition.phaseOrder(), delta), addNodeToGroup(dashboardGroups(definition), value, delta), definition.nodes());
			case "phase_assign" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), addPhase(definition.phaseOrder(), delta), addNodeToGroup(dashboardGroups(definition), value, delta), definition.nodes());
			case "phase_remove" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), removePhase(definition.phaseOrder(), delta), removeGroupOrNode(dashboardGroups(definition), value, delta), definition.nodes());
			case "phase_move" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), movePhase(definition.phaseOrder(), value, delta), moveGroup(dashboardGroups(definition), value, delta), definition.nodes());
			case "group_add" -> copy(definition, definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), addGroup(dashboardGroups(definition)), definition.nodes());
			default -> definition;
		};
		DEFINITIONS.put(intersectionId, updated);
		if (!updated.isEnabled() || updated.effectiveSignalMode() != TrafficIntersectionSignalMode.AUTO) {
			AUTO_SIGNAL_STATES.remove(intersectionId);
		}
		if (currentServer != null) {
			save(currentServer);
		}
		return true;
	}

	public static int refreshNodes(String dimensionId, MtrGraph graph) {
		if (graph == null || graph.isEmpty()) {
			return 0;
		}

		int changed = 0;
		for (TrafficIntersectionDefinition definition : List.copyOf(DEFINITIONS.values())) {
			if (!definition.dimensionId().equals(dimensionId) || !definition.effectiveAutoDetectNodes()) {
				continue;
			}

			final List<TrafficIntersectionNode> detectedNodes = detectBoundaryNodes(definition, graph);
			if (!detectedNodes.equals(definition.nodes())) {
				DEFINITIONS.put(definition.id(), copyWithNodes(definition, detectedNodes));
				changed++;
			}
		}

		if (changed > 0 && currentServer != null) {
			save(currentServer);
		}
		return changed;
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

	public static void tickAutoSignals(String dimensionId, MtrGraph graph, Collection<TrafficVehicle> vehicles, Collection<TrafficManager.MtrSignalVehicle> mtrVehicles, long serverTick) {
		if (dimensionId == null || DEFINITIONS.isEmpty()) {
			AUTO_SIGNAL_STATES.clear();
			return;
		}

		final Set<String> activeAutoIntersectionIds = new LinkedHashSet<>();
		for (TrafficIntersectionDefinition definition : DEFINITIONS.values()) {
			if (!definition.dimensionId().equals(dimensionId) || !definition.isEnabled() || definition.effectiveSignalMode() != TrafficIntersectionSignalMode.AUTO || definition.nodes().isEmpty()) {
				continue;
			}

			final List<TrafficIntersectionGroup> groups = validGroups(definition);
			if (groups.isEmpty()) {
				AUTO_SIGNAL_STATES.remove(definition.id());
				continue;
			}

			activeAutoIntersectionIds.add(definition.id());
			final AutoSignalState state = AUTO_SIGNAL_STATES.computeIfAbsent(definition.id(), ignored -> new AutoSignalState());
			state.lastTickWallMillis = System.currentTimeMillis();
			if (state.activeGroupIndex >= groups.size()) {
				state.activeGroupIndex = -1;
				state.switchAtTick = Long.MAX_VALUE;
				state.yellowNodeNumbers.clear();
				state.yellowUntilTick = Math.max(state.yellowUntilTick, serverTick + AUTO_YELLOW_TICKS);
			}
			state.queue.removeIf(index -> index < 0 || index >= groups.size());

			final Set<Integer> demandedGroups = demandedGroupIndexes(definition, groups, graph, vehicles, mtrVehicles, serverTick);
			for (Integer demandedGroup : demandedGroups) {
				if (demandedGroup != state.activeGroupIndex) {
					state.queue.add(demandedGroup);
				}
			}

			if (state.activeGroupIndex < 0) {
				activateNextQueuedGroup(definition, groups, state, demandedGroups, vehicles, mtrVehicles, graph, serverTick);
				continue;
			}

			final boolean otherDemandQueued = !state.queue.isEmpty();
			if (!otherDemandQueued) {
				state.switchAtTick = Long.MAX_VALUE;
				continue;
			}

			final boolean currentGroupHasVehicles = groupHasVehicles(definition, groups.get(state.activeGroupIndex), graph, vehicles, mtrVehicles, serverTick);
			final boolean minimumGreenElapsed = serverTick - state.greenSinceTick >= MIN_GREEN_TICKS;
			if (!currentGroupHasVehicles || minimumGreenElapsed) {
				if (!currentGroupHasVehicles || state.switchAtTick == Long.MAX_VALUE) {
					state.switchAtTick = currentGroupHasVehicles ? serverTick + AUTO_SWITCH_DELAY_TICKS : serverTick;
				}
				if (serverTick >= state.switchAtTick) {
					beginAutoYellow(groups, state, serverTick);
					activateNextQueuedGroup(definition, groups, state, demandedGroups, vehicles, mtrVehicles, graph, serverTick);
				}
			} else {
				state.switchAtTick = Long.MAX_VALUE;
			}
		}

		AUTO_SIGNAL_STATES.keySet().removeIf(id -> !activeAutoIntersectionIds.contains(id));
	}

	public static boolean isRedMtrEntry(String dimensionId, long startX, long startY, long startZ, long endX, long endY, long endZ, long serverTick) {
		if (dimensionId == null || DEFINITIONS.isEmpty() || !TrafficManager.trafficTicksAreFreshForMtr()) {
			return false;
		}

		for (TrafficIntersectionDefinition definition : DEFINITIONS.values()) {
			if (!definition.dimensionId().equals(dimensionId) || !definition.isEnabled() || definition.nodes().isEmpty()) {
				continue;
			}
			if (definition.contains(startX, startY, startZ) || !definition.contains(endX, endY, endZ)) {
				continue;
			}

			final Optional<Integer> inNumber = inNumberForEntry(definition, endX, endZ);
			if (inNumber.isPresent() && !activeInNumbers(definition, serverTick).contains(inNumber.get())) {
				return true;
			}
		}
		return false;
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

	private static void activateNextQueuedGroup(TrafficIntersectionDefinition definition, List<TrafficIntersectionGroup> groups, AutoSignalState state, Set<Integer> demandedGroups, Collection<TrafficVehicle> vehicles, Collection<TrafficManager.MtrSignalVehicle> mtrVehicles, MtrGraph graph, long serverTick) {
		if (serverTick < state.yellowUntilTick) {
			return;
		}
		if (!intersectionIsEmpty(definition, vehicles, mtrVehicles, graph, serverTick)) {
			return;
		}

		Integer nextGroupIndex = pollNextGroupWithDemand(state, demandedGroups);
		if (nextGroupIndex == null && state.activeGroupIndex < 0) {
			for (int i = 0; i < groups.size(); i++) {
				if (demandedGroups.contains(i)) {
					nextGroupIndex = i;
					break;
				}
			}
		}
		if (nextGroupIndex == null) {
			return;
		}

		state.activeGroupIndex = nextGroupIndex;
		state.greenSinceTick = serverTick;
		state.switchAtTick = Long.MAX_VALUE;
		state.yellowNodeNumbers.clear();
		state.yellowUntilTick = 0L;
	}

	private static void beginAutoYellow(List<TrafficIntersectionGroup> groups, AutoSignalState state, long serverTick) {
		state.yellowNodeNumbers.clear();
		if (state.activeGroupIndex >= 0 && state.activeGroupIndex < groups.size()) {
			state.yellowNodeNumbers.addAll(groups.get(state.activeGroupIndex).nodeNumbers());
		}
		state.activeGroupIndex = -1;
		state.switchAtTick = Long.MAX_VALUE;
		state.yellowUntilTick = Math.max(state.yellowUntilTick, serverTick + AUTO_YELLOW_TICKS);
	}

	private static Integer pollNextGroupWithDemand(AutoSignalState state, Set<Integer> demandedGroups) {
		while (!state.queue.isEmpty()) {
			final Integer nextGroupIndex = state.queue.iterator().next();
			state.queue.remove(nextGroupIndex);
			if (demandedGroups.contains(nextGroupIndex)) {
				return nextGroupIndex;
			}
		}
		return null;
	}

	private static Set<Integer> demandedGroupIndexes(TrafficIntersectionDefinition definition, List<TrafficIntersectionGroup> groups, MtrGraph graph, Collection<TrafficVehicle> vehicles, Collection<TrafficManager.MtrSignalVehicle> mtrVehicles, long serverTick) {
		final Set<Integer> demandedGroups = new LinkedHashSet<>();
		for (TrafficVehicle vehicle : vehicles) {
			approachingInNumber(definition, vehicle).ifPresent(inNumber -> addGroupsForNodeNumber(groups, inNumber, demandedGroups));
		}
		if (graph != null && !graph.isEmpty()) {
			for (TrafficManager.MtrSignalVehicle mtrVehicle : mtrVehicles) {
				if (serverTick - mtrVehicle.lastTick() <= 5) {
					approachingInNumber(definition, graph, mtrVehicle).ifPresent(inNumber -> addGroupsForNodeNumber(groups, inNumber, demandedGroups));
				}
			}
		}
		return demandedGroups;
	}

	private static Optional<Integer> approachingInNumber(TrafficIntersectionDefinition definition, TrafficVehicle vehicle) {
		final List<TrafficRouteSegment> segments = vehicle.route().segments();
		if (segments.isEmpty() || vehicle.segmentIndex() < 0 || vehicle.segmentIndex() >= segments.size()) {
			return Optional.empty();
		}

		double distanceFromVehicle = -vehicle.distanceOnSegmentMeters();
		for (int i = vehicle.segmentIndex(); i < segments.size(); i++) {
			final TrafficRouteSegment segment = segments.get(i);
			distanceFromVehicle += segment.lengthMeters();
			if (distanceFromVehicle > AUTO_DEMAND_LOOKAHEAD_METERS) {
				return Optional.empty();
			}
			if (isEntering(definition, segment)) {
				return inNumberForEntry(definition, segment);
			}
		}
		return Optional.empty();
	}

	private static Optional<Integer> approachingInNumber(TrafficIntersectionDefinition definition, MtrGraph graph, TrafficManager.MtrSignalVehicle mtrVehicle) {
		for (MtrGraphEdge edge : graph.edges()) {
			if (!edge.railId().equals(mtrVehicle.connectorId()) || !isEntering(definition, edge)) {
				continue;
			}

			final double distanceToEntry = Math.max(0.0D, edge.lengthMeters() - mtrVehicle.distanceOnSegmentMeters());
			if (distanceToEntry <= AUTO_DEMAND_LOOKAHEAD_METERS) {
				return inNumberForEntry(definition, edge);
			}
		}
		return Optional.empty();
	}

	private static boolean groupHasVehicles(TrafficIntersectionDefinition definition, TrafficIntersectionGroup group, MtrGraph graph, Collection<TrafficVehicle> vehicles, Collection<TrafficManager.MtrSignalVehicle> mtrVehicles, long serverTick) {
		for (TrafficVehicle vehicle : vehicles) {
			final Optional<Integer> inNumber = approachingInNumber(definition, vehicle);
			if (inNumber.isPresent() && group.nodeNumbers().contains(inNumber.get())) {
				return true;
			}
		}
		if (graph != null && !graph.isEmpty()) {
			for (TrafficManager.MtrSignalVehicle mtrVehicle : mtrVehicles) {
				final Optional<Integer> inNumber = serverTick - mtrVehicle.lastTick() <= 5 ? approachingInNumber(definition, graph, mtrVehicle) : Optional.empty();
				if (inNumber.isPresent() && group.nodeNumbers().contains(inNumber.get())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean intersectionIsEmpty(TrafficIntersectionDefinition definition, Collection<TrafficVehicle> vehicles, Collection<TrafficManager.MtrSignalVehicle> mtrVehicles, MtrGraph graph, long serverTick) {
		for (TrafficVehicle vehicle : vehicles) {
			if (vehicleBlocksIntersectionClearance(definition, vehicle)) {
				return false;
			}
		}

		if (graph == null || graph.isEmpty()) {
			return true;
		}
		for (TrafficManager.MtrSignalVehicle mtrVehicle : mtrVehicles) {
			if (serverTick - mtrVehicle.lastTick() > 5) {
				continue;
			}
			for (MtrGraphEdge edge : graph.edges()) {
				if (edge.railId().equals(mtrVehicle.connectorId()) && mtrVehicleBlocksIntersectionClearance(definition, edge, mtrVehicle)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean vehicleBlocksIntersectionClearance(TrafficIntersectionDefinition definition, TrafficVehicle vehicle) {
		final com.cookiecraftmods.mta.traffic.runtime.TrafficVehiclePosition position = vehicle.currentPosition();
		if (!definition.contains(position.x(), position.y(), position.z())) {
			return false;
		}

		final TrafficRouteSegment segment = vehicle.currentSegment().orElse(null);
		if (segment == null || !isEntering(definition, segment)) {
			return true;
		}

		final double stopLineTolerance = STOP_BUFFER_METERS + vehicle.definition().lengthMeters() * 0.5D + 1.0D;
		return vehicle.distanceToEndOfCurrentSegmentMeters() > stopLineTolerance;
	}

	private static boolean mtrVehicleBlocksIntersectionClearance(TrafficIntersectionDefinition definition, MtrGraphEdge edge, TrafficManager.MtrSignalVehicle mtrVehicle) {
		final double progress = edge.lengthMeters() <= 0.0D ? 0.0D : Math.min(1.0D, Math.max(0.0D, mtrVehicle.distanceOnSegmentMeters() / edge.lengthMeters()));
		final double x = edge.from().x() + (edge.to().x() - edge.from().x()) * progress;
		final double y = edge.from().y() + (edge.to().y() - edge.from().y()) * progress;
		final double z = edge.from().z() + (edge.to().z() - edge.from().z()) * progress;
		if (!definition.contains(x, y, z)) {
			return false;
		}
		if (!isEntering(definition, edge)) {
			return true;
		}

		final double stopLineTolerance = STOP_BUFFER_METERS + mtrVehicle.lengthMeters() * 0.5D + 1.0D;
		return Math.max(0.0D, edge.lengthMeters() - mtrVehicle.distanceOnSegmentMeters()) > stopLineTolerance;
	}

	private static void addGroupsForNodeNumber(List<TrafficIntersectionGroup> groups, int nodeNumber, Set<Integer> demandedGroups) {
		for (int i = 0; i < groups.size(); i++) {
			if (groups.get(i).nodeNumbers().contains(nodeNumber)) {
				demandedGroups.add(i);
			}
		}
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
		return inNumberForEntry(definition, Math.round(segment.endX()), Math.round(segment.endZ()));
	}

	private static Optional<Integer> inNumberForEntry(TrafficIntersectionDefinition definition, long endX, long endZ) {
		return definition.nodes().stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
			.filter(node -> node.x() == endX && node.z() == endZ)
			.map(TrafficIntersectionNode::number)
			.findFirst();
	}

	private static Optional<Integer> inNumberForEntry(TrafficIntersectionDefinition definition, MtrGraphEdge edge) {
		return definition.nodes().stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
			.filter(node -> node.x() == edge.to().x() && node.z() == edge.to().z())
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
		if (definition.effectiveSignalMode() == TrafficIntersectionSignalMode.AUTO) {
			final AutoSignalState state = AUTO_SIGNAL_STATES.get(definition.id());
			final List<TrafficIntersectionGroup> validGroups = validGroups(definition, inNumbers);
			if (state == null || !autoSignalStateIsFresh(state)) {
				return inNumbers;
			}
			if (state.activeGroupIndex < 0 || state.activeGroupIndex >= validGroups.size()) {
				return List.of();
			}
			return validGroups.get(state.activeGroupIndex).nodeNumbers();
		}
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
				if (validGroups.size() > 1) {
					cycleTicks += CLEARANCE_TICKS;
				}
			}
			long tickInCycle = cycleTicks <= 0 ? 0 : serverTick % cycleTicks;
			for (TrafficIntersectionGroup group : validGroups) {
				tickInCycle -= group.effectiveGreenDurationTicks();
				if (tickInCycle < 0) {
					return group.nodeNumbers();
				}
				if (validGroups.size() > 1) {
					tickInCycle -= CLEARANCE_TICKS;
					if (tickInCycle < 0) {
						return List.of();
					}
				}
			}
		}
		final List<Integer> phaseOrder = definition.phaseOrder().isEmpty() ? inNumbers : definition.phaseOrder().stream().filter(inNumbers::contains).toList();
		if (phaseOrder.isEmpty()) {
			return List.of();
		}
		if (phaseOrder.size() == 1) {
			return List.of(phaseOrder.get(0));
		}

		final long phaseBlockTicks = definition.effectivePhaseDurationTicks() + CLEARANCE_TICKS;
		final long tickInCycle = serverTick % (phaseBlockTicks * phaseOrder.size());
		if (tickInCycle % phaseBlockTicks >= definition.effectivePhaseDurationTicks()) {
			return List.of();
		}
		return List.of(phaseOrder.get((int) (tickInCycle / phaseBlockTicks)));
	}

	private static List<Integer> yellowInNumbers(TrafficIntersectionDefinition definition, long serverTick) {
		final List<Integer> inNumbers = definition.nodes().stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
			.map(TrafficIntersectionNode::number)
			.distinct()
			.sorted()
			.toList();
		if (inNumbers.isEmpty()) {
			return List.of();
		}
		if (definition.effectiveSignalMode() == TrafficIntersectionSignalMode.AUTO) {
			final AutoSignalState state = AUTO_SIGNAL_STATES.get(definition.id());
			if (state == null || !autoSignalStateIsFresh(state) || serverTick >= state.yellowUntilTick) {
				return List.of();
			}
			return state.yellowNodeNumbers.stream()
				.filter(inNumbers::contains)
				.distinct()
				.sorted()
				.toList();
		}
		final List<TrafficIntersectionGroup> groups = effectiveGroups(definition, inNumbers);
		if (!groups.isEmpty()) {
			final List<TrafficIntersectionGroup> validGroups = groups.stream()
				.map(group -> new TrafficIntersectionGroup(group.name(), group.effectiveGreenDurationTicks(), group.nodeNumbers().stream().filter(inNumbers::contains).distinct().toList()))
				.filter(group -> !group.nodeNumbers().isEmpty())
				.toList();
			if (validGroups.size() <= 1) {
				return List.of();
			}
			long cycleTicks = 0;
			for (TrafficIntersectionGroup group : validGroups) {
				cycleTicks += group.effectiveGreenDurationTicks() + CLEARANCE_TICKS;
			}
			long tickInCycle = cycleTicks <= 0 ? 0 : serverTick % cycleTicks;
			for (TrafficIntersectionGroup group : validGroups) {
				tickInCycle -= group.effectiveGreenDurationTicks();
				if (tickInCycle < 0) {
					return List.of();
				}
				tickInCycle -= CLEARANCE_TICKS;
				if (tickInCycle < 0) {
					return group.nodeNumbers();
				}
			}
			return List.of();
		}
		final List<Integer> phaseOrder = definition.phaseOrder().isEmpty() ? inNumbers : definition.phaseOrder().stream().filter(inNumbers::contains).toList();
		if (phaseOrder.size() <= 1) {
			return List.of();
		}
		final long phaseBlockTicks = definition.effectivePhaseDurationTicks() + CLEARANCE_TICKS;
		final long tickInCycle = serverTick % (phaseBlockTicks * phaseOrder.size());
		if (tickInCycle % phaseBlockTicks < definition.effectivePhaseDurationTicks()) {
			return List.of();
		}
		return List.of(phaseOrder.get((int) (tickInCycle / phaseBlockTicks)));
	}

	private static boolean autoSignalStateIsFresh(AutoSignalState state) {
		return state.lastTickWallMillis > 0L && System.currentTimeMillis() - state.lastTickWallMillis <= AUTO_SIGNAL_FAIL_OPEN_STALE_MILLIS;
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

	private static List<TrafficIntersectionGroup> validGroups(TrafficIntersectionDefinition definition) {
		final List<Integer> inNumbers = definition.nodes().stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
			.map(TrafficIntersectionNode::number)
			.distinct()
			.sorted()
			.toList();
		return validGroups(definition, inNumbers);
	}

	private static List<TrafficIntersectionGroup> validGroups(TrafficIntersectionDefinition definition, List<Integer> inNumbers) {
		return effectiveGroups(definition, inNumbers).stream()
			.map(group -> new TrafficIntersectionGroup(group.name(), group.effectiveGreenDurationTicks(), group.nodeNumbers().stream().filter(inNumbers::contains).distinct().toList()))
			.filter(group -> !group.nodeNumbers().isEmpty())
			.toList();
	}

	private static boolean isEntering(TrafficIntersectionDefinition definition, TrafficRouteSegment segment) {
		return !definition.contains(segment.startX(), segment.startY(), segment.startZ()) && definition.contains(segment.endX(), segment.endY(), segment.endZ());
	}

	private static boolean isEntering(TrafficIntersectionDefinition definition, MtrGraphEdge edge) {
		return !contains(definition, edge.from()) && contains(definition, edge.to());
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
		return new TrafficIntersectionDefinition(definition.id(), definition.name(), definition.dimensionId(), definition.minX(), definition.minY(), definition.minZ(), definition.maxX(), definition.maxY(), definition.maxZ(), definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), nodes);
	}

	private static TrafficIntersectionDefinition copy(TrafficIntersectionDefinition definition, Boolean enabled, Boolean autoDetectNodes, TrafficIntersectionSignalMode signalMode, Integer phaseDurationTicks, List<Integer> phaseOrder, List<TrafficIntersectionGroup> groups, List<TrafficIntersectionNode> nodes) {
		return new TrafficIntersectionDefinition(definition.id(), definition.name(), definition.dimensionId(), definition.minX(), definition.minY(), definition.minZ(), definition.maxX(), definition.maxY(), definition.maxZ(), enabled, autoDetectNodes, signalMode, phaseDurationTicks, phaseOrder, groups, nodes);
	}

	private static TrafficIntersectionDefinition copyWithName(TrafficIntersectionDefinition definition, String name) {
		final String trimmedName = name == null ? null : name.trim();
		return new TrafficIntersectionDefinition(definition.id(), trimmedName == null || trimmedName.isBlank() ? null : trimmedName, definition.dimensionId(), definition.minX(), definition.minY(), definition.minZ(), definition.maxX(), definition.maxY(), definition.maxZ(), definition.enabled(), definition.autoDetectNodes(), definition.signalMode(), definition.phaseDurationTicks(), definition.phaseOrder(), definition.groups(), definition.nodes());
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

	private static List<TrafficIntersectionNode> addManualNode(TrafficIntersectionDefinition definition, String encodedNode) {
		final MtrNodeKey targetNode = decodeNode(encodedNode);
		if (targetNode == null) {
			return definition.nodes();
		}

		final List<TrafficIntersectionNode> updatedNodes = new ArrayList<>(definition.nodes());
		for (TrafficIntersectionNode node : updatedNodes) {
			if (node.x() == targetNode.x() && node.z() == targetNode.z()) {
				return updatedNodes;
			}
		}

		final int nextNumber = updatedNodes.stream()
			.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
			.mapToInt(TrafficIntersectionNode::number)
			.max()
			.orElse(0) + 1;
		updatedNodes.add(new TrafficIntersectionNode(targetNode.x(), targetNode.y(), targetNode.z(), TrafficIntersectionNodeType.IN, nextNumber));
		updatedNodes.sort(nodeComparator());
		return updatedNodes;
	}

	private static TrafficIntersectionDefinition deleteNode(TrafficIntersectionDefinition definition, String encodedNode) {
		final MtrNodeKey targetNode = decodeNode(encodedNode);
		if (targetNode == null) {
			return definition;
		}

		TrafficIntersectionNode removedNode = null;
		final List<TrafficIntersectionNode> updatedNodes = new ArrayList<>(definition.nodes().size());
		for (TrafficIntersectionNode node : definition.nodes()) {
			if (node.x() == targetNode.x() && node.y() == targetNode.y() && node.z() == targetNode.z()) {
				removedNode = node;
			} else {
				updatedNodes.add(node);
			}
		}
		if (removedNode == null) {
			return definition;
		}

		List<Integer> updatedPhaseOrder = definition.phaseOrder();
		List<TrafficIntersectionGroup> updatedGroups = definition.groups();
		if (removedNode.type() == TrafficIntersectionNodeType.IN && !hasInNodeNumber(updatedNodes, removedNode.number())) {
			updatedPhaseOrder = removePhase(definition.phaseOrder(), removedNode.number());
			updatedGroups = removeNodeFromGroups(definition.groups(), removedNode.number());
		}
		return copy(definition, definition.enabled(), false, definition.signalMode(), definition.phaseDurationTicks(), updatedPhaseOrder, updatedGroups, updatedNodes);
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

	private static List<TrafficIntersectionGroup> removeNodeFromGroups(List<TrafficIntersectionGroup> groups, int nodeNumber) {
		final List<TrafficIntersectionGroup> updatedGroups = new ArrayList<>(groups.size());
		for (TrafficIntersectionGroup group : groups) {
			final List<Integer> nodeNumbers = new ArrayList<>(group.nodeNumbers());
			nodeNumbers.remove(Integer.valueOf(nodeNumber));
			updatedGroups.add(new TrafficIntersectionGroup(group.name(), group.effectiveGreenDurationTicks(), nodeNumbers));
		}
		return updatedGroups;
	}

	private static boolean hasInNodeNumber(List<TrafficIntersectionNode> nodes, int number) {
		for (TrafficIntersectionNode node : nodes) {
			if (node.type() == TrafficIntersectionNodeType.IN && node.number() == number) {
				return true;
			}
		}
		return false;
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

	private static final class AutoSignalState {
		private int activeGroupIndex = -1;
		private long greenSinceTick;
		private long switchAtTick = Long.MAX_VALUE;
		private long yellowUntilTick;
		private long lastTickWallMillis;
		private final LinkedHashSet<Integer> queue = new LinkedHashSet<>();
		private final LinkedHashSet<Integer> yellowNodeNumbers = new LinkedHashSet<>();
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
