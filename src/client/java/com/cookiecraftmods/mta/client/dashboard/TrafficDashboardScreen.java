package com.cookiecraftmods.mta.client.dashboard;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.dashboard.network.TrafficDashboardNetworking;
import com.cookiecraftmods.mta.client.render.ClientMtrVehicleResourceRegistry;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionGroup;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionLevel;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNodeType;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionSignalMode;
import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.mtr.core.data.TransportMode;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Identifier;
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
import org.mtr.mod.resource.VehicleResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TrafficDashboardScreen extends ScreenExtension implements IGui {

	private static final int PANEL_MIN_WIDTH    = 280;
	private static final int PANEL_MAX_WIDTH    = 640;
	private static final int MAP_MIN_WIDTH      = 200;
	private static final int NARROW_THRESHOLD   = 520;
	private static final double FIXED_GUI_SCALE = 2.0D;
	private static final int MAX_LIST_ROWS      = 10;
	private static final int GROUP_LIST_ROWS    = 5;
	private static final int SELECTED_VEH_ROWS  = 14;
	private static final int AVAIL_VEH_PER_PAGE = 14;
	private static final int TITLE_Y            = 8;
	private static final int HINT_Y             = 19;
	private static final int TAB_Y              = 32;
	private static final int LIST_START_Y       = 76;
	private static final int ROW_H              = 20;
	private static final int ROW_GAP            = 4;
	private static final int ROW_STRIDE         = ROW_H + ROW_GAP;
	private static final int PAGE_TO_DETAILS_GAP = 20;
	private static final int INLINE_BTN_W       = 22;
	private static final int ROW_LOCATE_BTN_W   = 54;
	private static final int ROW_POOL_BTN_W     = 42;
	private static final int MARGIN             = 10;
	private static final int GAP                = 6;
	private static final String LANG_PREFIX     = "gui.mtr-traffic-addon.traffic_dashboard.";
	private static final String KOFI_URL        = "https://ko-fi.com/cookiecraftmods";
	private static final String DISCORD_URL     = "https://discord.com/invite/mrVTtKNRkn";
	private static final Identifier KOFI_ICON   = new Identifier(MTRTrafficAddon.MOD_ID, "textures/gui/social/kofi.png");
	private static final Identifier DISCORD_ICON = new Identifier(MTRTrafficAddon.MOD_ID, "textures/gui/social/discord.png");

	private static final int C_WHITE    = 0xFFFFFFFF;
	private static final int C_HINT     = 0xFF888888;
	private static final int C_SECTION  = 0xFF9CC7FF;
	private static final int C_OK       = 0xFF7EE787;
	private static final int C_WARN     = 0xFFFFD166;
	private static final int C_MUTED    = 0xFFAAAAAA;
	private static final int C_DIVIDER  = 0xFF3A3A3A;
	private static final int C_SELECTED = 0xFF2A4870;

	private final List<ClientTrafficDashboardEntry>    entries              = new ArrayList<>();
	private final List<ClientTrafficDashboardEntry>    filteredEntries      = new ArrayList<>();
	private final List<ClientTrafficIntersectionEntry> intersections        = new ArrayList<>();
	private final List<ClientTrafficIntersectionEntry> filteredIntersections = new ArrayList<>();
	private final List<VehicleOption>                  vehicleOptions        = new ArrayList<>();
	private final List<VehicleOption>                  filteredVehicleOptions = new ArrayList<>();

	private final List<ButtonWidgetExtension> entryButtons              = new ArrayList<>();
	private final List<ButtonWidgetExtension> entryLocateButtons        = new ArrayList<>();
	private final List<ButtonWidgetExtension> entryPoolButtons          = new ArrayList<>();
	private final List<ButtonWidgetExtension> intersectionGroupButtons   = new ArrayList<>();
	private final List<ButtonWidgetExtension> intersectionGroupDeleteBtns = new ArrayList<>();
	private final List<ButtonWidgetExtension> selectedVehicleButtons    = new ArrayList<>();
	private final List<ButtonWidgetExtension> vehicleButtons            = new ArrayList<>();

	private final TrafficWidgetMap widgetMap;

	private PanelMode       panelMode       = PanelMode.OVERVIEW;
	private DashboardSection dashboardSection = DashboardSection.CONNECTORS;
	private boolean mapVisibleInNarrow      = false;
	private int connectorDetailsY = 0;
	private int intersectionDetailsY = 0;
	private int spawnIntervalRowY  = 0;
	private int phaseDurRowY       = 0;
	private int  selectedIndex;
	private int  selectedIntersectionIndex;
	private int  entryPage;
	private int  vehiclePage;
	private int  selectedVehiclePage;
	private String vehicleSearchQuery    = "";
	private String connectorSearchQuery  = "";
	private String intersectionSearchQuery = "";
	private String lastConnectorClickId;
	private long lastConnectorClickTimeMs;
	private int connectorNameEditX;
	private int connectorNameEditY;
	private int connectorNameEditWidth;
	private BlockPos pendingIntersectionCorner;
	private boolean  drawingIntersection;
	private String   selectedIntersectionNode;
	private boolean  editingConnectorName;
	private boolean  updatingConnectorNameField;
	private boolean  updatingIntersectionNameField;
	private int selectedPhaseIndex = -1;
	private static List<String> copiedVehiclePool = List.of();
	private static boolean hasCopiedVehiclePool;
	private int lastLayoutUiWidth = -1;
	private int lastLayoutUiHeight = -1;

	private final TextFieldWidgetExtension vehicleSearchField;
	private final TextFieldWidgetExtension connectorSearchField;
	private final TextFieldWidgetExtension connectorNameField;
	private final TextFieldWidgetExtension intersectionSearchField;
	private final TextFieldWidgetExtension intersectionNameField;

	private final ButtonWidgetExtension buttonEntryPageUp;
	private final ButtonWidgetExtension buttonEntryPageDown;
	private final ButtonWidgetExtension buttonVehiclePageUp;
	private final ButtonWidgetExtension buttonVehiclePageDown;
	private final ButtonWidgetExtension buttonSelectedVehiclePageUp;
	private final ButtonWidgetExtension buttonSelectedVehiclePageDown;
	private final ButtonWidgetExtension buttonCopyVehiclePool;
	private final ButtonWidgetExtension buttonPasteVehiclePool;
	private final ButtonWidgetExtension buttonOpenVehiclePool;
	private final ButtonWidgetExtension buttonBackToOverview;
	private final ButtonWidgetExtension buttonRefresh;
	private final ButtonWidgetExtension buttonClearVehicles;
	private final ButtonWidgetExtension buttonToggleEnabled;
	private final ButtonWidgetExtension buttonSpawnIntervalMinus;
	private final ButtonWidgetExtension buttonSpawnIntervalPlus;
	private final ButtonWidgetExtension buttonFocus;
	private final ButtonWidgetExtension buttonFitMap;
	private final ButtonWidgetExtension buttonZoomIn;
	private final ButtonWidgetExtension buttonZoomOut;
	private final ButtonWidgetExtension buttonSectionConnectors;
	private final ButtonWidgetExtension buttonSectionIntersections;
	private final ButtonWidgetExtension buttonAddIntersection;
	private final ButtonWidgetExtension buttonDeleteIntersection;
	private final ButtonWidgetExtension buttonIntersectionSignalMode;
	private final ButtonWidgetExtension buttonAutoDetectIntersection;
	private final ButtonWidgetExtension buttonIntersectionGroupAdd;
	private final ButtonWidgetExtension buttonToggleIntersectionNodeType;
	private final ButtonWidgetExtension buttonIntersectionPhaseMinus;
	private final ButtonWidgetExtension buttonIntersectionPhasePlus;
	private final ButtonWidgetExtension buttonIntersectionPhaseAdd;
	private final ButtonWidgetExtension buttonIntersectionPhaseRemove;
	private final ButtonWidgetExtension buttonToggleMap;

	public TrafficDashboardScreen(List<ClientTrafficDashboardEntry> entries, List<ClientTrafficIntersectionEntry> intersections) {
		super(TextHelper.literal(text("title")));

		widgetMap = new TrafficWidgetMap(
			() -> this.entries, () -> this.intersections,
			this::selectedEntry, this::selectedIntersection,
			this::selectedIntersectionNode, this::selectedGroupNodeNumbers,
			this::selectEntry, this::selectIntersection,
			this::handleIntersectionCornerClick, this::handleIntersectionNodeClick
		);

		vehicleSearchField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal(text("search_vehicles")), 128, TextCase.DEFAULT, "", text("search_vehicles"));
		vehicleSearchField.setChangedListener2(v -> { vehicleSearchQuery = v == null ? "" : v.trim(); vehiclePage = 0; refreshFilteredVehicleOptions(); refreshButtons(); });

		connectorSearchField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal(text("search_connectors")), 96, TextCase.DEFAULT, "", text("search_connectors"));
		connectorSearchField.setChangedListener2(v -> { connectorSearchQuery = v == null ? "" : v.trim(); entryPage = 0; refreshFilteredEntries(); layoutWidgets(); refreshButtons(); });

		connectorNameField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal(text("connector_name")), 64, TextCase.DEFAULT, "", text("connector_name"));

		intersectionSearchField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal(text("search_intersections")), 96, TextCase.DEFAULT, "", text("search_intersections"));
		intersectionSearchField.setChangedListener2(v -> { intersectionSearchQuery = v == null ? "" : v.trim(); entryPage = 0; refreshFilteredIntersections(); refreshButtons(); });

		intersectionNameField = new TextFieldWidgetExtension(0, 0, 0, 18, TextHelper.literal(text("intersection_name")), 64, TextCase.DEFAULT, "", text("intersection_name"));

		buttonEntryPageUp = btn("<", () -> changeEntryPage(-1));
		buttonEntryPageDown = btn(">", () -> changeEntryPage(1));
		buttonVehiclePageUp = btn("<", () -> changeVehiclePage(-1));
		buttonVehiclePageDown = btn(">", () -> changeVehiclePage(1));
		buttonSelectedVehiclePageUp = btn("<", () -> changeSelectedVehiclePage(-1));
		buttonSelectedVehiclePageDown = btn(">", () -> changeSelectedVehiclePage(1));
		buttonCopyVehiclePool = btnKey("copy_vehicles", this::copySelectedVehiclePool);
		buttonPasteVehiclePool = btnKey("paste_vehicles", this::pasteVehiclePool);

		buttonOpenVehiclePool = btnKey("vehicle_pool", () -> openVehiclePool());
		buttonBackToOverview  = btnKey("back",      () -> { panelMode = PanelMode.OVERVIEW; layoutWidgets(); refreshButtons(); });
		buttonRefresh         = btnKey("refresh_routes",   () -> sendRefresh());
		buttonClearVehicles   = btnKey("clear_active",     () -> sendClearVehicles());
		buttonToggleEnabled   = btnKey("enable",           () -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) sendIntersectionUpdate("enabled", 0, null);
			else sendUpdate("enabled", 0, null);
		});
		buttonSpawnIntervalMinus = btn("-", () -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) sendIntersectionUpdate("phase_duration", -20, null);
			else sendUpdate("spawn_interval", -20, null);
		});
		buttonSpawnIntervalPlus = btn("+", () -> {
			if (dashboardSection == DashboardSection.INTERSECTIONS) sendIntersectionUpdate("phase_duration", 20, null);
			else sendUpdate("spawn_interval", 20, null);
		});
		buttonFocus = btnKey("focus_map", this::focusSelectionOnMap);
		buttonFitMap    = btnKey("fit_map", () -> widgetMap.fitToContent());
		buttonZoomIn    = btn("", () -> openExternalLink(DISCORD_URL));
		buttonZoomOut   = btn("", () -> openExternalLink(KOFI_URL));

		buttonSectionConnectors    = btnKey("connectors",    () -> switchSection(DashboardSection.CONNECTORS));
		buttonSectionIntersections = btnKey("intersections", () -> switchSection(DashboardSection.INTERSECTIONS));

		buttonAddIntersection = btnKey("draw_area", this::toggleIntersectionDrawing);
		buttonDeleteIntersection    = btnKey("delete_area",     () -> sendIntersectionUpdate("delete", 0, null));
		buttonIntersectionSignalMode     = btnKey("mode",       () -> sendIntersectionUpdate("signal_mode", 0, null));
		buttonAutoDetectIntersection     = btnKey("find_nodes", () -> sendIntersectionUpdate("find_nodes",  0, null));
		buttonIntersectionGroupAdd = btnKey("add_group", this::addIntersectionGroup);
		buttonToggleIntersectionNodeType = btnKey("node_type", this::toggleSelectedIntersectionNodeType);
		buttonIntersectionPhaseMinus = btn("-", () -> sendIntersectionUpdate("phase_duration", -20, String.valueOf(selectedPhaseIndex)));
		buttonIntersectionPhasePlus  = btn("+", () -> sendIntersectionUpdate("phase_duration",  20, String.valueOf(selectedPhaseIndex)));
		buttonIntersectionPhaseAdd = btnKey("assign", this::assignSelectedNodeToPhase);
		buttonIntersectionPhaseRemove = btnKey("remove", this::removeSelectedNodeFromPhase);

		buttonToggleMap = btnKey("map", () -> { mapVisibleInNarrow = !mapVisibleInNarrow; layoutWidgets(); refreshButtons(); });

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
						selectIntersection(it);
						widgetMap.focusOn(it);
					}
				} else if (ei < filteredEntries.size()) {
					final ClientTrafficDashboardEntry clickedEntry = filteredEntries.get(ei);
					final long now = System.currentTimeMillis();
					final boolean doubleClick = clickedEntry.id().equals(lastConnectorClickId) && now - lastConnectorClickTimeMs <= 350;
					if (!doubleClick) {
						editingConnectorName = false;
					}
					selectConnector(clickedEntry, false);
					lastConnectorClickId = clickedEntry.id();
					lastConnectorClickTimeMs = now;
					if (doubleClick) {
						startConnectorNameEdit(entryButtons.get(idx));
					}
				}
			}));
			entryLocateButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), b -> {
				final int ei = entryPage * visibleListRows() + idx;
				if (dashboardSection == DashboardSection.CONNECTORS && ei < filteredEntries.size()) {
					selectConnector(filteredEntries.get(ei), true);
				}
			}));
			entryPoolButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), b -> {
				final int ei = entryPage * visibleListRows() + idx;
				if (dashboardSection == DashboardSection.CONNECTORS && ei < filteredEntries.size() && filteredEntries.get(ei).type() == TrafficPointType.SPAWN) {
					selectedIndex = entries.indexOf(filteredEntries.get(ei));
					openVehiclePool();
				}
			}));
		}

		for (int i = 0; i < SELECTED_VEH_ROWS; i++) {
			final int idx = i;
			selectedVehicleButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), b -> {
				final ClientTrafficDashboardEntry entry = selectedEntry();
				if (entry == null || entry.type() != TrafficPointType.SPAWN) return;
				final List<String> pool = entry.effectiveVehiclePool();
				final int vi = selectedVehiclePage * SELECTED_VEH_ROWS + idx;
				if (vi < pool.size()) sendUpdate("vehicle_pool_toggle", 0, pool.get(vi));
			}));
		}
		for (int i = 0; i < AVAIL_VEH_PER_PAGE; i++) {
			final int idx = i;
			vehicleButtons.add(new ButtonWidgetExtension(0, 0, 0, 18, TextHelper.literal(""), b -> {
				final int vi = vehiclePage * AVAIL_VEH_PER_PAGE + idx;
				if (vi < filteredVehicleOptions.size() && selectedEntry() != null && selectedEntry().type() == TrafficPointType.SPAWN)
					sendUpdate("vehicle_pool_toggle", 0, filteredVehicleOptions.get(vi).id());
			}));
		}

		reloadVehicleOptions();
		updateEntries(entries, intersections);
	}

	private static ButtonWidgetExtension btn(String label, Runnable action) {
		return new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.literal(label), b -> action.run());
	}

	private static ButtonWidgetExtension btnKey(String key, Runnable action) {
		return btn(text(key), action);
	}

	private static Component component(String key, Object... args) {
		return Component.translatable(LANG_PREFIX + key, args);
	}

	private static String text(String key, Object... args) {
		return component(key, args).getString();
	}

	private void switchSection(DashboardSection section) {
		dashboardSection = section;
		panelMode = PanelMode.OVERVIEW;
		editingConnectorName = false;
		pendingIntersectionCorner = null;
		drawingIntersection = false;
		selectedIntersectionNode = null;
		widgetMap.setCreatingIntersection(false);
		widgetMap.setPendingIntersectionCorner(null);
		syncIntersectionNameField();
		layoutWidgets();
		refreshButtons();
	}

	private void copySelectedVehiclePool() {
		final ClientTrafficDashboardEntry entry = selectedEntry();
		if (entry == null || entry.type() != TrafficPointType.SPAWN) {
			return;
		}
		copiedVehiclePool = List.copyOf(entry.effectiveVehiclePool());
		hasCopiedVehiclePool = true;
		refreshButtons();
	}

	private void pasteVehiclePool() {
		final ClientTrafficDashboardEntry entry = selectedEntry();
		if (entry != null && entry.type() == TrafficPointType.SPAWN && hasCopiedVehiclePool) {
			sendUpdate("vehicle_pool_replace", 0, String.join("\n", copiedVehiclePool));
		}
	}

	private void focusSelectionOnMap() {
		if (dashboardSection == DashboardSection.INTERSECTIONS) {
			final ClientTrafficIntersectionEntry intersection = selectedIntersection();
			if (intersection != null) {
				widgetMap.focusOn(intersection);
				return;
			}
		}
		final ClientTrafficDashboardEntry entry = selectedEntry();
		if (entry != null) {
			widgetMap.focusOn(entry);
		}
	}

	private void toggleIntersectionDrawing() {
		dashboardSection = DashboardSection.INTERSECTIONS;
		panelMode = PanelMode.OVERVIEW;
		drawingIntersection = !drawingIntersection;
		pendingIntersectionCorner = null;
		selectedIntersectionNode = null;
		widgetMap.setCreatingIntersection(drawingIntersection);
		widgetMap.setPendingIntersectionCorner(null);
		layoutWidgets();
		refreshButtons();
	}

	private void addIntersectionGroup() {
		final ClientTrafficIntersectionEntry intersection = selectedIntersection();
		selectedPhaseIndex = intersection == null ? 0 : effectiveGroups(intersection).size();
		sendIntersectionUpdate("group_add", 0, null);
	}

	private void toggleSelectedIntersectionNodeType() {
		final ClientTrafficIntersectionEntry intersection = selectedIntersection();
		final String action = intersection != null && intersection.level() == TrafficIntersectionLevel.TRAIN
			? "train_node_toggle"
			: "node_type";
		sendIntersectionUpdate(action, 0, selectedIntersectionNode);
	}

	private void assignSelectedNodeToPhase() {
		final Integer nodeNumber = selectedNodeNumber();
		if (nodeNumber != null) {
			sendIntersectionUpdate("phase_assign", nodeNumber, String.valueOf(selectedPhaseIndex));
		}
	}

	private void removeSelectedNodeFromPhase() {
		final Integer nodeNumber = selectedNodeNumber();
		sendIntersectionUpdate("phase_remove", nodeNumber == null ? 0 : nodeNumber, String.valueOf(selectedPhaseIndex));
	}

	public void updateEntries(List<ClientTrafficDashboardEntry> updatedEntries, List<ClientTrafficIntersectionEntry> updatedIntersections) {
		entries.clear();    entries.addAll(updatedEntries);
		intersections.clear(); intersections.addAll(updatedIntersections);
		refreshFilteredEntries();
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
		if (!editingConnectorName) {
			syncConnectorNameField();
		}
		if (!intersectionNameField.isFocused2()) {
			syncIntersectionNameField();
		}
		if (width > 0 && height > 0) {
			layoutWidgets();
		}
		refreshButtons();
	}

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
		addChild(new ClickableWidget(buttonCopyVehiclePool));
		addChild(new ClickableWidget(buttonPasteVehiclePool));
		addChild(new ClickableWidget(buttonOpenVehiclePool));
		addChild(new ClickableWidget(buttonBackToOverview));
		addChild(new ClickableWidget(buttonRefresh));
		addChild(new ClickableWidget(buttonClearVehicles));
		addChild(new ClickableWidget(buttonToggleEnabled));
		addChild(new ClickableWidget(buttonSpawnIntervalMinus));
		addChild(new ClickableWidget(buttonSpawnIntervalPlus));
		addChild(new ClickableWidget(buttonFocus));
		addChild(new ClickableWidget(buttonFitMap));
		addChild(new ClickableWidget(buttonZoomIn));
		addChild(new ClickableWidget(buttonZoomOut));
		addChild(new ClickableWidget(buttonSectionConnectors));
		addChild(new ClickableWidget(buttonSectionIntersections));
		addChild(new ClickableWidget(buttonAddIntersection));
		addChild(new ClickableWidget(buttonDeleteIntersection));
		addChild(new ClickableWidget(buttonIntersectionSignalMode));
		addChild(new ClickableWidget(buttonAutoDetectIntersection));
		addChild(new ClickableWidget(buttonIntersectionGroupAdd));
		addChild(new ClickableWidget(buttonToggleIntersectionNodeType));
		addChild(new ClickableWidget(buttonIntersectionPhaseMinus));
		addChild(new ClickableWidget(buttonIntersectionPhasePlus));
		addChild(new ClickableWidget(buttonIntersectionPhaseAdd));
		addChild(new ClickableWidget(buttonIntersectionPhaseRemove));
		addChild(new ClickableWidget(buttonToggleMap));
		addChild(new ClickableWidget(vehicleSearchField));
		addChild(new ClickableWidget(connectorSearchField));
		addChild(new ClickableWidget(connectorNameField));
		addChild(new ClickableWidget(intersectionSearchField));
		addChild(new ClickableWidget(intersectionNameField));
		entryButtons.forEach(b -> addChild(new ClickableWidget(b)));
		entryLocateButtons.forEach(b -> addChild(new ClickableWidget(b)));
		entryPoolButtons.forEach(b -> addChild(new ClickableWidget(b)));
		intersectionGroupButtons.forEach(b -> addChild(new ClickableWidget(b)));
		intersectionGroupDeleteBtns.forEach(b -> addChild(new ClickableWidget(b)));
		selectedVehicleButtons.forEach(b -> addChild(new ClickableWidget(b)));
		vehicleButtons.forEach(b -> addChild(new ClickableWidget(b)));

		refreshButtons();
	}

	private void layoutWidgets() {
		if (panelMode == PanelMode.VEHICLE_POOL) {
			layoutVehiclePoolWidgets();
		} else {
			layoutOverviewWidgets();
		}
	}

	private void layoutOverviewWidgets() {
		final boolean narrowMap = isNarrowMode() && mapVisibleInNarrow;
		final int panelW = leftPanelWidth();
		final int mapX   = narrowMap ? 0 : panelW;
		final int mapW   = narrowMap ? uiWidth() : Math.max(0, uiWidth() - panelW);
		connectorDetailsY = 0;
		intersectionDetailsY = 0;
		widgetMap.setPositionAndSize(mapX, 0, mapW, uiHeight());

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
		intersectionSearchField.setWidth2(dashboardSection == DashboardSection.INTERSECTIONS ? intersectionListWidth(cw) : cw);
		connectorSearchField.setX2(MARGIN);
		connectorSearchField.setY2(LIST_START_Y - 20);
		connectorSearchField.setWidth2(cw);

		final int rows   = visibleListRows();
		final int listW  = dashboardSection == DashboardSection.INTERSECTIONS ? intersectionListWidth(cw) : cw;
		int y = LIST_START_Y;
		for (int i = 0; i < MAX_LIST_ROWS; i++) {
			if (dashboardSection == DashboardSection.CONNECTORS) {
				final int actionW = ROW_LOCATE_BTN_W + ROW_POOL_BTN_W + GAP * 2;
				final int entryIndex = entryPage * rows + i;
				final boolean rowHasActions = i < rows
					&& entryIndex < filteredEntries.size()
					&& filteredEntries.get(entryIndex).type() == TrafficPointType.SPAWN;
				final int selectW = rowHasActions ? Math.max(90, listW - actionW) : listW;
				IDrawing.setPositionAndWidth(entryButtons.get(i), MARGIN, y, selectW);
				IDrawing.setPositionAndWidth(entryLocateButtons.get(i), MARGIN + selectW + GAP, y, rowHasActions ? ROW_LOCATE_BTN_W : 0);
				IDrawing.setPositionAndWidth(entryPoolButtons.get(i), MARGIN + selectW + GAP + ROW_LOCATE_BTN_W + GAP, y, rowHasActions ? ROW_POOL_BTN_W : 0);
			} else {
				IDrawing.setPositionAndWidth(entryButtons.get(i), MARGIN, y, listW);
				IDrawing.setPositionAndWidth(entryLocateButtons.get(i), MARGIN, y, 0);
				IDrawing.setPositionAndWidth(entryPoolButtons.get(i), MARGIN, y, 0);
			}
			y += ROW_STRIDE;
		}

		y = LIST_START_Y + rows * ROW_STRIDE + 2;
		IDrawing.setPositionAndWidth(buttonEntryPageUp,   MARGIN,                    y, (listW - GAP) / 2);
		IDrawing.setPositionAndWidth(buttonEntryPageDown, MARGIN + (listW + GAP) / 2, y, (listW - GAP) / 2);

		final int detailsY = y + SQUARE_SIZE + PAGE_TO_DETAILS_GAP;

		if (dashboardSection == DashboardSection.INTERSECTIONS) {
			final int dX = idX(cw);
			final int dW = idWidth(cw);
			final int detailY = LIST_START_Y;
			intersectionDetailsY = detailY;
			intersectionNameField.setX2(dX);
			intersectionNameField.setY2(detailY + 36);
			intersectionNameField.setWidth2(dW);
			layoutIntersectionGroupWidgets(dX, detailY + 158, dW);
			layoutIntersectionWidgets(dX, detailY + 158 + GROUP_LIST_ROWS * ROW_STRIDE + GAP, dW);
		} else {
			connectorDetailsY = detailsY;
			connectorNameField.setX2(editingConnectorName ? connectorNameEditX : MARGIN);
			connectorNameField.setY2(editingConnectorName ? connectorNameEditY : Math.max(LIST_START_Y, detailsY - 20));
			connectorNameField.setWidth2(editingConnectorName ? connectorNameEditWidth : cw);
			layoutConnectorWidgets(detailsY + connectorDetailsHeight() + GAP);
		}

		final int controlsY = uiHeight() - SQUARE_SIZE - 8;
		final int zoomInX = uiWidth() - SQUARE_SIZE - 8;
		final int zoomOutX = zoomInX - SQUARE_SIZE - GAP;
		final int fitW = 42;
		final int fitX = zoomOutX - fitW - GAP;
		IDrawing.setPositionAndWidth(buttonFitMap,  fitX,     controlsY, fitW);
		IDrawing.setPositionAndWidth(buttonZoomOut, zoomOutX, controlsY, SQUARE_SIZE);
		IDrawing.setPositionAndWidth(buttonZoomIn,  zoomInX,  controlsY, SQUARE_SIZE);
	}

	private void layoutConnectorWidgets(int y) {
		final int x  = MARGIN;
		final int cw = leftPanelWidth() - MARGIN * 2;
		final int half  = (cw - GAP) / 2;
		final int third = (cw - GAP * 2) / 3;
		final ClientTrafficDashboardEntry entry = selectedEntry();
		final boolean isSpawn = entry != null && entry.type() == TrafficPointType.SPAWN;
		spawnIntervalRowY = 0;

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

	private void layoutIntersectionGroupWidgets(int x, int y, int w) {
		final int delW = 22;
		for (int i = 0; i < GROUP_LIST_ROWS; i++) {
			IDrawing.setPositionAndWidth(intersectionGroupButtons.get(i),    x,         y, w - delW - GAP);
			IDrawing.setPositionAndWidth(intersectionGroupDeleteBtns.get(i), x + w - delW, y, delW);
			y += ROW_STRIDE;
		}
	}

	private void layoutIntersectionWidgets(int x, int y, int w) {
		final int third  = (w - GAP * 2) / 3;
		final ClientTrafficIntersectionEntry it = selectedIntersection();
		final boolean trainLevel = it != null && it.level() == TrafficIntersectionLevel.TRAIN;

		IDrawing.setPositionAndWidth(buttonToggleEnabled,        x,                    y, third);
		IDrawing.setPositionAndWidth(buttonFocus,                x + third + GAP,       y, third);
		IDrawing.setPositionAndWidth(buttonDeleteIntersection,   x + (third + GAP) * 2, y, w - (third + GAP) * 2);
		y += SQUARE_SIZE + GAP;

		if (trainLevel) {
			IDrawing.setPositionAndWidth(buttonIntersectionSignalMode,    x, y, 0);
			IDrawing.setPositionAndWidth(buttonAutoDetectIntersection,    x, y, w);
			IDrawing.setPositionAndWidth(buttonIntersectionGroupAdd,      x, y, 0);
		} else {
			IDrawing.setPositionAndWidth(buttonIntersectionSignalMode,    x,                    y, third);
			IDrawing.setPositionAndWidth(buttonAutoDetectIntersection,    x + third + GAP,       y, third);
			IDrawing.setPositionAndWidth(buttonIntersectionGroupAdd,      x + (third + GAP) * 2, y, w - (third + GAP) * 2);
		}
		y += SQUARE_SIZE + GAP;

		phaseDurRowY = y;
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseMinus, x,                 y, INLINE_BTN_W);
		IDrawing.setPositionAndWidth(buttonIntersectionPhasePlus,  x + w - INLINE_BTN_W, y, INLINE_BTN_W);
		y += SQUARE_SIZE + GAP;

		final int half = (w - GAP) / 2;
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseAdd,    x,            y, half);
		IDrawing.setPositionAndWidth(buttonIntersectionPhaseRemove, x + half + GAP, y, w - half - GAP);
		y += SQUARE_SIZE + GAP;

		IDrawing.setPositionAndWidth(buttonToggleIntersectionNodeType, x, y, w);
	}

	private void layoutVehiclePoolWidgets() {
		widgetMap.setPositionAndSize(uiWidth(), 0, 0, 0);

		final int lw   = vehiclePoolListWidth();
		final int lx   = vehiclePoolLeftX();
		final int rx   = vehiclePoolRightX();
		final int listY = vehiclePoolListY();
		final int pageY = Math.min(uiHeight() - 92, listY + SELECTED_VEH_ROWS * ROW_H + 8);

		IDrawing.setPositionAndWidth(buttonBackToOverview,        (uiWidth() - 144) / 2, uiHeight() - 40, 144);
		IDrawing.setPositionAndWidth(buttonCopyVehiclePool,       rx, uiHeight() - 64, (lw - GAP) / 2);
		IDrawing.setPositionAndWidth(buttonPasteVehiclePool,      rx + (lw + GAP) / 2, uiHeight() - 64, (lw - GAP) / 2);
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

	@Override
	public void render(GraphicsHolder gh, int mouseX, int mouseY, float delta) {
		ensureFixedScaleLayout();
		final int uiMouseX = toUiMouse(mouseX);
		final int uiMouseY = toUiMouse(mouseY);
		final boolean narrowMap = isNarrowMode() && mapVisibleInNarrow;
		gh.push();
		gh.scale((float) uiRenderScale(), (float) uiRenderScale(), 1);
		if (panelMode != PanelMode.VEHICLE_POOL && !narrowMap) {
			widgetMap.render(gh, uiMouseX, uiMouseY, delta);
		} else if (narrowMap) {
			widgetMap.render(gh, uiMouseX, uiMouseY, delta);
		}

		gh.push();
		gh.translate(0, 0, 500);
		final GuiDrawing gd = new GuiDrawing(gh);

		if (narrowMap) {
			super.render(gh, uiMouseX, uiMouseY, delta);
			gh.pop();
			gh.pop();
			return;
		}

		final int panelW = panelMode == PanelMode.VEHICLE_POOL ? uiWidth() : leftPanelWidth();
		gd.beginDrawingRectangle();
		gd.drawRectangle(0, 0, panelW, uiHeight(), ARGB_BACKGROUND);
		if (panelMode != PanelMode.VEHICLE_POOL) {
			gd.drawRectangle(panelW, 0, panelW + 1, uiHeight(), C_DIVIDER);
		}
		gd.finishDrawingRectangle();

		if (panelMode == PanelMode.VEHICLE_POOL) {
			renderVehiclePoolScreen(gh);
		} else {
			renderOverviewPanel(gh, gd);
		}

		super.render(gh, uiMouseX, uiMouseY, delta);
		renderSocialButtonIcons(gh);
		gh.pop();
		gh.pop();
	}

	private void renderOverviewPanel(GraphicsHolder gh, GuiDrawing gd) {
		gh.drawText(text("title"), MARGIN, TITLE_Y, C_WHITE, false, GraphicsHolder.getDefaultLight());
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

	private void renderConnectorPanel(GraphicsHolder gh) {
		final int cw = leftPanelWidth() - MARGIN * 2;
		final int rows = visibleListRows();

		if (entries.isEmpty()) {
			gh.drawText(text("no_connectors"), MARGIN, LIST_START_Y + 4, C_WARN, false, GraphicsHolder.getDefaultLight());
			gh.drawText(text("place_connectors"), MARGIN, LIST_START_Y + 16, C_MUTED, false, GraphicsHolder.getDefaultLight());
			return;
		}
		if (filteredEntries.isEmpty()) {
			gh.drawText(text("no_connectors_match"), MARGIN, LIST_START_Y + 4, C_WARN, false, GraphicsHolder.getDefaultLight());
			return;
		}

		final ClientTrafficDashboardEntry entry = selectedEntry();
		if (entry == null) return;

		final int detailsY = connectorDetailsY > 0 ? connectorDetailsY : LIST_START_Y + rows * ROW_STRIDE + 2 + SQUARE_SIZE + PAGE_TO_DETAILS_GAP;
		if (!editingConnectorName) {
			gh.drawText(text("selected_connector", entry.effectiveName()), MARGIN, detailsY - 12, C_SECTION, false, GraphicsHolder.getDefaultLight());
		}

		int y = detailsY;
		final boolean ok = entry.enabled() && entry.hasConnectorRoute();
		final String stateStr = text(entry.enabled() ? "state_enabled" : "state_disabled");
		final String routeStr = text(entry.hasConnectorRoute() ? "route_ready" : "route_missing");
		gh.drawText(stateStr, MARGIN, y, entry.enabled() ? C_OK : C_WARN, false, GraphicsHolder.getDefaultLight());
		gh.drawText(routeStr, MARGIN + 72, y, entry.hasConnectorRoute() ? C_OK : C_WARN, false, GraphicsHolder.getDefaultLight());
		y += 12;
		gh.drawText(text("active_vehicles", entry.activeVehicles()), MARGIN, y, ok ? C_OK : C_MUTED, false, GraphicsHolder.getDefaultLight());
		y += 12;
		gh.drawText(text("position", entry.blockPos().getX(), entry.blockPos().getY(), entry.blockPos().getZ()),
			MARGIN, y, C_MUTED, false, GraphicsHolder.getDefaultLight());
		y += 12;

		if (entry.type() == TrafficPointType.SPAWN) {
			final int missing = countMissingPoolEntries(entry);
			gh.drawText(missing > 0 ? text("pool_selected_missing", entry.effectiveVehiclePool().size(), missing) : text("pool_selected", entry.effectiveVehiclePool().size()),
				MARGIN, y, missing > 0 ? C_WARN : C_MUTED, false, GraphicsHolder.getDefaultLight());

			if (spawnIntervalRowY > 0) {
				final String intervalVal = String.format("%.1fs", entry.spawnIntervalTicks() / 20.0);
				drawStepperRowText(gh, text("spawn_interval"), intervalVal, MARGIN, cw, spawnIntervalRowY, C_WHITE);
			}
		}
	}

	private void renderIntersectionPanel(GraphicsHolder gh, GuiDrawing gd) {
		final int cw    = leftPanelWidth() - MARGIN * 2;
		final int dX    = idX(cw);
		final int dW    = idWidth(cw);
		final int detailY = intersectionDetailsY > 0 ? intersectionDetailsY : LIST_START_Y;

		gd.beginDrawingRectangle();
		gd.drawRectangle(dX - 5, LIST_START_Y - 12, dX - 4, uiHeight() - MARGIN, C_DIVIDER);
		gd.finishDrawingRectangle();

		if (drawingIntersection) {
			gh.drawText(text("drawing_area"), dX, detailY, C_WARN, false, GraphicsHolder.getDefaultLight());
			gh.drawText(pendingIntersectionCorner == null
				? text("click_first_corner")
				: text("click_opposite_corner", pendingIntersectionCorner.getX(), pendingIntersectionCorner.getZ()),
				dX, detailY + 12, C_MUTED, false, GraphicsHolder.getDefaultLight());
			return;
		}

		final ClientTrafficIntersectionEntry it = selectedIntersection();
		if (it == null) {
			gh.drawText(text("no_intersection_selected"), dX, detailY, C_MUTED, false, GraphicsHolder.getDefaultLight());
			gh.drawText(text("draw_area_hint"), dX, detailY + 12, C_MUTED, false, GraphicsHolder.getDefaultLight());
			return;
		}

		int y = detailY;
		gh.drawText(shortenToWidth(it.effectiveName(), dW), dX, y, it.enabled() ? C_WHITE : C_WARN, false, GraphicsHolder.getDefaultLight());
		y += 11;
		gh.drawText(text(it.enabled() ? "state_enabled" : "state_disabled"), dX, y, it.enabled() ? C_OK : C_WARN, false, GraphicsHolder.getDefaultLight());
		gh.drawText(text("nodes_count", it.nodes().size()), dX + 68, y, C_MUTED, false, GraphicsHolder.getDefaultLight());
		y += 11;
		gh.drawText(shortenToWidth(text("area", it.minX(), it.minZ(), it.maxX(), it.maxZ()), dW), dX, y, C_MUTED, false, GraphicsHolder.getDefaultLight());
		y += 11;
		gh.drawText(text("name_label"), dX, y + 6, C_MUTED, false, GraphicsHolder.getDefaultLight());
		y = detailY + 56;

		gh.drawText(text("intersection_level", levelLabel(it)), dX, y, it.level() == TrafficIntersectionLevel.TRAIN ? C_WARN : C_MUTED, false, GraphicsHolder.getDefaultLight());
		y += 12;
		if (it.level() == TrafficIntersectionLevel.TRAIN) {
			gh.drawText(text("train_nodes", trainNodeNumbersLabel(it)), dX, y, C_MUTED, false, GraphicsHolder.getDefaultLight());
			gh.drawText(text("train_level_hint"), dX, y + 12, C_MUTED, false, GraphicsHolder.getDefaultLight());
		} else {
			gh.drawText(text("signal_mode", modeLabel(it)), dX, y, C_MUTED, false, GraphicsHolder.getDefaultLight());
			gh.drawText(text("signal_groups"), dX, detailY + 146, C_SECTION, false, GraphicsHolder.getDefaultLight());
		}

		final List<TrafficIntersectionGroup> groups = effectiveGroups(it);
		final TrafficIntersectionGroup selGroup = selectedGroup(it);
		if (it.level() == TrafficIntersectionLevel.CROSSING && selGroup != null) {
			gh.drawText(shortenToWidth(text("group_nodes", selectedPhaseIndex + 1, shorten(selGroup.name(), 16), selGroup.nodeNumbers()), dW),
				dX, detailY + 146 + GROUP_LIST_ROWS * ROW_STRIDE + 6, C_SECTION, false, GraphicsHolder.getDefaultLight());

			if (phaseDurRowY > 0) {
				final String durVal = String.format("%.1fs", selGroup.effectiveGreenDurationTicks() / 20.0);
				drawStepperRowText(gh, text("green_duration"), durVal, dX, dW, phaseDurRowY, C_OK);
			}
		}

		final String nodeLabel = selectedNodeLabel(it);
		if (selectedIntersectionNode != null) {
			final int nx = dX;
			final int ny = uiHeight() - 60;
			gh.drawText(shortenToWidth(text("node", nodeLabel), dW), nx, ny, C_SECTION, false, GraphicsHolder.getDefaultLight());
		}
	}

	private void renderVehiclePoolScreen(GraphicsHolder gh) {
		final ClientTrafficDashboardEntry entry = selectedEntry();
		final int lw = vehiclePoolListWidth();
		final int lx = vehiclePoolLeftX();
		final int rx = vehiclePoolRightX();
		final int top = 34;
		if (entry == null || entry.type() != TrafficPointType.SPAWN) {
			drawCenteredText(gh, text("select_spawn_first"), uiWidth() / 2, top, C_WHITE);
			return;
		}
		drawCenteredText(gh, text("vehicle_pool_title", shortEntryLabel(entry)), uiWidth() / 2, 10, C_WHITE);
		drawCenteredText(gh, text("available"),    lx + lw / 2, top, C_SECTION);
		drawCenteredText(gh, text("selected"),     rx + lw / 2, top, C_SECTION);
		gh.drawText(text("vehicles_page", filteredVehicleOptions.size(), vehiclePage + 1, maxVehiclePage() + 1),
			lx, top + 12, C_MUTED, false, GraphicsHolder.getDefaultLight());
		gh.drawText(text("selected_page", entry.effectiveVehiclePool().size(), selectedVehiclePage + 1, maxSelectedVehiclePage() + 1),
			rx, top + 12, C_MUTED, false, GraphicsHolder.getDefaultLight());
		gh.drawText(text("copied_pool", hasCopiedVehiclePool ? copiedVehiclePool.size() : 0), rx, top + 24, hasCopiedVehiclePool ? C_OK : C_MUTED, false, GraphicsHolder.getDefaultLight());
		if (filteredVehicleOptions.isEmpty())
			gh.drawText(text("no_vehicles_match"), lx, vehiclePoolListY(), C_WARN, false, GraphicsHolder.getDefaultLight());
		if (entry.effectiveVehiclePool().isEmpty())
			gh.drawText(text("nothing_selected"), rx, vehiclePoolListY(), C_MUTED, false, GraphicsHolder.getDefaultLight());
	}

	private void renderSocialButtonIcons(GraphicsHolder gh) {
		if (buttonZoomOut.visible) {
			drawButtonIcon(gh, KOFI_ICON, buttonZoomOut, 322, 259);
		}
		if (buttonZoomIn.visible) {
			drawButtonIcon(gh, DISCORD_ICON, buttonZoomIn, 800, 800);
		}
	}

	private void drawButtonIcon(GraphicsHolder gh, Identifier texture, ButtonWidgetExtension button, int sourceWidth, int sourceHeight) {
		final int padding = 3;
		final int boxX = button.getX2() + padding;
		final int boxY = button.getY2() + padding;
		final int boxW = Math.max(1, button.getWidth2() - padding * 2);
		final int boxH = Math.max(1, button.getHeight2() - padding * 2);
		final double sourceAspect = sourceWidth / (double) sourceHeight;
		final double boxAspect = boxW / (double) boxH;
		final double drawW;
		final double drawH;
		if (sourceAspect > boxAspect) {
			drawW = boxW;
			drawH = boxW / sourceAspect;
		} else {
			drawH = boxH;
			drawW = boxH * sourceAspect;
		}
		final double x1 = boxX + (boxW - drawW) / 2.0D;
		final double y1 = boxY + (boxH - drawH) / 2.0D;
		final GuiDrawing gd = new GuiDrawing(gh);
		gd.beginDrawingTexture(texture);
		gd.drawTexture(x1, y1, x1 + drawW, y1 + drawH, 0, 0, 1, 1);
		gd.finishDrawingTexture();
	}

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
		final boolean trainLevel = hasIntersection && it.level() == TrafficIntersectionLevel.TRAIN;
		final boolean crossingLevel = hasIntersection && it.level() == TrafficIntersectionLevel.CROSSING;
		final boolean isSpawn = hasEntry && entry.type() == TrafficPointType.SPAWN;
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
			final ButtonWidgetExtension locateButton = entryLocateButtons.get(i);
			final ButtonWidgetExtension poolButton = entryPoolButtons.get(i);
			if (narrowMap || vehiclePoolMode || i >= rows) {
				b.visible = false; b.active = false; b.setMessage(Component.literal(""));
				locateButton.visible = false; locateButton.active = false; locateButton.setMessage(Component.literal(""));
				poolButton.visible = false; poolButton.active = false; poolButton.setMessage(Component.literal(""));
				continue;
			}
			locateButton.visible = false; locateButton.active = false; locateButton.setMessage(Component.literal(""));
			poolButton.visible = false; poolButton.active = false; poolButton.setMessage(Component.literal(""));
			if (iMode && ei < filteredIntersections.size()) {
				final ClientTrafficIntersectionEntry li = filteredIntersections.get(ei);
				final int si = intersections.indexOf(li);
				b.visible = true; b.active = true;
				b.setMessage(Component.literal(
					(si == selectedIntersectionIndex ? "► " : "  ")
					+ shorten(li.effectiveName(), 18)
					+ "  " + levelLabel(li)
					+ "  " + (li.enabled() ? "●" : "○")
					+ "  " + text("nodes_short", li.nodes().size())));
			} else if (cMode && ei < filteredEntries.size()) {
				final ClientTrafficDashboardEntry le = filteredEntries.get(ei);
				b.visible = true; b.active = true;
				b.setMessage(Component.literal(
					(entries.indexOf(le) == selectedIndex ? "► " : "  ")
					+ shorten(le.effectiveName(), 22)
					+ "  " + (le.enabled() ? "●" : "○")
					+ "  " + text("vehicles_short", le.activeVehicles())
					+ "  " + text(le.hasConnectorRoute() ? "route_ok_short" : "route_missing_short")));
				if (editingConnectorName && selectedEntry() != null && le.id().equals(selectedEntry().id())) {
					b.visible = false;
					b.active = false;
				}
				final boolean rowSpawn = le.type() == TrafficPointType.SPAWN;
				locateButton.visible = rowSpawn;
				locateButton.active = rowSpawn;
				locateButton.setMessage(component("locate"));
				poolButton.visible = rowSpawn;
				poolButton.active = rowSpawn;
				poolButton.setMessage(component("pool"));
			} else {
				b.visible = false; b.active = false; b.setMessage(Component.literal(""));
			}
		}

		final List<TrafficIntersectionGroup> groups = it == null ? List.of() : effectiveGroups(it);
		for (int i = 0; i < GROUP_LIST_ROWS; i++) {
			final ButtonWidgetExtension gb  = intersectionGroupButtons.get(i);
			final ButtonWidgetExtension del = intersectionGroupDeleteBtns.get(i);
			if (iMode && crossingLevel && i < groups.size()) {
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
		buttonCopyVehiclePool.visible = vehiclePoolMode;
		buttonCopyVehiclePool.active = vehiclePoolMode && isSpawn;
		buttonPasteVehiclePool.visible = vehiclePoolMode;
		buttonPasteVehiclePool.active = vehiclePoolMode && isSpawn && hasCopiedVehiclePool;

		final boolean hasNode  = selectedIntersectionNode != null;
		final boolean hasGroup = it != null && selectedGroup(it) != null;

		buttonToggleEnabled.active  = (cMode ? hasEntry : hasIntersection);
		buttonToggleEnabled.visible = !vehiclePoolMode && !narrowMap;
		buttonToggleEnabled.setMessage(component(
			(dashboardSection == DashboardSection.INTERSECTIONS ? hasIntersection && it.enabled() : hasEntry && entry.enabled()) ? "disable" : "enable"));

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
		buttonIntersectionSignalMode.visible = iMode && (!hasIntersection || crossingLevel);
		buttonIntersectionSignalMode.active  = iMode && crossingLevel;
		buttonIntersectionSignalMode.setMessage(component(
			it != null && it.signalMode() == TrafficIntersectionSignalMode.AUTO ? "mode_auto" : "mode_manual"));
		buttonAutoDetectIntersection.visible = iMode;
		buttonAutoDetectIntersection.active  = iMode && hasIntersection;
		buttonIntersectionGroupAdd.visible   = iMode && (!hasIntersection || crossingLevel);
		buttonIntersectionGroupAdd.active    = iMode && crossingLevel;

		buttonIntersectionPhaseMinus.visible = iMode && (!hasIntersection || crossingLevel);
		buttonIntersectionPhaseMinus.active  = iMode && crossingLevel && hasGroup;
		buttonIntersectionPhasePlus.visible  = iMode && (!hasIntersection || crossingLevel);
		buttonIntersectionPhasePlus.active   = iMode && crossingLevel && hasGroup;
		final Integer selectedNodeNumber = selectedNodeNumber();
		buttonIntersectionPhaseAdd.visible    = iMode && (!hasIntersection || crossingLevel);
		buttonIntersectionPhaseAdd.active     = iMode && crossingLevel && hasGroup && selectedNodeNumber != null && selectedNodeIsIn();
		buttonIntersectionPhaseRemove.visible = iMode && (!hasIntersection || crossingLevel);
		buttonIntersectionPhaseRemove.active  = iMode && crossingLevel && hasGroup && selectedNodeNumber != null && selectedGroupNodeNumbers().contains(selectedNodeNumber);
		buttonToggleIntersectionNodeType.visible = iMode;
		buttonToggleIntersectionNodeType.active  = iMode && hasNode;
		buttonToggleIntersectionNodeType.setMessage(component(trainLevel ? (selectedNodeNumber != null && it.trainNodeNumbers().contains(selectedNodeNumber) ? "train_node_on" : "train_node_off") : "node_type"));

		buttonSectionConnectors.visible    = !vehiclePoolMode && !narrowMap;
		buttonSectionIntersections.visible = !vehiclePoolMode && !narrowMap;
		buttonAddIntersection.visible      = !vehiclePoolMode && !narrowMap && dashboardSection == DashboardSection.INTERSECTIONS;
		buttonAddIntersection.setMessage(component(drawingIntersection ? "cancel_area" : "draw_area"));

		buttonSectionConnectors.setMessage(Component.literal(
			(dashboardSection == DashboardSection.CONNECTORS ? "> " : "") + text("connectors_tab", entries.size())));
		buttonSectionIntersections.setMessage(Component.literal(
			(dashboardSection == DashboardSection.INTERSECTIONS ? "> " : "") + text("intersections_tab", intersections.size())));

		buttonZoomIn.visible  = !vehiclePoolMode && !narrowMap;
		buttonZoomOut.visible = !vehiclePoolMode && !narrowMap;
		buttonFitMap.visible = !vehiclePoolMode && !narrowMap;
		buttonFitMap.active = !entries.isEmpty() || !intersections.isEmpty();

		buttonBackToOverview.visible = vehiclePoolMode;
		buttonBackToOverview.active  = vehiclePoolMode;

		buttonToggleMap.visible = isNarrowMode() && !vehiclePoolMode;
		buttonToggleMap.setMessage(component(mapVisibleInNarrow ? "panel" : "map"));

		intersectionSearchField.setVisible2(iMode);
		connectorSearchField.setVisible2(cMode);
		connectorSearchField.setActiveMapped(cMode);
		connectorNameField.setVisible2(cMode && editingConnectorName && hasEntry);
		connectorNameField.setActiveMapped(cMode && editingConnectorName && hasEntry);
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

	}

	@Override
	public boolean keyPressed2(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if (editingConnectorName && connectorNameField.isFocused2()) {
				submitConnectorNameEdit();
				return true;
			}
			if (intersectionNameField.isFocused2() && dashboardSection == DashboardSection.INTERSECTIONS && selectedIntersection() != null) {
				submitIntersectionNameEdit();
				return true;
			}
		}
		return super.keyPressed2(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled2(double mx, double my, double amount) {
		mx = toUiMouse(mx);
		my = toUiMouse(my);
		if (panelMode == PanelMode.VEHICLE_POOL) {
			if (mx >= vehiclePoolRightX()) selectedVehiclePage = clamp(selectedVehiclePage + (amount < 0 ? 1 : -1), maxSelectedVehiclePage());
			else vehiclePage = clamp(vehiclePage + (amount < 0 ? 1 : -1), maxVehiclePage());
			refreshButtons();
			return true;
		}
		if (mx <= leftPanelWidth()) {
			entryPage = clamp(entryPage + (amount < 0 ? 1 : -1), maxEntryPage());
			layoutWidgets();
			refreshButtons();
			return true;
		}
		return widgetMap.mouseScrolled(mx, my, amount);
	}

	@Override
	public boolean mouseClicked2(double mx, double my, int button) {
		mx = toUiMouse(mx);
		my = toUiMouse(my);
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && tryToggleIntersectionLevelFromRow(mx, my)) {
			return true;
		}
		if (super.mouseClicked2(mx, my, button)) return true;
		if (panelMode != PanelMode.VEHICLE_POOL) return widgetMap.mouseClicked(mx, my, button);
		return false;
	}

	private boolean tryToggleIntersectionLevelFromRow(double mx, double my) {
		if (dashboardSection != DashboardSection.INTERSECTIONS || panelMode == PanelMode.VEHICLE_POOL || isNarrowMode() && mapVisibleInNarrow) {
			return false;
		}
		final int rows = visibleListRows();
		for (int i = 0; i < MAX_LIST_ROWS && i < rows; i++) {
			final ButtonWidgetExtension rowButton = entryButtons.get(i);
			if (!rowButton.visible || !rowButton.active) {
				continue;
			}
			if (mx < rowButton.getX2() || mx >= rowButton.getX2() + rowButton.getWidth2() || my < rowButton.getY2() || my >= rowButton.getY2() + rowButton.getHeight2()) {
				continue;
			}
			final int ei = entryPage * rows + i;
			if (ei >= filteredIntersections.size()) {
				return false;
			}
			final ClientTrafficIntersectionEntry clickedIntersection = filteredIntersections.get(ei);
			selectedIntersectionIndex = intersections.indexOf(clickedIntersection);
			selectedIntersectionNode = null;
			selectedPhaseIndex = 0;
			syncIntersectionNameField();
			sendIntersectionUpdate("level", 0, null);
			layoutWidgets();
			refreshButtons();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged2(double mx, double my, int button, double dx, double dy) {
		mx = toUiMouse(mx);
		my = toUiMouse(my);
		dx = toUiDistance(dx);
		dy = toUiDistance(dy);
		if (super.mouseDragged2(mx, my, button, dx, dy)) return true;
		if (panelMode != PanelMode.VEHICLE_POOL) return widgetMap.mouseDragged(mx, my, button, dx, dy);
		return false;
	}

	@Override
	public boolean mouseReleased2(double mx, double my, int button) {
		mx = toUiMouse(mx);
		my = toUiMouse(my);
		if (panelMode != PanelMode.VEHICLE_POOL) widgetMap.mouseReleased(mx, my, button);
		return super.mouseReleased2(mx, my, button);
	}

	@Override public boolean isPauseScreen2() { return false; }
	@Override public void onClose2()          { widgetMap.onClose(); super.onClose2(); }

	private double currentGuiScale() {
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getWindow() == null) {
			return FIXED_GUI_SCALE;
		}
		return Math.max(1.0D, minecraft.getWindow().getGuiScale());
	}

	private double uiRenderScale() {
		return FIXED_GUI_SCALE / currentGuiScale();
	}

	private int uiWidth() {
		return Math.max(1, (int) Math.round(width / uiRenderScale()));
	}

	private int uiHeight() {
		return Math.max(1, (int) Math.round(height / uiRenderScale()));
	}

	private int toUiMouse(int value) {
		return (int) Math.round(value / uiRenderScale());
	}

	private double toUiMouse(double value) {
		return value / uiRenderScale();
	}

	private double toUiDistance(double value) {
		return value / uiRenderScale();
	}

	private void ensureFixedScaleLayout() {
		final int currentUiWidth = uiWidth();
		final int currentUiHeight = uiHeight();
		if (currentUiWidth != lastLayoutUiWidth || currentUiHeight != lastLayoutUiHeight) {
			lastLayoutUiWidth = currentUiWidth;
			lastLayoutUiHeight = currentUiHeight;
			layoutWidgets();
			refreshButtons();
		}
	}

	private boolean isNarrowMode() { return uiWidth() < NARROW_THRESHOLD; }

	private int leftPanelWidth() {
		final int w = uiWidth();
		if (panelMode == PanelMode.VEHICLE_POOL)    return w;
		if (isNarrowMode() && mapVisibleInNarrow)   return 0;
		if (isNarrowMode())                         return w;
		final int desired = (int)(w * 0.42);
		return Math.max(PANEL_MIN_WIDTH, Math.min(PANEL_MAX_WIDTH, Math.min(w - MAP_MIN_WIDTH, desired)));
	}

	private int visibleListRows() {
		final int searchH = ROW_H + 4;
		final int reservedAfterList = dashboardSection == DashboardSection.INTERSECTIONS
			? intersectionReservedHeightAfterList()
			: connectorReservedHeightAfterList();
		final int reserved = LIST_START_Y + searchH + reservedAfterList;
		return Math.max(3, Math.min(MAX_LIST_ROWS, (uiHeight() - reserved) / ROW_STRIDE));
	}

	private int connectorDetailsHeight() {
		return 74;
	}

	private int connectorControlsHeight() {
		return SQUARE_SIZE * 3 + GAP * 2;
	}

	private int connectorReservedHeightAfterList() {
		return 2 + SQUARE_SIZE + PAGE_TO_DETAILS_GAP + connectorDetailsHeight() + GAP + connectorControlsHeight() + MARGIN;
	}

	private static final int INTERSECTION_LIST_WIDTH = 260;
	private int intersectionListWidth(int cw) { return Math.min(INTERSECTION_LIST_WIDTH, Math.max(140, cw / 2 - 6)); }
	private int intersectionReservedHeightAfterList() {
		return 2 + SQUARE_SIZE + MARGIN;
	}
	private int idX(int cw)      { return MARGIN + intersectionListWidth(cw) + 8; }
	private int idWidth(int cw)  { return Math.max(160, cw - intersectionListWidth(cw) - 8); }

	private int vehiclePoolListWidth() { return Math.min(340, Math.max(140, (uiWidth() - 80) / 2)); }
	private int vehiclePoolLeftX()     { return uiWidth() / 2 - vehiclePoolListWidth() - 20; }
	private int vehiclePoolRightX()    { return uiWidth() / 2 + 20; }
	private int vehiclePoolListY()     { return 76; }

	private void selectConnectorIndex(int index, boolean focusMap) {
		if (index < 0 || index >= entries.size()) {
			return;
		}
		selectConnector(entries.get(index), focusMap);
	}

	private void openExternalLink(String url) {
		ConfirmLinkScreen.confirmLinkNow(url, this, true);
	}

	private void selectConnector(ClientTrafficDashboardEntry entry, boolean focusMap) {
		final int index = entries.indexOf(entry);
		if (index < 0) {
			return;
		}
		selectedIndex = index;
		final int filteredIndex = filteredEntries.indexOf(entry);
		if (filteredIndex >= 0) {
			entryPage = filteredIndex / visibleListRows();
		}
		panelMode = PanelMode.OVERVIEW;
		if (focusMap) {
			widgetMap.focusOn(entry);
		}
		if (!editingConnectorName) {
			syncConnectorNameField();
		}
		layoutWidgets();
		refreshButtons();
	}

	private void selectEntry(ClientTrafficDashboardEntry entry) {
		if (!entries.contains(entry)) {
			return;
		}
		editingConnectorName = false;
		selectConnector(entry, false);
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
		final int size = dashboardSection == DashboardSection.INTERSECTIONS ? filteredIntersections.size() : filteredEntries.size();
		final int rows = visibleListRows();
		return Math.max(0, (size - 1) / rows);
	}
	private int maxVehiclePage()         { return Math.max(0, (filteredVehicleOptions.size() - 1) / AVAIL_VEH_PER_PAGE); }
	private int maxSelectedVehiclePage() { final ClientTrafficDashboardEntry e = selectedEntry(); return Math.max(0, ((e == null ? 0 : e.effectiveVehiclePool().size()) - 1) / SELECTED_VEH_ROWS); }

	private void changeEntryPage(int delta) {
		entryPage = clamp(entryPage + delta, maxEntryPage());
		layoutWidgets();
		refreshButtons();
	}

	private void changeVehiclePage(int delta) {
		vehiclePage = clamp(vehiclePage + delta, maxVehiclePage());
		refreshButtons();
	}

	private void changeSelectedVehiclePage(int delta) {
		selectedVehiclePage = clamp(selectedVehiclePage + delta, maxSelectedVehiclePage());
		refreshButtons();
	}

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
		for (TrafficIntersectionNode node : it.nodes()) {
			if (node.x() == nx && node.z() == nz) {
				selectedIntersectionNode = node.x() + "," + node.y() + "," + node.z();
				refreshButtons();
				return;
			}
		}
	}

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

	private void refreshFilteredEntries() {
		filteredEntries.clear();
		final String q = connectorSearchQuery.toLowerCase(java.util.Locale.ROOT);
		for (ClientTrafficDashboardEntry entry : entries) {
			final String summary = (
				entry.effectiveName() + " "
					+ entry.type().name() + " "
					+ entry.blockPos().getX() + " "
					+ entry.blockPos().getY() + " "
					+ entry.blockPos().getZ() + " "
					+ (entry.enabled() ? "enabled " : "disabled ")
					+ (entry.hasConnectorRoute() ? "ready " : "missing ")
					+ entry.activeVehicles() + " "
					+ entry.id()
			).toLowerCase(java.util.Locale.ROOT);
			if (q.isBlank() || summary.contains(q)) {
				filteredEntries.add(entry);
			}
		}
		entryPage = Math.min(entryPage, maxEntryPage());
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

	private void syncConnectorNameField() {
		updatingConnectorNameField = true;
		try { final ClientTrafficDashboardEntry entry = selectedEntry(); connectorNameField.setText2(entry == null ? "" : entry.effectiveName()); }
		finally { updatingConnectorNameField = false; }
	}

	private void startConnectorNameEdit(ButtonWidgetExtension sourceButton) {
		final ClientTrafficDashboardEntry entry = selectedEntry();
		if (entry == null || dashboardSection != DashboardSection.CONNECTORS) {
			return;
		}
		connectorNameEditX = sourceButton.getX2();
		connectorNameEditY = sourceButton.getY2();
		connectorNameEditWidth = sourceButton.getWidth2();
		editingConnectorName = true;
		syncConnectorNameField();
		layoutWidgets();
		refreshButtons();
		connectorNameField.setTextFieldFocused2(true);
		connectorNameField.setSelectionStart2(0);
		connectorNameField.setSelectionEnd2(connectorNameField.getText2().length());
	}

	private void submitConnectorNameEdit() {
		if (!editingConnectorName || selectedEntry() == null) {
			return;
		}
		final String submittedName = connectorNameField.getText2();
		editingConnectorName = false;
		connectorNameField.setTextFieldFocused2(false);
		sendUpdate("name", 0, submittedName == null ? "" : submittedName);
		layoutWidgets();
		refreshButtons();
	}

	private void submitIntersectionNameEdit() {
		if (selectedIntersection() == null) {
			return;
		}
		final String submittedName = intersectionNameField.getText2();
		intersectionNameField.setTextFieldFocused2(false);
		sendIntersectionUpdate("name", 0, submittedName == null ? "" : submittedName);
		refreshButtons();
	}

	private void openVehiclePool() {
		if (selectedEntry() != null && selectedEntry().type() == TrafficPointType.SPAWN) {
			panelMode = PanelMode.VEHICLE_POOL; layoutWidgets(); refreshButtons();
		}
	}

	private String selectedIntersectionNode() { return selectedIntersectionNode; }

	private List<Integer> selectedGroupNodeNumbers() {
		final ClientTrafficIntersectionEntry it = selectedIntersection();
		if (it != null && it.level() == TrafficIntersectionLevel.TRAIN) {
			return it.trainNodeNumbers().isEmpty()
				? it.nodes().stream()
					.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
					.map(TrafficIntersectionNode::number)
					.distinct()
					.sorted()
					.toList()
				: it.trainNodeNumbers();
		}
		final TrafficIntersectionGroup g = it == null ? null : selectedGroup(it);
		return g == null ? List.of() : g.nodeNumbers();
	}

	private Integer selectedNodeNumber() {
		final ClientTrafficIntersectionEntry it = selectedIntersection();
		if (it == null || selectedIntersectionNode == null) return null;
		for (TrafficIntersectionNode node : it.nodes())
			if (selectedIntersectionNode.equals(node.x() + "," + node.y() + "," + node.z())) return node.number();
		return null;
	}

	private boolean selectedNodeIsIn() {
		final ClientTrafficIntersectionEntry it = selectedIntersection();
		if (it == null || selectedIntersectionNode == null) return false;
		for (TrafficIntersectionNode node : it.nodes())
			if (selectedIntersectionNode.equals(node.x() + "," + node.y() + "," + node.z())) return node.type() == TrafficIntersectionNodeType.IN;
		return false;
	}

	private static boolean containsNode(ClientTrafficIntersectionEntry it, String enc) {
		for (TrafficIntersectionNode n : it.nodes())
			if (enc.equals(n.x() + "," + n.y() + "," + n.z())) return true;
		return false;
	}

	private static List<Integer> effectivePhaseOrder(ClientTrafficIntersectionEntry it) {
		if (!it.phaseOrder().isEmpty()) return it.phaseOrder();
		return it.nodes().stream()
			.filter(n -> n.type() == TrafficIntersectionNodeType.IN)
			.map(TrafficIntersectionNode::number)
			.distinct().sorted().toList();
	}

	private static List<TrafficIntersectionGroup> effectiveGroups(ClientTrafficIntersectionEntry it) {
		if (!it.groups().isEmpty()) return it.groups();
		return effectivePhaseOrder(it).stream()
			.map(n -> new TrafficIntersectionGroup(text("default_group_name", n), it.phaseDurationTicks(), List.of(n)))
			.toList();
	}

	private TrafficIntersectionGroup selectedGroup(ClientTrafficIntersectionEntry it) {
		final List<TrafficIntersectionGroup> gs = effectiveGroups(it);
		return selectedPhaseIndex >= 0 && selectedPhaseIndex < gs.size() ? gs.get(selectedPhaseIndex) : null;
	}

	private String selectedNodeLabel(ClientTrafficIntersectionEntry it) {
		if (selectedIntersectionNode == null) return text("none");
		for (TrafficIntersectionNode n : it.nodes()) {
			if (selectedIntersectionNode.equals(n.x() + "," + n.y() + "," + n.z())) {
				final List<TrafficIntersectionGroup> gs = effectiveGroups(it);
				final List<Integer> gIdxs = new ArrayList<>();
				for (int i = 0; i < gs.size(); i++) if (gs.get(i).nodeNumbers().contains(n.number())) gIdxs.add(i + 1);
				if (it.level() == TrafficIntersectionLevel.TRAIN) {
					return text(
						it.trainNodeNumbers().contains(n.number()) ? "node_label_train" : "node_label_not_train",
						n.type(), n.number(), n.x(), n.z()
					);
				}
				return text(
					gIdxs.isEmpty() ? "node_label_unassigned" : "node_label_groups",
					n.type(), n.number(), n.x(), n.z(), gIdxs
				);
			}
		}
		return selectedIntersectionNode;
	}

	private static String modeLabel(ClientTrafficIntersectionEntry it) {
		return text(it.signalMode() == TrafficIntersectionSignalMode.AUTO ? "mode_auto" : "mode_manual");
	}

	private static String levelLabel(ClientTrafficIntersectionEntry it) {
		return text(it.level() == TrafficIntersectionLevel.TRAIN ? "level_train" : "level_crossing");
	}

	private static String trainNodeNumbersLabel(ClientTrafficIntersectionEntry it) {
		return it.trainNodeNumbers().isEmpty() ? text("all_detected_nodes") : it.trainNodeNumbers().toString();
	}

	private String shortEntryLabel(ClientTrafficDashboardEntry e) {
		return (e.type() == TrafficPointType.SPAWN ? "S" : "D") + " @ " + e.blockPos().getX() + "," + e.blockPos().getZ();
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
		if (panelMode == PanelMode.VEHICLE_POOL) return text("hint_vehicle_pool");
		if (dashboardSection == DashboardSection.INTERSECTIONS) return text("hint_intersections");
		return text("hint_connectors");
	}

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

	private static String shortenToWidth(String s, int maxWidth) {
		if (s == null || GraphicsHolder.getTextWidth(s) <= maxWidth) {
			return s;
		}
		final String suffix = "...";
		int length = s.length();
		while (length > 0 && GraphicsHolder.getTextWidth(s.substring(0, length) + suffix) > maxWidth) {
			length--;
		}
		return length <= 0 ? suffix : s.substring(0, length) + suffix;
	}

	private static void drawCenteredText(GraphicsHolder gh, String text, int cx, int y, int color) {
		gh.drawText(text, cx - GraphicsHolder.getTextWidth(text) / 2, y, color, false, GraphicsHolder.getDefaultLight());
	}

	private static void drawStepperRowText(GraphicsHolder gh, String label, String value, int x, int width, int y, int valueColor) {
		final int labelX = x + INLINE_BTN_W + GAP;
		final int valueW = GraphicsHolder.getTextWidth(value);
		final int valueX = x + width - INLINE_BTN_W - GAP - valueW;
		gh.drawText(label, labelX, y + 5, C_MUTED, false, GraphicsHolder.getDefaultLight());
		gh.drawText(value, valueX, y + 5, valueColor, false, GraphicsHolder.getDefaultLight());
	}

	private record VehicleOption(String id, String label) {}
	private enum PanelMode      { OVERVIEW, VEHICLE_POOL }
	private enum DashboardSection { CONNECTORS, INTERSECTIONS }
}
