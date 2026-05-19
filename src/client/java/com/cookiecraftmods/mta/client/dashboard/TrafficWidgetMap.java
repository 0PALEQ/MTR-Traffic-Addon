package com.cookiecraftmods.mta.client.dashboard;

import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.ClickableWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.WorldMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class TrafficWidgetMap extends ClickableWidgetExtension implements IGui {
	private static final int COLOR_SPAWN = 0xFF4285F4;
	private static final int COLOR_DESPAWN = 0xFFF4B400;
	private static final int COLOR_INTERSECTION = 0x55FF6D00;
	private static final int COLOR_INTERSECTION_PREVIEW = 0x77FFD166;
	private static final int COLOR_INTERSECTION_NODE_IN = 0xFF00C853;
	private static final int COLOR_INTERSECTION_NODE_OUT = 0xFFD50000;
	private static final int COLOR_SELECTED_GROUP_NODE = 0xAA7EE787;
	private static final int COLOR_SELECTED_NODE = 0xFFFFFFFF;
	private static final int COLOR_SELECTED = 0xFF34A853;
	private static final int COLOR_DISABLED = 0xFF777777;
	private static final int SCALE_UPPER_LIMIT = 64;
	private static final double SCALE_LOWER_LIMIT = 1 / 128D;
	private static final double SELECT_DISTANCE_BLOCKS = 4.0D;

	private final Supplier<List<ClientTrafficDashboardEntry>> entriesSupplier;
	private final Supplier<List<ClientTrafficIntersectionEntry>> intersectionsSupplier;
	private final Supplier<ClientTrafficDashboardEntry> selectedEntrySupplier;
	private final Supplier<ClientTrafficIntersectionEntry> selectedIntersectionSupplier;
	private final Supplier<String> selectedNodeSupplier;
	private final Supplier<List<Integer>> selectedGroupNodeNumbersSupplier;
	private final Consumer<ClientTrafficDashboardEntry> selectEntryConsumer;
	private final Consumer<ClientTrafficIntersectionEntry> selectIntersectionConsumer;
	private final BiConsumer<Double, Double> createCornerConsumer;
	private final TriConsumer<ClientTrafficIntersectionEntry, Long, Long> nodeClickConsumer;
	private final ClientWorld world;
	private final ClientPlayerEntity player;
	private final WorldMap worldMap;

	private double scale = 1;
	private double centerX;
	private double centerZ;
	private boolean creatingIntersection;
	private BlockPos pendingIntersectionCorner;

	public TrafficWidgetMap(Supplier<List<ClientTrafficDashboardEntry>> entriesSupplier, Supplier<List<ClientTrafficIntersectionEntry>> intersectionsSupplier, Supplier<ClientTrafficDashboardEntry> selectedEntrySupplier, Supplier<ClientTrafficIntersectionEntry> selectedIntersectionSupplier, Supplier<String> selectedNodeSupplier, Supplier<List<Integer>> selectedGroupNodeNumbersSupplier, Consumer<ClientTrafficDashboardEntry> selectEntryConsumer, Consumer<ClientTrafficIntersectionEntry> selectIntersectionConsumer, BiConsumer<Double, Double> createCornerConsumer, TriConsumer<ClientTrafficIntersectionEntry, Long, Long> nodeClickConsumer) {
		super(0, 0, 0, 0);
		this.entriesSupplier = entriesSupplier;
		this.intersectionsSupplier = intersectionsSupplier;
		this.selectedEntrySupplier = selectedEntrySupplier;
		this.selectedIntersectionSupplier = selectedIntersectionSupplier;
		this.selectedNodeSupplier = selectedNodeSupplier;
		this.selectedGroupNodeNumbersSupplier = selectedGroupNodeNumbersSupplier;
		this.selectEntryConsumer = selectEntryConsumer;
		this.selectIntersectionConsumer = selectIntersectionConsumer;
		this.createCornerConsumer = createCornerConsumer;
		this.nodeClickConsumer = nodeClickConsumer;

		final Minecraft minecraft = Minecraft.getInstance();
		world = new ClientWorld(minecraft.level);
		player = new ClientPlayerEntity(minecraft.player);
		if (minecraft.player == null) {
			centerX = 0;
			centerZ = 0;
		} else {
			centerX = minecraft.player.getX();
			centerZ = minecraft.player.getZ();
		}

		worldMap = new WorldMap();
		worldMap.setMapOverlayMode(WorldMap.MapOverlayMode.TOP_VIEW);
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		if (width <= 0 || height <= 0) {
			return;
		}

		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		final DoubleDoubleImmutablePair mouseWorldPos = coordsToWorldPos(mouseX - getX2(), mouseY - getY2());
		final int topLeftX = (int) Math.floor(coordsToWorldPos(0, 0).leftDouble());
		final int topLeftZ = (int) Math.floor(coordsToWorldPos(0, 0).rightDouble());
		final int bottomRightX = (int) Math.floor(coordsToWorldPos(width, height).leftDouble());
		final int bottomRightZ = (int) Math.floor(coordsToWorldPos(width, height).rightDouble());

		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(getX2(), getY2(), getX2() + width, getY2() + height, ARGB_BLACK);
		guiDrawing.finishDrawingRectangle();

		if (world != null) {
			worldMap.tick(World.cast(world), player, delta);
			worldMap.forEachTile(mapImage -> {
				final int posX = mapImage.chunkX * WorldMap.CHUNK_SIZE;
				final int posZ = mapImage.chunkZ * WorldMap.CHUNK_SIZE;
				if (posX + WorldMap.CHUNK_SIZE < topLeftX || posZ + WorldMap.CHUNK_SIZE < topLeftZ || posX > bottomRightX || posZ > bottomRightZ) {
					return;
				}
				drawTextureFromWorldCoords(guiDrawing, mapImage.textureId, posX, posZ, posX + WorldMap.CHUNK_SIZE, posZ + WorldMap.CHUNK_SIZE);
			});
		}

		guiDrawing.beginDrawingRectangle();
		for (ClientTrafficIntersectionEntry intersection : intersectionsSupplier.get()) {
			drawIntersection(guiDrawing, intersection, intersection.equals(selectedIntersectionSupplier.get()));
		}
		for (ClientTrafficDashboardEntry entry : entriesSupplier.get()) {
			final int color = colorForEntry(entry);
			if (entry.hasConnectorRoute()) {
				drawConnector(guiDrawing, entry.connectorStartPos(), entry.connectorEndPos(), color);
			}
			drawMarker(guiDrawing, entry.blockPos(), color, entry.equals(selectedEntrySupplier.get()));
		}
		if (creatingIntersection) {
			drawPendingIntersection(guiDrawing, mouseWorldPos.leftDouble(), mouseWorldPos.rightDouble());
		}
		if (player != null) {
			drawPlayerSymbol(guiDrawing, player);
		}
		guiDrawing.finishDrawingRectangle();

		if (scale >= 8) {
			for (ClientTrafficDashboardEntry entry : entriesSupplier.get()) {
				drawEntryLabel(graphicsHolder, entry);
			}
			for (ClientTrafficIntersectionEntry intersection : intersectionsSupplier.get()) {
				drawIntersectionLabel(graphicsHolder, intersection);
			}
		}
		if (scale >= 4) {
			final ClientTrafficIntersectionEntry selectedIntersection = selectedIntersectionSupplier.get();
			if (selectedIntersection != null) {
				drawIntersectionNodeLabels(graphicsHolder, selectedIntersection);
			}
		}

		final String mousePosText = String.format("(%.1f, %.1f)", mouseWorldPos.leftDouble(), mouseWorldPos.rightDouble());
		graphicsHolder.drawText(mousePosText, getX2() + width - TEXT_PADDING - GraphicsHolder.getTextWidth(mousePosText), getY2() + TEXT_PADDING, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
	}

	@Override
	public boolean mouseDragged2(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (width <= 0 || height <= 0) {
			return false;
		}

		centerX -= deltaX / scale;
		centerZ -= deltaY / scale;
		return true;
	}

	@Override
	public boolean mouseClicked2(double mouseX, double mouseY, int button) {
		if (width <= 0 || height <= 0) {
			return false;
		}

		if (!isMouseOver2(mouseX, mouseY)) {
			return false;
		}

		final DoubleDoubleImmutablePair worldPos = coordsToWorldPos(mouseX - getX2(), mouseY - getY2());
		if (creatingIntersection && createCornerConsumer != null) {
			createCornerConsumer.accept(worldPos.leftDouble(), worldPos.rightDouble());
			return true;
		}

		final ClientTrafficIntersectionEntry selectedIntersection = selectedIntersectionSupplier.get();
		if (selectedIntersection != null && nodeClickConsumer != null) {
			final TrafficNodeHit nodeHit = nearestIntersectionNode(selectedIntersection, worldPos.leftDouble(), worldPos.rightDouble());
			if (nodeHit != null && nodeHit.distance <= SELECT_DISTANCE_BLOCKS) {
				nodeClickConsumer.accept(selectedIntersection, nodeHit.x, nodeHit.z);
				return true;
			}
			if (worldPos.leftDouble() >= selectedIntersection.minX() && worldPos.leftDouble() <= selectedIntersection.maxX() && worldPos.rightDouble() >= selectedIntersection.minZ() && worldPos.rightDouble() <= selectedIntersection.maxZ()) {
				nodeClickConsumer.accept(selectedIntersection, (long) Math.floor(worldPos.leftDouble()), (long) Math.floor(worldPos.rightDouble()));
				return true;
			}
		}

		ClientTrafficIntersectionEntry bestIntersection = null;
		double bestIntersectionDistance = Double.POSITIVE_INFINITY;
		for (ClientTrafficIntersectionEntry intersection : intersectionsSupplier.get()) {
			final double candidateDistance = distanceToIntersection(intersection, worldPos.leftDouble(), worldPos.rightDouble());
			if (candidateDistance < bestIntersectionDistance) {
				bestIntersectionDistance = candidateDistance;
				bestIntersection = intersection;
			}
		}
		if (bestIntersection != null && bestIntersectionDistance <= SELECT_DISTANCE_BLOCKS) {
			selectIntersectionConsumer.accept(bestIntersection);
			return true;
		}

		ClientTrafficDashboardEntry bestEntry = null;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (ClientTrafficDashboardEntry entry : entriesSupplier.get()) {
			final double candidateDistance = distanceToEntry(entry, worldPos.leftDouble(), worldPos.rightDouble());
			if (candidateDistance < bestDistance) {
				bestDistance = candidateDistance;
				bestEntry = entry;
			}
		}

		if (bestEntry != null && bestDistance <= SELECT_DISTANCE_BLOCKS) {
			selectEntryConsumer.accept(bestEntry);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		if (width <= 0 || height <= 0) {
			return false;
		}

		final double oldScale = scale;
		if (oldScale > SCALE_LOWER_LIMIT && amount < 0) {
			centerX -= (mouseX - getX2() - width / 2D) / scale;
			centerZ -= (mouseY - getY2() - height / 2D) / scale;
		}
		scale(amount);
		if (oldScale < SCALE_UPPER_LIMIT && amount > 0) {
			centerX += (mouseX - getX2() - width / 2D) / scale;
			centerZ += (mouseY - getY2() - height / 2D) / scale;
		}
		return true;
	}

	@Override
	public boolean isMouseOver2(double mouseX, double mouseY) {
		if (width <= 0 || height <= 0) {
			return false;
		}

		return mouseX >= getX2() && mouseY >= getY2() && mouseX < getX2() + width && mouseY < getY2() + height;
	}

	public void setPositionAndSize(int x, int y, int width, int height) {
		setX2(x);
		setY2(y);
		this.width = width;
		this.height = height;
	}

	public void setCreatingIntersection(boolean creatingIntersection) {
		this.creatingIntersection = creatingIntersection;
	}

	public void setPendingIntersectionCorner(BlockPos pendingIntersectionCorner) {
		this.pendingIntersectionCorner = pendingIntersectionCorner;
	}

	public void scale(double amount) {
		scale *= Math.pow(2, amount);
		scale = Math.max(SCALE_LOWER_LIMIT, Math.min(SCALE_UPPER_LIMIT, scale));
	}

	public void focusOn(ClientTrafficDashboardEntry entry) {
		if (entry == null) {
			return;
		}
		centerX = entry.blockPos().getX();
		centerZ = entry.blockPos().getZ();
		scale = Math.max(8, scale);
	}

	public void focusOn(ClientTrafficIntersectionEntry intersection) {
		if (intersection == null) {
			return;
		}
		centerX = intersection.centerX();
		centerZ = intersection.centerZ();
		scale = Math.max(8, scale);
	}

	public void setMapOverlayMode(WorldMap.MapOverlayMode mapOverlayMode) {
		worldMap.setMapOverlayMode(mapOverlayMode);
		if (world != null) {
			worldMap.updateMap(World.cast(world), player);
		}
	}

	public boolean isMapOverlayMode(WorldMap.MapOverlayMode mapOverlayMode) {
		return worldMap.isMapOverlayMode(mapOverlayMode);
	}

	public void onClose() {
		worldMap.disposeImages();
	}

	private void drawEntryLabel(GraphicsHolder graphicsHolder, ClientTrafficDashboardEntry entry) {
		drawFromWorldCoords(entry.blockPos().getX() + 0.5D, entry.blockPos().getZ() + 0.5D, (x, z) -> {
			graphicsHolder.push();
			graphicsHolder.translate(getX2() + x.floatValue(), getY2() + z.floatValue(), 0);
			IDrawing.drawStringWithFont(graphicsHolder, shortLabel(entry), 0, -TEXT_HEIGHT, GraphicsHolder.getDefaultLight());
			graphicsHolder.pop();
		});
	}

	private void drawIntersectionLabel(GraphicsHolder graphicsHolder, ClientTrafficIntersectionEntry intersection) {
		drawFromWorldCoords(intersection.centerX() + 0.5D, intersection.centerZ() + 0.5D, (x, z) -> {
			graphicsHolder.push();
			graphicsHolder.translate(getX2() + x.floatValue(), getY2() + z.floatValue(), 0);
			IDrawing.drawStringWithFont(graphicsHolder, "I " + intersection.nodes().size(), 0, -TEXT_HEIGHT, GraphicsHolder.getDefaultLight());
			graphicsHolder.pop();
		});
	}

	private void drawIntersectionNodeLabels(GraphicsHolder graphicsHolder, ClientTrafficIntersectionEntry intersection) {
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node : intersection.nodes()) {
			drawFromWorldCoords(node.x() + 0.5D, node.z() + 0.5D, (x, z) -> {
				final String label = (node.type().name().equals("IN") ? "I " : "O ") + node.number();
				graphicsHolder.drawText(label, getX2() + x.intValue() + 6, getY2() + z.intValue() - 5, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			});
		}
	}

	private void drawIntersection(GuiDrawing guiDrawing, ClientTrafficIntersectionEntry intersection, boolean selected) {
		drawFromWorldCoords(intersection.minX(), intersection.minZ(), (x1, z1) -> drawFromWorldCoords(intersection.maxX(), intersection.maxZ(), (x2, z2) -> {
			guiDrawing.drawRectangle(getX2() + Math.min(x1, x2), getY2() + Math.min(z1, z2), getX2() + Math.max(x1, x2), getY2() + Math.max(z1, z2), selected ? 0x6600A676 : COLOR_INTERSECTION);
		}));
		final String selectedNodeKey = selected ? selectedNodeSupplier.get() : null;
		final List<Integer> selectedGroupNodeNumbers = selected ? selectedGroupNodeNumbersSupplier.get() : List.of();
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node : intersection.nodes()) {
			drawFromWorldCoords(node.x() + 0.5D, node.z() + 0.5D, (x, z) -> {
				final int color = node.type().name().equals("IN") ? COLOR_INTERSECTION_NODE_IN : COLOR_INTERSECTION_NODE_OUT;
				final boolean selectedNode = nodeKey(node).equals(selectedNodeKey);
				final boolean inSelectedGroup = node.type().name().equals("IN") && selectedGroupNodeNumbers.contains(node.number());
				if (selectedNode) {
					guiDrawing.drawRectangle(getX2() + x - 6, getY2() + z - 6, getX2() + x + 6, getY2() + z + 6, COLOR_SELECTED_NODE);
				} else if (inSelectedGroup) {
					guiDrawing.drawRectangle(getX2() + x - 5, getY2() + z - 5, getX2() + x + 5, getY2() + z + 5, COLOR_SELECTED_GROUP_NODE);
				}
				guiDrawing.drawRectangle(getX2() + x - 3, getY2() + z - 3, getX2() + x + 3, getY2() + z + 3, color);
			});
		}
	}

	private void drawPendingIntersection(GuiDrawing guiDrawing, double mouseWorldX, double mouseWorldZ) {
		if (pendingIntersectionCorner == null) {
			drawFromWorldCoords(mouseWorldX, mouseWorldZ, (x, z) -> {
				guiDrawing.drawRectangle(getX2() + x - 5, getY2() + z - 5, getX2() + x + 5, getY2() + z + 5, COLOR_INTERSECTION_PREVIEW);
			});
			return;
		}

		final double cornerX = pendingIntersectionCorner.getX();
		final double cornerZ = pendingIntersectionCorner.getZ();
		final double x1 = (cornerX - centerX) * scale + width / 2D;
		final double z1 = (cornerZ - centerZ) * scale + height / 2D;
		final double x2 = (mouseWorldX - centerX) * scale + width / 2D;
		final double z2 = (mouseWorldZ - centerZ) * scale + height / 2D;
		final double left = Math.max(0, Math.min(x1, x2));
		final double top = Math.max(0, Math.min(z1, z2));
		final double right = Math.min(width, Math.max(x1, x2));
		final double bottom = Math.min(height, Math.max(z1, z2));
		if (right > left && bottom > top) {
			guiDrawing.drawRectangle(getX2() + left, getY2() + top, getX2() + right, getY2() + bottom, COLOR_INTERSECTION_PREVIEW);
		}
		guiDrawing.drawRectangle(getX2() + x1 - 4, getY2() + z1 - 4, getX2() + x1 + 4, getY2() + z1 + 4, 0xFFFFD166);
	}

	private void drawMarker(GuiDrawing guiDrawing, BlockPos blockPos, int color, boolean selected) {
		drawFromWorldCoords(blockPos.getX() + 0.5D, blockPos.getZ() + 0.5D, (x, z) -> {
			final double markerRadius = selected ? 3.5D : 2.5D;
			guiDrawing.drawRectangle(getX2() + x - markerRadius, getY2() + z - markerRadius, getX2() + x + markerRadius, getY2() + z + markerRadius, selected ? COLOR_SELECTED : color);
		});
	}

	private void drawConnector(GuiDrawing guiDrawing, BlockPos start, BlockPos end, int color) {
		final int steps = Math.max(4, (int) Math.ceil(Math.hypot(end.getX() - start.getX(), end.getZ() - start.getZ()) * 1.5D));
		for (int i = 0; i <= steps; i++) {
			final double progress = i / (double) steps;
			final double worldX = lerp(start.getX() + 0.5D, end.getX() + 0.5D, progress);
			final double worldZ = lerp(start.getZ() + 0.5D, end.getZ() + 0.5D, progress);
			drawFromWorldCoords(worldX, worldZ, (x, z) -> guiDrawing.drawRectangle(getX2() + x - 1, getY2() + z - 1, getX2() + x + 1, getY2() + z + 1, color));
		}
	}

	private void drawPlayerSymbol(GuiDrawing guiDrawing, ClientPlayerEntity clientPlayer) {
		drawFromWorldCoords(clientPlayer.getX(), clientPlayer.getZ(), (x, z) -> {
			guiDrawing.drawRectangle(getX2() + Math.max(0, x - 2), getY2() + z - 3, getX2() + x + 2, getY2() + z + 3, ARGB_WHITE);
			guiDrawing.drawRectangle(getX2() + Math.max(0, x - 3), getY2() + z - 2, getX2() + x + 3, getY2() + z + 2, ARGB_WHITE);
			guiDrawing.drawRectangle(getX2() + Math.max(0, x - 2), getY2() + z - 2, getX2() + x + 2, getY2() + z + 2, COLOR_SPAWN);
		});
	}

	private int colorForEntry(ClientTrafficDashboardEntry entry) {
		if (!entry.enabled()) {
			return COLOR_DISABLED;
		}
		return entry.type().name().equals("SPAWN") ? COLOR_SPAWN : COLOR_DESPAWN;
	}

	private double distanceToEntry(ClientTrafficDashboardEntry entry, double worldX, double worldZ) {
		double bestDistance = Math.hypot(worldX - (entry.blockPos().getX() + 0.5D), worldZ - (entry.blockPos().getZ() + 0.5D));
		if (entry.hasConnectorRoute()) {
			bestDistance = Math.min(bestDistance, distanceToSegment(
				worldX,
				worldZ,
				entry.connectorStartPos().getX() + 0.5D,
				entry.connectorStartPos().getZ() + 0.5D,
				entry.connectorEndPos().getX() + 0.5D,
				entry.connectorEndPos().getZ() + 0.5D
			));
		}
		return bestDistance;
	}

	private double distanceToIntersection(ClientTrafficIntersectionEntry intersection, double worldX, double worldZ) {
		if (worldX >= intersection.minX() && worldX <= intersection.maxX() && worldZ >= intersection.minZ() && worldZ <= intersection.maxZ()) {
			return 0.0D;
		}
		final double dx = Math.max(Math.max(intersection.minX() - worldX, 0), worldX - intersection.maxX());
		final double dz = Math.max(Math.max(intersection.minZ() - worldZ, 0), worldZ - intersection.maxZ());
		return Math.hypot(dx, dz);
	}

	private TrafficNodeHit nearestIntersectionNode(ClientTrafficIntersectionEntry intersection, double worldX, double worldZ) {
		TrafficNodeHit bestHit = null;
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node : intersection.nodes()) {
			final double distance = Math.hypot(worldX - (node.x() + 0.5D), worldZ - (node.z() + 0.5D));
			if (bestHit == null || distance < bestHit.distance) {
				bestHit = new TrafficNodeHit(node.x(), node.z(), distance);
			}
		}
		return bestHit;
	}

	private static double distanceToSegment(double px, double pz, double x1, double z1, double x2, double z2) {
		final double dx = x2 - x1;
		final double dz = z2 - z1;
		if (dx == 0 && dz == 0) {
			return Math.hypot(px - x1, pz - z1);
		}
		final double t = Math.max(0, Math.min(1, ((px - x1) * dx + (pz - z1) * dz) / (dx * dx + dz * dz)));
		final double projectedX = x1 + t * dx;
		final double projectedZ = z1 + t * dz;
		return Math.hypot(px - projectedX, pz - projectedZ);
	}

	private DoubleDoubleImmutablePair coordsToWorldPos(double mouseX, double mouseY) {
		final double worldX = (mouseX - width / 2D) / scale + centerX;
		final double worldZ = (mouseY - height / 2D) / scale + centerZ;
		return new DoubleDoubleImmutablePair(worldX, worldZ);
	}

	private void drawFromWorldCoords(double worldX, double worldZ, java.util.function.BiConsumer<Double, Double> callback) {
		final double screenX = (worldX - centerX) * scale + width / 2D;
		final double screenZ = (worldZ - centerZ) * scale + height / 2D;
		if (Utilities.isBetween(screenX, 0, width) && Utilities.isBetween(screenZ, 0, height)) {
			callback.accept(screenX, screenZ);
		}
	}

	private void drawTextureFromWorldCoords(GuiDrawing guiDrawing, org.mtr.mapping.holder.Identifier texture, double posX1, double posZ1, double posX2, double posZ2) {
		guiDrawing.beginDrawingTexture(texture);
		final double x1 = (posX1 - centerX) * scale + width / 2D;
		final double z1 = (posZ1 - centerZ) * scale + height / 2D;
		final double x2 = (posX2 - centerX) * scale + width / 2D;
		final double z2 = (posZ2 - centerZ) * scale + height / 2D;
		final float uScale = x1 >= 0 ? 0 : 1F - (float) ((x2 - 0) / (x2 - x1));
		guiDrawing.drawTexture(getX2() + Math.max(0, x1), getY2() + z1, getX2() + x2, getY2() + z2, uScale, 0, 1, 1);
		guiDrawing.finishDrawingTexture();
	}

	private static String shortLabel(ClientTrafficDashboardEntry entry) {
		return (entry.type().name().equals("SPAWN") ? "S" : "D") + " " + entry.group();
	}

	private static String nodeKey(com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node) {
		return node.x() + "," + node.y() + "," + node.z();
	}

	private static double lerp(double start, double end, double progress) {
		return start + (end - start) * progress;
	}

	private record TrafficNodeHit(long x, long z, double distance) {
	}

	@FunctionalInterface
	public interface TriConsumer<A, B, C> {
		void accept(A first, B second, C third);
	}
}
