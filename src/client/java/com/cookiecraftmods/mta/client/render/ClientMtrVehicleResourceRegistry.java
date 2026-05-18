package com.cookiecraftmods.mta.client.render;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ClientMtrVehicleResourceRegistry implements SimpleSynchronousResourceReloadListener {
	private static final Gson GSON = new Gson();
	private static final ResourceLocation RELOAD_LISTENER_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "client_mtr_vehicle_resources");
	private static final ResourceLocation CUSTOM_RESOURCES_ID = new ResourceLocation("mtr", "mtr_custom_resources.json");
	private static final ResourceLocation CUSTOM_RESOURCES_PENDING_MIGRATION_ID = new ResourceLocation("mtr", "mtr_custom_resources_pending_migration.json");
	private static final Map<String, VisualDefinition> DEFINITIONS = new LinkedHashMap<>();
	private static boolean initialized;

	private ClientMtrVehicleResourceRegistry() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new ClientMtrVehicleResourceRegistry());
		initialized = true;
	}

	public static Optional<VisualDefinition> get(String id) {
		return Optional.ofNullable(DEFINITIONS.get(id));
	}

	public static Collection<VisualDefinition> all() {
		return List.copyOf(DEFINITIONS.values());
	}

	@Override
	public ResourceLocation getFabricId() {
		return RELOAD_LISTENER_ID;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		DEFINITIONS.clear();
		loadCustomResources(manager, CUSTOM_RESOURCES_ID);
		loadCustomResources(manager, CUSTOM_RESOURCES_PENDING_MIGRATION_ID);
		MTRTrafficAddon.LOGGER.info("Indexed {} MTR custom vehicle visual definitions for traffic rendering", DEFINITIONS.size());
	}

	private static void loadCustomResources(ResourceManager manager, ResourceLocation resourceLocation) {
		final List<net.minecraft.server.packs.resources.Resource> resources = manager.getResourceStack(resourceLocation);
		for (net.minecraft.server.packs.resources.Resource resource : resources) {
			try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
				final JsonElement rootElement = GSON.fromJson(reader, JsonElement.class);
				if (rootElement != null && rootElement.isJsonObject()) {
					final JsonObject root = rootElement.getAsJsonObject();
					loadVehiclesArray(root.getAsJsonArray("vehicles"));
					loadLegacyCustomTrains(root.getAsJsonObject("custom_trains"));
				} else {
					MTRTrafficAddon.LOGGER.warn("Skipping MTR custom vehicle resource {} because the root JSON value is not an object", resourceLocation);
				}
			} catch (Exception e) {
				MTRTrafficAddon.LOGGER.error("Failed to load MTR custom vehicle resources from {}", resourceLocation, e);
			}
		}
	}

	private static void loadVehiclesArray(JsonArray vehicles) {
		if (vehicles == null) {
			return;
		}

		for (JsonElement element : vehicles) {
			if (!element.isJsonObject()) {
				continue;
			}

			final JsonObject object = element.getAsJsonObject();
			final String id = stringValue(object, "id");
			if (id == null || id.isBlank()) {
				continue;
			}

			DEFINITIONS.put(id, new VisualDefinition(
				id,
				stringValue(object, "name"),
				doubleValue(object, "length"),
				doubleValue(object, "width"),
				doubleValue(object, "height")
			));
		}
	}

	private static void loadLegacyCustomTrains(JsonObject customTrains) {
		if (customTrains == null) {
			return;
		}

		for (Map.Entry<String, JsonElement> entry : customTrains.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}

			final JsonObject object = entry.getValue().getAsJsonObject();
			DEFINITIONS.putIfAbsent(entry.getKey(), new VisualDefinition(
				entry.getKey(),
				stringValue(object, "name"),
				doubleValue(object, "length"),
				doubleValue(object, "width"),
				doubleValue(object, "height")
			));
		}
	}

	private static String stringValue(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
	}

	private static double doubleValue(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsDouble() : 0.0D;
	}

	public record VisualDefinition(
		String id,
		String name,
		double lengthMeters,
		double widthMeters,
		double heightMeters
	) {
	}
}
