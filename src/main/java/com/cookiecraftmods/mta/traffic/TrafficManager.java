package com.cookiecraftmods.mta.traffic;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.config.TrafficAddonConfig;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionDefinition;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionRegistry;
import com.cookiecraftmods.mta.traffic.mtr.MtrApiClient;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraph;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphBuilder;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphPathFinder;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphRouteResult;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrNodeKey;
import com.cookiecraftmods.mta.traffic.network.TrafficNetworkVehicleSnapshot;
import com.cookiecraftmods.mta.traffic.point.TrafficPointDefinition;
import com.cookiecraftmods.mta.traffic.point.TrafficSavedPointRegistry;
import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
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
import org.mtr.core.data.Rail;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TrafficManager {
	private static final int SNAPSHOT_REFRESH_INTERVAL_TICKS = 200;
	private static final int TRAFFIC_POINT_CACHE_REFRESH_INTERVAL_TICKS = 20;
	private static final int GRAPH_PRUNE_RADIUS_BLOCKS = 30_000;
	private static final int SPAWN_DIAGNOSTIC_INTERVAL_TICKS = 100;
	private static final long FULL_RAIL_GRAPH_REFRESH_INTERVAL_MILLIS = 5000L;
	private static final long FULL_RAIL_GRAPH_STALE_MILLIS = 15000L;
	private static final int MAX_VIRTUAL_DEPARTURES_PER_SPAWN_SCAN = 2048;
	private static final int MTR_VEHICLE_OCCUPANCY_STALE_TICKS = 20;
	private static final int PAUSED_TRAFFIC_OBSTACLE_GRACE_TICKS = 20;
	private static final double MTR_SIGNAL_PATH_LOOKAHEAD_METERS = 256.0D;
	private static final double MTR_SIGNAL_PATH_SAMPLE_STEP_METERS = 4.0D;
	private static final int MTR_SIGNAL_PATH_MAX_SEGMENTS = 64;
	private static final int MTR_SIGNAL_PATH_MAX_POINTS = 72;
	private static final double MTR_SIGNAL_GEOMETRY_CACHE_WINDOW_METERS = 256.0D;
	private static final int MTR_SIGNAL_GEOMETRY_MAX_CACHE_WINDOWS = 4096;
	private static final long SIGNAL_TICK_MILLIS = 50L;
	private static final long MTR_FAIL_OPEN_AFTER_NO_TRAFFIC_TICK_MILLIS = 1500L;
	private static final double DEFAULT_TRAFFIC_TICK_DURATION_SECONDS = 1.0D / 20.0D;
	private static final double MAX_TRAFFIC_CATCH_UP_SECONDS = 1.0D;
	private static final double MATERIALIZATION_CLEARANCE_BUFFER_METERS = 2.0D;
	private static final double SPAWN_CONNECTED_NODE_CLEARANCE_METERS = 6.0D;
	private static final double SPAWN_TRACK_MATERIALIZATION_OFFSET_METERS = 8.0D;
	private static final long SIMULATION_INTERVAL_MILLIS = 50L;
	private static final long MATERIALIZATION_SCAN_INTERVAL_MILLIS = 250L;
	private static final Object SIMULATION_LOCK = new Object();
	private static final List<TrafficVehicle> ACTIVE_VEHICLES = new ArrayList<>();
	private static volatile List<TrafficVehicle> activeVehicleSnapshot = List.of();
	private static volatile List<TrafficNetworkVehicleSnapshot> activeNetworkVehicleSnapshot = List.of();
	private static volatile Map<String, List<IndexedTrafficVehicle>> activeTrafficByConnector = Map.of();
	private static final Map<Long, MtrVehicleOccupancy> MTR_VEHICLE_OCCUPANCY = new ConcurrentHashMap<>();
	private static final Map<Long, MtrVehiclePathState> MTR_VEHICLE_PATH_STATES = new ConcurrentHashMap<>();
	private static final Map<MtrPathGeometryKey, CachedMtrPathGeometry> MTR_PATH_GEOMETRY_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<MtrPathGeometryKey, CachedMtrPathGeometry> eldest) {
			return size() > MTR_SIGNAL_GEOMETRY_MAX_CACHE_WINDOWS;
		}
	});
	private static volatile Map<String, List<DirectedMtrOccupancy>> mtrOccupancyByConnector = Map.of();
	private static final Map<UUID, Long> LAST_RENDERED_WALL_MILLIS = new ConcurrentHashMap<>();
	private static final Set<UUID> SKIPPED_VIRTUAL_VEHICLE_IDS = ConcurrentHashMap.newKeySet();
	private static final MtrApiClient MTR_API_CLIENT = new MtrApiClient();
	private static ScheduledExecutorService simulationExecutor;
	private static ExecutorService graphBuildExecutor;
	private static volatile List<SimulationPlayerSnapshot> playerSnapshots = List.of();
	private static volatile MaterializationSnapshot materializationSnapshot = new MaterializationSnapshot(null, null, List.of(), List.of());
	private static final Map<String, List<VirtualRouteCandidate>> ROUTE_CANDIDATES_BY_SPAWN_ID = new HashMap<>();
	private static final Map<VirtualRouteCandidate, CachedVirtualRouteTiming> VIRTUAL_ROUTE_TIMINGS = new IdentityHashMap<>();
	private static boolean initialized;
	private static long lastSnapshotRefreshTick = -SNAPSHOT_REFRESH_INTERVAL_TICKS;
	private static volatile MtrGraph latestGraph;
	private static volatile String latestGraphDimensionId;
	private static volatile long lastServerTick;
	private static volatile long lastTrafficTickWallMillis;
	private static long lastMaterializationScanWallMillis;
	private static long signalClockTick;
	private static long signalClockWallMillis;
	private static boolean graphRefreshInFlight;
	private static long lastFullRailGraphRefreshWallMillis;
	private static volatile long lastFullRailGraphSeenWallMillis;
	private static boolean fullGraphRefreshInFlight;
	private static volatile boolean graphBuildAcceptingTasks;
	private static volatile long graphBuildGeneration;
	private static RailGraphSignature submittedRailGraphSignature;
	private static RailGraphSignature appliedRailGraphSignature;
	private static volatile PendingFullGraphRefresh pendingFullGraphRefresh;
	private static long lastSpawnDiagnosticTick = Long.MIN_VALUE / 4;
	private static String routeCacheSignature = "";
	private static volatile String pendingRouteCacheSignature = "";
	private static volatile long routeCacheGraphVersion;

	private TrafficManager() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerLifecycleEvents.SERVER_STARTED.register(TrafficManager::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			stopSimulationExecutor();
			stopGraphBuildExecutor();
			MTR_API_CLIENT.shutdown();
			synchronized (SIMULATION_LOCK) {
				ACTIVE_VEHICLES.clear();
				publishActiveVehicleSnapshot();
				MTR_VEHICLE_OCCUPANCY.clear();
				MTR_VEHICLE_PATH_STATES.clear();
				MTR_PATH_GEOMETRY_CACHE.clear();
				mtrOccupancyByConnector = Map.of();
				LAST_RENDERED_WALL_MILLIS.clear();
				SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
				playerSnapshots = List.of();
				materializationSnapshot = new MaterializationSnapshot(null, null, List.of(), List.of());
				ROUTE_CANDIDATES_BY_SPAWN_ID.clear();
				VIRTUAL_ROUTE_TIMINGS.clear();
				routeCacheSignature = "";
				pendingRouteCacheSignature = "";
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(TrafficManager::onServerTick);
		initialized = true;
	}

	public static Collection<TrafficVehicle> getActiveVehicles() {
		return activeVehicleSnapshot;
	}

	public static List<TrafficNetworkVehicleSnapshot> getActiveNetworkVehicleSnapshot() {
		return activeNetworkVehicleSnapshot;
	}

	public static Map<String, Integer> countActiveVehiclesBySpawnPointId() {
		final Map<String, Integer> counts = new HashMap<>();
		for (TrafficVehicle vehicle : activeVehicleSnapshot) {
			if (vehicle.spawnPointId() != null) {
				counts.merge(vehicle.spawnPointId(), 1, Integer::sum);
			}
		}

		return Map.copyOf(counts);
	}

	public static int clearAllVehicles() {
		synchronized (SIMULATION_LOCK) {
			final int clearedVehicles = ACTIVE_VEHICLES.size();
			ACTIVE_VEHICLES.clear();
			publishActiveVehicleSnapshot();
			LAST_RENDERED_WALL_MILLIS.clear();
			SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
			return clearedVehicles;
		}
	}

	public static void markVehiclesRendered(Collection<UUID> vehicleIds, long wallMillis) {
		if (vehicleIds == null || vehicleIds.isEmpty()) {
			return;
		}

		final Set<UUID> activeIds = new HashSet<>();
		for (TrafficVehicle vehicle : activeVehicleSnapshot) {
			activeIds.add(vehicle.id());
		}
		if (activeIds.isEmpty()) {
			return;
		}

		for (UUID vehicleId : vehicleIds) {
			if (activeIds.contains(vehicleId)) {
				LAST_RENDERED_WALL_MILLIS.put(vehicleId, wallMillis);
			}
		}
	}

	public static void updateFullMtrRailGraph(String dimensionId, Collection<Rail> rails) {
		if (!graphBuildAcceptingTasks || dimensionId == null || dimensionId.isBlank() || rails == null || rails.isEmpty()) {
			return;
		}
		final String normalizedDimensionId = normalizeMtrDimensionId(dimensionId);

		final long nowMillis = System.currentTimeMillis();
		final long buildGeneration;
		synchronized (SIMULATION_LOCK) {
			if (!graphBuildAcceptingTasks) {
				return;
			}
			lastFullRailGraphSeenWallMillis = nowMillis;
			if (fullGraphRefreshInFlight || nowMillis - lastFullRailGraphRefreshWallMillis < FULL_RAIL_GRAPH_REFRESH_INTERVAL_MILLIS) {
				return;
			}
			lastFullRailGraphRefreshWallMillis = nowMillis;
			fullGraphRefreshInFlight = true;
			buildGeneration = graphBuildGeneration;
		}

		final List<MtrGraphBuilder.RailSnapshot> railSnapshots;
		try {
			railSnapshots = MtrGraphBuilder.snapshotRails(rails);
		} catch (Exception e) {
			synchronized (SIMULATION_LOCK) {
				fullGraphRefreshInFlight = false;
			}
			MTRTrafficAddon.LOGGER.warn("Could not snapshot full MTR rail graph", e);
			return;
		}
		final RailGraphSignature signature = railGraphSignature(normalizedDimensionId, railSnapshots);
		synchronized (SIMULATION_LOCK) {
			if (signature.equals(submittedRailGraphSignature)) {
				fullGraphRefreshInFlight = false;
				return;
			}
			submittedRailGraphSignature = signature;
		}

		try {
			graphBuildExecutor().execute(() -> buildFullGraphAsync(buildGeneration, signature, railSnapshots));
		} catch (RejectedExecutionException e) {
			synchronized (SIMULATION_LOCK) {
				fullGraphRefreshInFlight = false;
				if (signature.equals(submittedRailGraphSignature)) {
					submittedRailGraphSignature = appliedRailGraphSignature;
				}
			}
			MTRTrafficAddon.LOGGER.debug("Full MTR graph build was rejected during shutdown", e);
		}
	}

	private static void buildFullGraphAsync(long buildGeneration, RailGraphSignature signature, List<MtrGraphBuilder.RailSnapshot> railSnapshots) {
		try {
			final MtrGraph fullGraph = MtrGraphBuilder.buildFromRailSnapshots(railSnapshots);
			synchronized (SIMULATION_LOCK) {
				if (buildGeneration != graphBuildGeneration || !graphBuildAcceptingTasks) {
					return;
				}
				if (fullGraph.isEmpty()) {
					if (signature.equals(submittedRailGraphSignature)) {
						submittedRailGraphSignature = appliedRailGraphSignature;
					}
				} else {
					pendingFullGraphRefresh = new PendingFullGraphRefresh(signature, fullGraph);
				}
			}
		} catch (Exception e) {
			MTRTrafficAddon.LOGGER.warn("Could not build full MTR traffic graph from immutable rail snapshot", e);
			synchronized (SIMULATION_LOCK) {
				if (buildGeneration == graphBuildGeneration && signature.equals(submittedRailGraphSignature)) {
					submittedRailGraphSignature = appliedRailGraphSignature;
				}
			}
		} finally {
			synchronized (SIMULATION_LOCK) {
				if (buildGeneration == graphBuildGeneration) {
					fullGraphRefreshInFlight = false;
				}
			}
		}
	}

	private static void applyPendingFullGraphRefresh() {
		final PendingFullGraphRefresh pendingRefresh = pendingFullGraphRefresh;
		if (pendingRefresh == null) {
			return;
		}

		synchronized (SIMULATION_LOCK) {
			if (pendingFullGraphRefresh != pendingRefresh) {
				return;
			}
			pendingFullGraphRefresh = null;
			latestGraphDimensionId = pendingRefresh.signature().dimensionId();
			latestGraph = pendingRefresh.graph();
			appliedRailGraphSignature = pendingRefresh.signature();
			MTR_PATH_GEOMETRY_CACHE.clear();
			TrafficSavedPointRegistry.refreshConnectorRoutes(latestGraphDimensionId, latestGraph);
			TrafficIntersectionRegistry.refreshNodes(latestGraphDimensionId, latestGraph);
			final List<TrafficPointDefinition> enabledSpawns = TrafficSavedPointRegistry.getByTypeAndDimension(latestGraphDimensionId, TrafficPointType.SPAWN).stream()
				.filter(TrafficPointDefinition::isEnabled)
				.toList();
			final List<TrafficPointDefinition> enabledDespawns = TrafficSavedPointRegistry.getByTypeAndDimension(latestGraphDimensionId, TrafficPointType.DESPAWN).stream()
				.filter(TrafficPointDefinition::isEnabled)
				.toList();
			materializationSnapshot = new MaterializationSnapshot(latestGraph, latestGraphDimensionId, enabledSpawns, enabledDespawns);
			routeCacheGraphVersion++;
			pendingRouteCacheSignature = routeCacheSignature(enabledSpawns, enabledDespawns, routeCacheGraphVersion);
		}
	}

	private static String normalizeMtrDimensionId(String dimensionId) {
		final int namespaceSeparator = dimensionId.indexOf('/');
		return namespaceSeparator <= 0 || dimensionId.indexOf(':') >= 0
			? dimensionId
			: dimensionId.substring(0, namespaceSeparator) + ":" + dimensionId.substring(namespaceSeparator + 1);
	}

	private static RailGraphSignature railGraphSignature(String dimensionId, List<MtrGraphBuilder.RailSnapshot> railSnapshots) {
		long contentSum = 0L;
		long contentXor = 0L;
		for (MtrGraphBuilder.RailSnapshot railSnapshot : railSnapshots) {
			long mixedHash = Integer.toUnsignedLong(railSnapshot.hashCode());
			mixedHash ^= mixedHash >>> 33;
			mixedHash *= 0xff51afd7ed558ccdL;
			mixedHash ^= mixedHash >>> 33;
			mixedHash *= 0xc4ceb9fe1a85ec53L;
			mixedHash ^= mixedHash >>> 33;
			contentSum += mixedHash;
			contentXor ^= Long.rotateLeft(mixedHash, (int) (mixedHash & 63L));
		}
		return new RailGraphSignature(dimensionId, railSnapshots.size(), contentSum, contentXor);
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

		final Map<String, List<IndexedTrafficVehicle>> trafficByConnector = activeTrafficByConnector;
		for (int i = startIndex; i < path.size(); i++) {
			final PathData pathData = path.get(i);
			if (pathData.getStartDistance() > lookaheadEnd) {
				break;
			}

			if (isRedMtrEntry(pathData, signalTick)) {
				closestDistance = Math.min(closestDistance, stopDistance(railProgress, stoppingSpace, pathData.getEndDistance()));
			}

			final String pathForwardId = pathData.getHexId(false);
			final String pathReverseId = pathData.getHexId(true);
			final List<IndexedTrafficVehicle> indexedVehicles = trafficByConnector.getOrDefault(pathForwardId, trafficByConnector.getOrDefault(pathReverseId, List.of()));
			for (IndexedTrafficVehicle indexedVehicle : indexedVehicles) {
				final TrafficRouteSegment segment = indexedVehicle.segment();
				final RailDirectionMatch railDirectionMatch = matchRouteRail(pathForwardId, pathReverseId, indexedVehicle);
				if (railDirectionMatch == null) {
					continue;
				}

				final double pathStart = pathData.getStartDistance();
				final double pathEnd = pathData.getEndDistance();
				final double segmentLength = Math.max(0.001D, segment.lengthMeters());
				final double progress = Math.min(1.0D, Math.max(0.0D, indexedVehicle.distanceOnSegmentMeters() / segmentLength));
				final double vehicleCenter = railDirectionMatch.sameDirection()
					? pathStart + (pathEnd - pathStart) * progress
					: pathEnd - (pathEnd - pathStart) * progress;
				final double vehicleHalfLength = indexedVehicle.lengthMeters() * 0.5D + 1.5D;
				final double vehicleStart = vehicleCenter - vehicleHalfLength;
				final double vehicleEnd = vehicleCenter + vehicleHalfLength;
				if (vehicleEnd < railProgress || vehicleStart > lookaheadEnd) {
					continue;
				}

				closestDistance = Math.min(closestDistance, stopDistance(railProgress, stoppingSpace, vehicleStart));
			}
		}

		return Double.isFinite(closestDistance) ? closestDistance : -1.0D;
	}

	public static void recordMtrVehicle(long vehicleId, List<PathData> path, double railProgress, double speedMetersPerMillisecond, double lengthMeters) {
		if (path == null || path.isEmpty() || !Double.isFinite(railProgress)) {
			MTR_VEHICLE_OCCUPANCY.remove(vehicleId);
			MTR_VEHICLE_PATH_STATES.remove(vehicleId);
			return;
		}

		final MtrVehiclePathState previousState = MTR_VEHICLE_PATH_STATES.get(vehicleId);
		final int currentPathIndex = findCurrentPathIndex(path, railProgress, previousState != null && previousState.path() == path ? previousState.pathIndex() : -1);
		if (currentPathIndex < 0) {
			MTR_VEHICLE_OCCUPANCY.remove(vehicleId);
			MTR_VEHICLE_PATH_STATES.remove(vehicleId);
			return;
		}
		final PathData currentPathData = path.get(currentPathIndex);

		final double segmentLengthMeters = Math.max(0.001D, currentPathData.getEndDistance() - currentPathData.getStartDistance());
		final double distanceOnSegmentMeters = Math.min(segmentLengthMeters, Math.max(0.0D, railProgress - currentPathData.getStartDistance()));
		double currentX = Double.NaN;
		double currentY = Double.NaN;
		double currentZ = Double.NaN;
		try {
			final org.mtr.core.tool.Vector currentPosition = currentPathData.getPosition(distanceOnSegmentMeters);
			currentX = currentPosition.x;
			currentY = currentPosition.y;
			currentZ = currentPosition.z;
		} catch (Exception ignored) {
		}
		final long geometryBucket = (long) Math.floor(railProgress / MTR_SIGNAL_PATH_SAMPLE_STEP_METERS);
		final boolean reuseSignalPath = previousState != null
			&& previousState.path() == path
			&& previousState.pathIndex() == currentPathIndex
			&& previousState.geometryBucket() == geometryBucket;
		final List<MtrSignalPathSegment> signalPathSegments;
		if (reuseSignalPath) {
			signalPathSegments = previousState.signalPathSegments();
		} else {
			signalPathSegments = mtrSignalPathSegments(path, currentPathIndex, railProgress, currentX, currentY, currentZ);
			MTR_VEHICLE_PATH_STATES.put(vehicleId, new MtrVehiclePathState(path, currentPathIndex, geometryBucket, signalPathSegments));
		}
		final double speedKph = Double.isFinite(speedMetersPerMillisecond) ? Math.max(0.0D, speedMetersPerMillisecond * 3600000.0D) : 0.0D;
		final double safeLengthMeters = Double.isFinite(lengthMeters) ? Math.max(0.0D, lengthMeters) : 0.0D;
		MTR_VEHICLE_OCCUPANCY.put(vehicleId, new MtrVehicleOccupancy(
			currentPathData.getHexId(false),
			currentPathData.getHexId(true),
			distanceOnSegmentMeters,
			segmentLengthMeters,
			safeLengthMeters,
			speedKph,
			currentSignalTick(),
			currentX,
			currentY,
			currentZ,
			signalPathSegments
		));
	}

	private static int findCurrentPathIndex(List<PathData> path, double railProgress, int hintIndex) {
		if (hintIndex >= 0 && hintIndex < path.size() && pathContainsProgress(path.get(hintIndex), railProgress)) {
			return hintIndex;
		}
		if (hintIndex + 1 >= 0 && hintIndex + 1 < path.size() && pathContainsProgress(path.get(hintIndex + 1), railProgress)) {
			return hintIndex + 1;
		}
		if (hintIndex > 0 && pathContainsProgress(path.get(hintIndex - 1), railProgress)) {
			return hintIndex - 1;
		}

		int low = 0;
		int high = path.size() - 1;
		while (low <= high) {
			final int middle = (low + high) >>> 1;
			final PathData candidate = path.get(middle);
			if (railProgress < candidate.getStartDistance() - 0.001D) {
				high = middle - 1;
			} else if (railProgress > candidate.getEndDistance() + 0.001D) {
				low = middle + 1;
			} else {
				return middle;
			}
		}
		return -1;
	}

	private static boolean pathContainsProgress(PathData pathData, double railProgress) {
		return railProgress + 0.001D >= pathData.getStartDistance() && railProgress - 0.001D <= pathData.getEndDistance();
	}

	public static Optional<MtrVehicleObstacle> closestMtrVehicleObstacle(TrafficVehicle followingVehicle) {
		final List<TrafficRouteSegment> followingSegments = followingVehicle.route().segments();
		final Map<String, List<DirectedMtrOccupancy>> occupancyIndex = mtrOccupancyByConnector;
		if (followingSegments.isEmpty() || followingVehicle.segmentIndex() < 0 || followingVehicle.segmentIndex() >= followingSegments.size() || occupancyIndex.isEmpty()) {
			return Optional.empty();
		}

		MtrVehicleObstacle closestObstacle = null;
		double distanceToSegmentStart = -followingVehicle.distanceOnSegmentMeters();
		for (int segmentIndex = followingVehicle.segmentIndex(); segmentIndex < followingSegments.size(); segmentIndex++) {
			final TrafficRouteSegment candidateSegment = followingSegments.get(segmentIndex);
			if (distanceToSegmentStart > 80.0D) {
				break;
			}

			for (DirectedMtrOccupancy directedOccupancy : occupancyIndex.getOrDefault(candidateSegment.connectorId(), List.of())) {
				final MtrVehicleOccupancy occupancy = directedOccupancy.occupancy();
				final double obstacleDistanceOnSegment = directedOccupancy.sameDirection()
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

	private static void rebuildMtrOccupancyIndex() {
		if (MTR_VEHICLE_OCCUPANCY.isEmpty()) {
			mtrOccupancyByConnector = Map.of();
			return;
		}
		final Map<String, List<DirectedMtrOccupancy>> mutableIndex = new HashMap<>();
		for (MtrVehicleOccupancy occupancy : MTR_VEHICLE_OCCUPANCY.values()) {
			mutableIndex.computeIfAbsent(occupancy.connectorId(), ignored -> new ArrayList<>()).add(new DirectedMtrOccupancy(occupancy, true));
			if (!occupancy.reverseConnectorId().equals(occupancy.connectorId())) {
				mutableIndex.computeIfAbsent(occupancy.reverseConnectorId(), ignored -> new ArrayList<>()).add(new DirectedMtrOccupancy(occupancy, false));
			}
		}
		final Map<String, List<DirectedMtrOccupancy>> immutableIndex = new HashMap<>();
		mutableIndex.forEach((connectorId, occupancies) -> immutableIndex.put(connectorId, List.copyOf(occupancies)));
		mtrOccupancyByConnector = Map.copyOf(immutableIndex);
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
				occupancy.speedKph(),
				occupancy.lastTick(),
				occupancy.currentX(),
				occupancy.currentY(),
				occupancy.currentZ(),
				occupancy.signalPathSegments()
			));
		}
		return signalVehicles;
	}

	private static List<MtrSignalPathSegment> mtrSignalPathSegments(List<PathData> path, int currentPathIndex, double railProgress, double currentX, double currentY, double currentZ) {
		if (currentPathIndex < 0 || currentPathIndex >= path.size()) {
			return List.of();
		}

		final List<MtrSignalPathSegment> segments = new ArrayList<>();
		int sampledPointCount = 0;
		for (int i = currentPathIndex; i < path.size(); i++) {
			if (segments.size() >= MTR_SIGNAL_PATH_MAX_SEGMENTS || sampledPointCount >= MTR_SIGNAL_PATH_MAX_POINTS) {
				break;
			}
			final PathData pathData = path.get(i);
			final double distanceToSegmentStartMeters = Math.max(0.0D, pathData.getStartDistance() - railProgress);
			if (!Double.isFinite(distanceToSegmentStartMeters) || distanceToSegmentStartMeters > MTR_SIGNAL_PATH_LOOKAHEAD_METERS) {
				break;
			}

			final double segmentLengthMeters = Math.max(0.001D, pathData.getEndDistance() - pathData.getStartDistance());
			if (!Double.isFinite(segmentLengthMeters)) {
				continue;
			}
			final double distanceOnSegmentMeters = i == currentPathIndex
				? Math.min(segmentLengthMeters, Math.max(0.0D, railProgress - pathData.getStartDistance()))
				: 0.0D;
			final List<MtrSignalPathPoint> pathPoints = new ArrayList<>();
			final double remainingLookaheadMeters = Math.max(0.0D, MTR_SIGNAL_PATH_LOOKAHEAD_METERS - distanceToSegmentStartMeters);
			final double sampleEndDistance = Math.min(segmentLengthMeters, distanceOnSegmentMeters + remainingLookaheadMeters);
			if (i == currentPathIndex && Double.isFinite(currentX) && Double.isFinite(currentY) && Double.isFinite(currentZ)) {
				pathPoints.add(new MtrSignalPathPoint(currentX, currentY, currentZ));
				sampledPointCount++;
			}
			for (MtrSampledPathPoint sampledPoint : cachedMtrPathGeometry(pathData, segmentLengthMeters, distanceOnSegmentMeters, sampleEndDistance)) {
				if (sampledPoint.distanceMeters() < distanceOnSegmentMeters - 0.001D || sampledPoint.distanceMeters() > sampleEndDistance + 0.001D) {
					continue;
				}
				if (!pathPoints.isEmpty() && Math.abs(sampledPoint.distanceMeters() - distanceOnSegmentMeters) <= 0.001D) {
					continue;
				}
				if (sampledPointCount >= MTR_SIGNAL_PATH_MAX_POINTS) {
					break;
				}
				pathPoints.add(sampledPoint.point());
				sampledPointCount++;
			}
			segments.add(new MtrSignalPathSegment(
				pathData.getHexId(false),
				pathData.getHexId(true),
				distanceToSegmentStartMeters,
				distanceOnSegmentMeters,
				segmentLengthMeters,
				pathPoints
			));
		}
		return List.copyOf(segments);
	}

	private static List<MtrSampledPathPoint> cachedMtrPathGeometry(PathData pathData, double segmentLengthMeters, double startDistanceMeters, double endDistanceMeters) {
		final long firstWindow = Math.max(0L, (long) Math.floor(startDistanceMeters / MTR_SIGNAL_GEOMETRY_CACHE_WINDOW_METERS));
		final double lastSampleDistance = endDistanceMeters > startDistanceMeters ? Math.nextDown(endDistanceMeters) : endDistanceMeters;
		final long lastWindow = Math.max(firstWindow, (long) Math.floor(lastSampleDistance / MTR_SIGNAL_GEOMETRY_CACHE_WINDOW_METERS));
		final String forwardId = pathData.getHexId(false);
		final String reverseId = pathData.getHexId(true);
		final long segmentLengthBits = Double.doubleToLongBits(segmentLengthMeters);
		final List<MtrSampledPathPoint> points = new ArrayList<>();
		for (long window = firstWindow; window <= lastWindow; window++) {
			final long windowIndex = window;
			final MtrPathGeometryKey cacheKey = new MtrPathGeometryKey(forwardId, reverseId, segmentLengthBits, windowIndex);
			final CachedMtrPathGeometry geometry = MTR_PATH_GEOMETRY_CACHE.computeIfAbsent(
				cacheKey,
				ignored -> sampleMtrPathGeometry(pathData, segmentLengthMeters, windowIndex)
			);
			for (MtrSampledPathPoint point : geometry.points()) {
				if (points.isEmpty() || Math.abs(points.get(points.size() - 1).distanceMeters() - point.distanceMeters()) > 0.001D) {
					points.add(point);
				}
			}
			if (window == Long.MAX_VALUE) {
				break;
			}
		}
		return points;
	}

	private static CachedMtrPathGeometry sampleMtrPathGeometry(PathData pathData, double segmentLengthMeters, long windowIndex) {
		final double windowStartDistance = Math.min(segmentLengthMeters, windowIndex * MTR_SIGNAL_GEOMETRY_CACHE_WINDOW_METERS);
		final double windowEndDistance = Math.min(segmentLengthMeters, windowStartDistance + MTR_SIGNAL_GEOMETRY_CACHE_WINDOW_METERS);
		final double windowLengthMeters = Math.max(0.0D, windowEndDistance - windowStartDistance);
		final int pointCount = Math.max(2, (int) Math.ceil(windowLengthMeters / MTR_SIGNAL_PATH_SAMPLE_STEP_METERS) + 1);
		final List<MtrSampledPathPoint> points = new ArrayList<>(pointCount);
		try {
			for (int i = 0; i < pointCount; i++) {
				final double distance = windowStartDistance + windowLengthMeters * i / (pointCount - 1.0D);
				final org.mtr.core.tool.Vector position = pathData.getPosition(distance);
				points.add(new MtrSampledPathPoint(distance, new MtrSignalPathPoint(position.x, position.y, position.z)));
			}
		} catch (Exception ignored) {
			return new CachedMtrPathGeometry(List.of());
		}
		return new CachedMtrPathGeometry(points);
	}

	private static void onServerStarted(MinecraftServer server) {
		MTR_API_CLIENT.start();
		synchronized (SIMULATION_LOCK) {
			ACTIVE_VEHICLES.clear();
			publishActiveVehicleSnapshot();
			MTR_VEHICLE_OCCUPANCY.clear();
			MTR_VEHICLE_PATH_STATES.clear();
			MTR_PATH_GEOMETRY_CACHE.clear();
			mtrOccupancyByConnector = Map.of();
			LAST_RENDERED_WALL_MILLIS.clear();
			SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
			playerSnapshots = List.of();
			materializationSnapshot = new MaterializationSnapshot(null, null, List.of(), List.of());
			ROUTE_CANDIDATES_BY_SPAWN_ID.clear();
			VIRTUAL_ROUTE_TIMINGS.clear();
			routeCacheSignature = "";
			pendingRouteCacheSignature = "";
			routeCacheGraphVersion = 0L;
			latestGraph = null;
			latestGraphDimensionId = null;
			graphRefreshInFlight = false;
			lastFullRailGraphRefreshWallMillis = 0L;
			lastFullRailGraphSeenWallMillis = 0L;
			fullGraphRefreshInFlight = false;
			submittedRailGraphSignature = null;
			appliedRailGraphSignature = null;
			pendingFullGraphRefresh = null;
			lastSnapshotRefreshTick = -SNAPSHOT_REFRESH_INTERVAL_TICKS;
			lastServerTick = 0;
			lastTrafficTickWallMillis = System.currentTimeMillis();
			lastMaterializationScanWallMillis = 0L;
			signalClockTick = 0;
			signalClockWallMillis = System.currentTimeMillis();
			lastSpawnDiagnosticTick = Long.MIN_VALUE / 4;
		}
		startGraphBuildExecutor();
		startSimulationExecutor();
	}

	private static void onServerTick(MinecraftServer server) {
		applyPendingFullGraphRefresh();
		lastServerTick = server.getTickCount();
		updatePlayerSnapshots(server);
		if (server.getTickCount() % TRAFFIC_POINT_CACHE_REFRESH_INTERVAL_TICKS == 0) {
			updateCachedTrafficPoints();
		}
		refreshGraphSnapshot(server);
		syncSignalClockToServerTick(lastServerTick);
	}

	private static void simulationTick() {
		final long nowMillis = System.currentTimeMillis();
		final double tickDurationSeconds;
		synchronized (SIMULATION_LOCK) {
			tickDurationSeconds = trafficTickDurationSeconds(nowMillis);
			lastTrafficTickWallMillis = nowMillis;
		}
		refreshRouteCacheIfNeeded();
		final long signalTick = currentSignalTick();
		MTR_VEHICLE_OCCUPANCY.entrySet().removeIf(entry -> signalTick - entry.getValue().lastTick() > MTR_VEHICLE_OCCUPANCY_STALE_TICKS);
		MTR_VEHICLE_PATH_STATES.keySet().removeIf(vehicleId -> !MTR_VEHICLE_OCCUPANCY.containsKey(vehicleId));
		rebuildMtrOccupancyIndex();

		materializeVirtualTraffic(nowMillis);

		synchronized (SIMULATION_LOCK) {
			removeVehiclesOutsideSimulationRangeAfterTimeout(nowMillis);
			TrafficIntersectionRegistry.tickAutoSignals(latestGraphDimensionId, latestGraph, ACTIVE_VEHICLES, mtrSignalVehicles(), signalTick);

			if (ACTIVE_VEHICLES.isEmpty()) {
				publishActiveVehicleSnapshot();
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
			publishActiveVehicleSnapshot();
		}
	}

	private static void publishActiveVehicleSnapshot() {
		activeVehicleSnapshot = List.copyOf(ACTIVE_VEHICLES);
		if (ACTIVE_VEHICLES.isEmpty()) {
			activeTrafficByConnector = Map.of();
			activeNetworkVehicleSnapshot = List.of();
			return;
		}
		final Map<String, List<IndexedTrafficVehicle>> mutableIndex = new HashMap<>();
		final List<TrafficNetworkVehicleSnapshot> networkSnapshots = new ArrayList<>(ACTIVE_VEHICLES.size());
		for (TrafficVehicle vehicle : ACTIVE_VEHICLES) {
			final TrafficVehiclePosition position = vehicle.currentPosition();
			networkSnapshots.add(new TrafficNetworkVehicleSnapshot(
				vehicle.id(),
				dimensionIdForVehicle(vehicle),
				vehicle.definition().effectiveVisualId(),
				vehicle.definition().type(),
				vehicle.definition().lengthMeters(),
				position.x(), position.y(), position.z(),
				position.yawDegrees(), position.pitchDegrees(),
				vehicle.speedKph()
			));
			final TrafficRouteSegment segment = vehicle.currentSegment().orElse(null);
			if (segment == null) {
				continue;
			}
			final String reverseConnectorId = railId(segment.endX(), segment.endY(), segment.endZ(), segment.startX(), segment.startY(), segment.startZ());
			final IndexedTrafficVehicle indexedVehicle = new IndexedTrafficVehicle(segment, reverseConnectorId, vehicle.distanceOnSegmentMeters(), vehicle.definition().lengthMeters());
			mutableIndex.computeIfAbsent(segment.connectorId(), ignored -> new ArrayList<>()).add(indexedVehicle);
			if (!reverseConnectorId.equals(segment.connectorId())) {
				mutableIndex.computeIfAbsent(reverseConnectorId, ignored -> new ArrayList<>()).add(indexedVehicle);
			}
		}
		final Map<String, List<IndexedTrafficVehicle>> immutableIndex = new HashMap<>();
		mutableIndex.forEach((connectorId, vehicles) -> immutableIndex.put(connectorId, List.copyOf(vehicles)));
		activeTrafficByConnector = Map.copyOf(immutableIndex);
		activeNetworkVehicleSnapshot = List.copyOf(networkSnapshots);
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
		simulationExecutor.scheduleWithFixedDelay(() -> {
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

	private static synchronized ExecutorService graphBuildExecutor() {
		if (!graphBuildAcceptingTasks) {
			throw new RejectedExecutionException("MTR rail graph builder is stopped");
		}
		if (graphBuildExecutor == null || graphBuildExecutor.isShutdown() || graphBuildExecutor.isTerminated()) {
			graphBuildExecutor = Executors.newSingleThreadExecutor(runnable -> {
				final Thread thread = new Thread(runnable, "MTR Traffic Addon Rail Graph Builder");
				thread.setDaemon(true);
				return thread;
			});
		}
		return graphBuildExecutor;
	}

	private static synchronized void startGraphBuildExecutor() {
		graphBuildGeneration++;
		graphBuildAcceptingTasks = true;
		graphBuildExecutor();
	}

	private static synchronized void stopGraphBuildExecutor() {
		graphBuildAcceptingTasks = false;
		graphBuildGeneration++;
		final ExecutorService executor = graphBuildExecutor;
		graphBuildExecutor = null;
		if (executor != null) {
			executor.shutdownNow();
		}
		pendingFullGraphRefresh = null;
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

		playerSnapshots = snapshots;
	}

	private static void updateCachedTrafficPoints() {
		final String dimensionId = latestGraphDimensionId;
		if (dimensionId == null) {
			materializationSnapshot = new MaterializationSnapshot(latestGraph, null, List.of(), List.of());
			pendingRouteCacheSignature = "";
			return;
		}

		final List<TrafficPointDefinition> spawns = TrafficSavedPointRegistry.getByTypeAndDimension(dimensionId, TrafficPointType.SPAWN).stream()
			.filter(TrafficPointDefinition::isEnabled)
			.toList();
		final List<TrafficPointDefinition> despawns = TrafficSavedPointRegistry.getByTypeAndDimension(dimensionId, TrafficPointType.DESPAWN).stream()
			.filter(TrafficPointDefinition::isEnabled)
			.toList();
		materializationSnapshot = new MaterializationSnapshot(latestGraph, dimensionId, spawns, despawns);
		pendingRouteCacheSignature = routeCacheSignature(spawns, despawns, routeCacheGraphVersion);
	}

	private static void refreshRouteCacheIfNeeded() {
		final String updatedSignature = pendingRouteCacheSignature;
		if (!updatedSignature.equals(routeCacheSignature)) {
			routeCacheSignature = updatedSignature;
			ROUTE_CANDIDATES_BY_SPAWN_ID.clear();
			VIRTUAL_ROUTE_TIMINGS.clear();
			SKIPPED_VIRTUAL_VEHICLE_IDS.clear();
		}
	}

	private static void refreshGraphSnapshot(MinecraftServer server) {
		if (System.currentTimeMillis() - lastFullRailGraphSeenWallMillis < FULL_RAIL_GRAPH_STALE_MILLIS) {
			return;
		}
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
				latestGraph = refreshedGraph.orElse(latestGraph);
				if (refreshedGraph.isPresent()) {
					routeCacheGraphVersion++;
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
					final List<TrafficPointDefinition> enabledSpawns = TrafficSavedPointRegistry.getByTypeAndDimension(latestGraphDimensionId, TrafficPointType.SPAWN).stream()
						.filter(TrafficPointDefinition::isEnabled)
						.toList();
					final List<TrafficPointDefinition> enabledDespawns = TrafficSavedPointRegistry.getByTypeAndDimension(latestGraphDimensionId, TrafficPointType.DESPAWN).stream()
						.filter(TrafficPointDefinition::isEnabled)
						.toList();
					materializationSnapshot = new MaterializationSnapshot(latestGraph, latestGraphDimensionId, enabledSpawns, enabledDespawns);
					pendingRouteCacheSignature = routeCacheSignature(enabledSpawns, enabledDespawns, routeCacheGraphVersion);
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

	private static void materializeVirtualTraffic(long nowMillis) {
		if (nowMillis - lastMaterializationScanWallMillis < MATERIALIZATION_SCAN_INTERVAL_MILLIS) {
			return;
		}
		lastMaterializationScanWallMillis = nowMillis;
		final MaterializationSnapshot materialization = materializationSnapshot;
		final MtrGraph graph = materialization.graph();
		if (graph == null || graph.isEmpty()) {
			return;
		}
		final Optional<TrafficVehicleDefinition> anyDefinition = TrafficVehicleDefinitionRegistry.getAnyDefinition();
		if (anyDefinition.isEmpty()) {
			logSpawnDiagnostic("Spawn blocked: no traffic vehicle definitions loaded from data/*/traffic_vehicles/*.json.");
			return;
		}

		final List<TrafficPointDefinition> spawns = materialization.enabledSpawns();
		final List<TrafficPointDefinition> despawns = materialization.enabledDespawns();
		final String dimensionId = materialization.dimensionId();
		if (spawns.isEmpty() || despawns.isEmpty()) {
			logSpawnDiagnostic("Virtual traffic skipped: enabled spawns={}, enabled despawns={} in dimension {}.", spawns.size(), despawns.size(), dimensionId);
			return;
		}

		final Set<UUID> activeIds = new HashSet<>();
		final List<TrafficVehicle> vehiclesToCheck;
		synchronized (SIMULATION_LOCK) {
			vehiclesToCheck = new ArrayList<>(ACTIVE_VEHICLES);
			for (TrafficVehicle vehicle : vehiclesToCheck) {
				activeIds.add(vehicle.id());
			}
		}

		final Set<UUID> consideredVirtualVehicleIds = new HashSet<>();
		final List<TrafficVehicle> vehiclesToAdd = new ArrayList<>();

		for (TrafficPointDefinition spawn : spawns) {
			if (!spawn.isEnabled() || spawn.effectiveVehiclePool().isEmpty()) {
				continue;
			}

			final List<VirtualRouteCandidate> routeCandidates = ROUTE_CANDIDATES_BY_SPAWN_ID.computeIfAbsent(spawn.id(), ignored -> buildVirtualRouteCandidates(graph, spawn, despawns));
			if (routeCandidates.isEmpty()) {
				continue;
			}

			final long intervalMillis = Math.max(1L, spawn.effectiveSpawnIntervalTicks() * SIGNAL_TICK_MILLIS);
			final long latestDepartureIndex = Math.floorDiv(nowMillis, intervalMillis);
			final int virtualVehicleCount = virtualDepartureScanCount(routeCandidates, anyDefinition.get(), intervalMillis);
			for (long departureIndex = latestDepartureIndex; departureIndex > latestDepartureIndex - virtualVehicleCount; departureIndex--) {
				final VirtualRouteCandidate candidate = routeCandidates.get(Math.floorMod(departureIndex, routeCandidates.size()));
				final TrafficVehicleDefinition definition = withSpawnVehiclePoolOverride(anyDefinition.get(), spawn, departureIndex);
				final VirtualVehicleSample sample = sampleVirtualVehicle(candidate, definition, nowMillis - departureIndex * intervalMillis);
				if (sample == null || !isPositionInMaterializationRange(dimensionId, sample.position().x(), sample.position().z())) {
					continue;
				}

				final UUID vehicleId = virtualVehicleId(spawn.id(), departureIndex);
				consideredVirtualVehicleIds.add(vehicleId);
				if (activeIds.contains(vehicleId) || SKIPPED_VIRTUAL_VEHICLE_IDS.contains(vehicleId)) {
					continue;
				}

				if (!hasMaterializationClearance(candidate.route(), definition, sample, vehiclesToCheck)) {
					SKIPPED_VIRTUAL_VEHICLE_IDS.add(vehicleId);
					continue;
				}

				final TrafficVehicle vehicle = createTrafficVehicle(definition, candidate, vehicleId, sample);
				vehiclesToAdd.add(vehicle);
				vehiclesToCheck.add(vehicle);
				activeIds.add(vehicleId);
			}
		}

		synchronized (SIMULATION_LOCK) {
			ACTIVE_VEHICLES.addAll(vehiclesToAdd);
			SKIPPED_VIRTUAL_VEHICLE_IDS.retainAll(consideredVirtualVehicleIds);
		}
	}

	private static int virtualDepartureScanCount(List<VirtualRouteCandidate> routeCandidates, TrafficVehicleDefinition definition, long intervalMillis) {
		long maxRouteDurationMillis = 0L;
		for (VirtualRouteCandidate candidate : routeCandidates) {
			maxRouteDurationMillis = Math.max(maxRouteDurationMillis, virtualRouteTiming(candidate, definition).totalDurationMillis());
		}
		final long routeDepartureCount = Math.max(1L, Math.floorDiv(maxRouteDurationMillis + intervalMillis - 1L, intervalMillis) + 1L);
		return (int) Math.min(MAX_VIRTUAL_DEPARTURES_PER_SPAWN_SCAN, routeDepartureCount);
	}

	private static VirtualRouteTiming virtualRouteTiming(VirtualRouteCandidate candidate, TrafficVehicleDefinition definition) {
		final double maxSpeedKph = Double.isFinite(definition.maxSpeedKph()) ? Math.max(1.0D, definition.maxSpeedKph()) : 1.0D;
		final long maxSpeedBits = Double.doubleToLongBits(maxSpeedKph);
		final CachedVirtualRouteTiming cachedTiming = VIRTUAL_ROUTE_TIMINGS.get(candidate);
		if (cachedTiming != null && cachedTiming.maxSpeedBits() == maxSpeedBits) {
			return cachedTiming.timing();
		}

		final List<TrafficRouteSegment> segments = candidate.route().segments();
		final double[] cumulativeEndSeconds = new double[segments.size()];
		final double[] speedLimitsKph = new double[segments.size()];
		double totalDurationSeconds = 0.0D;
		for (int i = 0; i < segments.size(); i++) {
			final TrafficRouteSegment segment = segments.get(i);
			final double segmentSpeedLimitKph = Double.isFinite(segment.speedLimitKph()) ? segment.speedLimitKph() : maxSpeedKph;
			final double speedKph = Math.max(1.0D, Math.min(maxSpeedKph, segmentSpeedLimitKph));
			speedLimitsKph[i] = speedKph;
			totalDurationSeconds += Math.max(0.0D, segment.lengthMeters()) / (speedKph / 3.6D);
			cumulativeEndSeconds[i] = totalDurationSeconds;
		}
		final long totalDurationMillis = !Double.isFinite(totalDurationSeconds) || totalDurationSeconds <= 0.0D
			? 0L
			: Math.min(Long.MAX_VALUE / 2L, (long) Math.ceil(totalDurationSeconds * 1000.0D));
		final VirtualRouteTiming timing = new VirtualRouteTiming(cumulativeEndSeconds, speedLimitsKph, totalDurationMillis);
		VIRTUAL_ROUTE_TIMINGS.put(candidate, new CachedVirtualRouteTiming(maxSpeedBits, timing));
		return timing;
	}

	private static boolean hasMaterializationClearance(TrafficRoute route, TrafficVehicleDefinition definition, VirtualVehicleSample sample, List<TrafficVehicle> vehiclesToCheck) {
		final List<TrafficRouteSegment> segments = route.segments();
		if (segments.isEmpty() || sample.segmentIndex() < 0 || sample.segmentIndex() >= segments.size()) {
			return false;
		}

		final TrafficRouteSegment spawnSegment = segments.get(0);
		final TrafficRouteSegment sampleSegment = segments.get(sample.segmentIndex());
		final double vehicleHalfLength = Math.max(0.0D, definition.lengthMeters()) * 0.5D;
		if (sampleIsNearSpawnCorridor(sample)) {
			if (isSpawnCorridorOccupied(segments, vehiclesToCheck) || isMtrSpawnCorridorOccupied(segments)) {
				return false;
			}
			if (isTrafficSpawnEntryOccupied(spawnSegment, vehicleHalfLength, vehiclesToCheck) || isMtrSegmentOccupiedAt(spawnSegment, 0.0D, vehicleHalfLength)) {
				return false;
			}
		}
		return !isTrafficSegmentOccupiedAt(sampleSegment, sample.distanceOnSegmentMeters(), vehicleHalfLength, vehiclesToCheck) && !isMtrSegmentOccupiedAt(sampleSegment, sample.distanceOnSegmentMeters(), vehicleHalfLength);
	}

	private static boolean sampleIsNearSpawnCorridor(VirtualVehicleSample sample) {
		return sample.segmentIndex() == 0 || sample.segmentIndex() == 1 && sample.distanceOnSegmentMeters() <= SPAWN_CONNECTED_NODE_CLEARANCE_METERS;
	}

	//__________________________________________________________________________
	//segment occupancy check below

	private static boolean isSpawnCorridorOccupied(List<TrafficRouteSegment> segments, List<TrafficVehicle> vehiclesToCheck) {
		if (segments.isEmpty()) {
			return true;
		}

		final TrafficRouteSegment spawnSegment = segments.get(0);
		if (isTrafficSegmentRangeOccupied(spawnSegment, 0.0D, Math.max(0.0D, spawnSegment.lengthMeters()), vehiclesToCheck)) {
			return true;
		}

		if (segments.size() < 2) {
			return false;
		}
		final TrafficRouteSegment connectedSegment = segments.get(1);
		return isTrafficSegmentRangeOccupied(connectedSegment, 0.0D, Math.min(SPAWN_CONNECTED_NODE_CLEARANCE_METERS, Math.max(0.0D, connectedSegment.lengthMeters())), vehiclesToCheck);
	}

	private static boolean isTrafficSegmentRangeOccupied(TrafficRouteSegment candidateSegment, double startDistanceMeters, double endDistanceMeters, List<TrafficVehicle> vehiclesToCheck) {
		for (TrafficVehicle otherVehicle : vehiclesToCheck) {
			final TrafficRouteSegment otherSegment = otherVehicle.currentSegment().orElse(null);
			if (otherSegment == null) {
				continue;
			}

			final Double otherDistanceInCandidateDirection = distanceOnSamePhysicalSegment(candidateSegment, otherSegment, otherVehicle.distanceOnSegmentMeters());
			if (otherDistanceInCandidateDirection == null) {
				continue;
			}

			final double clearance = Math.max(0.0D, otherVehicle.definition().lengthMeters()) * 0.5D + MATERIALIZATION_CLEARANCE_BUFFER_METERS;
			if (otherDistanceInCandidateDirection >= startDistanceMeters - clearance && otherDistanceInCandidateDirection <= endDistanceMeters + clearance) {
				return true;
			}
		}
		return false;
	}

	private static boolean isMtrSpawnCorridorOccupied(List<TrafficRouteSegment> segments) {
		if (segments.isEmpty()) {
			return true;
		}

		final TrafficRouteSegment spawnSegment = segments.get(0);
		if (isMtrSegmentRangeOccupied(spawnSegment, 0.0D, Math.max(0.0D, spawnSegment.lengthMeters()))) {
			return true;
		}

		if (segments.size() < 2) {
			return false;
		}
		final TrafficRouteSegment connectedSegment = segments.get(1);
		return isMtrSegmentRangeOccupied(connectedSegment, 0.0D, Math.min(SPAWN_CONNECTED_NODE_CLEARANCE_METERS, Math.max(0.0D, connectedSegment.lengthMeters())));
	}

	private static boolean isMtrSegmentRangeOccupied(TrafficRouteSegment candidateSegment, double startDistanceMeters, double endDistanceMeters) {
		for (DirectedMtrOccupancy directedOccupancy : mtrOccupancyByConnector.getOrDefault(candidateSegment.connectorId(), List.of())) {
			final MtrVehicleOccupancy occupancy = directedOccupancy.occupancy();
			final double occupiedDistanceMeters = directedOccupancy.sameDirection()
				? occupancy.distanceOnSegmentMeters()
				: Math.max(0.0D, occupancy.segmentLengthMeters() - occupancy.distanceOnSegmentMeters());

			final double clearance = Math.max(0.0D, occupancy.lengthMeters()) * 0.5D + MATERIALIZATION_CLEARANCE_BUFFER_METERS;
			if (occupiedDistanceMeters >= startDistanceMeters - clearance && occupiedDistanceMeters <= endDistanceMeters + clearance) {
				return true;
			}
		}
		return false;
	}

	private static boolean isTrafficSpawnEntryOccupied(TrafficRouteSegment spawnSegment, double candidateHalfLengthMeters, List<TrafficVehicle> vehiclesToCheck) {
		for (TrafficVehicle otherVehicle : vehiclesToCheck) {
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

	private static boolean isTrafficSegmentOccupiedAt(TrafficRouteSegment candidateSegment, double candidateDistanceMeters, double candidateHalfLengthMeters, List<TrafficVehicle> vehiclesToCheck) {
		for (TrafficVehicle otherVehicle : vehiclesToCheck) {
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
		for (DirectedMtrOccupancy directedOccupancy : mtrOccupancyByConnector.getOrDefault(candidateSegment.connectorId(), List.of())) {
			final MtrVehicleOccupancy occupancy = directedOccupancy.occupancy();
			final double occupiedDistanceMeters = directedOccupancy.sameDirection()
				? occupancy.distanceOnSegmentMeters()
				: Math.max(0.0D, occupancy.segmentLengthMeters() - occupancy.distanceOnSegmentMeters());

			final double requiredClearance = candidateHalfLengthMeters
				+ Math.max(0.0D, occupancy.lengthMeters()) * 0.5D
				+ MATERIALIZATION_CLEARANCE_BUFFER_METERS;
			if (Math.abs(occupiedDistanceMeters - candidateDistanceMeters) < requiredClearance) {
				return true;
			}
		}
		return false;
	}

	//_________________________________________________
	//Distance calculations
	//TODO: Optimize these distance calculations; they are called frequently.

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

	private static List<VirtualRouteCandidate> buildVirtualRouteCandidates(MtrGraph graph, TrafficPointDefinition spawn, List<TrafficPointDefinition> enabledDespawns) {
		if (!spawn.hasConnectorRoute()) {
			logSpawnDiagnostic("Virtual spawn {} skipped: connector route metadata is missing.", pointSummary(spawn));
			return List.of();
		}

		final List<VirtualRouteCandidate> candidates = new ArrayList<>();
		for (TrafficPointDefinition despawn : enabledDespawns) {
			if (despawn.id().equals(spawn.id()) || !despawn.hasConnectorRoute()) {
				continue;
			}

			buildConnectorAwareRoute(graph, spawn, despawn)
				.ifPresent(result -> candidates.add(new VirtualRouteCandidate(spawn, despawn, result.route())));
		}

		if (candidates.isEmpty()) {
			logSpawnDiagnostic("Virtual spawn {} skipped: no graph route found to {} compatible despawn(s).", pointSummary(spawn), enabledDespawns.size());
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
				final Optional<MtrGraphRouteResult> middleRouteResult = MtrGraphPathFinder.findFastestRoute(
					graph,
					spawnTraversal.routeEndNode(),
					despawnTraversal.routeStartNode()
				);

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

				final double travelTimeSeconds = spawnTraversal.travelTimeSeconds() + middleRouteResult.get().travelTimeSeconds() + despawnTraversal.travelTimeSeconds();
				if (bestResult == null || travelTimeSeconds < bestResult.travelTimeSeconds()) {
					bestResult = new MtrGraphRouteResult(new TrafficRoute(segments), travelTimeSeconds);
				}
			}
		}

		return Optional.ofNullable(bestResult);
	}

	//_____________________________________________________________________
	//vehicles timeout removal

	private static void removeVehiclesOutsideSimulationRangeAfterTimeout(long nowMillis) {
		if (ACTIVE_VEHICLES.isEmpty()) {
			LAST_RENDERED_WALL_MILLIS.clear();
			return;
		}
		if (playerSnapshots.isEmpty()) {
			return;
		}

		final int lifetimeSeconds = TrafficAddonConfig.trafficVehicleUnrenderedLifetimeSeconds();
		if (lifetimeSeconds <= 0) {
			LAST_RENDERED_WALL_MILLIS.clear();
			return;
		}

		final long lifetimeMillis = lifetimeSeconds * 1000L;
		ACTIVE_VEHICLES.removeIf(vehicle -> {
			if (isVehicleInSimulationRange(vehicle)) {
				LAST_RENDERED_WALL_MILLIS.remove(vehicle.id());
				return false;
			}
			final long firstOutOfRangeMillis = LAST_RENDERED_WALL_MILLIS.computeIfAbsent(vehicle.id(), ignored -> nowMillis);
			final boolean remove = nowMillis - firstOutOfRangeMillis > lifetimeMillis;
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

	private static boolean isPositionInMaterializationRange(String dimensionId, double x, double z) {
		if (dimensionId == null) {
			return false;
		}

		for (SimulationPlayerSnapshot player : playerSnapshots) {
			if (!dimensionId.equals(player.dimensionId())) {
				continue;
			}

			final double dx = x - player.x();
			final double dz = z - player.z();
			final double distanceSquared = dx * dx + dz * dz;
			final double simulationDistanceBlocks = TrafficAddonConfig.trafficVehicleSimulationDistanceBlocks(player.viewDistanceChunks());
			if (distanceSquared <= simulationDistanceBlocks * simulationDistanceBlocks) {
				return true;
			}
		}
		return false;
	}

	public static boolean isIntersectionInSimulationRange(TrafficIntersectionDefinition definition) {
		if (definition == null || definition.dimensionId() == null) {
			return false;
		}

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
		return false;
	}

	private static VirtualVehicleSample sampleVirtualVehicle(VirtualRouteCandidate candidate, TrafficVehicleDefinition definition, long elapsedMillis) {
		final TrafficRoute route = candidate.route();
		if (elapsedMillis < 0L || route.segments().isEmpty()) {
			return null;
		}

		final double elapsedSeconds = elapsedMillis / 1000.0D;
		final List<TrafficRouteSegment> segments = route.segments();
		final VirtualRouteTiming timing = virtualRouteTiming(candidate, definition);
		final double[] cumulativeEndSeconds = timing.cumulativeEndSeconds();
		if (cumulativeEndSeconds.length == 0 || elapsedSeconds > cumulativeEndSeconds[cumulativeEndSeconds.length - 1]) {
			return null;
		}

		int low = 0;
		int high = cumulativeEndSeconds.length - 1;
		while (low < high) {
			final int middle = (low + high) >>> 1;
			if (elapsedSeconds <= cumulativeEndSeconds[middle]) {
				high = middle;
			} else {
				low = middle + 1;
			}
		}
		final int segmentIndex = low;
		final TrafficRouteSegment segment = segments.get(segmentIndex);
		if (isProtectedConnectorMaterializationSegment(segment)) {
			return null;
		}
		final double segmentStartSeconds = segmentIndex == 0 ? 0.0D : cumulativeEndSeconds[segmentIndex - 1];
		final double speedKph = timing.speedLimitsKph()[segmentIndex];
		final double speedMetersPerSecond = speedKph / 3.6D;
		double distanceOnSegmentMeters = Math.min(segment.lengthMeters(), Math.max(0.0D, (elapsedSeconds - segmentStartSeconds) * speedMetersPerSecond));
		if (segmentIndex == 0 && segment.spawnConnector()) {
			distanceOnSegmentMeters = Math.max(distanceOnSegmentMeters, Math.min(segment.lengthMeters(), SPAWN_TRACK_MATERIALIZATION_OFFSET_METERS));
		}
		return new VirtualVehicleSample(
			segmentIndex,
			distanceOnSegmentMeters,
			speedKph,
			sampleRoutePosition(segment, distanceOnSegmentMeters)
		);
	}

	private static boolean isProtectedConnectorMaterializationSegment(TrafficRouteSegment segment) {
		return segment.despawnConnector();
	}

	private static TrafficVehiclePosition sampleRoutePosition(TrafficRouteSegment segment, double distanceMeters) {
		final List<com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint> path = segment.path();
		final double normalizedDistance = segment.lengthMeters() <= 0.0D
			? 0.0D
			: Math.min(1.0D, Math.max(0.0D, distanceMeters / segment.lengthMeters()));
		final double scaledIndex = normalizedDistance * (path.size() - 1.0D);
		final int previousIndex = Math.min(path.size() - 2, (int) Math.floor(scaledIndex));
		final double progress = Math.min(1.0D, Math.max(0.0D, scaledIndex - previousIndex));
		final com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint previous = path.get(previousIndex);
		final com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint next = path.get(previousIndex + 1);
		final Orientation orientation = orientation(
			next.x() - previous.x(),
			next.y() - previous.y(),
			next.z() - previous.z()
		);
		return new TrafficVehiclePosition(
			lerp(previous.x(), next.x(), progress),
			lerp(previous.y(), next.y(), progress),
			lerp(previous.z(), next.z(), progress),
			orientation.yawDegrees(),
			orientation.pitchDegrees()
		);
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

	private static String routeCacheSignature(List<TrafficPointDefinition> spawns, List<TrafficPointDefinition> despawns, long graphVersion) {
		final StringBuilder builder = new StringBuilder();
		builder.append("graph=").append(graphVersion).append('|');
		appendPointSignature(builder, spawns);
		builder.append("||");
		appendPointSignature(builder, despawns);
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

		final UUID vehicleId = virtualVehicleId(spawn.id(), departureIndex);
		final long randomizedIndexSeed = vehicleId.getMostSignificantBits() ^ vehicleId.getLeastSignificantBits();
		final int selectedIndex = Math.floorMod(randomizedIndexSeed, vehiclePool.size());
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

		addConnectorTraversal(traversals, graph, endNode, startNode, point);
		return traversals;
	}

	private static void addConnectorTraversal(List<ConnectorTraversal> traversals, MtrGraph graph, MtrNodeKey startNode, MtrNodeKey endNode, TrafficPointDefinition point) {
		graph.findEdge(startNode, endNode).filter(edge -> !edge.mtaPathBlocked()).ifPresent(edge -> traversals.add(new ConnectorTraversal(
			edge.from(),
			edge.to(),
			List.of(edge.toRouteSegment(point.type() == TrafficPointType.SPAWN, point.type() == TrafficPointType.DESPAWN)),
			edge.travelTimeSeconds()
		)));
	}

	private static double stopDistance(double railProgress, int stoppingSpace, double obstacleDistance) {
		return Math.max(0.0D, obstacleDistance - railProgress - Math.max(0, stoppingSpace));
	}

	private static RailDirectionMatch matchRouteRail(String pathForwardId, String pathReverseId, IndexedTrafficVehicle indexedVehicle) {
		final TrafficRouteSegment segment = indexedVehicle.segment();
		if (segment.connectorId().equals(pathForwardId)) {
			return new RailDirectionMatch(true);
		}
		if (segment.connectorId().equals(pathReverseId)) {
			return new RailDirectionMatch(false);
		}
		final String segmentReverseId = indexedVehicle.reverseConnectorId();
		if (segmentReverseId.equals(pathForwardId)) {
			return new RailDirectionMatch(false);
		}
		if (segmentReverseId.equals(pathReverseId)) {
			return new RailDirectionMatch(true);
		}
		return null;
	}

	private static String railId(double startX, double startY, double startZ, double endX, double endY, double endZ) {
		return org.mtr.core.data.TwoPositionsBase.getHexIdRaw(
			new org.mtr.core.data.Position(Math.round(startX), Math.round(startY), Math.round(startZ)),
			new org.mtr.core.data.Position(Math.round(endX), Math.round(endY), Math.round(endZ))
		);
	}

	private static boolean isRedMtrEntry(PathData pathData, long signalTick) {
		if (latestGraphDimensionId == null || latestGraph == null || latestGraph.isEmpty()) {
			return false;
		}

		final MtrGraph graph = latestGraph;
		final String routeRailId = pathData.getHexId(false);
		for (MtrGraphEdge edge : graph.edgesByRailId().getOrDefault(routeRailId, List.of())) {
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

	public static boolean isMtrObservationFresh(long observationTick, long currentTick) {
		return currentTick - observationTick <= MTR_VEHICLE_OCCUPANCY_STALE_TICKS;
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
		double speedKph,
		long lastTick,
		double currentX,
		double currentY,
		double currentZ,
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
		double segmentLengthMeters,
		List<MtrSignalPathPoint> pathPoints
	) {
		public MtrSignalPathSegment {
			pathPoints = pathPoints == null ? List.of() : List.copyOf(pathPoints);
		}
	}

	public record MtrSignalPathPoint(double x, double y, double z) {
	}

	private record MtrVehicleOccupancy(
		String connectorId,
		String reverseConnectorId,
		double distanceOnSegmentMeters,
		double segmentLengthMeters,
		double lengthMeters,
		double speedKph,
		long lastTick,
		double currentX,
		double currentY,
		double currentZ,
		List<MtrSignalPathSegment> signalPathSegments
	) {
		private MtrVehicleOccupancy {
			signalPathSegments = signalPathSegments == null ? List.of() : List.copyOf(signalPathSegments);
		}
	}

	private record RailDirectionMatch(boolean sameDirection) {
	}

	private record MtrVehiclePathState(List<PathData> path, int pathIndex, long geometryBucket, List<MtrSignalPathSegment> signalPathSegments) {
		private MtrVehiclePathState {
			signalPathSegments = signalPathSegments == null ? List.of() : List.copyOf(signalPathSegments);
		}
	}

	private record MtrSampledPathPoint(double distanceMeters, MtrSignalPathPoint point) {
	}

	private record CachedMtrPathGeometry(List<MtrSampledPathPoint> points) {
		private CachedMtrPathGeometry {
			points = List.copyOf(points);
		}
	}

	private record MtrPathGeometryKey(String forwardId, String reverseId, long segmentLengthBits, long windowIndex) {
	}

	private record DirectedMtrOccupancy(MtrVehicleOccupancy occupancy, boolean sameDirection) {
	}

	private record IndexedTrafficVehicle(TrafficRouteSegment segment, String reverseConnectorId, double distanceOnSegmentMeters, double lengthMeters) {
	}

	private record VirtualRouteCandidate(
		TrafficPointDefinition spawn,
		TrafficPointDefinition despawn,
		TrafficRoute route
	) {
	}

	private record CachedVirtualRouteTiming(long maxSpeedBits, VirtualRouteTiming timing) {
	}

	private record VirtualRouteTiming(double[] cumulativeEndSeconds, double[] speedLimitsKph, long totalDurationMillis) {
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

	private record RailGraphSignature(String dimensionId, int railCount, long contentSum, long contentXor) {
	}

	private record PendingFullGraphRefresh(RailGraphSignature signature, MtrGraph graph) {
	}

	private record MaterializationSnapshot(
		MtrGraph graph,
		String dimensionId,
		List<TrafficPointDefinition> enabledSpawns,
		List<TrafficPointDefinition> enabledDespawns
	) {
		private MaterializationSnapshot {
			enabledSpawns = enabledSpawns == null ? List.of() : List.copyOf(enabledSpawns);
			enabledDespawns = enabledDespawns == null ? List.of() : List.copyOf(enabledDespawns);
		}
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
		double travelTimeSeconds
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
