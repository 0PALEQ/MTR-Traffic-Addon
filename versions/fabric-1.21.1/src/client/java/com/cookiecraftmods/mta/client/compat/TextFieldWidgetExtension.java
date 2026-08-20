package com.cookiecraftmods.mta.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class TextFieldWidgetExtension extends EditBox {
	private final int configuredMaxLength;
	private final TextCase textCase;
	private final String filter;
	private final String emptySuggestion;
	private Consumer<String> changedListener = ignored -> { };
	private boolean normalizing;

	public TextFieldWidgetExtension(int x, int y, int width, int height, Component message, int maxLength, TextCase textCase, String filter, String suggestion) {
		super(Minecraft.getInstance().font, x, y, width, height, message);
		configuredMaxLength = maxLength;
		this.textCase = textCase == null ? TextCase.DEFAULT : textCase;
		this.filter = filter;
		emptySuggestion = suggestion;
		setMaxLength(Integer.MAX_VALUE);
		setResponder(this::onChanged);
		setSuggestion(suggestion);
	}

	private void onChanged(String value) {
		if (normalizing) {
			return;
		}
		String normalized = textCase.convert(value == null ? "" : value);
		if (filter != null && !filter.isEmpty()) {
			normalized = normalized.replaceAll(filter, "");
		}
		if (normalized.length() > configuredMaxLength) {
			normalized = normalized.substring(0, configuredMaxLength);
		}
		if (!normalized.equals(value)) {
			normalizing = true;
			setValue(normalized);
			normalizing = false;
		}
		setSuggestion(normalized.isEmpty() ? emptySuggestion : "");
		changedListener.accept(normalized);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (visible && mouseX >= getX() && mouseX <= getX() + width && mouseY >= getY() && mouseY <= getY() + height) {
			if (button == 1) {
				setValue("");
			}
			return super.mouseClicked(mouseX, mouseY, 0);
		}
		setFocused(false);
		return false;
	}

	public void setChangedListener2(Consumer<String> listener) {
		changedListener = listener == null ? ignored -> { } : listener;
	}

	public String getText2() {
		return getValue();
	}

	public void setText2(String text) {
		setValue(text == null ? "" : text);
	}

	public int getX2() {
		return getX();
	}

	public int getY2() {
		return getY();
	}

	public void setX2(int x) {
		setX(x);
	}

	public void setY2(int y) {
		setY(y);
	}

	public void setWidth2(int width) {
		setWidth(width);
	}

	public int getWidth2() {
		return getWidth();
	}

	public int getHeight2() {
		return getHeight();
	}

	public boolean isFocused2() {
		return isFocused();
	}

	public void setTextFieldFocused2(boolean focused) {
		setFocused(focused);
	}

	public void setVisible2(boolean visible) {
		setVisible(visible);
	}

	public void setActiveMapped(boolean active) {
		this.active = active;
	}

	public void setSelectionStart2(int position) {
		setCursorPosition(position);
	}

	public void setSelectionEnd2(int position) {
		setHighlightPos(position);
	}
}
