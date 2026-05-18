package com.cookiecraftmods.mta.init;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.dashboard.item.TrafficDashboardItem;
import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import com.cookiecraftmods.mta.traffic.point.connector.TrafficConnectorItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item TRAFFIC_DASHBOARD = registerItem("traffic_dashboard", new TrafficDashboardItem(new Item.Properties().stacksTo(1)));
	public static final Item TRAFFIC_SPAWN_CONNECTOR = registerItem("traffic_spawn_connector", new TrafficConnectorItem(TrafficPointType.SPAWN));
	public static final Item TRAFFIC_DESPAWN_CONNECTOR = registerItem("traffic_despawn_connector", new TrafficConnectorItem(TrafficPointType.DESPAWN));

	private ModItems() {
	}

	public static void initialize() {
	}

	private static Item registerItem(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MTRTrafficAddon.MOD_ID, path), item);
	}
}
