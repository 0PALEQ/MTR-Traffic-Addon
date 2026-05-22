package com.cookiecraftmods.mta.client.lights;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.init.ModBlockEntities;
import com.cookiecraftmods.mta.init.ModBlocks;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightSignalState;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsPoleTopBlock;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsPrimaryBlock;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsVerticalPoleBlock;
import com.cookiecraftmods.mta.traffic.lights.block.entity.TrafficLightBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class TrafficLightEmissiveRenderer implements BlockEntityRenderer<TrafficLightBlockEntity> {
	private static final ResourceLocation PRIMARY_RED = model("traffic_lights_primary_red_glow");
	private static final ResourceLocation PRIMARY_YELLOW = model("traffic_lights_primary_yellow_glow");
	private static final ResourceLocation PRIMARY_GREEN = model("traffic_lights_primary_green_glow");
	private static final ResourceLocation POLE_RED = model("traffic_lights_pole_middle_with_lights_red_glow");
	private static final ResourceLocation POLE_YELLOW = model("traffic_lights_pole_middle_with_lights_yellow_glow");
	private static final ResourceLocation POLE_GREEN = model("traffic_lights_pole_middle_with_lights_green_glow");
	private static final ResourceLocation VERTICAL_RED = model("traffic_lights_pole_top_vertical_with_lights_red_glow");
	private static final ResourceLocation VERTICAL_YELLOW = model("traffic_lights_pole_top_vertical_with_lights_yellow_glow");
	private static final ResourceLocation VERTICAL_GREEN = model("traffic_lights_pole_top_vertical_with_lights_green_glow");
	private static final List<ResourceLocation> OVERLAY_MODELS = List.of(
		PRIMARY_RED,
		PRIMARY_YELLOW,
		PRIMARY_GREEN,
		POLE_RED,
		POLE_YELLOW,
		POLE_GREEN,
		VERTICAL_RED,
		VERTICAL_YELLOW,
		VERTICAL_GREEN
	);

	public TrafficLightEmissiveRenderer(BlockEntityRendererProvider.Context context) {
	}

	public static void initialize() {
		ModelLoadingPlugin.register(context -> context.addModels(OVERLAY_MODELS));
		BlockEntityRendererRegistry.register(ModBlockEntities.TRAFFIC_LIGHT, TrafficLightEmissiveRenderer::new);
	}

	@Override
	public void render(TrafficLightBlockEntity blockEntity, float tickDelta, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		final BlockState state = blockEntity.getBlockState();
		final ResourceLocation modelLocation = modelFor(state);
		if (modelLocation == null) {
			return;
		}

		final Minecraft minecraft = Minecraft.getInstance();
		final BakedModel model = minecraft.getModelManager().getModel(modelLocation);
		final VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));

		poseStack.pushPose();
		rotateToFacing(poseStack, facing(state));
		minecraft.getBlockRenderer().getModelRenderer().renderModel(
			poseStack.last(),
			vertexConsumer,
			state,
			model,
			1.0F,
			1.0F,
			1.0F,
			LightTexture.FULL_BRIGHT,
			OverlayTexture.NO_OVERLAY
		);
		poseStack.popPose();
	}

	private static ResourceLocation modelFor(BlockState state) {
		if (state.is(ModBlocks.TRAFFIC_LIGHTS_PRIMARY)) {
			return primaryModel(state.getValue(TrafficLightsPrimaryBlock.SIGNAL));
		}
		if (state.is(ModBlocks.TRAFFIC_LIGHTS_POLE) && !state.getValue(TrafficLightsPoleTopBlock.CONNECTED) && state.getValue(TrafficLightsPoleTopBlock.HAS_LIGHTS)) {
			return poleModel(state.getValue(TrafficLightsPoleTopBlock.SIGNAL));
		}
		if (state.is(ModBlocks.TRAFFIC_LIGHTS_VERTICAL_POLE) && state.getValue(TrafficLightsVerticalPoleBlock.HAS_LIGHTS)) {
			return verticalModel(state.getValue(TrafficLightsVerticalPoleBlock.SIGNAL));
		}
		return null;
	}

	private static ResourceLocation primaryModel(TrafficLightSignalState signal) {
		return switch (signal) {
			case RED -> PRIMARY_RED;
			case YELLOW -> PRIMARY_YELLOW;
			case GREEN -> PRIMARY_GREEN;
			case OFF -> null;
		};
	}

	private static ResourceLocation poleModel(TrafficLightSignalState signal) {
		return switch (signal) {
			case RED -> POLE_RED;
			case YELLOW -> POLE_YELLOW;
			case GREEN -> POLE_GREEN;
			case OFF -> null;
		};
	}

	private static ResourceLocation verticalModel(TrafficLightSignalState signal) {
		return switch (signal) {
			case RED -> VERTICAL_RED;
			case YELLOW -> VERTICAL_YELLOW;
			case GREEN -> VERTICAL_GREEN;
			case OFF -> null;
		};
	}

	private static Direction facing(BlockState state) {
		if (state.hasProperty(TrafficLightsPrimaryBlock.FACING)) {
			return state.getValue(TrafficLightsPrimaryBlock.FACING);
		}
		return Direction.EAST;
	}

	private static void rotateToFacing(PoseStack poseStack, Direction facing) {
		final float rotation = switch (facing) {
			case EAST -> -90.0F;
			case SOUTH -> 180.0F;
			case WEST -> -270.0F;
			default -> 0.0F;
		};
		if (rotation != 0.0F) {
			poseStack.translate(0.5D, 0.5D, 0.5D);
			poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
			poseStack.translate(-0.5D, -0.5D, -0.5D);
		}
	}

	private static ResourceLocation model(String path) {
		return new ResourceLocation(MTRTrafficAddon.MOD_ID, "block/" + path);
	}
}
