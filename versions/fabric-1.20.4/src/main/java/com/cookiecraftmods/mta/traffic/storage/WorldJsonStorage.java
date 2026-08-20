package com.cookiecraftmods.mta.traffic.storage;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public final class WorldJsonStorage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private WorldJsonStorage() {
	}

	public static <T> List<T> loadList(MinecraftServer server, String fileName, Type listType, String description) {
		final Path path = path(server, fileName);
		if (!Files.exists(path)) {
			return List.of();
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			final List<T> values = GSON.fromJson(reader, listType);
			return values == null ? List.of() : values;
		} catch (IOException | JsonParseException exception) {
			MTRTrafficAddon.LOGGER.error("Failed to load {}", description, exception);
			return List.of();
		}
	}

	public static void saveList(MinecraftServer server, String fileName, Collection<?> values, String description) {
		final Path path = path(server, fileName);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(values, writer);
			}
		} catch (IOException | JsonParseException exception) {
			MTRTrafficAddon.LOGGER.error("Failed to save {}", description, exception);
		}
	}

	private static Path path(MinecraftServer server, String fileName) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MTRTrafficAddon.MOD_ID).resolve(fileName);
	}
}
