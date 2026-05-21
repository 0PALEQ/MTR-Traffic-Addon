package com.cookiecraftmods.mta.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

public record ClientTrafficRenderContext(
	PoseStack poseStack,
	MultiBufferSource bufferSource,
	VertexConsumer lineConsumer,
	VertexConsumer fillConsumer,
	Vec3 cameraPosition,
	double maxRenderDistanceBlocks
) {
}
