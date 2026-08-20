package com.cookiecraftmods.mta.client.tollgate;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.init.ModBlockEntities;
import com.cookiecraftmods.mta.init.ModBlocks;
import com.cookiecraftmods.mta.traffic.tollgate.TollgateBlock;
import com.cookiecraftmods.mta.traffic.tollgate.entity.TollgateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class TollgateRenderer implements BlockEntityRenderer<TollgateBlockEntity> {
	private static final int MAX_CONNECTED_BAR_LENGTH = 7;
	private static final ResourceLocation OPEN_BAR_MODEL = model("tollgate_bar_open_render");
	private static final ResourceLocation CLOSED_BAR_MODEL = model("tollgate_bar_closed_render");

	public TollgateRenderer(BlockEntityRendererProvider.Context context) {
	}

	public static void initialize() {
		ModelLoadingPlugin.register(context -> context.addModels(OPEN_BAR_MODEL, CLOSED_BAR_MODEL));
		BlockEntityRendererRegistry.register(ModBlockEntities.TOLLGATE, TollgateRenderer::new);
	}

	@Override
	public void render(TollgateBlockEntity blockEntity, float tickDelta, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		final Level level = blockEntity.getLevel();
		final BlockState poleState = blockEntity.getBlockState();
		if (level == null || !poleState.is(ModBlocks.TOLLGATE_POLE)) {
			return;
		}

		final Direction facing = poleState.getValue(TollgateBlock.FACING);
		final boolean closed = poleState.getValue(TollgateBlock.CLOSED);
		renderChain(level, blockEntity.getBlockPos(), facing, closed, poseStack, bufferSource, packedLight, packedOverlay);
		renderChain(level, blockEntity.getBlockPos(), facing.getOpposite(), closed, poseStack, bufferSource, packedLight, packedOverlay);
	}

	@Override
	public boolean shouldRenderOffScreen(TollgateBlockEntity blockEntity) {
		return true;
	}

	private static void renderChain(Level level, BlockPos polePos, Direction direction, boolean closed, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		int length = 0;
		for (int i = 1; i <= MAX_CONNECTED_BAR_LENGTH; i++) {
			if (!level.getBlockState(polePos.relative(direction, i)).is(ModBlocks.TOLLGATE_BAR)) {
				break;
			}
			length++;
		}
		if (length == 0) {
			return;
		}

		final Minecraft minecraft = Minecraft.getInstance();
		final BakedModel model = minecraft.getModelManager().getModel(closed ? CLOSED_BAR_MODEL : OPEN_BAR_MODEL);
		final VertexConsumer consumer = bufferSource.getBuffer(RenderType.cutout());
		final BlockState renderState = ModBlocks.TOLLGATE_BAR.defaultBlockState()
			.setValue(TollgateBlock.FACING, direction)
			.setValue(TollgateBlock.CLOSED, closed);
		for (int i = 1; i <= length; i++) {
			poseStack.pushPose();
			if (closed) {
				poseStack.translate(direction.getStepX() * i, 0.0D, direction.getStepZ() * i);
			} else {
				poseStack.translate(0.0D, i, 0.0D);
			}
			rotateToFacing(poseStack, direction);
			minecraft.getBlockRenderer().getModelRenderer().renderModel(
				poseStack.last(), consumer, renderState, model, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay
			);
			poseStack.popPose();
		}
	}

	private static void rotateToFacing(PoseStack poseStack, Direction facing) {
		final float rotation = switch (facing) {
			case SOUTH -> 90.0F;
			case WEST -> 180.0F;
			case NORTH -> 270.0F;
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
