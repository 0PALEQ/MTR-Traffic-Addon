package com.cookiecraftmods.mta.traffic;

import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionDefinition;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionRegistry;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphBuilder;
import com.cookiecraftmods.mta.traffic.point.TrafficPointDefinition;
import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import com.cookiecraftmods.mta.traffic.runtime.TrafficSpacingResolver;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinition;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinitionRegistry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.core.data.Rail;
import org.mtr.core.serializer.MessagePackReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.org.msgpack.core.MessagePack;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in replay; the original save is read-only and MTR runs on a temporary copy. */
class TrafficSavedWorldReplayTest {
	@TempDir Path replayDirectory;

	@Test
	@SuppressWarnings("unchecked")
	void replaySavedTrafficWithoutGlobalDeadlock() throws Exception {
		final String worldPath = System.getenv("MTA_REPLAY_WORLD");
		assumeTrue(worldPath != null && !worldPath.isBlank(), "Set MTA_REPLAY_WORLD to opt in");
		final Path world = Path.of(worldPath);
		Simulator mtr = null;
		java.lang.reflect.Method mtrTick = null;
		if (System.getenv("MTA_REPLAY_MTR") != null) {
			final Path mtrSource = world.resolve("mtr");
			try (var paths = Files.walk(mtrSource)) {
				for (Path source : paths.toList()) {
					final Path target = replayDirectory.resolve(mtrSource.relativize(source));
					if (Files.isDirectory(source)) {
						Files.createDirectories(target);
					} else {
						Files.copy(source, target);
					}
				}
			}
			mtr = new Simulator("minecraft/overworld", new String[]{"minecraft/overworld"}, replayDirectory, false);
			mtrTick = Simulator.class.getDeclaredMethod("tick", long.class);
			mtrTick.setAccessible(true);
		}
		final List<Rail> rails = new ArrayList<>();
		try (var paths = Files.walk(world.resolve("mtr/minecraft/overworld/rails"))) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
				try (var unpacker = MessagePack.newDefaultUnpacker(Files.readAllBytes(path))) {
					rails.add(new Rail(new MessagePackReader(unpacker)));
				}
			}
		}
		final var graph = MtrGraphBuilder.buildFromRailSnapshots(MtrGraphBuilder.snapshotRails(rails));
		final Gson gson = new Gson();
		final List<TrafficPointDefinition> points = gson.fromJson(
			Files.readString(world.resolve("data/mtr-traffic-addon/traffic_connector_points.json")),
			new TypeToken<List<TrafficPointDefinition>>() { }.getType());
		final List<TrafficIntersectionDefinition> intersections = gson.fromJson(
			Files.readString(world.resolve("data/mtr-traffic-addon/traffic_intersections.json")),
			new TypeToken<List<TrafficIntersectionDefinition>>() { }.getType());
		final Map<String, TrafficIntersectionDefinition> definitions =
			(Map<String, TrafficIntersectionDefinition>) field(TrafficIntersectionRegistry.class, "DEFINITIONS").get(null);
		definitions.clear();
		intersections.forEach(definition -> definitions.put(definition.id(), definition));
		final Map<String, TrafficVehicleDefinition> vehicleDefinitions =
			(Map<String, TrafficVehicleDefinition>) field(TrafficVehicleDefinitionRegistry.class, "DEFINITIONS").get(null);
		vehicleDefinitions.put("replay", new TrafficVehicleDefinition("replay", "car", 4.5, 70, 1, "replay", null, null));
		final List<TrafficPointDefinition> spawns = points.stream().filter(TrafficPointDefinition::isEnabled)
			.filter(point -> point.type() == TrafficPointType.SPAWN).toList();
		final List<TrafficPointDefinition> despawns = points.stream().filter(TrafficPointDefinition::isEnabled)
			.filter(point -> point.type() == TrafficPointType.DESPAWN).toList();
		field(TrafficManager.class, "latestGraph").set(null, graph);
		field(TrafficManager.class, "latestGraphDimensionId").set(null, "minecraft:overworld");
		field(TrafficManager.class, "materializationSnapshot").set(null,
			construct("MaterializationSnapshot", graph, "minecraft:overworld", spawns, despawns));
		field(TrafficManager.class, "playerSnapshots").set(null,
			List.of(construct("SimulationPlayerSnapshot", "minecraft:overworld", 6850.0, 2482.0, 16)));
		field(TrafficManager.class, "pendingRouteCacheSignature").set(null, "replay");
		TrafficManager.clearAllVehicles();
		final List<TrafficVehicle> vehicles = (List<TrafficVehicle>) field(TrafficManager.class, "ACTIVE_VEHICLES").get(null);
		System.out.println("Replay graph=" + graph.edges().size() + " spawns=" + spawns.size() + " despawns=" + despawns.size());
		final Map<java.util.UUID, Double> previousProgress = new java.util.HashMap<>();
		final Map<java.util.UUID, Integer> lastMovingTick = new java.util.HashMap<>();
		final Map<java.util.UUID, Long> outsideRangeAges = (Map<java.util.UUID, Long>) field(TrafficManager.class, "LAST_RENDERED_WALL_MILLIS").get(null);
		final int endTick = Integer.parseInt(System.getenv().getOrDefault("MTA_REPLAY_MINUTES", "20")) * 1200;
		long lastWallMillis = System.currentTimeMillis();
		int lastNetworkProgressTick = 0;
		try {
			for (int tick = 1; tick <= endTick; tick++) {
				// The fast replay must advance the unrendered-vehicle timeout too.
				final long nowMillis = System.currentTimeMillis();
				final long ageAdjustment = 50L - (nowMillis - lastWallMillis);
				outsideRangeAges.replaceAll((id, firstOutsideMillis) -> firstOutsideMillis - ageAdjustment);
				lastWallMillis = nowMillis;
				field(TrafficSignalClock.class, "epochNanos").setLong(null, System.nanoTime() - tick * 50_000_000L);
				if (mtr != null) {
					mtrTick.invoke(mtr, 50L);
				}
				TrafficManager.simulateUntil(tick);
				for (TrafficVehicle vehicle : vehicles) {
					final double progress = vehicle.segmentIndex() * 1_000_000.0 + vehicle.distanceOnSegmentMeters();
					final Double previous = previousProgress.get(vehicle.id());
					if (previous == null || Math.abs(progress - previous) > 0.1) {
						previousProgress.put(vehicle.id(), progress);
						lastMovingTick.put(vehicle.id(), tick);
						if (previous != null) {
							lastNetworkProgressTick = tick;
						}
					}
				}
				assertTrue(vehicles.isEmpty() || tick - lastNetworkProgressTick < 2400,
					"No vehicle has advanced at least ten centimeters for two simulated minutes; tick=" + tick);
				if (tick % 1200 == 0) {
					System.out.println("Replay minute=" + tick / 1200 + " vehicles=" + vehicles.size()
						+ " stopped=" + vehicles.stream().filter(vehicle -> vehicle.speedKph() < 0.01).count()
						+ " MTR=" + ((Map<?, ?>) field(TrafficManager.class, "MTR_VEHICLE_OCCUPANCY").get(null)).size());
				}
			}
			final var allowed = TrafficSpacingResolver.resolveAllowedSpeeds(vehicles);
			final var signalAllowed = new java.util.HashMap<>(allowed);
			TrafficIntersectionRegistry.applySignalSpeedLimits(vehicles, signalAllowed, endTick);
			for (TrafficVehicle vehicle : vehicles) {
				if (endTick - lastMovingTick.getOrDefault(vehicle.id(), endTick) > 2400) {
					System.out.println("Stopped " + vehicle.id() + " segment=" + vehicle.segmentIndex() + "/" + vehicle.route().segments().size()
						+ " distance=" + vehicle.distanceOnSegmentMeters() + " pos=" + vehicle.currentPosition()
						+ " spacing=" + allowed.get(vehicle) + " signals=" + signalAllowed.get(vehicle)
						+ " road=" + vehicle.currentSegment().orElseThrow().connectorId()
						+ " stuckSeconds=" + (endTick - lastMovingTick.get(vehicle.id())) / 20);
					final var closest = TrafficSpacingResolver.class.getDeclaredMethod("closestRouteObstacle", Map.class, TrafficVehicle.class);
					closest.setAccessible(true);
					final var buildIndex = TrafficSpacingResolver.class.getDeclaredMethod("buildVehiclesByDirectedSegment", java.util.Collection.class);
					buildIndex.setAccessible(true);
					final Object routeObstacle = closest.invoke(null, buildIndex.invoke(null, vehicles), vehicle);
					if (routeObstacle == null && allowed.get(vehicle) == 0.0) {
						System.out.println("Root alone=" + TrafficSpacingResolver.resolveAllowedSpeeds(List.of(vehicle)).get(vehicle));
						for (TrafficVehicle other : vehicles) {
							if (other != vehicle && TrafficSpacingResolver.resolveAllowedSpeeds(List.of(vehicle, other)).get(vehicle) == 0.0) {
								System.out.println("Root blocker=" + other.id() + " pos=" + other.currentPosition() + " remaining=" + other.distanceToEndOfCurrentSegmentMeters()
									+ " rootRemaining=" + vehicle.distanceToEndOfCurrentSegmentMeters() + " road=" + other.currentSegment().orElseThrow().directedConnectorId());
							}
						}
					}
				}
			}
			for (int i = 0; i < vehicles.size(); i++) {
				for (int j = i + 1; j < vehicles.size(); j++) {
					final var first = vehicles.get(i).currentPosition();
					final var second = vehicles.get(j).currentPosition();
					final double distance = Math.hypot(first.x() - second.x(), first.z() - second.z());
					if (distance < 4.0 && Math.abs(first.y() - second.y()) < 2.0) {
						System.out.println("Overlap " + vehicles.get(i).id() + " " + vehicles.get(j).id() + " distance=" + distance + " pos=" + first
							+ " firstRoad=" + vehicles.get(i).currentSegment().orElseThrow().directedConnectorId()
							+ " secondRoad=" + vehicles.get(j).currentSegment().orElseThrow().directedConnectorId()
							+ " speeds=" + vehicles.get(i).speedKph() + "," + vehicles.get(j).speedKph());
					}
				}
			}
		} finally {
			TrafficManager.clearAllVehicles();
			definitions.clear();
			vehicleDefinitions.clear();
			field(TrafficManager.class, "materializationSnapshot").set(null,
				construct("MaterializationSnapshot", null, null, List.of(), List.of()));
			field(TrafficManager.class, "playerSnapshots").set(null, List.of());
			field(TrafficManager.class, "lastTrafficSimulationTick").setLong(null, 0);
			((Map<?, ?>) field(TrafficManager.class, "MTR_VEHICLE_OCCUPANCY").get(null)).clear();
			((Map<?, ?>) field(TrafficManager.class, "MTR_VEHICLE_PATH_STATES").get(null)).clear();
			((Map<?, ?>) field(TrafficIntersectionRegistry.class, "AUTO_SIGNAL_STATES").get(null)).clear();
			field(TrafficManager.class, "latestGraph").set(null, null);
			field(TrafficManager.class, "latestGraphDimensionId").set(null, null);
			field(TrafficManager.class, "pendingRouteCacheSignature").set(null, "");
			field(TrafficManager.class, "lastTrafficSimulationWallMillis").setLong(null, 0);
			field(TrafficManager.class, "vehicleSimulationNanos").setLong(null, 0);
			TrafficSignalClock.reset();
		}
	}

	private static Object construct(String nestedClass, Object... arguments) throws Exception {
		final var constructor = Class.forName(TrafficManager.class.getName() + "$" + nestedClass).getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		return constructor.newInstance(arguments);
	}

	private static Field field(Class<?> type, String name) throws Exception {
		final Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
