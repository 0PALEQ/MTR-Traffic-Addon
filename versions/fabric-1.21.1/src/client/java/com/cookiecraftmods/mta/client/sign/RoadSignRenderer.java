package com.cookiecraftmods.mta.client.sign;

import com.cookiecraftmods.mta.init.ModBlockEntities;
import com.cookiecraftmods.mta.traffic.sign.RoadSignBlock;
import com.cookiecraftmods.mta.traffic.sign.entity.RoadSignBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class RoadSignRenderer implements BlockEntityRenderer<RoadSignBlockEntity> {
	private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/white_concrete.png");
	private static final float FRONT_OFFSET = 0.001F;
	private static final int TEXT_LAYER = 4;
	private final Font font;

	public RoadSignRenderer(BlockEntityRendererProvider.Context context) {
		font = context.getFont();
	}

	public static void initialize() {
		BlockEntityRendererRegistry.register(ModBlockEntities.ROAD_SIGN, RoadSignRenderer::new);
	}

	@Override
	public void render(RoadSignBlockEntity blockEntity, float tickDelta, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (!blockEntity.getBlockState().hasProperty(RoadSignBlock.FACING)) {
			return;
		}
		final RoadSignBaseDefinition definition = RoadSignBaseRegistry.resolve(blockEntity.getBaseId());
		final Direction facing = blockEntity.getBlockState().getValue(RoadSignBlock.FACING);
		final float width = blockEntity.getWidth() > 0.0F ? blockEntity.getWidth() : definition.width();
		final float height = blockEntity.getHeight() > 0.0F ? blockEntity.getHeight() : definition.height();
		final int backgroundColor = blockEntity.getBackgroundColor() < 0 ? definition.backgroundColor() : blockEntity.getBackgroundColor();
		final int borderColor = blockEntity.getEdgeColor() < 0 ? definition.borderColor() : blockEntity.getEdgeColor();
		final int backColor = blockEntity.getEdgeColor() < 0 ? definition.backColor() : blockEntity.getEdgeColor();
		final ResourceLocation customTexture = RoadSignImageRegistry.texture(blockEntity.getImageId(), blockEntity.getBlockPos()).orElse(null);
		poseStack.pushPose();
		poseStack.translate(0.5D, definition.yOffset() + height / 2.0F, 0.5D);
		poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
		renderPanel(definition, width, height, backgroundColor, borderColor, backColor, customTexture, poseStack, bufferSource, packedLight, packedOverlay);
		renderText(blockEntity, definition, width, height, poseStack, bufferSource, packedLight);
		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(RoadSignBlockEntity blockEntity) {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}

	private static void renderPanel(
		RoadSignBaseDefinition definition,
		float width,
		float height,
		int backgroundColor,
		int borderColor,
		int backColor,
		ResourceLocation customTexture,
		PoseStack poseStack,
		MultiBufferSource bufferSource,
		int packedLight,
		int packedOverlay
	) {
		final float left = -width / 2.0F;
		final float right = width / 2.0F;
		final float bottom = -height / 2.0F;
		final float top = height / 2.0F;
		final float back = -definition.thickness() / 2.0F;
		final float front = definition.thickness() / 2.0F;
		final PoseStack.Pose pose = poseStack.last();
		final Matrix4f positionMatrix = pose.pose();
		final Matrix3f normalMatrix = pose.normal();
		final VertexConsumer solidConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEXTURE));

		emitQuad(solidConsumer, positionMatrix, normalMatrix,
			left, bottom, front, right, bottom, front, right, top, front, left, top, front,
			0.0F, 0.0F, 1.0F, borderColor, packedLight, packedOverlay);
		emitQuad(solidConsumer, positionMatrix, normalMatrix,
			right, bottom, back, left, bottom, back, left, top, back, right, top, back,
			0.0F, 0.0F, -1.0F, backColor, packedLight, packedOverlay);
		emitQuad(solidConsumer, positionMatrix, normalMatrix,
			left, bottom, back, right, bottom, back, right, bottom, front, left, bottom, front,
			0.0F, -1.0F, 0.0F, backColor, packedLight, packedOverlay);
		emitQuad(solidConsumer, positionMatrix, normalMatrix,
			left, top, front, right, top, front, right, top, back, left, top, back,
			0.0F, 1.0F, 0.0F, backColor, packedLight, packedOverlay);
		emitQuad(solidConsumer, positionMatrix, normalMatrix,
			left, bottom, back, left, bottom, front, left, top, front, left, top, back,
			-1.0F, 0.0F, 0.0F, backColor, packedLight, packedOverlay);
		emitQuad(solidConsumer, positionMatrix, normalMatrix,
			right, bottom, front, right, bottom, back, right, top, back, right, top, front,
			1.0F, 0.0F, 0.0F, backColor, packedLight, packedOverlay);

		final float inset = Math.min(width, height) * definition.border();
		if (inset < Math.min(width, height) / 2.0F) {
			emitQuad(solidConsumer, positionMatrix, normalMatrix,
				left + inset, bottom + inset, front + FRONT_OFFSET,
				right - inset, bottom + inset, front + FRONT_OFFSET,
				right - inset, top - inset, front + FRONT_OFFSET,
				left + inset, top - inset, front + FRONT_OFFSET,
				0.0F, 0.0F, 1.0F, backgroundColor, packedLight, packedOverlay);
		}
		if (definition.texture() != null) {
			renderFrontTexture(definition.texture(), false, 2, left, right, bottom, top, front, positionMatrix, normalMatrix, bufferSource, packedLight, packedOverlay);
		}
		if (customTexture != null) {
			renderFrontTexture(customTexture, true, 3, left, right, bottom, top, front, positionMatrix, normalMatrix, bufferSource, packedLight, packedOverlay);
		}
	}

	private void renderText(RoadSignBlockEntity blockEntity, RoadSignBaseDefinition definition, float width, float height, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		final List<FormattedCharSequence> renderLines = new ArrayList<>();
		final List<String> storedLines = blockEntity.getLines();
		int lastLine = -1;
		for (int index = 0; index < Math.min(definition.maxLines(), storedLines.size()); index++) {
			if (!storedLines.get(index).isBlank()) {
				lastLine = index;
			}
		}
		if (lastLine < 0) {
			return;
		}
		for (int index = 0; index <= lastLine; index++) {
			renderLines.add(net.minecraft.network.chat.Component.literal(storedLines.get(index)).getVisualOrderText());
		}

		final float regionWidth = width * definition.textWidth();
		final float regionHeight = height * definition.textHeight();
		final float regionLeft = -width / 2.0F + width * definition.textX();
		final float regionTop = height / 2.0F - height * definition.textY();
		int widestLine = 1;
		for (FormattedCharSequence line : renderLines) {
			widestLine = Math.max(widestLine, font.width(line));
		}
		final float slotScale = regionHeight / definition.maxLines() / font.lineHeight * 0.82F;
		final float widthScale = regionWidth / widestLine * 0.96F;
		final float textScale = Math.max(0.0001F, Math.min(slotScale, widthScale));
		final float totalHeight = renderLines.size() * font.lineHeight * textScale;
		final float textTop = regionTop - (regionHeight - totalHeight) / 2.0F;
		final float front = definition.thickness() / 2.0F + FRONT_OFFSET * TEXT_LAYER;
		final int color = 0xFF000000 | definition.effectiveTextColor(blockEntity.getTextColor());
		final int light = definition.fullBright() ? LightTexture.FULL_BRIGHT : packedLight;

		poseStack.pushPose();
		poseStack.translate(0.0F, textTop, front);
		poseStack.scale(textScale, -textScale, textScale);
		final float regionLeftPixels = regionLeft / textScale;
		final float regionRightPixels = (regionLeft + regionWidth) / textScale;
		for (int index = 0; index < renderLines.size(); index++) {
			final FormattedCharSequence line = renderLines.get(index);
			final int lineWidth = font.width(line);
			final float x = switch (definition.alignment()) {
				case LEFT -> regionLeftPixels;
				case RIGHT -> regionRightPixels - lineWidth;
				case CENTER -> (regionLeftPixels + regionRightPixels - lineWidth) / 2.0F;
			};
			font.drawInBatch(
				line,
				x,
				index * font.lineHeight,
				color,
				definition.shadow(),
				poseStack.last().pose(),
				bufferSource,
				Font.DisplayMode.POLYGON_OFFSET,
				0,
				light
			);
		}
		poseStack.popPose();
	}

	private static void renderFrontTexture(
		ResourceLocation texture,
		boolean translucent,
		int layer,
		float left,
		float right,
		float bottom,
		float top,
		float front,
		Matrix4f positionMatrix,
		Matrix3f normalMatrix,
		MultiBufferSource bufferSource,
		int packedLight,
		int packedOverlay
	) {
		final VertexConsumer textureConsumer = bufferSource.getBuffer(translucent ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture));
		final float z = front + FRONT_OFFSET * layer;
		emitQuad(textureConsumer, positionMatrix, normalMatrix,
			left, bottom, z, right, bottom, z, right, top, z, left, top, z,
			0.0F, 0.0F, 1.0F, 0xFFFFFF, packedLight, packedOverlay);
	}

	private static void emitQuad(
		VertexConsumer consumer,
		Matrix4f positionMatrix,
		Matrix3f normalMatrix,
		float x0, float y0, float z0,
		float x1, float y1, float z1,
		float x2, float y2, float z2,
		float x3, float y3, float z3,
		float normalX, float normalY, float normalZ,
		int color,
		int packedLight,
		int packedOverlay
	) {
		final int red = color >> 16 & 0xFF;
		final int green = color >> 8 & 0xFF;
		final int blue = color & 0xFF;
		emitVertex(consumer, positionMatrix, normalMatrix, x0, y0, z0, 0.0F, 1.0F, normalX, normalY, normalZ, red, green, blue, packedLight, packedOverlay);
		emitVertex(consumer, positionMatrix, normalMatrix, x1, y1, z1, 1.0F, 1.0F, normalX, normalY, normalZ, red, green, blue, packedLight, packedOverlay);
		emitVertex(consumer, positionMatrix, normalMatrix, x2, y2, z2, 1.0F, 0.0F, normalX, normalY, normalZ, red, green, blue, packedLight, packedOverlay);
		emitVertex(consumer, positionMatrix, normalMatrix, x3, y3, z3, 0.0F, 0.0F, normalX, normalY, normalZ, red, green, blue, packedLight, packedOverlay);
	}

	private static void emitVertex(
		VertexConsumer consumer,
		Matrix4f positionMatrix,
		Matrix3f normalMatrix,
		float x, float y, float z,
		float u, float v,
		float normalX, float normalY, float normalZ,
		int red, int green, int blue,
		int packedLight,
		int packedOverlay
	) {
		final Vector3f normal = new Vector3f(normalX, normalY, normalZ).mul(normalMatrix).normalize();
		consumer.addVertex(positionMatrix, x, y, z)
			.setColor(red, green, blue, 0xFF)
			.setUv(u, v)
			.setOverlay(packedOverlay == 0 ? OverlayTexture.NO_OVERLAY : packedOverlay)
			.setLight(packedLight)
			.setNormal(normal.x(), normal.y(), normal.z());
	}
}
