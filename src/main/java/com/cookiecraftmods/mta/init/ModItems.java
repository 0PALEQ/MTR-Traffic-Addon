package com.cookiecraftmods.mta.init;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.dashboard.item.TrafficDashboardItem;
import com.cookiecraftmods.mta.traffic.lights.item.TrafficLightsPrimaryBlockItem;
import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import com.cookiecraftmods.mta.traffic.point.connector.TrafficConnectorItem;
import com.cookiecraftmods.mta.traffic.rail.MtaExclusiveRailConnectorItem;
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
	public static final Item MTA_RAIL_CONNECTOR_20 = registerItem("mta_rail_connector_20", new MtaExclusiveRailConnectorItem(20));
	public static final Item MTA_RAIL_CONNECTOR_30 = registerItem("mta_rail_connector_30", new MtaExclusiveRailConnectorItem(30));
	public static final Item MTA_RAIL_CONNECTOR_40 = registerItem("mta_rail_connector_40", new MtaExclusiveRailConnectorItem(40));
	public static final Item MTA_RAIL_CONNECTOR_50 = registerItem("mta_rail_connector_50", new MtaExclusiveRailConnectorItem(50));
	public static final Item MTA_RAIL_CONNECTOR_60 = registerItem("mta_rail_connector_60", new MtaExclusiveRailConnectorItem(60));
	public static final Item MTA_RAIL_CONNECTOR_70 = registerItem("mta_rail_connector_70", new MtaExclusiveRailConnectorItem(70));
	public static final Item MTA_RAIL_CONNECTOR_80 = registerItem("mta_rail_connector_80", new MtaExclusiveRailConnectorItem(80));
	public static final Item MTA_RAIL_CONNECTOR_90 = registerItem("mta_rail_connector_90", new MtaExclusiveRailConnectorItem(90));
	public static final Item MTA_RAIL_CONNECTOR_100 = registerItem("mta_rail_connector_100", new MtaExclusiveRailConnectorItem(100));
	public static final Item MTA_RAIL_CONNECTOR_110 = registerItem("mta_rail_connector_110", new MtaExclusiveRailConnectorItem(110));
	public static final Item MTA_RAIL_CONNECTOR_120 = registerItem("mta_rail_connector_120", new MtaExclusiveRailConnectorItem(120));
	public static final Item TRAFFIC_LIGHTS_POLE_BOTTOM = registerBlockItem("traffic_lights_pole_bottom", ModBlocks.TRAFFIC_LIGHTS_POLE_BOTTOM);
	public static final Item TRAFFIC_LIGHTS_POLE = registerBlockItem("traffic_lights_pole", ModBlocks.TRAFFIC_LIGHTS_POLE);
	public static final Item TRAFFIC_LIGHTS_VERTICAL_POLE = registerBlockItem("traffic_lights_vertical_pole", ModBlocks.TRAFFIC_LIGHTS_VERTICAL_POLE);
	public static final Item TRAFFIC_LIGHTS_PRIMARY = registerItem("traffic_lights_primary", new TrafficLightsPrimaryBlockItem(ModBlocks.TRAFFIC_LIGHTS_PRIMARY, new Item.Properties()));

	private ModItems() {
	}

	public static void initialize() {
	}

	private static Item registerItem(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MTRTrafficAddon.MOD_ID, path), item);
	}

	private static Item registerBlockItem(String path, Block block) {
		return registerItem(path, new BlockItem(block, new Item.Properties()));
	}
}
