package com.cookiecraftmods.mta.client.render;

import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.VehicleResource;

import java.util.concurrent.atomic.AtomicReference;

public record ClientTrafficVisualProfile(
	double lengthMeters,
	double widthMeters,
	double heightMeters,
	double noseLengthMeters
) {
	private static final double DEFAULT_WIDTH = 0.9D;
	private static final double DEFAULT_HEIGHT = 0.95D;
	private static final double DEFAULT_NOSE_LENGTH = 0.25D;

	public static ClientTrafficVisualProfile fromSnapshot(ClientTrafficDebugRenderState snapshot) {
		final VehicleResource vehicleResource = resolveVehicleResource(snapshot.visualId());
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

	private static VehicleResource resolveVehicleResource(String visualId) {
		if (visualId == null || visualId.isBlank()) {
			return null;
		}

		final AtomicReference<VehicleResource> reference = new AtomicReference<>();
		CustomResourceLoader.getVehicleById(TransportMode.TRAIN, visualId, pair -> reference.set(pair.left()));
		return reference.get();
	}
}
