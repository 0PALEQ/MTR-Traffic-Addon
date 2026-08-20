package com.cookiecraftmods.mta.init;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.dashboard.item.TrafficDashboardItem;
import com.cookiecraftmods.mta.traffic.lights.item.TrafficLightsPrimaryBlockItem;
import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import com.cookiecraftmods.mta.traffic.point.connector.TrafficConnectorItem;
import com.cookiecraftmods.mta.traffic.signal.SignalPathBlocker;
import com.cookiecraftmods.mta.traffic.signal.SignalPathBlockerConnectorItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModItems {

	public static final Item TRAFFIC_DASHBOARD = registerItem("traffic_dashboard", new TrafficDashboardItem(new Item.Properties().stacksTo(1)));

	public static final Item TRAFFIC_SPAWN_CONNECTOR = registerItem("traffic_spawn_connector", new TrafficConnectorItem(TrafficPointType.SPAWN));
	public static final Item TRAFFIC_DESPAWN_CONNECTOR = registerItem("traffic_despawn_connector", new TrafficConnectorItem(TrafficPointType.DESPAWN));
	public static final Item SIGNAL_PATH_BLOCKER_CONNECTOR = registerItem("signal_path_blocker_connector", new SignalPathBlockerConnectorItem(SignalPathBlocker.MTR_STYLE, "signal_path_blocker_connector"));
	public static final Item MTA_PATH_BLOCKER_CONNECTOR = registerItem("mta_path_blocker_connector", new SignalPathBlockerConnectorItem(SignalPathBlocker.MTA_STYLE, "mta_path_blocker_connector"));

	public static final Item TRAFFIC_LIGHTS_POLE_BOTTOM = registerBlockItem("traffic_lights_pole_bottom", ModBlocks.TRAFFIC_LIGHTS_POLE_BOTTOM);
	public static final Item TRAFFIC_LIGHTS_POLE = registerBlockItem("traffic_lights_pole", ModBlocks.TRAFFIC_LIGHTS_POLE);
	public static final Item TRAFFIC_LIGHTS_VERTICAL_POLE = registerBlockItem("traffic_lights_vertical_pole", ModBlocks.TRAFFIC_LIGHTS_VERTICAL_POLE);
	public static final Item TRAFFIC_LIGHTS_PRIMARY = registerItem("traffic_lights_primary", new TrafficLightsPrimaryBlockItem(ModBlocks.TRAFFIC_LIGHTS_PRIMARY, new Item.Properties()));
	public static final Item PEDESTRIAN_LIGHTS = registerBlockItem("pedestrian_lights", ModBlocks.PEDESTRIAN_LIGHTS);
	public static final Item PEDESTRIAN_LIGHTS_POLE = registerBlockItem("pedestrian_lights_pole", ModBlocks.PEDESTRIAN_LIGHTS_POLE);
	public static final Item TOLLGATE_POLE = registerBlockItem("tollgate_pole", ModBlocks.TOLLGATE_POLE);
	public static final Item TOLLGATE_BAR = registerBlockItem("tollgate_bar", ModBlocks.TOLLGATE_BAR);
	public static final Item ROAD_SIGN = registerBlockItem("road_sign", ModBlocks.ROAD_SIGN);

	private ModItems() {
	}

	public static void initialize() {
	}

	private static Item registerItem(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MTRTrafficAddon.MOD_ID, path), item);
	}

	private static Item registerBlockItem(String path, Block block) {
		return registerItem(path, new BlockItem(block, new Item.Properties()));
	}
}
