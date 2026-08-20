package com.cookiecraftmods.mta.client.sign;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.sign.entity.RoadSignBlockEntity;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RoadSignBaseRegistry implements SimpleSynchronousResourceReloadListener {
	private static final Gson GSON = new Gson();
	private static final ResourceLocation RELOAD_LISTENER_ID = ResourceLocation.fromNamespaceAndPath(MTRTrafficAddon.MOD_ID, "road_sign_bases");
	private static final String DEFINITION_DIRECTORY = "road_signs";
	private static volatile Map<ResourceLocation, RoadSignBaseDefinition> definitions = Map.of(
		RoadSignBlockEntity.DEFAULT_BASE_ID, RoadSignBaseDefinition.fallback()
	);
	private static boolean initialized;

	private RoadSignBaseRegistry() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new RoadSignBaseRegistry());
		initialized = true;
	}

	public static Optional<RoadSignBaseDefinition> get(ResourceLocation id) {
		return Optional.ofNullable(definitions.get(id));
	}

	public static RoadSignBaseDefinition resolve(ResourceLocation id) {
		return get(id).orElseGet(RoadSignBaseDefinition::fallback);
	}

	public static List<RoadSignBaseDefinition> all() {
		return List.copyOf(definitions.values());
	}

	@Override
	public ResourceLocation getFabricId() {
		return RELOAD_LISTENER_ID;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		final List<Map.Entry<ResourceLocation, Resource>> resources = new ArrayList<>(
			manager.listResources(DEFINITION_DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()
		);
		resources.sort(Map.Entry.comparingByKey());
		final Map<ResourceLocation, RoadSignBaseDefinition> loaded = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> entry : resources) {
			try (Reader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
				final JsonObject root = GSON.fromJson(reader, JsonObject.class);
				final RoadSignBaseDefinition definition = readDefinition(entry.getKey(), root);
				loaded.put(definition.id(), definition);
			} catch (Exception e) {
				MTRTrafficAddon.LOGGER.error("Failed to load road sign base definition {}", entry.getKey(), e);
			}
		}
		loaded.putIfAbsent(RoadSignBlockEntity.DEFAULT_BASE_ID, RoadSignBaseDefinition.fallback());

		final List<RoadSignBaseDefinition> sorted = new ArrayList<>(loaded.values());
		sorted.sort(Comparator.comparing(definition -> definition.nameComponent().getString(), String.CASE_INSENSITIVE_ORDER));
		final Map<ResourceLocation, RoadSignBaseDefinition> ordered = new LinkedHashMap<>();
		for (RoadSignBaseDefinition definition : sorted) {
			ordered.put(definition.id(), definition);
		}
		definitions = Collections.unmodifiableMap(ordered);
		MTRTrafficAddon.LOGGER.info("Loaded {} road sign base definition(s)", definitions.size());
	}

	private static RoadSignBaseDefinition readDefinition(ResourceLocation resourceId, JsonObject root) {
		if (root == null) {
			throw new IllegalArgumentException("Definition root is null");
		}
		final ResourceLocation defaultId = definitionId(resourceId);
		final ResourceLocation id = resourceLocationValue(root, "id", defaultId);
		final JsonObject text = root.has("text") && root.get("text").isJsonObject() ? root.getAsJsonObject("text") : new JsonObject();
		final float textX = clamp(floatValue(text, "x", 0.08F), 0.0F, 0.99F);
		final float textY = clamp(floatValue(text, "y", 0.10F), 0.0F, 0.99F);
		final float textWidth = clamp(floatValue(text, "width", 0.84F), 0.01F, 1.0F - textX);
		final float textHeight = clamp(floatValue(text, "height", 0.80F), 0.01F, 1.0F - textY);
		return new RoadSignBaseDefinition(
			id,
			stringValue(root, "display_name", id.toString()),
			stringValue(root, "translation_key", ""),
			textureValue(root, "texture"),
			clamp(floatValue(root, "width", 2.0F), 0.25F, 8.0F),
			clamp(floatValue(root, "height", 1.0F), 0.25F, 4.0F),
			clamp(floatValue(root, "thickness", 0.0625F), 0.01F, 0.25F),
			clamp(floatValue(root, "y_offset", 0.0F), -4.0F, 8.0F),
			clamp(floatValue(root, "border", 0.04F), 0.0F, 0.25F),
			colorValue(root, "background_color", 0x24529A),
			colorValue(root, "border_color", 0xFFFFFF),
			colorValue(root, "back_color", 0x70777C),
			colorValue(text, "color", 0xFFFFFF),
			textX,
			textY,
			textWidth,
			textHeight,
			clamp(intValue(text, "max_lines", RoadSignBlockEntity.MAX_LINES), 1, RoadSignBlockEntity.MAX_LINES),
			RoadSignBaseDefinition.TextAlignment.parse(stringValue(text, "alignment", "center")),
			booleanValue(text, "shadow", false),
			booleanValue(text, "full_bright", false)
		);
	}

	private static ResourceLocation definitionId(ResourceLocation resourceId) {
		final String path = resourceId.getPath();
		final int start = DEFINITION_DIRECTORY.length() + 1;
		return ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), path.substring(start, path.length() - ".json".length()));
	}

	private static String stringValue(JsonObject object, String key, String defaultValue) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : defaultValue;
	}

	private static float floatValue(JsonObject object, String key, float defaultValue) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsFloat() : defaultValue;
	}

	private static int intValue(JsonObject object, String key, int defaultValue) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : defaultValue;
	}

	private static boolean booleanValue(JsonObject object, String key, boolean defaultValue) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : defaultValue;
	}

	private static int colorValue(JsonObject object, String key, int defaultValue) {
		final String raw = stringValue(object, key, "").trim();
		if (raw.isEmpty()) {
			return defaultValue;
		}
		final String normalized = raw.startsWith("#") ? raw.substring(1) : raw;
		if (normalized.length() != 6) {
			throw new IllegalArgumentException("Color " + key + " must use #RRGGBB");
		}
		return Integer.parseInt(normalized, 16) & 0xFFFFFF;
	}

	private static ResourceLocation resourceLocationValue(JsonObject object, String key, ResourceLocation defaultValue) {
		final String raw = stringValue(object, key, "").trim();
		return raw.isEmpty() ? defaultValue : ResourceLocation.parse(raw);
	}

	private static ResourceLocation textureValue(JsonObject object, String key) {
		final String raw = stringValue(object, key, "").trim();
		if (raw.isEmpty()) {
			return null;
		}
		final ResourceLocation id = ResourceLocation.parse(raw);
		String path = id.getPath();
		if (!path.startsWith("textures/")) {
			path = "textures/" + path;
		}
		if (!path.endsWith(".png")) {
			path += ".png";
		}
		return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path);
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int clamp(int value, int minimum, int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
