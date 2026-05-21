package com.cookiecraftmods.mta.traffic.network;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.TrafficManager;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehiclePosition;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class TrafficNetworking {
	public static final ResourceLocation DEBUG_SNAPSHOT_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "debug_snapshot");
	private static final int SYNC_INTERVAL_TICKS = 5;
	private static final double SNAPSHOT_DISTANCE_MARGIN_BLOCKS = 32.0D;
	private static boolean initialized;

	private TrafficNetworking() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
				broadcastSnapshots(server.getPlayerList().getPlayers(), TrafficManager.getActiveVehicles(), server.getPlayerList().getViewDistance());
			}
		});
		initialized = true;
	}

	private static void broadcastSnapshots(Collection<ServerPlayer> players, Collection<TrafficVehicle> vehicles, int viewDistanceChunks) {
		if (players.isEmpty()) {
			return;
		}

		final double maxDistanceBlocks = Math.max(2, viewDistanceChunks) * 16.0D + SNAPSHOT_DISTANCE_MARGIN_BLOCKS;
		final double maxDistanceSquared = maxDistanceBlocks * maxDistanceBlocks;
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

		for (ServerPlayer player : players) {
			buffer.clear();
			writeSnapshotsForPlayer(buffer, player, vehicles, maxDistanceSquared);
			ServerPlayNetworking.send(player, DEBUG_SNAPSHOT_PACKET_ID, new FriendlyByteBuf(buffer.copy()));
		}
	}

	private static void writeSnapshotsForPlayer(FriendlyByteBuf buffer, ServerPlayer player, Collection<TrafficVehicle> vehicles, double maxDistanceSquared) {
		final List<TrafficVehicle> visibleVehicles = new ArrayList<>();
		for (TrafficVehicle vehicle : vehicles) {
			final TrafficVehiclePosition position = vehicle.currentPosition();
			final double dx = position.x() - player.getX();
			final double dz = position.z() - player.getZ();
			if (dx * dx + dz * dz <= maxDistanceSquared) {
				visibleVehicles.add(vehicle);
			}
		}

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
			buffer.writeDouble(vehicle.speedKph());
		}
	}
}
