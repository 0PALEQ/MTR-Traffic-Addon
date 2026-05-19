package com.cookiecraftmods.mta.traffic.runtime;

import com.cookiecraftmods.mta.traffic.TrafficManager;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TrafficSpacingResolver {
	private static final double MIN_SPACING_BUFFER_METERS = 2.0D;
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

		final Map<String, List<TrafficVehicle>> byConnector = vehicles.stream()
			.filter(vehicle -> vehicle.currentConnectorId().isPresent())
			.collect(Collectors.groupingBy(vehicle -> vehicle.currentConnectorId().orElseThrow()));

		for (List<TrafficVehicle> connectorVehicles : byConnector.values()) {
			connectorVehicles.sort(Comparator.comparingDouble(TrafficVehicle::distanceOnSegmentMeters).reversed());

			for (int i = 1; i < connectorVehicles.size(); i++) {
				final TrafficVehicle frontVehicle = connectorVehicles.get(i - 1);
				final TrafficVehicle followingVehicle = connectorVehicles.get(i);
				final double limitedSpeed = resolveFollowingSpeed(frontVehicle, followingVehicle, allowedSpeeds.get(followingVehicle));
				allowedSpeeds.put(followingVehicle, limitedSpeed);
			}
		}

		applyRouteLookaheadSpacing(vehicles, allowedSpeeds);
		applyMtrVehicleSpacing(vehicles, allowedSpeeds);
		applySignalLimits(vehicles, allowedSpeeds);
		return allowedSpeeds;
	}

	private static double resolveFollowingSpeed(TrafficVehicle frontVehicle, TrafficVehicle followingVehicle, double currentLimitKph) {
		final double minGap = frontVehicle.definition().lengthMeters() / 2.0D
			+ followingVehicle.definition().lengthMeters() / 2.0D
			+ MIN_SPACING_BUFFER_METERS;
		final double actualGap = frontVehicle.distanceOnSegmentMeters() - followingVehicle.distanceOnSegmentMeters();
		final double clearance = actualGap - minGap;

		if (clearance <= 0.0D) {
			return 0.0D;
		}

		final double lookaheadGap = minGap + LOOKAHEAD_BUFFER_METERS + followingVehicle.definition().lengthMeters();
		final double brakingCapKph = Math.sqrt(2.0D * followingVehicle.definition().effectiveBrakingMetersPerSecondSquared() * clearance) * 3.6D;
		if (actualGap >= lookaheadGap) {
			return Math.max(0.0D, Math.min(currentLimitKph, brakingCapKph));
		}

		final double progress = (actualGap - minGap) / Math.max(lookaheadGap - minGap, 0.001D);
		final double cappedByFrontSpeed = frontVehicle.speedKph() + Math.max(0.0D, progress) * Math.max(0.0D, currentLimitKph - frontVehicle.speedKph());
		return Math.max(0.0D, Math.min(currentLimitKph, Math.min(cappedByFrontSpeed, brakingCapKph)));
	}

	private static void applyRouteLookaheadSpacing(Collection<TrafficVehicle> vehicles, Map<TrafficVehicle, Double> allowedSpeeds) {
		for (TrafficVehicle followingVehicle : vehicles) {
			final RouteObstacle obstacle = closestRouteObstacle(vehicles, followingVehicle);
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

	private static RouteObstacle closestRouteObstacle(Collection<TrafficVehicle> vehicles, TrafficVehicle followingVehicle) {
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

			for (TrafficVehicle frontVehicle : vehicles) {
				if (frontVehicle == followingVehicle) {
					continue;
				}

				final TrafficRouteSegment frontSegment = frontVehicle.currentSegment().orElse(null);
				if (frontSegment == null || !sameDirectedSegment(candidateSegment, frontSegment)) {
					continue;
				}

				final double distanceToFrontVehicle = distanceToSegmentStart + frontVehicle.distanceOnSegmentMeters();
				if (distanceToFrontVehicle <= 0.0D || distanceToFrontVehicle > ROUTE_OCCUPANCY_LOOKAHEAD_METERS) {
					continue;
				}

				if (closestObstacle == null || distanceToFrontVehicle < closestObstacle.distanceMeters()) {
					closestObstacle = new RouteObstacle(frontVehicle, distanceToFrontVehicle);
				}
			}

			distanceToSegmentStart += Math.max(candidateSegment.lengthMeters(), 0.0D);
		}

		return closestObstacle;
	}

	private static double resolveProjectedFollowingSpeed(TrafficVehicle frontVehicle, TrafficVehicle followingVehicle, double actualGap, double currentLimitKph) {
		return resolveProjectedFollowingSpeed(frontVehicle.definition().lengthMeters(), frontVehicle.speedKph(), followingVehicle, actualGap, currentLimitKph);
	}

	private static double resolveProjectedFollowingSpeed(double frontVehicleLengthMeters, double frontVehicleSpeedKph, TrafficVehicle followingVehicle, double actualGap, double currentLimitKph) {
		final double minGap = frontVehicleLengthMeters / 2.0D
			+ followingVehicle.definition().lengthMeters() / 2.0D
			+ MIN_SPACING_BUFFER_METERS;
		final double clearance = actualGap - minGap;
		if (clearance <= 0.0D) {
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

	private static boolean sameDirectedSegment(TrafficRouteSegment first, TrafficRouteSegment second) {
		return first.directedConnectorId().equals(second.directedConnectorId());
	}

	private static void applySignalLimits(Collection<TrafficVehicle> vehicles, Map<TrafficVehicle, Double> allowedSpeeds) {
		for (TrafficVehicle vehicle : vehicles) {
			final TrafficRouteSegment currentSegment = vehicle.currentSegment().orElse(null);
			if (currentSegment == null) {
				continue;
			}

			if (!currentSegment.signalColors().isEmpty() && vehicle.distanceOnSegmentMeters() <= SIGNAL_APPROACH_LOOKAHEAD_METERS && isSignalSectionOccupiedByOtherVehicle(vehicles, vehicle, currentSegment)) {
				allowedSpeeds.put(vehicle, 0.0D);
				continue;
			}

			final TrafficRouteSegment nextSegment = vehicle.nextSegment().orElse(null);
			if (nextSegment == null || nextSegment.signalColors().isEmpty() || !isSignalSectionOccupiedByOtherVehicle(vehicles, vehicle, nextSegment)) {
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

	private static boolean isSignalSectionOccupiedByOtherVehicle(Collection<TrafficVehicle> vehicles, TrafficVehicle candidateVehicle, TrafficRouteSegment candidateSegment) {
		for (TrafficVehicle otherVehicle : vehicles) {
			if (otherVehicle == candidateVehicle) {
				continue;
			}

			final TrafficRouteSegment otherSegment = otherVehicle.currentSegment().orElse(null);
			if (otherSegment != null && overlaps(candidateSegment.signalColors(), otherSegment.signalColors())) {
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
