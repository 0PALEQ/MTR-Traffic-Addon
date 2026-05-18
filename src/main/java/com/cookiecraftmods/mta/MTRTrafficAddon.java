package com.cookiecraftmods.mta;

import com.cookiecraftmods.mta.init.ModCreativeTabs;
import com.cookiecraftmods.mta.init.ModItems;
import com.cookiecraftmods.mta.traffic.dashboard.network.TrafficDashboardNetworking;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionRegistry;
import com.cookiecraftmods.mta.traffic.network.TrafficNetworking;
import com.cookiecraftmods.mta.traffic.TrafficManager;
import com.cookiecraftmods.mta.traffic.point.TrafficSavedPointRegistry;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinitionRegistry;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MTRTrafficAddon implements ModInitializer {
	public static final String MOD_ID = "mtr-traffic-addon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.initialize();
		ModCreativeTabs.initialize();
		TrafficVehicleDefinitionRegistry.initialize();
		TrafficSavedPointRegistry.initialize();
		TrafficIntersectionRegistry.initialize();
		TrafficDashboardNetworking.initialize();
		TrafficNetworking.initialize();
		TrafficManager.initialize();
		LOGGER.info("Initialized {}", MOD_ID);
	}
}
