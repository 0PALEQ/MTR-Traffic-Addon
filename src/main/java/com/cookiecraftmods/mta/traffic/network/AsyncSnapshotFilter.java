package com.cookiecraftmods.mta.traffic.network;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehiclePosition;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AsyncSnapshotFilter {
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
		Math.max(2, Runtime.getRuntime().availableProcessors() - 2)
	);

	private static final class PlayerSnapshotCache {
		volatile byte[] buffer;
		volatile List<UUID> vehicleIds;
		volatile long generatedAtNanos;
	}

	private static final ConcurrentHashMap<UUID, PlayerSnapshotCache> PLAYER_SNAPSHOTS = new ConcurrentHashMap<>();

	private AsyncSnapshotFilter() {
	}

	public static void submitAsync(
			Collection<ServerPlayer> players,
			Collection<TrafficVehicle> vehicles,
			double maxDistanceBlocks) {

		EXECUTOR.submit(() -> {
			try {
				final SpatialVehicleIndex spatialIndex = SpatialVehicleIndex.build(vehicles);
				final double maxDistanceSquared = maxDistanceBlocks * maxDistanceBlocks;
				final long nowNanos = System.nanoTime();

				for (ServerPlayer player : players) {
					final PlayerSnapshotCache cache = PLAYER_SNAPSHOTS.computeIfAbsent(
						player.getUUID(),
						uuid -> new PlayerSnapshotCache()
					);

					// Query nearby vehicles using spatial index
					final List<TrafficVehicle> visibleVehicles = spatialIndex.queryNearby(
						player.getX(),
						player.getZ(),
						maxDistanceSquared
					);

					// Encode snapshot buffer
					final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
					final List<UUID> vehicleIds = new ArrayList<>(visibleVehicles.size());

					buffer.writeVarInt(visibleVehicles.size());
					for (TrafficVehicle vehicle : visibleVehicles) {
						final TrafficVehiclePosition position = vehicle.currentPosition();
						buffer.writeUUID(vehicle.id());
						buffer.writeUtf(vehicle.definition().effectiveVisualId());
						buffer.writeUtf(vehicle.definition().type());
						buffer.writeDouble(vehicle.definition().lengthMeters());
						buffer.writeDouble(position.x());
						buffer.writeDouble(position.y());
						buffer.writeDouble(position.z());
						buffer.writeFloat(position.yawDegrees());
						buffer.writeFloat(position.pitchDegrees());
						buffer.writeDouble(vehicle.speedKph());
						vehicleIds.add(vehicle.id());
					}

					// Atomically update cache
					cache.buffer = buffer.array();
					cache.vehicleIds = vehicleIds;
					cache.generatedAtNanos = nowNanos;
				}
			} catch (Exception e) {
				MTRTrafficAddon.LOGGER.error("Error in async snapshot filter", e);
			}
		});
	}

	public static SnapshotResult getSnapshot(ServerPlayer player) {
		final PlayerSnapshotCache cache = PLAYER_SNAPSHOTS.get(player.getUUID());
		if (cache == null || cache.buffer == null) {
			return null;
		}
		return new SnapshotResult(cache.buffer, cache.vehicleIds);
	}

	public static final class SnapshotResult {
		public final byte[] buffer;
		public final List<UUID> vehicleIds;

		SnapshotResult(byte[] buffer, List<UUID> vehicleIds) {
			this.buffer = buffer;
			this.vehicleIds = vehicleIds;
		}
	}

	public static void clearPlayer(UUID playerUUID) {
		PLAYER_SNAPSHOTS.remove(playerUUID);
	}
	
	public static void shutdown() {
		EXECUTOR.shutdownNow();
	}
}
