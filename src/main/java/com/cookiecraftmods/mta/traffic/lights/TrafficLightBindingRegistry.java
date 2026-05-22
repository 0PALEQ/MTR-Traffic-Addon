package com.cookiecraftmods.mta.traffic.lights;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.init.ModBlocks;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionDefinition;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNodeType;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionRegistry;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightSignalState;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsPoleTopBlock;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsPrimaryBlock;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsVerticalPoleBlock;
import com.cookiecraftmods.mta.traffic.lights.network.TrafficLightBindingNetworking;
import com.cookiecraftmods.mta.traffic.TrafficManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrafficLightBindingRegistry {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<TrafficLightBinding>>() { }.getType();
	private static final ResourceLocation MTR_BRUSH_ID = new ResourceLocation("mtr", "brush");
	private static final Map<String, TrafficLightBinding> BINDINGS = new LinkedHashMap<>();
	private static boolean initialized;
	private static MinecraftServer currentServer;

	private TrafficLightBindingRegistry() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			currentServer = server;
			load(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			save(server);
			currentServer = null;
			BINDINGS.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(TrafficLightBindingRegistry::tick);
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			final ItemStack stack = player.getItemInHand(hand);
			if (!isMtrBrush(stack)) {
				return InteractionResult.PASS;
			}
			final BlockPos blockPos = hitResult.getBlockPos();
			if (!isBindableTrafficLight(world.getBlockState(blockPos))) {
				return InteractionResult.PASS;
			}
			openBindMenu(serverPlayer, blockPos);
			return InteractionResult.SUCCESS;
		});
		initialized = true;
	}

	public static void bind(ServerPlayer player, BlockPos blockPos, String intersectionId, int nodeNumber) {
		final ServerLevel level = player.serverLevel();
		final BlockState state = level.getBlockState(blockPos);
		if (!isBindableTrafficLight(state)) {
			player.displayClientMessage(Component.literal("This block is not a bindable traffic light."), true);
			return;
		}
		final TrafficIntersectionDefinition definition = TrafficIntersectionRegistry.getDefinition(intersectionId).orElse(null);
		if (definition == null || !definition.dimensionId().equals(level.dimension().location().toString()) || !definition.contains(blockPos.getX(), blockPos.getY(), blockPos.getZ())) {
			player.displayClientMessage(Component.literal("Traffic light is not inside that intersection."), true);
			return;
		}
		final boolean nodeExists = definition.nodes().stream().anyMatch(node -> node.type() == TrafficIntersectionNodeType.IN && node.number() == nodeNumber);
		if (!nodeExists) {
			player.displayClientMessage(Component.literal("Intersection IN node #" + nodeNumber + " does not exist."), true);
			return;
		}

		final TrafficLightBinding binding = new TrafficLightBinding(level.dimension().location().toString(), blockPos.getX(), blockPos.getY(), blockPos.getZ(), intersectionId, nodeNumber);
		BINDINGS.put(key(binding.dimensionId(), blockPos), binding);
		applyBindingState(level, blockPos, binding);
		saveIfPossible();
		player.displayClientMessage(Component.literal("Bound traffic light to node #" + nodeNumber + "."), true);
	}

	private static void openBindMenu(ServerPlayer player, BlockPos blockPos) {
		final List<TrafficIntersectionDefinition> intersections = TrafficIntersectionRegistry.getDefinitions().stream()
			.filter(definition -> definition.dimensionId().equals(player.level().dimension().location().toString()))
			.filter(definition -> definition.contains(blockPos.getX(), blockPos.getY(), blockPos.getZ()))
			.sorted(Comparator.comparing(TrafficIntersectionDefinition::effectiveName, String.CASE_INSENSITIVE_ORDER))
			.toList();
		if (intersections.isEmpty()) {
			player.displayClientMessage(Component.literal("This traffic light is not inside an intersection area."), true);
			return;
		}

		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeBlockPos(blockPos);
		buffer.writeVarInt(intersections.size());
		for (TrafficIntersectionDefinition intersection : intersections) {
			final List<TrafficIntersectionNode> inNodes = intersection.nodes().stream()
				.filter(node -> node.type() == TrafficIntersectionNodeType.IN)
				.toList();
			buffer.writeUtf(intersection.id());
			buffer.writeUtf(intersection.effectiveName());
			buffer.writeVarInt(inNodes.size());
			for (TrafficIntersectionNode node : inNodes) {
				buffer.writeLong(node.x());
				buffer.writeLong(node.y());
				buffer.writeLong(node.z());
				buffer.writeEnum(node.type());
				buffer.writeVarInt(node.number());
			}
		}
		ServerPlayNetworking.send(player, TrafficLightBindingNetworking.OPEN_MENU_PACKET_ID, buffer);
	}

	private static void tick(MinecraftServer server) {
		if (BINDINGS.isEmpty()) {
			return;
		}
		for (TrafficLightBinding binding : List.copyOf(BINDINGS.values())) {
			ServerLevel level = null;
			for (ServerLevel candidateLevel : server.getAllLevels()) {
				if (candidateLevel.dimension().location().toString().equals(binding.dimensionId())) {
					level = candidateLevel;
					break;
				}
			}
			if (level == null) {
				continue;
			}
			applyBindingState(level, new BlockPos((int) binding.x(), (int) binding.y(), (int) binding.z()), binding);
		}
	}

	private static void applyBindingState(ServerLevel level, BlockPos blockPos, TrafficLightBinding binding) {
		final BlockState state = level.getBlockState(blockPos);
		if (!isBindableTrafficLight(state)) {
			BINDINGS.remove(key(binding.dimensionId(), blockPos));
			saveIfPossible();
			return;
		}
		final TrafficLightSignalState signal = TrafficIntersectionRegistry.signalState(binding.intersectionId(), binding.nodeNumber(), TrafficManager.signalTick()).orElse(TrafficLightSignalState.RED);
		if (state.is(ModBlocks.TRAFFIC_LIGHTS_POLE)) {
			final BlockState updated = state
				.setValue(TrafficLightsPoleTopBlock.HAS_LIGHTS, true)
				.setValue(TrafficLightsPoleTopBlock.SIGNAL, signal);
			if (updated != state) {
				level.setBlock(blockPos, updated, Block.UPDATE_ALL);
			}
		} else if (state.is(ModBlocks.TRAFFIC_LIGHTS_PRIMARY)) {
			final BlockState updated = state.setValue(TrafficLightsPrimaryBlock.SIGNAL, signal);
			if (updated != state) {
				level.setBlock(blockPos, updated, Block.UPDATE_ALL);
			}
		} else if (state.is(ModBlocks.TRAFFIC_LIGHTS_VERTICAL_POLE)) {
			final BlockState updated = state
				.setValue(TrafficLightsVerticalPoleBlock.HAS_LIGHTS, true)
				.setValue(TrafficLightsVerticalPoleBlock.SIGNAL, signal);
			if (updated != state) {
				level.setBlock(blockPos, updated, Block.UPDATE_ALL);
			}
		}
	}

	private static boolean isBindableTrafficLight(BlockState state) {
		return state.is(ModBlocks.TRAFFIC_LIGHTS_POLE) || state.is(ModBlocks.TRAFFIC_LIGHTS_PRIMARY) || state.is(ModBlocks.TRAFFIC_LIGHTS_VERTICAL_POLE);
	}

	private static boolean isMtrBrush(ItemStack stack) {
		return !stack.isEmpty() && MTR_BRUSH_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	private static String key(String dimensionId, BlockPos blockPos) {
		return dimensionId + "|" + blockPos.asLong();
	}

	private static String key(String dimensionId, long x, long y, long z) {
		return dimensionId + "|" + BlockPos.asLong((int) x, (int) y, (int) z);
	}

	private static void load(MinecraftServer server) {
		BINDINGS.clear();
		final Path path = savePath(server);
		if (!Files.exists(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			final List<TrafficLightBinding> bindings = GSON.fromJson(reader, LIST_TYPE);
			if (bindings != null) {
				for (TrafficLightBinding binding : bindings) {
					BINDINGS.put(key(binding.dimensionId(), binding.x(), binding.y(), binding.z()), binding);
				}
			}
		} catch (Exception e) {
			MTRTrafficAddon.LOGGER.error("Failed to load traffic light bindings", e);
		}
	}

	private static void saveIfPossible() {
		if (currentServer != null) {
			save(currentServer);
		}
	}

	private static void save(MinecraftServer server) {
		try {
			final Path path = savePath(server);
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(List.copyOf(BINDINGS.values()), writer);
			}
		} catch (Exception e) {
			MTRTrafficAddon.LOGGER.error("Failed to save traffic light bindings", e);
		}
	}

	private static Path savePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MTRTrafficAddon.MOD_ID).resolve("traffic_light_bindings.json");
	}
}
