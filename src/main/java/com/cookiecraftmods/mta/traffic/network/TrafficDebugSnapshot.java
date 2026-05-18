package com.cookiecraftmods.mta.traffic.network;

import java.util.UUID;

public record TrafficDebugSnapshot(
	UUID id,
	String visualId,
	String vehicleType,
	double lengthMeters,
	double x,
	double y,
	double z,
	float yawDegrees,
	double speedKph
) {
}
