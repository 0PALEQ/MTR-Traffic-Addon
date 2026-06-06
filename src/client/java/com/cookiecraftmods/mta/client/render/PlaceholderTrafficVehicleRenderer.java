package com.cookiecraftmods.mta.client.render;

import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.AABB;

public final class PlaceholderTrafficVehicleRenderer implements ClientTrafficVehicleRenderer {
	@Override
	public void render(ClientTrafficRenderContext context, ClientTrafficDebugRenderState snapshot, ClientTrafficVisualProfile visualProfile) {
		context.poseStack().pushPose();
		context.poseStack().translate(
			snapshot.x() - context.cameraPosition().x,
			snapshot.y() - context.cameraPosition().y,
			snapshot.z() - context.cameraPosition().z
		);
		context.poseStack().mulPose(Axis.YP.rotationDegrees(-snapshot.yawDegrees()));
		context.poseStack().mulPose(Axis.XP.rotationDegrees(-snapshot.pitchDegrees()));

		final double halfWidth = visualProfile.widthMeters() / 2.0D;
		final double halfLength = visualProfile.lengthMeters() / 2.0D;
		final AABB bodyBox = new AABB(
			-halfWidth,
			0.0D,
			-halfLength,
			halfWidth,
			visualProfile.heightMeters(),
			halfLength
		);
		final AABB noseBox = new AABB(
			-halfWidth * 0.35D,
			0.2D,
			-halfLength - visualProfile.noseLengthMeters(),
			halfWidth * 0.35D,
			0.55D,
			-halfLength
		);

		LevelRenderer.addChainedFilledBoxVertices(
			context.poseStack(),
			context.fillConsumer(),
			bodyBox.minX, bodyBox.minY, bodyBox.minZ,
			bodyBox.maxX, bodyBox.maxY, bodyBox.maxZ,
			0.2F, 0.55F, 0.95F, 0.2F
		);
		LevelRenderer.addChainedFilledBoxVertices(
			context.poseStack(),
			context.fillConsumer(),
			noseBox.minX, noseBox.minY, noseBox.minZ,
			noseBox.maxX, noseBox.maxY, noseBox.maxZ,
			1.0F, 0.85F, 0.15F, 0.45F
		);
		LevelRenderer.renderLineBox(context.poseStack(), context.lineConsumer(), bodyBox, 0.2F, 0.8F, 1.0F, 1.0F);
		LevelRenderer.renderLineBox(context.poseStack(), context.lineConsumer(), noseBox, 1.0F, 0.9F, 0.2F, 1.0F);
		context.poseStack().popPose();
	}
}
