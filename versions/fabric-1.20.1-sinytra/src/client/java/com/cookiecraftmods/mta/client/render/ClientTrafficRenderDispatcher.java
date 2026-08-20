
package com.cookiecraftmods.mta.client.render;

import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugState;
import com.cookiecraftmods.mta.client.render.custom.CustomTrafficVehicleRenderer;
import com.cookiecraftmods.mta.config.TrafficAddonConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import org.mtr.mapping.mapper.GraphicsHolder;

public final class ClientTrafficRenderDispatcher {
	private static final ClientTrafficVehicleRenderer CUSTOM_MODEL_RENDERER = new CustomTrafficVehicleRenderer();
	private static final MtrVehicleResourceRenderer MTR_RESOURCE_RENDERER = new MtrVehicleResourceRenderer();
	private static final ClientTrafficVehicleRenderer PLACEHOLDER_RENDERER = new PlaceholderTrafficVehicleRenderer();

	private ClientTrafficRenderDispatcher() {
	}

	public static void render(WorldRenderContext context) {
		if (context.consumers() == null || context.camera() == null || context.matrixStack() == null) {
			return;
		}

		GraphicsHolder.createInstanceSafe(context.matrixStack(), context.consumers(), graphicsHolder -> {
			final ClientTrafficRenderContext renderContext = new ClientTrafficRenderContext(
				context.matrixStack(),
				context.consumers(),
				context.consumers().getBuffer(RenderType.lines()),
				context.consumers().getBuffer(RenderType.debugFilledBox()),
				context.camera().getPosition(),
				maxRenderDistanceBlocks(),
				graphicsHolder
			);

			renderContext.poseStack().pushPose();
			MTR_RESOURCE_RENDERER.beginFrame();
			try {
				for (ClientTrafficDebugRenderState snapshot : ClientTrafficDebugState.allInterpolated()) {
					if (!isInRenderRange(renderContext, snapshot)) {
						continue;
					}
					renderVehicle(renderContext, snapshot);
				}
			} finally {
				MTR_RESOURCE_RENDERER.endFrame();
				renderContext.poseStack().popPose();
			}
		});
	}

	private static void renderVehicle(ClientTrafficRenderContext context, ClientTrafficDebugRenderState snapshot) {
		final ClientTrafficVisualProfile visualProfile = ClientTrafficVisualProfile.fromSnapshot(snapshot);
		if (CUSTOM_MODEL_RENDERER.tryRender(context, snapshot, visualProfile)) {
			return;
		}
		if (MTR_RESOURCE_RENDERER.tryRender(context, snapshot, visualProfile)) {
			return;
		}
		PLACEHOLDER_RENDERER.tryRender(context, snapshot, visualProfile);
	}

	private static boolean isInRenderRange(ClientTrafficRenderContext context, ClientTrafficDebugRenderState snapshot) {
		final double dx = snapshot.x() - context.cameraPosition().x;
		final double dz = snapshot.z() - context.cameraPosition().z;
		if (dx * dx + dz * dz > context.maxRenderDistanceBlocks() * context.maxRenderDistanceBlocks()) {
			return false;
		}

		final Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level != null;
	}

	private static double maxRenderDistanceBlocks() {
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.options == null) {
			return 160.0D;
		}

		final int renderDistanceChunks = Math.max(2, minecraft.options.getEffectiveRenderDistance());
		return TrafficAddonConfig.trafficVehicleVisibilityDistanceBlocks(renderDistanceChunks);
	}
}
