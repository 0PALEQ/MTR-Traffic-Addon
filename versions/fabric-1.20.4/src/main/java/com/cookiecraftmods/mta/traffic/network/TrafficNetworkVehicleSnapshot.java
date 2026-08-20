package com.cookiecraftmods.mta.traffic.network;

import java.util.UUID;

public record TrafficNetworkVehicleSnapshot(
	UUID id,
	String dimensionId,
	String visualId,
	String vehicleType,
	double lengthMeters,
	double x,
	double y,
	double z,
	float yawDegrees,
	float pitchDegrees,
	double speedKph
) {
}
