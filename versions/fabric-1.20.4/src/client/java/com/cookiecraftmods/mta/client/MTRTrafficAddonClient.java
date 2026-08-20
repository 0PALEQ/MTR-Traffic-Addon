package com.cookiecraftmods.mta.client;

import com.cookiecraftmods.mta.client.dashboard.ClientTrafficDashboardEntry;
import com.cookiecraftmods.mta.client.dashboard.ClientTrafficIntersectionEntry;
import com.cookiecraftmods.mta.client.dashboard.TrafficDashboardClient;
import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugSnapshot;
import com.cookiecraftmods.mta.client.debug.ClientTrafficDebugState;
import com.cookiecraftmods.mta.client.lights.TrafficLightBindingScreen;
import com.cookiecraftmods.mta.client.lights.TrafficLightEmissiveRenderer;
import com.cookiecraftmods.mta.client.sign.RoadSignBaseRegistry;
import com.cookiecraftmods.mta.client.sign.RoadSignEditScreen;
import com.cookiecraftmods.mta.client.sign.RoadSignImageRegistry;
import com.cookiecraftmods.mta.client.sign.RoadSignRenderer;
import com.cookiecraftmods.mta.client.tollgate.TollgateRenderer;
import com.cookiecraftmods.mta.client.render.ClientMtrVehicleResourceRegistry;
import com.cookiecraftmods.mta.client.render.ClientTrafficRenderDispatcher;
import com.cookiecraftmods.mta.client.render.custom.CustomTrafficModelRegistry;
import com.cookiecraftmods.mta.init.ModBlocks;
import com.cookiecraftmods.mta.init.ModItems;
import com.cookiecraftmods.mta.traffic.dashboard.network.TrafficDashboardNetworking;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionGroup;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionLevel;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNodeType;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionSignalMode;
import com.cookiecraftmods.mta.traffic.lights.network.TrafficLightBindingNetworking;
import com.cookiecraftmods.mta.traffic.sign.entity.RoadSignBlockEntity;
import com.cookiecraftmods.mta.traffic.sign.network.RoadSignNetworking;
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
		// INICJALIZACJA SYSTEMÓW RENDEROWANIA
		ClientMtrVehicleResourceRegistry.initialize();     // Zasoby pojazdów z MTR
		CustomTrafficModelRegistry.initialize();            // Niestandardowe modele pojazdów
		TrafficLightEmissiveRenderer.initialize();          // Renderowanie emitowanych świateł
		TollgateRenderer.initialize();
		RoadSignBaseRegistry.initialize();
		RoadSignImageRegistry.initialize();
		RoadSignRenderer.initialize();

		// USTAWIENIA RENDER LAYERS - które bloki są przezroczyste
		BlockRenderLayerMap.INSTANCE.putBlocks(
			RenderType.cutout(),
			ModBlocks.TRAFFIC_LIGHTS_POLE_BOTTOM,
			ModBlocks.TRAFFIC_LIGHTS_POLE,
			ModBlocks.TRAFFIC_LIGHTS_VERTICAL_POLE,
			ModBlocks.TRAFFIC_LIGHTS_PRIMARY,
			ModBlocks.PEDESTRIAN_LIGHTS,
			ModBlocks.PEDESTRIAN_LIGHTS_POLE,
			ModBlocks.TOLLGATE_POLE,
			ModBlocks.TOLLGATE_BAR,
			ModBlocks.ROAD_SIGN
		);

		// WŁAŚCIWOŚCI ITEMÓW - zmienia visual state na podstawie tagu NBT
		// Connector zmienia wygląd gdy ma zapisaną pozycję
		ItemProperties.register(ModItems.TRAFFIC_SPAWN_CONNECTOR, new ResourceLocation("mtr", "selected"), (stack, level, entity, seed) -> stack.getTag() != null && stack.getTag().contains("pos") ? 1.0F : 0.0F);
		ItemProperties.register(ModItems.TRAFFIC_DESPAWN_CONNECTOR, new ResourceLocation("mtr", "selected"), (stack, level, entity, seed) -> stack.getTag() != null && stack.getTag().contains("pos") ? 1.0F : 0.0F);
		ItemProperties.register(ModItems.SIGNAL_PATH_BLOCKER_CONNECTOR, new ResourceLocation("mtr", "selected"), (stack, level, entity, seed) -> stack.getTag() != null && stack.getTag().contains("pos") ? 1.0F : 0.0F);
		ItemProperties.register(ModItems.MTA_PATH_BLOCKER_CONNECTOR, new ResourceLocation("mtr", "selected"), (stack, level, entity, seed) -> stack.getTag() != null && stack.getTag().contains("pos") ? 1.0F : 0.0F);

		// NETWORK PACKET LISTENERS - odbieranie danych z serwera
		// Debug snapshot - pozycje pojazdów dla debugowania
		ClientPlayNetworking.registerGlobalReceiver(TrafficNetworking.DEBUG_SNAPSHOT_PACKET_ID, (client, handler, buffer, responseSender) -> {
			final long sequence = buffer.readLong();
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
					buffer.readFloat(),
					buffer.readDouble()
				));
			}

			client.execute(() -> ClientTrafficDebugState.replace(sequence, snapshots));
		});

		// DASHBOARD PACKET - informacje o punktach spawn i skrzyżowaniach
		ClientPlayNetworking.registerGlobalReceiver(TrafficDashboardNetworking.SNAPSHOT_PACKET_ID, (client, handler, buffer, responseSender) -> {
			final int count = buffer.readVarInt();
			final List<ClientTrafficDashboardEntry> entries = new ArrayList<>(count);

			for (int i = 0; i < count; i++) {
				final String id = buffer.readUtf();
				final String name = buffer.readUtf();
				final TrafficPointType type = buffer.readEnum(TrafficPointType.class);
				final BlockPos blockPos = new BlockPos((int) buffer.readLong(), (int) buffer.readLong(), (int) buffer.readLong());
				final boolean enabled = buffer.readBoolean();
				final int spawnIntervalTicks = buffer.readVarInt();
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
					name,
					type,
					blockPos,
					enabled,
					spawnIntervalTicks,
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
				final TrafficIntersectionLevel level = buffer.readEnum(TrafficIntersectionLevel.class);
				final TrafficIntersectionSignalMode signalMode = buffer.readEnum(TrafficIntersectionSignalMode.class);
				final int phaseDurationTicks = buffer.readVarInt();
				final int phaseOrderSize = buffer.readVarInt();
				final List<Integer> phaseOrder = new ArrayList<>(phaseOrderSize);
				for (int j = 0; j < phaseOrderSize; j++) {
					phaseOrder.add(buffer.readVarInt());
				}
				final int trainNodeNumberCount = buffer.readVarInt();
				final List<Integer> trainNodeNumbers = new ArrayList<>(trainNodeNumberCount);
				for (int j = 0; j < trainNodeNumberCount; j++) {
					trainNodeNumbers.add(buffer.readVarInt());
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
				intersections.add(new ClientTrafficIntersectionEntry(id, name, minX, minY, minZ, maxX, maxY, maxZ, enabled, autoDetectNodes, level, signalMode, phaseDurationTicks, phaseOrder, trainNodeNumbers, groups, nodes));
			}

			client.execute(() -> TrafficDashboardClient.openOrUpdate(entries, intersections));
		});

		// LIGHT BINDING MENU PACKET - otwarcie menu bindowania świateł
		ClientPlayNetworking.registerGlobalReceiver(TrafficLightBindingNetworking.OPEN_MENU_PACKET_ID, (client, handler, buffer, responseSender) -> {
			final BlockPos blockPos = buffer.readBlockPos();
			final int intersectionCount = buffer.readVarInt();
			final List<TrafficLightBindingScreen.IntersectionOption> intersections = new ArrayList<>(intersectionCount);
			for (int i = 0; i < intersectionCount; i++) {
				final String id = buffer.readUtf();
				final String name = buffer.readUtf();
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
				intersections.add(new TrafficLightBindingScreen.IntersectionOption(id, name, groups, nodes));
			}
			client.execute(() -> client.setScreen(new TrafficLightBindingScreen(blockPos, intersections)));
		});

		ClientPlayNetworking.registerGlobalReceiver(RoadSignNetworking.OPEN_EDITOR_PACKET_ID, (client, handler, buffer, responseSender) -> {
			final BlockPos blockPos = buffer.readBlockPos();
			final ResourceLocation parsedBaseId = ResourceLocation.tryParse(buffer.readUtf(RoadSignNetworking.MAX_BASE_ID_LENGTH));
			final ResourceLocation baseId = parsedBaseId == null ? RoadSignBlockEntity.DEFAULT_BASE_ID : parsedBaseId;
			final int lineCount = buffer.readVarInt();
			if (lineCount < 0 || lineCount > RoadSignBlockEntity.MAX_LINES) {
				return;
			}
			final List<String> lines = new ArrayList<>(Math.max(0, lineCount));
			for (int index = 0; index < lineCount; index++) {
				lines.add(buffer.readUtf(RoadSignBlockEntity.MAX_LINE_LENGTH));
			}
			final int textColor = buffer.readInt();
			final float signWidth = buffer.readFloat();
			final float signHeight = buffer.readFloat();
			final int backgroundColor = buffer.readInt();
			final int edgeColor = buffer.readInt();
			final String imageId = buffer.readUtf(com.cookiecraftmods.mta.traffic.sign.image.RoadSignImageData.IMAGE_ID_LENGTH);
			client.execute(() -> client.setScreen(new RoadSignEditScreen(blockPos, baseId, lines, textColor, signWidth, signHeight, backgroundColor, edgeColor, imageId)));
		});

		// EVENT LISTENERY - czyszczenie przy rozłączeniu i renderowanie
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
