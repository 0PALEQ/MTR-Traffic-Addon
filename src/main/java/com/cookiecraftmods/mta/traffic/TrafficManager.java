package com.cookiecraftmods.mta.traffic;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.config.TrafficAddonConfig;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionDefinition;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionRegistry;
import com.cookiecraftmods.mta.traffic.mtr.MtrApiClient;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraph;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphPathFinder;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphRouteResult;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrNodeKey;
import com.cookiecraftmods.mta.traffic.point.TrafficPointDefinition;
import com.cookiecraftmods.mta.traffic.point.TrafficSavedPointRegistry;
import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import com.cookiecraftmods.mta.traffic.rail.MtaExclusiveRailRegistry;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRoute;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRouteSegment;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehiclePosition;
import com.cookiecraftmods.mta.traffic.runtime.TrafficSpacingResolver;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinition;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinitionRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mtr.core.data.PathData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TrafficManager {
	private static final int SNAPSHOT_REFRESH_INTERVAL_TICKS = 200;
	private static final int GRAPH_PRUNE_RADIUS_BLOCKS = 448;
	private static final int SPAWN_DIAGNOSTIC_INTERVAL_TICKS = 100;
	private static final int MTR_VEHICLE_OCCUPANCY_STALE_TICKS = 5;
	private static final int PAUSED_TRAFFIC_OBSTACLE_GRACE_TICKS = 20;
	private static final double MTR_SIGNAL_PATH_LOOKAHEAD_METERS = 512.0D;
	private static final long SIGNAL_TICK_MILLIS = 50L;
	private static final long MTR_FAIL_OPEN_AFTER_NO_TRAFFIC_TICK_MILLIS = 1500L;
	private static final double DEFAULT_TRAFFIC_TICK_DURATION_SECONDS = 1.0D / 20.0D;
	private static final double MAX_TRAFFIC_CATCH_UP_SECONDS = 1.0D;
	private static final double MATERIALIZATION_CLEARANCE_BUFFER_METERS = 2.0D;
	private static final long SIMULATION_INTERVAL_MILLIS = 50L;
	private static final Object SIMULATION_LOCK = new Object();
	private static final List<TrafficVehicle> ACTIVE_VEHICLES = new ArrayList<>();
	private static final Map<Long, MtrVehicleOccupancy> MTR_VEHICLE_OCCUPANCY = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_RENDERED_WALL_MILLIS = new HashMap<>();
	private static final Set<UUID> SKIPPED_VIRTUAL_VEHICLE_IDS = new HashSet<>();
	private static final MtrApiClient MTR_API_CLIENT = new MtrApiClient();
	private static ScheduledExecutorService simulationExecutor;
	private static List<SimulationPlayerSnapshot> playerSnapshots = List.of();
	private static List<TrafficPointDefinition> cachedEnabledSpawns = List.of();
	private static List<TrafficPointDefinition> cachedEnabledDespawns = List.of();
	private static final Map<String, List<VirtualRouteCandidate>> ROUTE_CANDIDATES_BY_SPAWN_ID = new HashMap<>();
	private static boolean initialized;
	private static long lastSnapshotRefreshTick = -SNAPSHOT_REFRESH_INTERVAL_TICKS;
	private static MtrGraph latestGraph;
	private static String latestGraphDimensionId;
	private static long lastServerTick;
	private static long lastTrafficTickWallMillis;
	private static long signalClockTick;
	private static long signalClockWallMillis;
	private static boolean graphRefreshInFlight;
	private static long lastSpawnDiagnosticTick = Long.MIN_VALUE / 4;
	private static String routeCacheSignature = "";

	private TrafficManager() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerLifecycleEvents.SERVER_STARTED.register(TrafficManager::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			stopSimulationExecutor();
			synchronized (SIMULATION_LOCK) {
				ACTIVE_VEHICLES.clear();
				MTR_VEHICLE_OCCUPANCY.clear();
				LAST_RENDERED_WALL_MILLIS.clear();
				SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
				playerSnapshots = List.of();
				cachedEnabledSpawns = List.of();
				cachedEnabledDespawns = List.of();
				ROUTE_CANDIDATES_BY_SPAWN_ID.clear();
				routeCacheSignature = "";
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(TrafficManager::onServerTick);
		initialized = true;
	}

	public static Collection<TrafficVehicle> getActiveVehicles() {
		synchronized (SIMULATION_LOCK) {
			return List.copyOf(ACTIVE_VEHICLES);
		}
	}

	public static Map<String, Integer> countActiveVehiclesBySpawnPointId() {
		final Map<String, Integer> counts = new HashMap<>();
		synchronized (SIMULATION_LOCK) {
			for (TrafficVehicle vehicle : ACTIVE_VEHICLES) {
				if (vehicle.spawnPointId() != null) {
					counts.merge(vehicle.spawnPointId(), 1, Integer::sum);
				}
			}
		}

		return Map.copyOf(counts);
	}

	public static int clearAllVehicles() {
		synchronized (SIMULATION_LOCK) {
			final int clearedVehicles = ACTIVE_VEHICLES.size();
			ACTIVE_VEHICLES.clear();
			LAST_RENDERED_WALL_MILLIS.clear();
			SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
			return clearedVehicles;
		}
	}

	public static void markVehiclesRendered(Collection<UUID> vehicleIds, long wallMillis) {
		if (vehicleIds == null || vehicleIds.isEmpty()) {
			return;
		}

		synchronized (SIMULATION_LOCK) {
			if (ACTIVE_VEHICLES.isEmpty()) {
				return;
			}

			final Set<UUID> activeIds = new HashSet<>();
			for (TrafficVehicle vehicle : ACTIVE_VEHICLES) {
				activeIds.add(vehicle.id());
			}
			for (UUID vehicleId : vehicleIds) {
				if (activeIds.contains(vehicleId)) {
					LAST_RENDERED_WALL_MILLIS.put(vehicleId, wallMillis);
				}
			}
		}
	}

	public static double mtrVehicleBlockedDistance(List<PathData> path, int startIndex, double railProgress, double additionalDistance, int stoppingSpace) {
		if (path == null || path.isEmpty() || startIndex < 0 || startIndex >= path.size()) {
			return -1.0D;
		}
		if (!trafficTicksAreFreshForMtr()) {
			return -1.0D;
		}

		final double lookaheadEnd = railProgress + Math.max(0.0D, additionalDistance) + Math.max(0, stoppingSpace);
		double closestDistance = Double.POSITIVE_INFINITY;
		final long signalTick = currentSignalTick();
		final boolean includeTrafficVehicleObstacles = signalTick - lastServerTick <= PAUSED_TRAFFIC_OBSTACLE_GRACE_TICKS;

		synchronized (SIMULATION_LOCK) {
			for (int i = startIndex; i < path.size(); i++) {
				final PathData pathData = path.get(i);
				if (pathData.getStartDistance() > lookaheadEnd) {
					break;
				}

				if (isRedMtrEntry(pathData, signalTick)) {
					closestDistance = Math.min(closestDistance, stopDistance(railProgress, stoppingSpace, pathData.getEndDistance()));
				}

				if (includeTrafficVehicleObstacles) {
					for (TrafficVehicle vehicle : ACTIVE_VEHICLES) {
						final TrafficRouteSegment segment = vehicle.currentSegment().orElse(null);
						final RailDirectionMatch railDirectionMatch = segment == null ? null : matchRouteRail(pathData, segment);
						if (railDirectionMatch == null) {
							continue;
						}

						final double pathStart = pathData.getStartDistance();
						final double pathEnd = pathData.getEndDistance();
						final double segmentLength = Math.max(0.001D, segment.lengthMeters());
						final double progress = Math.min(1.0D, Math.max(0.0D, vehicle.distanceOnSegmentMeters() / segmentLength));
						final double vehicleCenter = railDirectionMatch.sameDirection()
							? pathStart + (pathEnd - pathStart) * progress
							: pathEnd - (pathEnd - pathStart) * progress;
						final double vehicleHalfLength = vehicle.definition().lengthMeters() * 0.5D + 1.5D;
						final double vehicleStart = vehicleCenter - vehicleHalfLength;
						final double vehicleEnd = vehicleCenter + vehicleHalfLength;
						if (vehicleEnd < railProgress || vehicleStart > lookaheadEnd) {
							continue;
						}

						closestDistance = Math.min(closestDistance, stopDistance(railProgress, stoppingSpace, vehicleStart));
					}
				}
			}
		}

		return Double.isFinite(closestDistance) ? closestDistance : -1.0D;
	}

	public static void recordMtrVehicle(long vehicleId, List<PathData> path, double railProgress, double speedMetersPerMillisecond, double lengthMeters) {
		if (path == null || path.isEmpty()) {
			MTR_VEHICLE_OCCUPANCY.remove(vehicleId);
			return;
		}

		PathData currentPathData = null;
		int currentPathIndex = -1;
		for (int i = 0; i < path.size(); i++) {
			final PathData pathData = path.get(i);
			if (railProgress + 0.001D >= pathData.getStartDistance() && railProgress - 0.001D <= pathData.getEndDistance()) {
				currentPathData = pathData;
				currentPathIndex = i;
				break;
			}
		}

		if (currentPathData == null || currentPathIndex < 0) {
			MTR_VEHICLE_OCCUPANCY.remove(vehicleId);
			return;
		}

		final double segmentLengthMeters = Math.max(0.001D, currentPathData.getEndDistance() - currentPathData.getStartDistance());
		final double distanceOnSegmentMeters = Math.min(segmentLengthMeters, Math.max(0.0D, railProgress - currentPathData.getStartDistance()));
		final List<MtrSignalPathSegment> signalPathSegments = mtrSignalPathSegments(path, currentPathIndex, railProgress);
		MTR_VEHICLE_OCCUPANCY.put(vehicleId, new MtrVehicleOccupancy(
			currentPathData.getHexId(false),
			currentPathData.getHexId(true),
			distanceOnSegmentMeters,
			segmentLengthMeters,
			Math.max(0.0D, lengthMeters),
			Math.max(0.0D, speedMetersPerMillisecond * 3600000.0D),
			lastServerTick,
			signalPathSegments
		));
	}

	public static Optional<MtrVehicleObstacle> closestMtrVehicleObstacle(TrafficVehicle followingVehicle) {
		synchronized (SIMULATION_LOCK) {
			final List<TrafficRouteSegment> followingSegments = followingVehicle.route().segments();
			if (followingSegments.isEmpty() || followingVehicle.segmentIndex() < 0 || followingVehicle.segmentIndex() >= followingSegments.size() || MTR_VEHICLE_OCCUPANCY.isEmpty()) {
				return Optional.empty();
			}

			MtrVehicleObstacle closestObstacle = null;
			double distanceToSegmentStart = -followingVehicle.distanceOnSegmentMeters();
			for (int segmentIndex = followingVehicle.segmentIndex(); segmentIndex < followingSegments.size(); segmentIndex++) {
				final TrafficRouteSegment candidateSegment = followingSegments.get(segmentIndex);
				if (distanceToSegmentStart > 80.0D) {
					break;
				}

				for (MtrVehicleOccupancy occupancy : MTR_VEHICLE_OCCUPANCY.values()) {
					if (lastServerTick - occupancy.lastTick() > MTR_VEHICLE_OCCUPANCY_STALE_TICKS) {
						continue;
					}

					final boolean sameDirection = candidateSegment.connectorId().equals(occupancy.connectorId());
					final boolean oppositeDirection = candidateSegment.connectorId().equals(occupancy.reverseConnectorId());
					if (!sameDirection && !oppositeDirection) {
						continue;
					}

					final double obstacleDistanceOnSegment = sameDirection
						? occupancy.distanceOnSegmentMeters()
						: Math.max(0.0D, occupancy.segmentLengthMeters() - occupancy.distanceOnSegmentMeters());
					final double distanceToObstacle = distanceToSegmentStart + obstacleDistanceOnSegment;
					if (distanceToObstacle <= 0.0D || distanceToObstacle > 80.0D) {
						continue;
					}

					if (closestObstacle == null || distanceToObstacle < closestObstacle.distanceMeters()) {
						closestObstacle = new MtrVehicleObstacle(distanceToObstacle, occupancy.lengthMeters(), occupancy.speedKph());
					}
				}

				distanceToSegmentStart += Math.max(candidateSegment.lengthMeters(), 0.0D);
			}
			return Optional.ofNullable(closestObstacle);
		}
	}

	private static List<MtrSignalVehicle> mtrSignalVehicles() {
		if (MTR_VEHICLE_OCCUPANCY.isEmpty()) {
			return List.of();
		}

		final List<MtrSignalVehicle> signalVehicles = new ArrayList<>(MTR_VEHICLE_OCCUPANCY.size());
		for (MtrVehicleOccupancy occupancy : MTR_VEHICLE_OCCUPANCY.values()) {
			signalVehicles.add(new MtrSignalVehicle(
				occupancy.connectorId(),
				occupancy.reverseConnectorId(),
				occupancy.distanceOnSegmentMeters(),
				occupancy.segmentLengthMeters(),
				occupancy.lengthMeters(),
				occupancy.lastTick(),
				occupancy.signalPathSegments()
			));
		}
		return signalVehicles;
	}

	private static List<MtrSignalPathSegment> mtrSignalPathSegments(List<PathData> path, int currentPathIndex, double railProgress) {
		if (currentPathIndex < 0 || currentPathIndex >= path.size()) {
			return List.of();
		}

		final List<MtrSignalPathSegment> segments = new ArrayList<>();
		for (int i = currentPathIndex; i < path.size(); i++) {
			final PathData pathData = path.get(i);
			final double distanceToSegmentStartMeters = Math.max(0.0D, pathData.getStartDistance() - railProgress);
			if (distanceToSegmentStartMeters > MTR_SIGNAL_PATH_LOOKAHEAD_METERS) {
				break;
			}

			final double segmentLengthMeters = Math.max(0.001D, pathData.getEndDistance() - pathData.getStartDistance());
			final double distanceOnSegmentMeters = i == currentPathIndex
				? Math.min(segmentLengthMeters, Math.max(0.0D, railProgress - pathData.getStartDistance()))
				: 0.0D;
			segments.add(new MtrSignalPathSegment(
				pathData.getHexId(false),
				pathData.getHexId(true),
				distanceToSegmentStartMeters,
				distanceOnSegmentMeters,
				segmentLengthMeters
			));
		}
		return List.copyOf(segments);
	}

	private static void onServerStarted(MinecraftServer server) {
		synchronized (SIMULATION_LOCK) {
			ACTIVE_VEHICLES.clear();
			MTR_VEHICLE_OCCUPANCY.clear();
			LAST_RENDERED_WALL_MILLIS.clear();
			SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
			playerSnapshots = List.of();
			cachedEnabledSpawns = List.of();
			cachedEnabledDespawns = List.of();
			ROUTE_CANDIDATES_BY_SPAWN_ID.clear();
			routeCacheSignature = "";
			latestGraph = null;
			latestGraphDimensionId = null;
			graphRefreshInFlight = false;
			lastSnapshotRefreshTick = -SNAPSHOT_REFRESH_INTERVAL_TICKS;
			lastServerTick = 0;
			lastTrafficTickWallMillis = System.currentTimeMillis();
			signalClockTick = 0;
			signalClockWallMillis = System.currentTimeMillis();
			lastSpawnDiagnosticTick = Long.MIN_VALUE / 4;
		}
		startSimulationExecutor();
	}

	private static void onServerTick(MinecraftServer server) {
		lastServerTick = server.getTickCount();
		updatePlayerSnapshots(server);
		updateCachedTrafficPoints();
		refreshGraphSnapshot(server);
		syncSignalClockToServerTick(lastServerTick);
	}

	private static void simulationTick() {
		synchronized (SIMULATION_LOCK) {
			final long nowMillis = System.currentTimeMillis();
			final double tickDurationSeconds = trafficTickDurationSeconds(nowMillis);
			lastTrafficTickWallMillis = nowMillis;
			materializeVirtualTraffic(nowMillis);
			removeVehiclesOutsideSimulationRange();
			removeUnrenderedVehiclesAfterTimeout(nowMillis);
			MTR_VEHICLE_OCCUPANCY.entrySet().removeIf(entry -> lastServerTick - entry.getValue().lastTick() > MTR_VEHICLE_OCCUPANCY_STALE_TICKS);
			final long signalTick = currentSignalTick();
			TrafficIntersectionRegistry.tickAutoSignals(latestGraphDimensionId, latestGraph, ACTIVE_VEHICLES, mtrSignalVehicles(), signalTick);

			if (ACTIVE_VEHICLES.isEmpty()) {
				return;
			}

			final Map<TrafficVehicle, Double> allowedSpeeds = TrafficSpacingResolver.resolveAllowedSpeeds(ACTIVE_VEHICLES);
			TrafficIntersectionRegistry.applySignalSpeedLimits(ACTIVE_VEHICLES, allowedSpeeds, signalTick);
			ACTIVE_VEHICLES.removeIf(vehicle -> {
				final boolean remove = vehicle.tick(tickDurationSeconds, allowedSpeeds.getOrDefault(vehicle, 0.0D));
				if (remove) {
					LAST_RENDERED_WALL_MILLIS.remove(vehicle.id());
				}
				return remove;
			});
		}
	}

	private static double trafficTickDurationSeconds(long nowMillis) {
		if (lastTrafficTickWallMillis <= 0L) {
			return DEFAULT_TRAFFIC_TICK_DURATION_SECONDS;
		}

		final long elapsedMillis = nowMillis - lastTrafficTickWallMillis;
		if (elapsedMillis <= 0L) {
			return DEFAULT_TRAFFIC_TICK_DURATION_SECONDS;
		}
		return Math.min(MAX_TRAFFIC_CATCH_UP_SECONDS, elapsedMillis / 1000.0D);
	}

	private static void startSimulationExecutor() {
		stopSimulationExecutor();
		simulationExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			final Thread thread = new Thread(runnable, "MTR Traffic Addon Simulation");
			thread.setDaemon(true);
			return thread;
		});
		simulationExecutor.scheduleAtFixedRate(() -> {
			try {
				simulationTick();
			} catch (Exception e) {
				MTRTrafficAddon.LOGGER.error("Traffic simulation tick failed", e);
			}
		}, 0L, SIMULATION_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
	}

	private static void stopSimulationExecutor() {
		final ScheduledExecutorService executor = simulationExecutor;
		simulationExecutor = null;
		if (executor == null) {
			return;
		}

		executor.shutdown();
		try {
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private static void updatePlayerSnapshots(MinecraftServer server) {
		final List<SimulationPlayerSnapshot> snapshots = server.getPlayerList().getPlayers().stream()
			.map(player -> new SimulationPlayerSnapshot(
				player.level().dimension().location().toString(),
				player.getX(),
				player.getZ(),
				server.getPlayerList().getViewDistance()
			))
			.toList();

		synchronized (SIMULATION_LOCK) {
			playerSnapshots = snapshots;
		}
	}

	private static void updateCachedTrafficPoints() {
		synchronized (SIMULATION_LOCK) {
			if (latestGraphDimensionId == null) {
				cachedEnabledSpawns = List.of();
				cachedEnabledDespawns = List.of();
				return;
			}

			cachedEnabledSpawns = TrafficSavedPointRegistry.getByTypeAndDimension(latestGraphDimensionId, TrafficPointType.SPAWN).stream()
				.filter(TrafficPointDefinition::isEnabled)
				.toList();
			cachedEnabledDespawns = TrafficSavedPointRegistry.getByTypeAndDimension(latestGraphDimensionId, TrafficPointType.DESPAWN).stream()
				.filter(TrafficPointDefinition::isEnabled)
				.toList();
			final String updatedSignature = routeCacheSignature(cachedEnabledSpawns, cachedEnabledDespawns);
			if (!updatedSignature.equals(routeCacheSignature)) {
				routeCacheSignature = updatedSignature;
				ROUTE_CANDIDATES_BY_SPAWN_ID.clear();
				SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
			}
		}
	}

	private static void refreshGraphSnapshot(MinecraftServer server) {
		if (server.getTickCount() - lastSnapshotRefreshTick < SNAPSHOT_REFRESH_INTERVAL_TICKS) {
			return;
		}

		lastSnapshotRefreshTick = server.getTickCount();
		final ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
		if (player == null) {
			return;
		}

		if (graphRefreshInFlight) {
			logSpawnDiagnostic("MTR graph refresh skipped: previous internal request is still in flight.");
			return;
		}

		graphRefreshInFlight = true;
		final String requestedDimensionId = player.level().dimension().location().toString();
		final net.minecraft.core.BlockPos requestedPosition = player.blockPosition();
		MTR_API_CLIENT.fetchGraphNearPlayer(player, refreshedGraph -> {
			synchronized (SIMULATION_LOCK) {
				graphRefreshInFlight = false;
				latestGraphDimensionId = requestedDimensionId;
				latestGraph = MtaExclusiveRailRegistry.appendToGraph(latestGraphDimensionId, refreshedGraph.orElse(latestGraph));
				if (refreshedGraph.isPresent()) {
					MTRTrafficAddon.LOGGER.debug("MTR traffic graph refreshed for {} near {}: {} nodes, {} edges", latestGraphDimensionId, requestedPosition, refreshedGraph.get().adjacency().size(), refreshedGraph.get().edges().size());
				} else {
					logSpawnDiagnostic("MTR graph refresh returned no rails near {} in dimension {}; keeping previous graph: {}", requestedPosition, latestGraphDimensionId, latestGraph == null ? "none" : latestGraph.edges().size() + " edges");
				}
				if (latestGraph != null) {
					final int repaired = TrafficSavedPointRegistry.refreshConnectorRoutes(
						latestGraphDimensionId,
						latestGraph,
						requestedPosition.getX(),
						requestedPosition.getZ(),
						GRAPH_PRUNE_RADIUS_BLOCKS
					);
					if (repaired > 0) {
						MTRTrafficAddon.LOGGER.info("Refreshed {} traffic connector route(s)", repaired);
					}
					TrafficIntersectionRegistry.refreshNodes(latestGraphDimensionId, latestGraph);
					cachedEnabledSpawns = TrafficSavedPointRegistry.getByTypeAndDimension(latestGraphDimensionId, TrafficPointType.SPAWN).stream()
						.filter(TrafficPointDefinition::isEnabled)
						.toList();
					cachedEnabledDespawns = TrafficSavedPointRegistry.getByTypeAndDimension(latestGraphDimensionId, TrafficPointType.DESPAWN).stream()
						.filter(TrafficPointDefinition::isEnabled)
						.toList();
					routeCacheSignature = routeCacheSignature(cachedEnabledSpawns, cachedEnabledDespawns);
					ROUTE_CANDIDATES_BY_SPAWN_ID.clear();
					SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
				}
			}
		});
	}

	public static int refreshSavedConnectorRoutesNear(ServerPlayer player) {
		if (player == null || latestGraph == null || latestGraph.isEmpty()) {
			return 0;
		}
		final String dimensionId = player.level().dimension().location().toString();
		if (!dimensionId.equals(latestGraphDimensionId)) {
			return 0;
		}
		final net.minecraft.core.BlockPos playerPosition = player.blockPosition();
		return TrafficSavedPointRegistry.refreshConnectorRoutes(
			dimensionId,
			latestGraph,
			playerPosition.getX(),
			playerPosition.getZ(),
			GRAPH_PRUNE_RADIUS_BLOCKS
		);
	}

	public static int refreshIntersectionNodesNear(ServerPlayer player) {
		if (player == null || latestGraph == null || latestGraph.isEmpty()) {
			return 0;
		}
		final String dimensionId = player.level().dimension().location().toString();
		if (!dimensionId.equals(latestGraphDimensionId)) {
			return 0;
		}
		return TrafficIntersectionRegistry.refreshNodes(dimensionId, latestGraph);
	}

	public static void onExclusiveRailGraphChanged(String dimensionId) {
		if (dimensionId == null) {
			return;
		}

		synchronized (SIMULATION_LOCK) {
			if (!dimensionId.equals(latestGraphDimensionId) || latestGraph == null) {
				ROUTE_CANDIDATES_BY_SPAWN_ID.clear();
				SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
				routeCacheSignature = "";
				return;
			}

			latestGraph = MtaExclusiveRailRegistry.appendToGraph(dimensionId, latestGraph);
			TrafficSavedPointRegistry.refreshConnectorRoutes(dimensionId, latestGraph);
			TrafficIntersectionRegistry.refreshNodes(dimensionId, latestGraph);
			ROUTE_CANDIDATES_BY_SPAWN_ID.clear();
			SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
			routeCacheSignature = "";
		}
	}

	private static void materializeVirtualTraffic(long nowMillis) {
		if (latestGraph == null || latestGraph.isEmpty()) {
			return;
		}
		final Optional<TrafficVehicleDefinition> anyDefinition = TrafficVehicleDefinitionRegistry.getAnyDefinition();
		if (anyDefinition.isEmpty()) {
			logSpawnDiagnostic("Spawn blocked: no traffic vehicle definitions loaded from data/*/traffic_vehicles/*.json.");
			return;
		}
		if (cachedEnabledSpawns.isEmpty() || cachedEnabledDespawns.isEmpty()) {
			logSpawnDiagnostic("Virtual traffic skipped: enabled spawns={}, enabled despawns={} in dimension {}.", cachedEnabledSpawns.size(), cachedEnabledDespawns.size(), latestGraphDimensionId);
			return;
		}

		final Set<UUID> activeIds = new HashSet<>();
		for (TrafficVehicle vehicle : ACTIVE_VEHICLES) {
			activeIds.add(vehicle.id());
		}
		final Set<UUID> consideredVirtualVehicleIds = new HashSet<>();

		for (TrafficPointDefinition spawn : cachedEnabledSpawns) {
			if (!spawn.isEnabled() || spawn.effectiveVehiclePool().isEmpty()) {
				continue;
			}

			final List<VirtualRouteCandidate> routeCandidates = ROUTE_CANDIDATES_BY_SPAWN_ID.computeIfAbsent(spawn.id(), ignored -> buildVirtualRouteCandidates(latestGraph, spawn));
			if (routeCandidates.isEmpty()) {
				continue;
			}

			final long intervalMillis = Math.max(1L, spawn.effectiveSpawnIntervalTicks() * SIGNAL_TICK_MILLIS);
			final long latestDepartureIndex = Math.floorDiv(nowMillis, intervalMillis);
			final int virtualVehicleCount = Math.max(1, spawn.effectiveMaxVehicles());
			for (long departureIndex = latestDepartureIndex; departureIndex > latestDepartureIndex - virtualVehicleCount; departureIndex--) {
				final VirtualRouteCandidate candidate = routeCandidates.get(Math.floorMod(departureIndex, routeCandidates.size()));
				final TrafficVehicleDefinition definition = withSpawnVehiclePoolOverride(anyDefinition.get(), spawn, departureIndex);
				final VirtualVehicleSample sample = sampleVirtualVehicle(candidate.route(), definition, nowMillis - departureIndex * intervalMillis);
				if (sample == null || !isPositionInSimulationRange(latestGraphDimensionId, sample.position().x(), sample.position().z())) {
					continue;
				}

				final UUID vehicleId = virtualVehicleId(spawn.id(), departureIndex);
				consideredVirtualVehicleIds.add(vehicleId);
				if (activeIds.contains(vehicleId) || SKIPPED_VIRTUAL_VEHICLE_IDS.contains(vehicleId)) {
					continue;
				}

				if (!hasMaterializationClearance(candidate.route(), definition, sample)) {
					SKIPPED_VIRTUAL_VEHICLE_IDS.add(vehicleId);
					continue;
				}

				ACTIVE_VEHICLES.add(createTrafficVehicle(definition, candidate, vehicleId, sample));
				LAST_RENDERED_WALL_MILLIS.putIfAbsent(vehicleId, nowMillis);
				activeIds.add(vehicleId);
			}
		}
		SKIPPED_VIRTUAL_VEHICLE_IDS.retainAll(consideredVirtualVehicleIds);
	}

	private static boolean hasMaterializationClearance(TrafficRoute route, TrafficVehicleDefinition definition, VirtualVehicleSample sample) {
		final List<TrafficRouteSegment> segments = route.segments();
		if (segments.isEmpty() || sample.segmentIndex() < 0 || sample.segmentIndex() >= segments.size()) {
			return false;
		}

		final TrafficRouteSegment spawnSegment = segments.get(0);
		final TrafficRouteSegment sampleSegment = segments.get(sample.segmentIndex());
		final double vehicleHalfLength = Math.max(0.0D, definition.lengthMeters()) * 0.5D;
		if (isTrafficSpawnEntryOccupied(spawnSegment, vehicleHalfLength) || isMtrSegmentOccupiedAt(spawnSegment, 0.0D, vehicleHalfLength)) {
			return false;
		}
		return !isTrafficSegmentOccupiedAt(sampleSegment, sample.distanceOnSegmentMeters(), vehicleHalfLength) && !isMtrSegmentOccupiedAt(sampleSegment, sample.distanceOnSegmentMeters(), vehicleHalfLength);
	}

	private static boolean isTrafficSpawnEntryOccupied(TrafficRouteSegment spawnSegment, double candidateHalfLengthMeters) {
		for (TrafficVehicle otherVehicle : ACTIVE_VEHICLES) {
			final TrafficRouteSegment otherSegment = otherVehicle.currentSegment().orElse(null);
			if (otherSegment == null) {
				continue;
			}

			final Double otherDistanceFromSpawnNode = distanceFromNode(
				spawnSegment.startX(),
				spawnSegment.startY(),
				spawnSegment.startZ(),
				otherSegment,
				otherVehicle.distanceOnSegmentMeters()
			);
			if (otherDistanceFromSpawnNode == null) {
				continue;
			}

			final double requiredClearance = candidateHalfLengthMeters
				+ Math.max(0.0D, otherVehicle.definition().lengthMeters()) * 0.5D
				+ MATERIALIZATION_CLEARANCE_BUFFER_METERS;
			if (otherDistanceFromSpawnNode < requiredClearance) {
				return true;
			}
		}
		return false;
	}

	private static boolean isTrafficSegmentOccupiedAt(TrafficRouteSegment candidateSegment, double candidateDistanceMeters, double candidateHalfLengthMeters) {
		for (TrafficVehicle otherVehicle : ACTIVE_VEHICLES) {
			final TrafficRouteSegment otherSegment = otherVehicle.currentSegment().orElse(null);
			if (otherSegment == null) {
				continue;
			}

			final Double otherDistanceInCandidateDirection = distanceOnSamePhysicalSegment(candidateSegment, otherSegment, otherVehicle.distanceOnSegmentMeters());
			if (otherDistanceInCandidateDirection == null) {
				continue;
			}

			final double requiredClearance = candidateHalfLengthMeters
				+ Math.max(0.0D, otherVehicle.definition().lengthMeters()) * 0.5D
				+ MATERIALIZATION_CLEARANCE_BUFFER_METERS;
			if (Math.abs(otherDistanceInCandidateDirection - candidateDistanceMeters) < requiredClearance) {
				return true;
			}
		}
		return false;
	}

	private static boolean isMtrSegmentOccupiedAt(TrafficRouteSegment candidateSegment, double candidateDistanceMeters, double candidateHalfLengthMeters) {
		for (MtrVehicleOccupancy occupancy : MTR_VEHICLE_OCCUPANCY.values()) {
			if (lastServerTick - occupancy.lastTick() > MTR_VEHICLE_OCCUPANCY_STALE_TICKS) {
				continue;
			}

			final double occupiedDistanceMeters;
			if (candidateSegment.connectorId().equals(occupancy.connectorId())) {
				occupiedDistanceMeters = occupancy.distanceOnSegmentMeters();
			} else if (candidateSegment.connectorId().equals(occupancy.reverseConnectorId())) {
				occupiedDistanceMeters = Math.max(0.0D, occupancy.segmentLengthMeters() - occupancy.distanceOnSegmentMeters());
			} else {
				continue;
			}

			final double requiredClearance = candidateHalfLengthMeters
				+ Math.max(0.0D, occupancy.lengthMeters()) * 0.5D
				+ MATERIALIZATION_CLEARANCE_BUFFER_METERS;
			if (Math.abs(occupiedDistanceMeters - candidateDistanceMeters) < requiredClearance) {
				return true;
			}
		}
		return false;
	}

	private static Double distanceOnSamePhysicalSegment(TrafficRouteSegment candidateSegment, TrafficRouteSegment otherSegment, double otherDistanceMeters) {
		if (sameDirectedSegment(candidateSegment, otherSegment)) {
			return otherDistanceMeters;
		}
		if (samePhysicalSegment(candidateSegment, otherSegment)) {
			return Math.max(0.0D, otherSegment.lengthMeters() - otherDistanceMeters);
		}
		return null;
	}

	private static Double distanceFromNode(double nodeX, double nodeY, double nodeZ, TrafficRouteSegment segment, double distanceMeters) {
		if (sameNode(nodeX, nodeY, nodeZ, segment.startX(), segment.startY(), segment.startZ())) {
			return distanceMeters;
		}
		if (sameNode(nodeX, nodeY, nodeZ, segment.endX(), segment.endY(), segment.endZ())) {
			return Math.max(0.0D, segment.lengthMeters() - distanceMeters);
		}
		return null;
	}

	private static boolean sameDirectedSegment(TrafficRouteSegment first, TrafficRouteSegment second) {
		return first.directedConnectorId().equals(second.directedConnectorId());
	}

	private static boolean samePhysicalSegment(TrafficRouteSegment first, TrafficRouteSegment second) {
		return sameNode(first.startX(), first.startY(), first.startZ(), second.endX(), second.endY(), second.endZ())
			&& sameNode(first.endX(), first.endY(), first.endZ(), second.startX(), second.startY(), second.startZ());
	}

	private static boolean sameNode(double firstX, double firstY, double firstZ, double secondX, double secondY, double secondZ) {
		return Double.compare(firstX, secondX) == 0 && Double.compare(firstY, secondY) == 0 && Double.compare(firstZ, secondZ) == 0;
	}

	private static List<VirtualRouteCandidate> buildVirtualRouteCandidates(MtrGraph graph, TrafficPointDefinition spawn) {
		if (!spawn.hasConnectorRoute()) {
			logSpawnDiagnostic("Virtual spawn {} skipped: connector route metadata is missing.", pointSummary(spawn));
			return List.of();
		}

		final List<VirtualRouteCandidate> candidates = new ArrayList<>();
		for (TrafficPointDefinition despawn : cachedEnabledDespawns) {
			if (despawn.id().equals(spawn.id()) || !despawn.hasConnectorRoute()) {
				continue;
			}

			buildConnectorAwareRoute(graph, spawn, despawn)
				.ifPresent(result -> candidates.add(new VirtualRouteCandidate(spawn, despawn, result.route())));
		}

		if (candidates.isEmpty()) {
			logSpawnDiagnostic("Virtual spawn {} skipped: no graph route found to {} compatible despawn(s).", pointSummary(spawn), cachedEnabledDespawns.size());
		}
		return List.copyOf(candidates);
	}

	private static Optional<MtrGraphRouteResult> buildConnectorAwareRoute(MtrGraph graph, TrafficPointDefinition spawn, TrafficPointDefinition despawn) {
		final List<ConnectorTraversal> spawnTraversals = connectorTraversals(graph, spawn);
		final List<ConnectorTraversal> despawnTraversals = connectorTraversals(graph, despawn);
		if (spawnTraversals.isEmpty() || despawnTraversals.isEmpty()) {
			return Optional.empty();
		}
		MtrGraphRouteResult bestResult = null;

		for (ConnectorTraversal spawnTraversal : spawnTraversals) {
			for (ConnectorTraversal despawnTraversal : despawnTraversals) {
				final Optional<MtrGraphRouteResult> middleRouteResult;
				if (spawnTraversal.routeEndNode().equals(despawnTraversal.routeStartNode())) {
					middleRouteResult = Optional.of(new MtrGraphRouteResult(new TrafficRoute(List.of()), 0.0D));
				} else {
					middleRouteResult = MtrGraphPathFinder.findShortestRoute(graph, spawnTraversal.routeEndNode(), despawnTraversal.routeStartNode());
				}

				if (middleRouteResult.isEmpty()) {
					continue;
				}

				final List<TrafficRouteSegment> segments = new ArrayList<>();
				segments.addAll(spawnTraversal.routeSegments());
				segments.addAll(middleRouteResult.get().route().segments());
				segments.addAll(despawnTraversal.routeSegments());
				if (segments.isEmpty()) {
					continue;
				}

				final double totalCostSeconds = spawnTraversal.routeCostSeconds() + middleRouteResult.get().totalCostSeconds() + despawnTraversal.routeCostSeconds();
				if (bestResult == null || totalCostSeconds < bestResult.totalCostSeconds()) {
					bestResult = new MtrGraphRouteResult(new TrafficRoute(segments), totalCostSeconds);
				}
			}
		}

		return Optional.ofNullable(bestResult);
	}

	private static void removeVehiclesOutsideSimulationRange() {
		if (ACTIVE_VEHICLES.isEmpty()) {
			return;
		}
		if (playerSnapshots.isEmpty()) {
			ACTIVE_VEHICLES.clear();
			LAST_RENDERED_WALL_MILLIS.clear();
			SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
			return;
		}

		ACTIVE_VEHICLES.removeIf(vehicle -> {
			final boolean remove = !isVehicleInSimulationRange(vehicle);
			if (remove) {
				LAST_RENDERED_WALL_MILLIS.remove(vehicle.id());
			}
			return remove;
		});
	}

	private static void removeUnrenderedVehiclesAfterTimeout(long nowMillis) {
		if (ACTIVE_VEHICLES.isEmpty()) {
			LAST_RENDERED_WALL_MILLIS.clear();
			return;
		}

		final int lifetimeSeconds = TrafficAddonConfig.trafficVehicleUnrenderedLifetimeSeconds();
		if (lifetimeSeconds <= 0) {
			return;
		}

		final long lifetimeMillis = lifetimeSeconds * 1000L;
		ACTIVE_VEHICLES.removeIf(vehicle -> {
			final long lastRenderedMillis = LAST_RENDERED_WALL_MILLIS.getOrDefault(vehicle.id(), nowMillis);
			final boolean remove = nowMillis - lastRenderedMillis > lifetimeMillis;
			if (remove) {
				LAST_RENDERED_WALL_MILLIS.remove(vehicle.id());
			}
			return remove;
		});
		final Set<UUID> activeIds = new HashSet<>();
		for (TrafficVehicle vehicle : ACTIVE_VEHICLES) {
			activeIds.add(vehicle.id());
		}
		LAST_RENDERED_WALL_MILLIS.keySet().removeIf(id -> !activeIds.contains(id));
	}

	private static boolean isVehicleInSimulationRange(TrafficVehicle vehicle) {
		final String dimensionId = dimensionIdForVehicle(vehicle);
		if (dimensionId == null) {
			return false;
		}

		final TrafficVehiclePosition position = vehicle.currentPosition();
		return isPositionInSimulationRange(dimensionId, position.x(), position.z());
	}

	private static boolean isPositionInSimulationRange(String dimensionId, double x, double z) {
		if (dimensionId == null) {
			return false;
		}

		for (SimulationPlayerSnapshot player : playerSnapshots) {
			if (!dimensionId.equals(player.dimensionId())) {
				continue;
			}

			final double maxDistanceBlocks = TrafficAddonConfig.trafficVehicleSimulationDistanceBlocks(player.viewDistanceChunks());
			final double maxDistanceSquared = maxDistanceBlocks * maxDistanceBlocks;
			final double dx = x - player.x();
			final double dz = z - player.z();
			if (dx * dx + dz * dz <= maxDistanceSquared) {
				return true;
			}
		}
		return false;
	}

	public static boolean isIntersectionInSimulationRange(TrafficIntersectionDefinition definition) {
		if (definition == null || definition.dimensionId() == null) {
			return false;
		}

		synchronized (SIMULATION_LOCK) {
			for (SimulationPlayerSnapshot player : playerSnapshots) {
				if (!definition.dimensionId().equals(player.dimensionId())) {
					continue;
				}

				final double maxDistanceBlocks = TrafficAddonConfig.trafficVehicleSimulationDistanceBlocks(player.viewDistanceChunks());
				final double dx = distanceToRange(player.x(), definition.minX(), definition.maxX());
				final double dz = distanceToRange(player.z(), definition.minZ(), definition.maxZ());
				if (dx * dx + dz * dz <= maxDistanceBlocks * maxDistanceBlocks) {
					return true;
				}
			}
		}
		return false;
	}

	private static VirtualVehicleSample sampleVirtualVehicle(TrafficRoute route, TrafficVehicleDefinition definition, long elapsedMillis) {
		if (elapsedMillis < 0L || route.segments().isEmpty()) {
			return null;
		}

		double remainingSeconds = elapsedMillis / 1000.0D;
		final List<TrafficRouteSegment> segments = route.segments();
		for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
			final TrafficRouteSegment segment = segments.get(segmentIndex);
			final double speedKph = Math.max(1.0D, Math.min(definition.maxSpeedKph(), segment.speedLimitKph()));
			final double speedMetersPerSecond = speedKph / 3.6D;
			final double segmentDurationSeconds = Math.max(0.0D, segment.lengthMeters()) / speedMetersPerSecond;
			if (remainingSeconds <= segmentDurationSeconds) {
				if (isProtectedConnectorMaterializationSegment(segment)) {
					return null;
				}
				final double distanceOnSegmentMeters = Math.min(segment.lengthMeters(), Math.max(0.0D, remainingSeconds * speedMetersPerSecond));
				return new VirtualVehicleSample(
					segmentIndex,
					distanceOnSegmentMeters,
					speedKph,
					sampleRoutePosition(segment, distanceOnSegmentMeters)
				);
			}
			remainingSeconds -= segmentDurationSeconds;
		}
		return null;
	}

	private static boolean isProtectedConnectorMaterializationSegment(TrafficRouteSegment segment) {
		return segment.despawnConnector();
	}

	private static TrafficVehiclePosition sampleRoutePosition(TrafficRouteSegment segment, double distanceMeters) {
		final List<com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint> path = segment.path();
		double remaining = Math.max(0.0D, distanceMeters);
		for (int i = 1; i < path.size(); i++) {
			final com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint previous = path.get(i - 1);
			final com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint next = path.get(i);
			final double length = distance(previous.x(), previous.y(), previous.z(), next.x(), next.y(), next.z());
			if (remaining <= length || i == path.size() - 1) {
				final double progress = length <= 0.0D ? 0.0D : Math.min(1.0D, remaining / length);
				final double x = lerp(previous.x(), next.x(), progress);
				final double y = lerp(previous.y(), next.y(), progress);
				final double z = lerp(previous.z(), next.z(), progress);
				final Orientation orientation = orientation(
					next.x() - previous.x(),
					next.y() - previous.y(),
					next.z() - previous.z()
				);
				return new TrafficVehiclePosition(x, y, z, orientation.yawDegrees(), orientation.pitchDegrees());
			}
			remaining -= length;
		}

		final com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint previous = path.get(path.size() - 2);
		final com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint last = path.get(path.size() - 1);
		final Orientation orientation = orientation(
			last.x() - previous.x(),
			last.y() - previous.y(),
			last.z() - previous.z()
		);
		return new TrafficVehiclePosition(last.x(), last.y(), last.z(), orientation.yawDegrees(), orientation.pitchDegrees());
	}

	private static Orientation orientation(double dx, double dy, double dz) {
		final double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		final float yawDegrees = (float) Math.toDegrees(Math.atan2(dz, dx));
		final float pitchDegrees = (float) Math.toDegrees(Math.atan2(dy, horizontalDistance));
		return new Orientation(yawDegrees, pitchDegrees);
	}

	private static UUID virtualVehicleId(String spawnPointId, long departureIndex) {
		return UUID.nameUUIDFromBytes((spawnPointId + "|" + departureIndex).getBytes(StandardCharsets.UTF_8));
	}

	private static String routeCacheSignature(List<TrafficPointDefinition> spawns, List<TrafficPointDefinition> despawns) {
		final StringBuilder builder = new StringBuilder();
		appendPointSignature(builder, spawns);
		builder.append("||");
		appendPointSignature(builder, despawns);
		builder.append("||mtaRails=");
		builder.append(MtaExclusiveRailRegistry.signature(latestGraphDimensionId));
		return builder.toString();
	}

	private static void appendPointSignature(StringBuilder builder, List<TrafficPointDefinition> points) {
		for (TrafficPointDefinition point : points) {
			builder
				.append(point.id()).append('@')
				.append(point.connectorStartX()).append(',')
				.append(point.connectorStartY()).append(',')
				.append(point.connectorStartZ()).append("->")
				.append(point.connectorEndX()).append(',')
				.append(point.connectorEndY()).append(',')
				.append(point.connectorEndZ()).append(';');
		}
	}

	private static double lerp(double start, double end, double progress) {
		return start + (end - start) * progress;
	}

	private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
		final double dx = x1 - x2;
		final double dy = y1 - y2;
		final double dz = z1 - z2;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static double distanceToRange(double value, double min, double max) {
		if (value < min) {
			return min - value;
		}
		if (value > max) {
			return value - max;
		}
		return 0.0D;
	}

	private static String dimensionIdForVehicle(TrafficVehicle vehicle) {
		final String spawnPointId = vehicle.spawnPointId();
		if (spawnPointId != null) {
			final int separatorIndex = spawnPointId.indexOf('|');
			if (separatorIndex > 0) {
				return spawnPointId.substring(0, separatorIndex);
			}
		}
		return latestGraphDimensionId;
	}

	private static TrafficVehicle createTrafficVehicle(TrafficVehicleDefinition definition, VirtualRouteCandidate candidate, UUID vehicleId, VirtualVehicleSample sample) {
		return new TrafficVehicle(
			vehicleId,
			definition,
			candidate.route(),
			candidate.spawn().id(),
			candidate.despawn().id(),
			sample.segmentIndex(),
			sample.distanceOnSegmentMeters(),
			sample.speedKph()
		);
	}

	private static TrafficVehicleDefinition withSpawnVehiclePoolOverride(TrafficVehicleDefinition definition, TrafficPointDefinition spawn, long departureIndex) {
		if (spawn == null) {
			return definition;
		}

		final List<String> vehiclePool = spawn.effectiveVehiclePool();
		if (vehiclePool.isEmpty()) {
			return definition;
		}

		final int selectedIndex = Math.floorMod(Long.hashCode(departureIndex), vehiclePool.size());
		final String selectedVisualId = vehiclePool.get(selectedIndex);
		if (selectedVisualId == null || selectedVisualId.isBlank() || selectedVisualId.equals(definition.effectiveVisualId())) {
			return definition;
		}

		return new TrafficVehicleDefinition(
			definition.id(),
			definition.type(),
			definition.lengthMeters(),
			definition.maxSpeedKph(),
			definition.spawnWeight(),
			selectedVisualId,
			definition.accelerationMetersPerSecondSquared(),
			definition.brakingMetersPerSecondSquared()
		);
	}

	private static List<ConnectorTraversal> connectorTraversals(MtrGraph graph, TrafficPointDefinition point) {
		if (!point.hasConnectorRoute()) {
			return List.of();
		}

		final MtrNodeKey startNode = new MtrNodeKey(point.connectorStartX(), point.connectorStartY(), point.connectorStartZ());
		final MtrNodeKey endNode = new MtrNodeKey(point.connectorEndX(), point.connectorEndY(), point.connectorEndZ());
		final List<ConnectorTraversal> traversals = new ArrayList<>(2);
		addConnectorTraversal(traversals, graph, startNode, endNode, point);
		if (point.type() == TrafficPointType.SPAWN && !traversals.isEmpty()) {
			return traversals;
		}

		// Existing saved spawn points may have been created with the opposite node order.
		// Keep them usable, but do not prefer the far node when the saved direction exists.
		addConnectorTraversal(traversals, graph, endNode, startNode, point);
		return traversals;
	}

	private static void addConnectorTraversal(List<ConnectorTraversal> traversals, MtrGraph graph, MtrNodeKey startNode, MtrNodeKey endNode, TrafficPointDefinition point) {
		MtrGraphPathFinder.findEdge(graph, startNode, endNode).ifPresent(edge -> traversals.add(new ConnectorTraversal(
			edge.from(),
			edge.to(),
			List.of(toSegment(edge, point.type() == TrafficPointType.SPAWN, point.type() == TrafficPointType.DESPAWN)),
			edge.travelTimeSeconds()
		)));
	}

	private static TrafficRouteSegment toSegment(MtrGraphEdge edge) {
		return toSegment(edge, false, false);
	}

	private static TrafficRouteSegment toSegment(MtrGraphEdge edge, boolean spawnConnector, boolean despawnConnector) {
		return new TrafficRouteSegment(
			edge.railId(),
			edge.lengthMeters(),
			edge.speedLimitKph(),
			edge.from().x(),
			edge.from().y(),
			edge.from().z(),
			edge.to().x(),
			edge.to().y(),
			edge.to().z(),
			spawnConnector,
			despawnConnector,
			edge.signalColors(),
			edge.path()
		);
	}

	private static double stopDistance(double railProgress, int stoppingSpace, double obstacleDistance) {
		return Math.max(0.0D, obstacleDistance - railProgress - Math.max(0, stoppingSpace));
	}

	private static RailDirectionMatch matchRouteRail(PathData pathData, TrafficRouteSegment segment) {
		if (segment.connectorId().equals(pathData.getHexId(false))) {
			return new RailDirectionMatch(true);
		}
		if (segment.connectorId().equals(pathData.getHexId(true))) {
			return new RailDirectionMatch(false);
		}
		return null;
	}

	private static boolean isRedMtrEntry(PathData pathData, long signalTick) {
		if (latestGraphDimensionId == null || latestGraph == null || latestGraph.isEmpty()) {
			return false;
		}

		final String routeRailId = pathData.getHexId(false);
		for (MtrGraphEdge edge : latestGraph.edges()) {
			if (!edge.railId().equals(routeRailId)) {
				continue;
			}
			return TrafficIntersectionRegistry.isRedMtrEntry(
				latestGraphDimensionId,
				edge.from().x(),
				edge.from().y(),
				edge.from().z(),
				edge.to().x(),
				edge.to().y(),
				edge.to().z(),
				signalTick
			);
		}
		return false;
	}

	private static synchronized long currentSignalTick() {
		final long nowMillis = System.currentTimeMillis();
		if (signalClockWallMillis <= 0L) {
			signalClockWallMillis = nowMillis;
			return signalClockTick;
		}

		final long elapsedTicks = Math.max(0L, (nowMillis - signalClockWallMillis) / SIGNAL_TICK_MILLIS);
		if (elapsedTicks > 0L) {
			signalClockTick += elapsedTicks;
			signalClockWallMillis += elapsedTicks * SIGNAL_TICK_MILLIS;
		}
		return signalClockTick;
	}

	private static synchronized void syncSignalClockToServerTick(long serverTick) {
		currentSignalTick();
		if (serverTick > signalClockTick) {
			signalClockTick = serverTick;
			signalClockWallMillis = System.currentTimeMillis();
		}
	}

	public static long signalTick() {
		return currentSignalTick();
	}

	public static boolean trafficTicksAreFreshForMtr() {
		final long lastTickMillis = lastTrafficTickWallMillis;
		return lastTickMillis > 0L && System.currentTimeMillis() - lastTickMillis <= MTR_FAIL_OPEN_AFTER_NO_TRAFFIC_TICK_MILLIS;
	}

	public record MtrVehicleObstacle(double distanceMeters, double lengthMeters, double speedKph) {
	}

	public record MtrSignalVehicle(
		String connectorId,
		String reverseConnectorId,
		double distanceOnSegmentMeters,
		double segmentLengthMeters,
		double lengthMeters,
		long lastTick,
		List<MtrSignalPathSegment> pathSegments
	) {
		public MtrSignalVehicle {
			pathSegments = pathSegments == null ? List.of() : List.copyOf(pathSegments);
		}
	}

	public record MtrSignalPathSegment(
		String connectorId,
		String reverseConnectorId,
		double distanceToSegmentStartMeters,
		double distanceOnSegmentMeters,
		double segmentLengthMeters
	) {
	}

	private record MtrVehicleOccupancy(
		String connectorId,
		String reverseConnectorId,
		double distanceOnSegmentMeters,
		double segmentLengthMeters,
		double lengthMeters,
		double speedKph,
		long lastTick,
		List<MtrSignalPathSegment> signalPathSegments
	) {
		private MtrVehicleOccupancy {
			signalPathSegments = signalPathSegments == null ? List.of() : List.copyOf(signalPathSegments);
		}
	}

	private record RailDirectionMatch(boolean sameDirection) {
	}

	private record VirtualRouteCandidate(
		TrafficPointDefinition spawn,
		TrafficPointDefinition despawn,
		TrafficRoute route
	) {
	}

	private record VirtualVehicleSample(
		int segmentIndex,
		double distanceOnSegmentMeters,
		double speedKph,
		TrafficVehiclePosition position
	) {
	}

	private record Orientation(float yawDegrees, float pitchDegrees) {
	}

	private record SimulationPlayerSnapshot(
		String dimensionId,
		double x,
		double z,
		int viewDistanceChunks
	) {
	}

	private record ConnectorTraversal(
		MtrNodeKey routeStartNode,
		MtrNodeKey routeEndNode,
		List<TrafficRouteSegment> routeSegments,
		double routeCostSeconds
	) {
		private ConnectorTraversal {
			routeSegments = List.copyOf(routeSegments);
		}
	}

	private static void logSpawnDiagnostic(String message, Object... args) {
		if (lastServerTick - lastSpawnDiagnosticTick < SPAWN_DIAGNOSTIC_INTERVAL_TICKS) {
			return;
		}
		lastSpawnDiagnosticTick = lastServerTick;
		MTRTrafficAddon.LOGGER.debug(message, args);
	}

	private static String pointSummary(TrafficPointDefinition point) {
		if (point == null) {
			return "auto";
		}
		return point.type() + " @ " + point.x() + "," + point.y() + "," + point.z();
	}
}
