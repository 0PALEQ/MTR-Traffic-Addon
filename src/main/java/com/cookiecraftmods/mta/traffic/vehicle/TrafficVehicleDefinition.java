package com.cookiecraftmods.mta.traffic.vehicle;

public record TrafficVehicleDefinition(
	String id,
	String type,
	double lengthMeters,
	double maxSpeedKph,
	int spawnWeight,
	String visualId,
	Double accelerationMetersPerSecondSquared,
	Double brakingMetersPerSecondSquared
) {
	private static final double DEFAULT_ACCELERATION_METERS_PER_SECOND_SQUARED = 1.2D;
	private static final double DEFAULT_BRAKING_METERS_PER_SECOND_SQUARED = 2.4D;

	public String effectiveVisualId() {
		return visualId == null || visualId.isBlank() ? id : visualId;
	}

	public double effectiveAccelerationMetersPerSecondSquared() {
		return accelerationMetersPerSecondSquared == null || accelerationMetersPerSecondSquared <= 0.0D ? DEFAULT_ACCELERATION_METERS_PER_SECOND_SQUARED : accelerationMetersPerSecondSquared;
	}

	public double effectiveBrakingMetersPerSecondSquared() {
		return brakingMetersPerSecondSquared == null || brakingMetersPerSecondSquared <= 0.0D ? DEFAULT_BRAKING_METERS_PER_SECOND_SQUARED : brakingMetersPerSecondSquared;
	}
}
