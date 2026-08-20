package com.cookiecraftmods.mta.client.render.custom;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CustomTrafficModelRegistry implements SimpleSynchronousResourceReloadListener {
	private static final Gson GSON = new Gson();
	private static final ResourceLocation RELOAD_LISTENER_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "custom_traffic_models");
	private static final String MODEL_DEFINITION_DIRECTORY = "traffic_models";
	private static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
	private static final Map<String, CustomTrafficModel> MODELS = new LinkedHashMap<>();
	private static boolean initialized;

	private CustomTrafficModelRegistry() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new CustomTrafficModelRegistry());
		initialized = true;
	}

	public static Optional<CustomTrafficModel> get(String id) {
		return Optional.ofNullable(MODELS.get(id));
	}

	public static Collection<CustomTrafficModel> all() {
		return List.copyOf(MODELS.values());
	}

	@Override
	public ResourceLocation getFabricId() {
		return RELOAD_LISTENER_ID;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		final Map<String, CustomTrafficModel> updatedModels = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(MODEL_DEFINITION_DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
				final JsonObject root = GSON.fromJson(reader, JsonObject.class);
				final CustomTrafficModelDefinition definition = readDefinition(entry.getKey(), root);
				final List<TrafficMeshFace> faces = loadFaces(manager, definition);
				if (faces.isEmpty()) {
					MTRTrafficAddon.LOGGER.warn("Skipping custom traffic model {} because it has no renderable faces", definition.id());
					continue;
				}
				updatedModels.put(definition.id(), new CustomTrafficModel(definition, faces));
			} catch (Exception e) {
				MTRTrafficAddon.LOGGER.error("Failed to load custom traffic model definition {}", entry.getKey(), e);
			}
		}

		MODELS.clear();
		MODELS.putAll(updatedModels);
		MTRTrafficAddon.LOGGER.info("Loaded {} custom traffic model(s)", MODELS.size());
	}

	private static CustomTrafficModelDefinition readDefinition(ResourceLocation resourceId, JsonObject root) {
		if (root == null) {
			throw new IllegalArgumentException("Definition root is null");
		}

		final String id = stringValue(root, "id", resourceId.getNamespace() + ":" + resourceId.getPath().substring(MODEL_DEFINITION_DIRECTORY.length() + 1, resourceId.getPath().length() - ".json".length()));
		final ResourceLocation model = resourceLocationValue(root, "model", resourceId);
		final ResourceLocation texture = resourceLocationValue(root, "texture", DEFAULT_TEXTURE);
		return new CustomTrafficModelDefinition(
			id,
			stringValue(root, "format", ""),
			model,
			texture,
			doubleValue(root, "scale", 1.0D),
			vectorValue(root, "offset", 0, 0.0D),
			vectorValue(root, "offset", 1, 0.0D),
			vectorValue(root, "offset", 2, 0.0D),
			vectorValue(root, "rotation", 0, 0.0D),
			vectorValue(root, "rotation", 1, 0.0D),
			vectorValue(root, "rotation", 2, 0.0D),
			colorValue(root, "color", 0xFFFFFFFF)
		);
	}

	private static List<TrafficMeshFace> loadFaces(ResourceManager manager, CustomTrafficModelDefinition definition) throws Exception {
		final String format = definition.effectiveFormat();
		if (format.equals("obj")) {
			final Resource resource = manager.getResource(definition.model()).orElseThrow(() -> new IllegalArgumentException("Missing model resource " + definition.model()));
			try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
				return ObjTrafficModelLoader.load(reader, manager, definition.model(), definition.texture());
			}
		}
		if (format.equals("json") || format.equals("bbmodel")) {
			final Resource resource = manager.getResource(definition.model()).orElseThrow(() -> new IllegalArgumentException("Missing model resource " + definition.model()));
			try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
				return JsonTrafficModelLoader.load(GSON.fromJson(reader, JsonObject.class));
			}
		}
		if (format.equals("gltf") || format.equals("glb")) {
			throw new IllegalArgumentException("GLTF/GLB definitions are recognized, but this build does not include a glTF mesh decoder yet");
		}
		throw new IllegalArgumentException("Unsupported custom traffic model format " + format);
	}

	private static String stringValue(JsonObject object, String key, String defaultValue) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : defaultValue;
	}

	private static double doubleValue(JsonObject object, String key, double defaultValue) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsDouble() : defaultValue;
	}

	private static double vectorValue(JsonObject object, String key, int index, double defaultValue) {
		if (!object.has(key) || !object.get(key).isJsonArray() || object.getAsJsonArray(key).size() <= index) {
			return defaultValue;
		}
		return object.getAsJsonArray(key).get(index).getAsDouble();
	}

	private static int colorValue(JsonObject object, String key, int defaultValue) {
		final String rawColor = stringValue(object, key, "");
		if (rawColor.isBlank()) {
			return defaultValue;
		}
		final String normalizedColor = rawColor.startsWith("#") ? rawColor.substring(1) : rawColor;
		return (int) Long.parseLong(normalizedColor.length() == 6 ? "FF" + normalizedColor : normalizedColor, 16);
	}

	private static ResourceLocation resourceLocationValue(JsonObject object, String key, ResourceLocation defaultValue) {
		final String rawValue = stringValue(object, key, "");
		return rawValue.isBlank() ? defaultValue : new ResourceLocation(rawValue);
	}
}
