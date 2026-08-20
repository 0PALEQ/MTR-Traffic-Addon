package com.cookiecraftmods.mta.client.sign;

import com.cookiecraftmods.mta.traffic.sign.entity.RoadSignBlockEntity;
import com.cookiecraftmods.mta.traffic.sign.image.RoadSignImageData;
import com.cookiecraftmods.mta.traffic.sign.network.RoadSignNetworking;
import com.cookiecraftmods.mta.traffic.network.MtaPacketPayload;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class RoadSignEditScreen extends Screen {
	private static final int PANEL_MAX_WIDTH = 400;
	private static final int PREVIEW_MAX_WIDTH = 230;
	private static final int PREVIEW_MAX_HEIGHT = 34;
	private static final int PREVIEW_TOP = 49;
	private static final int TAB_Y = 96;
	private static final int CONTENT_START_Y = 118;
	private static final int LINE_SPACING = 19;
	private final BlockPos blockPos;
	private final List<RoadSignBaseDefinition> definitions;
	private final String[] lines = new String[RoadSignBlockEntity.MAX_LINES];
	private final EditBox[] lineInputs = new EditBox[RoadSignBlockEntity.MAX_LINES];
	private final float initialWidth;
	private final float initialHeight;
	private final int initialBackgroundColor;
	private final int initialEdgeColor;
	private int selectedDefinitionIndex;
	private int textColor;
	private String imageId;
	private byte[] pendingImage;
	private Page page = Page.CONTENT;
	private Component imageStatus;
	private int imageStatusColor = 0xA0A0A0;
	private boolean imageStatusLoading;
	private EditBox textColorInput;
	private EditBox widthInput;
	private EditBox heightInput;
	private EditBox backgroundColorInput;
	private EditBox edgeColorInput;
	private Button previousButton;
	private Button nextButton;
	private Button contentTab;
	private Button appearanceTab;
	private Button textColorResetButton;
	private Button importImageButton;
	private Button removeImageButton;
	private Button matchRatioButton;
	private Button resetAppearanceButton;
	private Button saveButton;

	public RoadSignEditScreen(
		BlockPos blockPos,
		ResourceLocation baseId,
		List<String> currentLines,
		int textColor,
		float width,
		float height,
		int backgroundColor,
		int edgeColor,
		String imageId
	) {
		super(Component.translatable("gui.mtr-traffic-addon.road_sign.title"));
		this.blockPos = blockPos;
		this.textColor = RoadSignBlockEntity.normalizeTextColor(textColor);
		initialWidth = RoadSignBlockEntity.normalizeWidth(width);
		initialHeight = RoadSignBlockEntity.normalizeHeight(height);
		initialBackgroundColor = RoadSignBlockEntity.normalizePanelColor(backgroundColor);
		initialEdgeColor = RoadSignBlockEntity.normalizePanelColor(edgeColor);
		this.imageId = RoadSignImageData.normalizeId(imageId);
		for (int index = 0; index < lines.length; index++) {
			lines[index] = currentLines != null && index < currentLines.size() ? currentLines.get(index) : "";
		}

		definitions = new ArrayList<>(RoadSignBaseRegistry.all());
		selectedDefinitionIndex = indexOf(baseId);
		if (selectedDefinitionIndex < 0) {
			definitions.add(0, RoadSignBaseDefinition.missing(baseId));
			selectedDefinitionIndex = 0;
		}
		setInitialImageStatus();
	}

	@Override
	protected void init() {
		final int panelWidth = panelWidth();
		final int left = (width - panelWidth) / 2;
		previousButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> selectDefinition(-1))
			.bounds(left, 19, 42, 18)
			.build());
		nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> selectDefinition(1))
			.bounds(left + panelWidth - 42, 19, 42, 18)
			.build());

		contentTab = addRenderableWidget(Button.builder(Component.translatable("gui.mtr-traffic-addon.road_sign.tab.content"), button -> setPage(Page.CONTENT))
			.bounds(left, TAB_Y, panelWidth / 2 - 2, 18)
			.build());
		appearanceTab = addRenderableWidget(Button.builder(Component.translatable("gui.mtr-traffic-addon.road_sign.tab.appearance"), button -> setPage(Page.APPEARANCE))
			.bounds(left + panelWidth / 2 + 2, TAB_Y, panelWidth / 2 - 2, 18)
			.build());

		for (int index = 0; index < lineInputs.length; index++) {
			final int lineIndex = index;
			final EditBox input = new EditBox(
				font,
				left,
				CONTENT_START_Y + index * LINE_SPACING,
				panelWidth,
				18,
				Component.translatable("gui.mtr-traffic-addon.road_sign.line", index + 1)
			);
			input.setMaxLength(RoadSignBlockEntity.MAX_LINE_LENGTH);
			input.setHint(Component.translatable("gui.mtr-traffic-addon.road_sign.line", index + 1));
			input.setValue(lines[index]);
			input.setResponder(value -> lines[lineIndex] = value);
			lineInputs[index] = addRenderableWidget(input);
		}

		final int colorY = CONTENT_START_Y + RoadSignBlockEntity.MAX_LINES * LINE_SPACING + 3;
		textColorInput = colorInput(left + 92, colorY, 112, Component.translatable("gui.mtr-traffic-addon.road_sign.text_color"), textColor);
		textColorResetButton = addRenderableWidget(Button.builder(Component.translatable("gui.mtr-traffic-addon.road_sign.use_default_color"), button -> textColorInput.setValue(""))
			.bounds(left + panelWidth - 110, colorY, 110, 18)
			.build());

		final int columnWidth = (panelWidth - 6) / 2;
		final int rightColumn = left + columnWidth + 6;
		widthInput = dimensionInput(left + 70, 120, columnWidth - 70, Component.translatable("gui.mtr-traffic-addon.road_sign.width"), initialWidth);
		heightInput = dimensionInput(rightColumn + 70, 120, columnWidth - 70, Component.translatable("gui.mtr-traffic-addon.road_sign.height"), initialHeight);
		backgroundColorInput = colorInput(left + 70, 146, columnWidth - 70, Component.translatable("gui.mtr-traffic-addon.road_sign.background_color"), initialBackgroundColor);
		edgeColorInput = colorInput(rightColumn + 70, 146, columnWidth - 70, Component.translatable("gui.mtr-traffic-addon.road_sign.edge_color"), initialEdgeColor);

		importImageButton = addRenderableWidget(Button.builder(Component.translatable("gui.mtr-traffic-addon.road_sign.import_png"), button -> importImage())
			.bounds(left, 172, panelWidth / 2 - 3, 18)
			.build());
		removeImageButton = addRenderableWidget(Button.builder(Component.translatable("gui.mtr-traffic-addon.road_sign.remove_image"), button -> removeImage())
			.bounds(left + panelWidth / 2 + 3, 172, panelWidth / 2 - 3, 18)
			.build());
		matchRatioButton = addRenderableWidget(Button.builder(Component.translatable("gui.mtr-traffic-addon.road_sign.match_image_ratio"), button -> matchImageRatio())
			.bounds(left, 197, panelWidth / 2 - 3, 18)
			.build());
		resetAppearanceButton = addRenderableWidget(Button.builder(Component.translatable("gui.mtr-traffic-addon.road_sign.reset_appearance"), button -> resetAppearance())
			.bounds(left + panelWidth / 2 + 3, 197, panelWidth / 2 - 3, 18)
			.build());

		final int actionY = Math.min(height - 24, 218);
		saveButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
			.bounds(left, actionY, panelWidth / 2 - 3, 20)
			.build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
			.bounds(left + panelWidth / 2 + 3, actionY, panelWidth / 2 - 3, 20)
			.build());

		updateDefinitionState();
		updatePageState();
		updateSaveState();
		setInitialFocus(lineInputs[0]);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		renderBackground(guiGraphics, mouseX, mouseY, delta);
		guiGraphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
		final RoadSignBaseDefinition definition = selectedDefinition();
		guiGraphics.drawCenteredString(font, definition.nameComponent(), width / 2, 22, 0xFFFFFF);
		guiGraphics.drawCenteredString(font, definition.id().toString(), width / 2, 36, 0x909090);
		renderPreview(guiGraphics, definition);
		refreshLoadedImageStatus();
		final Component validationError = validationError();
		guiGraphics.drawCenteredString(font, validationError == null ? imageStatus : validationError, width / 2, 85, validationError == null ? imageStatusColor : 0xFF6060);

		final int panelWidth = panelWidth();
		final int left = (width - panelWidth) / 2;
		if (page == Page.CONTENT) {
			guiGraphics.drawString(font, Component.translatable("gui.mtr-traffic-addon.road_sign.text_color"), left, CONTENT_START_Y + RoadSignBlockEntity.MAX_LINES * LINE_SPACING + 8, 0xFFFFFF, false);
		} else {
			final int columnWidth = (panelWidth - 6) / 2;
			final int rightColumn = left + columnWidth + 6;
			guiGraphics.drawString(font, Component.translatable("gui.mtr-traffic-addon.road_sign.width"), left, 125, 0xFFFFFF, false);
			guiGraphics.drawString(font, Component.translatable("gui.mtr-traffic-addon.road_sign.height"), rightColumn, 125, 0xFFFFFF, false);
			guiGraphics.drawString(font, Component.translatable("gui.mtr-traffic-addon.road_sign.background_color"), left, 151, 0xFFFFFF, false);
			guiGraphics.drawString(font, Component.translatable("gui.mtr-traffic-addon.road_sign.edge_color"), rightColumn, 151, 0xFFFFFF, false);
		}
		super.render(guiGraphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private EditBox dimensionInput(int x, int y, int inputWidth, Component label, float value) {
		final EditBox input = new EditBox(font, x, y, Math.max(44, inputWidth), 18, label);
		input.setMaxLength(6);
		input.setFilter(candidate -> candidate.matches("[0-9]{0,2}(\\.[0-9]{0,3})?"));
		input.setHint(Component.translatable("gui.mtr-traffic-addon.road_sign.default_color"));
		input.setValue(formatDimension(value));
		input.setResponder(candidate -> updateSaveState());
		return addRenderableWidget(input);
	}

	private EditBox colorInput(int x, int y, int inputWidth, Component label, int value) {
		final EditBox input = new EditBox(font, x, y, Math.max(44, inputWidth), 18, label);
		input.setMaxLength(7);
		input.setFilter(candidate -> candidate.matches("#?[0-9a-fA-F]{0,6}"));
		input.setHint(Component.translatable("gui.mtr-traffic-addon.road_sign.default_color"));
		input.setValue(value < 0 ? "" : String.format(Locale.ROOT, "#%06X", value));
		input.setResponder(candidate -> updateSaveState());
		return addRenderableWidget(input);
	}

	private void selectDefinition(int offset) {
		if (definitions.size() <= 1) {
			return;
		}
		selectedDefinitionIndex = Math.floorMod(selectedDefinitionIndex + offset, definitions.size());
		updateDefinitionState();
	}

	private void setPage(Page newPage) {
		page = newPage;
		updatePageState();
		if (page == Page.CONTENT) {
			setFocused(lineInputs[0]);
		} else {
			setFocused(widthInput);
		}
	}

	private void updateDefinitionState() {
		final RoadSignBaseDefinition definition = selectedDefinition();
		for (int index = 0; index < lineInputs.length; index++) {
			final boolean enabled = index < definition.maxLines();
			lineInputs[index].setEditable(enabled);
			lineInputs[index].active = enabled;
			lineInputs[index].setTextColorUneditable(0x707070);
		}
		previousButton.active = definitions.size() > 1;
		nextButton.active = definitions.size() > 1;
	}

	private void updatePageState() {
		final boolean contentVisible = page == Page.CONTENT;
		for (EditBox lineInput : lineInputs) {
			lineInput.visible = contentVisible;
		}
		textColorInput.visible = contentVisible;
		textColorResetButton.visible = contentVisible;
		widthInput.visible = !contentVisible;
		heightInput.visible = !contentVisible;
		backgroundColorInput.visible = !contentVisible;
		edgeColorInput.visible = !contentVisible;
		importImageButton.visible = !contentVisible;
		removeImageButton.visible = !contentVisible;
		matchRatioButton.visible = !contentVisible;
		resetAppearanceButton.visible = !contentVisible;
		contentTab.active = !contentVisible;
		appearanceTab.active = contentVisible;
		updateImageButtons();
	}

	private void updateSaveState() {
		if (saveButton != null) {
			saveButton.active = validationError() == null;
		}
	}

	private Component validationError() {
		if (textColorInput != null && !isColorValid(textColorInput)) {
			return Component.translatable("gui.mtr-traffic-addon.road_sign.invalid_color");
		}
		if (backgroundColorInput != null && (!isColorValid(backgroundColorInput) || !isColorValid(edgeColorInput))) {
			return Component.translatable("gui.mtr-traffic-addon.road_sign.invalid_color");
		}
		if (widthInput != null && !isDimensionValid(widthInput, RoadSignBlockEntity.MIN_WIDTH, RoadSignBlockEntity.MAX_WIDTH)) {
			return Component.translatable("gui.mtr-traffic-addon.road_sign.invalid_width", RoadSignBlockEntity.MIN_WIDTH, RoadSignBlockEntity.MAX_WIDTH);
		}
		if (heightInput != null && !isDimensionValid(heightInput, RoadSignBlockEntity.MIN_HEIGHT, RoadSignBlockEntity.MAX_HEIGHT)) {
			return Component.translatable("gui.mtr-traffic-addon.road_sign.invalid_height", RoadSignBlockEntity.MIN_HEIGHT, RoadSignBlockEntity.MAX_HEIGHT);
		}
		return null;
	}

	private static boolean isColorValid(EditBox input) {
		if (input == null || input.getValue().isBlank()) {
			return true;
		}
		final String normalized = input.getValue().startsWith("#") ? input.getValue().substring(1) : input.getValue();
		return normalized.length() == 6;
	}

	private static boolean isDimensionValid(EditBox input, float minimum, float maximum) {
		if (input == null || input.getValue().isBlank()) {
			return true;
		}
		try {
			final float value = Float.parseFloat(input.getValue());
			return Float.isFinite(value) && value >= minimum && value <= maximum;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private static int parsedColor(EditBox input) {
		if (input.getValue().isBlank()) {
			return RoadSignBlockEntity.DEFAULT_PANEL_COLOR;
		}
		final String normalized = input.getValue().startsWith("#") ? input.getValue().substring(1) : input.getValue();
		return Integer.parseInt(normalized, 16) & 0xFFFFFF;
	}

	private static float parsedDimension(EditBox input) {
		return input.getValue().isBlank() ? RoadSignBlockEntity.DEFAULT_DIMENSION : Float.parseFloat(input.getValue());
	}

	private void save() {
		if (validationError() != null) {
			return;
		}
		textColor = parsedColor(textColorInput);
		if (pendingImage != null) {
			uploadPendingImage();
		}
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeBlockPos(blockPos);
		buffer.writeUtf(selectedDefinition().id().toString(), RoadSignNetworking.MAX_BASE_ID_LENGTH);
		buffer.writeVarInt(RoadSignBlockEntity.MAX_LINES);
		for (String line : lines) {
			buffer.writeUtf(line, RoadSignBlockEntity.MAX_LINE_LENGTH);
		}
		buffer.writeInt(textColor);
		buffer.writeFloat(parsedDimension(widthInput));
		buffer.writeFloat(parsedDimension(heightInput));
		buffer.writeInt(parsedColor(backgroundColorInput));
		buffer.writeInt(parsedColor(edgeColorInput));
		buffer.writeUtf(imageId, RoadSignImageData.IMAGE_ID_LENGTH);
		ClientPlayNetworking.send(MtaPacketPayload.fromBuffer(RoadSignNetworking.UPDATE_PACKET_ID, buffer));
		onClose();
	}

	private void uploadPendingImage() {
		final int chunkCount = (pendingImage.length + RoadSignNetworking.IMAGE_CHUNK_BYTES - 1) / RoadSignNetworking.IMAGE_CHUNK_BYTES;
		for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
			final int start = chunkIndex * RoadSignNetworking.IMAGE_CHUNK_BYTES;
			final int end = Math.min(pendingImage.length, start + RoadSignNetworking.IMAGE_CHUNK_BYTES);
			final byte[] chunk = Arrays.copyOfRange(pendingImage, start, end);
			final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
			buffer.writeBlockPos(blockPos);
			buffer.writeUtf(imageId, RoadSignImageData.IMAGE_ID_LENGTH);
			buffer.writeVarInt(pendingImage.length);
			buffer.writeVarInt(chunkIndex);
			buffer.writeVarInt(chunkCount);
			buffer.writeByteArray(chunk);
			ClientPlayNetworking.send(MtaPacketPayload.fromBuffer(RoadSignNetworking.UPLOAD_IMAGE_CHUNK_PACKET_ID, buffer));
		}
	}

	private void importImage() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final PointerBuffer filters = stack.mallocPointer(1);
			filters.put(MemoryUtil.memAddress(stack.UTF8("*.png")));
			filters.flip();
			final String selected = TinyFileDialogs.tinyfd_openFileDialog(
				Component.translatable("gui.mtr-traffic-addon.road_sign.import_dialog").getString(),
				Minecraft.getInstance().gameDirectory.getAbsolutePath(),
				filters,
				"PNG images (*.png)",
				false
			);
			if (selected == null || selected.isBlank()) {
				return;
			}
			final Path path = Path.of(selected);
			if (!Files.isRegularFile(path)) {
				throw new IOException("Selected path is not a file");
			}
			if (Files.size(path) > RoadSignImageData.MAX_BYTES) {
				throw new RoadSignImageData.ImageValidationException(RoadSignImageData.ValidationError.TOO_LARGE);
			}
			final byte[] data = Files.readAllBytes(path);
			final RoadSignImageData.ValidatedImage validated = RoadSignImageRegistry.registerLocal(data);
			imageId = validated.id();
			pendingImage = data;
			imageStatus = Component.translatable("gui.mtr-traffic-addon.road_sign.image_selected", validated.width(), validated.height(), shortImageId());
			imageStatusColor = 0x80FF80;
			imageStatusLoading = false;
			updateImageButtons();
		} catch (RoadSignImageData.ImageValidationException exception) {
			imageStatus = validationStatus(exception.error());
			imageStatusColor = 0xFF6060;
			imageStatusLoading = false;
		} catch (IOException | RuntimeException | LinkageError exception) {
			imageStatus = Component.translatable("gui.mtr-traffic-addon.road_sign.image_read_failed");
			imageStatusColor = 0xFF6060;
			imageStatusLoading = false;
		}
	}

	private void removeImage() {
		imageId = "";
		pendingImage = null;
		imageStatus = Component.translatable("gui.mtr-traffic-addon.road_sign.image_none");
		imageStatusColor = 0xA0A0A0;
		imageStatusLoading = false;
		updateImageButtons();
	}

	private void matchImageRatio() {
		final Optional<RoadSignImageRegistry.ImageDimensions> dimensions = RoadSignImageRegistry.dimensions(imageId);
		if (dimensions.isEmpty()) {
			return;
		}
		final float ratio = dimensions.get().aspectRatio();
		float targetHeight = effectiveHeight(selectedDefinition());
		float targetWidth = targetHeight * ratio;
		if (targetWidth > RoadSignBlockEntity.MAX_WIDTH) {
			targetWidth = RoadSignBlockEntity.MAX_WIDTH;
			targetHeight = targetWidth / ratio;
		}
		if (targetWidth < RoadSignBlockEntity.MIN_WIDTH) {
			targetWidth = RoadSignBlockEntity.MIN_WIDTH;
			targetHeight = targetWidth / ratio;
		}
		targetHeight = Math.max(RoadSignBlockEntity.MIN_HEIGHT, Math.min(RoadSignBlockEntity.MAX_HEIGHT, targetHeight));
		targetWidth = Math.max(RoadSignBlockEntity.MIN_WIDTH, Math.min(RoadSignBlockEntity.MAX_WIDTH, targetHeight * ratio));
		widthInput.setValue(formatDimension(targetWidth));
		heightInput.setValue(formatDimension(targetHeight));
	}

	private void resetAppearance() {
		widthInput.setValue("");
		heightInput.setValue("");
		backgroundColorInput.setValue("");
		edgeColorInput.setValue("");
	}

	private void renderPreview(GuiGraphics guiGraphics, RoadSignBaseDefinition definition) {
		final float signWidth = effectiveWidth(definition);
		final float signHeight = effectiveHeight(definition);
		final float aspectRatio = signWidth / signHeight;
		int previewWidth = Math.min(PREVIEW_MAX_WIDTH, Math.max(18, Math.round(PREVIEW_MAX_HEIGHT * aspectRatio)));
		int previewHeight = Math.max(10, Math.round(previewWidth / aspectRatio));
		if (previewHeight > PREVIEW_MAX_HEIGHT) {
			previewHeight = PREVIEW_MAX_HEIGHT;
			previewWidth = Math.max(18, Math.round(previewHeight * aspectRatio));
		}
		final int left = (width - previewWidth) / 2;
		final int top = PREVIEW_TOP + (PREVIEW_MAX_HEIGHT - previewHeight) / 2;
		final int edgeColor = effectiveEdgeColor(definition);
		final int backgroundColor = effectiveBackgroundColor(definition);
		guiGraphics.fill(left, top, left + previewWidth, top + previewHeight, 0xFF000000 | edgeColor);
		final int inset = Math.max(0, Math.round(Math.min(previewWidth, previewHeight) * definition.border()));
		guiGraphics.fill(left + inset, top + inset, left + previewWidth - inset, top + previewHeight - inset, 0xFF000000 | backgroundColor);
		if (definition.texture() != null) {
			guiGraphics.blit(definition.texture(), left, top, 0, 0.0F, 0.0F, previewWidth, previewHeight, previewWidth, previewHeight);
		}
		final Optional<ResourceLocation> customTexture = RoadSignImageRegistry.texture(imageId, blockPos);
		if (customTexture.isPresent()) {
			guiGraphics.blit(customTexture.get(), left, top, 0, 0.0F, 0.0F, previewWidth, previewHeight, previewWidth, previewHeight);
		}
		renderPreviewText(guiGraphics, definition, left, top, previewWidth, previewHeight);
	}

	private void renderPreviewText(GuiGraphics guiGraphics, RoadSignBaseDefinition definition, int left, int top, int previewWidth, int previewHeight) {
		int lastLine = -1;
		for (int index = 0; index < Math.min(definition.maxLines(), lines.length); index++) {
			if (!lines[index].isBlank()) {
				lastLine = index;
			}
		}
		if (lastLine < 0) {
			return;
		}
		int widestLine = 1;
		for (int index = 0; index <= lastLine; index++) {
			widestLine = Math.max(widestLine, font.width(lines[index]));
		}
		final float regionLeft = left + previewWidth * definition.textX();
		final float regionTop = top + previewHeight * definition.textY();
		final float regionWidth = previewWidth * definition.textWidth();
		final float regionHeight = previewHeight * definition.textHeight();
		final float slotScale = regionHeight / definition.maxLines() / font.lineHeight * 0.82F;
		final float scale = Math.max(0.05F, Math.min(1.0F, Math.min(regionWidth / widestLine * 0.96F, slotScale)));
		final float totalHeight = (lastLine + 1) * font.lineHeight * scale;
		final float startY = regionTop + (regionHeight - totalHeight) / 2.0F;
		final int previewTextColor = textColorInput != null && isColorValid(textColorInput) ? parsedColor(textColorInput) : RoadSignBlockEntity.DEFAULT_TEXT_COLOR;
		final int color = 0xFF000000 | definition.effectiveTextColor(previewTextColor);

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(regionLeft, startY, 1.0F);
		guiGraphics.pose().scale(scale, scale, 1.0F);
		final float availableWidth = regionWidth / scale;
		for (int index = 0; index <= lastLine; index++) {
			final int lineWidth = font.width(lines[index]);
			final int x = Math.round(switch (definition.alignment()) {
				case LEFT -> 0.0F;
				case RIGHT -> availableWidth - lineWidth;
				case CENTER -> (availableWidth - lineWidth) / 2.0F;
			});
			guiGraphics.drawString(font, lines[index], x, index * font.lineHeight, color, definition.shadow());
		}
		guiGraphics.pose().popPose();
	}

	private float effectiveWidth(RoadSignBaseDefinition definition) {
		return widthInput != null && isDimensionValid(widthInput, RoadSignBlockEntity.MIN_WIDTH, RoadSignBlockEntity.MAX_WIDTH) && !widthInput.getValue().isBlank()
			? parsedDimension(widthInput)
			: definition.width();
	}

	private float effectiveHeight(RoadSignBaseDefinition definition) {
		return heightInput != null && isDimensionValid(heightInput, RoadSignBlockEntity.MIN_HEIGHT, RoadSignBlockEntity.MAX_HEIGHT) && !heightInput.getValue().isBlank()
			? parsedDimension(heightInput)
			: definition.height();
	}

	private int effectiveBackgroundColor(RoadSignBaseDefinition definition) {
		return backgroundColorInput != null && isColorValid(backgroundColorInput) && !backgroundColorInput.getValue().isBlank()
			? parsedColor(backgroundColorInput)
			: definition.backgroundColor();
	}

	private int effectiveEdgeColor(RoadSignBaseDefinition definition) {
		return edgeColorInput != null && isColorValid(edgeColorInput) && !edgeColorInput.getValue().isBlank()
			? parsedColor(edgeColorInput)
			: definition.borderColor();
	}

	private void setInitialImageStatus() {
		if (imageId.isEmpty()) {
			imageStatus = Component.translatable("gui.mtr-traffic-addon.road_sign.image_none");
			imageStatusLoading = false;
		} else {
			imageStatus = Component.translatable("gui.mtr-traffic-addon.road_sign.image_loading", shortImageId());
			imageStatusLoading = true;
		}
	}

	private void refreshLoadedImageStatus() {
		if (!imageStatusLoading) {
			return;
		}
		RoadSignImageRegistry.dimensions(imageId).ifPresent(dimensions -> {
			imageStatus = Component.translatable("gui.mtr-traffic-addon.road_sign.image_selected", dimensions.width(), dimensions.height(), shortImageId());
			imageStatusColor = 0x80FF80;
			imageStatusLoading = false;
			updateImageButtons();
		});
	}

	private void updateImageButtons() {
		if (removeImageButton != null) {
			removeImageButton.active = !imageId.isEmpty();
		}
		if (matchRatioButton != null) {
			matchRatioButton.active = RoadSignImageRegistry.dimensions(imageId).isPresent();
		}
	}

	private Component validationStatus(RoadSignImageData.ValidationError error) {
		return switch (error) {
			case TOO_LARGE -> Component.translatable("gui.mtr-traffic-addon.road_sign.image_too_large", RoadSignImageData.MAX_BYTES / 1024);
			case INVALID_DIMENSIONS -> Component.translatable("gui.mtr-traffic-addon.road_sign.image_dimensions", RoadSignImageData.MAX_DIMENSION, RoadSignImageData.MAX_PIXELS);
			case INVALID_FORMAT -> Component.translatable("gui.mtr-traffic-addon.road_sign.image_invalid");
		};
	}

	private String shortImageId() {
		return imageId.length() <= 8 ? imageId : imageId.substring(0, 8);
	}

	private int indexOf(ResourceLocation id) {
		for (int index = 0; index < definitions.size(); index++) {
			if (definitions.get(index).id().equals(id)) {
				return index;
			}
		}
		return -1;
	}

	private RoadSignBaseDefinition selectedDefinition() {
		return definitions.get(Math.max(0, Math.min(selectedDefinitionIndex, definitions.size() - 1)));
	}

	private int panelWidth() {
		return Math.min(PANEL_MAX_WIDTH, width - 24);
	}

	private static String formatDimension(float value) {
		if (value <= 0.0F) {
			return "";
		}
		String formatted = String.format(Locale.ROOT, "%.3f", value);
		while (formatted.contains(".") && (formatted.endsWith("0") || formatted.endsWith("."))) {
			formatted = formatted.substring(0, formatted.length() - 1);
		}
		return formatted;
	}

	private enum Page {
		CONTENT,
		APPEARANCE
	}
}
