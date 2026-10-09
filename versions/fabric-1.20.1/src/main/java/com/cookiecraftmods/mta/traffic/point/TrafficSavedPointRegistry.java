
package com.cookiecraftmods.mta.traffic.point;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraph;
import com.cookiecraftmods.mta.traffic.storage.WorldJsonStorage;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.mtr.core.data.Position;
import org.mtr.core.data.TwoPositionsBase;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TrafficSavedPointRegistry {
	private static final Type LIST_TYPE = new TypeToken<List<TrafficPointDefinition>>() { }.getType();
	private static final Map<String, TrafficPointDefinition> DEFINITIONS = new LinkedHashMap<>();
	private static final Map<ConnectorKey, Set<String>> POINT_IDS_BY_RAIL = new HashMap<>();
	private record ConnectorKey(String dimensionId, String railId) { }
	private static boolean initialized;
	private static MinecraftServer currentServer;

	private TrafficSavedPointRegistry() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			currentServer = server;
			load(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			synchronized (TrafficSavedPointRegistry.class) {
				save(server);
				currentServer = null;
				DEFINITIONS.clear();
				POINT_IDS_BY_RAIL.clear();
			}
		});
		initialized = true;
	}

	public static synchronized Collection<TrafficPointDefinition> getDefinitions() {
		return List.copyOf(DEFINITIONS.values());
	}

	public static synchronized List<TrafficPointDefinition> getByTypeAndDimension(String dimensionId, TrafficPointType type) {
		return DEFINITIONS.values().stream()
			.filter(definition -> definition.id().startsWith(dimensionId + "|"))
			.filter(definition -> definition.type() == type)
			.toList();
	}

	public static synchronized void createConnectorPoint(ServerLevel level, TrafficPointType type, BlockPos firstNode, BlockPos secondNode) {
		final String dimensionId = level.dimension().location().toString();
		final long midpointX = Math.round((firstNode.getX() + secondNode.getX()) / 2.0D);
		final long midpointY = Math.round((firstNode.getY() + secondNode.getY()) / 2.0D);
		final long midpointZ = Math.round((firstNode.getZ() + secondNode.getZ()) / 2.0D);
		final String pointId = dimensionId + "|" + type.name().toLowerCase() + "|" + firstNode.asLong() + "|" + secondNode.asLong();

		putDefinition(new TrafficPointDefinition(
			pointId,
			type,
			midpointX,
			midpointY,
			midpointZ,
			true,
			type == TrafficPointType.SPAWN ? 40 : null,
			(long) firstNode.getX(),
			(long) firstNode.getY(),
			(long) firstNode.getZ(),
			(long) secondNode.getX(),
			(long) secondNode.getY(),
			(long) secondNode.getZ(),
			null,
			List.of()
		));
		save(level.getServer());
	}

	public static synchronized boolean applyUpdate(String pointId, String action, int delta) {
		final TrafficPointDefinition definition = DEFINITIONS.get(pointId);
		if (definition == null) {
			return false;
		}

		TrafficPointDefinition updated = switch (action) {
			case "enabled" -> copy(definition, !definition.isEnabled(), definition.spawnIntervalTicks(), definition.effectiveVehiclePool());
			case "spawn_interval" -> definition.type() == TrafficPointType.SPAWN ? copy(definition, definition.enabled(), clamp((definition.spawnIntervalTicks() == null ? 40 : definition.spawnIntervalTicks()) + delta, 20, 1200), definition.effectiveVehiclePool()) : definition;
			default -> definition;
		};

		DEFINITIONS.put(pointId, updated);
		if (currentServer != null) {
			save(currentServer);
		}
		return true;
	}

	public static synchronized boolean toggleVehiclePool(String pointId, String vehicleId) {
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

		DEFINITIONS.put(pointId, copy(definition, definition.enabled(), definition.spawnIntervalTicks(), updatedPool));
		if (currentServer != null) {
			save(currentServer);
		}
		return true;
	}

	public static synchronized boolean replaceVehiclePool(String pointId, List<String> vehicleIds) {
		final TrafficPointDefinition definition = DEFINITIONS.get(pointId);
		if (definition == null || definition.type() != TrafficPointType.SPAWN || vehicleIds == null) {
			return false;
		}

		final List<String> updatedPool = vehicleIds.stream()
			.filter(vehicleId -> vehicleId != null && !vehicleId.isBlank())
			.distinct()
			.toList();
		DEFINITIONS.put(pointId, copy(definition, definition.enabled(), definition.spawnIntervalTicks(), updatedPool));
		if (currentServer != null) {
			save(currentServer);
		}
		return true;
	}

	public static synchronized boolean rename(String pointId, String name) {
		final TrafficPointDefinition definition = DEFINITIONS.get(pointId);
		if (definition == null) {
			return false;
		}

		final String trimmedName = name == null ? null : name.trim();
		DEFINITIONS.put(pointId, copyWithName(definition, trimmedName == null || trimmedName.isBlank() ? null : trimmedName));
		if (currentServer != null) {
			save(currentServer);
		}
		return true;
	}

	public static synchronized int refreshConnectorRoutes(String dimensionId, MtrGraph graph, long centerX, long centerZ, int radius) {
		if (graph == null || graph.isEmpty()) {
			return 0;
		}

		int changed = 0;
		for (TrafficPointDefinition definition : List.copyOf(DEFINITIONS.values())) {
			if (!definition.id().startsWith(dimensionId + "|")) {
				continue;
			}

			final long dx = definition.x() - centerX;
			final long dz = definition.z() - centerZ;
			if (dx * dx + dz * dz > (long) radius * radius) {
				continue;
			}

			if (definition.hasConnectorRoute()) {
				continue;
			}

			final Optional<com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge> nearestEdge = nearestEdge(graph, definition);
			if (nearestEdge.isPresent()) {
				putDefinition(copyWithConnectorRoute(definition, nearestEdge.get()));
				changed++;
			}
		}

		if (changed > 0 && currentServer != null) {
			save(currentServer);
		}
		return changed;
	}

	public static synchronized int refreshConnectorRoutes(String dimensionId, MtrGraph graph) {
		if (graph == null || graph.isEmpty()) {
			return 0;
		}

		int repaired = 0;
		for (TrafficPointDefinition definition : List.copyOf(DEFINITIONS.values())) {
			if (!definition.id().startsWith(dimensionId + "|")) {
				continue;
			}

			if (definition.hasConnectorRoute()) {
				continue;
			}

			final Optional<com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge> nearestEdge = nearestEdge(graph, definition);
			if (nearestEdge.isPresent()) {
				putDefinition(copyWithConnectorRoute(definition, nearestEdge.get()));
				repaired++;
			}
		}

		if (repaired > 0 && currentServer != null) {
			save(currentServer);
		}
		return repaired;
	}

	private static TrafficPointDefinition copy(TrafficPointDefinition definition, Boolean enabled, Integer spawnIntervalTicks, List<String> vehiclePool) {
		return new TrafficPointDefinition(
			definition.id(),
			definition.type(),
			definition.x(),
			definition.y(),
			definition.z(),
			enabled,
			spawnIntervalTicks,
			definition.connectorStartX(),
			definition.connectorStartY(),
			definition.connectorStartZ(),
			definition.connectorEndX(),
			definition.connectorEndY(),
			definition.connectorEndZ(),
			definition.name(),
			List.copyOf(vehiclePool)
		);
	}

	private static TrafficPointDefinition copyWithName(TrafficPointDefinition definition, String name) {
		return new TrafficPointDefinition(
			definition.id(),
			definition.type(),
			definition.x(),
			definition.y(),
			definition.z(),
			definition.enabled(),
			definition.spawnIntervalTicks(),
			definition.connectorStartX(),
			definition.connectorStartY(),
			definition.connectorStartZ(),
			definition.connectorEndX(),
			definition.connectorEndY(),
			definition.connectorEndZ(),
			name,
			definition.effectiveVehiclePool()
		);
	}

	private static TrafficPointDefinition copyWithConnectorRoute(TrafficPointDefinition definition, com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge edge) {
		return new TrafficPointDefinition(
			definition.id(),
			definition.type(),
			definition.x(),
			definition.y(),
			definition.z(),
			definition.enabled(),
			definition.spawnIntervalTicks(),
			edge.from().x(),
			edge.from().y(),
			edge.from().z(),
			edge.to().x(),
			edge.to().y(),
			edge.to().z(),
			definition.name(),
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

	// The full MTR rail snapshot is authoritative, including rails with both
	// directions disabled. A routable graph alone cannot prove a rail was deleted.
	public static synchronized List<String> reconcileConnectorPoints(String dimensionId, Set<String> existingRailIds) {
		final List<String> removed = new ArrayList<>();
		for (TrafficPointDefinition definition : List.copyOf(DEFINITIONS.values())) {
			if (definition.hasConnectorRoute() && definition.id().startsWith(dimensionId + "|")
				&& !existingRailIds.contains(connectorKey(definition).railId())) {
				removeDefinition(definition.id());
				removed.add(definition.id());
			}
		}
		saveRemovals(removed);
		return List.copyOf(removed);
	}

	public static synchronized List<String> removeConnectorPoints(String dimensionId, Collection<String> railIds) {
		final List<String> removed = new ArrayList<>();
		for (String railId : railIds) {
			final Set<String> pointIds = POINT_IDS_BY_RAIL.remove(new ConnectorKey(dimensionId, railId));
			if (pointIds != null) {
				for (String pointId : pointIds) {
					if (DEFINITIONS.remove(pointId) != null) {
						removed.add(pointId);
					}
				}
			}
		}
		saveRemovals(removed);
		return List.copyOf(removed);
	}

	private static ConnectorKey connectorKey(TrafficPointDefinition definition) {
		return new ConnectorKey(definition.id().substring(0, definition.id().indexOf('|')),
			TwoPositionsBase.getHexId(
				new Position(definition.connectorStartX(), definition.connectorStartY(), definition.connectorStartZ()),
				new Position(definition.connectorEndX(), definition.connectorEndY(), definition.connectorEndZ())));
	}

	private static void putDefinition(TrafficPointDefinition definition) {
		removeDefinition(definition.id());
		DEFINITIONS.put(definition.id(), definition);
		if (definition.hasConnectorRoute()) {
			POINT_IDS_BY_RAIL.computeIfAbsent(connectorKey(definition), ignored -> new HashSet<>()).add(definition.id());
		}
	}

	private static void removeDefinition(String pointId) {
		final TrafficPointDefinition definition = DEFINITIONS.remove(pointId);
		if (definition != null && definition.hasConnectorRoute()) {
			final ConnectorKey key = connectorKey(definition);
			final Set<String> pointIds = POINT_IDS_BY_RAIL.get(key);
			if (pointIds != null && pointIds.remove(pointId) && pointIds.isEmpty()) {
				POINT_IDS_BY_RAIL.remove(key);
			}
		}
	}

	private static void saveRemovals(List<String> removed) {
		if (!removed.isEmpty()) {
			MTRTrafficAddon.LOGGER.info("Removed {} deleted traffic connector point(s)", removed.size());
			if (currentServer != null) {
				save(currentServer);
			}
		}
	}

	private static synchronized void load(MinecraftServer server) {
		DEFINITIONS.clear();
		POINT_IDS_BY_RAIL.clear();
		for (TrafficPointDefinition definition : WorldJsonStorage.<TrafficPointDefinition>loadList(server, "traffic_connector_points.json", LIST_TYPE, "saved traffic connector points")) {
			putDefinition(definition);
		}
	}

	private static synchronized void save(MinecraftServer server) {
		WorldJsonStorage.saveList(server, "traffic_connector_points.json", DEFINITIONS.values(), "traffic connector points");
	}

}
