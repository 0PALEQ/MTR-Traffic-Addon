package com.cookiecraftmods.mta.client.render;

import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugState;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.RenderType;

public final class ClientTrafficRenderDispatcher {
	private static final ClientTrafficVehicleRenderer DEFAULT_RENDERER = new MtrVehicleResourceRenderer();

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
			context.camera().getPosition()
		);

		renderContext.poseStack().pushPose();
		for (ClientTrafficDebugRenderState snapshot : ClientTrafficDebugState.allInterpolated()) {
			DEFAULT_RENDERER.render(renderContext, snapshot, ClientTrafficVisualProfile.fromSnapshot(snapshot));
		}
		renderContext.poseStack().popPose();
	}
}
