package com.cookiecraftmods.mta.client.render;

import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.mtr.core.data.TransportMode;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.resource.VehicleResourceCache;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class MtrVehicleResourceRenderer implements ClientTrafficVehicleRenderer {
	private static final ClientTrafficVehicleRenderer FALLBACK_RENDERER = new PlaceholderTrafficVehicleRenderer();
	private static final int SINGLE_VEHICLE_CAR_COUNT = 1;
	private static final String BATCH_RENDER_WARNING_KEY = "<batch-render>";
	private static final Set<String> WARNED_RENDER_FAILURES = new HashSet<>();
	private static final Map<String, Optional<VehicleResource>> VEHICLE_RESOURCES = new ConcurrentHashMap<>();
	private static final Map<String, Optional<VehicleResourceCache>> VEHICLE_RESOURCE_CACHES = new ConcurrentHashMap<>();
	private static final String LEGACY_SEDAN_VISUAL_ID = "mtr_traffic_addon_sedan:sedan";
	private static final String MTR_SEDAN_VISUAL_ID = "mta_sedan";
	private boolean frameActive;
	private boolean frameHasQueuedModels;

	void beginFrame() {
		frameActive = true;
		frameHasQueuedModels = false;
	}

	void endFrame() {
		try {
			if (frameHasQueuedModels) {
				CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.render(false);
			}
		} catch (Exception e) {
			if (WARNED_RENDER_FAILURES.add(BATCH_RENDER_WARNING_KEY)) {
				com.cookiecraftmods.mta.MTRTrafficAddon.LOGGER.warn("Failed to flush batched MTR traffic vehicle models", e);
			}
		} finally {
			frameActive = false;
			frameHasQueuedModels = false;
		}
	}

	static void clearResourceCache() {
		VEHICLE_RESOURCES.clear();
		VEHICLE_RESOURCE_CACHES.clear();
	}

	@Override
	public void render(ClientTrafficRenderContext context, ClientTrafficDebugRenderState snapshot, ClientTrafficVisualProfile visualProfile) {
		final VehicleResourceCache vehicleResourceCache = resolveVehicleResourceCache(snapshot.visualId());
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
			context.poseStack().mulPose(Axis.XP.rotationDegrees(-snapshot.pitchDegrees()));
			context.poseStack().mulPose(Axis.XP.rotationDegrees(180.0F));

			queue(context.graphicsHolder(), vehicleResourceCache, lightFor(snapshot));
			frameHasQueuedModels = true;
			if (!frameActive) {
				CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.render(false);
				frameHasQueuedModels = false;
			}
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

	private static void queue(GraphicsHolder graphicsHolder, VehicleResourceCache vehicleResourceCache, int light) {
		if (!vehicleResourceCache.optimizedModelsDoorsClosed.isEmpty()) {
			queueAll(graphicsHolder, vehicleResourceCache.optimizedModelsDoorsClosed.values(), light);
		} else {
			queueAll(graphicsHolder, vehicleResourceCache.optimizedModels.values(), light);
		}
	}

	private static void queueAll(GraphicsHolder graphicsHolder, Iterable<OptimizedModelWrapper> optimizedModelWrappers, int light) {
		for (OptimizedModelWrapper optimizedModelWrapper : optimizedModelWrappers) {
			CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.queue(optimizedModelWrapper, graphicsHolder, light);
		}
	}

	private static int lightFor(ClientTrafficDebugRenderState snapshot) {
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return GraphicsHolder.getDefaultLight();
		}
		return LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(snapshot.x(), snapshot.y() + 0.5D, snapshot.z()));
	}

	static VehicleResource resolveVehicleResource(String visualId) {
		if (visualId == null || visualId.isBlank()) {
			return null;
		}
		final String resolvedVisualId = remapLegacyVisualId(visualId);
		return VEHICLE_RESOURCES.computeIfAbsent(resolvedVisualId, id -> {
			final AtomicReference<VehicleResource> reference = new AtomicReference<>();
			CustomResourceLoader.getVehicleById(TransportMode.TRAIN, id, pair -> reference.set(pair.left()));
			return Optional.ofNullable(reference.get());
		}).orElse(null);
	}

	private static VehicleResourceCache resolveVehicleResourceCache(String visualId) {
		if (visualId == null || visualId.isBlank()) {
			return null;
		}
		final String resolvedVisualId = remapLegacyVisualId(visualId);
		return VEHICLE_RESOURCE_CACHES.computeIfAbsent(resolvedVisualId, id -> {
			final VehicleResource resource = resolveVehicleResource(id);
			return Optional.ofNullable(resource == null ? null : resource.getCachedVehicleResource(0, SINGLE_VEHICLE_CAR_COUNT, false));
		}).orElse(null);
	}

	private static String remapLegacyVisualId(String visualId) {
		return LEGACY_SEDAN_VISUAL_ID.equals(visualId) ? MTR_SEDAN_VISUAL_ID : visualId;
	}
}
