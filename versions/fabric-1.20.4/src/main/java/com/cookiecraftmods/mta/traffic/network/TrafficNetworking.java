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
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TrafficNetworking {
	public static final ResourceLocation DEBUG_SNAPSHOT_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "debug_snapshot");
	private static ScheduledExecutorService snapshotExecutor;
	private static final long SNAPSHOT_INTERVAL_MILLIS = 100L;
	private static volatile Audience audience = new Audience(List.of(), List.of(), 0);

	private record Audience(List<ServerPlayer> players, List<AsyncSnapshotFilter.PlayerViewSnapshot> views, double distance) {
	}
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
			// Capture all world/entity data on the server thread. The publisher only
			// uses these immutable views and the players' network connections.
			final List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
			audience = new Audience(players, players.stream()
				.map(player -> new AsyncSnapshotFilter.PlayerViewSnapshot(player.getUUID(),
					player.level().dimension().location().toString(), player.getX(), player.getZ()))
				.toList(), TrafficAddonConfig.trafficVehicleVisibilityDistanceBlocks(server.getPlayerList().getViewDistance()));
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			final UUID playerId = handler.getPlayer().getUUID();
			final Audience current = audience;
			audience = new Audience(
				current.players().stream().filter(player -> !player.getUUID().equals(playerId)).toList(),
				current.views().stream().filter(view -> !view.playerId().equals(playerId)).toList(), current.distance());
			AsyncSnapshotFilter.clearPlayer(playerId);
			LAST_SENT_SEQUENCE.remove(playerId);
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			audience = new Audience(List.of(), List.of(), 0);
			snapshotSequence = 0L;
			LAST_SENT_SEQUENCE.clear();
			AsyncSnapshotFilter.start();
			snapshotExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
				final Thread thread = new Thread(runnable, "MTR Traffic Addon Snapshot Publisher");
				thread.setDaemon(true);
				return thread;
			});
			snapshotExecutor.scheduleAtFixedRate(() -> {
				try {
					final Audience current = audience;
					broadcastReadySnapshots(current.players());
					final TrafficManager.NetworkFrame frame = TrafficManager.getActiveNetworkFrame();
					AsyncSnapshotFilter.submitAsync(current.views(), frame.vehicles(),
						current.distance(), ++snapshotSequence, frame.simulationNanos());
				} catch (Exception exception) {
					com.cookiecraftmods.mta.MTRTrafficAddon.LOGGER.error("Traffic snapshot publication failed", exception);
				}
			}, 0L, SNAPSHOT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (snapshotExecutor != null) {
				snapshotExecutor.shutdownNow();
				try {
					snapshotExecutor.awaitTermination(5L, TimeUnit.SECONDS);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
				snapshotExecutor = null;
			}
			audience = new Audience(List.of(), List.of(), 0);
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

}
