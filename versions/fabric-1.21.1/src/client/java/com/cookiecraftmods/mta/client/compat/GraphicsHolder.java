package com.cookiecraftmods.mta.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

/**
 * Small source-compatible drawing facade for the dashboard code that predates
 * MTR 4.1's removal of its mapping wrappers.
 */
public final class GraphicsHolder {
	private final GuiGraphics graphics;

	public GraphicsHolder(GuiGraphics graphics) {
		this.graphics = graphics;
	}

	public GuiGraphics graphics() {
		return graphics;
	}

	public PoseStack pose() {
		return graphics.pose();
	}

	public MultiBufferSource.BufferSource bufferSource() {
		return graphics.bufferSource();
	}

	public void push() {
		graphics.pose().pushPose();
	}

	public void pop() {
		graphics.pose().popPose();
	}

	public void translate(float x, float y, float z) {
		graphics.pose().translate(x, y, z);
	}

	public void scale(float x, float y, float z) {
		graphics.pose().scale(x, y, z);
	}

	public void drawText(String text, int x, int y, int color, boolean shadow, int light) {
		graphics.drawString(Minecraft.getInstance().font, text, x, y, color, shadow);
	}

	public void drawText(Component text, int x, int y, int color, boolean shadow, int light) {
		graphics.drawString(Minecraft.getInstance().font, text, x, y, color, shadow);
	}

	public static int getTextWidth(String text) {
		return Minecraft.getInstance().font.width(text == null ? "" : text);
	}

	public static int getTextWidth(Component text) {
		return Minecraft.getInstance().font.width(text);
	}

	public static int getDefaultLight() {
		return LightTexture.FULL_BRIGHT;
	}
}
