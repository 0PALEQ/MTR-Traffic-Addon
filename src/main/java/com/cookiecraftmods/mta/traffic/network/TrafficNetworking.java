package com.cookiecraftmods.mta.traffic.network;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.config.TrafficAddonConfig;
import com.cookiecraftmods.mta.traffic.TrafficManager;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TrafficNetworking {
	public static final ResourceLocation DEBUG_SNAPSHOT_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "debug_snapshot");
	private static final int SYNC_INTERVAL_TICKS = 5;
	private static boolean initialized;

	private TrafficNetworking() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
				broadcastCachedSnapshots(server.getPlayerList().getPlayers());
				submitNextFilteringPass(server.getPlayerList().getPlayers(), TrafficManager.getActiveVehicles(), server.getPlayerList().getViewDistance());
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			AsyncSnapshotFilter.clearPlayer(handler.getPlayer().getUUID());
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			AsyncSnapshotFilter.shutdown();
		});

		initialized = true;
	}

	private static void broadcastCachedSnapshots(Collection<ServerPlayer> players) {
		if (players.isEmpty()) {
			return;
		}

		final Set<UUID> renderedVehicleIds = new HashSet<>();

		for (ServerPlayer player : players) {
			final AsyncSnapshotFilter.SnapshotResult snapshot = AsyncSnapshotFilter.getSnapshot(player);
			if (snapshot != null) {
				final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(snapshot.buffer));
				ServerPlayNetworking.send(player, DEBUG_SNAPSHOT_PACKET_ID, buffer);
				renderedVehicleIds.addAll(snapshot.vehicleIds);
			}
		}

		if (!renderedVehicleIds.isEmpty()) {
			TrafficManager.markVehiclesRendered(renderedVehicleIds, System.currentTimeMillis());
		}
	}

	private static void submitNextFilteringPass(Collection<ServerPlayer> players, Collection<TrafficVehicle> vehicles, int viewDistanceChunks) {
		if (players.isEmpty() || vehicles.isEmpty()) {
			return;
		}

		final double maxDistanceBlocks = TrafficAddonConfig.trafficVehicleVisibilityDistanceBlocks(viewDistanceChunks);
		AsyncSnapshotFilter.submitAsync(players, vehicles, maxDistanceBlocks);
	}
}
