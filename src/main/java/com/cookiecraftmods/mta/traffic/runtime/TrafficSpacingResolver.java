package com.cookiecraftmods.mta.traffic.runtime;

import com.cookiecraftmods.mta.traffic.TrafficManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private static final double SPATIAL_VERTICAL_CLEARANCE_METERS = 2.0D;
	private static final double SPATIAL_INDEX_CELL_SIZE_METERS = 16.0D;
	private static final double MERGE_NODE_TOLERANCE_METERS = 0.5D;
	private static final double MERGE_PRIORITY_DISTANCE_EPSILON_METERS = 0.25D;
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
				final double limitedSpeed = resolveFollowingSpeed(frontVehicle, followingVehicle, allowedSpeeds.get(followingVehicle));
				allowedSpeeds.put(followingVehicle, limitedSpeed);
			}
		}

		applyRouteLookaheadSpacing(vehicles, byConnector, allowedSpeeds);
		applySpatialSpacing(vehicles, allowedSpeeds);
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

	private static double resolveFollowingSpeed(TrafficVehicle frontVehicle, TrafficVehicle followingVehicle, double currentLimitKph) {
		final double minGap = frontVehicle.definition().lengthMeters() / 2.0D
			+ followingVehicle.definition().lengthMeters() / 2.0D
			+ followingGapMeters(followingVehicle);
		final double actualGap = frontVehicle.distanceOnSegmentMeters() - followingVehicle.distanceOnSegmentMeters();
		final double clearance = actualGap - minGap;

		if (clearance <= 0.0D) {
			return 0.0D;
		}

		final double frontSpeedKph = frontVehicle.smoothedSpeedKph();
		if (shouldHoldInStandingQueue(frontSpeedKph, followingVehicle, clearance)) {
			return 0.0D;
		}

		final double lookaheadGap = minGap + LOOKAHEAD_BUFFER_METERS + followingVehicle.definition().lengthMeters();
		final double brakingCapKph = Math.sqrt(2.0D * followingVehicle.definition().effectiveBrakingMetersPerSecondSquared() * clearance) * 3.6D;
		if (actualGap >= lookaheadGap) {
			return Math.max(0.0D, Math.min(currentLimitKph, brakingCapKph));
		}

		final double progress = (actualGap - minGap) / Math.max(lookaheadGap - minGap, 0.001D);
		final double cappedByFrontSpeed = frontSpeedKph + Math.max(0.0D, progress) * Math.max(0.0D, currentLimitKph - frontSpeedKph);
		return Math.max(0.0D, Math.min(currentLimitKph, Math.min(cappedByFrontSpeed, brakingCapKph)));
	}

	private static void applyRouteLookaheadSpacing(Collection<TrafficVehicle> vehicles, Map<String, List<TrafficVehicle>> vehiclesByConnector, Map<TrafficVehicle, Double> allowedSpeeds) {
		for (TrafficVehicle followingVehicle : vehicles) {
			final RouteObstacle obstacle = closestRouteObstacle(vehiclesByConnector, followingVehicle);
			if (obstacle == null) {
				continue;
			}

			final double currentLimitKph = allowedSpeeds.getOrDefault(followingVehicle, 0.0D);
			final double limitedSpeed = resolveProjectedFollowingSpeed(obstacle.frontVehicle(), followingVehicle, obstacle.distanceMeters(), currentLimitKph);
			allowedSpeeds.put(followingVehicle, Math.min(currentLimitKph, limitedSpeed));
		}
	}

	private static void applyMtrVehicleSpacing(Collection<TrafficVehicle> vehicles, Map<TrafficVehicle, Double> allowedSpeeds) {
		for (TrafficVehicle followingVehicle : vehicles) {
			TrafficManager.closestMtrVehicleObstacle(followingVehicle).ifPresent(obstacle -> {
				final double currentLimitKph = allowedSpeeds.getOrDefault(followingVehicle, 0.0D);
				final double limitedSpeed = resolveProjectedFollowingSpeed(obstacle.lengthMeters(), obstacle.speedKph(), followingVehicle, obstacle.distanceMeters(), currentLimitKph);
				allowedSpeeds.put(followingVehicle, Math.min(currentLimitKph, limitedSpeed));
			});
		}
	}

	private static void applySpatialSpacing(Collection<TrafficVehicle> vehicles, Map<TrafficVehicle, Double> allowedSpeeds) {
		final Map<Long, List<VehicleSpatialSnapshot>> spatialIndex = new HashMap<>();
		for (TrafficVehicle vehicle : vehicles) {
			final TrafficVehiclePosition position = vehicle.currentPosition();
			spatialIndex.computeIfAbsent(spatialCellKey(position.x(), position.z()), ignored -> new ArrayList<>())
				.add(new VehicleSpatialSnapshot(vehicle, position));
		}

		final int cellRadius = (int) Math.ceil(SPATIAL_OCCUPANCY_LOOKAHEAD_METERS / SPATIAL_INDEX_CELL_SIZE_METERS);
		for (TrafficVehicle followingVehicle : vehicles) {
			final TrafficVehiclePosition followingPosition = followingVehicle.currentPosition();
			final long centerCellX = spatialCellCoordinate(followingPosition.x());
			final long centerCellZ = spatialCellCoordinate(followingPosition.z());
			final double yawRadians = Math.toRadians(followingPosition.yawDegrees());
			final double forwardX = Math.cos(yawRadians);
			final double forwardZ = Math.sin(yawRadians);
			VehicleSpatialObstacle closestObstacle = null;

			for (long offsetX = -cellRadius; offsetX <= cellRadius; offsetX++) {
				for (long offsetZ = -cellRadius; offsetZ <= cellRadius; offsetZ++) {
					final List<VehicleSpatialSnapshot> nearbyVehicles = spatialIndex.get(spatialCellKey(centerCellX + offsetX, centerCellZ + offsetZ));
					if (nearbyVehicles == null) {
						continue;
					}

					for (VehicleSpatialSnapshot nearby : nearbyVehicles) {
						if (nearby.vehicle() == followingVehicle || Math.abs(nearby.position().y() - followingPosition.y()) > SPATIAL_VERTICAL_CLEARANCE_METERS) {
							continue;
						}

						final double dx = nearby.position().x() - followingPosition.x();
						final double dz = nearby.position().z() - followingPosition.z();
						final double longitudinalDistance = dx * forwardX + dz * forwardZ;
						if (longitudinalDistance <= 0.0D || longitudinalDistance > SPATIAL_OCCUPANCY_LOOKAHEAD_METERS) {
							continue;
						}

						final double lateralDistance = Math.abs(-dx * forwardZ + dz * forwardX);
						if (lateralDistance >= SPATIAL_LATERAL_CLEARANCE_METERS) {
							continue;
						}

						if (projectsAsSpatialObstacle(nearby.position(), followingPosition)
							&& compareSpatialConflictPriority(followingVehicle, nearby.vehicle()) < 0) {
							continue;
						}

						if (closestObstacle == null || longitudinalDistance < closestObstacle.distanceMeters()) {
							closestObstacle = new VehicleSpatialObstacle(nearby.vehicle(), longitudinalDistance);
						}
					}
				}
			}

			if (closestObstacle != null) {
				final double currentLimitKph = allowedSpeeds.getOrDefault(followingVehicle, 0.0D);
				final double limitedSpeed = resolveProjectedFollowingSpeed(closestObstacle.frontVehicle(), followingVehicle, closestObstacle.distanceMeters(), currentLimitKph);
				allowedSpeeds.put(followingVehicle, Math.min(currentLimitKph, limitedSpeed));
			}
		}
	}

	private static boolean projectsAsSpatialObstacle(TrafficVehiclePosition observer, TrafficVehiclePosition candidate) {
		if (Math.abs(candidate.y() - observer.y()) > SPATIAL_VERTICAL_CLEARANCE_METERS) {
			return false;
		}

		final double yawRadians = Math.toRadians(observer.yawDegrees());
		final double forwardX = Math.cos(yawRadians);
		final double forwardZ = Math.sin(yawRadians);
		final double dx = candidate.x() - observer.x();
		final double dz = candidate.z() - observer.z();
		final double longitudinalDistance = dx * forwardX + dz * forwardZ;
		if (longitudinalDistance <= 0.0D || longitudinalDistance > SPATIAL_OCCUPANCY_LOOKAHEAD_METERS) {
			return false;
		}

		return Math.abs(-dx * forwardZ + dz * forwardX) < SPATIAL_LATERAL_CLEARANCE_METERS;
	}

	private static int compareSpatialConflictPriority(TrafficVehicle first, TrafficVehicle second) {
		if (approachesSameMerge(first, second)) {
			final double distanceDifference = first.distanceToEndOfCurrentSegmentMeters() - second.distanceToEndOfCurrentSegmentMeters();
			if (Math.abs(distanceDifference) > MERGE_PRIORITY_DISTANCE_EPSILON_METERS) {
				return distanceDifference < 0.0D ? -1 : 1;
			}
		}

		return first.id().compareTo(second.id());
	}

	private static boolean approachesSameMerge(TrafficVehicle first, TrafficVehicle second) {
		final TrafficRouteSegment firstCurrent = first.currentSegment().orElse(null);
		final TrafficRouteSegment secondCurrent = second.currentSegment().orElse(null);
		final TrafficRouteSegment firstNext = first.nextSegment().orElse(null);
		final TrafficRouteSegment secondNext = second.nextSegment().orElse(null);
		if (firstCurrent == null || secondCurrent == null || firstNext == null || secondNext == null
			|| firstCurrent.directedConnectorId().equals(secondCurrent.directedConnectorId())
			|| !firstNext.directedConnectorId().equals(secondNext.directedConnectorId())) {
			return false;
		}

		final double dx = firstCurrent.endX() - secondCurrent.endX();
		final double dy = firstCurrent.endY() - secondCurrent.endY();
		final double dz = firstCurrent.endZ() - secondCurrent.endZ();
		return dx * dx + dy * dy + dz * dz <= MERGE_NODE_TOLERANCE_METERS * MERGE_NODE_TOLERANCE_METERS;
	}

	private static long spatialCellCoordinate(double coordinate) {
		return (long) Math.floor(coordinate / SPATIAL_INDEX_CELL_SIZE_METERS);
	}

	private static long spatialCellKey(double x, double z) {
		return spatialCellKey(spatialCellCoordinate(x), spatialCellCoordinate(z));
	}

	private static long spatialCellKey(long cellX, long cellZ) {
		return (cellX & 0xFFFFFFFFL) | ((cellZ & 0xFFFFFFFFL) << 32);
	}

	private static RouteObstacle closestRouteObstacle(Map<String, List<TrafficVehicle>> vehiclesByConnector, TrafficVehicle followingVehicle) {
		final List<TrafficRouteSegment> followingSegments = followingVehicle.route().segments();
		if (followingSegments.isEmpty() || followingVehicle.segmentIndex() < 0 || followingVehicle.segmentIndex() >= followingSegments.size()) {
			return null;
		}

		RouteObstacle closestObstacle = null;
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
					closestObstacle = new RouteObstacle(frontVehicle, distanceToFrontVehicle);
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

	private static double resolveProjectedFollowingSpeed(TrafficVehicle frontVehicle, TrafficVehicle followingVehicle, double actualGap, double currentLimitKph) {
		return resolveProjectedFollowingSpeed(frontVehicle.definition().lengthMeters(), frontVehicle.smoothedSpeedKph(), followingVehicle, actualGap, currentLimitKph);
	}

	private static double resolveProjectedFollowingSpeed(double frontVehicleLengthMeters, double frontVehicleSpeedKph, TrafficVehicle followingVehicle, double actualGap, double currentLimitKph) {
		final double minGap = frontVehicleLengthMeters / 2.0D
			+ followingVehicle.definition().lengthMeters() / 2.0D
			+ followingGapMeters(followingVehicle);
		final double clearance = actualGap - minGap;
		if (clearance <= 0.0D) {
			return 0.0D;
		}

		if (shouldHoldInStandingQueue(frontVehicleSpeedKph, followingVehicle, clearance)) {
			return 0.0D;
		}

		final double brakingCapKph = Math.sqrt(2.0D * followingVehicle.definition().effectiveBrakingMetersPerSecondSquared() * clearance) * 3.6D;
		final double lookaheadGap = minGap + LOOKAHEAD_BUFFER_METERS + followingVehicle.definition().lengthMeters();
		if (actualGap >= lookaheadGap) {
			return Math.max(0.0D, Math.min(currentLimitKph, brakingCapKph));
		}

		final double progress = (actualGap - minGap) / Math.max(lookaheadGap - minGap, 0.001D);
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

	private record RouteObstacle(TrafficVehicle frontVehicle, double distanceMeters) {
	}

	private record VehicleSpatialSnapshot(TrafficVehicle vehicle, TrafficVehiclePosition position) {
	}

	private record VehicleSpatialObstacle(TrafficVehicle frontVehicle, double distanceMeters) {
	}
}
