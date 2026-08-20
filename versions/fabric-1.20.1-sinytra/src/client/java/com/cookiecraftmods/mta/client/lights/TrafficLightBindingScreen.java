package com.cookiecraftmods.mta.client.lights;

import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionGroup;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNodeType;
import com.cookiecraftmods.mta.traffic.lights.TrafficLightBindingTargetType;
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
	private final List<BindingOption> visibleOptions = new ArrayList<>();
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
		visibleOptions.clear();
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
		final List<BindingOption> options = bindingOptions(intersection);
		final int end = Math.min(options.size(), start + ROWS);
		for (int i = start; i < end; i++) {
			final BindingOption option = options.get(i);
			visibleOptions.add(option);
			addRenderableWidget(Button.builder(Component.literal(option.label()), button -> bind(option))
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

	private void bind(BindingOption option) {
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeBlockPos(blockPos);
		buffer.writeUtf(option.intersectionId());
		buffer.writeEnum(option.targetType());
		buffer.writeVarInt(option.targetNumber());
		ClientPlayNetworking.send(TrafficLightBindingNetworking.BIND_PACKET_ID, buffer);
		onClose();
	}

	private IntersectionOption selectedIntersection() {
		return selectedIntersectionIndex >= 0 && selectedIntersectionIndex < intersections.size() ? intersections.get(selectedIntersectionIndex) : null;
	}

	private static int maxPage(IntersectionOption intersection) {
		return Math.max(0, (bindingOptions(intersection).size() - 1) / ROWS);
	}

	private static List<BindingOption> bindingOptions(IntersectionOption intersection) {
		final List<BindingOption> options = new ArrayList<>();
		for (int i = 0; i < intersection.groups().size(); i++) {
			final TrafficIntersectionGroup group = intersection.groups().get(i);
			options.add(new BindingOption(intersection.id(), TrafficLightBindingTargetType.GROUP, i, groupLabel(i, group)));
		}
		for (TrafficIntersectionNode node : intersection.nodes()) {
			options.add(new BindingOption(intersection.id(), TrafficLightBindingTargetType.NODE, node.number(), nodeLabel(node)));
		}
		return options;
	}

	private static String groupLabel(int index, TrafficIntersectionGroup group) {
		final String nodes = group.nodeNumbers().isEmpty() ? "no nodes" : "nodes " + group.nodeNumbers();
		return "Group #" + (index + 1) + " " + group.name() + " (" + nodes + ")";
	}

	private static String nodeLabel(TrafficIntersectionNode node) {
		final String type = node.type() == TrafficIntersectionNodeType.IN ? "IN" : "OUT";
		return type + " #" + node.number() + " @ " + node.x() + ", " + node.y() + ", " + node.z();
	}

	public record IntersectionOption(String id, String name, List<TrafficIntersectionGroup> groups, List<TrafficIntersectionNode> nodes) {
		public IntersectionOption {
			groups = groups == null ? List.of() : List.copyOf(groups);
			nodes = nodes == null ? List.of() : List.copyOf(nodes);
		}
	}

	private record BindingOption(String intersectionId, TrafficLightBindingTargetType targetType, int targetNumber, String label) {
	}
}
