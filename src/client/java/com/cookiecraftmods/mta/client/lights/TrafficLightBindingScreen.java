package com.cookiecraftmods.mta.client.lights;

import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNodeType;
import com.cookiecraftmods.mta.traffic.lights.network.TrafficLightBindingNetworking;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TrafficLightBindingScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private static final int ROWS = 10;
	private final BlockPos blockPos;
	private final List<IntersectionOption> intersections;
	private final List<NodeOption> visibleNodes = new ArrayList<>();
	private int selectedIntersectionIndex;
	private int page;

	public TrafficLightBindingScreen(BlockPos blockPos, List<IntersectionOption> intersections) {
		super(Component.literal("Bind Traffic Light"));
		this.blockPos = blockPos;
		this.intersections = intersections;
	}

	@Override
	protected void init() {
		rebuildBindingWidgets();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
		renderBackground(guiGraphics);
		guiGraphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
		final IntersectionOption intersection = selectedIntersection();
		if (intersection != null) {
			guiGraphics.drawCenteredString(font, Component.literal(intersection.name()), width / 2, 34, 0xA0C8FF);
			guiGraphics.drawCenteredString(font, Component.literal("Traffic light @ " + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ()), width / 2, 48, 0xC0C0C0);
		}
		super.render(guiGraphics, mouseX, mouseY, delta);
	}

	private void rebuildBindingWidgets() {
		clearWidgets();
		visibleNodes.clear();
		final IntersectionOption intersection = selectedIntersection();
		if (intersection == null) {
			return;
		}
		final int panelWidth = Math.min(360, width - 40);
		final int left = (width - panelWidth) / 2;
		int y = 68;
		if (intersections.size() > 1) {
			addRenderableWidget(Button.builder(Component.literal("< Intersection"), button -> {
				selectedIntersectionIndex = Math.max(0, selectedIntersectionIndex - 1);
				page = 0;
				rebuildBindingWidgets();
			}).bounds(left, y, panelWidth / 2 - 3, 18).build());
			addRenderableWidget(Button.builder(Component.literal("Intersection >"), button -> {
				selectedIntersectionIndex = Math.min(intersections.size() - 1, selectedIntersectionIndex + 1);
				page = 0;
				rebuildBindingWidgets();
			}).bounds(left + panelWidth / 2 + 3, y, panelWidth / 2 - 3, 18).build());
			y += 24;
		}
		final int start = page * ROWS;
		final int end = Math.min(intersection.nodes().size(), start + ROWS);
		for (int i = start; i < end; i++) {
			final TrafficIntersectionNode node = intersection.nodes().get(i);
			final NodeOption option = new NodeOption(intersection.id(), node.number());
			visibleNodes.add(option);
			addRenderableWidget(Button.builder(Component.literal(nodeLabel(node)), button -> bind(option))
				.bounds(left, y + (i - start) * ROW_HEIGHT, panelWidth, 18)
				.build());
		}
		y += ROWS * ROW_HEIGHT + 6;
		addRenderableWidget(Button.builder(Component.literal("Prev"), button -> {
			page = Math.max(0, page - 1);
			rebuildBindingWidgets();
		}).bounds(left, y, 74, 18).build()).active = page > 0;
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(left + panelWidth / 2 - 37, y, 74, 18).build());
		addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
			page = Math.min(maxPage(intersection), page + 1);
			rebuildBindingWidgets();
		}).bounds(left + panelWidth - 74, y, 74, 18).build()).active = page < maxPage(intersection);
	}

	private void bind(NodeOption option) {
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeBlockPos(blockPos);
		buffer.writeUtf(option.intersectionId());
		buffer.writeVarInt(option.nodeNumber());
		ClientPlayNetworking.send(TrafficLightBindingNetworking.BIND_PACKET_ID, buffer);
		onClose();
	}

	private IntersectionOption selectedIntersection() {
		return selectedIntersectionIndex >= 0 && selectedIntersectionIndex < intersections.size() ? intersections.get(selectedIntersectionIndex) : null;
	}

	private static int maxPage(IntersectionOption intersection) {
		return Math.max(0, (intersection.nodes().size() - 1) / ROWS);
	}

	private static String nodeLabel(TrafficIntersectionNode node) {
		final String type = node.type() == TrafficIntersectionNodeType.IN ? "IN" : "OUT";
		return type + " #" + node.number() + " @ " + node.x() + ", " + node.y() + ", " + node.z();
	}

	public record IntersectionOption(String id, String name, List<TrafficIntersectionNode> nodes) {
		public IntersectionOption {
			nodes = nodes == null ? List.of() : List.copyOf(nodes);
		}
	}

	private record NodeOption(String intersectionId, int nodeNumber) {
	}
}
