package com.cookiecraftmods.mta.client.debug;

import java.util.UUID;

public record ClientTrafficDebugRenderState(
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
