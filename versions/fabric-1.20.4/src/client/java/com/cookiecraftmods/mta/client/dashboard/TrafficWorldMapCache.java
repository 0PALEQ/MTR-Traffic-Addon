package com.cookiecraftmods.mta.client.dashboard;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ChunkManager;
import org.mtr.mapping.holder.HeightMapType;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.MapColor;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.NativeImage;
import org.mtr.mapping.holder.NativeImageBackedTexture;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.holder.ClientPlayerEntity;
import org.mtr.mapping.mapper.MinecraftClientHelper;

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

	void tick(World world, ClientPlayerEntity player, float delta) {
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

	void updateVisibleMap(World world, ClientPlayerEntity player) {
		if (world == null || player == null) {
			return;
		}

		final int renderDistanceChunks = MinecraftClientHelper.getRenderDistance() + 1;
		final ChunkManager chunkManager = world.getChunkManager();
		final int playerBlockX = player.getBlockPos().getX();
		final int playerBlockZ = player.getBlockPos().getZ();

		for (int offsetX = -renderDistanceChunks; offsetX <= renderDistanceChunks; offsetX++) {
			for (int offsetZ = -renderDistanceChunks; offsetZ <= renderDistanceChunks; offsetZ++) {
				final int sampleBlockX = playerBlockX + offsetX * CHUNK_SIZE;
				final int sampleBlockZ = playerBlockZ + offsetZ * CHUNK_SIZE;
				final int chunkX = floorDiv(sampleBlockX, CHUNK_SIZE);
				final int chunkZ = floorDiv(sampleBlockZ, CHUNK_SIZE);
				if (!chunkManager.isChunkLoaded(chunkX, chunkZ)) {
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

	private void renderChunk(World world, ChunkManager chunkManager, int chunkX, int chunkZ) {
		final TileKey tileKey = new TileKey(chunkX, chunkZ);
		final MapTile mapTile = mapTiles.computeIfAbsent(tileKey, ignored -> new MapTile(chunkX, chunkZ, new NativeImage(CHUNK_SIZE, CHUNK_SIZE, false)));
		mapTile.lastAccess = ++accessCounter;
		if (mapTile.disposed) {
			return;
		}

		final int chunkBlockX = chunkX * CHUNK_SIZE;
		final int chunkBlockZ = chunkZ * CHUNK_SIZE;
		final NativeImage image = mapTile.texture.getImage();
		if (image == null) {
			return;
		}

		boolean changed = false;
		for (int localX = 0; localX < CHUNK_SIZE; localX++) {
			for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
				final int worldX = chunkBlockX + localX;
				final int worldZ = chunkBlockZ + localZ;
				final int topY = Math.max(MIN_BUILD_Y, world.getTopY(HeightMapType.getMotionBlockingMapped(), worldX, worldZ) - 1);
				final BlockPos blockPos = new BlockPos(worldX, topY, worldZ);
				final MapColor mapColor = world.getBlockState(blockPos).getBlock().getDefaultMapColor();
				final int shadedColor = convertColorABGR(shadeColor(mapColor.getColorMapped(), heightShade(world, chunkManager, worldX, worldZ, topY)));
				if (image.getColor(localX, localZ) != shadedColor) {
					image.setPixelColor(localX, localZ, shadedColor);
					changed = true;
				}
			}
		}
		if (changed) {
			mapTile.texture.upload();
			writeImage(tileKey, image);
		}
	}

	private int heightShade(World world, ChunkManager chunkManager, int worldX, int worldZ, int centerTopY) {
		final int north = topYIfLoaded(world, chunkManager, worldX, worldZ - 1, centerTopY);
		final int south = topYIfLoaded(world, chunkManager, worldX, worldZ + 1, centerTopY);
		final int west = topYIfLoaded(world, chunkManager, worldX - 1, worldZ, centerTopY);
		final int east = topYIfLoaded(world, chunkManager, worldX + 1, worldZ, centerTopY);
		return clamp((west - east + north - south) * 2, -24, 24);
	}

	private int topYIfLoaded(World world, ChunkManager chunkManager, int worldX, int worldZ, int fallbackTopY) {
		final int chunkX = floorDiv(worldX, CHUNK_SIZE);
		final int chunkZ = floorDiv(worldZ, CHUNK_SIZE);
		return chunkManager.isChunkLoaded(chunkX, chunkZ)
			? Math.max(MIN_BUILD_Y, world.getTopY(HeightMapType.getMotionBlockingMapped(), worldX, worldZ) - 1)
			: fallbackTopY;
	}

	private void writeImage(TileKey tileKey, NativeImage image) {
		try {
			Files.createDirectories(cacheDirectory);
			image.writeTo(tilePath(tileKey));
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
		final Identifier textureId;
		final NativeImageBackedTexture texture;
		final int chunkX;
		final int chunkZ;
		private boolean disposed;
		private long lastAccess;

		MapTile(int chunkX, int chunkZ, NativeImage image) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			texture = new NativeImageBackedTexture(image);
			texture.setFilter(false, false);
			textureId = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("mtr_traffic_dashboard_map", texture);
			texture.upload();
		}

		void dispose() {
			if (!disposed) {
				disposed = true;
				MinecraftClient.getInstance().getTextureManager().destroyTexture(textureId);
			}
		}
	}

	private record TileKey(int chunkX, int chunkZ) {
	}
}
