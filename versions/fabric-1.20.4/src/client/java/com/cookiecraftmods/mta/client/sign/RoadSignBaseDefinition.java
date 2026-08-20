package com.cookiecraftmods.mta.client.sign;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record RoadSignBaseDefinition(
	ResourceLocation id,
	String displayName,
	String translationKey,
	@Nullable ResourceLocation texture,
	float width,
	float height,
	float thickness,
	float yOffset,
	float border,
	int backgroundColor,
	int borderColor,
	int backColor,
	int defaultTextColor,
	float textX,
	float textY,
	float textWidth,
	float textHeight,
	int maxLines,
	TextAlignment alignment,
	boolean shadow,
	boolean fullBright
) {
	public Component nameComponent() {
		if (translationKey != null && !translationKey.isBlank()) {
			return Component.translatable(translationKey);
		}
		return Component.literal(displayName == null || displayName.isBlank() ? id.toString() : displayName);
	}

	public int effectiveTextColor(int overrideColor) {
		return overrideColor < 0 ? defaultTextColor : overrideColor & 0xFFFFFF;
	}

	public static RoadSignBaseDefinition fallback() {
		return fallback(new ResourceLocation(MTRTrafficAddon.MOD_ID, "blue_direction"), "Blue direction board");
	}

	public static RoadSignBaseDefinition missing(ResourceLocation id) {
		return fallback(id, "Missing base: " + id);
	}

	private static RoadSignBaseDefinition fallback(ResourceLocation id, String name) {
		return new RoadSignBaseDefinition(
			id,
			name,
			"",
			null,
			2.5F,
			1.25F,
			0.0625F,
			0.0F,
			0.035F,
			0x24529A,
			0xFFFFFF,
			0x70777C,
			0xFFFFFF,
			0.08F,
			0.10F,
			0.84F,
			0.80F,
			4,
			TextAlignment.CENTER,
			false,
			false
		);
	}

	public enum TextAlignment {
		LEFT,
		CENTER,
		RIGHT;

		public static TextAlignment parse(String value) {
			if (value == null) {
				return CENTER;
			}
			return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
				case "left" -> LEFT;
				case "right" -> RIGHT;
				default -> CENTER;
			};
		}
	}
}
