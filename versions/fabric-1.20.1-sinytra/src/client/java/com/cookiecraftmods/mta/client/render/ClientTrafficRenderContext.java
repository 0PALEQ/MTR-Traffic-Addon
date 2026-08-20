
package com.cookiecraftmods.mta.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.mtr.mapping.mapper.GraphicsHolder;

public record ClientTrafficRenderContext(
	PoseStack poseStack,
	MultiBufferSource bufferSource,
	VertexConsumer lineConsumer,
	VertexConsumer fillConsumer,
	Vec3 cameraPosition,
	double maxRenderDistanceBlocks,
	GraphicsHolder graphicsHolder
) {
	public void translateTo(double x, double y, double z) {
		poseStack.translate(x - cameraPosition.x, y - cameraPosition.y, z - cameraPosition.z);
	}

	public int lightAt(double x, double y, double z) {
		final Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null
			? GraphicsHolder.getDefaultLight()
			: LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(x, y, z));
	}
}
