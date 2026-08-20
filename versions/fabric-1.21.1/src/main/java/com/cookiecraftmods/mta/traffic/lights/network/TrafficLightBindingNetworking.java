package com.cookiecraftmods.mta.traffic.lights.network;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.lights.TrafficLightBindingTargetType;
import com.cookiecraftmods.mta.traffic.lights.TrafficLightBindingRegistry;
import com.cookiecraftmods.mta.traffic.network.MtaPacketPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class TrafficLightBindingNetworking {
	public static final CustomPacketPayload.Type<MtaPacketPayload> OPEN_MENU_PACKET_ID = MtaPacketPayload.type("traffic_light_bind_open");
	public static final CustomPacketPayload.Type<MtaPacketPayload> BIND_PACKET_ID = MtaPacketPayload.type("traffic_light_bind");
	private static boolean initialized;

	private TrafficLightBindingNetworking() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		MtaPacketPayload.registerS2C(OPEN_MENU_PACKET_ID);
		MtaPacketPayload.registerC2S(BIND_PACKET_ID);

		ServerPlayNetworking.registerGlobalReceiver(BIND_PACKET_ID, (payload, context) -> {
			final FriendlyByteBuf buffer = payload.buffer();
			final BlockPos blockPos = buffer.readBlockPos();
			final String intersectionId = buffer.readUtf();
			final TrafficLightBindingTargetType targetType = buffer.readEnum(TrafficLightBindingTargetType.class);
			final int targetNumber = buffer.readVarInt();
			TrafficLightBindingRegistry.bind(context.player(), blockPos, intersectionId, targetType, targetNumber);
		});

		initialized = true;
	}
}
