package com.cookiecraftmods.mta.client.dashboard;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

final class TrafficWorldMapCache {
	static final int CHUNK_SIZE = 16;
	private static final int UPDATE_FREQUENCY_TICKS = 40;
	private static final int MAX_CACHED_TILES = 8192;
	private static final int MIN_BUILD_Y = -64;
	private static final String TILE_EXTENSION = ".png";

	private final Map<TileKey, MapTile> mapTiles = new HashMap<>();
	private final Path cacheDirectory;
	private double updateMapTimer = -1.0D;
	private long accessCounter;

	TrafficWorldMapCache(Path cacheDirectory) {
		this.cacheDirectory = cacheDirectory;
		loadCachedTiles();
	}

	void tick(ClientLevel world, LocalPlayer player, float delta) {
		if (world == null || player == null) {
			return;
		}

		if (updateMapTimer == -1.0D || updateMapTimer >= UPDATE_FREQUENCY_TICKS) {
			updateMapTimer = 0.0D;
			updateVisibleMap(world, player);
		}
		updateMapTimer += delta;
	}

	void forEachVisibleTile(Consumer<MapTile> consumer) {
		for (MapTile mapTile : mapTiles.values()) {
			if (!mapTile.disposed) {
				consumer.accept(mapTile);
			}
		}
	}

	void updateVisibleMap(ClientLevel world, LocalPlayer player) {
		if (world == null || player == null) {
			return;
		}

		final int renderDistanceChunks = Minecraft.getInstance().options.getEffectiveRenderDistance() + 1;
		final ClientChunkCache chunkManager = world.getChunkSource();
		final int playerBlockX = player.blockPosition().getX();
		final int playerBlockZ = player.blockPosition().getZ();

		for (int offsetX = -renderDistanceChunks; offsetX <= renderDistanceChunks; offsetX++) {
			for (int offsetZ = -renderDistanceChunks; offsetZ <= renderDistanceChunks; offsetZ++) {
				final int sampleBlockX = playerBlockX + offsetX * CHUNK_SIZE;
				final int sampleBlockZ = playerBlockZ + offsetZ * CHUNK_SIZE;
				final int chunkX = floorDiv(sampleBlockX, CHUNK_SIZE);
				final int chunkZ = floorDiv(sampleBlockZ, CHUNK_SIZE);
				if (!isChunkLoaded(chunkManager, chunkX, chunkZ)) {
					continue;
				}
				renderChunk(world, chunkManager, chunkX, chunkZ);
			}
		}

		pruneOldTiles();
	}

	void disposeImages() {
		mapTiles.values().forEach(MapTile::dispose);
		mapTiles.clear();
	}

	int cachedTileCount() {
		return mapTiles.size();
	}

