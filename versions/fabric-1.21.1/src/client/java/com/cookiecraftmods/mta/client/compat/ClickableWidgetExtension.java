package com.cookiecraftmods.mta.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class ClickableWidgetExtension extends AbstractWidget {
	public ClickableWidgetExtension(int x, int y, int width, int height) {
		super(x, y, width, height, Component.empty());
	}

	@Override
	protected final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		render(new GraphicsHolder(graphics), mouseX, mouseY, delta);
	}

	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
	}

	@Override
	public final boolean mouseClicked(double mouseX, double mouseY, int button) {
		return mouseClicked2(mouseX, mouseY, button);
	}

	public boolean mouseClicked2(double mouseX, double mouseY, int button) {
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public final boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return mouseDragged2(mouseX, mouseY, button, deltaX, deltaY);
	}

	public boolean mouseDragged2(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public final boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		return mouseScrolled2(mouseX, mouseY, verticalAmount);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return mouseScrolled2(mouseX, mouseY, amount);
	}

	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		return false;
	}

	@Override
	public final boolean isMouseOver(double mouseX, double mouseY) {
		return isMouseOver2(mouseX, mouseY);
	}

	public boolean isMouseOver2(double mouseX, double mouseY) {
		return super.isMouseOver(mouseX, mouseY);
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

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
