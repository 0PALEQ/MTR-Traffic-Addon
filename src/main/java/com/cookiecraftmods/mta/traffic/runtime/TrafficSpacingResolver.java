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
}
