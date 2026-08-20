package com.cookiecraftmods.mta.traffic.lights.network;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.lights.TrafficLightBindingTargetType;
import com.cookiecraftmods.mta.traffic.lights.TrafficLightBindingRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class TrafficLightBindingNetworking {
	public static final ResourceLocation OPEN_MENU_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "traffic_light_bind_open");
	public static final ResourceLocation BIND_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "traffic_light_bind");
	private static boolean initialized;

	private TrafficLightBindingNetworking() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerPlayNetworking.registerGlobalReceiver(BIND_PACKET_ID, (server, player, handler, buffer, responseSender) -> {
			final BlockPos blockPos = buffer.readBlockPos();
			final String intersectionId = buffer.readUtf();
			final TrafficLightBindingTargetType targetType = buffer.readEnum(TrafficLightBindingTargetType.class);
			final int targetNumber = buffer.readVarInt();
			server.execute(() -> TrafficLightBindingRegistry.bind(player, blockPos, intersectionId, targetType, targetNumber));
		});

		initialized = true;
	}
}
