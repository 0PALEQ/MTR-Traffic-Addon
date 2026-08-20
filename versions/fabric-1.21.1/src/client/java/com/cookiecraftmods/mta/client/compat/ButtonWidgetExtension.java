package com.cookiecraftmods.mta.client.compat;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class ButtonWidgetExtension extends Button {
	public ButtonWidgetExtension(int x, int y, int width, int height, Component message, PressAction action) {
		super(x, y, width, height, message, button -> action.onPress((ButtonWidgetExtension) button), DEFAULT_NARRATION);
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

	public int getWidth2() {
		return getWidth();
	}

	public int getHeight2() {
		return getHeight();
	}

	@FunctionalInterface
	public interface PressAction {
		void onPress(ButtonWidgetExtension button);
	}
}
