package com.cookiecraftmods.mta.traffic.sign.entity;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.init.ModBlockEntities;
import com.cookiecraftmods.mta.traffic.sign.image.RoadSignImageData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RoadSignBlockEntity extends BlockEntity {
	public static final ResourceLocation DEFAULT_BASE_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "blue_direction");
	public static final int MAX_LINES = 4;
	public static final int MAX_LINE_LENGTH = 64;
	public static final int DEFAULT_TEXT_COLOR = -1;
	public static final float DEFAULT_DIMENSION = 0.0F;
	public static final int DEFAULT_PANEL_COLOR = -1;
	public static final float MIN_WIDTH = 0.25F;
	public static final float MAX_WIDTH = 8.0F;
	public static final float MIN_HEIGHT = 0.25F;
	public static final float MAX_HEIGHT = 4.0F;
	private static final String KEY_BASE = "Base";
	private static final String KEY_LINES = "Lines";
	private static final String KEY_TEXT_COLOR = "TextColor";
	private static final String KEY_WIDTH = "Width";
	private static final String KEY_HEIGHT = "Height";
	private static final String KEY_BACKGROUND_COLOR = "BackgroundColor";
	private static final String KEY_EDGE_COLOR = "EdgeColor";
	private static final String KEY_IMAGE_ID = "ImageId";

	private ResourceLocation baseId = DEFAULT_BASE_ID;
	private List<String> lines = emptyLines();
	private int textColor = DEFAULT_TEXT_COLOR;
	private float width = DEFAULT_DIMENSION;
	private float height = DEFAULT_DIMENSION;
	private int backgroundColor = DEFAULT_PANEL_COLOR;
	private int edgeColor = DEFAULT_PANEL_COLOR;
	private String imageId = "";

	public RoadSignBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ROAD_SIGN, pos, state);
	}

	public ResourceLocation getBaseId() {
		return baseId;
	}

	public List<String> getLines() {
		return List.copyOf(lines);
	}

	public int getTextColor() {
		return textColor;
	}

	public float getWidth() {
		return width;
	}

	public float getHeight() {
		return height;
	}

	public int getBackgroundColor() {
		return backgroundColor;
	}

	public int getEdgeColor() {
		return edgeColor;
	}

	public String getImageId() {
		return imageId;
	}

	public void setContent(
		ResourceLocation newBaseId,
		List<String> newLines,
		int newTextColor,
		float newWidth,
		float newHeight,
		int newBackgroundColor,
		int newEdgeColor,
		String newImageId
	) {
		final ResourceLocation normalizedBaseId = newBaseId == null ? DEFAULT_BASE_ID : newBaseId;
		final List<String> normalizedLines = normalizeLines(newLines);
		final int normalizedTextColor = normalizeTextColor(newTextColor);
		final float normalizedWidth = normalizeDimension(newWidth, MIN_WIDTH, MAX_WIDTH);
		final float normalizedHeight = normalizeDimension(newHeight, MIN_HEIGHT, MAX_HEIGHT);
		final int normalizedBackgroundColor = normalizePanelColor(newBackgroundColor);
		final int normalizedEdgeColor = normalizePanelColor(newEdgeColor);
		final String normalizedImageId = RoadSignImageData.normalizeId(newImageId);
		if (baseId.equals(normalizedBaseId)
			&& lines.equals(normalizedLines)
			&& textColor == normalizedTextColor
			&& Float.compare(width, normalizedWidth) == 0
			&& Float.compare(height, normalizedHeight) == 0
			&& backgroundColor == normalizedBackgroundColor
			&& edgeColor == normalizedEdgeColor
			&& imageId.equals(normalizedImageId)) {
			return;
		}

		baseId = normalizedBaseId;
		lines = normalizedLines;
		textColor = normalizedTextColor;
		width = normalizedWidth;
		height = normalizedHeight;
		backgroundColor = normalizedBackgroundColor;
		edgeColor = normalizedEdgeColor;
		imageId = normalizedImageId;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		final ResourceLocation loadedBaseId = ResourceLocation.tryParse(tag.getString(KEY_BASE));
		baseId = loadedBaseId == null ? DEFAULT_BASE_ID : loadedBaseId;
		final ListTag storedLines = tag.getList(KEY_LINES, Tag.TAG_STRING);
		final List<String> loadedLines = new ArrayList<>(MAX_LINES);
		for (int index = 0; index < Math.min(storedLines.size(), MAX_LINES); index++) {
			loadedLines.add(storedLines.getString(index));
		}
		lines = normalizeLines(loadedLines);
		textColor = tag.contains(KEY_TEXT_COLOR, Tag.TAG_INT) ? normalizeTextColor(tag.getInt(KEY_TEXT_COLOR)) : DEFAULT_TEXT_COLOR;
		width = tag.contains(KEY_WIDTH, Tag.TAG_FLOAT) ? normalizeDimension(tag.getFloat(KEY_WIDTH), MIN_WIDTH, MAX_WIDTH) : DEFAULT_DIMENSION;
		height = tag.contains(KEY_HEIGHT, Tag.TAG_FLOAT) ? normalizeDimension(tag.getFloat(KEY_HEIGHT), MIN_HEIGHT, MAX_HEIGHT) : DEFAULT_DIMENSION;
		backgroundColor = tag.contains(KEY_BACKGROUND_COLOR, Tag.TAG_INT) ? normalizePanelColor(tag.getInt(KEY_BACKGROUND_COLOR)) : DEFAULT_PANEL_COLOR;
		edgeColor = tag.contains(KEY_EDGE_COLOR, Tag.TAG_INT) ? normalizePanelColor(tag.getInt(KEY_EDGE_COLOR)) : DEFAULT_PANEL_COLOR;
		imageId = RoadSignImageData.normalizeId(tag.getString(KEY_IMAGE_ID));
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putString(KEY_BASE, baseId.toString());
		final ListTag storedLines = new ListTag();
		for (String line : lines) {
			storedLines.add(StringTag.valueOf(line));
		}
		tag.put(KEY_LINES, storedLines);
		tag.putInt(KEY_TEXT_COLOR, textColor);
		tag.putFloat(KEY_WIDTH, width);
		tag.putFloat(KEY_HEIGHT, height);
		tag.putInt(KEY_BACKGROUND_COLOR, backgroundColor);
		tag.putInt(KEY_EDGE_COLOR, edgeColor);
		if (!imageId.isEmpty()) {
			tag.putString(KEY_IMAGE_ID, imageId);
		} else {
			tag.remove(KEY_IMAGE_ID);
		}
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata();
	}

	public static int normalizeTextColor(int color) {
		return color < 0 ? DEFAULT_TEXT_COLOR : color & 0xFFFFFF;
	}

	public static int normalizePanelColor(int color) {
		return color < 0 ? DEFAULT_PANEL_COLOR : color & 0xFFFFFF;
	}

	public static float normalizeWidth(float value) {
		return normalizeDimension(value, MIN_WIDTH, MAX_WIDTH);
	}

	public static float normalizeHeight(float value) {
		return normalizeDimension(value, MIN_HEIGHT, MAX_HEIGHT);
	}

	private static float normalizeDimension(float value, float minimum, float maximum) {
		if (!Float.isFinite(value) || value <= 0.0F) {
			return DEFAULT_DIMENSION;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static List<String> normalizeLines(List<String> source) {
		final List<String> normalized = new ArrayList<>(MAX_LINES);
		for (int index = 0; index < MAX_LINES; index++) {
			normalized.add(sanitizeLine(source != null && index < source.size() ? source.get(index) : ""));
		}
		return List.copyOf(normalized);
	}

	private static String sanitizeLine(String value) {
		final String source = Objects.requireNonNullElse(value, "");
		final StringBuilder builder = new StringBuilder(source.length());
		source.codePoints()
			.filter(codePoint -> !Character.isISOControl(codePoint) && codePoint != '\u00A7')
			.limit(MAX_LINE_LENGTH)
			.forEach(builder::appendCodePoint);
		return builder.toString();
	}

	private static List<String> emptyLines() {
		return List.of("", "", "", "");
	}
}
