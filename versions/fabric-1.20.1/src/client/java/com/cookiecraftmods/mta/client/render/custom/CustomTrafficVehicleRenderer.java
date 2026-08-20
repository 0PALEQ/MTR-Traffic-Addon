package com.cookiecraftmods.mta.client.render.custom;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugRenderState;
import com.cookiecraftmods.mta.client.render.ClientTrafficRenderContext;
import com.cookiecraftmods.mta.client.render.ClientTrafficVehicleRenderer;
import com.cookiecraftmods.mta.client.render.ClientTrafficVisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CustomTrafficVehicleRenderer implements ClientTrafficVehicleRenderer {
	private static final float MAX_RANDOM_PITCH_DEGREES = 0.3F;
	private static final Set<String> WARNED_RENDER_FAILURES = new HashSet<>();

	@Override
	public boolean tryRender(ClientTrafficRenderContext context, ClientTrafficDebugRenderState snapshot, ClientTrafficVisualProfile visualProfile) {
		final CustomTrafficModel model = CustomTrafficModelRegistry.get(snapshot.visualId()).orElse(null);
		if (model == null) {
			return false;
		}

		context.poseStack().pushPose();
		try {
			context.translateTo(snapshot.x(), snapshot.y(), snapshot.z());
			context.poseStack().mulPose(Axis.YP.rotationDegrees(-snapshot.yawDegrees()));
			applyDefinitionTransform(context.poseStack(), model.definition());
			context.poseStack().mulPose(Axis.XP.rotationDegrees(snapshot.pitchDegrees() + deterministicPitchOffset(snapshot.id())));

			final PoseStack.Pose pose = context.poseStack().last();
			final Matrix4f positionMatrix = pose.pose();
			final Matrix3f normalMatrix = pose.normal();
			final int color = model.definition().color();
			final int alpha = color >>> 24 & 0xFF;
			final int red = color >>> 16 & 0xFF;
			final int green = color >>> 8 & 0xFF;
			final int blue = color & 0xFF;
			final int light = context.lightAt(snapshot.x(), snapshot.y() + 0.5D, snapshot.z());

			for (TrafficMeshFace face : model.faces()) {
				final ResourceLocation texture = face.texture() == null ? model.texture() : face.texture();
				final VertexConsumer vertexConsumer = context.bufferSource().getBuffer(RenderType.entityCutout(texture));
				emitFace(vertexConsumer, positionMatrix, normalMatrix, face, red, green, blue, alpha, light);
			}
			return true;
		} catch (Exception e) {
			if (WARNED_RENDER_FAILURES.add(snapshot.visualId())) {
				MTRTrafficAddon.LOGGER.warn("Failed to render custom traffic model {}", snapshot.visualId(), e);
			}
			return false;
		} finally {
			context.poseStack().popPose();
		}
	}

	private static void emitFace(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix, TrafficMeshFace face, int red, int green, int blue, int alpha, int light) {
		if (face.vertices().size() == 3) {
			emitVertex(vertexConsumer, positionMatrix, normalMatrix, face, face.vertices().get(0), red, green, blue, alpha, light);
			emitVertex(vertexConsumer, positionMatrix, normalMatrix, face, face.vertices().get(1), red, green, blue, alpha, light);
			emitVertex(vertexConsumer, positionMatrix, normalMatrix, face, face.vertices().get(2), red, green, blue, alpha, light);
			emitVertex(vertexConsumer, positionMatrix, normalMatrix, face, face.vertices().get(2), red, green, blue, alpha, light);
			return;
		}

		for (TrafficMeshVertex vertex : face.vertices()) {
			emitVertex(vertexConsumer, positionMatrix, normalMatrix, face, vertex, red, green, blue, alpha, light);
		}
	}

	private static void emitVertex(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix, TrafficMeshFace face, TrafficMeshVertex vertex, int red, int green, int blue, int alpha, int light) {
		vertexConsumer.vertex(positionMatrix, vertex.x(), vertex.y(), vertex.z())
			.color(red, green, blue, alpha)
			.uv(vertex.u(), vertex.v())
			.overlayCoords(OverlayTexture.NO_OVERLAY)
			.uv2(light)
			.normal(normalMatrix, face.normalX(), face.normalY(), face.normalZ())
			.endVertex();
	}

	private static float deterministicPitchOffset(UUID id) {
		if (id == null) {
			return 0.0F;
		}

		final long hash = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17);
		final double normalized = (double) Long.remainderUnsigned(hash, 10_000L) / 9_999.0D;
		return (float) (-MAX_RANDOM_PITCH_DEGREES + normalized * MAX_RANDOM_PITCH_DEGREES * 2.0D);
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
