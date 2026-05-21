package com.cookiecraftmods.mta.client.render;

import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugState;
import com.cookiecraftmods.mta.client.render.custom.CustomTrafficVehicleRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

public final class ClientTrafficRenderDispatcher {
	private static final ClientTrafficVehicleRenderer DEFAULT_RENDERER = new CustomTrafficVehicleRenderer(new MtrVehicleResourceRenderer());
	private static final double RENDER_DISTANCE_MARGIN_BLOCKS = 8.0D;

	private ClientTrafficRenderDispatcher() {
	}

	public static void render(WorldRenderContext context) {
		if (context.consumers() == null || context.camera() == null || context.matrixStack() == null) {
			return;
		}

		final ClientTrafficRenderContext renderContext = new ClientTrafficRenderContext(
			context.matrixStack(),
			context.consumers(),
			context.consumers().getBuffer(RenderType.lines()),
			context.consumers().getBuffer(RenderType.debugFilledBox()),
			context.camera().getPosition(),
			maxRenderDistanceBlocks()
		);

		renderContext.poseStack().pushPose();
		for (ClientTrafficDebugRenderState snapshot : ClientTrafficDebugState.allInterpolated()) {
			if (!isInRenderRange(renderContext, snapshot)) {
				continue;
			}
			DEFAULT_RENDERER.render(renderContext, snapshot, ClientTrafficVisualProfile.fromSnapshot(snapshot));
		}
		renderContext.poseStack().popPose();
	}

	private static boolean isInRenderRange(ClientTrafficRenderContext context, ClientTrafficDebugRenderState snapshot) {
		final double dx = snapshot.x() - context.cameraPosition().x;
		final double dz = snapshot.z() - context.cameraPosition().z;
		if (dx * dx + dz * dz > context.maxRenderDistanceBlocks() * context.maxRenderDistanceBlocks()) {
			return false;
		}

		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return false;
		}
		return minecraft.level.hasChunk(floorChunk(snapshot.x()), floorChunk(snapshot.z()));
	}

	private static double maxRenderDistanceBlocks() {
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.options == null) {
			return 160.0D;
		}

		final int renderDistanceChunks = Math.max(2, minecraft.options.getEffectiveRenderDistance());
		final double entityDistanceScale = Math.max(0.25D, minecraft.options.entityDistanceScaling().get());
		return renderDistanceChunks * 16.0D * entityDistanceScale + RENDER_DISTANCE_MARGIN_BLOCKS;
	}

	private static int floorChunk(double coordinate) {
		return (int) Math.floor(coordinate / 16.0D);
	}
}
