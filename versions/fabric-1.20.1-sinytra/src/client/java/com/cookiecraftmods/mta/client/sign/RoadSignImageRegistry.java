package com.cookiecraftmods.mta.client.sign;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.sign.image.RoadSignImageData;
import com.cookiecraftmods.mta.traffic.sign.network.RoadSignNetworking;
import com.mojang.blaze3d.platform.NativeImage;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RoadSignImageRegistry {
	private static final Map<String, TextureEntry> TEXTURES = new HashMap<>();
	private static final Set<String> PENDING_REQUESTS = new HashSet<>();
	private static boolean initialized;

	private RoadSignImageRegistry() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		ClientPlayNetworking.registerGlobalReceiver(RoadSignNetworking.IMAGE_DATA_PACKET_ID, (client, handler, buffer, responseSender) -> {
			final String imageId = buffer.readUtf(RoadSignImageData.IMAGE_ID_LENGTH);
			final byte[] data = buffer.readByteArray(RoadSignImageData.MAX_BYTES);
			client.execute(() -> registerFromServer(imageId, data));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		initialized = true;
	}

	public static Optional<ResourceLocation> texture(String rawImageId, BlockPos blockPos) {
		final String imageId = RoadSignImageData.normalizeId(rawImageId);
		if (imageId.isEmpty()) {
			return Optional.empty();
		}
		final TextureEntry entry = TEXTURES.get(imageId);
		if (entry != null) {
			return Optional.of(entry.textureId());
		}
		request(imageId, blockPos);
		return Optional.empty();
	}

	public static Optional<ImageDimensions> dimensions(String rawImageId) {
		final TextureEntry entry = TEXTURES.get(RoadSignImageData.normalizeId(rawImageId));
		return entry == null ? Optional.empty() : Optional.of(new ImageDimensions(entry.width(), entry.height()));
	}

	public static RoadSignImageData.ValidatedImage registerLocal(byte[] data) throws RoadSignImageData.ImageValidationException, IOException {
		final RoadSignImageData.ValidatedImage validated = RoadSignImageData.validate(data);
		register(validated, data);
		return validated;
	}

	public static void clear() {
		final Minecraft minecraft = Minecraft.getInstance();
		for (TextureEntry entry : TEXTURES.values()) {
			minecraft.getTextureManager().release(entry.textureId());
		}
		TEXTURES.clear();
		PENDING_REQUESTS.clear();
	}

	private static void request(String imageId, BlockPos blockPos) {
		if (!PENDING_REQUESTS.add(imageId)) {
			return;
		}
		try {
			if (!ClientPlayNetworking.canSend(RoadSignNetworking.REQUEST_IMAGE_PACKET_ID)) {
				PENDING_REQUESTS.remove(imageId);
				return;
			}
			final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
			buffer.writeBlockPos(blockPos);
			buffer.writeUtf(imageId, RoadSignImageData.IMAGE_ID_LENGTH);
			ClientPlayNetworking.send(RoadSignNetworking.REQUEST_IMAGE_PACKET_ID, buffer);
		} catch (IllegalStateException exception) {
			PENDING_REQUESTS.remove(imageId);
		}
	}

	private static void registerFromServer(String rawImageId, byte[] data) {
		try {
			final RoadSignImageData.ValidatedImage validated = RoadSignImageData.validate(data);
			if (!validated.id().equals(RoadSignImageData.normalizeId(rawImageId))) {
				throw new IOException("Image content hash does not match its ID");
			}
			register(validated, data);
		} catch (IOException | RoadSignImageData.ImageValidationException exception) {
			MTRTrafficAddon.LOGGER.warn("Rejected custom road sign image {} from the server", rawImageId, exception);
		}
	}

	private static void register(RoadSignImageData.ValidatedImage validated, byte[] data) throws IOException {
		if (TEXTURES.containsKey(validated.id())) {
			PENDING_REQUESTS.remove(validated.id());
			return;
		}
		final NativeImage nativeImage = NativeImage.read(data);
		if (nativeImage.getWidth() != validated.width() || nativeImage.getHeight() != validated.height()) {
			nativeImage.close();
			throw new IOException("Decoded image dimensions changed during validation");
		}
		final DynamicTexture texture = new DynamicTexture(nativeImage);
		final ResourceLocation textureId = new ResourceLocation(MTRTrafficAddon.MOD_ID, "dynamic/road_sign/" + validated.id());
		try {
			Minecraft.getInstance().getTextureManager().register(textureId, texture);
		} catch (RuntimeException exception) {
			texture.close();
			throw exception;
		}
		TEXTURES.put(validated.id(), new TextureEntry(textureId, validated.width(), validated.height()));
		PENDING_REQUESTS.remove(validated.id());
	}

	private record TextureEntry(ResourceLocation textureId, int width, int height) {
	}

	public record ImageDimensions(int width, int height) {
		public float aspectRatio() {
			return (float) width / height;
		}
	}
}
