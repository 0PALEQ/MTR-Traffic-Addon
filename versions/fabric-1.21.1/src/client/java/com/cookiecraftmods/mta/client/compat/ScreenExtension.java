package com.cookiecraftmods.mta.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Keeps the dashboard's mapped hook names while delegating to Mojang's 1.21.1 GUI API. */
public abstract class ScreenExtension extends Screen {
	protected ScreenExtension(Component title) {
		super(title);
	}

	@Override
	protected final void init() {
		init2();
	}

	protected void init2() {
	}

	@Override
	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		render(new GraphicsHolder(graphics), mouseX, mouseY, delta);
	}

	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		super.render(graphicsHolder.graphics(), mouseX, mouseY, delta);
	}

	@Override
	public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		// The mapped MTR screen base used by the original dashboard only rendered
		// child widgets here. Vanilla 1.21 also applies its full-screen blur from
		// Screen.render(), after the dashboard has drawn its map and labels.
	}

	protected final void addChild(ClickableWidget widget) {
		addRenderableWidget(widget.widget());
	}

	@Override
	public final boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return keyPressed2(keyCode, scanCode, modifiers);
	}

	public boolean keyPressed2(int keyCode, int scanCode, int modifiers) {
		return super.keyPressed(keyCode, scanCode, modifiers);
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
	public final boolean mouseReleased(double mouseX, double mouseY, int button) {
		return mouseReleased2(mouseX, mouseY, button);
	}

	public boolean mouseReleased2(double mouseX, double mouseY, int button) {
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public final boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		return mouseScrolled2(mouseX, mouseY, verticalAmount);
	}

	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		return super.mouseScrolled(mouseX, mouseY, 0, amount);
	}

	@Override
	public final void tick() {
		tick2();
	}

	public void tick2() {
		super.tick();
	}

	@Override
	public final boolean isPauseScreen() {
		return isPauseScreen2();
	}

	public boolean isPauseScreen2() {
		return super.isPauseScreen();
	}

	@Override
	public final void onClose() {
		onClose2();
	}

	public void onClose2() {
		super.onClose();
	}
}
