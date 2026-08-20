package com.cookiecraftmods.mta.traffic.vehicle;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class TrafficVehicleDefinitionRegistry implements SimpleSynchronousResourceReloadListener {
	private static final Gson GSON = new GsonBuilder().create();
	private static final ResourceLocation RELOAD_LISTENER_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "traffic_vehicle_definitions");
	private static final String VEHICLE_DIRECTORY = "traffic_vehicles";
	private static final Map<String, TrafficVehicleDefinition> DEFINITIONS = new LinkedHashMap<>();
	private static boolean initialized;

	private TrafficVehicleDefinitionRegistry() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new TrafficVehicleDefinitionRegistry());
		initialized = true;
	}

	public static Collection<TrafficVehicleDefinition> getDefinitions() {
		return DEFINITIONS.values();
	}

	public static Optional<TrafficVehicleDefinition> getDefinition(String id) {
		return Optional.ofNullable(DEFINITIONS.get(id));
	}

	public static Optional<TrafficVehicleDefinition> getAnyDefinition() {
		return DEFINITIONS.values().stream().findFirst();
	}

	@Override
	public ResourceLocation getFabricId() {
		return RELOAD_LISTENER_ID;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		final Map<String, TrafficVehicleDefinition> updatedDefinitions = new LinkedHashMap<>();

		for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(VEHICLE_DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
				final TrafficVehicleDefinition definition = GSON.fromJson(reader, TrafficVehicleDefinition.class);
				validate(definition, entry.getKey());
				updatedDefinitions.put(definition.id(), definition);
				MTRTrafficAddon.LOGGER.info("Loaded traffic vehicle definition {} type={} length={} maxSpeed={} visual={}", definition.id(), definition.type(), definition.lengthMeters(), definition.maxSpeedKph(), definition.effectiveVisualId());
			} catch (Exception e) {
				MTRTrafficAddon.LOGGER.error("Failed to load traffic vehicle definition from {}", entry.getKey(), e);
			}
		}

		DEFINITIONS.clear();
		DEFINITIONS.putAll(updatedDefinitions);
		MTRTrafficAddon.LOGGER.info("Loaded {} traffic vehicle definitions", DEFINITIONS.size());
	}

	private static void validate(TrafficVehicleDefinition definition, ResourceLocation resourceId) {
		if (definition == null) {
			throw new IllegalArgumentException("Definition is null");
		}
		if (definition.id() == null || definition.id().isBlank()) {
			throw new IllegalArgumentException("Missing id in " + resourceId);
		}
		if (definition.type() == null || definition.type().isBlank()) {
			throw new IllegalArgumentException("Missing type in " + resourceId);
		}
		if (definition.lengthMeters() <= 0.0D) {
			throw new IllegalArgumentException("lengthMeters must be positive in " + resourceId);
		}
		if (definition.maxSpeedKph() <= 0.0D) {
			throw new IllegalArgumentException("maxSpeedKph must be positive in " + resourceId);
		}
		if (definition.spawnWeight() <= 0) {
			throw new IllegalArgumentException("spawnWeight must be positive in " + resourceId);
		}
	}
}
