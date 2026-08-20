package com.cookiecraftmods.mta.traffic.network;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Typed 1.21 transport for the addon's existing packet byte formats.
 */
public final class MtaPacketPayload implements CustomPacketPayload {
	private static final int MAX_PAYLOAD_BYTES = 1_048_576;
	private final Type<MtaPacketPayload> type;
	private final byte[] data;

	private MtaPacketPayload(Type<MtaPacketPayload> type, byte[] data) {
		this.type = type;
		this.data = data;
	}

	public static Type<MtaPacketPayload> type(String path) {
		return new Type<>(ResourceLocation.fromNamespaceAndPath(MTRTrafficAddon.MOD_ID, path));
	}

	public static void registerC2S(Type<MtaPacketPayload> type) {
		PayloadTypeRegistry.playC2S().register(type, codec(type));
	}

	public static void registerS2C(Type<MtaPacketPayload> type) {
		PayloadTypeRegistry.playS2C().register(type, codec(type));
	}

	public static MtaPacketPayload fromBuffer(Type<MtaPacketPayload> type, FriendlyByteBuf buffer) {
		final byte[] data = new byte[buffer.readableBytes()];
		buffer.getBytes(buffer.readerIndex(), data);
		return new MtaPacketPayload(type, data);
	}

	public FriendlyByteBuf buffer() {
		return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
	}

	@Override
	public Type<MtaPacketPayload> type() {
		return type;
	}

	private static StreamCodec<RegistryFriendlyByteBuf, MtaPacketPayload> codec(Type<MtaPacketPayload> type) {
		return new StreamCodec<>() {
			@Override
			public MtaPacketPayload decode(RegistryFriendlyByteBuf buffer) {
				return new MtaPacketPayload(type, buffer.readByteArray(MAX_PAYLOAD_BYTES));
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buffer, MtaPacketPayload payload) {
				buffer.writeByteArray(payload.data);
			}
		};
	}
}
