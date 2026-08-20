package com.cookiecraftmods.mta.traffic.sign.image;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class RoadSignImageStore {
	private static final String DIRECTORY_NAME = "road_sign_images";

	private RoadSignImageStore() {
	}

	public static RoadSignImageData.ValidatedImage store(MinecraftServer server, byte[] data) throws IOException, RoadSignImageData.ImageValidationException {
		final RoadSignImageData.ValidatedImage validated = RoadSignImageData.validate(data);
		final Path directory = directory(server);
		final Path target = directory.resolve(validated.id() + ".png");
		Files.createDirectories(directory);
		if (Files.isRegularFile(target) && load(server, validated.id()).isPresent()) {
			return validated;
		}

		final Path temporary = Files.createTempFile(directory, "road-sign-", ".tmp");
		try {
			Files.write(temporary, data, StandardOpenOption.TRUNCATE_EXISTING);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
		return validated;
	}

	public static Optional<byte[]> load(MinecraftServer server, String rawId) {
		final String id = RoadSignImageData.normalizeId(rawId);
		if (id.isEmpty()) {
			return Optional.empty();
		}
		final Path path = directory(server).resolve(id + ".png");
		try {
			if (!Files.isRegularFile(path) || Files.size(path) > RoadSignImageData.MAX_BYTES) {
				return Optional.empty();
			}
			final byte[] data = Files.readAllBytes(path);
			final RoadSignImageData.ValidatedImage validated = RoadSignImageData.validate(data);
			return id.equals(validated.id()) ? Optional.of(data) : Optional.empty();
		} catch (IOException | RoadSignImageData.ImageValidationException exception) {
			MTRTrafficAddon.LOGGER.warn("Could not read custom road sign image {}", id, exception);
			return Optional.empty();
		}
	}

	private static Path directory(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT)
			.resolve("data")
			.resolve(MTRTrafficAddon.MOD_ID)
			.resolve(DIRECTORY_NAME);
	}
}
