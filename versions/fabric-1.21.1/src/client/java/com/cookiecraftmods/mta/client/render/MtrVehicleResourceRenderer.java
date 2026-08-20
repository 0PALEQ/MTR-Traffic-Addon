package com.cookiecraftmods.mta.client.render;

import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import com.cookiecraftmods.mta.mixin.BuiltVehicleModelHolderAccessor;
import com.mojang.math.Axis;
import org.mtr.client.CustomResourceLoader;
import org.mtr.core.data.TransportMode;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.model.BuiltVehicleModelHolder;
import org.mtr.model.NewOptimizedModel;
import org.mtr.render.MainRenderer;
import org.mtr.render.StoredMatrixTransformations;
import org.mtr.resource.PartCondition;
import org.mtr.resource.RenderStage;
import org.mtr.resource.VehicleResource;
import org.mtr.resource.VehicleResourceCache;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Renders MTR 4.1 vehicle resources as the addon's road-traffic visuals. */
public final class MtrVehicleResourceRenderer implements ClientTrafficVehicleRenderer {
	private static final int SINGLE_VEHICLE_CAR_COUNT = 1;
	private static final Set<String> WARNED_RENDER_FAILURES = new HashSet<>();
	private static final Map<String, VehicleResource> VEHICLE_RESOURCES = new ConcurrentHashMap<>();
	private static final Map<String, VehicleResourceCache> VEHICLE_RESOURCE_CACHES = new ConcurrentHashMap<>();
	private static final String LEGACY_SEDAN_VISUAL_ID = "mtr_traffic_addon_sedan:sedan";
	private static final String MTR_SEDAN_VISUAL_ID = "mta_sedan";

	static void clearResourceCache() {
		VEHICLE_RESOURCES.clear();
		VEHICLE_RESOURCE_CACHES.clear();
	}

	@Override
	public boolean tryRender(ClientTrafficRenderContext context, ClientTrafficDebugRenderState snapshot, ClientTrafficVisualProfile visualProfile) {
		final VehicleResourceCache vehicleResourceCache = resolveVehicleResourceCache(snapshot.visualId());
		if (vehicleResourceCache == null) {
			return false;
		}

		try {
			final StoredMatrixTransformations transformations = new StoredMatrixTransformations(snapshot.x(), snapshot.y(), snapshot.z());
			transformations.add(poseStack -> {
				poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - snapshot.yawDegrees()));
				poseStack.mulPose(Axis.XP.rotationDegrees(-snapshot.pitchDegrees()));
			});

			final boolean[] queued = {false};
			vehicleResourceCache.iterateModels((carIndex, modelHolder) -> {
				queueModelHolder(modelHolder, transformations, context.lightAt(snapshot.x(), snapshot.y() + 0.5D, snapshot.z()));
				queued[0] = true;
			});
			return queued[0];
		} catch (Exception e) {
			if (WARNED_RENDER_FAILURES.add(snapshot.visualId())) {
				com.cookiecraftmods.mta.MTRTrafficAddon.LOGGER.warn("Failed to render MTR traffic vehicle resource {}", snapshot.visualId(), e);
			}
			return false;
		}
	}

	private static void queueModelHolder(BuiltVehicleModelHolder modelHolder, StoredMatrixTransformations transformations, int light) {
		final BuiltVehicleModelHolderAccessor accessor = (BuiltVehicleModelHolderAccessor) (Object) modelHolder;
		queueAllConditions(accessor.mta$getBuiltModels(), transformations, light);
		for (BuiltVehicleModelHolder.BuiltDoorModelDetails doorModel : accessor.mta$getBuiltDoorModelDetailsList()) {
			final StoredMatrixTransformations doorTransformations = doorModel.modelPropertiesPart().getDoorOffset(transformations, null, doorModel.flipped());
			queueAllConditions(doorModel.models(), doorTransformations, light);
		}
	}

	private static void queueAllConditions(
		Object2ObjectOpenHashMap<PartCondition, Object2ObjectOpenHashMap<RenderStage, ObjectArrayList<NewOptimizedModel>>> models,
		StoredMatrixTransformations transformations,
		int light
	) {
		models.values().forEach(modelsByRenderStage -> MainRenderer.renderModel(modelsByRenderStage, transformations, light));
	}

	static VehicleResource resolveVehicleResource(String visualId) {
		if (visualId == null || visualId.isBlank()) {
			return null;
		}
		final String resolvedVisualId = normalizeVisualId(visualId);
		return VEHICLE_RESOURCES.computeIfAbsent(resolvedVisualId, id -> {
			final AtomicReference<VehicleResource> reference = new AtomicReference<>();
			CustomResourceLoader.getVehicleById(TransportMode.TRAIN, id, pair -> reference.set(pair.left()));
			return reference.get();
		});
	}

	private static VehicleResourceCache resolveVehicleResourceCache(String visualId) {
		if (visualId == null || visualId.isBlank()) {
			return null;
		}
		final String resolvedVisualId = normalizeVisualId(visualId);
		return VEHICLE_RESOURCE_CACHES.computeIfAbsent(resolvedVisualId, id -> {
			final VehicleResource resource = resolveVehicleResource(id);
			return resource == null ? null : resource.getCachedVehicleResource(0, SINGLE_VEHICLE_CAR_COUNT);
		});
	}

	static String normalizeVisualId(String visualId) {
		return LEGACY_SEDAN_VISUAL_ID.equals(visualId) ? MTR_SEDAN_VISUAL_ID : visualId;
	}
}
