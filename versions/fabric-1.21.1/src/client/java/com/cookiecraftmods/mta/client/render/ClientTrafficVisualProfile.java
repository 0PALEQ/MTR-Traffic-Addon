package com.cookiecraftmods.mta.client.render;

import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import org.mtr.resource.VehicleResource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record ClientTrafficVisualProfile(
	double lengthMeters,
	double widthMeters,
	double heightMeters,
	double noseLengthMeters
) {
	private static final double DEFAULT_WIDTH = 0.9D;
	private static final double DEFAULT_HEIGHT = 0.95D;
	private static final double DEFAULT_NOSE_LENGTH = 0.25D;
	private static final Map<String, ClientTrafficVisualProfile> PROFILES = new ConcurrentHashMap<>();

	public static ClientTrafficVisualProfile fromSnapshot(ClientTrafficDebugRenderState snapshot) {
		final String normalizedVisualId = MtrVehicleResourceRenderer.normalizeVisualId(snapshot.visualId());
		final String key = normalizedVisualId + '|' + Double.doubleToLongBits(snapshot.lengthMeters());
		return PROFILES.computeIfAbsent(key, ignored -> create(snapshot));
	}

	private static ClientTrafficVisualProfile create(ClientTrafficDebugRenderState snapshot) {
		final VehicleResource vehicleResource = MtrVehicleResourceRenderer.resolveVehicleResource(snapshot.visualId());
		final double length = vehicleResource != null && vehicleResource.getLength() > 0.5D ? vehicleResource.getLength() : Math.max(1.6D, snapshot.lengthMeters());
		final double width = vehicleResource != null && vehicleResource.getWidth() > 0.5D ? vehicleResource.getWidth() : DEFAULT_WIDTH;
		final double height = DEFAULT_HEIGHT;

		return new ClientTrafficVisualProfile(
			length,
			width,
			height,
			DEFAULT_NOSE_LENGTH
		);
	}

	static void clearCache() {
		PROFILES.clear();
	}
}
