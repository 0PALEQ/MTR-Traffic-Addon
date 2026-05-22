package com.cookiecraftmods.mta.client;

import com.cookiecraftmods.mta.client.dashboard.ClientTrafficDashboardEntry;
import com.cookiecraftmods.mta.client.dashboard.ClientTrafficIntersectionEntry;
import com.cookiecraftmods.mta.client.dashboard.TrafficDashboardClient;
import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugSnapshot;
import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugState;
import com.cookiecraftmods.mta.client.lights.TrafficLightBindingScreen;
import com.cookiecraftmods.mta.client.lights.TrafficLightEmissiveRenderer;
import com.cookiecraftmods.mta.client.render.ClientMtrVehicleResourceRegistry;
import com.cookiecraftmods.mta.client.render.ClientTrafficRenderDispatcher;
import com.cookiecraftmods.mta.client.render.custom.CustomTrafficModelRegistry;
import com.cookiecraftmods.mta.init.ModBlocks;
import com.cookiecraftmods.mta.init.ModItems;
import com.cookiecraftmods.mta.traffic.dashboard.network.TrafficDashboardNetworking;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionGroup;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNodeType;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionSignalMode;
import com.cookiecraftmods.mta.traffic.lights.network.TrafficLightBindingNetworking;
import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import com.cookiecraftmods.mta.traffic.network.TrafficNetworking;
import net.minecraft.client.renderer.item.ItemProperties;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class MTRTrafficAddonClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientMtrVehicleResourceRegistry.initialize();
		CustomTrafficModelRegistry.initialize();
		TrafficLightEmissiveRenderer.initialize();
		BlockRenderLayerMap.INSTANCE.putBlocks(
			RenderType.cutout(),
			ModBlocks.TRAFFIC_LIGHTS_POLE_BOTTOM,
			ModBlocks.TRAFFIC_LIGHTS_POLE,
			ModBlocks.TRAFFIC_LIGHTS_VERTICAL_POLE,
			ModBlocks.TRAFFIC_LIGHTS_PRIMARY
		);
		ItemProperties.register(ModItems.TRAFFIC_SPAWN_CONNECTOR, new ResourceLocation("mtr", "selected"), (stack, level, entity, seed) -> stack.getTag() != null && stack.getTag().contains("pos") ? 1.0F : 0.0F);
		ItemProperties.register(ModItems.TRAFFIC_DESPAWN_CONNECTOR, new ResourceLocation("mtr", "selected"), (stack, level, entity, seed) -> stack.getTag() != null && stack.getTag().contains("pos") ? 1.0F : 0.0F);

		ClientPlayNetworking.registerGlobalReceiver(TrafficNetworking.DEBUG_SNAPSHOT_PACKET_ID, (client, handler, buffer, responseSender) -> {
			final int count = buffer.readVarInt();
			final List<ClientTrafficDebugSnapshot> snapshots = new ArrayList<>(count);

			for (int i = 0; i < count; i++) {
				snapshots.add(new ClientTrafficDebugSnapshot(
					buffer.readUUID(),
					buffer.readUtf(),
					buffer.readUtf(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readFloat(),
					buffer.readDouble()
				));
			}

			client.execute(() -> ClientTrafficDebugState.replace(snapshots));
		});

		ClientPlayNetworking.registerGlobalReceiver(TrafficDashboardNetworking.SNAPSHOT_PACKET_ID, (client, handler, buffer, responseSender) -> {
			final int count = buffer.readVarInt();
			final List<ClientTrafficDashboardEntry> entries = new ArrayList<>(count);

			for (int i = 0; i < count; i++) {
				final String id = buffer.readUtf();
				final TrafficPointType type = buffer.readEnum(TrafficPointType.class);
				final BlockPos blockPos = new BlockPos((int) buffer.readLong(), (int) buffer.readLong(), (int) buffer.readLong());
				final int group = buffer.readVarInt();
				final boolean enabled = buffer.readBoolean();
				final int maxVehicles = buffer.readVarInt();
				final int spawnIntervalTicks = buffer.readVarInt();
				final int targetGroup = buffer.readVarInt();
				final int activeVehicles = buffer.readVarInt();
				final Long connectorStartX = readNullableLong(buffer);
				final Long connectorStartY = readNullableLong(buffer);
				final Long connectorStartZ = readNullableLong(buffer);
				final Long connectorEndX = readNullableLong(buffer);
				final Long connectorEndY = readNullableLong(buffer);
				final Long connectorEndZ = readNullableLong(buffer);
				final int vehiclePoolSize = buffer.readVarInt();
				final List<String> vehiclePool = new ArrayList<>(vehiclePoolSize);
				for (int j = 0; j < vehiclePoolSize; j++) {
					vehiclePool.add(buffer.readUtf());
				}

				entries.add(new ClientTrafficDashboardEntry(
					id,
					type,
					blockPos,
					group,
					enabled,
					maxVehicles,
					spawnIntervalTicks,
					targetGroup,
					activeVehicles,
					connectorStartX == null || connectorStartY == null || connectorStartZ == null ? null : new BlockPos(connectorStartX.intValue(), connectorStartY.intValue(), connectorStartZ.intValue()),
					connectorEndX == null || connectorEndY == null || connectorEndZ == null ? null : new BlockPos(connectorEndX.intValue(), connectorEndY.intValue(), connectorEndZ.intValue()),
					vehiclePool
				));
			}

			final int intersectionCount = buffer.readVarInt();
			final List<ClientTrafficIntersectionEntry> intersections = new ArrayList<>(intersectionCount);
			for (int i = 0; i < intersectionCount; i++) {
				final String id = buffer.readUtf();
				final String name = buffer.readUtf();
				final long minX = buffer.readLong();
				final long minY = buffer.readLong();
				final long minZ = buffer.readLong();
				final long maxX = buffer.readLong();
				final long maxY = buffer.readLong();
				final long maxZ = buffer.readLong();
				final boolean enabled = buffer.readBoolean();
				final boolean autoDetectNodes = buffer.readBoolean();
				final TrafficIntersectionSignalMode signalMode = buffer.readEnum(TrafficIntersectionSignalMode.class);
				final int phaseDurationTicks = buffer.readVarInt();
				final int phaseOrderSize = buffer.readVarInt();
				final List<Integer> phaseOrder = new ArrayList<>(phaseOrderSize);
				for (int j = 0; j < phaseOrderSize; j++) {
					phaseOrder.add(buffer.readVarInt());
				}
				final int groupCount = buffer.readVarInt();
				final List<TrafficIntersectionGroup> groups = new ArrayList<>(groupCount);
				for (int j = 0; j < groupCount; j++) {
					final String groupName = buffer.readUtf();
					final int greenDurationTicks = buffer.readVarInt();
					final int nodeNumberCount = buffer.readVarInt();
					final List<Integer> nodeNumbers = new ArrayList<>(nodeNumberCount);
					for (int k = 0; k < nodeNumberCount; k++) {
						nodeNumbers.add(buffer.readVarInt());
					}
					groups.add(new TrafficIntersectionGroup(groupName, greenDurationTicks, nodeNumbers));
				}
				final int nodeCount = buffer.readVarInt();
				final List<TrafficIntersectionNode> nodes = new ArrayList<>(nodeCount);
				for (int j = 0; j < nodeCount; j++) {
					nodes.add(new TrafficIntersectionNode(
						buffer.readLong(),
						buffer.readLong(),
						buffer.readLong(),
						buffer.readEnum(TrafficIntersectionNodeType.class),
						buffer.readVarInt()
					));
				}
				intersections.add(new ClientTrafficIntersectionEntry(id, name, minX, minY, minZ, maxX, maxY, maxZ, enabled, autoDetectNodes, signalMode, phaseDurationTicks, phaseOrder, groups, nodes));
			}

			client.execute(() -> TrafficDashboardClient.openOrUpdate(entries, intersections));
		});

		ClientPlayNetworking.registerGlobalReceiver(TrafficLightBindingNetworking.OPEN_MENU_PACKET_ID, (client, handler, buffer, responseSender) -> {
			final BlockPos blockPos = buffer.readBlockPos();
			final int intersectionCount = buffer.readVarInt();
			final List<TrafficLightBindingScreen.IntersectionOption> intersections = new ArrayList<>(intersectionCount);
			for (int i = 0; i < intersectionCount; i++) {
				final String id = buffer.readUtf();
				final String name = buffer.readUtf();
				final int nodeCount = buffer.readVarInt();
				final List<TrafficIntersectionNode> nodes = new ArrayList<>(nodeCount);
				for (int j = 0; j < nodeCount; j++) {
					nodes.add(new TrafficIntersectionNode(
						buffer.readLong(),
						buffer.readLong(),
						buffer.readLong(),
						buffer.readEnum(TrafficIntersectionNodeType.class),
						buffer.readVarInt()
					));
				}
				intersections.add(new TrafficLightBindingScreen.IntersectionOption(id, name, nodes));
			}
			client.execute(() -> client.setScreen(new TrafficLightBindingScreen(blockPos, intersections)));
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientTrafficDebugState.clear();
			TrafficDashboardClient.clear();
		});
		WorldRenderEvents.AFTER_ENTITIES.register(ClientTrafficRenderDispatcher::render);
	}

	private static Long readNullableLong(net.minecraft.network.FriendlyByteBuf buffer) {
		return buffer.readBoolean() ? buffer.readLong() : null;
	}
}
