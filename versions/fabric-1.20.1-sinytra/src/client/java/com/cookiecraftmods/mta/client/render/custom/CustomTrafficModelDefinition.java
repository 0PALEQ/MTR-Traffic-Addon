package com.cookiecraftmods.mta.client.render.custom;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public record CustomTrafficModelDefinition(
	String id,
	String format,
	ResourceLocation model,
	ResourceLocation texture,
	double scale,
	double offsetX,
	double offsetY,
	double offsetZ,
	double rotationX,
	double rotationY,
	double rotationZ,
	int color
) {
	public String effectiveFormat() {
		if (format != null && !format.isBlank()) {
			return format.toLowerCase(Locale.ROOT);
		}
		final String path = model == null ? "" : model.getPath();
		final int dotIndex = path.lastIndexOf('.');
		return dotIndex < 0 ? "" : path.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
	}
}
