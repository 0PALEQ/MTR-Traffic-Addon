package com.cookiecraftmods.mta.traffic.runtime;

import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TrafficVehicle {
	private final UUID id;
	private final TrafficVehicleDefinition definition;
	private final TrafficRoute route;
	private final String spawnPointId;
	private final String despawnPointId;
	private int segmentIndex;
	private double distanceOnSegmentMeters;
	private double speedKph;

	public TrafficVehicle(UUID id, TrafficVehicleDefinition definition, TrafficRoute route, String spawnPointId, String despawnPointId, double distanceOnSegmentMeters, double speedKph) {
		this.id = id;
		this.definition = definition;
		this.route = route;
		this.spawnPointId = spawnPointId;
		this.despawnPointId = despawnPointId;
		this.distanceOnSegmentMeters = distanceOnSegmentMeters;
		this.speedKph = speedKph;
	}

	public UUID id() {
		return id;
	}

	public TrafficVehicleDefinition definition() {
		return definition;
	}

	public TrafficRoute route() {
		return route;
	}

	public String spawnPointId() {
		return spawnPointId;
	}

	public String despawnPointId() {
		return despawnPointId;
	}

	public int segmentIndex() {
		return segmentIndex;
	}

	public double distanceOnSegmentMeters() {
		return distanceOnSegmentMeters;
	}

	public double speedKph() {
		return speedKph;
	}

	public Optional<TrafficRouteSegment> currentSegment() {
		final List<TrafficRouteSegment> segments = route.segments();
		if (segments.isEmpty() || segmentIndex < 0 || segmentIndex >= segments.size()) {
			return Optional.empty();
		}

		return Optional.of(segments.get(segmentIndex));
	}

	public Optional<String> currentConnectorId() {
		return currentSegment().map(TrafficRouteSegment::directedConnectorId);
	}

	public Optional<TrafficRouteSegment> nextSegment() {
		final List<TrafficRouteSegment> segments = route.segments();
		final int nextSegmentIndex = segmentIndex + 1;
		if (segments.isEmpty() || nextSegmentIndex < 0 || nextSegmentIndex >= segments.size()) {
			return Optional.empty();
		}

		return Optional.of(segments.get(nextSegmentIndex));
	}

	public double distanceToEndOfCurrentSegmentMeters() {
		return currentSegment().map(segment -> Math.max(0.0D, segment.lengthMeters() - distanceOnSegmentMeters)).orElse(0.0D);
	}

	public double currentSegmentSpeedLimitKph() {
		return currentSegment().map(TrafficRouteSegment::speedLimitKph).orElse(0.0D);
	}

	public TrafficVehiclePosition currentPosition() {
		final List<TrafficRouteSegment> segments = route.segments();
		if (segments.isEmpty()) {
			return new TrafficVehiclePosition(0.0D, 0.0D, 0.0D, 0.0F);
		}

		final int currentSegmentIndex = Math.min(segmentIndex, segments.size() - 1);
		final TrafficRouteSegment currentSegment = segments.get(currentSegmentIndex);
		final PathSample pathSample = samplePath(currentSegment, distanceOnSegmentMeters);
		final double x = pathSample.x();
		final double y = pathSample.y();
		final double z = pathSample.z();
		final float yawDegrees = pathSample.yawDegrees();
		return new TrafficVehiclePosition(x, y, z, yawDegrees);
	}

	public boolean tick(double tickDurationSeconds, double allowedSpeedKph) {
		final List<TrafficRouteSegment> segments = route.segments();
		if (segments.isEmpty()) {
			return true;
		}

		final double previousSpeedMetersPerSecond = Math.max(0.0D, speedKph) / 3.6D;
		final double targetSpeedKph = speedTargetWithBrakingDistance(segments, allowedSpeedKph);
		final double targetSpeedMetersPerSecond = Math.max(0.0D, targetSpeedKph) / 3.6D;
		final double accelerationMetersPerSecondSquared = definition.effectiveAccelerationMetersPerSecondSquared();
		final double brakingMetersPerSecondSquared = definition.effectiveBrakingMetersPerSecondSquared();
		final double nextSpeedMetersPerSecond;
		if (previousSpeedMetersPerSecond < targetSpeedMetersPerSecond) {
			nextSpeedMetersPerSecond = Math.min(targetSpeedMetersPerSecond, previousSpeedMetersPerSecond + accelerationMetersPerSecondSquared * tickDurationSeconds);
		} else {
			nextSpeedMetersPerSecond = Math.max(targetSpeedMetersPerSecond, previousSpeedMetersPerSecond - brakingMetersPerSecondSquared * tickDurationSeconds);
		}

		speedKph = nextSpeedMetersPerSecond * 3.6D;
		double remainingDistanceMeters = (previousSpeedMetersPerSecond + nextSpeedMetersPerSecond) * 0.5D * tickDurationSeconds;

		while (remainingDistanceMeters > 0.0D && segmentIndex < segments.size()) {
			final TrafficRouteSegment currentSegment = segments.get(segmentIndex);
			final double remainingSegmentMeters = currentSegment.lengthMeters() - distanceOnSegmentMeters;

			if (remainingDistanceMeters < remainingSegmentMeters) {
				distanceOnSegmentMeters += remainingDistanceMeters;
				if (shouldDespawnOnCurrentSegment(segments)) {
					return true;
				}
				return false;
			}

			remainingDistanceMeters -= Math.max(remainingSegmentMeters, 0.0D);
			segmentIndex++;
			distanceOnSegmentMeters = 0.0D;

			if (segmentIndex >= segments.size()) {
				return true;
			}

			speedKph = Math.min(speedKph, Math.max(0.0D, Math.min(allowedSpeedKph, segments.get(segmentIndex).speedLimitKph())));
			if (shouldDespawnOnCurrentSegment(segments)) {
				return true;
			}
		}

		return false;
	}

	private double speedTargetWithBrakingDistance(List<TrafficRouteSegment> segments, double allowedSpeedKph) {
		if (segmentIndex < 0 || segmentIndex >= segments.size()) {
			return 0.0D;
		}

		final TrafficRouteSegment currentSegment = segments.get(segmentIndex);
		double targetSpeedKph = Math.max(0.0D, Math.min(allowedSpeedKph, Math.min(definition.maxSpeedKph(), currentSegment.speedLimitKph())));
		final double nextSegmentTargetSpeedKph = nextSegment()
			.map(segment -> Math.min(definition.maxSpeedKph(), segment.speedLimitKph()))
			.orElse(0.0D);

		if (nextSegmentTargetSpeedKph < targetSpeedKph) {
			final double distanceToBrakeMeters = Math.max(0.0D, currentSegment.lengthMeters() - distanceOnSegmentMeters);
			final double brakingMetersPerSecondSquared = definition.effectiveBrakingMetersPerSecondSquared();
			final double nextTargetMetersPerSecond = nextSegmentTargetSpeedKph / 3.6D;
			final double maxMetersPerSecondForBrakeDistance = Math.sqrt(nextTargetMetersPerSecond * nextTargetMetersPerSecond + 2.0D * brakingMetersPerSecondSquared * distanceToBrakeMeters);
			targetSpeedKph = Math.min(targetSpeedKph, maxMetersPerSecondForBrakeDistance * 3.6D);
		}

		return targetSpeedKph;
	}

	private boolean shouldDespawnOnCurrentSegment(List<TrafficRouteSegment> segments) {
		if (segmentIndex < 0 || segmentIndex >= segments.size()) {
			return false;
		}

		final TrafficRouteSegment currentSegment = segments.get(segmentIndex);
		if (!currentSegment.despawnConnector()) {
			return false;
		}

		final double activationDistance = Math.min(Math.max(definition.lengthMeters() * 0.25D, 0.5D), Math.max(currentSegment.lengthMeters(), 0.5D));
		return distanceOnSegmentMeters >= activationDistance || currentSegment.lengthMeters() <= activationDistance;
	}

	private static double lerp(double start, double end, double progress) {
		return start + (end - start) * progress;
	}

	private static PathSample samplePath(TrafficRouteSegment segment, double distanceMeters) {
		final List<TrafficPathPoint> path = segment.path();
		if (path.size() < 2) {
			final double progress = segment.lengthMeters() <= 0.0D ? 0.0D : Math.min(1.0D, Math.max(0.0D, distanceMeters / segment.lengthMeters()));
			final double x = lerp(segment.startX(), segment.endX(), progress);
			final double y = lerp(segment.startY(), segment.endY(), progress);
			final double z = lerp(segment.startZ(), segment.endZ(), progress);
			final float yawDegrees = (float) Math.toDegrees(Math.atan2(segment.endZ() - segment.startZ(), segment.endX() - segment.startX()));
			return new PathSample(x, y, z, yawDegrees);
		}

		double remaining = Math.max(0.0D, distanceMeters);
		for (int i = 1; i < path.size(); i++) {
			final TrafficPathPoint previous = path.get(i - 1);
			final TrafficPathPoint next = path.get(i);
			final double length = distance(previous, next);
			if (remaining <= length || i == path.size() - 1) {
				final double progress = length <= 0.0D ? 0.0D : Math.min(1.0D, remaining / length);
				final double x = lerp(previous.x(), next.x(), progress);
				final double y = lerp(previous.y(), next.y(), progress);
				final double z = lerp(previous.z(), next.z(), progress);
				final float yawDegrees = (float) Math.toDegrees(Math.atan2(next.z() - previous.z(), next.x() - previous.x()));
				return new PathSample(x, y, z, yawDegrees);
			}
			remaining -= length;
		}

		final TrafficPathPoint previous = path.get(path.size() - 2);
		final TrafficPathPoint last = path.get(path.size() - 1);
		final float yawDegrees = (float) Math.toDegrees(Math.atan2(last.z() - previous.z(), last.x() - previous.x()));
		return new PathSample(last.x(), last.y(), last.z(), yawDegrees);
	}

	private static double distance(TrafficPathPoint a, TrafficPathPoint b) {
		final double dx = a.x() - b.x();
		final double dy = a.y() - b.y();
		final double dz = a.z() - b.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private record PathSample(double x, double y, double z, float yawDegrees) {
	}
}
