package com.cookiecraftmods.mta.traffic;

import com.cookiecraftmods.mta.MTRTrafficAddon;
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
import com.cookiecraftmods.mta.traffic.runtime.TrafficRoute;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRouteSegment;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;
import com.cookiecraftmods.mta.traffic.runtime.TrafficSpacingResolver;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinition;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinitionRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mtr.core.data.PathData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class TrafficManager {
	private static final int SNAPSHOT_REFRESH_INTERVAL_TICKS = 200;
	private static final int GRAPH_PRUNE_RADIUS_BLOCKS = 448;
	private static final int FAILED_SPAWN_RETRY_TICKS = 100;
	private static final int SPAWN_DIAGNOSTIC_INTERVAL_TICKS = 100;
	private static final int MTR_VEHICLE_OCCUPANCY_STALE_TICKS = 5;
	private static final int PAUSED_TRAFFIC_OBSTACLE_GRACE_TICKS = 20;
	private static final long SIGNAL_TICK_MILLIS = 50L;
	private static final long MTR_FAIL_OPEN_AFTER_NO_TRAFFIC_TICK_MILLIS = 1500L;
	private static final List<TrafficVehicle> ACTIVE_VEHICLES = new ArrayList<>();
	private static final Map<Long, MtrVehicleOccupancy> MTR_VEHICLE_OCCUPANCY = new ConcurrentHashMap<>();
	private static final MtrApiClient MTR_API_CLIENT = new MtrApiClient();
	private static boolean initialized;
	private static long lastSnapshotRefreshTick = -SNAPSHOT_REFRESH_INTERVAL_TICKS;
	private static MtrGraph latestGraph;
	private static String latestGraphDimensionId;
	private static long lastServerTick;
	private static long lastTrafficTickWallMillis;
	private static long signalClockTick;
	private static long signalClockWallMillis;
	private static boolean graphRefreshInFlight;
	private static final Map<String, Long> LAST_SPAWN_TICK_BY_POINT_ID = new HashMap<>();
	private static final Map<String, Long> LAST_FAILED_SPAWN_TICK_BY_POINT_ID = new HashMap<>();
	private static long lastSpawnDiagnosticTick = Long.MIN_VALUE / 4;

	private TrafficManager() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerLifecycleEvents.SERVER_STARTED.register(TrafficManager::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ACTIVE_VEHICLES.clear();
			MTR_VEHICLE_OCCUPANCY.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(TrafficManager::tick);
		initialized = true;
	}

	public static Collection<TrafficVehicle> getActiveVehicles() {
		return List.copyOf(ACTIVE_VEHICLES);
	}

	public static Map<String, Integer> countActiveVehiclesBySpawnPointId() {
		final Map<String, Integer> counts = new HashMap<>();
		for (TrafficVehicle vehicle : ACTIVE_VEHICLES) {
			if (vehicle.spawnPointId() != null) {
				counts.merge(vehicle.spawnPointId(), 1, Integer::sum);
			}
		}

		return Map.copyOf(counts);
	}

	public static int clearAllVehicles() {
		final int clearedVehicles = ACTIVE_VEHICLES.size();
		ACTIVE_VEHICLES.clear();
		return clearedVehicles;
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

		return Double.isFinite(closestDistance) ? closestDistance : -1.0D;
	}

	public static void recordMtrVehicle(long vehicleId, List<PathData> path, double railProgress, double speedMetersPerMillisecond, double lengthMeters) {
		if (path == null || path.isEmpty()) {
			MTR_VEHICLE_OCCUPANCY.remove(vehicleId);
			return;
		}

		PathData currentPathData = null;
		for (PathData pathData : path) {
			if (railProgress + 0.001D >= pathData.getStartDistance() && railProgress - 0.001D <= pathData.getEndDistance()) {
				currentPathData = pathData;
				break;
			}
		}

		if (currentPathData == null) {
			MTR_VEHICLE_OCCUPANCY.remove(vehicleId);
			return;
		}

		final double segmentLengthMeters = Math.max(0.001D, currentPathData.getEndDistance() - currentPathData.getStartDistance());
		final double distanceOnSegmentMeters = Math.min(segmentLengthMeters, Math.max(0.0D, railProgress - currentPathData.getStartDistance()));
		MTR_VEHICLE_OCCUPANCY.put(vehicleId, new MtrVehicleOccupancy(
			currentPathData.getHexId(false),
			currentPathData.getHexId(true),
			distanceOnSegmentMeters,
			segmentLengthMeters,
			Math.max(0.0D, lengthMeters),
			Math.max(0.0D, speedMetersPerMillisecond * 3600000.0D),
			lastServerTick
		));
	}

	public static Optional<MtrVehicleObstacle> closestMtrVehicleObstacle(TrafficVehicle followingVehicle) {
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
				occupancy.lastTick()
			));
		}
		return signalVehicles;
	}

	private static void onServerStarted(MinecraftServer server) {
		ACTIVE_VEHICLES.clear();
		MTR_VEHICLE_OCCUPANCY.clear();
		latestGraph = null;
		latestGraphDimensionId = null;
		graphRefreshInFlight = false;
		lastSnapshotRefreshTick = -SNAPSHOT_REFRESH_INTERVAL_TICKS;
		lastServerTick = 0;
		lastTrafficTickWallMillis = System.currentTimeMillis();
		signalClockTick = 0;
		signalClockWallMillis = System.currentTimeMillis();
		LAST_SPAWN_TICK_BY_POINT_ID.clear();
		LAST_FAILED_SPAWN_TICK_BY_POINT_ID.clear();
		lastSpawnDiagnosticTick = Long.MIN_VALUE / 4;
	}

	private static void tick(MinecraftServer server) {
		lastServerTick = server.getTickCount();
		lastTrafficTickWallMillis = System.currentTimeMillis();
		syncSignalClockToServerTick(lastServerTick);
		refreshGraphSnapshot(server);
		spawnBootstrapVehicleIfPossible();
		MTR_VEHICLE_OCCUPANCY.entrySet().removeIf(entry -> lastServerTick - entry.getValue().lastTick() > MTR_VEHICLE_OCCUPANCY_STALE_TICKS);
		final long signalTick = currentSignalTick();
		TrafficIntersectionRegistry.tickAutoSignals(latestGraphDimensionId, latestGraph, ACTIVE_VEHICLES, mtrSignalVehicles(), signalTick);

		if (ACTIVE_VEHICLES.isEmpty()) {
			return;
		}

		final double tickDurationSeconds = 1.0D / 20.0D;
		final Map<TrafficVehicle, Double> allowedSpeeds = TrafficSpacingResolver.resolveAllowedSpeeds(ACTIVE_VEHICLES);
		TrafficIntersectionRegistry.applySignalSpeedLimits(ACTIVE_VEHICLES, allowedSpeeds, signalTick);
		ACTIVE_VEHICLES.removeIf(vehicle -> vehicle.tick(tickDurationSeconds, allowedSpeeds.getOrDefault(vehicle, 0.0D)));
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
			graphRefreshInFlight = false;
			latestGraph = refreshedGraph.orElse(latestGraph);
			latestGraphDimensionId = requestedDimensionId;
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

	private static void spawnBootstrapVehicleIfPossible() {
		if (latestGraph == null || latestGraph.isEmpty()) {
			return;
		}

		final Optional<SelectedRoutePlan> selectedPlan = createConfiguredRoute(latestGraph);

		final Optional<TrafficVehicleDefinition> anyDefinition = TrafficVehicleDefinitionRegistry.getAnyDefinition();
		if (anyDefinition.isEmpty()) {
			logSpawnDiagnostic("Spawn blocked: no traffic vehicle definitions loaded from data/*/traffic_vehicles/*.json.");
			return;
		}
		if (selectedPlan.isEmpty()) {
			logSpawnDiagnostic("Spawn blocked: no configured spawn/despawn route plan found. Graph has {} edges.", latestGraph.edges().size());
			return;
		}

		anyDefinition.ifPresent(definition -> selectedPlan.ifPresent(plan -> {
			ACTIVE_VEHICLES.add(createTrafficVehicle(definition, plan));
			if (plan.spawn() != null) {
				LAST_SPAWN_TICK_BY_POINT_ID.put(plan.spawn().id(), lastServerTick);
				LAST_FAILED_SPAWN_TICK_BY_POINT_ID.remove(plan.spawn().id());
			}
			MTRTrafficAddon.LOGGER.debug("Spawned traffic vehicle {} using definition {} visual {} on {} route segments; spawn={}, despawn={}", ACTIVE_VEHICLES.size(), definition.id(), definition.effectiveVisualId(), plan.routeResult().route().segments().size(), pointSummary(plan.spawn()), pointSummary(plan.despawn()));
		}));
	}

	private static Optional<SelectedRoutePlan> createConfiguredRoute(MtrGraph graph) {
		final List<TrafficPointDefinition> savedSpawns = latestGraphDimensionId == null ? List.of() : TrafficSavedPointRegistry.getByTypeAndDimension(latestGraphDimensionId, TrafficPointType.SPAWN).stream().filter(TrafficPointDefinition::isEnabled).toList();
		final List<TrafficPointDefinition> savedDespawns = latestGraphDimensionId == null ? List.of() : TrafficSavedPointRegistry.getByTypeAndDimension(latestGraphDimensionId, TrafficPointType.DESPAWN).stream().filter(TrafficPointDefinition::isEnabled).toList();
		final List<TrafficPointDefinition> spawns = List.copyOf(savedSpawns);
		final List<TrafficPointDefinition> despawns = List.copyOf(savedDespawns);
		if (spawns.isEmpty() || despawns.isEmpty()) {
			logSpawnDiagnostic("Configured spawn skipped: enabled spawns={}, enabled despawns={} in dimension {}.", spawns.size(), despawns.size(), latestGraphDimensionId);
			return Optional.empty();
		}

		final Map<String, List<WeightedRouteCandidate>> candidatesBySpawnId = new LinkedHashMap<>();

		for (TrafficPointDefinition spawn : spawns) {
			if (!canSpawnFromPoint(spawn)) {
				continue;
			}
			if (spawn.effectiveVehiclePool().isEmpty()) {
				logSpawnDiagnostic("Configured spawn {} skipped: vehicle pool is empty.", pointSummary(spawn));
				continue;
			}

			final List<TrafficPointDefinition> candidateDespawns = despawns.stream()
				.filter(despawn -> !despawn.id().equals(spawn.id()))
				.toList();
			if (candidateDespawns.isEmpty()) {
				logSpawnDiagnostic("Configured spawn {} skipped: no enabled despawn connectors are available.", pointSummary(spawn));
				continue;
			}

			if (!spawn.hasConnectorRoute()) {
				logSpawnDiagnostic("Configured spawn {} skipped: connector route metadata is missing.", pointSummary(spawn));
				markSpawnFailure(spawn);
				continue;
			}

			final List<WeightedRouteCandidate> spawnCandidates = new ArrayList<>();
			for (TrafficPointDefinition despawn : candidateDespawns) {
				if (!despawn.hasConnectorRoute()) {
					continue;
				}

				buildConnectorAwareRoute(graph, spawn, despawn)
					.ifPresent(result -> spawnCandidates.add(new WeightedRouteCandidate(spawn, despawn, result)));
			}

			if (spawnCandidates.isEmpty()) {
				logSpawnDiagnostic("Configured spawn {} skipped: no graph route found to {} compatible despawn(s).", pointSummary(spawn), candidateDespawns.size());
				markSpawnFailure(spawn);
			} else {
				candidatesBySpawnId.put(spawn.id(), spawnCandidates);
			}
		}

		if (candidatesBySpawnId.isEmpty()) {
			logSpawnDiagnostic("Configured spawn skipped: 0 viable route candidates from {} spawns to {} despawns.", spawns.size(), despawns.size());
			return Optional.empty();
		}

		final List<String> spawnIds = List.copyOf(candidatesBySpawnId.keySet());
		final String selectedSpawnId = spawnIds.get(ThreadLocalRandom.current().nextInt(spawnIds.size()));
		final List<WeightedRouteCandidate> selectedSpawnCandidates = candidatesBySpawnId.getOrDefault(selectedSpawnId, List.of());
		final WeightedRouteCandidate selectedCandidate = selectedSpawnCandidates.get(ThreadLocalRandom.current().nextInt(selectedSpawnCandidates.size()));
		return Optional.of(new SelectedRoutePlan(selectedCandidate.spawn(), selectedCandidate.despawn(), selectedCandidate.routeResult()));
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

	private static boolean canSpawnFromPoint(TrafficPointDefinition spawn) {
		final long lastSpawnTickAtPoint = LAST_SPAWN_TICK_BY_POINT_ID.getOrDefault(spawn.id(), Long.MIN_VALUE / 4);
		if (lastServerTick - lastSpawnTickAtPoint < spawn.effectiveSpawnIntervalTicks()) {
			return false;
		}

		final long lastFailedSpawnTickAtPoint = LAST_FAILED_SPAWN_TICK_BY_POINT_ID.getOrDefault(spawn.id(), Long.MIN_VALUE / 4);
		final boolean retryReady = lastServerTick - lastFailedSpawnTickAtPoint >= FAILED_SPAWN_RETRY_TICKS;
		if (!retryReady) {
			logSpawnDiagnostic("Configured spawn {} waiting after failed route attempt; retry in {} ticks.", pointSummary(spawn), FAILED_SPAWN_RETRY_TICKS - (lastServerTick - lastFailedSpawnTickAtPoint));
		}
		if (!retryReady) {
			return false;
		}

		if (isSpawnTrackOccupied(spawn)) {
			logSpawnDiagnostic("Configured spawn {} waiting: spawn connector is occupied.", pointSummary(spawn));
			return false;
		}

		return true;
	}

	private static void markSpawnFailure(TrafficPointDefinition spawn) {
		LAST_FAILED_SPAWN_TICK_BY_POINT_ID.put(spawn.id(), lastServerTick);
	}

	private static TrafficVehicle createTrafficVehicle(TrafficVehicleDefinition definition, SelectedRoutePlan plan) {
		final TrafficVehicleDefinition effectiveDefinition = withSpawnVehiclePoolOverride(definition, plan.spawn());
		final TrafficRoute route = plan.routeResult().route();
		final double initialSpeed = 0.0D;
		final double initialDistanceOnSegmentMeters = initialDistanceForRoute(effectiveDefinition, route);

		return new TrafficVehicle(
			UUID.randomUUID(),
			effectiveDefinition,
			route,
			plan.spawn() == null ? null : plan.spawn().id(),
			plan.despawn() == null ? null : plan.despawn().id(),
			initialDistanceOnSegmentMeters,
			initialSpeed
		);
	}

	private static TrafficVehicleDefinition withSpawnVehiclePoolOverride(TrafficVehicleDefinition definition, TrafficPointDefinition spawn) {
		if (spawn == null) {
			return definition;
		}

		final List<String> vehiclePool = spawn.effectiveVehiclePool();
		if (vehiclePool.isEmpty()) {
			return definition;
		}

		final String selectedVisualId = vehiclePool.get(ThreadLocalRandom.current().nextInt(vehiclePool.size()));
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

	private static boolean isSpawnTrackOccupied(TrafficPointDefinition spawn) {
		if (latestGraph == null || latestGraph.isEmpty() || spawn == null || !spawn.hasConnectorRoute()) {
			return false;
		}

		final List<String> connectorIds = connectorIdsForPoint(latestGraph, spawn);
		if (connectorIds.isEmpty()) {
			return false;
		}

		for (TrafficVehicle vehicle : ACTIVE_VEHICLES) {
			if (isVehicleOccupyingConnector(vehicle, connectorIds)) {
				return true;
			}
		}

		for (MtrVehicleOccupancy occupancy : MTR_VEHICLE_OCCUPANCY.values()) {
			if (lastServerTick - occupancy.lastTick() <= MTR_VEHICLE_OCCUPANCY_STALE_TICKS
				&& (connectorIds.contains(occupancy.connectorId()) || connectorIds.contains(occupancy.reverseConnectorId()))) {
				return true;
			}
		}

		return false;
	}

	private static List<String> connectorIdsForPoint(MtrGraph graph, TrafficPointDefinition point) {
		final MtrNodeKey startNode = new MtrNodeKey(point.connectorStartX(), point.connectorStartY(), point.connectorStartZ());
		final MtrNodeKey endNode = new MtrNodeKey(point.connectorEndX(), point.connectorEndY(), point.connectorEndZ());
		final List<String> connectorIds = new ArrayList<>(2);
		MtrGraphPathFinder.findEdge(graph, startNode, endNode).ifPresent(edge -> connectorIds.add(edge.railId()));
		MtrGraphPathFinder.findEdge(graph, endNode, startNode).ifPresent(edge -> {
			if (!connectorIds.contains(edge.railId())) {
				connectorIds.add(edge.railId());
			}
		});
		return connectorIds;
	}

	private static boolean isVehicleOccupyingConnector(TrafficVehicle vehicle, List<String> connectorIds) {
		if (vehicle.currentSegment().map(segment -> connectorIds.contains(segment.connectorId())).orElse(false)) {
			return true;
		}

		final int previousSegmentIndex = vehicle.segmentIndex() - 1;
		final List<TrafficRouteSegment> segments = vehicle.route().segments();
		if (previousSegmentIndex < 0 || previousSegmentIndex >= segments.size()) {
			return false;
		}

		final TrafficRouteSegment previousSegment = segments.get(previousSegmentIndex);
		if (!connectorIds.contains(previousSegment.connectorId())) {
			return false;
		}

		final double rearClearanceMeters = vehicle.definition().lengthMeters() * 0.5D + 2.0D;
		return vehicle.distanceOnSegmentMeters() <= rearClearanceMeters;
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

	private static double initialDistanceForRoute(TrafficVehicleDefinition definition, TrafficRoute route) {
		if (route.segments().isEmpty()) {
			return 0.0D;
		}

		final TrafficRouteSegment firstSegment = route.segments().get(0);
		if (!firstSegment.spawnConnector()) {
			return 0.0D;
		}

		return Math.min(firstSegment.lengthMeters(), Math.max(0.5D, definition.lengthMeters() * 0.5D));
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
		long lastTick
	) {
	}

	private record MtrVehicleOccupancy(
		String connectorId,
		String reverseConnectorId,
		double distanceOnSegmentMeters,
		double segmentLengthMeters,
		double lengthMeters,
		double speedKph,
		long lastTick
	) {
	}

	private record RailDirectionMatch(boolean sameDirection) {
	}

	private record SelectedRoutePlan(
		TrafficPointDefinition spawn,
		TrafficPointDefinition despawn,
		MtrGraphRouteResult routeResult
	) {
	}

	private record WeightedRouteCandidate(
		TrafficPointDefinition spawn,
		TrafficPointDefinition despawn,
		MtrGraphRouteResult routeResult
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
