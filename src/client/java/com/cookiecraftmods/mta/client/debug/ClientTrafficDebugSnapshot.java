package com.cookiecraftmods.mta.client.debug;

import java.util.UUID;

public record ClientTrafficDebugSnapshot(
	UUID id,
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
