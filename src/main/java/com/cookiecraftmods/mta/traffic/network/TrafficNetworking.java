package com.cookiecraftmods.mta.traffic.network;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.config.TrafficAddonConfig;
import com.cookiecraftmods.mta.traffic.TrafficManager;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrafficNetworking {
	public static final ResourceLocation DEBUG_SNAPSHOT_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "debug_snapshot");
	private static final int SYNC_INTERVAL_TICKS = 5;
	private static final Map<UUID, Long> LAST_SENT_SEQUENCE = new ConcurrentHashMap<>();
	private static boolean initialized;
	private static long snapshotSequence;

	private TrafficNetworking() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			broadcastReadySnapshots(server.getPlayerList().getPlayers());
			if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
				submitNextFilteringPass(server.getPlayerList().getPlayers(), TrafficManager.getActiveNetworkVehicleSnapshot(), server.getPlayerList().getViewDistance());
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			AsyncSnapshotFilter.clearPlayer(handler.getPlayer().getUUID());
			LAST_SENT_SEQUENCE.remove(handler.getPlayer().getUUID());
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			snapshotSequence = 0L;
			LAST_SENT_SEQUENCE.clear();
			AsyncSnapshotFilter.start();
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			AsyncSnapshotFilter.shutdown();
			LAST_SENT_SEQUENCE.clear();
		});

		initialized = true;
	}

	private static void broadcastReadySnapshots(Collection<ServerPlayer> players) {
		if (players.isEmpty()) {
			return;
		}

		final Set<UUID> renderedVehicleIds = new HashSet<>();

		for (ServerPlayer player : players) {
			final AsyncSnapshotFilter.SnapshotResult snapshot = AsyncSnapshotFilter.getSnapshot(player.getUUID());
			if (snapshot != null && snapshot.sequence() > LAST_SENT_SEQUENCE.getOrDefault(player.getUUID(), -1L)) {
				final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(snapshot.buffer()));
				ServerPlayNetworking.send(player, DEBUG_SNAPSHOT_PACKET_ID, buffer);
				LAST_SENT_SEQUENCE.put(player.getUUID(), snapshot.sequence());
				renderedVehicleIds.addAll(snapshot.vehicleIds());
			}
		}

		if (!renderedVehicleIds.isEmpty()) {
			TrafficManager.markVehiclesRendered(renderedVehicleIds, System.currentTimeMillis());
		}
	}

	private static void submitNextFilteringPass(Collection<ServerPlayer> players, Collection<TrafficNetworkVehicleSnapshot> vehicles, int viewDistanceChunks) {
		if (players.isEmpty()) {
			return;
		}

		final double maxDistanceBlocks = TrafficAddonConfig.trafficVehicleVisibilityDistanceBlocks(viewDistanceChunks);
		final Collection<AsyncSnapshotFilter.PlayerViewSnapshot> playerSnapshots = players.stream()
			.map(player -> new AsyncSnapshotFilter.PlayerViewSnapshot(
				player.getUUID(),
				player.level().dimension().location().toString(),
				player.getX(),
				player.getZ()
			))
			.toList();
		AsyncSnapshotFilter.submitAsync(playerSnapshots, vehicles, maxDistanceBlocks, ++snapshotSequence);
	}
}
