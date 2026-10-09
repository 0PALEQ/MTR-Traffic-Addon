package com.cookiecraftmods.mta.traffic.runtime;

import com.cookiecraftmods.mta.traffic.TrafficManager;
import com.cookiecraftmods.mta.traffic.spatial.SpatialGrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class TrafficSpacingResolver {
	private static final double MIN_SPACING_BUFFER_METERS = 2.0D;
	private static final double SPEED_BASED_GAP_FACTOR_METERS_PER_KPH = 0.5D;
	private static final double QUEUE_STOP_CLEARANCE_METERS = 0.75D;
	private static final double QUEUE_STOP_FRONT_SPEED_KPH = 0.75D;
	private static final double QUEUE_STOP_FOLLOWING_SPEED_KPH = 1.25D;
	private static final double LOOKAHEAD_BUFFER_METERS = 8.0D;
	private static final double ROUTE_OCCUPANCY_LOOKAHEAD_METERS = 80.0D;
	private static final double SPATIAL_OCCUPANCY_LOOKAHEAD_METERS = 32.0D;
	private static final double SPATIAL_LATERAL_CLEARANCE_METERS = 1.5D;
	private static final double SPATIAL_PREDICTION_MIN_SPEED_METERS_PER_SECOND = 2.0D;
	private static final double SPATIAL_VERTICAL_CLEARANCE_METERS = 2.0D;
	private static final double SPATIAL_INDEX_CELL_SIZE_METERS = 16.0D;
	private static final double MERGE_NODE_TOLERANCE_METERS = 0.5D;
	private static final double MERGE_PRIORITY_DISTANCE_BUCKET_METERS = 0.25D;
	private static final double SIGNAL_STOP_BUFFER_METERS = 2.0D;
	private static final double SIGNAL_APPROACH_LOOKAHEAD_METERS = 10.0D;
	private static final double TICK_DURATION_SECONDS = 1.0D / 20.0D;

	private TrafficSpacingResolver() {
	}

	public static Map<TrafficVehicle, Double> resolveAllowedSpeeds(Collection<TrafficVehicle> vehicles) {
		final Map<TrafficVehicle, Double> allowedSpeeds = new HashMap<>();

		for (TrafficVehicle vehicle : vehicles) {
			final double segmentSpeedLimit = Math.min(vehicle.definition().maxSpeedKph(), vehicle.currentSegmentSpeedLimitKph());
			allowedSpeeds.put(vehicle, Math.max(0.0D, segmentSpeedLimit));
		}

		final Map<String, List<TrafficVehicle>> byConnector = buildVehiclesByDirectedSegment(vehicles);

		for (List<TrafficVehicle> connectorVehicles : byConnector.values()) {
			for (int i = 0; i + 1 < connectorVehicles.size(); i++) {
				final TrafficVehicle followingVehicle = connectorVehicles.get(i);
				final TrafficVehicle frontVehicle = connectorVehicles.get(i + 1);
				final double distanceMeters = frontVehicle.distanceOnSegmentMeters() - followingVehicle.distanceOnSegmentMeters();
				applyFollowingLimit(allowedSpeeds, frontVehicle, followingVehicle, distanceMeters);
			}
		}

		final Map<TrafficVehicle, VehicleObstacle> routeObstacles = applyRouteLookaheadSpacing(vehicles, byConnector, allowedSpeeds);
		final Map<TrafficVehicle, Integer> rightOfWayOrder = buildRightOfWayOrder(vehicles, routeObstacles);
		applyMergeSpacing(vehicles, allowedSpeeds, rightOfWayOrder);
		applySpatialSpacing(vehicles, allowedSpeeds, rightOfWayOrder);
		applyMtrVehicleSpacing(vehicles, allowedSpeeds);
		applySignalLimits(vehicles, countSignalSectionOccupancy(vehicles), allowedSpeeds);
		return allowedSpeeds;
	}

	private static Map<String, List<TrafficVehicle>> buildVehiclesByDirectedSegment(Collection<TrafficVehicle> vehicles) {
		final Map<String, List<TrafficVehicle>> byConnector = new HashMap<>();
		for (TrafficVehicle vehicle : vehicles) {
			final TrafficRouteSegment segment = vehicle.currentSegment().orElse(null);
			if (segment != null) {
				byConnector.computeIfAbsent(segment.directedConnectorId(), ignored -> new ArrayList<>()).add(vehicle);
			}
		}

		for (List<TrafficVehicle> connectorVehicles : byConnector.values()) {
			connectorVehicles.sort(Comparator.comparingDouble(TrafficVehicle::distanceOnSegmentMeters));
		}
		return byConnector;
	}

	private static Map<TrafficVehicle, VehicleObstacle> applyRouteLookaheadSpacing(Collection<TrafficVehicle> vehicles, Map<String, List<TrafficVehicle>> vehiclesByConnector, Map<TrafficVehicle, Double> allowedSpeeds) {
		final Map<TrafficVehicle, VehicleObstacle> obstacles = new HashMap<>();
		for (TrafficVehicle followingVehicle : vehicles) {
			final VehicleObstacle obstacle = closestRouteObstacle(vehiclesByConnector, followingVehicle);
			if (obstacle == null) {
				continue;
			}

			applyFollowingLimit(allowedSpeeds, obstacle.frontVehicle(), followingVehicle, obstacle.distanceMeters());
			obstacles.put(followingVehicle, obstacle);
		}
		return obstacles;
	}

	private static Map<TrafficVehicle, Integer> buildRightOfWayOrder(Collection<TrafficVehicle> vehicles, Map<TrafficVehicle, VehicleObstacle> routeObstacles) {
		final Map<TrafficVehicle, List<TrafficVehicle>> followersByLeader = new HashMap<>();
		final Map<TrafficVehicle, PriorityCandidate> candidates = new HashMap<>();
		final Comparator<PriorityCandidate> comparator = Comparator.comparingLong(PriorityCandidate::distanceBucket)
			.thenComparing(candidate -> candidate.vehicle().id());
		final PriorityQueue<PriorityCandidate> ready = new PriorityQueue<>(comparator);
		for (TrafficVehicle vehicle : vehicles) {
			final PriorityCandidate candidate = new PriorityCandidate(vehicle, mergePriorityBucket(vehicle));
			candidates.put(vehicle, candidate);
			final VehicleObstacle obstacle = routeObstacles.get(vehicle);
			if (obstacle == null) {
				ready.add(candidate);
			} else {
				followersByLeader.computeIfAbsent(obstacle.frontVehicle(), ignored -> new ArrayList<>()).add(vehicle);
			}
		}
		final Map<TrafficVehicle, Integer> order = new HashMap<>();
		while (!ready.isEmpty()) {
			final TrafficVehicle leader = ready.remove().vehicle();
			order.put(leader, order.size());
			for (TrafficVehicle follower : followersByLeader.getOrDefault(leader, List.of())) {
				ready.add(candidates.get(follower));
			}
		}
		// Route leaders must never yield back to their own waiting followers.
		// Keep a total order even in a genuinely full circular route; physical
		// following limits still protect occupied road space in that case.
		for (PriorityCandidate candidate : candidates.values()) {
			if (!order.containsKey(candidate.vehicle())) {
				ready.add(candidate);
			}
		}
		while (!ready.isEmpty()) {
			order.put(ready.remove().vehicle(), order.size());
		}
		return order;
	}

	private static void applyMergeSpacing(Collection<TrafficVehicle> vehicles, Map<TrafficVehicle, Double> allowedSpeeds, Map<TrafficVehicle, Integer> rightOfWayOrder) {
		final Map<MergeNode, TrafficVehicle> winnerByExit = new HashMap<>();
		for (TrafficVehicle vehicle : vehicles) {
			final TrafficRouteSegment segment = vehicle.currentSegment().orElse(null);
			if (segment == null || vehicle.distanceToEndOfCurrentSegmentMeters() > SPATIAL_OCCUPANCY_LOOKAHEAD_METERS) {
				continue;
			}
			winnerByExit.merge(MergeNode.of(segment), vehicle,
				(first, second) -> rightOfWayOrder.get(first) <= rightOfWayOrder.get(second) ? first : second);
		}
		for (TrafficVehicle vehicle : vehicles) {
			final TrafficRouteSegment segment = vehicle.currentSegment().orElse(null);
			if (segment == null) {
				continue;
			}
			final TrafficVehicle winner = winnerByExit.get(MergeNode.of(segment));
			if (winner == null || winner == vehicle || !approachesSameMerge(vehicle, winner)) {
				continue;
			}
			// Stop the body before the shared node, not only once another center
			// is directly in front of us on the converging curves.
			final double clearance = vehicle.distanceToEndOfCurrentSegmentMeters()
				- Math.max(0.0D, vehicle.definition().lengthMeters()) * 0.5D - MIN_SPACING_BUFFER_METERS;
			final double speedCap = clearance <= 0.0D ? 0.0D
				: Math.sqrt(2.0D * vehicle.definition().effectiveBrakingMetersPerSecondSquared() * clearance) * 3.6D;
			allowedSpeeds.put(vehicle, Math.min(allowedSpeeds.getOrDefault(vehicle, 0.0D), speedCap));
		}
	}

	private static void applyMtrVehicleSpacing(Collection<TrafficVehicle> vehicles, Map<TrafficVehicle, Double> allowedSpeeds) {
		for (TrafficVehicle followingVehicle : vehicles) {
			TrafficManager.closestMtrVehicleObstacle(followingVehicle).ifPresent(obstacle ->
				applyFollowingLimit(allowedSpeeds, obstacle.lengthMeters(), obstacle.speedKph(), followingVehicle, obstacle.distanceMeters())
			);
		}
	}

	private static void applySpatialSpacing(Collection<TrafficVehicle> vehicles, Map<TrafficVehicle, Double> allowedSpeeds, Map<TrafficVehicle, Integer> rightOfWayOrder) {
		final SpatialGrid<VehicleSpatialSnapshot> spatialIndex = new SpatialGrid<>(SPATIAL_INDEX_CELL_SIZE_METERS);
		final List<VehicleSpatialSnapshot> snapshots = new ArrayList<>(vehicles.size());
		for (TrafficVehicle vehicle : vehicles) {
			final TrafficVehiclePosition position = vehicle.currentPosition();
			final double yaw = Math.toRadians(position.yawDegrees());
			final VehicleSpatialSnapshot snapshot = new VehicleSpatialSnapshot(vehicle, position, Math.cos(yaw), Math.sin(yaw),
				Math.max(0.0D, vehicle.definition().lengthMeters()) * 0.5D, Math.max(0.0D, vehicle.speedKph()) / 3.6D,
				vehicle.currentSegment().orElse(null), rightOfWayOrder.get(vehicle));
			snapshots.add(snapshot);
			spatialIndex.add(position.x(), position.z(), snapshot);
		}

		final int cellRadius = spatialIndex.radius(SPATIAL_OCCUPANCY_LOOKAHEAD_METERS);
		for (VehicleSpatialSnapshot following : snapshots) {
			final TrafficVehicle followingVehicle = following.vehicle();
			final TrafficVehiclePosition followingPosition = following.position();
			final long centerCellX = spatialIndex.coordinate(followingPosition.x());
			final long centerCellZ = spatialIndex.coordinate(followingPosition.z());
			double closestClearance = Double.POSITIVE_INFINITY;

			for (long offsetX = -cellRadius; offsetX <= cellRadius; offsetX++) {
				for (long offsetZ = -cellRadius; offsetZ <= cellRadius; offsetZ++) {
					final List<VehicleSpatialSnapshot> nearbyVehicles = spatialIndex.cell(centerCellX + offsetX, centerCellZ + offsetZ);
					if (nearbyVehicles == null) {
						continue;
					}

					for (VehicleSpatialSnapshot nearby : nearbyVehicles) {
						if (nearby.vehicle() == followingVehicle || Math.abs(nearby.position().y() - followingPosition.y()) > SPATIAL_VERTICAL_CLEARANCE_METERS) {
							continue;
						}

						final double dx = nearby.position().x() - followingPosition.x();
						final double dz = nearby.position().z() - followingPosition.z();
						if (dx * dx + dz * dz > SPATIAL_OCCUPANCY_LOOKAHEAD_METERS * SPATIAL_OCCUPANCY_LOOKAHEAD_METERS
							|| following.segment() == null || nearby.segment() == null
							|| following.segment().directedConnectorId().equals(nearby.segment().directedConnectorId())
							|| compareSpatialPriority(following, nearby) < 0) {
							continue;
						}
						closestClearance = Math.min(closestClearance, spatialConflictClearance(following, nearby));
					}
				}
			}

			if (Double.isFinite(closestClearance)) {
				final double speedCap = closestClearance <= 0.0D ? 0.0D
					: Math.sqrt(2.0D * followingVehicle.definition().effectiveBrakingMetersPerSecondSquared() * closestClearance) * 3.6D;
				allowedSpeeds.put(followingVehicle, Math.min(allowedSpeeds.getOrDefault(followingVehicle, 0.0D), speedCap));
			}
		}
	}

	private static double spatialConflictClearance(VehicleSpatialSnapshot following, VehicleSpatialSnapshot nearby) {
		// Continuous SAT on four rectangle axes: constant work per local pair,
		// no forward-path sampling or full-network scans on every simulation tick.
		final double followingSpeed = Math.max(SPATIAL_PREDICTION_MIN_SPEED_METERS_PER_SECOND, following.speedMetersPerSecond());
		final double dx = nearby.position().x() - following.position().x();
		final double dz = nearby.position().z() - following.position().z();
		final double velocityX = nearby.forwardX() * nearby.speedMetersPerSecond() - following.forwardX() * followingSpeed;
		final double velocityZ = nearby.forwardZ() * nearby.speedMetersPerSecond() - following.forwardZ() * followingSpeed;
		double enterTime = 0.0D;
		double exitTime = SPATIAL_OCCUPANCY_LOOKAHEAD_METERS / Math.max(followingSpeed, nearby.speedMetersPerSecond());
		for (int axis = 0; axis < 4; axis++) {
			final VehicleSpatialSnapshot basis = axis < 2 ? following : nearby;
			final double axisX = axis % 2 == 0 ? basis.forwardX() : -basis.forwardZ();
			final double axisZ = axis % 2 == 0 ? basis.forwardZ() : basis.forwardX();
			final double radius = projectedRadius(following, axisX, axisZ) + projectedRadius(nearby, axisX, axisZ);
			final double distance = dx * axisX + dz * axisZ;
			final double velocity = velocityX * axisX + velocityZ * axisZ;
			if (Math.abs(velocity) < 0.000001D) {
				if (Math.abs(distance) >= radius) {
					return Double.POSITIVE_INFINITY;
				}
			} else {
				final double first = (-radius - distance) / velocity;
				final double second = (radius - distance) / velocity;
				enterTime = Math.max(enterTime, Math.min(first, second));
				exitTime = Math.min(exitTime, Math.max(first, second));
				if (enterTime >= exitTime) {
					return Double.POSITIVE_INFINITY;
				}
			}
		}
		return followingSpeed * enterTime - MIN_SPACING_BUFFER_METERS
			- following.speedMetersPerSecond() * TICK_DURATION_SECONDS * 0.5D;
	}

	private static double projectedRadius(VehicleSpatialSnapshot snapshot, double axisX, double axisZ) {
		return projectedRadius(snapshot.forwardX(), snapshot.forwardZ(), snapshot.halfLength(), axisX, axisZ);
	}

	private static double projectedRadius(double forwardX, double forwardZ, double halfLength, double axisX, double axisZ) {
		return Math.abs(axisX * forwardX + axisZ * forwardZ) * halfLength
			+ Math.abs(-axisX * forwardZ + axisZ * forwardX) * SPATIAL_LATERAL_CLEARANCE_METERS * 0.5D;
	}

	private static long mergePriorityBucket(TrafficVehicle vehicle) {
		// Keep departing bodies ahead of approaching cars in the same total
		// order. Pairwise clearing exceptions can create another yield cycle.
		final double distanceFromNode = Math.max(0.0D, vehicle.distanceOnSegmentMeters());
		final double rearClearance = Math.max(0.0D, vehicle.definition().lengthMeters()) * 0.5D + MIN_SPACING_BUFFER_METERS;
		if (vehicle.segmentIndex() > 0 && distanceFromNode <= rearClearance) {
			return -1L - (long) Math.floor(distanceFromNode / MERGE_PRIORITY_DISTANCE_BUCKET_METERS);
		}
		return (long) Math.floor(vehicle.distanceToEndOfCurrentSegmentMeters() / MERGE_PRIORITY_DISTANCE_BUCKET_METERS);
	}

	private static int compareSpatialPriority(VehicleSpatialSnapshot first, VehicleSpatialSnapshot second) {
		return Integer.compare(first.rightOfWayRank(), second.rightOfWayRank());
	}

	private static boolean approachesSameMerge(TrafficVehicle first, TrafficVehicle second) {
		final TrafficRouteSegment firstCurrent = first.currentSegment().orElse(null);
		final TrafficRouteSegment secondCurrent = second.currentSegment().orElse(null);
		return approachesSameMerge(firstCurrent, secondCurrent);
	}

	private static boolean approachesSameMerge(TrafficRouteSegment firstCurrent, TrafficRouteSegment secondCurrent) {
		if (firstCurrent == null || secondCurrent == null
			|| firstCurrent.directedConnectorId().equals(secondCurrent.directedConnectorId())) {
			return false;
		}

		final double dx = firstCurrent.endX() - secondCurrent.endX();
		final double dy = firstCurrent.endY() - secondCurrent.endY();
		final double dz = firstCurrent.endZ() - secondCurrent.endZ();
		return dx * dx + dy * dy + dz * dz <= MERGE_NODE_TOLERANCE_METERS * MERGE_NODE_TOLERANCE_METERS;
	}

	private static VehicleObstacle closestRouteObstacle(Map<String, List<TrafficVehicle>> vehiclesByConnector, TrafficVehicle followingVehicle) {
		final List<TrafficRouteSegment> followingSegments = followingVehicle.route().segments();
		if (followingSegments.isEmpty() || followingVehicle.segmentIndex() < 0 || followingVehicle.segmentIndex() >= followingSegments.size()) {
			return null;
		}

		VehicleObstacle closestObstacle = null;
		double distanceToSegmentStart = -followingVehicle.distanceOnSegmentMeters();
		for (int segmentIndex = followingVehicle.segmentIndex(); segmentIndex < followingSegments.size(); segmentIndex++) {
			final TrafficRouteSegment candidateSegment = followingSegments.get(segmentIndex);
			if (distanceToSegmentStart > ROUTE_OCCUPANCY_LOOKAHEAD_METERS) {
				break;
			}

			final List<TrafficVehicle> segmentVehicles = vehiclesByConnector.get(candidateSegment.directedConnectorId());
			final TrafficVehicle frontVehicle = firstVehicleAheadOnSegment(segmentVehicles, followingVehicle, segmentIndex == followingVehicle.segmentIndex());
			if (frontVehicle != null) {
				final double distanceToFrontVehicle = distanceToSegmentStart + frontVehicle.distanceOnSegmentMeters();
				if (distanceToFrontVehicle > 0.0D && distanceToFrontVehicle <= ROUTE_OCCUPANCY_LOOKAHEAD_METERS && (closestObstacle == null || distanceToFrontVehicle < closestObstacle.distanceMeters())) {
					closestObstacle = new VehicleObstacle(frontVehicle, distanceToFrontVehicle);
				}
			}

			distanceToSegmentStart += Math.max(candidateSegment.lengthMeters(), 0.0D);
		}

		return closestObstacle;
	}

	private static TrafficVehicle firstVehicleAheadOnSegment(List<TrafficVehicle> segmentVehicles, TrafficVehicle followingVehicle, boolean sameCurrentSegment) {
		if (segmentVehicles == null || segmentVehicles.isEmpty()) {
			return null;
		}

		if (!sameCurrentSegment) {
			for (TrafficVehicle vehicle : segmentVehicles) {
				if (vehicle != followingVehicle) {
					return vehicle;
				}
			}
			return null;
		}

		final double followingDistance = followingVehicle.distanceOnSegmentMeters();
		int low = 0;
		int high = segmentVehicles.size();
		while (low < high) {
			final int mid = (low + high) >>> 1;
			if (segmentVehicles.get(mid).distanceOnSegmentMeters() <= followingDistance) {
				low = mid + 1;
			} else {
				high = mid;
			}
		}

		for (int i = low; i < segmentVehicles.size(); i++) {
			final TrafficVehicle vehicle = segmentVehicles.get(i);
			if (vehicle != followingVehicle) {
				return vehicle;
			}
		}
		return null;
	}

	private static void applyFollowingLimit(Map<TrafficVehicle, Double> allowedSpeeds, TrafficVehicle frontVehicle, TrafficVehicle followingVehicle, double distanceMeters) {
		applyFollowingLimit(allowedSpeeds, frontVehicle.definition().lengthMeters(), frontVehicle.smoothedSpeedKph(), followingVehicle, distanceMeters);
	}

	private static void applyFollowingLimit(Map<TrafficVehicle, Double> allowedSpeeds, double frontVehicleLengthMeters, double frontVehicleSpeedKph, TrafficVehicle followingVehicle, double distanceMeters) {
		final double currentLimitKph = allowedSpeeds.getOrDefault(followingVehicle, 0.0D);
		final double limitedSpeedKph = resolveFollowingSpeed(frontVehicleLengthMeters, frontVehicleSpeedKph, followingVehicle, distanceMeters, currentLimitKph);
		allowedSpeeds.put(followingVehicle, Math.min(currentLimitKph, limitedSpeedKph));
	}

	private static double resolveFollowingSpeed(double frontVehicleLengthMeters, double frontVehicleSpeedKph, TrafficVehicle followingVehicle, double distanceMeters, double currentLimitKph) {
		final double minGap = frontVehicleLengthMeters / 2.0D
			+ followingVehicle.definition().lengthMeters() / 2.0D
			+ followingGapMeters(followingVehicle);
		final double clearance = distanceMeters - minGap;
		if (clearance <= 0.0D) {
			return 0.0D;
		}

		if (shouldHoldInStandingQueue(frontVehicleSpeedKph, followingVehicle, clearance)) {
			return 0.0D;
		}

		final double brakingCapKph = Math.sqrt(2.0D * followingVehicle.definition().effectiveBrakingMetersPerSecondSquared() * clearance) * 3.6D;
		final double lookaheadGap = minGap + LOOKAHEAD_BUFFER_METERS + followingVehicle.definition().lengthMeters();
		if (distanceMeters >= lookaheadGap) {
			return Math.max(0.0D, Math.min(currentLimitKph, brakingCapKph));
		}

		final double progress = (distanceMeters - minGap) / Math.max(lookaheadGap - minGap, 0.001D);
		final double cappedByFrontSpeed = frontVehicleSpeedKph + Math.max(0.0D, progress) * Math.max(0.0D, currentLimitKph - frontVehicleSpeedKph);
		return Math.max(0.0D, Math.min(currentLimitKph, Math.min(cappedByFrontSpeed, brakingCapKph)));
	}

	private static double followingGapMeters(TrafficVehicle followingVehicle) {
		return Math.max(MIN_SPACING_BUFFER_METERS, Math.max(0.0D, followingVehicle.speedKph()) * SPEED_BASED_GAP_FACTOR_METERS_PER_KPH);
	}

	private static boolean shouldHoldInStandingQueue(double frontVehicleSpeedKph, TrafficVehicle followingVehicle, double clearanceMeters) {
		return frontVehicleSpeedKph <= QUEUE_STOP_FRONT_SPEED_KPH
			&& followingVehicle.speedKph() <= QUEUE_STOP_FOLLOWING_SPEED_KPH
			&& clearanceMeters <= QUEUE_STOP_CLEARANCE_METERS;
	}

	private static Map<Long, Integer> countSignalSectionOccupancy(Collection<TrafficVehicle> vehicles) {
		final Map<Long, Integer> signalSectionOccupancy = new HashMap<>();
		for (TrafficVehicle vehicle : vehicles) {
			final TrafficRouteSegment currentSegment = vehicle.currentSegment().orElse(null);
			if (currentSegment == null) {
				continue;
			}

			for (Long signalColor : currentSegment.signalColors()) {
				signalSectionOccupancy.merge(signalColor, 1, Integer::sum);
			}
		}
		return signalSectionOccupancy;
	}

	private static void applySignalLimits(Collection<TrafficVehicle> vehicles, Map<Long, Integer> signalSectionOccupancy, Map<TrafficVehicle, Double> allowedSpeeds) {
		for (TrafficVehicle vehicle : vehicles) {
			final TrafficRouteSegment currentSegment = vehicle.currentSegment().orElse(null);
			if (currentSegment == null) {
				continue;
			}

			final TrafficRouteSegment nextSegment = vehicle.nextSegment().orElse(null);
			if (nextSegment == null || !isNextSegmentSignalEntry(currentSegment, nextSegment) || !isSignalSectionOccupiedByOtherVehicle(signalSectionOccupancy, vehicle, nextSegment)) {
				continue;
			}

			final double distanceToStop = vehicle.distanceToEndOfCurrentSegmentMeters() - SIGNAL_STOP_BUFFER_METERS;
			if (distanceToStop <= 0.0D) {
				allowedSpeeds.put(vehicle, 0.0D);
			} else if (distanceToStop <= SIGNAL_APPROACH_LOOKAHEAD_METERS) {
				final double maxSpeedToStopKph = distanceToStop / TICK_DURATION_SECONDS * 3.6D;
				allowedSpeeds.put(vehicle, Math.min(allowedSpeeds.getOrDefault(vehicle, 0.0D), maxSpeedToStopKph));
			}
		}
	}

	private static boolean isNextSegmentSignalEntry(TrafficRouteSegment currentSegment, TrafficRouteSegment nextSegment) {
		return !nextSegment.signalColors().isEmpty() && !overlaps(currentSegment.signalColors(), nextSegment.signalColors());
	}

	private static boolean isSignalSectionOccupiedByOtherVehicle(Map<Long, Integer> signalSectionOccupancy, TrafficVehicle candidateVehicle, TrafficRouteSegment candidateSegment) {
		final TrafficRouteSegment candidateCurrentSegment = candidateVehicle.currentSegment().orElse(null);
		for (Long signalColor : candidateSegment.signalColors()) {
			int occupiedCount = signalSectionOccupancy.getOrDefault(signalColor, 0);
			if (candidateCurrentSegment != null && candidateCurrentSegment.signalColors().contains(signalColor)) {
				occupiedCount--;
			}
			if (occupiedCount > 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlaps(List<Long> first, List<Long> second) {
		if (first.isEmpty() || second.isEmpty()) {
			return false;
		}

		for (Long value : first) {
			if (second.contains(value)) {
				return true;
			}
		}
		return false;
	}

	private record VehicleObstacle(TrafficVehicle frontVehicle, double distanceMeters) {
	}

	private record VehicleSpatialSnapshot(TrafficVehicle vehicle, TrafficVehiclePosition position, double forwardX, double forwardZ,
		double halfLength, double speedMetersPerSecond, TrafficRouteSegment segment, int rightOfWayRank) {
	}

	private record PriorityCandidate(TrafficVehicle vehicle, long distanceBucket) {
	}

	private record MergeNode(double x, double y, double z) {
		static MergeNode of(TrafficRouteSegment segment) {
			return new MergeNode(segment.endX(), segment.endY(), segment.endZ());
		}
	}

}
