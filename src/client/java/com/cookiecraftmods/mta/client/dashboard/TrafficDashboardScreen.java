package com.cookiecraftmods.mta.client.dashboard;

import com.cookiecraftmods.mta.traffic.dashboard.network.TrafficDashboardNetworking;
import com.cookiecraftmods.mta.client.render.ClientMtrVehicleResourceRegistry;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionGroup;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.mtr.core.data.TransportMode;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.WorldMap;
import org.mtr.mod.resource.VehicleResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TrafficDashboardScreen extends ScreenExtension implements IGui {
	private static final int LIST_ENTRIES_PER_PAGE = 7;
	private static final int GROUP_LIST_ROWS = 5;
	private static final int SELECTED_VEHICLE_ROWS = 14;
	private static final int AVAILABLE_VEHICLE_ENTRIES_PER_PAGE = 14;
	private static final int LEFT_PANEL_WIDTH = PANEL_WIDTH + 260;
	private static final int TITLE_Y = 4;
	private static final int LIST_START_Y = 58;
	private static final int LIST_ROW_HEIGHT = 20;
	private static final int LIST_SECTION_HEIGHT = LIST_ENTRIES_PER_PAGE * LIST_ROW_HEIGHT + 28;
	private static final int PANEL_SECTION_GAP = 12;
	private static final int DETAILS_START_Y = LIST_START_Y + LIST_SECTION_HEIGHT + PANEL_SECTION_GAP;
	private static final int INTERSECTION_LIST_WIDTH = 248;
	private static final int OVERVIEW_CONTROLS_START_Y = DETAILS_START_Y + 112;
	private static final int VEHICLE_POOL_CONTROLS_START_Y = DETAILS_START_Y + 64;
	private static final int ARGB_MUTED_BLUE = 0xFF9CC7FF;
	private static final int ARGB_WARNING = 0xFFFFD166;
	private static final int ARGB_OK = 0xFF7EE787;

	private final List<ClientTrafficDashboardEntry> entries = new ArrayList<>();
	private final List<ClientTrafficIntersectionEntry> intersections = new ArrayList<>();
	private final List<ClientTrafficIntersectionEntry> filteredIntersections = new ArrayList<>();
	private final List<VehicleOption> vehicleOptions = new ArrayList<>();
	private final List<VehicleOption> filteredVehicleOptions = new ArrayList<>();
	private final List<ButtonWidgetExtension> entryButtons = new ArrayList<>();
	private final List<ButtonWidgetExtension> intersectionGroupButtons = new ArrayList<>();
	private final List<ButtonWidgetExtension> intersectionGroupDeleteButtons = new ArrayList<>();
	private final List<ButtonWidgetExtension> selectedVehicleButtons = new ArrayList<>();
	private final List<ButtonWidgetExtension> vehicleButtons = new ArrayList<>();
	private final TrafficWidgetMap widgetMap;
	private PanelMode panelMode = PanelMode.OVERVIEW;
	private DashboardSection dashboardSection = DashboardSection.CONNECTORS;

	private int selectedIndex;
	private int selectedIntersectionIndex;
	private int entryPage;
	private int vehiclePage;
	private int selectedVehiclePage;
	private String vehicleSearchQuery = "";
	private String intersectionSearchQuery = "";
	private BlockPos pendingIntersectionCorner;
	private boolean drawingIntersection;
	private String selectedIntersectionNode;
	private boolean updatingIntersectionNameField;

	private final TextFieldWidgetExtension vehicleSearchField;
	private final TextFieldWidgetExtension intersectionSearchField;
	private final TextFieldWidgetExtension intersectionNameField;
	private final ButtonWidgetExtension buttonEntryPageUp;
	private final ButtonWidgetExtension buttonEntryPageDown;
	private final ButtonWidgetExtension buttonVehiclePageUp;
	private final ButtonWidgetExtension buttonVehiclePageDown;
	private final ButtonWidgetExtension buttonSelectedVehiclePageUp;
	private final ButtonWidgetExtension buttonSelectedVehiclePageDown;
	private final ButtonWidgetExtension buttonOpenVehiclePool;
	private final ButtonWidgetExtension buttonBackToOverview;
	private final ButtonWidgetExtension buttonRefresh;
	private final ButtonWidgetExtension buttonClearVehicles;
	private final ButtonWidgetExtension buttonToggleEnabled;
	private final ButtonWidgetExtension buttonGroupMinus;
	private final ButtonWidgetExtension buttonGroupPlus;
	private final ButtonWidgetExtension buttonMaxVehiclesMinus;
	private final ButtonWidgetExtension buttonMaxVehiclesPlus;
	private final ButtonWidgetExtension buttonSpawnIntervalMinus;
	private final ButtonWidgetExtension buttonSpawnIntervalPlus;
	private final ButtonWidgetExtension buttonTargetGroupMinus;
	private final ButtonWidgetExtension buttonTargetGroupPlus;
	private final ButtonWidgetExtension buttonFocus;
	private final ButtonWidgetExtension buttonZoomIn;
	private final ButtonWidgetExtension buttonZoomOut;
	private final ButtonWidgetExtension buttonMapTopView;
	private final ButtonWidgetExtension buttonMapCurrentY;
	private final ButtonWidgetExtension buttonSectionConnectors;
	private final ButtonWidgetExtension buttonSectionIntersections;
	private final ButtonWidgetExtension buttonAddIntersection;
	private final ButtonWidgetExtension buttonDeleteIntersection;
	private final ButtonWidgetExtension buttonAutoDetectIntersection;
	private final ButtonWidgetExtension buttonIntersectionGroupAdd;
	private final ButtonWidgetExtension buttonIntersectionGroupPrevious;
	private final ButtonWidgetExtension buttonIntersectionGroupNext;
	private final ButtonWidgetExtension buttonToggleIntersectionNodeType;
	private final ButtonWidgetExtension buttonIntersectionNodeMinus;
	private final ButtonWidgetExtension buttonIntersectionNodePlus;
	private final ButtonWidgetExtension buttonIntersectionPhaseMinus;
	private final ButtonWidgetExtension buttonIntersectionPhasePlus;
	private final ButtonWidgetExtension buttonIntersectionPhaseAdd;
	private final ButtonWidgetExtension buttonIntersectionPhaseRemove;
	private final ButtonWidgetExtension buttonIntersectionPhaseUp;
	private final ButtonWidgetExtension buttonIntersectionPhaseDown;
	private int selectedPhaseIndex = -1;

	public TrafficDashboardScreen(List<ClientTrafficDashboardEntry> entries, List<ClientTrafficIntersectionEntry> intersections) {
		super(TextHelper.literal("Traffic Dashboard"));
		widgetMap = new TrafficWidgetMap(() -> this.entries, () -> this.intersections, this::selectedEntry, this::selectedIntersection, this::selectedIntersectionNode, this::selectedGroupNodeNumbers, this::selectEntry, this::selectIntersection, this::handleIntersectionCornerClick, this::handleIntersectionNodeClick);
		vehicleSearchField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal("Search vehicles"), 128, TextCase.DEFAULT, "", "Search vehicles");
		vehicleSearchField.setChangedListener2(value -> {
			vehicleSearchQuery = value == null ? "" : value.trim();
			vehiclePage = 0;
			refreshFilteredVehicleOptions();
			refreshButtons();
		});
		intersectionSearchField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal("Search intersections"), 96, TextCase.DEFAULT, "", "Search intersections");
		intersectionSearchField.setChangedListener2(value -> {
			intersectionSearchQuery = value == null ? "" : value.trim();
			entryPage = 0;
			refreshFilteredIntersections();
			refreshButtons();
		});
		intersectionNameField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal("Intersection name"), 64, TextCase.DEFAULT, "", "Intersection name");
		intersectionNameField.setChangedListener2(value -> {
			if (!updatingIntersectionNameField && dashboardSection == DashboardSection.INTERSECTIONS && selectedIntersection() != null) {
				sendIntersectionUpdate("name", 0, value == null ? "" : value);
			}
		});

		buttonEntryPageUp = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Prev"), button -> {
			entryPage = Math.max(0, entryPage - 1);
			refreshButtons();
		});
		buttonEntryPageDown = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Next"), button -> {
			entryPage = Math.min(maxEntryPage(), entryPage + 1);
			refreshButtons();
		});
		buttonVehiclePageUp = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Prev"), button -> {
			vehiclePage = Math.max(0, vehiclePage - 1);
			refreshButtons();
		});
		buttonVehiclePageDown = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Next"), button -> {
			vehiclePage = Math.min(maxVehiclePage(), vehiclePage + 1);
			refreshButtons();
		});
		buttonSelectedVehiclePageUp = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Prev"), button -> {
			selectedVehiclePage = Math.max(0, selectedVehiclePage - 1);
			refreshButtons();
		});
		buttonSelectedVehiclePageDown = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Next"), button -> {
			selectedVehiclePage = Math.min(maxSelectedVehiclePage(), selectedVehiclePage + 1);
			refreshButtons();
		});
		buttonOpenVehiclePool = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Vehicle Pool"), button -> openVehiclePool());
		buttonBackToOverview = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Back"), button -> {
			panelMode = PanelMode.OVERVIEW;
			layoutWidgets();
			refreshButtons();
		});
		buttonRefresh = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Refresh Data"), button -> sendRefresh());
		buttonClearVehicles = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Clear Vehicles"), button -> sendClearVehicles());
		buttonToggleEnabled = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Enable/Disable"), button -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) {
				sendIntersectionUpdate("enabled", 0, null);
			} else {
				sendUpdate("enabled", 0, null);
			}
		});
		buttonGroupMinus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Group -"), button -> sendUpdate("group", -1, null));
		buttonGroupPlus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Group +"), button -> sendUpdate("group", 1, null));
		buttonMaxVehiclesMinus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Node -"), button -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) {
				sendIntersectionUpdate("node_number", -1, selectedIntersectionNode);
			} else {
				sendUpdate("max_vehicles", -1, null);
			}
		});
		buttonMaxVehiclesPlus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Node +"), button -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) {
				sendIntersectionUpdate("node_number", 1, selectedIntersectionNode);
			} else {
				sendUpdate("max_vehicles", 1, null);
			}
		});
		buttonSpawnIntervalMinus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Phase -"), button -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) {
				sendIntersectionUpdate("phase_duration", -20, null);
			} else {
				sendUpdate("spawn_interval", -20, null);
			}
		});
		buttonSpawnIntervalPlus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Phase +"), button -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) {
				sendIntersectionUpdate("phase_duration", 20, null);
			} else {
				sendUpdate("spawn_interval", 20, null);
			}
		});
		buttonTargetGroupMinus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Target -"), button -> sendUpdate("target_group", -1, null));
		buttonTargetGroupPlus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Target +"), button -> sendUpdate("target_group", 1, null));
		buttonFocus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Focus"), button -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS && selectedIntersection() != null) {
				widgetMap.focusOn(selectedIntersection());
			} else if (selectedEntry() != null) {
				widgetMap.focusOn(selectedEntry());
			}
		});
		buttonZoomIn = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("+"), button -> widgetMap.scale(1));
		buttonZoomOut = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("-"), button -> widgetMap.scale(-1));
		buttonMapTopView = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Top"), button -> {
			widgetMap.setMapOverlayMode(WorldMap.MapOverlayMode.TOP_VIEW);
			refreshButtons();
		});
		buttonMapCurrentY = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Y"), button -> {
			widgetMap.setMapOverlayMode(WorldMap.MapOverlayMode.CURRENT_Y);
			refreshButtons();
		});
		buttonSectionConnectors = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Connectors"), button -> {
			dashboardSection = DashboardSection.CONNECTORS;
			panelMode = PanelMode.OVERVIEW;
			pendingIntersectionCorner = null;
			drawingIntersection = false;
			selectedIntersectionNode = null;
			widgetMap.setCreatingIntersection(false);
			widgetMap.setPendingIntersectionCorner(null);
			syncIntersectionNameField();
			layoutWidgets();
			refreshButtons();
		});
		buttonSectionIntersections = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Intersections"), button -> {
			dashboardSection = DashboardSection.INTERSECTIONS;
			panelMode = PanelMode.OVERVIEW;
			syncIntersectionNameField();
			layoutWidgets();
			refreshButtons();
		});
		buttonAddIntersection = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Area"), button -> {
			dashboardSection = DashboardSection.INTERSECTIONS;
			panelMode = PanelMode.OVERVIEW;
			drawingIntersection = !drawingIntersection;
			pendingIntersectionCorner = null;
			selectedIntersectionNode = null;
			widgetMap.setCreatingIntersection(drawingIntersection);
			widgetMap.setPendingIntersectionCorner(null);
			layoutWidgets();
			refreshButtons();
		});
		buttonDeleteIntersection = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Del"), button -> sendIntersectionUpdate("delete", 0, null));
		buttonAutoDetectIntersection = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Auto"), button -> sendIntersectionUpdate("find_nodes", 0, null));
		buttonIntersectionGroupAdd = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("+ Group"), button -> {
			final ClientTrafficIntersectionEntry intersection = selectedIntersection();
			selectedPhaseIndex = intersection == null ? 0 : effectiveGroups(intersection).size();
			sendIntersectionUpdate("group_add", 0, null);
		});
		buttonIntersectionGroupPrevious = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("< Group"), button -> {
			selectedPhaseIndex = Math.max(0, selectedPhaseIndex - 1);
			selectedIntersectionNode = null;
			refreshButtons();
		});
		buttonIntersectionGroupNext = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Group >"), button -> {
			final ClientTrafficIntersectionEntry intersection = selectedIntersection();
			if (intersection != null) {
				selectedPhaseIndex = Math.min(Math.max(0, effectiveGroups(intersection).size() - 1), selectedPhaseIndex + 1);
				selectedIntersectionNode = null;
			}
			refreshButtons();
		});
		buttonToggleIntersectionNodeType = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("IN/OUT"), button -> sendIntersectionUpdate("node_type", 0, selectedIntersectionNode));
		buttonIntersectionNodeMinus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("# -"), button -> sendIntersectionUpdate("node_number", -1, selectedIntersectionNode));
		buttonIntersectionNodePlus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("# +"), button -> sendIntersectionUpdate("node_number", 1, selectedIntersectionNode));
		buttonIntersectionPhaseMinus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("-1s"), button -> sendIntersectionUpdate("phase_duration", -20, String.valueOf(selectedPhaseIndex)));
		buttonIntersectionPhasePlus = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("+1s"), button -> sendIntersectionUpdate("phase_duration", 20, String.valueOf(selectedPhaseIndex)));
		buttonIntersectionPhaseAdd = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("+ Node"), button -> {
			final Integer nodeNumber = selectedNodeNumber();
			if (nodeNumber != null) {
				sendIntersectionUpdate("phase_assign", nodeNumber, String.valueOf(selectedPhaseIndex));
			}
		});
		buttonIntersectionPhaseRemove = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("- Node"), button -> {
			final Integer nodeNumber = selectedNodeNumber();
			sendIntersectionUpdate("phase_remove", nodeNumber == null ? 0 : nodeNumber, String.valueOf(selectedPhaseIndex));
		});
		buttonIntersectionPhaseUp = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Up"), button -> sendIntersectionUpdate("phase_move", -1, String.valueOf(selectedPhaseIndex)));
		buttonIntersectionPhaseDown = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal("Down"), button -> sendIntersectionUpdate("phase_move", 1, String.valueOf(selectedPhaseIndex)));

		for (int i = 0; i < GROUP_LIST_ROWS; i++) {
			final int index = i;
			intersectionGroupButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), button -> {
				final ClientTrafficIntersectionEntry intersection = selectedIntersection();
				if (intersection == null) {
					return;
				}
				if (index < effectiveGroups(intersection).size()) {
					selectedPhaseIndex = index;
					refreshButtons();
				}
			}));
			intersectionGroupDeleteButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal("x"), button -> {
				final ClientTrafficIntersectionEntry intersection = selectedIntersection();
				if (intersection == null || index >= effectiveGroups(intersection).size()) {
					return;
				}
				selectedPhaseIndex = index;
				sendIntersectionUpdate("phase_remove", 0, String.valueOf(index));
			}));
		}

		for (int i = 0; i < LIST_ENTRIES_PER_PAGE; i++) {
			final int index = i;
			entryButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), button -> {
				final int entryIndex = entryPage * LIST_ENTRIES_PER_PAGE + index;
				if (dashboardSection == DashboardSection.INTERSECTIONS) {
					if (entryIndex < this.filteredIntersections.size()) {
						final ClientTrafficIntersectionEntry intersection = this.filteredIntersections.get(entryIndex);
						selectedIntersectionIndex = this.intersections.indexOf(intersection);
						selectedIntersectionNode = null;
						pendingIntersectionCorner = null;
						drawingIntersection = false;
						selectedPhaseIndex = 0;
						widgetMap.setCreatingIntersection(false);
						widgetMap.setPendingIntersectionCorner(null);
						widgetMap.focusOn(intersection);
						syncIntersectionNameField();
						layoutWidgets();
						refreshButtons();
					}
				} else if (entryIndex < this.entries.size()) {
					selectedIndex = entryIndex;
					widgetMap.focusOn(this.entries.get(entryIndex));
					if (this.entries.get(entryIndex).type().name().equals("SPAWN")) {
						openVehiclePool();
					} else {
						panelMode = PanelMode.OVERVIEW;
					}
					refreshButtons();
				}
			}));
		}

		for (int i = 0; i < SELECTED_VEHICLE_ROWS; i++) {
			final int index = i;
			selectedVehicleButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), button -> {
				final ClientTrafficDashboardEntry entry = selectedEntry();
				if (entry == null || !entry.type().name().equals("SPAWN")) {
					return;
				}

				final List<String> selectedPool = entry.effectiveVehiclePool();
				final int vehicleIndex = selectedVehiclePage * SELECTED_VEHICLE_ROWS + index;
				if (vehicleIndex < selectedPool.size()) {
					sendUpdate("vehicle_pool_toggle", 0, selectedPool.get(vehicleIndex));
				}
			}));
		}

		for (int i = 0; i < AVAILABLE_VEHICLE_ENTRIES_PER_PAGE; i++) {
			final int index = i;
			vehicleButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), button -> {
				final int vehicleIndex = vehiclePage * AVAILABLE_VEHICLE_ENTRIES_PER_PAGE + index;
				if (vehicleIndex < filteredVehicleOptions.size() && selectedEntry() != null && selectedEntry().type().name().equals("SPAWN")) {
					sendUpdate("vehicle_pool_toggle", 0, filteredVehicleOptions.get(vehicleIndex).id());
				}
			}));
		}

		reloadVehicleOptions();
		updateEntries(entries, intersections);
	}

	public void updateEntries(List<ClientTrafficDashboardEntry> updatedEntries, List<ClientTrafficIntersectionEntry> updatedIntersections) {
		entries.clear();
		entries.addAll(updatedEntries);
		intersections.clear();
		intersections.addAll(updatedIntersections);
		refreshFilteredIntersections();
		if (!entries.isEmpty() && (selectedIndex < 0 || selectedIndex >= entries.size())) {
			selectedIndex = 0;
		}
		selectedIndex = Math.min(selectedIndex, Math.max(entries.size() - 1, 0));
		selectedIntersectionIndex = Math.min(selectedIntersectionIndex, Math.max(intersections.size() - 1, 0));
		final ClientTrafficIntersectionEntry selectedIntersection = selectedIntersection();
		if (selectedIntersection != null) {
			final int groupCount = effectiveGroups(selectedIntersection).size();
			if (groupCount > 0 && (selectedPhaseIndex < 0 || selectedPhaseIndex >= groupCount)) {
				selectedPhaseIndex = 0;
			}
		}
		entryPage = Math.min(entryPage, maxEntryPage());
		vehiclePage = Math.min(vehiclePage, maxVehiclePage());
		selectedVehiclePage = Math.min(selectedVehiclePage, maxSelectedVehiclePage());
		refreshFilteredVehicleOptions();
		syncIntersectionNameField();
		refreshButtons();
	}

	@Override
	protected void init2() {
		super.init2();

		final boolean vehiclePoolMode = panelMode == PanelMode.VEHICLE_POOL;
		widgetMap.setPositionAndSize(vehiclePoolMode ? width : LEFT_PANEL_WIDTH, 0, vehiclePoolMode ? 0 : width - LEFT_PANEL_WIDTH, height);

		intersectionSearchField.setX2(8);
		intersectionSearchField.setY2(LIST_START_Y);
		intersectionSearchField.setWidth2(LEFT_PANEL_WIDTH - 16);
		final int listStartY = dashboardSection == DashboardSection.INTERSECTIONS ? LIST_START_Y + 24 : LIST_START_Y;
		int y = listStartY;
		for (ButtonWidgetExtension entryButton : entryButtons) {
			IDrawing.setPositionAndWidth(entryButton, 8, y, LEFT_PANEL_WIDTH - 16);
			y += LIST_ROW_HEIGHT;
		}

		IDrawing.setPositionAndWidth(buttonEntryPageUp, 8, y, (LEFT_PANEL_WIDTH - 20) / 2);
		IDrawing.setPositionAndWidth(buttonEntryPageDown, 12 + (LEFT_PANEL_WIDTH - 20) / 2, y, (LEFT_PANEL_WIDTH - 20) / 2);
		y = OVERVIEW_CONTROLS_START_Y;

		IDrawing.setPositionAndWidth(buttonToggleEnabled, 8, y, 92);
		IDrawing.setPositionAndWidth(buttonFocus, 108, y, 92);
		IDrawing.setPositionAndWidth(buttonOpenVehiclePool, 208, y, LEFT_PANEL_WIDTH - 216);
		y += 24;

		IDrawing.setPositionAndWidth(buttonRefresh, 8, y, 192);
		IDrawing.setPositionAndWidth(buttonClearVehicles, 208, y, LEFT_PANEL_WIDTH - 216);
		y += 24;

		IDrawing.setPositionAndWidth(buttonMaxVehiclesMinus, 8, y, 92);
		IDrawing.setPositionAndWidth(buttonMaxVehiclesPlus, 108, y, 92);
		IDrawing.setPositionAndWidth(buttonSpawnIntervalMinus, 208, y, 92);
		IDrawing.setPositionAndWidth(buttonSpawnIntervalPlus, 308, y, LEFT_PANEL_WIDTH - 316);
		y += 24;

		IDrawing.setPositionAndWidth(buttonTargetGroupMinus, 8, y, 92);
		IDrawing.setPositionAndWidth(buttonTargetGroupPlus, 108, y, 92);

		final int poolMargin = 16;
		final int poolTop = 50;
		final int poolGap = 16;
		final int poolColumnWidth = Math.max(180, (width - poolMargin * 2 - poolGap) / 2);
		final int rightColumnX = poolMargin + poolColumnWidth + poolGap;
		IDrawing.setPositionAndWidth(buttonBackToOverview, poolMargin, 20, 88);
		IDrawing.setPositionAndWidth(buttonVehiclePageUp, Math.max(poolMargin, rightColumnX + poolColumnWidth - 180), 20, 84);
		IDrawing.setPositionAndWidth(buttonVehiclePageDown, Math.max(poolMargin, rightColumnX + poolColumnWidth - 88), 20, 88);
		vehicleSearchField.setX2(poolMargin);
		vehicleSearchField.setY2(poolTop + 18);
		vehicleSearchField.setWidth(poolColumnWidth);

		y = poolTop + 48;
		for (ButtonWidgetExtension vehicleButton : vehicleButtons) {
			IDrawing.setPositionAndWidth(vehicleButton, poolMargin, y, poolColumnWidth);
			y += 20;
		}

		y = poolTop + 48;
		for (ButtonWidgetExtension selectedVehicleButton : selectedVehicleButtons) {
			IDrawing.setPositionAndWidth(selectedVehicleButton, rightColumnX, y, poolColumnWidth);
			y += 20;
		}

		final int bottomRowY = height - SQUARE_SIZE;
		IDrawing.setPositionAndWidth(buttonZoomIn, width - SQUARE_SIZE * 2, bottomRowY - SQUARE_SIZE, SQUARE_SIZE);
		IDrawing.setPositionAndWidth(buttonZoomOut, width - SQUARE_SIZE, bottomRowY - SQUARE_SIZE, SQUARE_SIZE);
		IDrawing.setPositionAndWidth(buttonMapCurrentY, width - SQUARE_SIZE * 2, bottomRowY, SQUARE_SIZE);
		IDrawing.setPositionAndWidth(buttonMapTopView, width - SQUARE_SIZE, bottomRowY, SQUARE_SIZE);

		layoutWidgets();

		addChild(new ClickableWidget(widgetMap));
		addChild(new ClickableWidget(buttonEntryPageUp));
		addChild(new ClickableWidget(buttonEntryPageDown));
		addChild(new ClickableWidget(buttonVehiclePageUp));
		addChild(new ClickableWidget(buttonVehiclePageDown));
		addChild(new ClickableWidget(buttonSelectedVehiclePageUp));
		addChild(new ClickableWidget(buttonSelectedVehiclePageDown));
		addChild(new ClickableWidget(buttonOpenVehiclePool));
		addChild(new ClickableWidget(buttonBackToOverview));
		addChild(new ClickableWidget(buttonRefresh));
		addChild(new ClickableWidget(buttonClearVehicles));
		addChild(new ClickableWidget(buttonToggleEnabled));
		addChild(new ClickableWidget(buttonGroupMinus));
		addChild(new ClickableWidget(buttonGroupPlus));
		addChild(new ClickableWidget(buttonMaxVehiclesMinus));
		addChild(new ClickableWidget(buttonMaxVehiclesPlus));
		addChild(new ClickableWidget(buttonSpawnIntervalMinus));
		addChild(new ClickableWidget(buttonSpawnIntervalPlus));
		addChild(new ClickableWidget(buttonTargetGroupMinus));
		addChild(new ClickableWidget(buttonTargetGroupPlus));
		addChild(new ClickableWidget(buttonFocus));
		addChild(new ClickableWidget(buttonZoomIn));
		addChild(new ClickableWidget(buttonZoomOut));
		addChild(new ClickableWidget(buttonMapTopView));
		addChild(new ClickableWidget(buttonMapCurrentY));
		addChild(new ClickableWidget(buttonSectionConnectors));
		addChild(new ClickableWidget(buttonSectionIntersections));
		addChild(new ClickableWidget(buttonAddIntersection));
		addChild(new ClickableWidget(buttonDeleteIntersection));
		addChild(new ClickableWidget(buttonAutoDetectIntersection));
		addChild(new ClickableWidget(buttonIntersectionGroupAdd));
		addChild(new ClickableWidget(buttonIntersectionGroupPrevious));
		addChild(new ClickableWidget(buttonIntersectionGroupNext));
		addChild(new ClickableWidget(buttonToggleIntersectionNodeType));
		addChild(new ClickableWidget(buttonIntersectionNodeMinus));
		addChild(new ClickableWidget(buttonIntersectionNodePlus));
		addChild(new ClickableWidget(buttonIntersectionPhaseMinus));
		addChild(new ClickableWidget(buttonIntersectionPhasePlus));
		addChild(new ClickableWidget(buttonIntersectionPhaseAdd));
		addChild(new ClickableWidget(buttonIntersectionPhaseRemove));
		addChild(new ClickableWidget(buttonIntersectionPhaseUp));
		addChild(new ClickableWidget(buttonIntersectionPhaseDown));
		addChild(new ClickableWidget(vehicleSearchField));
		addChild(new ClickableWidget(intersectionSearchField));
		addChild(new ClickableWidget(intersectionNameField));
		entryButtons.forEach(button -> addChild(new ClickableWidget(button)));
		intersectionGroupButtons.forEach(button -> addChild(new ClickableWidget(button)));
		intersectionGroupDeleteButtons.forEach(button -> addChild(new ClickableWidget(button)));
		selectedVehicleButtons.forEach(button -> addChild(new ClickableWidget(button)));
		vehicleButtons.forEach(button -> addChild(new ClickableWidget(button)));

		refreshButtons();
	}

	private void layoutWidgets() {
		final boolean vehiclePoolMode = panelMode == PanelMode.VEHICLE_POOL;
		if (vehiclePoolMode) {
			layoutVehiclePoolWidgets();
		} else {
			layoutOverviewWidgets();
		}
	}

	private void layoutOverviewWidgets() {
		widgetMap.setPositionAndSize(LEFT_PANEL_WIDTH, 0, Math.max(0, width - LEFT_PANEL_WIDTH), height);
		final int margin = 8;
		final int gap = 8;
		final int contentWidth = LEFT_PANEL_WIDTH - margin * 2;
		final int sectionWidth = dashboardSection == DashboardSection.INTERSECTIONS ? (contentWidth - gap * 2) / 3 : (contentWidth - gap) / 2;
		IDrawing.setPositionAndWidth(buttonSectionConnectors, margin, 30, sectionWidth);
		IDrawing.setPositionAndWidth(buttonSectionIntersections, margin + sectionWidth + gap, 30, sectionWidth);
		if (dashboardSection == DashboardSection.INTERSECTIONS) {
			IDrawing.setPositionAndWidth(buttonAddIntersection, margin + (sectionWidth + gap) * 2, 30, contentWidth - (sectionWidth + gap) * 2);
		}

		intersectionSearchField.setX2(margin);
		intersectionSearchField.setY2(LIST_START_Y);
		intersectionSearchField.setWidth2(dashboardSection == DashboardSection.INTERSECTIONS ? intersectionListWidth(contentWidth) : contentWidth);
		int y = dashboardSection == DashboardSection.INTERSECTIONS ? LIST_START_Y + 24 : LIST_START_Y;
		for (ButtonWidgetExtension entryButton : entryButtons) {
			IDrawing.setPositionAndWidth(entryButton, margin, y, dashboardSection == DashboardSection.INTERSECTIONS ? intersectionListWidth(contentWidth) : contentWidth);
			y += LIST_ROW_HEIGHT;
		}

		if (dashboardSection == DashboardSection.INTERSECTIONS) {
			final int listWidth = intersectionListWidth(contentWidth);
			IDrawing.setPositionAndWidth(buttonEntryPageUp, margin, y, (listWidth - gap) / 2);
			IDrawing.setPositionAndWidth(buttonEntryPageDown, margin + (listWidth + gap) / 2, y, (listWidth - gap) / 2);
			intersectionNameField.setX2(intersectionDetailX(contentWidth));
			intersectionNameField.setY2(LIST_START_Y + 44);
			intersectionNameField.setWidth2(intersectionDetailWidth(contentWidth));
			layoutIntersectionGroupWidgets(intersectionDetailX(contentWidth), LIST_START_Y + 180, intersectionDetailWidth(contentWidth));
			layoutIntersectionWidgets(intersectionDetailX(contentWidth), LIST_START_Y + 294, intersectionDetailWidth(contentWidth));
		} else {
			IDrawing.setPositionAndWidth(buttonEntryPageUp, margin, y, (contentWidth - gap) / 2);
			IDrawing.setPositionAndWidth(buttonEntryPageDown, margin + (contentWidth + gap) / 2, y, (contentWidth - gap) / 2);
			layoutConnectorWidgets(connectorControlsStartY());
		}

		final int bottomRowY = height - SQUARE_SIZE;
		IDrawing.setPositionAndWidth(buttonZoomIn, width - SQUARE_SIZE * 2, bottomRowY - SQUARE_SIZE, SQUARE_SIZE);
		IDrawing.setPositionAndWidth(buttonZoomOut, width - SQUARE_SIZE, bottomRowY - SQUARE_SIZE, SQUARE_SIZE);
		IDrawing.setPositionAndWidth(buttonMapCurrentY, width - SQUARE_SIZE * 2, bottomRowY, SQUARE_SIZE);
		IDrawing.setPositionAndWidth(buttonMapTopView, width - SQUARE_SIZE, bottomRowY, SQUARE_SIZE);
	}

	private void layoutConnectorWidgets(int y) {
		final int x = 8;
		final int gap = 8;
		final int width = LEFT_PANEL_WIDTH - 16;
		final int half = (width - gap) / 2;
		final int third = (width - gap * 2) / 3;
		IDrawing.setPositionAndWidth(buttonToggleEnabled, x, y, third);
		IDrawing.setPositionAndWidth(buttonFocus, x + third + gap, y, third);
		IDrawing.setPositionAndWidth(buttonOpenVehiclePool, x + (third + gap) * 2, y, width - (third + gap) * 2);
		y += 24;
		IDrawing.setPositionAndWidth(buttonRefresh, x, y, half);
		IDrawing.setPositionAndWidth(buttonClearVehicles, x + half + gap, y, width - half - gap);
		y += 24;
		IDrawing.setPositionAndWidth(buttonMaxVehiclesMinus, x, y, half);
		IDrawing.setPositionAndWidth(buttonMaxVehiclesPlus, x + half + gap, y, width - half - gap);
		y += 24;
		IDrawing.setPositionAndWidth(buttonSpawnIntervalMinus, x, y, half);
		IDrawing.setPositionAndWidth(buttonSpawnIntervalPlus, x + half + gap, y, width - half - gap);
	}

	private void layoutIntersectionGroupWidgets(int x, int y, int width) {
		final int deleteWidth = 22;
		final int gap = 4;
		for (int i = 0; i < GROUP_LIST_ROWS; i++) {
			IDrawing.setPositionAndWidth(intersectionGroupButtons.get(i), x, y, width - deleteWidth - gap);
			IDrawing.setPositionAndWidth(intersectionGroupDeleteButtons.get(i), x + width - deleteWidth, y, deleteWidth);
			y += LIST_ROW_HEIGHT;
		}
	}

	private void layoutIntersectionWidgets(int x, int y, int width) {
		final int gap = 8;
		final int half = (width - gap) / 2;
		final int third = (width - gap * 2) / 3;
		IDrawing.setPositionAndWidth(buttonToggleEnabled, x, y, third);
		IDrawing.setPositionAndWidth(buttonFocus, x + third + gap, y, third);
		IDrawing.setPositionAndWidth(buttonDeleteIntersection, x + (third + gap) * 2, y, width - (third + gap) * 2);
		y += 24;

		IDrawing.setPositionAndWidth(buttonAutoDetectIntersection, x, y, half);
		IDrawing.setPositionAndWidth(buttonIntersectionGroupAdd, x + half + gap, y, width - half - gap);
		y += 24;

		IDrawing.setPositionAndWidth(buttonIntersectionPhaseUp, x, y, third);
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseDown, x + third + gap, y, third);
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseMinus, x + (third + gap) * 2, y, width - (third + gap) * 2);
		y += 24;

		IDrawing.setPositionAndWidth(buttonIntersectionPhasePlus, x, y, third);
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseAdd, x + third + gap, y, third);
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseRemove, x + (third + gap) * 2, y, width - (third + gap) * 2);
		y += 24;

		IDrawing.setPositionAndWidth(buttonToggleIntersectionNodeType, x, y, third);
		IDrawing.setPositionAndWidth(buttonIntersectionNodeMinus, x + third + gap, y, third);
		IDrawing.setPositionAndWidth(buttonIntersectionNodePlus, x + (third + gap) * 2, y, width - (third + gap) * 2);
	}

	private int connectorControlsStartY() {
		return DETAILS_START_Y + 108;
	}

	private int intersectionControlsStartY() {
		return DETAILS_START_Y + 144;
	}

	private int intersectionListWidth(int contentWidth) {
		return Math.min(INTERSECTION_LIST_WIDTH, Math.max(160, contentWidth / 2 - 8));
	}

	private int intersectionDetailX(int contentWidth) {
		return 8 + intersectionListWidth(contentWidth) + 8;
	}

	private int intersectionDetailWidth(int contentWidth) {
		return Math.max(180, contentWidth - intersectionListWidth(contentWidth) - 8);
	}

	private void layoutVehiclePoolWidgets() {
		widgetMap.setPositionAndSize(width, 0, 0, 0);

		final int listWidth = vehiclePoolListWidth();
		final int leftX = vehiclePoolLeftX();
		final int rightX = vehiclePoolRightX();
		final int listY = vehiclePoolListY();
		final int pageY = Math.min(height - 68, listY + SELECTED_VEHICLE_ROWS * LIST_ROW_HEIGHT + 8);

		IDrawing.setPositionAndWidth(buttonBackToOverview, (width - 144) / 2, height - 40, 144);
		IDrawing.setPositionAndWidth(buttonVehiclePageUp, leftX, pageY, (listWidth - 8) / 2);
		IDrawing.setPositionAndWidth(buttonVehiclePageDown, leftX + (listWidth + 8) / 2, pageY, (listWidth - 8) / 2);
		IDrawing.setPositionAndWidth(buttonSelectedVehiclePageUp, rightX, pageY, (listWidth - 8) / 2);
		IDrawing.setPositionAndWidth(buttonSelectedVehiclePageDown, rightX + (listWidth + 8) / 2, pageY, (listWidth - 8) / 2);

		vehicleSearchField.setX2(leftX);
		vehicleSearchField.setY2(54);
		vehicleSearchField.setWidth(listWidth);

		int y = listY;
		for (ButtonWidgetExtension vehicleButton : vehicleButtons) {
			IDrawing.setPositionAndWidth(vehicleButton, leftX, y, listWidth);
			y += LIST_ROW_HEIGHT;
		}

		y = listY;
		for (ButtonWidgetExtension selectedVehicleButton : selectedVehicleButtons) {
			IDrawing.setPositionAndWidth(selectedVehicleButton, rightX, y, listWidth);
			y += LIST_ROW_HEIGHT;
		}
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		if (panelMode != PanelMode.VEHICLE_POOL) {
			widgetMap.render(graphicsHolder, mouseX, mouseY, delta);
		}
		graphicsHolder.push();
		graphicsHolder.translate(0, 0, 500);
		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(0, 0, panelMode == PanelMode.VEHICLE_POOL ? width : LEFT_PANEL_WIDTH, height, ARGB_BACKGROUND);
		guiDrawing.finishDrawingRectangle();
		graphicsHolder.drawText("Traffic Dashboard", 8, TITLE_Y, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
		renderHeaderHint(graphicsHolder);
		if (panelMode != PanelMode.VEHICLE_POOL) {
			renderOverviewSections(graphicsHolder, guiDrawing);
		}
		if (panelMode == PanelMode.VEHICLE_POOL) {
			renderVehiclePoolDetails(graphicsHolder);
		} else {
			renderSelectedEntryDetails(graphicsHolder);
		}
		super.render(graphicsHolder, mouseX, mouseY, delta);
		graphicsHolder.pop();
	}

	private void renderOverviewSections(GraphicsHolder graphicsHolder, GuiDrawing guiDrawing) {
		final int contentWidth = LEFT_PANEL_WIDTH - 16;
		if (dashboardSection == DashboardSection.INTERSECTIONS) {
			final int detailX = intersectionDetailX(contentWidth);
			final int dividerX = detailX - 6;
			guiDrawing.beginDrawingRectangle();
			guiDrawing.drawRectangle(dividerX, LIST_START_Y - 12, dividerX + 1, Math.min(height - 8, LIST_START_Y + LIST_SECTION_HEIGHT + 170), ARGB_LIGHT_GRAY);
			guiDrawing.finishDrawingRectangle();
			graphicsHolder.drawText("Search", 8, LIST_START_Y - 12, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText("Intersection Details", detailX, LIST_START_Y - 12, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText("Groups", detailX, LIST_START_Y + 164, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText("Signal Controls", detailX, LIST_START_Y + 280, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
		} else {
			graphicsHolder.drawText("Connectors", 8, LIST_START_Y - 12, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText("Selected Connector", 8, DETAILS_START_Y - 14, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText("Actions", 8, connectorControlsStartY() - 14, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
		}
	}

	@Override
	public void tick2() {
		reloadVehicleOptions();
		refreshButtons();
	}

	@Override
	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		if (panelMode == PanelMode.VEHICLE_POOL) {
			if (mouseX >= vehiclePoolRightX()) {
				selectedVehiclePage = clampPage(selectedVehiclePage + (amount < 0 ? 1 : -1), maxSelectedVehiclePage());
			} else {
				vehiclePage = clampPage(vehiclePage + (amount < 0 ? 1 : -1), maxVehiclePage());
			}
			refreshButtons();
			return true;
		}

		if (mouseX <= LEFT_PANEL_WIDTH) {
			entryPage = clampPage(entryPage + (amount < 0 ? 1 : -1), maxEntryPage());
			refreshButtons();
			return true;
		}

		return super.mouseScrolled2(mouseX, mouseY, amount);
	}

	@Override
	public boolean isPauseScreen2() {
		return false;
	}

	@Override
	public void onClose2() {
		widgetMap.onClose();
		super.onClose2();
	}

	private void renderSelectedEntryDetails(GraphicsHolder graphicsHolder) {
		if (dashboardSection == DashboardSection.INTERSECTIONS) {
			renderIntersectionDetails(graphicsHolder);
			return;
		}
		final ClientTrafficDashboardEntry entry = selectedEntry();
		int y = DETAILS_START_Y;
		if (entry == null) {
			graphicsHolder.drawText("No traffic connectors found", 8, y, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			y += 14;
			graphicsHolder.drawText("Place Spawn and Despawn connectors on MTR rails, then reopen this dashboard.", 8, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
			return;
		}

		graphicsHolder.drawText(connectorTypeLabel(entry), 8, y, entry.enabled() ? ARGB_WHITE : ARGB_WARNING, false, GraphicsHolder.getDefaultLight());
		y += 14;
		graphicsHolder.drawText("State: " + (entry.enabled() ? "enabled" : "disabled") + "  Route: " + (entry.hasConnectorRoute() ? "ready" : "missing") + "  Active here: " + entry.activeVehicles(), 8, y, entry.enabled() && entry.hasConnectorRoute() ? ARGB_OK : ARGB_WARNING, false, GraphicsHolder.getDefaultLight());
		y += 12;
		graphicsHolder.drawText("Position: " + entry.blockPos().getX() + ", " + entry.blockPos().getY() + ", " + entry.blockPos().getZ(), 8, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
		if (entry.type().name().equals("SPAWN")) {
			final int missingPoolEntries = countMissingPoolEntries(entry);
			y += 12;
			graphicsHolder.drawText("Pool: " + entry.effectiveVehiclePool().size() + " selected  Missing: " + missingPoolEntries + "  Loaded: " + vehicleOptions.size(), 8, y, missingPoolEntries == 0 ? ARGB_LIGHT_GRAY : ARGB_WARNING, false, GraphicsHolder.getDefaultLight());
			y += 12;
			graphicsHolder.drawText("Spawn limits: max " + entry.maxVehicles() + " vehicles, every " + String.format("%.1f", entry.spawnIntervalTicks() / 20.0D) + "s", 8, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
		}
	}

	private void renderVehiclePoolDetails(GraphicsHolder graphicsHolder) {
		final ClientTrafficDashboardEntry entry = selectedEntry();
		final int top = 34;
		final int listY = vehiclePoolListY();
		final int columnWidth = vehiclePoolListWidth();
		final int leftX = vehiclePoolLeftX();
		final int rightX = vehiclePoolRightX();
		if (entry == null || !entry.type().name().equals("SPAWN")) {
			drawCenteredText(graphicsHolder, "Select a spawn connector", width / 2, top, ARGB_WHITE);
			return;
		}

		drawCenteredText(graphicsHolder, "Vehicle Pool - " + shortEntryLabel(entry), width / 2, 10, ARGB_WHITE);
		drawCenteredText(graphicsHolder, "Available", leftX + columnWidth / 2, top, ARGB_MUTED_BLUE);
		drawCenteredText(graphicsHolder, "Selected", rightX + columnWidth / 2, top, ARGB_MUTED_BLUE);
		graphicsHolder.drawText(filteredVehicleOptions.size() + "/" + vehicleOptions.size() + " vehicles  page " + (vehiclePage + 1) + "/" + (maxVehiclePage() + 1), leftX, top + 11, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawText(entry.effectiveVehiclePool().size() + " selected  page " + (selectedVehiclePage + 1) + "/" + (maxSelectedVehiclePage() + 1), rightX, top + 11, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
		if (filteredVehicleOptions.isEmpty()) {
			graphicsHolder.drawText("No vehicles match the search.", leftX, listY, ARGB_WARNING, false, GraphicsHolder.getDefaultLight());
		}
		if (entry.effectiveVehiclePool().isEmpty()) {
			graphicsHolder.drawText("No selected vehicles. Add entries from the left list.", rightX, listY, ARGB_WARNING, false, GraphicsHolder.getDefaultLight());
		}
	}

	private void renderIntersectionDetails(GraphicsHolder graphicsHolder) {
		final ClientTrafficIntersectionEntry intersection = selectedIntersection();
		final int contentWidth = LEFT_PANEL_WIDTH - 16;
		final int x = intersectionDetailX(contentWidth);
		final int textWidth = intersectionDetailWidth(contentWidth);
		int y = LIST_START_Y;
		if (drawingIntersection) {
			graphicsHolder.drawText("Drawing intersection area", x, y, ARGB_WARNING, false, GraphicsHolder.getDefaultLight());
			y += 14;
			if (pendingIntersectionCorner == null) {
				graphicsHolder.drawText(shorten("Click the first corner on the map, like a station/depot area.", textWidth / 6), x, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
			} else {
				graphicsHolder.drawText("First corner: " + pendingIntersectionCorner.getX() + "," + pendingIntersectionCorner.getZ() + ". Click opposite corner.", x, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
			}
			return;
		}
		if (intersection == null) {
			graphicsHolder.drawText("No intersections found", x, y, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
			y += 14;
			graphicsHolder.drawText("Use Draw Area, then click two map corners.", x, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
			return;
		}

		graphicsHolder.drawText("Selected: " + shorten(intersection.effectiveName(), 32), x, y, intersection.enabled() ? ARGB_WHITE : ARGB_WARNING, false, GraphicsHolder.getDefaultLight());
		y += 12;
		graphicsHolder.drawText("Area: " + intersection.minX() + "," + intersection.minZ() + " -> " + intersection.maxX() + "," + intersection.maxZ(), x, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
		y += 12;
		graphicsHolder.drawText("Name:", x, y, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
		y = LIST_START_Y + 66;
		final List<TrafficIntersectionGroup> groups = effectiveGroups(intersection);
		final TrafficIntersectionGroup selectedGroup = selectedGroup(intersection);
		graphicsHolder.drawText("Nodes: " + intersection.nodes().size() + "  Active group: " + (selectedGroup == null ? "none" : selectedGroup.name() + " (" + (selectedPhaseIndex + 1) + "/" + groups.size() + ")"), x, y, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
		y += 12;
		if (selectedGroup == null) {
			graphicsHolder.drawText("Create a group to control green phases.", x, y, ARGB_WARNING, false, GraphicsHolder.getDefaultLight());
			y += 12;
		} else {
			graphicsHolder.drawText("Green: " + String.format("%.1f", selectedGroup.effectiveGreenDurationTicks() / 20.0D) + "s  IN node numbers: " + shorten(selectedGroup.nodeNumbers().toString(), 22), x, y, ARGB_OK, false, GraphicsHolder.getDefaultLight());
			y += 12;
		}
		graphicsHolder.drawText("Selected node: " + shorten(selectedNodeLabel(intersection), 34), x, y, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
		y += 12;
		graphicsHolder.drawText("Selected group nodes are highlighted on the map.", x, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
	}

	//Adjust this shit so it's not over other contnt...
	private void renderHeaderHint(GraphicsHolder graphicsHolder) {
		final String hint = panelMode == PanelMode.VEHICLE_POOL
			? "Vehicle pool edits apply to the selected spawn connector."
			: dashboardSection == DashboardSection.INTERSECTIONS
				? "Draw areas, inspect detected nodes, and tune signal groups."
				: "Review connector health, spawn limits, and vehicle pools.";
		graphicsHolder.drawText(hint, 8, TITLE_Y + 12, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
	}

	private void renderGroupSummary(GraphicsHolder graphicsHolder, ClientTrafficIntersectionEntry intersection, int x, int y, int width) {
		final List<TrafficIntersectionGroup> groups = effectiveGroups(intersection);
		if (groups.isEmpty()) {
			return;
		}

		graphicsHolder.drawText("Groups:", x, y, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
		y += 10;
		final int rows = Math.min(groups.size(), 4);
		for (int i = 0; i < rows; i++) {
			final TrafficIntersectionGroup group = groups.get(i);
			final String prefix = i == selectedPhaseIndex ? "> " : "  ";
			final String groupText = prefix + (i + 1) + ". " + shorten(group.name(), 12) + "  nodes " + group.nodeNumbers() + "  " + String.format("%.1f", group.effectiveGreenDurationTicks() / 20.0D) + "s";
			graphicsHolder.drawText(shorten(groupText, Math.max(10, width / 6)), x, y, i == selectedPhaseIndex ? ARGB_OK : ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
			y += 10;
		}
		if (groups.size() > rows) {
			graphicsHolder.drawText("+" + (groups.size() - rows) + " more groups", x, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
		}
	}

	private void refreshButtons() {
		if (entryButtons.isEmpty()) {
			return;
		}

		final ClientTrafficDashboardEntry entry = selectedEntry();
		final boolean hasEntry = entry != null;
		final ClientTrafficIntersectionEntry intersection = selectedIntersection();
		final boolean hasIntersection = intersection != null;
		final boolean isSpawn = hasEntry && entry.type().name().equals("SPAWN");
		final boolean vehiclePoolMode = panelMode == PanelMode.VEHICLE_POOL;
		if (vehiclePoolMode || dashboardSection != DashboardSection.INTERSECTIONS) {
			drawingIntersection = false;
			pendingIntersectionCorner = null;
		}
		widgetMap.setCreatingIntersection(drawingIntersection && dashboardSection == DashboardSection.INTERSECTIONS && !vehiclePoolMode);
		widgetMap.setPendingIntersectionCorner(pendingIntersectionCorner);
		final boolean intersectionMode = dashboardSection == DashboardSection.INTERSECTIONS && !vehiclePoolMode;
		final boolean connectorMode = dashboardSection == DashboardSection.CONNECTORS && !vehiclePoolMode;

		for (int i = 0; i < entryButtons.size(); i++) {
			final int entryIndex = entryPage * LIST_ENTRIES_PER_PAGE + i;
			final ButtonWidgetExtension button = entryButtons.get(i);
			if (!vehiclePoolMode && dashboardSection == DashboardSection.INTERSECTIONS && entryIndex < filteredIntersections.size()) {
				final ClientTrafficIntersectionEntry listEntry = filteredIntersections.get(entryIndex);
				final int sourceIndex = intersections.indexOf(listEntry);
				button.visible = true;
				button.active = true;
				button.setMessage(Component.literal((sourceIndex == selectedIntersectionIndex ? "> " : "") + shorten(listEntry.effectiveName(), 24) + " | " + (listEntry.enabled() ? "on" : "off") + " | " + listEntry.nodes().size() + " nodes"));
			} else if (!vehiclePoolMode && dashboardSection == DashboardSection.CONNECTORS && entryIndex < entries.size()) {
				final ClientTrafficDashboardEntry listEntry = entries.get(entryIndex);
				button.visible = true;
				button.active = true;
				button.setMessage(Component.literal((entryIndex == selectedIndex ? "> " : "") + connectorTypeShortLabel(listEntry) + " | " + listEntry.blockPos().getX() + "," + listEntry.blockPos().getZ() + " | " + (listEntry.enabled() ? "on" : "off") + " | " + listEntry.activeVehicles() + " active | " + (listEntry.hasConnectorRoute() ? "route ok" : "no route")));
			} else {
				button.visible = false;
				button.active = false;
				button.setMessage(Component.literal(""));
			}
		}

		final List<TrafficIntersectionGroup> groups = intersection == null ? List.of() : effectiveGroups(intersection);
		for (int i = 0; i < GROUP_LIST_ROWS; i++) {
			final ButtonWidgetExtension groupButton = intersectionGroupButtons.get(i);
			final ButtonWidgetExtension deleteButton = intersectionGroupDeleteButtons.get(i);
			if (intersectionMode && i < groups.size()) {
				final TrafficIntersectionGroup group = groups.get(i);
				groupButton.visible = true;
				groupButton.active = true;
				groupButton.setMessage(Component.literal((i == selectedPhaseIndex ? "> " : "") + (i + 1) + ". " + shorten(group.name(), 12) + " | nodes " + group.nodeNumbers() + " | " + String.format("%.1f", group.effectiveGreenDurationTicks() / 20.0D) + "s"));
				deleteButton.visible = true;
				deleteButton.active = true;
				deleteButton.setMessage(Component.literal("x"));
			} else {
				groupButton.visible = false;
				groupButton.active = false;
				groupButton.setMessage(Component.literal(""));
				deleteButton.visible = false;
				deleteButton.active = false;
				deleteButton.setMessage(Component.literal(""));
			}
		}

		//What the fuck am I doing at this point :<
		//Improve this LATER!!!

		buttonEntryPageUp.active = entryPage > 0;
		buttonEntryPageDown.active = entryPage < maxEntryPage();
		buttonVehiclePageUp.active = vehiclePage > 0;
		buttonVehiclePageDown.active = vehiclePage < maxVehiclePage();
		buttonSelectedVehiclePageUp.active = selectedVehiclePage > 0;
		buttonSelectedVehiclePageDown.active = selectedVehiclePage < maxSelectedVehiclePage();
		final boolean hasSelectedNode = selectedIntersectionNode != null;
		final boolean hasSelectedGroup = intersection != null && selectedGroup(intersection) != null;

		buttonToggleEnabled.active = (connectorMode ? hasEntry : hasIntersection) && !vehiclePoolMode;
		buttonGroupMinus.active = false;
		buttonGroupPlus.active = false;
		buttonMaxVehiclesMinus.active = connectorMode && isSpawn;
		buttonMaxVehiclesPlus.active = connectorMode && isSpawn;
		buttonSpawnIntervalMinus.active = connectorMode && isSpawn;
		buttonSpawnIntervalPlus.active = connectorMode && isSpawn;
		buttonTargetGroupMinus.active = false;
		buttonTargetGroupPlus.active = false;
		buttonFocus.active = (dashboardSection == DashboardSection.CONNECTORS ? hasEntry : hasIntersection) && !vehiclePoolMode;
		buttonOpenVehiclePool.active = connectorMode && isSpawn;
		buttonClearVehicles.active = connectorMode;
		buttonDeleteIntersection.active = intersectionMode && hasIntersection;
		buttonAutoDetectIntersection.active = intersectionMode && hasIntersection;
		buttonIntersectionGroupAdd.active = intersectionMode && hasIntersection;
		buttonIntersectionGroupPrevious.active = false;
		buttonIntersectionGroupNext.active = false;
		buttonToggleIntersectionNodeType.active = intersectionMode && hasSelectedNode;
		buttonIntersectionNodeMinus.active = intersectionMode && hasSelectedNode;
		buttonIntersectionNodePlus.active = intersectionMode && hasSelectedNode;
		buttonIntersectionPhaseMinus.active = intersectionMode && hasSelectedGroup;
		buttonIntersectionPhasePlus.active = intersectionMode && hasSelectedGroup;
		buttonIntersectionPhaseAdd.active = intersectionMode && hasSelectedGroup && selectedNodeNumber() != null && selectedNodeIsIn();
		buttonIntersectionPhaseRemove.active = intersectionMode && hasSelectedGroup && hasSelectedNode && selectedNodeIsIn();
		buttonIntersectionPhaseUp.active = intersectionMode && hasSelectedGroup && selectedPhaseIndex > 0;
		buttonIntersectionPhaseDown.active = intersectionMode && hasSelectedGroup && intersection != null && selectedPhaseIndex < effectiveGroups(intersection).size() - 1;
		buttonMapTopView.active = !vehiclePoolMode && !widgetMap.isMapOverlayMode(WorldMap.MapOverlayMode.TOP_VIEW);
		buttonMapCurrentY.active = !vehiclePoolMode && !widgetMap.isMapOverlayMode(WorldMap.MapOverlayMode.CURRENT_Y);
		buttonBackToOverview.visible = vehiclePoolMode;
		buttonBackToOverview.active = vehiclePoolMode;
		buttonAddIntersection.setMessage(Component.literal(drawingIntersection ? "Cancel Area" : "Draw Area"));
		buttonToggleEnabled.setMessage(Component.literal((dashboardSection == DashboardSection.INTERSECTIONS ? hasIntersection && intersection.enabled() : hasEntry && entry.enabled()) ? "Disable" : "Enable"));
		buttonFocus.setMessage(Component.literal("Focus Map"));
		buttonOpenVehiclePool.setMessage(Component.literal("Vehicle Pool"));
		buttonRefresh.setMessage(Component.literal("Refresh Routes"));
		buttonClearVehicles.setMessage(Component.literal("Clear Active"));
		buttonMaxVehiclesMinus.setMessage(Component.literal("Max Vehicles -"));
		buttonMaxVehiclesPlus.setMessage(Component.literal("Max Vehicles +"));
		buttonSpawnIntervalMinus.setMessage(Component.literal("Interval -1s"));
		buttonSpawnIntervalPlus.setMessage(Component.literal("Interval +1s"));
		buttonDeleteIntersection.setMessage(Component.literal("Delete"));
		buttonAutoDetectIntersection.setMessage(Component.literal("Find Nodes"));
		buttonIntersectionGroupAdd.setMessage(Component.literal("Add Group"));
		buttonIntersectionGroupPrevious.setMessage(Component.literal(""));
		buttonIntersectionGroupNext.setMessage(Component.literal(""));
		buttonToggleIntersectionNodeType.setMessage(Component.literal("Node Type"));
		buttonIntersectionNodeMinus.setMessage(Component.literal("Node # -"));
		buttonIntersectionNodePlus.setMessage(Component.literal("Node # +"));
		buttonIntersectionPhaseMinus.setMessage(Component.literal("Green -1s"));
		buttonIntersectionPhasePlus.setMessage(Component.literal("Green +1s"));
		buttonIntersectionPhaseAdd.setMessage(Component.literal("Assign Node"));
		buttonIntersectionPhaseRemove.setMessage(Component.literal("Remove Node"));
		buttonIntersectionPhaseUp.setMessage(Component.literal("Move Up"));
		buttonIntersectionPhaseDown.setMessage(Component.literal("Move Down"));

		final List<String> selectedVehiclePool = entry == null ? List.of() : entry.effectiveVehiclePool();
		selectedVehiclePage = Math.min(selectedVehiclePage, maxSelectedVehiclePage());
		for (int i = 0; i < selectedVehicleButtons.size(); i++) {
			final int vehicleIndex = selectedVehiclePage * SELECTED_VEHICLE_ROWS + i;
			final ButtonWidgetExtension button = selectedVehicleButtons.get(i);
			if (vehiclePoolMode && isSpawn && vehicleIndex < selectedVehiclePool.size()) {
				final String vehicleId = selectedVehiclePool.get(vehicleIndex);
				final VehicleOption vehicleOption = findVehicleOption(vehicleId);
				button.visible = true;
				button.active = true;
				button.setMessage(Component.literal("[-] " + shorten(vehicleOption == null ? vehicleId : vehicleOption.label(), 42)));
			} else {
				button.visible = false;
				button.active = false;
				button.setMessage(Component.literal(""));
			}
		}

		for (int i = 0; i < vehicleButtons.size(); i++) {
			final int vehicleIndex = vehiclePage * AVAILABLE_VEHICLE_ENTRIES_PER_PAGE + i;
			final ButtonWidgetExtension button = vehicleButtons.get(i);
			if (vehiclePoolMode && isSpawn && vehicleIndex < filteredVehicleOptions.size()) {
				final VehicleOption vehicleOption = filteredVehicleOptions.get(vehicleIndex);
				button.visible = true;
				button.active = true;
				button.setMessage(Component.literal((selectedVehiclePool.contains(vehicleOption.id()) ? "[x] " : "[+] ") + shorten(vehicleOption.label(), 72)));
			} else {
				button.visible = false;
				button.active = false;
				button.setMessage(Component.literal(""));
			}
		}

		buttonVehiclePageUp.visible = vehiclePoolMode;
		buttonVehiclePageDown.visible = vehiclePoolMode;
		buttonSelectedVehiclePageUp.visible = vehiclePoolMode;
		buttonSelectedVehiclePageDown.visible = vehiclePoolMode;
		vehicleSearchField.setVisible(vehiclePoolMode);
		intersectionSearchField.setVisible2(intersectionMode);
		intersectionNameField.setVisible2(intersectionMode && hasIntersection);
		intersectionNameField.setActiveMapped(intersectionMode && hasIntersection);
		selectedVehicleButtons.forEach(button -> button.visible = vehiclePoolMode && isSpawn && button.visible);
		vehicleButtons.forEach(button -> button.visible = vehiclePoolMode && isSpawn && button.visible);
		buttonEntryPageUp.visible = !vehiclePoolMode;
		buttonEntryPageDown.visible = !vehiclePoolMode;
		buttonToggleEnabled.visible = !vehiclePoolMode;
		buttonFocus.visible = !vehiclePoolMode;
		buttonOpenVehiclePool.visible = connectorMode && isSpawn;
		buttonRefresh.visible = connectorMode;
		buttonClearVehicles.visible = connectorMode;
		buttonGroupMinus.visible = false;
		buttonGroupPlus.visible = false;
		buttonMaxVehiclesMinus.visible = connectorMode && isSpawn;
		buttonMaxVehiclesPlus.visible = connectorMode && isSpawn;
		buttonSpawnIntervalMinus.visible = connectorMode && isSpawn;
		buttonSpawnIntervalPlus.visible = connectorMode && isSpawn;
		buttonDeleteIntersection.visible = intersectionMode;
		buttonAutoDetectIntersection.visible = intersectionMode;
		buttonIntersectionGroupAdd.visible = intersectionMode;
		buttonIntersectionGroupPrevious.visible = false;
		buttonIntersectionGroupNext.visible = false;
		buttonToggleIntersectionNodeType.visible = intersectionMode;
		buttonIntersectionNodeMinus.visible = intersectionMode;
		buttonIntersectionNodePlus.visible = intersectionMode;
		buttonIntersectionPhaseMinus.visible = intersectionMode;
		buttonIntersectionPhasePlus.visible = intersectionMode;
		buttonIntersectionPhaseAdd.visible = intersectionMode;
		buttonIntersectionPhaseRemove.visible = intersectionMode;
		buttonIntersectionPhaseUp.visible = intersectionMode;
		buttonIntersectionPhaseDown.visible = intersectionMode;
		buttonSectionConnectors.visible = !vehiclePoolMode;
		buttonSectionIntersections.visible = !vehiclePoolMode;
		buttonAddIntersection.visible = !vehiclePoolMode && dashboardSection == DashboardSection.INTERSECTIONS;
		buttonTargetGroupMinus.visible = false;
		buttonTargetGroupPlus.visible = false;
		buttonZoomIn.visible = !vehiclePoolMode;
		buttonZoomOut.visible = !vehiclePoolMode;
		buttonMapTopView.visible = !vehiclePoolMode;
		buttonMapCurrentY.visible = !vehiclePoolMode;
	}

	private void selectEntry(ClientTrafficDashboardEntry entry) {
		final int index = entries.indexOf(entry);
		if (index >= 0) {
			selectedIndex = index;
			entryPage = index / LIST_ENTRIES_PER_PAGE;
			panelMode = entry.type().name().equals("SPAWN") ? PanelMode.VEHICLE_POOL : PanelMode.OVERVIEW;
			layoutWidgets();
			refreshButtons();
		}
	}

	private void selectIntersection(ClientTrafficIntersectionEntry intersection) {
		final int index = intersections.indexOf(intersection);
		if (index >= 0) {
			selectedIntersectionIndex = index;
			dashboardSection = DashboardSection.INTERSECTIONS;
			pendingIntersectionCorner = null;
			drawingIntersection = false;
			selectedIntersectionNode = null;
			selectedPhaseIndex = 0;
			widgetMap.setCreatingIntersection(false);
			widgetMap.setPendingIntersectionCorner(null);
			syncIntersectionNameField();
			layoutWidgets();
			refreshButtons();
		}
	}

	private ClientTrafficDashboardEntry selectedEntry() {
		return selectedIndex >= 0 && selectedIndex < entries.size() ? entries.get(selectedIndex) : null;
	}

	private ClientTrafficIntersectionEntry selectedIntersection() {
		return selectedIntersectionIndex >= 0 && selectedIntersectionIndex < intersections.size() ? intersections.get(selectedIntersectionIndex) : null;
	}

	private int maxEntryPage() {
		final int size = dashboardSection == DashboardSection.INTERSECTIONS ? filteredIntersections.size() : entries.size();
		return Math.max(0, (size - 1) / LIST_ENTRIES_PER_PAGE);
	}

	private int maxVehiclePage() {
		return Math.max(0, (filteredVehicleOptions.size() - 1) / AVAILABLE_VEHICLE_ENTRIES_PER_PAGE);
	}

	private int maxSelectedVehiclePage() {
		final ClientTrafficDashboardEntry entry = selectedEntry();
		final int selectedVehicleCount = entry == null ? 0 : entry.effectiveVehiclePool().size();
		return Math.max(0, (selectedVehicleCount - 1) / SELECTED_VEHICLE_ROWS);
	}

	private void sendIntersectionUpdate(String action, int delta, String value) {
		final ClientTrafficIntersectionEntry intersection = selectedIntersection();
		if (intersection == null) {
			return;
		}

		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeUtf(intersection.id());
		buffer.writeUtf(action);
		buffer.writeVarInt(delta);
		buffer.writeBoolean(value != null);
		if (value != null) {
			buffer.writeUtf(value);
		}
		ClientPlayNetworking.send(TrafficDashboardNetworking.INTERSECTION_UPDATE_PACKET_ID, buffer);
	}

	private void sendCreateIntersection(BlockPos firstCorner, BlockPos secondCorner) {
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeBlockPos(firstCorner);
		buffer.writeBlockPos(secondCorner);
		ClientPlayNetworking.send(TrafficDashboardNetworking.INTERSECTION_CREATE_PACKET_ID, buffer);
	}

	private void handleIntersectionCornerClick(Double worldX, Double worldZ) {
		if (dashboardSection != DashboardSection.INTERSECTIONS || !drawingIntersection) {
			return;
		}

		final int y = Minecraft.getInstance().player == null ? 64 : Minecraft.getInstance().player.blockPosition().getY();
		final BlockPos corner = new BlockPos((int) Math.floor(worldX), y, (int) Math.floor(worldZ));
		if (pendingIntersectionCorner == null) {
			pendingIntersectionCorner = corner;
			widgetMap.setPendingIntersectionCorner(corner);
		} else {
			sendCreateIntersection(new BlockPos(pendingIntersectionCorner.getX(), y - 8, pendingIntersectionCorner.getZ()), new BlockPos(corner.getX(), y + 8, corner.getZ()));
			pendingIntersectionCorner = null;
			drawingIntersection = false;
			widgetMap.setCreatingIntersection(false);
			widgetMap.setPendingIntersectionCorner(null);
		}
		refreshButtons();
	}

	private void handleIntersectionNodeClick(ClientTrafficIntersectionEntry intersection, Long nodeX, Long nodeZ) {
		if (dashboardSection != DashboardSection.INTERSECTIONS || intersection == null) {
			return;
		}

		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node : intersection.nodes()) {
			if (node.x() == nodeX && node.z() == nodeZ) {
				selectedIntersectionNode = node.x() + "," + node.y() + "," + node.z();
				refreshButtons();
				return;
			}
		}

		if (nodeX >= intersection.minX() && nodeX <= intersection.maxX() && nodeZ >= intersection.minZ() && nodeZ <= intersection.maxZ()) {
			final int y = Minecraft.getInstance().player == null ? (int) Math.round((intersection.minY() + intersection.maxY()) / 2.0D) : Minecraft.getInstance().player.blockPosition().getY();
			selectedIntersectionNode = nodeX + "," + y + "," + nodeZ;
			sendIntersectionUpdate("node_add", 0, selectedIntersectionNode);
			refreshButtons();
		}
	}

	private String selectedIntersectionNode() {
		return selectedIntersectionNode;
	}

	private List<Integer> selectedGroupNodeNumbers() {
		final ClientTrafficIntersectionEntry intersection = selectedIntersection();
		final TrafficIntersectionGroup group = intersection == null ? null : selectedGroup(intersection);
		return group == null ? List.of() : group.nodeNumbers();
	}

	private Integer selectedNodeNumber() {
		final ClientTrafficIntersectionEntry intersection = selectedIntersection();
		if (intersection == null || selectedIntersectionNode == null) {
			return null;
		}
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node : intersection.nodes()) {
			if (selectedIntersectionNode.equals(node.x() + "," + node.y() + "," + node.z())) {
				return node.number();
			}
		}
		return null;
	}

	private boolean selectedNodeIsIn() {
		final ClientTrafficIntersectionEntry intersection = selectedIntersection();
		if (intersection == null || selectedIntersectionNode == null) {
			return false;
		}
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node : intersection.nodes()) {
			if (selectedIntersectionNode.equals(node.x() + "," + node.y() + "," + node.z())) {
				return node.type().name().equals("IN");
			}
		}
		return false;
	}

	private Integer selectedPhaseValue() {
		return selectedNodeNumber();
	}

	private static List<Integer> effectivePhaseOrder(ClientTrafficIntersectionEntry intersection) {
		if (!intersection.phaseOrder().isEmpty()) {
			return intersection.phaseOrder();
		}
		return intersection.nodes().stream()
			.filter(node -> node.type().name().equals("IN"))
			.map(com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode::number)
			.distinct()
			.sorted()
			.toList();
	}

	private static List<TrafficIntersectionGroup> effectiveGroups(ClientTrafficIntersectionEntry intersection) {
		if (!intersection.groups().isEmpty()) {
			return intersection.groups();
		}
		return effectivePhaseOrder(intersection).stream()
			.map(number -> new TrafficIntersectionGroup("Group " + number, intersection.phaseDurationTicks(), List.of(number)))
			.toList();
	}

	private TrafficIntersectionGroup selectedGroup(ClientTrafficIntersectionEntry intersection) {
		final List<TrafficIntersectionGroup> groups = effectiveGroups(intersection);
		return selectedPhaseIndex >= 0 && selectedPhaseIndex < groups.size() ? groups.get(selectedPhaseIndex) : null;
	}

	private static List<Integer> groupIndexesForNode(List<TrafficIntersectionGroup> groups, int nodeNumber) {
		final List<Integer> groupIndexes = new ArrayList<>();
		for (int i = 0; i < groups.size(); i++) {
			if (groups.get(i).nodeNumbers().contains(nodeNumber)) {
				groupIndexes.add(i);
			}
		}
		return groupIndexes;
	}

	private static String phaseOrderLabel(List<Integer> phaseOrder) {
		return phaseOrder.isEmpty() ? "none" : phaseOrder.stream().map(value -> "#" + value).collect(java.util.stream.Collectors.joining(" -> "));
	}

	private String selectedNodeLabel(ClientTrafficIntersectionEntry intersection) {
		if (selectedIntersectionNode == null) {
			return "none";
		}
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node : intersection.nodes()) {
			if (selectedIntersectionNode.equals(node.x() + "," + node.y() + "," + node.z())) {
				final List<TrafficIntersectionGroup> groups = effectiveGroups(intersection);
				final List<Integer> groupIndexes = groupIndexesForNode(groups, node.number());
				final String groupLabel = groupIndexes.isEmpty()
					? "not in a group"
					: "in groups " + groupIndexes.stream().map(index -> String.valueOf(index + 1)).collect(java.util.stream.Collectors.joining(","));
				return node.type() + " #" + node.number() + " @ " + node.x() + "," + node.z() + "  " + groupLabel;
			}
		}
		return selectedIntersectionNode;
	}

	private int vehiclePoolListWidth() {
		return Math.min(360, Math.max(144, (width - 80) / 2));
	}

	private int vehiclePoolLeftX() {
		return width / 2 - vehiclePoolListWidth() - 20;
	}

	private int vehiclePoolRightX() {
		return width / 2 + 20;
	}

	private int vehiclePoolListY() {
		return 76;
	}

	private String shortEntryLabel(ClientTrafficDashboardEntry entry) {
		return (entry.type().name().equals("SPAWN") ? "S" : "D") + " @ " + entry.blockPos().getX() + "," + entry.blockPos().getZ();
	}

	private String connectorTypeLabel(ClientTrafficDashboardEntry entry) {
		return entry.type().name().equals("SPAWN") ? "Spawn Connector" : "Despawn Connector";
	}

	private String connectorTypeShortLabel(ClientTrafficDashboardEntry entry) {
		return entry.type().name().equals("SPAWN") ? "Spawn" : "Despawn";
	}

	private void sendUpdate(String action, int delta, String value) {
		final ClientTrafficDashboardEntry entry = selectedEntry();
		if (entry == null) {
			return;
		}

		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeUtf(entry.id());
		buffer.writeBlockPos(entry.blockPos());
		buffer.writeUtf(action);
		buffer.writeVarInt(delta);
		buffer.writeBoolean(value != null);
		if (value != null) {
			buffer.writeUtf(value);
		}
		ClientPlayNetworking.send(TrafficDashboardNetworking.UPDATE_PACKET_ID, buffer);
	}

	private void sendRefresh() {
		ClientPlayNetworking.send(TrafficDashboardNetworking.REFRESH_PACKET_ID, new FriendlyByteBuf(Unpooled.buffer()));
	}

	private void sendClearVehicles() {
		ClientPlayNetworking.send(TrafficDashboardNetworking.CLEAR_VEHICLES_PACKET_ID, new FriendlyByteBuf(Unpooled.buffer()));
	}

	private void reloadVehicleOptions() {
		final java.util.Map<String, VehicleOption> updatedOptionsById = new java.util.LinkedHashMap<>();
		CustomResourceLoader.iterateVehicles(TransportMode.TRAIN, vehicleResource -> updatedOptionsById.put(vehicleResource.getId(), new VehicleOption(vehicleResource.getId(), formatVehicleLabel(vehicleResource))));
		for (ClientMtrVehicleResourceRegistry.VisualDefinition visualDefinition : ClientMtrVehicleResourceRegistry.all()) {
			updatedOptionsById.putIfAbsent(visualDefinition.id(), new VehicleOption(visualDefinition.id(), formatVehicleLabel(visualDefinition)));
		}
		final List<VehicleOption> updatedOptions = new ArrayList<>(updatedOptionsById.values());
		updatedOptions.sort(Comparator.comparing(VehicleOption::label, String.CASE_INSENSITIVE_ORDER));
		if (!updatedOptions.equals(vehicleOptions)) {
			vehicleOptions.clear();
			vehicleOptions.addAll(updatedOptions);
			refreshFilteredVehicleOptions();
			vehiclePage = Math.min(vehiclePage, maxVehiclePage());
		}
	}

	private void refreshFilteredVehicleOptions() {
		filteredVehicleOptions.clear();
		final String query = vehicleSearchQuery.toLowerCase(java.util.Locale.ROOT);
		for (VehicleOption vehicleOption : vehicleOptions) {
			if (query.isBlank() || vehicleOption.label().toLowerCase(java.util.Locale.ROOT).contains(query) || vehicleOption.id().toLowerCase(java.util.Locale.ROOT).contains(query)) {
				filteredVehicleOptions.add(vehicleOption);
			}
		}
		vehiclePage = Math.min(vehiclePage, maxVehiclePage());
	}

	private void refreshFilteredIntersections() {
		filteredIntersections.clear();
		final String query = intersectionSearchQuery.toLowerCase(java.util.Locale.ROOT);
		for (ClientTrafficIntersectionEntry intersection : intersections) {
			final String searchable = (intersection.effectiveName() + " " + intersection.centerX() + " " + intersection.centerZ() + " " + intersection.id()).toLowerCase(java.util.Locale.ROOT);
			if (query.isBlank() || searchable.contains(query)) {
				filteredIntersections.add(intersection);
			}
		}
		entryPage = Math.min(entryPage, maxEntryPage());
	}

	private void syncIntersectionNameField() {
		updatingIntersectionNameField = true;
		try {
			final ClientTrafficIntersectionEntry intersection = selectedIntersection();
			intersectionNameField.setText2(intersection == null ? "" : intersection.effectiveName());
		} finally {
			updatingIntersectionNameField = false;
		}
	}

	private static String formatVehicleLabel(VehicleResource vehicleResource) {
		final String name = vehicleResource.getName().getString();
		final String metrics = String.format(" %.1fm x %.1fm", vehicleResource.getLength(), vehicleResource.getWidth());
		if (name == null || name.isBlank()) {
			return vehicleResource.getId() + metrics;
		}
		return name + " [" + vehicleResource.getId() + "]" + metrics;
	}

	private static String formatVehicleLabel(ClientMtrVehicleResourceRegistry.VisualDefinition visualDefinition) {
		final String metrics = visualDefinition.lengthMeters() > 0.0D || visualDefinition.widthMeters() > 0.0D
			? String.format(" %.1fm x %.1fm", visualDefinition.lengthMeters(), visualDefinition.widthMeters())
			: "";
		if (visualDefinition.name() == null || visualDefinition.name().isBlank()) {
			return visualDefinition.id() + metrics;
		}
		return visualDefinition.name() + " [" + visualDefinition.id() + "]" + metrics;
	}

	private void openVehiclePool() {
		if (selectedEntry() != null && selectedEntry().type().name().equals("SPAWN")) {
			panelMode = PanelMode.VEHICLE_POOL;
			layoutWidgets();
			refreshButtons();
		}
	}

	private VehicleOption findVehicleOption(String vehicleId) {
		for (VehicleOption vehicleOption : vehicleOptions) {
			if (vehicleOption.id().equals(vehicleId)) {
				return vehicleOption;
			}
		}
		return null;
	}

	private int countMissingPoolEntries(ClientTrafficDashboardEntry entry) {
		int missing = 0;
		for (String vehicleId : entry.effectiveVehiclePool()) {
			if (findVehicleOption(vehicleId) == null) {
				missing++;
			}
		}
		return missing;
	}

	private void renderInstructionLines(GraphicsHolder graphicsHolder, int y) {
		graphicsHolder.drawText("Workflow:", 8, y, ARGB_MUTED_BLUE, false, GraphicsHolder.getDefaultLight());
		y += 10;
		graphicsHolder.drawText("1. Spawn and Despawn connectors need valid MTR rail paths.", 8, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
		y += 10;
		graphicsHolder.drawText("2. Any connected enabled despawn can be used as a destination.", 8, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
		y += 10;
		graphicsHolder.drawText("3. Vehicle Pool chooses loaded MTR vehicle IDs.", 8, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
		y += 10;
		graphicsHolder.drawText("4. Active count confirms successful spawning.", 8, y, ARGB_LIGHT_GRAY, false, GraphicsHolder.getDefaultLight());
	}

	private static int clampPage(int value, int maxPage) {
		return Math.max(0, Math.min(maxPage, value));
	}

	private static String shorten(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, Math.max(0, maxLength - 3)) + "...";
	}

	private static void drawCenteredText(GraphicsHolder graphicsHolder, String text, int centerX, int y, int color) {
		graphicsHolder.drawText(text, centerX - GraphicsHolder.getTextWidth(text) / 2, y, color, false, GraphicsHolder.getDefaultLight());
	}

	private record VehicleOption(String id, String label) {
	}

	private enum PanelMode {
		OVERVIEW,
		VEHICLE_POOL
	}

	private enum DashboardSection {
		CONNECTORS,
		INTERSECTIONS
	}
}
