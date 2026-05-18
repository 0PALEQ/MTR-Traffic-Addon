package com.cookiecraftmods.mta.traffic.point;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraph;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphPathFinder;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrNodeKey;
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

public final class TrafficSavedPointRegistry {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<TrafficPointDefinition>>() { }.getType();
	private static final Map<String, TrafficPointDefinition> DEFINITIONS = new LinkedHashMap<>();
	private static boolean initialized;
	private static MinecraftServer currentServer;

	private TrafficSavedPointRegistry() {
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

	public static Collection<TrafficPointDefinition> getDefinitions() {
		return List.copyOf(DEFINITIONS.values());
	}

	public static List<TrafficPointDefinition> getByTypeAndDimension(String dimensionId, TrafficPointType type) {
		return DEFINITIONS.values().stream()
			.filter(definition -> definition.id().startsWith(dimensionId + "|"))
			.filter(definition -> definition.type() == type)
			.toList();
	}

	public static void createConnectorPoint(ServerLevel level, TrafficPointType type, BlockPos firstNode, BlockPos secondNode) {
		final String dimensionId = level.dimension().location().toString();
		final long midpointX = Math.round((firstNode.getX() + secondNode.getX()) / 2.0D);
		final long midpointY = Math.round((firstNode.getY() + secondNode.getY()) / 2.0D);
		final long midpointZ = Math.round((firstNode.getZ() + secondNode.getZ()) / 2.0D);
		final String pointId = dimensionId + "|" + type.name().toLowerCase() + "|" + firstNode.asLong() + "|" + secondNode.asLong();

		DEFINITIONS.put(pointId, new TrafficPointDefinition(
			pointId,
			type,
			midpointX,
			midpointY,
			midpointZ,
			0,
			true,
			type == TrafficPointType.SPAWN ? 1 : null,
			type == TrafficPointType.SPAWN ? 40 : null,
			type == TrafficPointType.SPAWN ? -1 : null,
			(long) firstNode.getX(),
			(long) firstNode.getY(),
			(long) firstNode.getZ(),
			(long) secondNode.getX(),
			(long) secondNode.getY(),
			(long) secondNode.getZ(),
			List.of()
		));
		save(level.getServer());
	}

	public static boolean applyUpdate(String pointId, String action, int delta) {
		final TrafficPointDefinition definition = DEFINITIONS.get(pointId);
		if (definition == null) {
			return false;
		}

		TrafficPointDefinition updated = switch (action) {
			case "group" -> copy(definition, clamp(definition.group() + delta, 0, 15), definition.enabled(), definition.maxVehicles(), definition.spawnIntervalTicks(), definition.targetGroup(), definition.effectiveVehiclePool());
			case "enabled" -> copy(definition, definition.group(), !definition.isEnabled(), definition.maxVehicles(), definition.spawnIntervalTicks(), definition.targetGroup(), definition.effectiveVehiclePool());
			case "max_vehicles" -> definition.type() == TrafficPointType.SPAWN ? copy(definition, definition.group(), definition.enabled(), clamp((definition.maxVehicles() == null ? 1 : definition.maxVehicles()) + delta, 0, 32), definition.spawnIntervalTicks(), definition.targetGroup(), definition.effectiveVehiclePool()) : definition;
			case "spawn_interval" -> definition.type() == TrafficPointType.SPAWN ? copy(definition, definition.group(), definition.enabled(), definition.maxVehicles(), clamp((definition.spawnIntervalTicks() == null ? 40 : definition.spawnIntervalTicks()) + delta, 20, 1200), definition.targetGroup(), definition.effectiveVehiclePool()) : definition;
			case "target_group" -> definition.type() == TrafficPointType.SPAWN ? copy(definition, definition.group(), definition.enabled(), definition.maxVehicles(), definition.spawnIntervalTicks(), clamp((definition.targetGroup() == null ? definition.group() : definition.targetGroup()) + delta, -1, 15), definition.effectiveVehiclePool()) : definition;
			default -> definition;
		};

		DEFINITIONS.put(pointId, updated);
		if (currentServer != null) {
			save(currentServer);
		}
		return true;
	}

	public static boolean toggleVehiclePool(String pointId, String vehicleId) {
		final TrafficPointDefinition definition = DEFINITIONS.get(pointId);
		if (definition == null || definition.type() != TrafficPointType.SPAWN || vehicleId == null || vehicleId.isBlank()) {
			return false;
		}

		final List<String> updatedPool = new ArrayList<>(definition.effectiveVehiclePool());
		if (updatedPool.contains(vehicleId)) {
			updatedPool.remove(vehicleId);
		} else {
			updatedPool.add(vehicleId);
		}

		DEFINITIONS.put(pointId, copy(definition, definition.group(), definition.enabled(), definition.maxVehicles(), definition.spawnIntervalTicks(), definition.targetGroup(), updatedPool));
		if (currentServer != null) {
			save(currentServer);
		}
		return true;
	}

	public static int refreshConnectorRoutes(String dimensionId, MtrGraph graph, long centerX, long centerZ, int radius) {
		if (graph == null || graph.isEmpty()) {
			return 0;
		}

		int repaired = 0;
		for (TrafficPointDefinition definition : DEFINITIONS.values()) {
			if (!definition.id().startsWith(dimensionId + "|")) {
				continue;
			}

			final long dx = definition.x() - centerX;
			final long dz = definition.z() - centerZ;
			if (dx * dx + dz * dz > (long) radius * radius) {
				continue;
			}

			if (definition.hasConnectorRoute()) {
				final MtrNodeKey start = new MtrNodeKey(definition.connectorStartX(), definition.connectorStartY(), definition.connectorStartZ());
				final MtrNodeKey end = new MtrNodeKey(definition.connectorEndX(), definition.connectorEndY(), definition.connectorEndZ());
				final boolean existsForward = MtrGraphPathFinder.findEdge(graph, start, end).isPresent();
				final boolean existsBackward = MtrGraphPathFinder.findEdge(graph, end, start).isPresent();
				if (existsForward || existsBackward) {
					continue;
				}
			}

			final Optional<com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge> nearestEdge = nearestEdge(graph, definition);
			if (nearestEdge.isPresent()) {
				DEFINITIONS.put(definition.id(), copyWithConnectorRoute(definition, nearestEdge.get()));
				repaired++;
			}
		}

		if (repaired > 0 && currentServer != null) {
			save(currentServer);
		}
		return repaired;
	}

	public static int refreshConnectorRoutes(String dimensionId, MtrGraph graph) {
		if (graph == null || graph.isEmpty()) {
			return 0;
		}

		int repaired = 0;
		for (TrafficPointDefinition definition : DEFINITIONS.values()) {
			if (!definition.id().startsWith(dimensionId + "|")) {
				continue;
			}

			if (definition.hasConnectorRoute()) {
				final MtrNodeKey start = new MtrNodeKey(definition.connectorStartX(), definition.connectorStartY(), definition.connectorStartZ());
				final MtrNodeKey end = new MtrNodeKey(definition.connectorEndX(), definition.connectorEndY(), definition.connectorEndZ());
				if (MtrGraphPathFinder.findEdge(graph, start, end).isPresent() || MtrGraphPathFinder.findEdge(graph, end, start).isPresent()) {
					continue;
				}
			}

			final Optional<com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge> nearestEdge = nearestEdge(graph, definition);
			if (nearestEdge.isPresent()) {
				DEFINITIONS.put(definition.id(), copyWithConnectorRoute(definition, nearestEdge.get()));
				repaired++;
			}
		}

		if (repaired > 0 && currentServer != null) {
			save(currentServer);
		}
		return repaired;
	}

	private static TrafficPointDefinition copy(TrafficPointDefinition definition, int group, Boolean enabled, Integer maxVehicles, Integer spawnIntervalTicks, Integer targetGroup, List<String> vehiclePool) {
		return new TrafficPointDefinition(
			definition.id(),
			definition.type(),
			definition.x(),
			definition.y(),
			definition.z(),
			group,
			enabled,
			maxVehicles,
			spawnIntervalTicks,
			targetGroup,
			definition.connectorStartX(),
			definition.connectorStartY(),
			definition.connectorStartZ(),
			definition.connectorEndX(),
			definition.connectorEndY(),
			definition.connectorEndZ(),
			List.copyOf(vehiclePool)
		);
	}

	private static TrafficPointDefinition copyWithConnectorRoute(TrafficPointDefinition definition, com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge edge) {
		return new TrafficPointDefinition(
			definition.id(),
			definition.type(),
			definition.x(),
			definition.y(),
			definition.z(),
			definition.group(),
			definition.enabled(),
			definition.maxVehicles(),
			definition.spawnIntervalTicks(),
			definition.targetGroup(),
			edge.from().x(),
			edge.from().y(),
			edge.from().z(),
			edge.to().x(),
			edge.to().y(),
			edge.to().z(),
			definition.effectiveVehiclePool()
		);
	}

	private static Optional<com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge> nearestEdge(MtrGraph graph, TrafficPointDefinition definition) {
		return graph.edges().stream()
			.min(Comparator.comparingDouble(edge -> distanceSquaredToEdge(edge, definition.x(), definition.y(), definition.z())));
	}

	private static double distanceSquaredToEdge(com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge edge, double x, double y, double z) {
		if (edge.path().size() >= 2) {
			double bestDistanceSquared = Double.POSITIVE_INFINITY;
			for (int i = 1; i < edge.path().size(); i++) {
				final com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint previous = edge.path().get(i - 1);
				final com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint next = edge.path().get(i);
				bestDistanceSquared = Math.min(bestDistanceSquared, distanceSquaredToSegment(x, y, z, previous.x(), previous.y(), previous.z(), next.x(), next.y(), next.z()));
			}
			return bestDistanceSquared;
		}

		return distanceSquaredToSegment(x, y, z, edge.from().x(), edge.from().y(), edge.from().z(), edge.to().x(), edge.to().y(), edge.to().z());
	}

	private static double distanceSquaredToSegment(double px, double py, double pz, double x1, double y1, double z1, double x2, double y2, double z2) {
		final double dx = x2 - x1;
		final double dy = y2 - y1;
		final double dz = z2 - z1;
		final double lengthSquared = dx * dx + dy * dy + dz * dz;
		if (lengthSquared <= 0.000001D) {
			return distanceSquared(px, py, pz, x1, y1, z1);
		}

		final double t = Math.max(0.0D, Math.min(1.0D, ((px - x1) * dx + (py - y1) * dy + (pz - z1) * dz) / lengthSquared));
		return distanceSquared(px, py, pz, x1 + dx * t, y1 + dy * t, z1 + dz * t);
	}

	private static double distanceSquared(double x1, double y1, double z1, double x2, double y2, double z2) {
		final double dx = x1 - x2;
		final double dy = y1 - y2;
		final double dz = z1 - z2;
		return dx * dx + dy * dy + dz * dz;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static void load(MinecraftServer server) {
		DEFINITIONS.clear();
		final Path path = savePath(server);
		if (!Files.exists(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			final List<TrafficPointDefinition> definitions = GSON.fromJson(reader, LIST_TYPE);
			if (definitions != null) {
				for (TrafficPointDefinition definition : definitions) {
					DEFINITIONS.put(definition.id(), definition);
				}
			}
		} catch (Exception e) {
			MTRTrafficAddon.LOGGER.error("Failed to load saved traffic connector points", e);
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
			MTRTrafficAddon.LOGGER.error("Failed to save traffic connector points", e);
		}
	}

	private static Path savePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MTRTrafficAddon.MOD_ID).resolve("traffic_connector_points.json");
	}
}
