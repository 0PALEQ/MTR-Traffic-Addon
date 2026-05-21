package com.cookiecraftmods.mta.traffic.dashboard.network;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.TrafficManager;
import com.cookiecraftmods.mta.traffic.dashboard.TrafficIntersectionSnapshotEntry;
import com.cookiecraftmods.mta.traffic.dashboard.TrafficDashboardSnapshotEntry;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionDefinition;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionGroup;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNodeType;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionRegistry;
import com.cookiecraftmods.mta.traffic.point.TrafficPointDefinition;
import com.cookiecraftmods.mta.traffic.point.TrafficSavedPointRegistry;
import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class TrafficDashboardNetworking {
	public static final ResourceLocation SNAPSHOT_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "traffic_dashboard_snapshot");
	public static final ResourceLocation UPDATE_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "traffic_dashboard_update");
	public static final ResourceLocation INTERSECTION_UPDATE_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "traffic_dashboard_intersection_update");
	public static final ResourceLocation INTERSECTION_CREATE_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "traffic_dashboard_intersection_create");
	public static final ResourceLocation REFRESH_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "traffic_dashboard_refresh");
	public static final ResourceLocation CLEAR_VEHICLES_PACKET_ID = new ResourceLocation(MTRTrafficAddon.MOD_ID, "traffic_dashboard_clear_vehicles");
	private static boolean initialized;

	private TrafficDashboardNetworking() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerPlayNetworking.registerGlobalReceiver(REFRESH_PACKET_ID, (server, player, handler, buffer, responseSender) ->
			server.execute(() -> {
				final int refreshedRoutes = TrafficManager.refreshSavedConnectorRoutesNear(player);
				if (refreshedRoutes > 0) {
					MTRTrafficAddon.LOGGER.info("Traffic dashboard refresh updated {} saved connector route(s) for {}", refreshedRoutes, player.getGameProfile().getName());
				}
				sendSnapshot(player);
			})
		);

		ServerPlayNetworking.registerGlobalReceiver(CLEAR_VEHICLES_PACKET_ID, (server, player, handler, buffer, responseSender) ->
			server.execute(() -> {
				final int clearedVehicles = TrafficManager.clearAllVehicles();
				MTRTrafficAddon.LOGGER.info("Traffic dashboard cleared {} active traffic vehicle(s) for {}", clearedVehicles, player.getGameProfile().getName());
				sendSnapshot(player);
			})
		);

		ServerPlayNetworking.registerGlobalReceiver(UPDATE_PACKET_ID, (server, player, handler, buffer, responseSender) -> {
			final String pointId = buffer.readUtf();
			final BlockPos blockPos = buffer.readBlockPos();
			final String action = buffer.readUtf();
			final int delta = buffer.readVarInt();
			final String value = buffer.readBoolean() ? buffer.readUtf() : null;

			server.execute(() -> {
				applyUpdate(player, pointId, blockPos, action, delta, value);
				sendSnapshot(player);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(INTERSECTION_CREATE_PACKET_ID, (server, player, handler, buffer, responseSender) -> {
			final BlockPos firstCorner = buffer.readBlockPos();
			final BlockPos secondCorner = buffer.readBlockPos();
			server.execute(() -> {
				TrafficIntersectionRegistry.createIntersection(player.serverLevel(), firstCorner, secondCorner);
				sendSnapshot(player);
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(INTERSECTION_UPDATE_PACKET_ID, (server, player, handler, buffer, responseSender) -> {
			final String intersectionId = buffer.readUtf();
			final String action = buffer.readUtf();
			final int delta = buffer.readVarInt();
			final String value = buffer.readBoolean() ? buffer.readUtf() : null;
			server.execute(() -> {
				final boolean changed = TrafficIntersectionRegistry.applyDashboardUpdate(intersectionId, action, delta, value);
				final int refreshedNodes = "find_nodes".equals(action) || "auto_detect".equals(action) ? TrafficManager.refreshIntersectionNodesNear(player) : 0;
				MTRTrafficAddon.LOGGER.info("Traffic dashboard intersection update from {}: action={} delta={} value={} intersection={} changed={} refreshedNodes={}", player.getGameProfile().getName(), action, delta, value, intersectionId, changed, refreshedNodes);
				sendSnapshot(player);
			});
		});

		initialized = true;
	}

	public static void sendSnapshot(ServerPlayer player) {
		final List<TrafficDashboardSnapshotEntry> entries = collectEntries(player);
		final List<TrafficIntersectionSnapshotEntry> intersections = collectIntersections(player);
		final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeVarInt(entries.size());

		for (TrafficDashboardSnapshotEntry entry : entries) {
			buffer.writeUtf(entry.id());
			buffer.writeEnum(entry.type());
			buffer.writeLong(entry.x());
			buffer.writeLong(entry.y());
			buffer.writeLong(entry.z());
			buffer.writeVarInt(entry.group());
			buffer.writeBoolean(entry.enabled());
			buffer.writeVarInt(entry.maxVehicles());
			buffer.writeVarInt(entry.spawnIntervalTicks());
			buffer.writeVarInt(entry.targetGroup());
			buffer.writeVarInt(entry.activeVehicles());
			writeNullableLong(buffer, entry.connectorStartX());
			writeNullableLong(buffer, entry.connectorStartY());
			writeNullableLong(buffer, entry.connectorStartZ());
			writeNullableLong(buffer, entry.connectorEndX());
			writeNullableLong(buffer, entry.connectorEndY());
			writeNullableLong(buffer, entry.connectorEndZ());
			buffer.writeVarInt(entry.vehiclePool().size());
			for (String vehicleId : entry.vehiclePool()) {
				buffer.writeUtf(vehicleId);
			}
		}

		buffer.writeVarInt(intersections.size());
		for (TrafficIntersectionSnapshotEntry intersection : intersections) {
			buffer.writeUtf(intersection.id());
			buffer.writeUtf(intersection.name());
			buffer.writeLong(intersection.minX());
			buffer.writeLong(intersection.minY());
			buffer.writeLong(intersection.minZ());
			buffer.writeLong(intersection.maxX());
			buffer.writeLong(intersection.maxY());
			buffer.writeLong(intersection.maxZ());
			buffer.writeBoolean(intersection.enabled());
			buffer.writeBoolean(intersection.autoDetectNodes());
			buffer.writeEnum(intersection.signalMode());
			buffer.writeVarInt(intersection.phaseDurationTicks());
			buffer.writeVarInt(intersection.phaseOrder().size());
			for (Integer phase : intersection.phaseOrder()) {
				buffer.writeVarInt(phase);
			}
			buffer.writeVarInt(intersection.groups().size());
			for (TrafficIntersectionGroup group : intersection.groups()) {
				buffer.writeUtf(group.name());
				buffer.writeVarInt(group.effectiveGreenDurationTicks());
				buffer.writeVarInt(group.nodeNumbers().size());
				for (Integer nodeNumber : group.nodeNumbers()) {
					buffer.writeVarInt(nodeNumber);
				}
			}
			buffer.writeVarInt(intersection.nodes().size());
			for (TrafficIntersectionNode node : intersection.nodes()) {
				buffer.writeLong(node.x());
				buffer.writeLong(node.y());
				buffer.writeLong(node.z());
				buffer.writeEnum(node.type());
				buffer.writeVarInt(node.number());
			}
		}

		ServerPlayNetworking.send(player, SNAPSHOT_PACKET_ID, buffer);
	}

	private static List<TrafficDashboardSnapshotEntry> collectEntries(ServerPlayer player) {
		final String dimensionId = player.level().dimension().location().toString();
		final Map<String, Integer> activeVehiclesBySpawnPointId = TrafficManager.countActiveVehiclesBySpawnPointId();
		final List<TrafficPointDefinition> combinedDefinitions = TrafficSavedPointRegistry.getDefinitions().stream().filter(definition -> definition.id().startsWith(dimensionId + "|")).toList();

		return combinedDefinitions.stream()
			.sorted(Comparator.comparing(TrafficPointDefinition::type).thenComparingLong(TrafficPointDefinition::x).thenComparingLong(TrafficPointDefinition::z))
			.map(definition -> new TrafficDashboardSnapshotEntry(
				definition.id(),
				definition.type(),
				definition.x(),
				definition.y(),
				definition.z(),
				definition.group(),
				definition.isEnabled(),
				definition.effectiveMaxVehicles(),
				definition.effectiveSpawnIntervalTicks(),
				definition.effectiveTargetGroup(),
				activeVehiclesBySpawnPointId.getOrDefault(definition.id(), 0),
				definition.connectorStartX(),
				definition.connectorStartY(),
				definition.connectorStartZ(),
				definition.connectorEndX(),
				definition.connectorEndY(),
				definition.connectorEndZ(),
				definition.effectiveVehiclePool()
			))
			.toList();
	}

	private static List<TrafficIntersectionSnapshotEntry> collectIntersections(ServerPlayer player) {
		final String dimensionId = player.level().dimension().location().toString();
		return TrafficIntersectionRegistry.getDefinitions().stream()
			.filter(definition -> definition.dimensionId().equals(dimensionId))
			.sorted(Comparator.comparingLong(TrafficIntersectionDefinition::minX).thenComparingLong(TrafficIntersectionDefinition::minZ))
			.map(definition -> new TrafficIntersectionSnapshotEntry(
				definition.id(),
				definition.effectiveName(),
				definition.minX(),
				definition.minY(),
				definition.minZ(),
				definition.maxX(),
				definition.maxY(),
				definition.maxZ(),
				definition.isEnabled(),
				definition.effectiveAutoDetectNodes(),
				definition.effectiveSignalMode(),
				definition.effectivePhaseDurationTicks(),
				definition.phaseOrder(),
				definition.groups(),
				definition.nodes()
			))
			.toList();
	}

	private static void applyUpdate(ServerPlayer player, String pointId, BlockPos blockPos, String action, int delta, String value) {
		final boolean changed;
		if ("vehicle_pool_toggle".equals(action)) {
			changed = TrafficSavedPointRegistry.toggleVehiclePool(pointId, value);
		} else {
			changed = TrafficSavedPointRegistry.applyUpdate(pointId, action, delta);
		}
		MTRTrafficAddon.LOGGER.info("Traffic dashboard update from {}: action={} delta={} value={} point={} at {} changed={}", player.getGameProfile().getName(), action, delta, value, pointId, blockPos, changed);
	}

	private static void writeNullableLong(FriendlyByteBuf buffer, Long value) {
		buffer.writeBoolean(value != null);
		if (value != null) {
			buffer.writeLong(value);
		}
	}
}
