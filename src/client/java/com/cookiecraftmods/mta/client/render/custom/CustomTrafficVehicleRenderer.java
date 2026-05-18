package com.cookiecraftmods.mta.client.render.custom;

import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import com.cookiecraftmods.mta.client.render.ClientTrafficRenderContext;
import com.cookiecraftmods.mta.client.render.ClientTrafficVehicleRenderer;
import com.cookiecraftmods.mta.client.render.ClientTrafficVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class CustomTrafficVehicleRenderer implements ClientTrafficVehicleRenderer {
	private final ClientTrafficVehicleRenderer fallbackRenderer;

	public CustomTrafficVehicleRenderer(ClientTrafficVehicleRenderer fallbackRenderer) {
		this.fallbackRenderer = fallbackRenderer;
	}

	@Override
	public void render(ClientTrafficRenderContext context, ClientTrafficDebugRenderState snapshot, ClientTrafficVisualProfile visualProfile) {
		final CustomTrafficModel model = CustomTrafficModelRegistry.get(snapshot.visualId()).orElse(null);
		if (model == null) {
			fallbackRenderer.render(context, snapshot, visualProfile);
			return;
		}

		context.poseStack().pushPose();
		context.poseStack().translate(
			snapshot.x() - context.cameraPosition().x,
			snapshot.y() - context.cameraPosition().y,
			snapshot.z() - context.cameraPosition().z
		);
		context.poseStack().mulPose(Axis.YP.rotationDegrees(-snapshot.yawDegrees()));
		applyDefinitionTransform(context.poseStack(), model.definition());

		final VertexConsumer vertexConsumer = context.bufferSource().getBuffer(RenderType.entityCutout(model.texture()));
		final PoseStack.Pose pose = context.poseStack().last();
		final Matrix4f positionMatrix = pose.pose();
		final Matrix3f normalMatrix = pose.normal();
		final int color = model.definition().color();
		final int alpha = color >>> 24 & 0xFF;
		final int red = color >>> 16 & 0xFF;
		final int green = color >>> 8 & 0xFF;
		final int blue = color & 0xFF;

		for (TrafficMeshFace face : model.faces()) {
			for (TrafficMeshVertex vertex : face.vertices()) {
				vertexConsumer.vertex(positionMatrix, vertex.x(), vertex.y(), vertex.z())
					.color(red, green, blue, alpha)
					.uv(vertex.u(), vertex.v())
					.overlayCoords(OverlayTexture.NO_OVERLAY)
					.uv2(LightTexture.FULL_BRIGHT)
					.normal(normalMatrix, face.normalX(), face.normalY(), face.normalZ())
					.endVertex();
			}
		}
		context.poseStack().popPose();
	}

	private static void applyDefinitionTransform(PoseStack poseStack, CustomTrafficModelDefinition definition) {
		poseStack.translate(definition.offsetX(), definition.offsetY(), definition.offsetZ());
		if (definition.rotationY() != 0.0D) {
			poseStack.mulPose(Axis.YP.rotationDegrees((float) definition.rotationY()));
		}
		if (definition.rotationX() != 0.0D) {
			poseStack.mulPose(Axis.XP.rotationDegrees((float) definition.rotationX()));
		}
		if (definition.rotationZ() != 0.0D) {
			poseStack.mulPose(Axis.ZP.rotationDegrees((float) definition.rotationZ()));
		}
		poseStack.scale((float) definition.scale(), (float) definition.scale(), (float) definition.scale());
	}
}