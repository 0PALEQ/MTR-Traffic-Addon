package com.cookiecraftmods.mta.traffic.rail;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class MtaExclusiveRailNetworking {
	public static final ResourceLocation SNAPSHOT_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "mta_exclusive_rails");
	private static final int SYNC_INTERVAL_TICKS = 20;
	private static boolean initialized;

	private MtaExclusiveRailNetworking() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					sendSnapshot(player);
				}
			}
		});
		initialized = true;
	}

	private static void sendSnapshot(ServerPlayer player) {
		final String dimensionId = player.level().dimension().location().toString();
		final List<MtaExclusiveRailDefinition> rails = MtaExclusiveRailRegistry.getDefinitions().stream()
			.filter(definition -> definition.dimensionId().equals(dimensionId))
			.toList();
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeVarInt(rails.size());
		for (MtaExclusiveRailDefinition rail : rails) {
			buffer.writeUtf(rail.id());
			buffer.writeLong(rail.startX());
			buffer.writeLong(rail.startY());
			buffer.writeLong(rail.startZ());
			buffer.writeLong(rail.endX());
			buffer.writeLong(rail.endY());
			buffer.writeLong(rail.endZ());
			buffer.writeUtf(rail.startAngle());
			buffer.writeUtf(rail.endAngle());
			buffer.writeVarInt(rail.speedLimitKph());
		}
		ServerPlayNetworking.send(player, SNAPSHOT_PACKET_ID, buffer);
	}
}
