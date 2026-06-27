package com.cookiecraftmods.mta.client.dashboard;

import com.cookiecraftmods.mta.traffic.dashboard.network.TrafficDashboardNetworking;
import com.cookiecraftmods.mta.client.render.ClientMtrVehicleResourceRegistry;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionGroup;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionSignalMode;
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

	// ── Layout ────────────────────────────────────────────────────────────────
	private static final int PANEL_MIN_WIDTH    = 280;
	private static final int PANEL_MAX_WIDTH    = 640;
	private static final int MAP_MIN_WIDTH      = 200;
	private static final int NARROW_THRESHOLD   = 520;
	private static final int MAX_LIST_ROWS      = 10;
	private static final int GROUP_LIST_ROWS    = 5;
	private static final int SELECTED_VEH_ROWS  = 14;
	private static final int AVAIL_VEH_PER_PAGE = 14;
	private static final int TITLE_Y            = 8;
	private static final int HINT_Y             = 19;
	private static final int TAB_Y              = 32;
	private static final int LIST_START_Y       = 76;
	private static final int ROW_H              = 20;
	private static final int INLINE_BTN_W       = 22;
	private static final int MARGIN             = 10;
	private static final int GAP                = 6;

	// ── Colors ────────────────────────────────────────────────────────────────
	private static final int C_WHITE    = 0xFFFFFFFF;
	private static final int C_HINT     = 0xFF888888;
	private static final int C_SECTION  = 0xFF9CC7FF;
	private static final int C_OK       = 0xFF7EE787;
	private static final int C_WARN     = 0xFFFFD166;
	private static final int C_MUTED    = 0xFFAAAAAA;
	private static final int C_DIVIDER  = 0xFF3A3A3A;
	private static final int C_SELECTED = 0xFF2A4870;

	// ── Data ──────────────────────────────────────────────────────────────────
	private final List<ClientTrafficDashboardEntry>    entries              = new ArrayList<>();
	private final List<ClientTrafficIntersectionEntry> intersections        = new ArrayList<>();
	private final List<ClientTrafficIntersectionEntry> filteredIntersections = new ArrayList<>();
	private final List<VehicleOption>                  vehicleOptions        = new ArrayList<>();
	private final List<VehicleOption>                  filteredVehicleOptions = new ArrayList<>();

	private final List<ButtonWidgetExtension> entryButtons              = new ArrayList<>();
	private final List<ButtonWidgetExtension> intersectionGroupButtons   = new ArrayList<>();
	private final List<ButtonWidgetExtension> intersectionGroupDeleteBtns = new ArrayList<>();
	private final List<ButtonWidgetExtension> selectedVehicleButtons    = new ArrayList<>();
	private final List<ButtonWidgetExtension> vehicleButtons            = new ArrayList<>();

	private final TrafficWidgetMap widgetMap;

	// ── State ─────────────────────────────────────────────────────────────────
	private PanelMode       panelMode       = PanelMode.OVERVIEW;
	private DashboardSection dashboardSection = DashboardSection.CONNECTORS;
	private boolean mapVisibleInNarrow      = false;
	private int spawnIntervalRowY  = 0;
	private int phaseDurRowY       = 0;
	private int  selectedIndex;
	private int  selectedIntersectionIndex;
	private int  entryPage;
	private int  vehiclePage;
	private int  selectedVehiclePage;
	private String vehicleSearchQuery    = "";
	private String intersectionSearchQuery = "";
	private BlockPos pendingIntersectionCorner;
	private boolean  drawingIntersection;
	private String   selectedIntersectionNode;
	private boolean  updatingIntersectionNameField;
	private int selectedPhaseIndex = -1;

	// ── Widgets ───────────────────────────────────────────────────────────────
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
	private final ButtonWidgetExtension buttonIntersectionSignalMode;
	private final ButtonWidgetExtension buttonAutoDetectIntersection;
	private final ButtonWidgetExtension buttonIntersectionGroupAdd;
	private final ButtonWidgetExtension buttonIntersectionGroupPrevious;
	private final ButtonWidgetExtension buttonIntersectionGroupNext;
	private final ButtonWidgetExtension buttonToggleIntersectionNodeType;
	private final ButtonWidgetExtension buttonIntersectionNodeMinus;
	private final ButtonWidgetExtension buttonIntersectionNodePlus;
	private final ButtonWidgetExtension buttonIntersectionNodeDelete;
	private final ButtonWidgetExtension buttonIntersectionPhaseMinus;
	private final ButtonWidgetExtension buttonIntersectionPhasePlus;
	private final ButtonWidgetExtension buttonIntersectionPhaseAdd;
	private final ButtonWidgetExtension buttonIntersectionPhaseRemove;
	private final ButtonWidgetExtension buttonIntersectionPhaseUp;
	private final ButtonWidgetExtension buttonIntersectionPhaseDown;
	private final ButtonWidgetExtension buttonToggleMap;

	// ── Constructor ───────────────────────────────────────────────────────────
	public TrafficDashboardScreen(List<ClientTrafficDashboardEntry> entries, List<ClientTrafficIntersectionEntry> intersections) {
		super(TextHelper.literal("Traffic Dashboard"));

		widgetMap = new TrafficWidgetMap(
			() -> this.entries, () -> this.intersections,
			this::selectedEntry, this::selectedIntersection,
			this::selectedIntersectionNode, this::selectedGroupNodeNumbers,
			this::selectEntry, this::selectIntersection,
			this::handleIntersectionCornerClick, this::handleIntersectionNodeClick
		);

		vehicleSearchField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal("Search vehicles"), 128, TextCase.DEFAULT, "", "Search vehicles");
		vehicleSearchField.setChangedListener2(v -> { vehicleSearchQuery = v == null ? "" : v.trim(); vehiclePage = 0; refreshFilteredVehicleOptions(); refreshButtons(); });

		intersectionSearchField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal("Search intersections"), 96, TextCase.DEFAULT, "", "Search intersections");
		intersectionSearchField.setChangedListener2(v -> { intersectionSearchQuery = v == null ? "" : v.trim(); entryPage = 0; refreshFilteredIntersections(); refreshButtons(); });

		intersectionNameField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal("Intersection name"), 64, TextCase.DEFAULT, "", "Intersection name");
		intersectionNameField.setChangedListener2(v -> {
			if (!updatingIntersectionNameField && dashboardSection == DashboardSection.INTERSECTIONS && selectedIntersection() != null)
				sendIntersectionUpdate("name", 0, v == null ? "" : v);
		});

		buttonEntryPageUp   = btn("<", () -> { entryPage = Math.max(0, entryPage - 1);                   refreshButtons(); });
		buttonEntryPageDown = btn(">", () -> { entryPage = Math.min(maxEntryPage(), entryPage + 1);       refreshButtons(); });
		buttonVehiclePageUp    = btn("<", () -> { vehiclePage = Math.max(0, vehiclePage - 1);             refreshButtons(); });
		buttonVehiclePageDown  = btn(">", () -> { vehiclePage = Math.min(maxVehiclePage(), vehiclePage + 1); refreshButtons(); });
		buttonSelectedVehiclePageUp   = btn("<", () -> { selectedVehiclePage = Math.max(0, selectedVehiclePage - 1);                        refreshButtons(); });
		buttonSelectedVehiclePageDown = btn(">", () -> { selectedVehiclePage = Math.min(maxSelectedVehiclePage(), selectedVehiclePage + 1); refreshButtons(); });

		buttonOpenVehiclePool = btn("Vehicle Pool ...", () -> openVehiclePool());
		buttonBackToOverview  = btn("← Back",      () -> { panelMode = PanelMode.OVERVIEW; layoutWidgets(); refreshButtons(); });
		buttonRefresh         = btn("Refresh Routes",   () -> sendRefresh());
		buttonClearVehicles   = btn("Clear Active",     () -> sendClearVehicles());
		buttonToggleEnabled   = btn("Enable",           () -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) sendIntersectionUpdate("enabled", 0, null);
			else sendUpdate("enabled", 0, null);
		});
		buttonGroupMinus = btn("-", () -> sendUpdate("group", -1, null));
		buttonGroupPlus  = btn("+", () -> sendUpdate("group",  1, null));
		buttonMaxVehiclesMinus = btn("-", () -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) sendIntersectionUpdate("node_number", -1, selectedIntersectionNode);
			else sendUpdate("max_vehicles", -1, null);
		});
		buttonMaxVehiclesPlus = btn("+", () -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) sendIntersectionUpdate("node_number", 1, selectedIntersectionNode);
			else sendUpdate("max_vehicles", 1, null);
		});
		buttonSpawnIntervalMinus = btn("-", () -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) sendIntersectionUpdate("phase_duration", -20, null);
			else sendUpdate("spawn_interval", -20, null);
		});
		buttonSpawnIntervalPlus = btn("+", () -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) sendIntersectionUpdate("phase_duration", 20, null);
			else sendUpdate("spawn_interval", 20, null);
		});
		buttonTargetGroupMinus = btn("-", () -> sendUpdate("target_group", -1, null));
		buttonTargetGroupPlus  = btn("+", () -> sendUpdate("target_group",  1, null));

		buttonFocus     = btn("Focus Map", () -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS && selectedIntersection() != null) widgetMap.focusOn(selectedIntersection());
			else if (selectedEntry() != null) widgetMap.focusOn(selectedEntry());
		});
		buttonZoomIn    = btn("+", () -> widgetMap.scale(1));
		buttonZoomOut   = btn("-", () -> widgetMap.scale(-1));
		buttonMapTopView   = btn("Top", () -> { widgetMap.setMapOverlayMode(WorldMap.MapOverlayMode.TOP_VIEW);    refreshButtons(); });
		buttonMapCurrentY  = btn("Y",   () -> { widgetMap.setMapOverlayMode(WorldMap.MapOverlayMode.CURRENT_Y);  refreshButtons(); });

		buttonSectionConnectors    = btn("Connectors",    () -> switchSection(DashboardSection.CONNECTORS));
		buttonSectionIntersections = btn("Intersections", () -> switchSection(DashboardSection.INTERSECTIONS));

		buttonAddIntersection = btn("Draw Area", () -> {
			dashboardSection = DashboardSection.INTERSECTIONS;
			panelMode = PanelMode.OVERVIEW;
			drawingIntersection = !drawingIntersection;
			pendingIntersectionCorner = null;
			selectedIntersectionNode = null;
			widgetMap.setCreatingIntersection(drawingIntersection);
			widgetMap.setPendingIntersectionCorner(null);
			layoutWidgets(); refreshButtons();
		});
		buttonDeleteIntersection    = btn("Del Area",     () -> sendIntersectionUpdate("delete", 0, null));
		buttonIntersectionSignalMode     = btn("Mode",       () -> sendIntersectionUpdate("signal_mode", 0, null));
		buttonAutoDetectIntersection     = btn("Find Nodes", () -> sendIntersectionUpdate("find_nodes",  0, null));
		buttonIntersectionGroupAdd       = btn("+ Group",    () -> {
			final ClientTrafficIntersectionEntry it = selectedIntersection();
			selectedPhaseIndex = it == null ? 0 : effectiveGroups(it).size();
			sendIntersectionUpdate("group_add", 0, null);
		});
		buttonIntersectionGroupPrevious = btn("< Prev",   () -> { selectedPhaseIndex = Math.max(0, selectedPhaseIndex - 1);
		selectedIntersectionNode = null; refreshButtons(); });
		buttonIntersectionGroupNext     = btn("Next >",   () -> {
			final ClientTrafficIntersectionEntry it = selectedIntersection();
			if (it != null) { selectedPhaseIndex = Math.min(Math.max(0, effectiveGroups(it).size() - 1), selectedPhaseIndex + 1); selectedIntersectionNode = null; }
			refreshButtons();
		});
		buttonToggleIntersectionNodeType = btn("IN/OUT",        () -> sendIntersectionUpdate("node_type",   0, selectedIntersectionNode));
		buttonIntersectionNodeMinus      = btn("# -",           () -> sendIntersectionUpdate("node_number", -1, selectedIntersectionNode));
		buttonIntersectionNodePlus       = btn("# +",           () -> sendIntersectionUpdate("node_number",  1, selectedIntersectionNode));
		buttonIntersectionNodeDelete     = btn("Del Node",      () -> { sendIntersectionUpdate("node_delete", 0, selectedIntersectionNode);
		selectedIntersectionNode = null; refreshButtons(); });
		buttonIntersectionPhaseMinus = btn("-", () -> sendIntersectionUpdate("phase_duration", -20, String.valueOf(selectedPhaseIndex)));
		buttonIntersectionPhasePlus  = btn("+", () -> sendIntersectionUpdate("phase_duration",  20, String.valueOf(selectedPhaseIndex)));
		buttonIntersectionPhaseAdd    = btn("+ Assign",   () -> { final Integer n = selectedNodeNumber(); if (n != null) sendIntersectionUpdate("phase_assign",  n, String.valueOf(selectedPhaseIndex)); });
		buttonIntersectionPhaseRemove = btn("- Remove",   () -> { final Integer n = selectedNodeNumber(); sendIntersectionUpdate("phase_remove", n == null ? 0 : n, String.valueOf(selectedPhaseIndex)); });
		buttonIntersectionPhaseUp   = btn("▲ Up",   () -> sendIntersectionUpdate("phase_move", -1, String.valueOf(selectedPhaseIndex)));
		buttonIntersectionPhaseDown = btn("▼ Down", () -> sendIntersectionUpdate("phase_move",  1, String.valueOf(selectedPhaseIndex)));

		buttonToggleMap = btn("Map ►", () -> { mapVisibleInNarrow = !mapVisibleInNarrow; layoutWidgets(); refreshButtons(); });

		for (int i = 0; i < GROUP_LIST_ROWS; i++) {
			final int idx = i;
			intersectionGroupButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), b -> {
				final ClientTrafficIntersectionEntry it = selectedIntersection();
				if (it != null && idx < effectiveGroups(it).size()) { selectedPhaseIndex = idx; refreshButtons(); }
			}));
			intersectionGroupDeleteBtns.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal("x"), b -> {
				final ClientTrafficIntersectionEntry it = selectedIntersection();
				if (it != null && idx < effectiveGroups(it).size()) { selectedPhaseIndex = idx; sendIntersectionUpdate("phase_remove", 0, String.valueOf(idx)); }
			}));
		}

		for (int i = 0; i < MAX_LIST_ROWS; i++) {
			final int idx = i;
			entryButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), b -> {
				final int ei = entryPage * visibleListRows() + idx;
				if (dashboardSection == DashboardSection.INTERSECTIONS) {
					if (ei < filteredIntersections.size()) {
						final ClientTrafficIntersectionEntry it = filteredIntersections.get(ei);
						selectedIntersectionIndex = intersections.indexOf(it);
						selectedIntersectionNode = null; pendingIntersectionCorner = null;
						drawingIntersection = false; selectedPhaseIndex = 0;
						widgetMap.setCreatingIntersection(false); widgetMap.setPendingIntersectionCorner(null);
						widgetMap.focusOn(it);
						syncIntersectionNameField(); layoutWidgets(); refreshButtons();
					}
				} else if (ei < entries.size()) {
					selectedIndex = ei;
					widgetMap.focusOn(entries.get(ei));
					if (entries.get(ei).type().name().equals("SPAWN")) openVehiclePool();
					else { panelMode = PanelMode.OVERVIEW; layoutWidgets(); }
					refreshButtons();
				}
			}));
		}

		for (int i = 0; i < SELECTED_VEH_ROWS; i++) {
			final int idx = i;
			selectedVehicleButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), b -> {
				final ClientTrafficDashboardEntry entry = selectedEntry();
				if (entry == null || !entry.type().name().equals("SPAWN")) return;
				final List<String> pool = entry.effectiveVehiclePool();
				final int vi = selectedVehiclePage * SELECTED_VEH_ROWS + idx;
				if (vi < pool.size()) sendUpdate("vehicle_pool_toggle", 0, pool.get(vi));
			}));
		}
		for (int i = 0; i < AVAIL_VEH_PER_PAGE; i++) {
			final int idx = i;
			vehicleButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), b -> {
				final int vi = vehiclePage * AVAIL_VEH_PER_PAGE + idx;
				if (vi < filteredVehicleOptions.size() && selectedEntry() != null && selectedEntry().type().name().equals("SPAWN"))
					sendUpdate("vehicle_pool_toggle", 0, filteredVehicleOptions.get(vi).id());
			}));
		}

		reloadVehicleOptions();
		updateEntries(entries, intersections);
	}

	// ── Helper ────────────────────────────────────────────────────────────────
	private static ButtonWidgetExtension btn(String label, Runnable action) {
		return new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal(label), b -> action.run());
	}

	private void switchSection(DashboardSection section) {
		dashboardSection = section;
		panelMode = PanelMode.OVERVIEW;
		pendingIntersectionCorner = null;
		drawingIntersection = false;
		selectedIntersectionNode = null;
		widgetMap.setCreatingIntersection(false);
		widgetMap.setPendingIntersectionCorner(null);
		syncIntersectionNameField();
		layoutWidgets();
		refreshButtons();
	}

	// ── Entry updates ─────────────────────────────────────────────────────────
	public void updateEntries(List<ClientTrafficDashboardEntry> updatedEntries, List<ClientTrafficIntersectionEntry> updatedIntersections) {
		entries.clear();    entries.addAll(updatedEntries);
		intersections.clear(); intersections.addAll(updatedIntersections);
		refreshFilteredIntersections();
		if (!entries.isEmpty() && (selectedIndex < 0 || selectedIndex >= entries.size())) selectedIndex = 0;
		selectedIndex = Math.min(selectedIndex, Math.max(entries.size() - 1, 0));
		selectedIntersectionIndex = Math.min(selectedIntersectionIndex, Math.max(intersections.size() - 1, 0));
		final ClientTrafficIntersectionEntry selIntersection = selectedIntersection();
		if (selIntersection != null) {
			final int gc = effectiveGroups(selIntersection).size();
			if (gc > 0 && (selectedPhaseIndex < 0 || selectedPhaseIndex >= gc)) selectedPhaseIndex = 0;
			if (selectedIntersectionNode != null && !containsNode(selIntersection, selectedIntersectionNode)) selectedIntersectionNode = null;
		} else {
			selectedIntersectionNode = null;
		}
		entryPage         = Math.min(entryPage, maxEntryPage());
		vehiclePage       = Math.min(vehiclePage, maxVehiclePage());
		selectedVehiclePage = Math.min(selectedVehiclePage, maxSelectedVehiclePage());
		refreshFilteredVehicleOptions();
		syncIntersectionNameField();
		refreshButtons();
	}

	// ── init2 ─────────────────────────────────────────────────────────────────
	@Override
	protected void init2() {
		super.init2();
		layoutWidgets();

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
		addChild(new ClickableWidget(buttonIntersectionSignalMode));
		addChild(new ClickableWidget(buttonAutoDetectIntersection));
		addChild(new ClickableWidget(buttonIntersectionGroupAdd));
		addChild(new ClickableWidget(buttonIntersectionGroupPrevious));
		addChild(new ClickableWidget(buttonIntersectionGroupNext));
		addChild(new ClickableWidget(buttonToggleIntersectionNodeType));
		addChild(new ClickableWidget(buttonIntersectionNodeMinus));
		addChild(new ClickableWidget(buttonIntersectionNodePlus));
		addChild(new ClickableWidget(buttonIntersectionNodeDelete));
		addChild(new ClickableWidget(buttonIntersectionPhaseMinus));
		addChild(new ClickableWidget(buttonIntersectionPhasePlus));
		addChild(new ClickableWidget(buttonIntersectionPhaseAdd));
		addChild(new ClickableWidget(buttonIntersectionPhaseRemove));
		addChild(new ClickableWidget(buttonIntersectionPhaseUp));
		addChild(new ClickableWidget(buttonIntersectionPhaseDown));
		addChild(new ClickableWidget(buttonToggleMap));
		addChild(new ClickableWidget(vehicleSearchField));
		addChild(new ClickableWidget(intersectionSearchField));
		addChild(new ClickableWidget(intersectionNameField));
		entryButtons.forEach(b -> addChild(new ClickableWidget(b)));
		intersectionGroupButtons.forEach(b -> addChild(new ClickableWidget(b)));
		intersectionGroupDeleteBtns.forEach(b -> addChild(new ClickableWidget(b)));
		selectedVehicleButtons.forEach(b -> addChild(new ClickableWidget(b)));
		vehicleButtons.forEach(b -> addChild(new ClickableWidget(b)));

		refreshButtons();
	}

	// ── Layout dispatch ───────────────────────────────────────────────────────
	private void layoutWidgets() {
		if (panelMode == PanelMode.VEHICLE_POOL) {
			layoutVehiclePoolWidgets();
		} else {
			layoutOverviewWidgets();
		}
	}

	// ── Overview layout ───────────────────────────────────────────────────────
	private void layoutOverviewWidgets() {
		final boolean narrowMap = isNarrowMode() && mapVisibleInNarrow;
		final int panelW = leftPanelWidth();
		final int mapX   = narrowMap ? 0 : panelW;
		final int mapW   = narrowMap ? width : Math.max(0, width - panelW);
		widgetMap.setPositionAndSize(mapX, 0, mapW, height);

		if (narrowMap) return;

		final int cw = panelW - MARGIN * 2;

		final int tabW  = (cw - GAP) / 2;
		final int tabW3 = (cw - GAP * 2) / 3;
		IDrawing.setPositionAndWidth(buttonSectionConnectors,    MARGIN, TAB_Y, tabW);
		IDrawing.setPositionAndWidth(buttonSectionIntersections, MARGIN + tabW + GAP, TAB_Y, cw - tabW - GAP);
		if (dashboardSection == DashboardSection.INTERSECTIONS) {
			IDrawing.setPositionAndWidth(buttonSectionConnectors,    MARGIN, TAB_Y, tabW3);
			IDrawing.setPositionAndWidth(buttonSectionIntersections, MARGIN + tabW3 + GAP, TAB_Y, tabW3);
			IDrawing.setPositionAndWidth(buttonAddIntersection,      MARGIN + (tabW3 + GAP) * 2, TAB_Y, cw - (tabW3 + GAP) * 2);
		}

		IDrawing.setPositionAndWidth(buttonToggleMap, panelW - SQUARE_SIZE - 4, 4, SQUARE_SIZE);

		intersectionSearchField.setX2(MARGIN);
		intersectionSearchField.setY2(LIST_START_Y - 20);
		intersectionSearchField.setWidth2(dashboardSection == DashboardSection.INTERSECTIONS ? ilWidth(cw) : cw);

		final int rows   = visibleListRows();
		final int listW  = dashboardSection == DashboardSection.INTERSECTIONS ? ilWidth(cw) : cw;
		int y = LIST_START_Y;
		for (int i = 0; i < MAX_LIST_ROWS; i++) {
			IDrawing.setPositionAndWidth(entryButtons.get(i), MARGIN, y, listW);
			y += ROW_H;
		}

		y = LIST_START_Y + rows * ROW_H + 2;
		IDrawing.setPositionAndWidth(buttonEntryPageUp,   MARGIN,                    y, (listW - GAP) / 2);
		IDrawing.setPositionAndWidth(buttonEntryPageDown, MARGIN + (listW + GAP) / 2, y, (listW - GAP) / 2);

		final int detailsY = y + SQUARE_SIZE + GAP + 4;

		if (dashboardSection == DashboardSection.INTERSECTIONS) {
			final int dX = idX(cw);
			final int dW = idWidth(cw);
			intersectionNameField.setX2(dX);
			intersectionNameField.setY2(LIST_START_Y + 36);
			intersectionNameField.setWidth2(dW);
			layoutIntersectionGroupWidgets(dX, LIST_START_Y + 158, dW);
			layoutIntersectionWidgets(dX, LIST_START_Y + 158 + GROUP_LIST_ROWS * ROW_H + GAP, dW);
		} else {
			layoutConnectorWidgets(detailsY);
		}

		final int bx = width - SQUARE_SIZE - 8;
		final int by = height - SQUARE_SIZE - 8;
		IDrawing.setPositionAndWidth(buttonZoomIn,      bx - SQUARE_SIZE - 4, by - SQUARE_SIZE - 4, SQUARE_SIZE);
		IDrawing.setPositionAndWidth(buttonZoomOut,     bx,                   by - SQUARE_SIZE - 4, SQUARE_SIZE);
		IDrawing.setPositionAndWidth(buttonMapCurrentY, bx - SQUARE_SIZE - 4, by,                   SQUARE_SIZE);
		IDrawing.setPositionAndWidth(buttonMapTopView,  bx,                   by,                   SQUARE_SIZE);
	}

	// ── Connector controls ────────────────────────────────────────────────────
	private void layoutConnectorWidgets(int y) {
		final int x  = MARGIN;
		final int cw = leftPanelWidth() - MARGIN * 2;
		final int half  = (cw - GAP) / 2;
		final int third = (cw - GAP * 2) / 3;
		final ClientTrafficDashboardEntry entry = selectedEntry();
		final boolean isSpawn = entry != null && entry.type().name().equals("SPAWN");

		if (isSpawn) {
			IDrawing.setPositionAndWidth(buttonToggleEnabled,  x,                        y, third);
			IDrawing.setPositionAndWidth(buttonFocus,          x + third + GAP,           y, third);
			IDrawing.setPositionAndWidth(buttonOpenVehiclePool, x + (third + GAP) * 2,   y, cw - (third + GAP) * 2);
		} else {
			IDrawing.setPositionAndWidth(buttonToggleEnabled, x,            y, half);
			IDrawing.setPositionAndWidth(buttonFocus,         x + half + GAP, y, cw - half - GAP);
		}
		y += SQUARE_SIZE + GAP;

		IDrawing.setPositionAndWidth(buttonRefresh,       x,            y, half);
		IDrawing.setPositionAndWidth(buttonClearVehicles, x + half + GAP, y, cw - half - GAP);
		y += SQUARE_SIZE + GAP;

		if (isSpawn) {
			spawnIntervalRowY = y;
			IDrawing.setPositionAndWidth(buttonSpawnIntervalMinus, x,                      y, INLINE_BTN_W);
			IDrawing.setPositionAndWidth(buttonSpawnIntervalPlus,  x + cw - INLINE_BTN_W, y, INLINE_BTN_W);
		}
	}

	// ── Intersection group list ────────────────────────────────────────────────
	private void layoutIntersectionGroupWidgets(int x, int y, int w) {
		final int delW = 22;
		for (int i = 0; i < GROUP_LIST_ROWS; i++) {
			IDrawing.setPositionAndWidth(intersectionGroupButtons.get(i),    x,         y, w - delW - GAP);
			IDrawing.setPositionAndWidth(intersectionGroupDeleteBtns.get(i), x + w - delW, y, delW);
			y += ROW_H;
		}
	}

	// ── Intersection action buttons ────────────────────────────────────────────
	private void layoutIntersectionWidgets(int x, int y, int w) {
		final int third  = (w - GAP * 2) / 3;
		final int quarter = (w - GAP * 3) / 4;

		IDrawing.setPositionAndWidth(buttonToggleEnabled,        x,                    y, third);
		IDrawing.setPositionAndWidth(buttonFocus,                x + third + GAP,       y, third);
		IDrawing.setPositionAndWidth(buttonDeleteIntersection,   x + (third + GAP) * 2, y, w - (third + GAP) * 2);
		y += SQUARE_SIZE + GAP;

		IDrawing.setPositionAndWidth(buttonIntersectionSignalMode,    x,                    y, third);
		IDrawing.setPositionAndWidth(buttonAutoDetectIntersection,    x + third + GAP,       y, third);
		IDrawing.setPositionAndWidth(buttonIntersectionGroupAdd,      x + (third + GAP) * 2, y, w - (third + GAP) * 2);
		y += SQUARE_SIZE + GAP;

		phaseDurRowY = y;
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseMinus, x,                 y, INLINE_BTN_W);
		IDrawing.setPositionAndWidth(buttonIntersectionPhasePlus,  x + w - INLINE_BTN_W, y, INLINE_BTN_W);
		y += SQUARE_SIZE + GAP;

		IDrawing.setPositionAndWidth(buttonIntersectionPhaseAdd,    x,                    y, third);
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseRemove, x + third + GAP,       y, third);
		final int udW = (w - (third + GAP) * 2 - GAP) / 2;
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseUp,   x + (third + GAP) * 2,          y, udW);
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseDown, x + (third + GAP) * 2 + udW + GAP, y, w - (third + GAP) * 2 - udW - GAP);
		y += SQUARE_SIZE + GAP;

		IDrawing.setPositionAndWidth(buttonToggleIntersectionNodeType, x,                     y, quarter);
		IDrawing.setPositionAndWidth(buttonIntersectionNodeDelete,     x + quarter + GAP,      y, quarter);
		IDrawing.setPositionAndWidth(buttonIntersectionNodeMinus,      x + (quarter + GAP) * 2, y, quarter);
		IDrawing.setPositionAndWidth(buttonIntersectionNodePlus,       x + (quarter + GAP) * 3, y, w - (quarter + GAP) * 3);
	}

	// ── Vehicle pool layout ────────────────────────────────────────────────────
	private void layoutVehiclePoolWidgets() {
		widgetMap.setPositionAndSize(width, 0, 0, 0);

		final int lw   = vehiclePoolListWidth();
		final int lx   = vehiclePoolLeftX();
		final int rx   = vehiclePoolRightX();
		final int listY = vehiclePoolListY();
		final int pageY = Math.min(height - 68, listY + SELECTED_VEH_ROWS * ROW_H + 8);

		IDrawing.setPositionAndWidth(buttonBackToOverview,        (width - 144) / 2, height - 40, 144);
		IDrawing.setPositionAndWidth(buttonVehiclePageUp,         lx,                    pageY, (lw - GAP) / 2);
		IDrawing.setPositionAndWidth(buttonVehiclePageDown,       lx + (lw + GAP) / 2,   pageY, (lw - GAP) / 2);
		IDrawing.setPositionAndWidth(buttonSelectedVehiclePageUp,   rx,                  pageY, (lw - GAP) / 2);
		IDrawing.setPositionAndWidth(buttonSelectedVehiclePageDown, rx + (lw + GAP) / 2, pageY, (lw - GAP) / 2);

		vehicleSearchField.setX2(lx);
		vehicleSearchField.setY2(54);
		vehicleSearchField.setWidth(lw);

		int y = listY;
		for (ButtonWidgetExtension vb : vehicleButtons)         { IDrawing.setPositionAndWidth(vb, lx, y, lw); y += ROW_H; }
		y = listY;
		for (ButtonWidgetExtension sb : selectedVehicleButtons) { IDrawing.setPositionAndWidth(sb, rx, y, lw); y += ROW_H; }
	}

	// ── Render ────────────────────────────────────────────────────────────────
	@Override
	public void render(GraphicsHolder gh, int mouseX, int mouseY, float delta) {
		final boolean narrowMap = isNarrowMode() && mapVisibleInNarrow;
		if (panelMode != PanelMode.VEHICLE_POOL && !narrowMap) {
			widgetMap.render(gh, mouseX, mouseY, delta);
		} else if (narrowMap) {
			widgetMap.render(gh, mouseX, mouseY, delta);
		}

		gh.push();
		gh.translate(0, 0, 500);
		final GuiDrawing gd = new GuiDrawing(gh);

		if (narrowMap) {
			super.render(gh, mouseX, mouseY, delta);
			gh.pop();
			return;
		}

		final int panelW = panelMode == PanelMode.VEHICLE_POOL ? width : leftPanelWidth();
		gd.beginDrawingRectangle();
		gd.drawRectangle(0, 0, panelW, height, ARGB_BACKGROUND);
		if (panelMode != PanelMode.VEHICLE_POOL) {
			gd.drawRectangle(panelW, 0, panelW + 1, height, C_DIVIDER);
		}
		gd.finishDrawingRectangle();

		if (panelMode == PanelMode.VEHICLE_POOL) {
			renderVehiclePoolScreen(gh);
		} else {
			renderOverviewPanel(gh, gd);
		}

		super.render(gh, mouseX, mouseY, delta);
		gh.pop();
	}

	private void renderOverviewPanel(GraphicsHolder gh, GuiDrawing gd) {
		gh.drawText("Traffic Dashboard", MARGIN, TITLE_Y, C_WHITE, false, GraphicsHolder.getDefaultLight());
		gh.drawText(headerHint(), MARGIN, HINT_Y, C_HINT, false, GraphicsHolder.getDefaultLight());

		gd.beginDrawingRectangle();
		gd.drawRectangle(MARGIN, TAB_Y + SQUARE_SIZE + 2, leftPanelWidth() - MARGIN, TAB_Y + SQUARE_SIZE + 3, C_DIVIDER);
		gd.finishDrawingRectangle();

		if (dashboardSection == DashboardSection.INTERSECTIONS) {
			renderIntersectionPanel(gh, gd);
		} else {
			renderConnectorPanel(gh);
		}
	}

	// ── Connector panel ────────────────────────────────────────────────────────
	private void renderConnectorPanel(GraphicsHolder gh) {
		final int cw = leftPanelWidth() - MARGIN * 2;
		final int rows = visibleListRows();

		gh.drawText("Connectors  (" + entries.size() + ")", MARGIN, LIST_START_Y - 11, C_SECTION, false, GraphicsHolder.getDefaultLight());

		if (entries.isEmpty()) {
			gh.drawText("No connectors found.", MARGIN, LIST_START_Y + 4, C_WARN, false, GraphicsHolder.getDefaultLight());
			gh.drawText("Place Spawn/Despawn items on MTR rails.", MARGIN, LIST_START_Y + 16, C_MUTED, false, GraphicsHolder.getDefaultLight());
			return;
		}

		final ClientTrafficDashboardEntry entry = selectedEntry();
		if (entry == null) return;

		final int detailsY = LIST_START_Y + rows * ROW_H + 28 + SQUARE_SIZE + GAP + 4;
		gh.drawText("Selected: " + (entry.type().name().equals("SPAWN") ? "Spawn Connector" : "Despawn Connector"),
			MARGIN, detailsY - 12, C_SECTION, false, GraphicsHolder.getDefaultLight());

		int y = detailsY;
		final boolean ok = entry.enabled() && entry.hasConnectorRoute();
		final String stateStr = entry.enabled() ? "● Enabled" : "○ Disabled";
		final String routeStr = entry.hasConnectorRoute() ? "  Route: ● Ready" : "  Route: ○ Missing";
		gh.drawText(stateStr, MARGIN, y, entry.enabled() ? C_OK : C_WARN, false, GraphicsHolder.getDefaultLight());
		gh.drawText(routeStr, MARGIN + 72, y, entry.hasConnectorRoute() ? C_OK : C_WARN, false, GraphicsHolder.getDefaultLight());
		y += 12;
		gh.drawText("Active: " + entry.activeVehicles() + " vehicles", MARGIN, y, ok ? C_OK : C_MUTED, false, GraphicsHolder.getDefaultLight());
		y += 12;
		gh.drawText("Position: " + entry.blockPos().getX() + ", " + entry.blockPos().getY() + ", " + entry.blockPos().getZ(),
			MARGIN, y, C_MUTED, false, GraphicsHolder.getDefaultLight());
		y += 12;

		if (entry.type().name().equals("SPAWN")) {
			final int missing = countMissingPoolEntries(entry);
			gh.drawText("Pool: " + entry.effectiveVehiclePool().size() + " selected" + (missing > 0 ? "  !" + missing + " missing" : ""),
				MARGIN, y, missing > 0 ? C_WARN : C_MUTED, false, GraphicsHolder.getDefaultLight());

			if (spawnIntervalRowY > 0) {
				gh.drawText("Spawn interval", MARGIN, spawnIntervalRowY + 5, C_MUTED, false, GraphicsHolder.getDefaultLight());
				final String intervalVal = String.format("%.1fs", entry.spawnIntervalTicks() / 20.0);
				final int valW = GraphicsHolder.getTextWidth(intervalVal);
				final int valX = MARGIN + INLINE_BTN_W + (cw - INLINE_BTN_W * 2 - valW) / 2;
				gh.drawText(intervalVal, valX, spawnIntervalRowY + 5, C_WHITE, false, GraphicsHolder.getDefaultLight());
			}
		}
	}

	// ── Intersection panel ─────────────────────────────────────────────────────
	private void renderIntersectionPanel(GraphicsHolder gh, GuiDrawing gd) {
		final int cw    = leftPanelWidth() - MARGIN * 2;
		final int listW = ilWidth(cw);
		final int dX    = idX(cw);
		final int dW    = idWidth(cw);

		gd.beginDrawingRectangle();
		gd.drawRectangle(dX - 5, LIST_START_Y - 12, dX - 4, height - MARGIN, C_DIVIDER);
		gd.finishDrawingRectangle();

		gh.drawText("Intersections  (" + filteredIntersections.size() + "/" + intersections.size() + ")",
			MARGIN, LIST_START_Y - 30, C_SECTION, false, GraphicsHolder.getDefaultLight());

		if (drawingIntersection) {
			gh.drawText("Drawing Area", dX, LIST_START_Y, C_WARN, false, GraphicsHolder.getDefaultLight());
			gh.drawText(pendingIntersectionCorner == null
				? "Click first corner on the map."
				: "First: " + pendingIntersectionCorner.getX() + "," + pendingIntersectionCorner.getZ() + "  Now click opposite corner.",
				dX, LIST_START_Y + 12, C_MUTED, false, GraphicsHolder.getDefaultLight());
			return;
		}

		final ClientTrafficIntersectionEntry it = selectedIntersection();
		if (it == null) {
			gh.drawText("No intersection selected.", dX, LIST_START_Y, C_MUTED, false, GraphicsHolder.getDefaultLight());
			gh.drawText("Click Draw Area, then pick two corners.", dX, LIST_START_Y + 12, C_MUTED, false, GraphicsHolder.getDefaultLight());
			return;
		}

		int y = LIST_START_Y;
		gh.drawText(shorten(it.effectiveName(), 28), dX, y, it.enabled() ? C_WHITE : C_WARN, false, GraphicsHolder.getDefaultLight());
		y += 11;
		gh.drawText((it.enabled() ? "● Enabled" : "○ Disabled"), dX, y, it.enabled() ? C_OK : C_WARN, false, GraphicsHolder.getDefaultLight());
		gh.drawText("  " + it.nodes().size() + " nodes", dX + 68, y, C_MUTED, false, GraphicsHolder.getDefaultLight());
		y += 11;
		gh.drawText("Area: " + it.minX() + "," + it.minZ() + " → " + it.maxX() + "," + it.maxZ(), dX, y, C_MUTED, false, GraphicsHolder.getDefaultLight());
		y += 11;
		gh.drawText("Name:", dX, y + 6, C_MUTED, false, GraphicsHolder.getDefaultLight());
		y = LIST_START_Y + 56;

		gh.drawText("Signal mode: " + (it.signalMode() == TrafficIntersectionSignalMode.AUTO ? "Auto" : "Manual"),
			dX, y, C_MUTED, false, GraphicsHolder.getDefaultLight());
		y += 12;
		gh.drawText("Signal Groups", dX, LIST_START_Y + 146, C_SECTION, false, GraphicsHolder.getDefaultLight());

		final List<TrafficIntersectionGroup> groups = effectiveGroups(it);
		final TrafficIntersectionGroup selGroup = selectedGroup(it);
		if (selGroup != null) {
			gh.drawText("Group " + (selectedPhaseIndex + 1) + ": " + shorten(selGroup.name(), 16) + "  nodes: " + selGroup.nodeNumbers(),
				dX, LIST_START_Y + 146 + GROUP_LIST_ROWS * ROW_H + 6, C_SECTION, false, GraphicsHolder.getDefaultLight());

			if (phaseDurRowY > 0) {
				gh.drawText("Green duration", dX, phaseDurRowY + 5, C_MUTED, false, GraphicsHolder.getDefaultLight());
				final String durVal = String.format("%.1fs", selGroup.effectiveGreenDurationTicks() / 20.0);
				final int valW = GraphicsHolder.getTextWidth(durVal);
				final int valCenterX = dX + INLINE_BTN_W + (dW - INLINE_BTN_W * 2 - valW) / 2;
				gh.drawText(durVal, valCenterX, phaseDurRowY + 5, C_OK, false, GraphicsHolder.getDefaultLight());
			}
		}

		final String nodeLabel = selectedNodeLabel(it);
		if (selectedIntersectionNode != null) {
			final int nx = dX;
			final int ny = height - 60;
			gh.drawText("Node: " + shorten(nodeLabel, 38), nx, ny, C_SECTION, false, GraphicsHolder.getDefaultLight());
		}
	}

	// ── Vehicle pool screen ────────────────────────────────────────────────────
	private void renderVehiclePoolScreen(GraphicsHolder gh) {
		final ClientTrafficDashboardEntry entry = selectedEntry();
		final int lw = vehiclePoolListWidth();
		final int lx = vehiclePoolLeftX();
		final int rx = vehiclePoolRightX();
		final int top = 34;
		if (entry == null || !entry.type().name().equals("SPAWN")) {
			drawCenteredText(gh, "Select a spawn connector first.", width / 2, top, C_WHITE);
			return;
		}
		drawCenteredText(gh, "Vehicle Pool  —  " + shortEntryLabel(entry), width / 2, 10, C_WHITE);
		drawCenteredText(gh, "Available",    lx + lw / 2, top, C_SECTION);
		drawCenteredText(gh, "Selected",     rx + lw / 2, top, C_SECTION);
		gh.drawText(filteredVehicleOptions.size() + " vehicles  (page " + (vehiclePage + 1) + "/" + (maxVehiclePage() + 1) + ")",
			lx, top + 12, C_MUTED, false, GraphicsHolder.getDefaultLight());
		gh.drawText(entry.effectiveVehiclePool().size() + " selected  (page " + (selectedVehiclePage + 1) + "/" + (maxSelectedVehiclePage() + 1) + ")",
			rx, top + 12, C_MUTED, false, GraphicsHolder.getDefaultLight());
		if (filteredVehicleOptions.isEmpty())
			gh.drawText("No vehicles match the search.", lx, vehiclePoolListY(), C_WARN, false, GraphicsHolder.getDefaultLight());
		if (entry.effectiveVehiclePool().isEmpty())
			gh.drawText("Nothing selected yet. Click a vehicle on the left.", rx, vehiclePoolListY(), C_MUTED, false, GraphicsHolder.getDefaultLight());
	}

	// ── refreshButtons ────────────────────────────────────────────────────────
	@Override
	public void tick2() {
		reloadVehicleOptions();
		refreshButtons();
	}

	private void refreshButtons() {
		if (entryButtons.isEmpty()) return;

		final ClientTrafficDashboardEntry entry = selectedEntry();
		final boolean hasEntry = entry != null;
		final ClientTrafficIntersectionEntry it = selectedIntersection();
		final boolean hasIntersection = it != null;
		final boolean isSpawn = hasEntry && entry.type().name().equals("SPAWN");
		final boolean vehiclePoolMode = panelMode == PanelMode.VEHICLE_POOL;
		final boolean narrowMap = isNarrowMode() && mapVisibleInNarrow;

		if (vehiclePoolMode || dashboardSection != DashboardSection.INTERSECTIONS) {
			drawingIntersection = false;
			pendingIntersectionCorner = null;
		}
		widgetMap.setCreatingIntersection(drawingIntersection && dashboardSection == DashboardSection.INTERSECTIONS && !vehiclePoolMode);
		widgetMap.setPendingIntersectionCorner(pendingIntersectionCorner);

		final boolean iMode = dashboardSection == DashboardSection.INTERSECTIONS && !vehiclePoolMode && !narrowMap;
		final boolean cMode = dashboardSection == DashboardSection.CONNECTORS    && !vehiclePoolMode && !narrowMap;

		final int rows = visibleListRows();
		for (int i = 0; i < MAX_LIST_ROWS; i++) {
			final int ei = entryPage * rows + i;
			final ButtonWidgetExtension b = entryButtons.get(i);
			if (narrowMap || vehiclePoolMode || i >= rows) {
				b.visible = false; b.active = false; b.setMessage(Component.literal("")); continue;
			}
			if (iMode && ei < filteredIntersections.size()) {
				final ClientTrafficIntersectionEntry li = filteredIntersections.get(ei);
				final int si = intersections.indexOf(li);
				b.visible = true; b.active = true;
				b.setMessage(Component.literal(
					(si == selectedIntersectionIndex ? "► " : "  ")
					+ shorten(li.effectiveName(), 18)
					+ "  " + modeLabel(li)
					+ "  " + (li.enabled() ? "●" : "○")
					+ "  " + li.nodes().size() + "n"));
			} else if (cMode && ei < entries.size()) {
				final ClientTrafficDashboardEntry le = entries.get(ei);
				b.visible = true; b.active = true;
				b.setMessage(Component.literal(
					(ei == selectedIndex ? "► " : "  ")
					+ (le.type().name().equals("SPAWN") ? "S " : "D ")
					+ le.blockPos().getX() + "," + le.blockPos().getZ()
					+ "  " + (le.enabled() ? "●" : "○")
					+ "  " + le.activeVehicles() + "v"
					+ "  " + (le.hasConnectorRoute() ? "OK" : "??")));
			} else {
				b.visible = false; b.active = false; b.setMessage(Component.literal(""));
			}
		}

		final List<TrafficIntersectionGroup> groups = it == null ? List.of() : effectiveGroups(it);
		for (int i = 0; i < GROUP_LIST_ROWS; i++) {
			final ButtonWidgetExtension gb  = intersectionGroupButtons.get(i);
			final ButtonWidgetExtension del = intersectionGroupDeleteBtns.get(i);
			if (iMode && i < groups.size()) {
				final TrafficIntersectionGroup g = groups.get(i);
				gb.visible = true; gb.active = true;
				gb.setMessage(Component.literal(
					(i == selectedPhaseIndex ? "► " : "  ")
					+ (i + 1) + ". " + shorten(g.name(), 10)
					+ "  " + g.nodeNumbers()
					+ "  " + String.format("%.1f", g.effectiveGreenDurationTicks() / 20.0) + "s"));
				del.visible = true; del.active = true;
			} else {
				gb.visible = false; gb.active = false; gb.setMessage(Component.literal(""));
				del.visible = false; del.active = false;
			}
		}

		buttonEntryPageUp.active   = entryPage > 0;
		buttonEntryPageDown.active = entryPage < maxEntryPage();
		buttonEntryPageUp.visible  = !vehiclePoolMode && !narrowMap;
		buttonEntryPageDown.visible = !vehiclePoolMode && !narrowMap;
		buttonVehiclePageUp.visible   = vehiclePoolMode;
		buttonVehiclePageDown.visible = vehiclePoolMode;
		buttonSelectedVehiclePageUp.visible   = vehiclePoolMode;
		buttonSelectedVehiclePageDown.visible = vehiclePoolMode;
		buttonVehiclePageUp.active   = vehiclePage > 0;
		buttonVehiclePageDown.active = vehiclePage < maxVehiclePage();
		buttonSelectedVehiclePageUp.active   = selectedVehiclePage > 0;
		buttonSelectedVehiclePageDown.active = selectedVehiclePage < maxSelectedVehiclePage();

		final boolean hasNode  = selectedIntersectionNode != null;
		final boolean hasGroup = it != null && selectedGroup(it) != null;

		buttonToggleEnabled.active  = (cMode ? hasEntry : hasIntersection);
		buttonToggleEnabled.visible = !vehiclePoolMode && !narrowMap;
		buttonToggleEnabled.setMessage(Component.literal(
			(dashboardSection == DashboardSection.INTERSECTIONS ? hasIntersection && it.enabled() : hasEntry && entry.enabled()) ? "Disable" : "Enable"));

		buttonFocus.active  = (dashboardSection == DashboardSection.CONNECTORS ? hasEntry : hasIntersection);
		buttonFocus.visible = !vehiclePoolMode && !narrowMap;

		buttonOpenVehiclePool.active  = cMode && isSpawn;
		buttonOpenVehiclePool.visible = cMode && isSpawn;

		buttonRefresh.visible     = cMode;
		buttonClearVehicles.visible = cMode;

		buttonSpawnIntervalMinus.active  = cMode && isSpawn;
		buttonSpawnIntervalMinus.visible = cMode && isSpawn;
		buttonSpawnIntervalPlus.active   = cMode && isSpawn;
		buttonSpawnIntervalPlus.visible  = cMode && isSpawn;

		buttonDeleteIntersection.visible    = iMode;
		buttonDeleteIntersection.active     = iMode && hasIntersection;
		buttonIntersectionSignalMode.visible = iMode;
		buttonIntersectionSignalMode.active  = iMode && hasIntersection;
		buttonIntersectionSignalMode.setMessage(Component.literal(
			it != null && it.signalMode() == TrafficIntersectionSignalMode.AUTO ? "Auto" : "Manual"));
		buttonAutoDetectIntersection.visible = iMode;
		buttonAutoDetectIntersection.active  = iMode && hasIntersection;
		buttonIntersectionGroupAdd.visible   = iMode;
		buttonIntersectionGroupAdd.active    = iMode && hasIntersection;

		buttonIntersectionPhaseMinus.visible = iMode;
		buttonIntersectionPhaseMinus.active  = iMode && hasGroup;
		buttonIntersectionPhasePlus.visible  = iMode;
		buttonIntersectionPhasePlus.active   = iMode && hasGroup;
		buttonIntersectionPhaseAdd.visible    = iMode;
		buttonIntersectionPhaseAdd.active     = iMode && hasGroup && selectedNodeNumber() != null && selectedNodeIsIn();
		buttonIntersectionPhaseRemove.visible = iMode;
		buttonIntersectionPhaseRemove.active  = iMode && hasGroup && hasNode && selectedNodeIsIn();
		buttonIntersectionPhaseUp.visible     = iMode;
		buttonIntersectionPhaseUp.active      = iMode && hasGroup && selectedPhaseIndex > 0;
		buttonIntersectionPhaseDown.visible   = iMode;
		buttonIntersectionPhaseDown.active    = iMode && hasGroup && it != null && selectedPhaseIndex < effectiveGroups(it).size() - 1;

		buttonToggleIntersectionNodeType.visible = iMode;
		buttonToggleIntersectionNodeType.active  = iMode && hasNode;
		buttonIntersectionNodeMinus.visible      = iMode;
		buttonIntersectionNodeMinus.active       = iMode && hasNode;
		buttonIntersectionNodePlus.visible       = iMode;
		buttonIntersectionNodePlus.active        = iMode && hasNode;
		buttonIntersectionNodeDelete.visible     = iMode;
		buttonIntersectionNodeDelete.active      = iMode && hasNode;

		buttonSectionConnectors.visible    = !vehiclePoolMode && !narrowMap;
		buttonSectionIntersections.visible = !vehiclePoolMode && !narrowMap;
		buttonAddIntersection.visible      = !vehiclePoolMode && !narrowMap && dashboardSection == DashboardSection.INTERSECTIONS;
		buttonAddIntersection.setMessage(Component.literal(drawingIntersection ? "Cancel Area" : "Draw Area"));

		buttonSectionConnectors.setMessage(Component.literal(
			(dashboardSection == DashboardSection.CONNECTORS ? "► " : "") + "Connectors (" + entries.size() + ")"));
		buttonSectionIntersections.setMessage(Component.literal(
			(dashboardSection == DashboardSection.INTERSECTIONS ? "► " : "") + "Intersections (" + intersections.size() + ")"));

		buttonZoomIn.visible  = !vehiclePoolMode && !narrowMap;
		buttonZoomOut.visible = !vehiclePoolMode && !narrowMap;
		buttonMapTopView.visible   = !vehiclePoolMode && !narrowMap;
		buttonMapCurrentY.visible  = !vehiclePoolMode && !narrowMap;
		buttonMapTopView.active    = !widgetMap.isMapOverlayMode(WorldMap.MapOverlayMode.TOP_VIEW);
		buttonMapCurrentY.active   = !widgetMap.isMapOverlayMode(WorldMap.MapOverlayMode.CURRENT_Y);

		buttonBackToOverview.visible = vehiclePoolMode;
		buttonBackToOverview.active  = vehiclePoolMode;

		buttonToggleMap.visible = isNarrowMode() && !vehiclePoolMode;
		buttonToggleMap.setMessage(Component.literal(mapVisibleInNarrow ? "← Panel" : "Map ►"));

		intersectionSearchField.setVisible2(iMode);
		intersectionNameField.setVisible2(iMode && hasIntersection);
		intersectionNameField.setActiveMapped(iMode && hasIntersection);
		vehicleSearchField.setVisible(vehiclePoolMode);

		final List<String> selectedPool = entry == null ? List.of() : entry.effectiveVehiclePool();
		for (int i = 0; i < SELECTED_VEH_ROWS; i++) {
			final int vi = selectedVehiclePage * SELECTED_VEH_ROWS + i;
			final ButtonWidgetExtension b = selectedVehicleButtons.get(i);
			if (vehiclePoolMode && isSpawn && vi < selectedPool.size()) {
				final String vid = selectedPool.get(vi);
				final VehicleOption vo = findVehicleOption(vid);
				b.visible = true; b.active = true;
				b.setMessage(Component.literal("[-] " + shorten(vo == null ? vid : vo.label(), 42)));
			} else { b.visible = false; b.active = false; b.setMessage(Component.literal("")); }
		}
		for (int i = 0; i < AVAIL_VEH_PER_PAGE; i++) {
			final int vi = vehiclePage * AVAIL_VEH_PER_PAGE + i;
			final ButtonWidgetExtension b = vehicleButtons.get(i);
			if (vehiclePoolMode && isSpawn && vi < filteredVehicleOptions.size()) {
				final VehicleOption vo = filteredVehicleOptions.get(vi);
				b.visible = true; b.active = true;
				b.setMessage(Component.literal((selectedPool.contains(vo.id()) ? "[x] " : "[+] ") + shorten(vo.label(), 62)));
			} else { b.visible = false; b.active = false; b.setMessage(Component.literal("")); }
		}

		buttonGroupMinus.visible       = false;
		buttonGroupPlus.visible        = false;
		buttonMaxVehiclesMinus.visible = false;
		buttonMaxVehiclesPlus.visible  = false;
		buttonTargetGroupMinus.visible = false;
		buttonTargetGroupPlus.visible  = false;
		buttonIntersectionGroupPrevious.visible = false;
		buttonIntersectionGroupNext.visible     = false;
	}

	// ── Scroll ────────────────────────────────────────────────────────────────
	@Override
	public boolean mouseScrolled2(double mx, double my, double amount) {
		if (panelMode == PanelMode.VEHICLE_POOL) {
			if (mx >= vehiclePoolRightX()) selectedVehiclePage = clamp(selectedVehiclePage + (amount < 0 ? 1 : -1), maxSelectedVehiclePage());
			else vehiclePage = clamp(vehiclePage + (amount < 0 ? 1 : -1), maxVehiclePage());
			refreshButtons();
			return true;
		}
		if (mx <= leftPanelWidth()) {
			entryPage = clamp(entryPage + (amount < 0 ? 1 : -1), maxEntryPage());
			refreshButtons();
			return true;
		}
		return widgetMap.mouseScrolled(mx, my, amount);
	}

	@Override
	public boolean mouseClicked2(double mx, double my, int button) {
		if (super.mouseClicked2(mx, my, button)) return true;
		if (panelMode != PanelMode.VEHICLE_POOL) return widgetMap.mouseClicked(mx, my, button);
		return false;
	}

	@Override
	public boolean mouseDragged2(double mx, double my, int button, double dx, double dy) {
		if (super.mouseDragged2(mx, my, button, dx, dy)) return true;
		if (panelMode != PanelMode.VEHICLE_POOL) return widgetMap.mouseDragged(mx, my, button, dx, dy);
		return false;
	}

	@Override
	public boolean mouseReleased2(double mx, double my, int button) {
		if (panelMode != PanelMode.VEHICLE_POOL) widgetMap.mouseReleased(mx, my, button);
		return super.mouseReleased2(mx, my, button);
	}

	@Override public boolean isPauseScreen2() { return false; }
	@Override public void onClose2()          { widgetMap.onClose(); super.onClose2(); }

	// ── Layout helpers ─────────────────────────────────────────────────────────
	private boolean isNarrowMode() { return width < NARROW_THRESHOLD; }

	private int leftPanelWidth() {
		if (panelMode == PanelMode.VEHICLE_POOL)    return width;
		if (isNarrowMode() && mapVisibleInNarrow)   return 0;
		if (isNarrowMode())                         return width;
		final int desired = (int)(width * 0.42);
		return Math.max(PANEL_MIN_WIDTH, Math.min(PANEL_MAX_WIDTH, Math.min(width - MAP_MIN_WIDTH, desired)));
	}

	private int visibleListRows() {
		final int searchH  = dashboardSection == DashboardSection.INTERSECTIONS ? ROW_H + 4 : 0;
		final int reserved = LIST_START_Y + searchH + (SQUARE_SIZE + GAP) + 100 + 12;
		return Math.max(3, Math.min(MAX_LIST_ROWS, (height - reserved) / ROW_H));
	}

	private static final int INTERSECTION_LIST_WIDTH = 260;
	private int ilWidth(int cw)  { return Math.min(INTERSECTION_LIST_WIDTH, Math.max(140, cw / 2 - 6)); }
	private int idX(int cw)      { return MARGIN + ilWidth(cw) + 8; }
	private int idWidth(int cw)  { return Math.max(160, cw - ilWidth(cw) - 8); }

	private int vehiclePoolListWidth() { return Math.min(340, Math.max(140, (width - 80) / 2)); }
	private int vehiclePoolLeftX()     { return width / 2 - vehiclePoolListWidth() - 20; }
	private int vehiclePoolRightX()    { return width / 2 + 20; }
	private int vehiclePoolListY()     { return 76; }

	// ── Selection helpers ──────────────────────────────────────────────────────
	private void selectEntry(ClientTrafficDashboardEntry entry) {
		final int idx = entries.indexOf(entry);
		if (idx >= 0) { selectedIndex = idx; entryPage = idx / visibleListRows(); panelMode = PanelMode.OVERVIEW; layoutWidgets(); refreshButtons(); }
	}

	private void selectIntersection(ClientTrafficIntersectionEntry it) {
		final int idx = intersections.indexOf(it);
		if (idx >= 0) {
			selectedIntersectionIndex = idx;
			dashboardSection = DashboardSection.INTERSECTIONS;
			pendingIntersectionCorner = null; drawingIntersection = false;
			selectedIntersectionNode = null; selectedPhaseIndex = 0;
			widgetMap.setCreatingIntersection(false); widgetMap.setPendingIntersectionCorner(null);
			syncIntersectionNameField(); layoutWidgets(); refreshButtons();
		}
	}

	private ClientTrafficDashboardEntry    selectedEntry()        { return selectedIndex >= 0 && selectedIndex < entries.size() ? entries.get(selectedIndex) : null; }
	private ClientTrafficIntersectionEntry selectedIntersection() { return selectedIntersectionIndex >= 0 && selectedIntersectionIndex < intersections.size() ? intersections.get(selectedIntersectionIndex) : null; }

	private int maxEntryPage() {
		final int size = dashboardSection == DashboardSection.INTERSECTIONS ? filteredIntersections.size() : entries.size();
		final int rows = visibleListRows();
		return Math.max(0, (size - 1) / rows);
	}
	private int maxVehiclePage()         { return Math.max(0, (filteredVehicleOptions.size() - 1) / AVAIL_VEH_PER_PAGE); }
	private int maxSelectedVehiclePage() { final ClientTrafficDashboardEntry e = selectedEntry(); return Math.max(0, ((e == null ? 0 : e.effectiveVehiclePool().size()) - 1) / SELECTED_VEH_ROWS); }

	// ── Intersection corner / node interaction ─────────────────────────────────
	private void handleIntersectionCornerClick(Double wx, Double wz) {
		if (dashboardSection != DashboardSection.INTERSECTIONS || !drawingIntersection) return;
		final int y = Minecraft.getInstance().player == null ? 64 : Minecraft.getInstance().player.blockPosition().getY();
		final BlockPos corner = new BlockPos((int) Math.floor(wx), y, (int) Math.floor(wz));
		if (pendingIntersectionCorner == null) {
			pendingIntersectionCorner = corner;
			widgetMap.setPendingIntersectionCorner(corner);
		} else {
			sendCreateIntersection(new BlockPos(pendingIntersectionCorner.getX(), y - 8, pendingIntersectionCorner.getZ()), new BlockPos(corner.getX(), y + 8, corner.getZ()));
			pendingIntersectionCorner = null; drawingIntersection = false;
			widgetMap.setCreatingIntersection(false); widgetMap.setPendingIntersectionCorner(null);
		}
		refreshButtons();
	}

	private void handleIntersectionNodeClick(ClientTrafficIntersectionEntry it, Long nx, Long nz) {
		if (dashboardSection != DashboardSection.INTERSECTIONS || it == null) return;
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node : it.nodes()) {
			if (node.x() == nx && node.z() == nz) {
				selectedIntersectionNode = node.x() + "," + node.y() + "," + node.z();
				refreshButtons();
				return;
			}
		}
	}

	// ── Networking ────────────────────────────────────────────────────────────
	private void sendUpdate(String action, int delta, String value) {
		final ClientTrafficDashboardEntry entry = selectedEntry();
		if (entry == null) return;
		final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeUtf(entry.id()); buf.writeBlockPos(entry.blockPos());
		buf.writeUtf(action); buf.writeVarInt(delta);
		buf.writeBoolean(value != null); if (value != null) buf.writeUtf(value);
		ClientPlayNetworking.send(TrafficDashboardNetworking.UPDATE_PACKET_ID, buf);
	}

	private void sendIntersectionUpdate(String action, int delta, String value) {
		final ClientTrafficIntersectionEntry it = selectedIntersection();
		if (it == null) return;
		final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeUtf(it.id()); buf.writeUtf(action); buf.writeVarInt(delta);
		buf.writeBoolean(value != null); if (value != null) buf.writeUtf(value);
		ClientPlayNetworking.send(TrafficDashboardNetworking.INTERSECTION_UPDATE_PACKET_ID, buf);
	}

	private void sendCreateIntersection(BlockPos a, BlockPos b) {
		final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeBlockPos(a); buf.writeBlockPos(b);
		ClientPlayNetworking.send(TrafficDashboardNetworking.INTERSECTION_CREATE_PACKET_ID, buf);
	}

	private void sendRefresh()       { ClientPlayNetworking.send(TrafficDashboardNetworking.REFRESH_PACKET_ID,         new FriendlyByteBuf(Unpooled.buffer())); }
	private void sendClearVehicles() { ClientPlayNetworking.send(TrafficDashboardNetworking.CLEAR_VEHICLES_PACKET_ID,  new FriendlyByteBuf(Unpooled.buffer())); }

	// ── Vehicle options ────────────────────────────────────────────────────────
	private void reloadVehicleOptions() {
		final java.util.Map<String, VehicleOption> map = new java.util.LinkedHashMap<>();
		CustomResourceLoader.iterateVehicles(TransportMode.TRAIN, vr -> map.put(vr.getId(), new VehicleOption(vr.getId(), fmtVehicle(vr))));
		for (ClientMtrVehicleResourceRegistry.VisualDefinition vd : ClientMtrVehicleResourceRegistry.all())
			map.putIfAbsent(vd.id(), new VehicleOption(vd.id(), fmtVehicle(vd)));
		final List<VehicleOption> updated = new ArrayList<>(map.values());
		updated.sort(Comparator.comparing(VehicleOption::label, String.CASE_INSENSITIVE_ORDER));
		if (!updated.equals(vehicleOptions)) {
			vehicleOptions.clear(); vehicleOptions.addAll(updated);
			refreshFilteredVehicleOptions();
			vehiclePage = Math.min(vehiclePage, maxVehiclePage());
		}
	}

	private void refreshFilteredVehicleOptions() {
		filteredVehicleOptions.clear();
		final String q = vehicleSearchQuery.toLowerCase(java.util.Locale.ROOT);
		for (VehicleOption vo : vehicleOptions)
			if (q.isBlank() || vo.label().toLowerCase(java.util.Locale.ROOT).contains(q) || vo.id().toLowerCase(java.util.Locale.ROOT).contains(q))
				filteredVehicleOptions.add(vo);
		vehiclePage = Math.min(vehiclePage, maxVehiclePage());
	}

	private void refreshFilteredIntersections() {
		filteredIntersections.clear();
		final String q = intersectionSearchQuery.toLowerCase(java.util.Locale.ROOT);
		for (ClientTrafficIntersectionEntry it : intersections) {
			final String s = (it.effectiveName() + " " + it.centerX() + " " + it.centerZ() + " " + it.id()).toLowerCase(java.util.Locale.ROOT);
			if (q.isBlank() || s.contains(q)) filteredIntersections.add(it);
		}
		entryPage = Math.min(entryPage, maxEntryPage());
	}

	private void syncIntersectionNameField() {
		updatingIntersectionNameField = true;
		try { final ClientTrafficIntersectionEntry it = selectedIntersection(); intersectionNameField.setText2(it == null ? "" : it.effectiveName()); }
		finally { updatingIntersectionNameField = false; }
	}

	private void openVehiclePool() {
		if (selectedEntry() != null && selectedEntry().type().name().equals("SPAWN")) {
			panelMode = PanelMode.VEHICLE_POOL; layoutWidgets(); refreshButtons();
		}
	}

	// ── Query helpers ──────────────────────────────────────────────────────────
	private String selectedIntersectionNode() { return selectedIntersectionNode; }

	private List<Integer> selectedGroupNodeNumbers() {
		final ClientTrafficIntersectionEntry it = selectedIntersection();
		final TrafficIntersectionGroup g = it == null ? null : selectedGroup(it);
		return g == null ? List.of() : g.nodeNumbers();
	}

	private Integer selectedNodeNumber() {
		final ClientTrafficIntersectionEntry it = selectedIntersection();
		if (it == null || selectedIntersectionNode == null) return null;
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node : it.nodes())
			if (selectedIntersectionNode.equals(node.x() + "," + node.y() + "," + node.z())) return node.number();
		return null;
	}

	private boolean selectedNodeIsIn() {
		final ClientTrafficIntersectionEntry it = selectedIntersection();
		if (it == null || selectedIntersectionNode == null) return false;
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode node : it.nodes())
			if (selectedIntersectionNode.equals(node.x() + "," + node.y() + "," + node.z())) return node.type().name().equals("IN");
		return false;
	}

	private static boolean containsNode(ClientTrafficIntersectionEntry it, String enc) {
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode n : it.nodes())
			if (enc.equals(n.x() + "," + n.y() + "," + n.z())) return true;
		return false;
	}

	private static List<Integer> effectivePhaseOrder(ClientTrafficIntersectionEntry it) {
		if (!it.phaseOrder().isEmpty()) return it.phaseOrder();
		return it.nodes().stream()
			.filter(n -> n.type().name().equals("IN"))
			.map(com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode::number)
			.distinct().sorted().toList();
	}

	private static List<TrafficIntersectionGroup> effectiveGroups(ClientTrafficIntersectionEntry it) {
		if (!it.groups().isEmpty()) return it.groups();
		return effectivePhaseOrder(it).stream()
			.map(n -> new TrafficIntersectionGroup("Group " + n, it.phaseDurationTicks(), List.of(n)))
			.toList();
	}

	private TrafficIntersectionGroup selectedGroup(ClientTrafficIntersectionEntry it) {
		final List<TrafficIntersectionGroup> gs = effectiveGroups(it);
		return selectedPhaseIndex >= 0 && selectedPhaseIndex < gs.size() ? gs.get(selectedPhaseIndex) : null;
	}

	private String selectedNodeLabel(ClientTrafficIntersectionEntry it) {
		if (selectedIntersectionNode == null) return "none";
		for (com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode n : it.nodes()) {
			if (selectedIntersectionNode.equals(n.x() + "," + n.y() + "," + n.z())) {
				final List<TrafficIntersectionGroup> gs = effectiveGroups(it);
				final List<Integer> gIdxs = new ArrayList<>();
				for (int i = 0; i < gs.size(); i++) if (gs.get(i).nodeNumbers().contains(n.number())) gIdxs.add(i + 1);
				return n.type() + " #" + n.number() + " @ " + n.x() + "," + n.z() + (gIdxs.isEmpty() ? "  (unassigned)" : "  groups " + gIdxs);
			}
		}
		return selectedIntersectionNode;
	}

	private static String modeLabel(ClientTrafficIntersectionEntry it) {
		return it.signalMode() == TrafficIntersectionSignalMode.AUTO ? "Auto" : "Manual";
	}

	private String shortEntryLabel(ClientTrafficDashboardEntry e) {
		return (e.type().name().equals("SPAWN") ? "S" : "D") + " @ " + e.blockPos().getX() + "," + e.blockPos().getZ();
	}

	private VehicleOption findVehicleOption(String id) {
		for (VehicleOption vo : vehicleOptions) if (vo.id().equals(id)) return vo;
		return null;
	}

	private int countMissingPoolEntries(ClientTrafficDashboardEntry e) {
		int missing = 0;
		for (String id : e.effectiveVehiclePool()) if (findVehicleOption(id) == null) missing++;
		return missing;
	}

	private String headerHint() {
		if (panelMode == PanelMode.VEHICLE_POOL) return "Click a vehicle to add/remove it from the pool.";
		if (dashboardSection == DashboardSection.INTERSECTIONS) return "Draw areas, set signal groups, then tune green times.";
		return "Manage spawn/despawn connectors and vehicle pools.";
	}

	// ── Static helpers ────────────────────────────────────────────────────────
	private static String fmtVehicle(VehicleResource vr) {
		final String n = vr.getName().getString();
		final String m = String.format(" %.1fm×%.1fm", vr.getLength(), vr.getWidth());
		return (n == null || n.isBlank()) ? vr.getId() + m : n + " [" + vr.getId() + "]" + m;
	}

	private static String fmtVehicle(ClientMtrVehicleResourceRegistry.VisualDefinition vd) {
		final String m = vd.lengthMeters() > 0 || vd.widthMeters() > 0 ? String.format(" %.1fm×%.1fm", vd.lengthMeters(), vd.widthMeters()) : "";
		return (vd.name() == null || vd.name().isBlank()) ? vd.id() + m : vd.name() + " [" + vd.id() + "]" + m;
	}

	private static int clamp(int v, int max) { return Math.max(0, Math.min(max, v)); }

	private static String shorten(String s, int max) {
		return s.length() <= max ? s : s.substring(0, Math.max(0, max - 3)) + "...";
	}

	private static void drawCenteredText(GraphicsHolder gh, String text, int cx, int y, int color) {
		gh.drawText(text, cx - GraphicsHolder.getTextWidth(text) / 2, y, color, false, GraphicsHolder.getDefaultLight());
	}

	// ── Inner types ────────────────────────────────────────────────────────────
	private record VehicleOption(String id, String label) {}
	private enum PanelMode      { OVERVIEW, VEHICLE_POOL }
	private enum DashboardSection { CONNECTORS, INTERSECTIONS }
}