	private void loadCachedTiles() {
		if (!Files.isDirectory(cacheDirectory)) {
			return;
		}

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheDirectory, "*" + TILE_EXTENSION)) {
			for (Path path : stream) {
				if (mapTiles.size() >= MAX_CACHED_TILES) {
					return;
				}
				final TileKey tileKey = tileKeyFromFile(path);
				if (tileKey == null) {
					continue;
				}
				final NativeImage image = readImage(path);
				if (image == null) {
					continue;
				}
				mapTiles.put(tileKey, new MapTile(tileKey.chunkX, tileKey.chunkZ, image));
			}
		} catch (IOException ignored) {
		}
	}

	private void renderChunk(ClientLevel world, ClientChunkCache chunkManager, int chunkX, int chunkZ) {
		final TileKey tileKey = new TileKey(chunkX, chunkZ);
		final MapTile mapTile = mapTiles.computeIfAbsent(tileKey, ignored -> new MapTile(chunkX, chunkZ, new NativeImage(CHUNK_SIZE, CHUNK_SIZE, false)));
		mapTile.lastAccess = ++accessCounter;
		if (mapTile.disposed) {
			return;
		}

		final int chunkBlockX = chunkX * CHUNK_SIZE;
		final int chunkBlockZ = chunkZ * CHUNK_SIZE;
		final NativeImage image = mapTile.texture.getPixels();
		if (image == null) {
			return;
		}

		boolean changed = false;
		for (int localX = 0; localX < CHUNK_SIZE; localX++) {
			for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
				final int worldX = chunkBlockX + localX;
				final int worldZ = chunkBlockZ + localZ;
				final int topY = Math.max(MIN_BUILD_Y, world.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ) - 1);
				final BlockPos blockPos = new BlockPos(worldX, topY, worldZ);
				final MapColor mapColor = world.getBlockState(blockPos).getMapColor(world, blockPos);
				final int shadedColor = convertColorABGR(shadeColor(mapColor.col, heightShade(world, chunkManager, worldX, worldZ, topY)));
				if (image.getPixelRGBA(localX, localZ) != shadedColor) {
					image.setPixelRGBA(localX, localZ, shadedColor);
					changed = true;
				}
			}
		}
		if (changed) {
			mapTile.texture.upload();
			writeImage(tileKey, image);
		}
	}

	private int heightShade(ClientLevel world, ClientChunkCache chunkManager, int worldX, int worldZ, int centerTopY) {
		final int north = topYIfLoaded(world, chunkManager, worldX, worldZ - 1, centerTopY);
		final int south = topYIfLoaded(world, chunkManager, worldX, worldZ + 1, centerTopY);
		final int west = topYIfLoaded(world, chunkManager, worldX - 1, worldZ, centerTopY);
		final int east = topYIfLoaded(world, chunkManager, worldX + 1, worldZ, centerTopY);
		return clamp((west - east + north - south) * 2, -24, 24);
	}

	private int topYIfLoaded(ClientLevel world, ClientChunkCache chunkManager, int worldX, int worldZ, int fallbackTopY) {
		final int chunkX = floorDiv(worldX, CHUNK_SIZE);
		final int chunkZ = floorDiv(worldZ, CHUNK_SIZE);
		return isChunkLoaded(chunkManager, chunkX, chunkZ)
			? Math.max(MIN_BUILD_Y, world.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ) - 1)
			: fallbackTopY;
	}

	private static boolean isChunkLoaded(ClientChunkCache chunkManager, int chunkX, int chunkZ) {
		// ClientLevel.hasChunk deliberately returns true on the client, including for its empty sentinel chunk.
		return chunkManager.hasChunk(chunkX, chunkZ);
	}

	private void writeImage(TileKey tileKey, NativeImage image) {
		try {
			Files.createDirectories(cacheDirectory);
			image.writeToFile(tilePath(tileKey));
		} catch (IOException ignored) {
		}
	}

	private NativeImage readImage(Path path) {
		try (InputStream stream = Files.newInputStream(path)) {
			final NativeImage image = NativeImage.read(stream);
			if (image.getWidth() == CHUNK_SIZE && image.getHeight() == CHUNK_SIZE) {
				return image;
			}
			image.close();
		} catch (IOException ignored) {
		}
		return null;
	}

	private Path tilePath(TileKey tileKey) {
		return cacheDirectory.resolve(tileKey.chunkX + "_" + tileKey.chunkZ + TILE_EXTENSION);
	}

	private TileKey tileKeyFromFile(Path path) {
		final String fileName = path.getFileName().toString();
		if (!fileName.endsWith(TILE_EXTENSION)) {
			return null;
		}
		final String[] parts = fileName.substring(0, fileName.length() - TILE_EXTENSION.length()).split("_", 2);
		if (parts.length != 2) {
			return null;
		}
		try {
			return new TileKey(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private void pruneOldTiles() {
		if (mapTiles.size() <= MAX_CACHED_TILES) {
			return;
		}

		long oldestAccess = Long.MAX_VALUE;
		TileKey oldestKey = null;
		for (Map.Entry<TileKey, MapTile> entry : mapTiles.entrySet()) {
			if (entry.getValue().lastAccess < oldestAccess) {
				oldestAccess = entry.getValue().lastAccess;
				oldestKey = entry.getKey();
			}
		}

		final MapTile removed = mapTiles.remove(oldestKey);
		if (removed != null) {
			removed.dispose();
		}

		final Iterator<Map.Entry<TileKey, MapTile>> iterator = mapTiles.entrySet().iterator();
		while (mapTiles.size() > MAX_CACHED_TILES && iterator.hasNext()) {
			final MapTile removedTile = iterator.next().getValue();
			iterator.remove();
			removedTile.dispose();
		}
	}

	private static int floorDiv(int value, int divisor) {
		return (int) Math.floor(value / (double) divisor);
	}

	private static int convertColorABGR(int color) {
		final int red = color >> 16 & 0xFF;
		final int green = color >> 8 & 0xFF;
		final int blue = color & 0xFF;
		return 0xFF000000 | blue << 16 | green << 8 | red;
	}

	private static int shadeColor(int color, int heightShade) {
		final int shade = heightShade;
		final int red = clamp((color >> 16 & 0xFF) + shade, 0, 255);
		final int green = clamp((color >> 8 & 0xFF) + shade, 0, 255);
		final int blue = clamp((color & 0xFF) + shade, 0, 255);
		return red << 16 | green << 8 | blue;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	static final class MapTile {
		final ResourceLocation textureId;
		final DynamicTexture texture;
		final int chunkX;
		final int chunkZ;
		private boolean disposed;
		private long lastAccess;

		MapTile(int chunkX, int chunkZ, NativeImage image) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			texture = new DynamicTexture(image);
			texture.setFilter(false, false);
			textureId = Minecraft.getInstance().getTextureManager().register("mtr_traffic_dashboard_map", texture);
			texture.upload();
		}

		void dispose() {
			if (!disposed) {
				disposed = true;
				Minecraft.getInstance().getTextureManager().release(textureId);
			}
		}
	}

	private record TileKey(int chunkX, int chunkZ) {
	}
}
