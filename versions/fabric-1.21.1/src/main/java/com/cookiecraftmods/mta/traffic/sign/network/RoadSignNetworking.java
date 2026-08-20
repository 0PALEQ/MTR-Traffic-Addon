package com.cookiecraftmods.mta.traffic.sign.network;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.network.MtaPacketPayload;
import com.cookiecraftmods.mta.traffic.sign.entity.RoadSignBlockEntity;
import com.cookiecraftmods.mta.traffic.sign.image.RoadSignImageData;
import com.cookiecraftmods.mta.traffic.sign.image.RoadSignImageStore;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RoadSignNetworking {
	public static final CustomPacketPayload.Type<MtaPacketPayload> OPEN_EDITOR_PACKET_ID = MtaPacketPayload.type("road_sign_open");
	public static final CustomPacketPayload.Type<MtaPacketPayload> UPDATE_PACKET_ID = MtaPacketPayload.type("road_sign_update");
	public static final CustomPacketPayload.Type<MtaPacketPayload> UPLOAD_IMAGE_CHUNK_PACKET_ID = MtaPacketPayload.type("road_sign_image_upload");
	public static final CustomPacketPayload.Type<MtaPacketPayload> REQUEST_IMAGE_PACKET_ID = MtaPacketPayload.type("road_sign_image_request");
	public static final CustomPacketPayload.Type<MtaPacketPayload> IMAGE_DATA_PACKET_ID = MtaPacketPayload.type("road_sign_image_data");
	public static final int MAX_BASE_ID_LENGTH = 128;
	public static final int IMAGE_CHUNK_BYTES = 24 * 1024;
	public static final int MAX_IMAGE_CHUNKS = (RoadSignImageData.MAX_BYTES + IMAGE_CHUNK_BYTES - 1) / IMAGE_CHUNK_BYTES;
	private static final ResourceLocation MTR_BRUSH_ID = ResourceLocation.fromNamespaceAndPath("mtr", "brush");
	private static final double MAX_EDIT_DISTANCE_SQUARED = 64.0D;
	private static final double MAX_IMAGE_DISTANCE_SQUARED = 16384.0D;
	private static final int MAX_UPLOAD_AGE_TICKS = 20 * 30;
	private static final Map<UploadKey, UploadSession> UPLOADS = new HashMap<>();
	private static boolean initialized;

	private RoadSignNetworking() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		MtaPacketPayload.registerS2C(OPEN_EDITOR_PACKET_ID);
		MtaPacketPayload.registerS2C(IMAGE_DATA_PACKET_ID);
		MtaPacketPayload.registerC2S(UPDATE_PACKET_ID);
		MtaPacketPayload.registerC2S(UPLOAD_IMAGE_CHUNK_PACKET_ID);
		MtaPacketPayload.registerC2S(REQUEST_IMAGE_PACKET_ID);

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide || !(player instanceof ServerPlayer serverPlayer) || !isMtrBrush(player.getItemInHand(hand))) {
				return InteractionResult.PASS;
			}
			if (!(world.getBlockEntity(hitResult.getBlockPos()) instanceof RoadSignBlockEntity blockEntity)) {
				return InteractionResult.PASS;
			}
			if (!serverPlayer.mayBuild()) {
				serverPlayer.displayClientMessage(Component.translatable("message.mtr-traffic-addon.road_sign.no_permission"), true);
				return InteractionResult.FAIL;
			}
			openEditor(serverPlayer, blockEntity);
			return InteractionResult.SUCCESS;
		});

		ServerPlayNetworking.registerGlobalReceiver(UPDATE_PACKET_ID, (payload, context) -> {
			final FriendlyByteBuf buffer = payload.buffer();
			final ServerPlayer player = context.player();
			final BlockPos pos = buffer.readBlockPos();
			final String rawBaseId = buffer.readUtf(MAX_BASE_ID_LENGTH);
			final int lineCount = buffer.readVarInt();
			if (lineCount < 0 || lineCount > RoadSignBlockEntity.MAX_LINES) {
				return;
			}
			final List<String> lines = new ArrayList<>(lineCount);
			for (int index = 0; index < lineCount; index++) {
				lines.add(buffer.readUtf(RoadSignBlockEntity.MAX_LINE_LENGTH));
			}
			final int textColor = buffer.readInt();
			final float width = buffer.readFloat();
			final float height = buffer.readFloat();
			final int backgroundColor = buffer.readInt();
			final int edgeColor = buffer.readInt();
			final String imageId = buffer.readUtf(RoadSignImageData.IMAGE_ID_LENGTH);
			context.server().execute(() -> updateRoadSign(player, pos, rawBaseId, lines, textColor, width, height, backgroundColor, edgeColor, imageId));
		});

		ServerPlayNetworking.registerGlobalReceiver(UPLOAD_IMAGE_CHUNK_PACKET_ID, (payload, context) -> {
			final FriendlyByteBuf buffer = payload.buffer();
			final ServerPlayer player = context.player();
			final BlockPos pos = buffer.readBlockPos();
			final String imageId = buffer.readUtf(RoadSignImageData.IMAGE_ID_LENGTH);
			final int totalBytes = buffer.readVarInt();
			final int chunkIndex = buffer.readVarInt();
			final int chunkCount = buffer.readVarInt();
			final byte[] chunk = buffer.readByteArray(IMAGE_CHUNK_BYTES);
			context.server().execute(() -> receiveImageChunk(player, pos, imageId, totalBytes, chunkIndex, chunkCount, chunk));
		});

		ServerPlayNetworking.registerGlobalReceiver(REQUEST_IMAGE_PACKET_ID, (payload, context) -> {
			final FriendlyByteBuf buffer = payload.buffer();
			final ServerPlayer player = context.player();
			final BlockPos pos = buffer.readBlockPos();
			final String imageId = buffer.readUtf(RoadSignImageData.IMAGE_ID_LENGTH);
			context.server().execute(() -> sendRequestedImage(player, pos, imageId));
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			UPLOADS.keySet().removeIf(key -> key.playerId().equals(handler.player.getUUID()))
		);

		initialized = true;
	}

	private static void openEditor(ServerPlayer player, RoadSignBlockEntity blockEntity) {
		sendStoredImage(player, blockEntity.getImageId());
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeBlockPos(blockEntity.getBlockPos());
		buffer.writeUtf(blockEntity.getBaseId().toString(), MAX_BASE_ID_LENGTH);
		buffer.writeVarInt(RoadSignBlockEntity.MAX_LINES);
		for (String line : blockEntity.getLines()) {
			buffer.writeUtf(line, RoadSignBlockEntity.MAX_LINE_LENGTH);
		}
		buffer.writeInt(blockEntity.getTextColor());
		buffer.writeFloat(blockEntity.getWidth());
		buffer.writeFloat(blockEntity.getHeight());
		buffer.writeInt(blockEntity.getBackgroundColor());
		buffer.writeInt(blockEntity.getEdgeColor());
		buffer.writeUtf(blockEntity.getImageId(), RoadSignImageData.IMAGE_ID_LENGTH);
		ServerPlayNetworking.send(player, MtaPacketPayload.fromBuffer(OPEN_EDITOR_PACKET_ID, buffer));
	}

	private static void updateRoadSign(
		ServerPlayer player,
		BlockPos pos,
		String rawBaseId,
		List<String> lines,
		int textColor,
		float width,
		float height,
		int backgroundColor,
		int edgeColor,
		String rawImageId
	) {
		if (!canEdit(player, pos)) {
			return;
		}
		final ResourceLocation baseId = ResourceLocation.tryParse(rawBaseId);
		if (baseId == null || !(player.serverLevel().getBlockEntity(pos) instanceof RoadSignBlockEntity blockEntity)) {
			return;
		}

		final String previousImageId = blockEntity.getImageId();
		final String imageId;
		Optional<byte[]> imageData = Optional.empty();
		if (rawImageId.isBlank()) {
			imageId = "";
		} else {
			imageId = RoadSignImageData.normalizeId(rawImageId);
			if (imageId.isEmpty()) {
				return;
			}
			if (!imageId.equals(previousImageId)) {
				imageData = RoadSignImageStore.load(player.getServer(), imageId);
				if (imageData.isEmpty()) {
					player.displayClientMessage(Component.translatable("message.mtr-traffic-addon.road_sign.image_missing"), true);
					return;
				}
			}
		}

		blockEntity.setContent(baseId, lines, textColor, width, height, backgroundColor, edgeColor, imageId);
		if (!imageId.equals(previousImageId) && imageData.isPresent()) {
			broadcastImage(player, pos, imageId, imageData.get());
		}
		player.displayClientMessage(Component.translatable("message.mtr-traffic-addon.road_sign.updated"), true);
	}

	private static void receiveImageChunk(
		ServerPlayer player,
		BlockPos pos,
		String rawImageId,
		int totalBytes,
		int chunkIndex,
		int chunkCount,
		byte[] chunk
	) {
		if (!canEdit(player, pos) || !(player.serverLevel().getBlockEntity(pos) instanceof RoadSignBlockEntity)) {
			return;
		}
		final String imageId = RoadSignImageData.normalizeId(rawImageId);
		final int expectedChunkCount = (totalBytes + IMAGE_CHUNK_BYTES - 1) / IMAGE_CHUNK_BYTES;
		if (imageId.isEmpty()
			|| totalBytes <= 0
			|| totalBytes > RoadSignImageData.MAX_BYTES
			|| chunkCount <= 0
			|| chunkCount > MAX_IMAGE_CHUNKS
			|| chunkCount != expectedChunkCount
			|| chunkIndex < 0
			|| chunkIndex >= chunkCount) {
			return;
		}
		final int expectedChunkLength = chunkIndex == chunkCount - 1
			? totalBytes - IMAGE_CHUNK_BYTES * (chunkCount - 1)
			: IMAGE_CHUNK_BYTES;
		if (chunk.length != expectedChunkLength) {
			return;
		}

		final long currentTick = player.getServer().getTickCount();
		UPLOADS.entrySet().removeIf(entry -> currentTick - entry.getValue().createdTick() > MAX_UPLOAD_AGE_TICKS);
		final UploadKey key = new UploadKey(player.getUUID(), pos.immutable(), imageId);
		final UploadSession session;
		if (chunkIndex == 0) {
			UPLOADS.keySet().removeIf(existingKey -> existingKey.playerId().equals(player.getUUID()));
			session = new UploadSession(totalBytes, chunkCount, currentTick);
			UPLOADS.put(key, session);
		} else {
			session = UPLOADS.get(key);
			if (session == null || session.totalBytes() != totalBytes || session.chunkCount() != chunkCount) {
				return;
			}
		}

		if (!session.accept(chunkIndex, chunk) || !session.complete()) {
			return;
		}
		UPLOADS.remove(key);
		final byte[] imageData = session.assemble();
		try {
			final RoadSignImageData.ValidatedImage validated = RoadSignImageData.validate(imageData);
			if (!imageId.equals(validated.id())) {
				player.displayClientMessage(Component.translatable("message.mtr-traffic-addon.road_sign.image_invalid"), true);
				return;
			}
			RoadSignImageStore.store(player.getServer(), imageData);
		} catch (RoadSignImageData.ImageValidationException exception) {
			player.displayClientMessage(Component.translatable(validationMessage(exception.error())), true);
		} catch (IOException exception) {
			MTRTrafficAddon.LOGGER.error("Could not store a custom road sign image uploaded by {}", player.getGameProfile().getName(), exception);
			player.displayClientMessage(Component.translatable("message.mtr-traffic-addon.road_sign.image_failed"), true);
		}
	}

	private static void sendRequestedImage(ServerPlayer player, BlockPos pos, String rawImageId) {
		final String imageId = RoadSignImageData.normalizeId(rawImageId);
		if (imageId.isEmpty()
			|| player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > MAX_IMAGE_DISTANCE_SQUARED
			|| !(player.serverLevel().getBlockEntity(pos) instanceof RoadSignBlockEntity blockEntity)
			|| !imageId.equals(blockEntity.getImageId())) {
			return;
		}
		sendStoredImage(player, imageId);
	}

	private static void sendStoredImage(ServerPlayer player, String imageId) {
		if (imageId.isEmpty()) {
			return;
		}
		RoadSignImageStore.load(player.getServer(), imageId).ifPresent(data -> sendImage(player, imageId, data));
	}

	private static void broadcastImage(ServerPlayer editingPlayer, BlockPos pos, String imageId, byte[] data) {
		final Set<ServerPlayer> recipients = new LinkedHashSet<>(PlayerLookup.tracking(editingPlayer.serverLevel(), pos));
		recipients.add(editingPlayer);
		for (ServerPlayer recipient : recipients) {
			sendImage(recipient, imageId, data);
		}
	}

	private static void sendImage(ServerPlayer player, String imageId, byte[] data) {
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeUtf(imageId, RoadSignImageData.IMAGE_ID_LENGTH);
		buffer.writeByteArray(data);
		ServerPlayNetworking.send(player, MtaPacketPayload.fromBuffer(IMAGE_DATA_PACKET_ID, buffer));
	}

	private static String validationMessage(RoadSignImageData.ValidationError error) {
		return switch (error) {
			case TOO_LARGE -> "message.mtr-traffic-addon.road_sign.image_too_large";
			case INVALID_DIMENSIONS -> "message.mtr-traffic-addon.road_sign.image_dimensions";
			case INVALID_FORMAT -> "message.mtr-traffic-addon.road_sign.image_invalid";
		};
	}

	private static boolean canEdit(ServerPlayer player, BlockPos pos) {
		return player.mayBuild()
			&& hasMtrBrush(player)
			&& player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= MAX_EDIT_DISTANCE_SQUARED;
	}

	private static boolean hasMtrBrush(ServerPlayer player) {
		return isMtrBrush(player.getMainHandItem()) || isMtrBrush(player.getOffhandItem());
	}

	private static boolean isMtrBrush(ItemStack stack) {
		return !stack.isEmpty() && MTR_BRUSH_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	private record UploadKey(UUID playerId, BlockPos pos, String imageId) {
	}

	private static final class UploadSession {
		private final int totalBytes;
		private final byte[][] chunks;
		private final long createdTick;
		private int receivedChunks;

		private UploadSession(int totalBytes, int chunkCount, long createdTick) {
			this.totalBytes = totalBytes;
			chunks = new byte[chunkCount][];
			this.createdTick = createdTick;
		}

		private int totalBytes() {
			return totalBytes;
		}

		private int chunkCount() {
			return chunks.length;
		}

		private long createdTick() {
			return createdTick;
		}

		private boolean accept(int index, byte[] chunk) {
			if (chunks[index] != null) {
				return false;
			}
			chunks[index] = chunk;
			receivedChunks++;
			return true;
		}

		private boolean complete() {
			return receivedChunks == chunks.length;
		}

		private byte[] assemble() {
			final byte[] result = new byte[totalBytes];
			int offset = 0;
			for (byte[] chunk : chunks) {
				System.arraycopy(chunk, 0, result, offset, chunk.length);
				offset += chunk.length;
			}
			return result;
		}
	}
}
