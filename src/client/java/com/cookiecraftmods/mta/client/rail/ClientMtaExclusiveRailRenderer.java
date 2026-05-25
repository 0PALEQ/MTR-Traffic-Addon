package com.cookiecraftmods.mta.client.rail;

import com.cookiecraftmods.mta.client.rail.ClientMtaExclusiveRailState.ClientMtaExclusiveRail;
import com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mod.render.RenderRails;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.List;

public final class ClientMtaExclusiveRailRenderer {
	private static final int COLOR_RED = 46;
	private static final int COLOR_GREEN = 13;
	private static final int COLOR_BLUE = 74;
	private static final int COLOR_ALPHA = 255;
	private static final double RAIL_HALF_WIDTH = 0.22D;
	private static final double RENDER_Y_OFFSET = 0.08D;
	private static final int SLEEPER_INTERVAL_SEGMENTS = 4;
	private static Method renderRailStandardMethod;
	private static Object coloredRenderState;
	private static boolean mtrRendererLookupFailed;

	private ClientMtaExclusiveRailRenderer() {
	}

	public static void render(WorldRenderContext context) {
		if (context.consumers() == null || context.camera() == null || context.matrixStack() == null) {
			return;
		}

		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || !RenderRails.isHoldingRailRelated(new ClientPlayerEntity(minecraft.player))) {
			return;
		}

		if (renderWithMtrRenderer(minecraft, context)) {
			return;
		}

		final PoseStack poseStack = context.matrixStack();
		final VertexConsumer vertexConsumer = context.consumers().getBuffer(RenderType.lines());
		final Vec3 camera = context.camera().getPosition();
		poseStack.pushPose();
		final PoseStack.Pose pose = poseStack.last();
		final Matrix4f positionMatrix = pose.pose();
		final Matrix3f normalMatrix = pose.normal();

		for (ClientMtaExclusiveRail rail : ClientMtaExclusiveRailState.all()) {
			renderRail(vertexConsumer, positionMatrix, normalMatrix, camera, rail);
		}

		poseStack.popPose();
	}

	private static boolean renderWithMtrRenderer(Minecraft minecraft, WorldRenderContext context) {
		final Method method = renderRailStandardMethod();
		if (method == null || coloredRenderState == null) {
			return false;
		}

		try {
			final ClientWorld clientWorld = new ClientWorld(minecraft.level);
			for (ClientMtaExclusiveRail rail : ClientMtaExclusiveRailState.all()) {
				final Rail renderRail = rail.renderRail();
				if (renderRail != null) {
					method.invoke(null, clientWorld, renderRail, coloredRenderState, context.tickDelta());
				}
			}
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Method renderRailStandardMethod() {
		if (renderRailStandardMethod != null || mtrRendererLookupFailed) {
			return renderRailStandardMethod;
		}

		try {
			final Class<?> renderStateClass = Class.forName("org.mtr.mod.render.RenderRails$RenderState");
			coloredRenderState = Enum.valueOf((Class<? extends Enum>) renderStateClass.asSubclass(Enum.class), "COLORED");
			renderRailStandardMethod = RenderRails.class.getDeclaredMethod("renderRailStandard", ClientWorld.class, Rail.class, renderStateClass, float.class);
			renderRailStandardMethod.setAccessible(true);
		} catch (Exception ignored) {
			mtrRendererLookupFailed = true;
		}
		return renderRailStandardMethod;
	}

	private static void renderRail(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix, Vec3 camera, ClientMtaExclusiveRail rail) {
		final List<TrafficPathPoint> path = rail.path();
		if (path.size() < 2) {
			return;
		}

		for (int i = 1; i < path.size(); i++) {
			final TrafficPathPoint previous = path.get(i - 1);
			final TrafficPathPoint next = path.get(i);
			final double dx = next.x() - previous.x();
			final double dz = next.z() - previous.z();
			final double horizontalLength = Math.sqrt(dx * dx + dz * dz);
			final double offsetX = horizontalLength <= 0.0001D ? 0.0D : -dz / horizontalLength * RAIL_HALF_WIDTH;
			final double offsetZ = horizontalLength <= 0.0001D ? 0.0D : dx / horizontalLength * RAIL_HALF_WIDTH;

			emitLine(vertexConsumer, positionMatrix, normalMatrix, camera, previous, next, offsetX, offsetZ);
			emitLine(vertexConsumer, positionMatrix, normalMatrix, camera, previous, next, -offsetX, -offsetZ);
			if (i % SLEEPER_INTERVAL_SEGMENTS == 0) {
				emitSleeper(vertexConsumer, positionMatrix, normalMatrix, camera, previous, offsetX, offsetZ);
			}
		}
	}

	private static void emitLine(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix, Vec3 camera, TrafficPathPoint previous, TrafficPathPoint next, double offsetX, double offsetZ) {
		emitLineVertex(vertexConsumer, positionMatrix, normalMatrix, previous.x() + offsetX - camera.x, previous.y() + RENDER_Y_OFFSET - camera.y, previous.z() + offsetZ - camera.z);
		emitLineVertex(vertexConsumer, positionMatrix, normalMatrix, next.x() + offsetX - camera.x, next.y() + RENDER_Y_OFFSET - camera.y, next.z() + offsetZ - camera.z);
	}

	private static void emitSleeper(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix, Vec3 camera, TrafficPathPoint point, double offsetX, double offsetZ) {
		emitLineVertex(vertexConsumer, positionMatrix, normalMatrix, point.x() + offsetX - camera.x, point.y() + RENDER_Y_OFFSET - camera.y, point.z() + offsetZ - camera.z);
		emitLineVertex(vertexConsumer, positionMatrix, normalMatrix, point.x() - offsetX - camera.x, point.y() + RENDER_Y_OFFSET - camera.y, point.z() - offsetZ - camera.z);
	}

	private static void emitLineVertex(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix, double x, double y, double z) {
		vertexConsumer
			.vertex(positionMatrix, (float) x, (float) y, (float) z)
			.color(COLOR_RED, COLOR_GREEN, COLOR_BLUE, COLOR_ALPHA)
			.normal(normalMatrix, 0.0F, 1.0F, 0.0F)
			.endVertex();
	}
}
