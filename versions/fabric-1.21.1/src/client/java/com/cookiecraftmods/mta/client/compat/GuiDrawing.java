package com.cookiecraftmods.mta.client.compat;

import net.minecraft.resources.ResourceLocation;

/** Draws the dashboard's batched rectangles and normalized-UV textures. */
public final class GuiDrawing {
	private static final int UV_RESOLUTION = 4096;
	private final GraphicsHolder graphicsHolder;
	private ResourceLocation texture;

	public GuiDrawing(GraphicsHolder graphicsHolder) {
		this.graphicsHolder = graphicsHolder;
	}

	public void beginDrawingRectangle() {
	}

	public void finishDrawingRectangle() {
	}

	public void drawRectangle(double x1, double y1, double x2, double y2, int color) {
		final int left = (int) Math.floor(Math.min(x1, x2));
		final int top = (int) Math.floor(Math.min(y1, y2));
		final int right = (int) Math.ceil(Math.max(x1, x2));
		final int bottom = (int) Math.ceil(Math.max(y1, y2));
		if (right > left && bottom > top) {
			graphicsHolder.graphics().fill(left, top, right, bottom, color);
		}
	}

	public void beginDrawingTexture(ResourceLocation texture) {
		this.texture = texture;
	}

	public void finishDrawingTexture() {
		texture = null;
	}

	public void drawTexture(double x1, double y1, double x2, double y2, float u1, float v1, float u2, float v2) {
		if (texture == null) {
			return;
		}
		final int x = (int) Math.floor(Math.min(x1, x2));
		final int y = (int) Math.floor(Math.min(y1, y2));
		final int width = (int) Math.ceil(Math.abs(x2 - x1));
		final int height = (int) Math.ceil(Math.abs(y2 - y1));
		if (width <= 0 || height <= 0) {
			return;
		}
		final float u = Math.min(u1, u2) * UV_RESOLUTION;
		final float v = Math.min(v1, v2) * UV_RESOLUTION;
		final int sourceWidth = Math.max(1, Math.round(Math.abs(u2 - u1) * UV_RESOLUTION));
		final int sourceHeight = Math.max(1, Math.round(Math.abs(v2 - v1) * UV_RESOLUTION));
		graphicsHolder.graphics().blit(texture, x, y, width, height, u, v, sourceWidth, sourceHeight, UV_RESOLUTION, UV_RESOLUTION);
	}
}
