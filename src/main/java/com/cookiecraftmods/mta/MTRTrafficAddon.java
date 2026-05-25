package com.cookiecraftmods.mta;

import com.cookiecraftmods.mta.compat.MagicCompat;
import com.cookiecraftmods.mta.config.TrafficAddonConfig;
import com.cookiecraftmods.mta.init.ModBlockEntities;
import com.cookiecraftmods.mta.init.ModBlocks;
import com.cookiecraftmods.mta.init.ModCreativeTabs;
import com.cookiecraftmods.mta.init.ModItems;
import com.cookiecraftmods.mta.traffic.dashboard.network.TrafficDashboardNetworking;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionRegistry;
import com.cookiecraftmods.mta.traffic.lights.TrafficLightBindingRegistry;
import com.cookiecraftmods.mta.traffic.lights.network.TrafficLightBindingNetworking;
import com.cookiecraftmods.mta.traffic.network.TrafficNetworking;
import com.cookiecraftmods.mta.traffic.TrafficManager;
import com.cookiecraftmods.mta.traffic.point.TrafficSavedPointRegistry;
import com.cookiecraftmods.mta.traffic.rail.MtaExclusiveRailNetworking;
import com.cookiecraftmods.mta.traffic.rail.MtaExclusiveRailRegistry;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinitionRegistry;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MTRTrafficAddon implements ModInitializer {
	public static final String MOD_ID = "mtr-traffic-addon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TrafficAddonConfig.load();
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModItems.initialize();
		ModCreativeTabs.initialize();
		TrafficVehicleDefinitionRegistry.initialize();
		TrafficSavedPointRegistry.initialize();
		MtaExclusiveRailRegistry.initialize();
		MtaExclusiveRailNetworking.initialize();
		TrafficIntersectionRegistry.initialize();
		TrafficLightBindingNetworking.initialize();
		TrafficLightBindingRegistry.initialize();
		TrafficDashboardNetworking.initialize();
		TrafficNetworking.initialize();
		TrafficManager.initialize();
		if (MagicCompat.isMagicLoaded()) {
			LOGGER.info("MAGIC addon detected; enabling compatibility guard for MTR traffic blocker probes.");
		}
		LOGGER.info("Initialized {}", MOD_ID);
	}
}
