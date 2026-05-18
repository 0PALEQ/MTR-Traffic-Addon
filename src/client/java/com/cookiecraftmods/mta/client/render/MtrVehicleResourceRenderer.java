package com.cookiecraftmods.mta.client.render;

import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import com.mojang.math.Axis;
import org.mtr.core.data.TransportMode;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.PartCondition;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.resource.VehicleResourceCache;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class MtrVehicleResourceRenderer implements ClientTrafficVehicleRenderer {
	private static final ClientTrafficVehicleRenderer FALLBACK_RENDERER = new PlaceholderTrafficVehicleRenderer();
	private static final int SINGLE_VEHICLE_CAR_COUNT = 1;
	private static final Set<String> WARNED_RENDER_FAILURES = new HashSet<>();

	@Override
	public void render(ClientTrafficRenderContext context, ClientTrafficDebugRenderState snapshot, ClientTrafficVisualProfile visualProfile) {
		final VehicleResource vehicleResource = resolveVehicleResource(snapshot.visualId());
		if (vehicleResource == null) {
			FALLBACK_RENDERER.render(context, snapshot, visualProfile);
			return;
		}

		final VehicleResourceCache vehicleResourceCache = vehicleResource.getCachedVehicleResource(0, SINGLE_VEHICLE_CAR_COUNT, false);
		if (vehicleResourceCache == null || vehicleResourceCache.optimizedModels == null || vehicleResourceCache.optimizedModels.isEmpty()) {
			FALLBACK_RENDERER.render(context, snapshot, visualProfile);
			return;
		}

		boolean pushed = false;
		boolean useFallback = false;
		try {
			context.poseStack().pushPose();
			pushed = true;
			context.poseStack().translate(
				snapshot.x() - context.cameraPosition().x,
				snapshot.y() - context.cameraPosition().y,
				snapshot.z() - context.cameraPosition().z
			);
			context.poseStack().mulPose(Axis.YP.rotationDegrees(90.0F - snapshot.yawDegrees()));
			context.poseStack().mulPose(Axis.XP.rotationDegrees(180.0F));
			final ModelOffset modelOffset = modelOffset(vehicleResourceCache);
			context.poseStack().translate(modelOffset.x(), modelOffset.y(), modelOffset.z());

			GraphicsHolder.createInstanceSafe(context.poseStack(), context.bufferSource(), graphicsHolder -> {
				queue(graphicsHolder, vehicleResourceCache);
				CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.render(false);
			});
		} catch (Exception e) {
			useFallback = true;
			if (WARNED_RENDER_FAILURES.add(snapshot.visualId())) {
				com.cookiecraftmods.mta.MTRTrafficAddon.LOGGER.warn("Failed to render MTR traffic vehicle resource {}; using debug fallback", snapshot.visualId(), e);
			}
		} finally {
			if (pushed) {
				context.poseStack().popPose();
			}
		}

		if (useFallback) {
			FALLBACK_RENDERER.render(context, snapshot, visualProfile);
		}
	}

	private static void queue(GraphicsHolder graphicsHolder, VehicleResourceCache vehicleResourceCache) {
		final OptimizedModelWrapper normalModel = vehicleResourceCache.optimizedModels.get(PartCondition.NORMAL);
		if (normalModel != null) {
			CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(normalModel, graphicsHolder, GraphicsHolder.getDefaultLight());
		}

		final OptimizedModelWrapper doorsClosedModel = vehicleResourceCache.optimizedModelsDoorsClosed.get(PartCondition.DOORS_CLOSED);
		if (doorsClosedModel != null) {
			CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(doorsClosedModel, graphicsHolder, GraphicsHolder.getDefaultLight());
		}

		if (normalModel == null && doorsClosedModel == null) {
			for (OptimizedModelWrapper optimizedModelWrapper : vehicleResourceCache.optimizedModels.values()) {
				CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(optimizedModelWrapper, graphicsHolder, GraphicsHolder.getDefaultLight());
			}
			for (OptimizedModelWrapper optimizedModelWrapper : vehicleResourceCache.optimizedModelsDoorsClosed.values()) {
				CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(optimizedModelWrapper, graphicsHolder, GraphicsHolder.getDefaultLight());
			}
		}
	}

	private static ModelOffset modelOffset(VehicleResourceCache vehicleResourceCache) {
		final Bounds bounds = new Bounds();
		vehicleResourceCache.floors.forEach(bounds::include);
		vehicleResourceCache.doorways.forEach(bounds::include);
		if (!bounds.hasAny()) {
			return ModelOffset.NONE;
		}

		return new ModelOffset(
			0.0D,
			-bounds.minY,
			0.0D
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

	private record ModelOffset(double x, double y, double z) {
		private static final ModelOffset NONE = new ModelOffset(0.0D, 0.0D, 0.0D);
	}

	private static final class Bounds {
		private double minY = Double.POSITIVE_INFINITY;

		private void include(Box box) {
			minY = Math.min(minY, box.getMinYMapped());
		}

		private boolean hasAny() {
			return Double.isFinite(minY);
		}
	}
}
