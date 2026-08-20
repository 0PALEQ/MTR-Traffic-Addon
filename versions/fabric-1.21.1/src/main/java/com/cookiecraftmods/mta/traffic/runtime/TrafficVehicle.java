package com.cookiecraftmods.mta.traffic.runtime;

import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TrafficVehicle {
	private static final double MIN_LATERAL_OFFSET_METERS = -0.3D;
	private static final double MAX_LATERAL_OFFSET_METERS = 0.3D;

	private final UUID id;
	private final TrafficVehicleDefinition definition;
	private final TrafficRoute route;
	private final String spawnPointId;
	private final String despawnPointId;
	private final double lateralOffsetMeters;

	private double smoothedSpeedKph;
	private TrafficVehiclePosition cachedCurrentPosition;
	private int cachedPositionSegmentIndex = -1;
	private double cachedPositionDistanceMeters = Double.NaN;
	
	private int segmentIndex;
	private double distanceOnSegmentMeters;
	private double speedKph;

	public TrafficVehicle(UUID id, TrafficVehicleDefinition definition, TrafficRoute route, String spawnPointId, String despawnPointId, double distanceOnSegmentMeters, double speedKph) {
		this(id, definition, route, spawnPointId, despawnPointId, 0, distanceOnSegmentMeters, speedKph);
	}

	public TrafficVehicle(UUID id, TrafficVehicleDefinition definition, TrafficRoute route, String spawnPointId, String despawnPointId, int segmentIndex, double distanceOnSegmentMeters, double speedKph) {
		this.id = id;
		this.definition = definition;
		this.route = route;
		this.spawnPointId = spawnPointId;
		this.despawnPointId = despawnPointId;
		this.lateralOffsetMeters = deterministicLateralOffset(id);
		this.segmentIndex = segmentIndex;
		this.distanceOnSegmentMeters = distanceOnSegmentMeters;
		this.speedKph = speedKph;
		this.smoothedSpeedKph = Math.max(0.0D, speedKph);
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

	public double smoothedSpeedKph() {
		return smoothedSpeedKph;
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
			return new TrafficVehiclePosition(0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
		}

		final int currentSegmentIndex = Math.min(segmentIndex, segments.size() - 1);
		if (cachedCurrentPosition == null || cachedPositionSegmentIndex != currentSegmentIndex || Double.compare(cachedPositionDistanceMeters, distanceOnSegmentMeters) != 0) {
			cachedCurrentPosition = positionOnSegment(segments.get(currentSegmentIndex), distanceOnSegmentMeters);
			cachedPositionSegmentIndex = currentSegmentIndex;
			cachedPositionDistanceMeters = distanceOnSegmentMeters;
		}
		return cachedCurrentPosition;
	}

	public TrafficVehiclePosition positionOnSegment(TrafficRouteSegment segment, double distanceMeters) {
		final PathSample pathSample = samplePath(segment, distanceMeters);
		final double yawRadians = Math.toRadians(pathSample.yawDegrees());
		final double x = pathSample.x() - Math.sin(yawRadians) * lateralOffsetMeters;
		final double y = pathSample.y();
		final double z = pathSample.z() + Math.cos(yawRadians) * lateralOffsetMeters;
		final float yawDegrees = pathSample.yawDegrees();
		final float pitchDegrees = pathSample.pitchDegrees();
		return new TrafficVehiclePosition(x, y, z, yawDegrees, pitchDegrees);
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
		
		final double nextSpeedMetersPerSecond;
		if (previousSpeedMetersPerSecond < targetSpeedMetersPerSecond) {
			nextSpeedMetersPerSecond = Math.min(targetSpeedMetersPerSecond, previousSpeedMetersPerSecond + accelerationMetersPerSecondSquared * tickDurationSeconds);
		} else {
			nextSpeedMetersPerSecond = targetSpeedMetersPerSecond;
		}

		speedKph = nextSpeedMetersPerSecond * 3.6D;
		smoothedSpeedKph += (speedKph - smoothedSpeedKph) * 0.25D;
		
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

	private static double deterministicLateralOffset(UUID id) {
		final long hash = id == null ? 0L : id.getMostSignificantBits() ^ id.getLeastSignificantBits();
		final double normalized = (double) Long.remainderUnsigned(hash, 10_000L) / 9_999.0D;
		return MIN_LATERAL_OFFSET_METERS + normalized * (MAX_LATERAL_OFFSET_METERS - MIN_LATERAL_OFFSET_METERS);
	}

	private static PathSample samplePath(TrafficRouteSegment segment, double distanceMeters) {
		final List<TrafficPathPoint> path = segment.path();
		if (path.size() < 2) {
			final double progress = segment.lengthMeters() <= 0.0D ? 0.0D : Math.min(1.0D, Math.max(0.0D, distanceMeters / segment.lengthMeters()));
			final double x = lerp(segment.startX(), segment.endX(), progress);
			final double y = lerp(segment.startY(), segment.endY(), progress);
			final double z = lerp(segment.startZ(), segment.endZ(), progress);
			final Orientation orientation = orientation(
				segment.endX() - segment.startX(),
				segment.endY() - segment.startY(),
				segment.endZ() - segment.startZ()
			);
			return new PathSample(x, y, z, orientation.yawDegrees(), orientation.pitchDegrees());
		}

		final double normalizedDistance = segment.lengthMeters() <= 0.0D
			? 0.0D
			: Math.min(1.0D, Math.max(0.0D, distanceMeters / segment.lengthMeters()));
		final double scaledIndex = normalizedDistance * (path.size() - 1.0D);
		final int previousIndex = Math.min(path.size() - 2, (int) Math.floor(scaledIndex));
		final double progress = Math.min(1.0D, Math.max(0.0D, scaledIndex - previousIndex));
		final TrafficPathPoint previous = path.get(previousIndex);
		final TrafficPathPoint next = path.get(previousIndex + 1);
		final Orientation orientation = orientation(
			next.x() - previous.x(),
			next.y() - previous.y(),
			next.z() - previous.z()
		);
		return new PathSample(
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

	private record PathSample(double x, double y, double z, float yawDegrees, float pitchDegrees) {
	}

	private record Orientation(float yawDegrees, float pitchDegrees) {
	}
}
