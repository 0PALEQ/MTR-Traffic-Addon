package com.cookiecraftmods.mta.traffic;

import com.cookiecraftmods.mta.traffic.runtime.TrafficRoute;
import com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint;
import com.cookiecraftmods.mta.traffic.runtime.TrafficMaterializationIndex;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRouteSegment;
import com.cookiecraftmods.mta.traffic.runtime.TrafficSpacingResolver;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class TrafficSpacingRegressionTest {
	@Test
	void threeCloseMergeApproachesCannotYieldInACycle() {
		final List<TrafficVehicle> vehicles = new ArrayList<>(List.of(
			approachingCar(3, 0.5, 0), approachingCar(2, 0.7, 120), approachingCar(1, 0.9, 240)));
		assertTrue(TrafficSpacingResolver.resolveAllowedSpeeds(vehicles).values().stream().anyMatch(speed -> speed > 0),
			"A merge must have a winner, even when pairwise distance differences straddle the priority tolerance");
		for (int tick = 0; tick < 600 && !vehicles.isEmpty(); tick++) {
			final var allowed = TrafficSpacingResolver.resolveAllowedSpeeds(vehicles);
			vehicles.removeIf(vehicle -> vehicle.tick(0.05, allowed.get(vehicle)));
		}
		assertTrue(vehicles.isEmpty(), "Overlapping merge arrivals must recover, not remain stopped forever");
	}

	@Test
	void movingCarsQueueBeforeTheMergeWithoutPilingUp() {
		final List<TrafficVehicle> vehicles = new ArrayList<>(List.of(
			approachingCar(3, 30.0, 0), approachingCar(2, 30.2, 120), approachingCar(1, 30.4, 240)));
		assertCarsClearTheNetworkWithoutOverlap(vehicles);
	}

	@Test
	void longCurvedApproachesYieldBeforeTheirPathsMeetAndMayTakeDifferentExits() {
		final TrafficRouteSegment straight = new TrafficRouteSegment("straight", 40, 36, 0, 64, 0, 40, 64, 0);
		final TrafficRouteSegment curved = new TrafficRouteSegment("curved", 41, 36, 0, 64, -6, 40, 64, 0,
			false, false, List.of(), List.of(new TrafficPathPoint(0, 64, -6), new TrafficPathPoint(20, 64, 0), new TrafficPathPoint(40, 64, 0)));
		final List<TrafficVehicle> vehicles = new ArrayList<>(List.of(
			car(1, straight, exit("east", 40, 64, 0, 60, 64, 0)),
			car(2, curved, exit("north", 40, 64, 0, 40, 64, 20))));
		assertCarsClearTheNetworkWithoutOverlap(vehicles);
	}

	@Test
	void crossingRailsWithDifferentEndpointsDoNotPileUp() {
		final List<TrafficVehicle> vehicles = new ArrayList<>(List.of(
			car(1, new TrafficRouteSegment("east", 40, 36, -20, 64, 0, 20, 64, 0), exit("east-exit", 20, 64, 0, 40, 64, 0)),
			car(2, new TrafficRouteSegment("north", 40, 36, 0, 64, -20, 0, 64, 20), exit("north-exit", 0, 64, 20, 0, 64, 40))));
		assertCarsClearTheNetworkWithoutOverlap(vehicles);
	}

	@Test
	void aCarAlreadyLeavingACrossingKeepsPriorityAfterChangingSegments() {
		final TrafficRouteSegment entry = new TrafficRouteSegment("entry", 20, 36, -20, 64, 0, 0, 64, 0);
		final TrafficRouteSegment departing = new TrafficRouteSegment("departing", 100, 36, 0, 64, 0, 100, 64, 0);
		final TrafficVehicle clearing = new TrafficVehicle(new UUID(0, 1), new TrafficVehicleDefinition("car", "car", 4.5, 36, 1, "car", null, null),
			new TrafficRoute(List.of(entry, departing, exit("departing-exit", 100, 64, 0, 120, 64, 0))), "spawn", "despawn", 1, 1, 0);
		final TrafficVehicle arriving = new TrafficVehicle(new UUID(0, 2), clearing.definition(),
			new TrafficRoute(List.of(new TrafficRouteSegment("crossing", 40, 36, 0, 64, -20, 0, 64, 20), exit("crossing-exit", 0, 64, 20, 0, 64, 40))),
			"spawn", "despawn", 16, 0);
		assertCarsClearTheNetworkWithoutOverlap(new ArrayList<>(List.of(clearing, arriving)));
	}

	@Test
	void threeOverlappingCrossingCarsCannotFormAClearingPriorityCycle() {
		final List<TrafficVehicle> vehicles = new ArrayList<>(List.of(
			crossingCar(1, 0, 0, 180, 9.8), crossingCar(2, -2.118, 1.548, 58, 13.6), crossingCar(3, 0.695, 3.47, -49, 9.5)));
		assertTrue(TrafficSpacingResolver.resolveAllowedSpeeds(vehicles).values().stream().anyMatch(speed -> speed > 0));
		for (int tick = 0; tick < 1000 && !vehicles.isEmpty(); tick++) {
			final var allowed = TrafficSpacingResolver.resolveAllowedSpeeds(vehicles);
			vehicles.removeIf(vehicle -> vehicle.tick(0.05, allowed.get(vehicle)));
		}
		assertTrue(vehicles.isEmpty(), "Clearing preference must not override a total order into a three-car yield cycle");
	}

	@Test
	@SuppressWarnings("unchecked")
	void routeLeadersTakePriorityOverTheirOwnWaitingFollowers() throws Exception {
		final TrafficRouteSegment previous = new TrafficRouteSegment("previous", 10, 36, -20, 64, 0, -10, 64, 0);
		final TrafficRouteSegment entry = new TrafficRouteSegment("entry", 10, 36, -10, 64, 0, 0, 64, 0);
		final TrafficRouteSegment shared = new TrafficRouteSegment("shared", 100, 36, 0, 64, 0, 100, 64, 0);
		final TrafficRouteSegment exit = exit("exit", 100, 64, 0, 120, 64, 0);
		final var definition = new TrafficVehicleDefinition("car", "car", 4.5, 36, 1, "car", null, null);
		final TrafficVehicle follower = new TrafficVehicle(new UUID(0, 1), definition,
			new TrafficRoute(List.of(previous, entry, shared, exit)), "spawn", "despawn", 1, 1, 0);
		final TrafficVehicle leader = new TrafficVehicle(new UUID(0, 2), definition,
			new TrafficRoute(List.of(shared, exit)), "spawn", "despawn", 6, 0);
		final List<TrafficVehicle> vehicles = List.of(follower, leader);
		final var indexMethod = TrafficSpacingResolver.class.getDeclaredMethod("buildVehiclesByDirectedSegment", java.util.Collection.class);
		final var obstaclesMethod = TrafficSpacingResolver.class.getDeclaredMethod("applyRouteLookaheadSpacing", java.util.Collection.class, Map.class, Map.class);
		final var orderMethod = TrafficSpacingResolver.class.getDeclaredMethod("buildRightOfWayOrder", java.util.Collection.class, Map.class);
		indexMethod.setAccessible(true);
		obstaclesMethod.setAccessible(true);
		orderMethod.setAccessible(true);
		final Object obstacles = obstaclesMethod.invoke(null, vehicles, indexMethod.invoke(null, vehicles), new java.util.HashMap<>(Map.of(follower, 36.0, leader, 36.0)));
		final Map<TrafficVehicle, Integer> order = (Map<TrafficVehicle, Integer>) orderMethod.invoke(null, vehicles, obstacles);
		assertTrue(order.get(leader) < order.get(follower), "Yielding back to a route follower would create a hard/soft blocking cycle");
	}

	private static TrafficVehicle crossingCar(long id, double x, double z, double yaw, double remaining) {
		final double dx = Math.cos(Math.toRadians(yaw));
		final double dz = Math.sin(Math.toRadians(yaw));
		final TrafficRouteSegment road = new TrafficRouteSegment("cross-" + id, 20 + remaining, 36,
			x - 20 * dx, 64, z - 20 * dz, x + remaining * dx, 64, z + remaining * dz);
		return new TrafficVehicle(new UUID(0, id), new TrafficVehicleDefinition("car", "car", 4.5, 36, 1, "car", null, null),
			new TrafficRoute(List.of(road, exit("exit-" + id, road.endX(), 64, road.endZ(), road.endX() + 20 * dx, 64, road.endZ() + 20 * dz))),
			"spawn", "despawn", 20, 0);
	}

	@Test
	void parallelLanesAndBridgesRemainUnblocked() {
		final TrafficVehicle first = car(1, new TrafficRouteSegment("first", 40, 36, 0, 64, 0, 40, 64, 0), exit("first-exit", 40, 64, 0, 60, 64, 0));
		final TrafficVehicle parallel = car(2, new TrafficRouteSegment("parallel", 40, 36, 0, 64, 3, 40, 64, 3), exit("parallel-exit", 40, 64, 3, 60, 64, 3));
		final TrafficVehicle bridge = car(3, new TrafficRouteSegment("bridge", 40, 36, 0, 68, -20, 0, 68, 20), exit("bridge-exit", 0, 68, 20, 0, 68, 40));
		assertTrue(TrafficSpacingResolver.resolveAllowedSpeeds(List.of(first, parallel, bridge)).values().stream().allMatch(speed -> speed == 36.0));
	}

	@Test
	void spacingQueriesRemainLocalWithTwentyThousandDistantVehicles() {
		final List<TrafficVehicle> vehicles = new ArrayList<>();
		for (int index = 0; index < 20_000; index++) {
			final double x = index * 128.0;
			vehicles.add(car(index + 1, new TrafficRouteSegment("road-" + index, 40, 36, x, 64, 1000, x + 40, 64, 1000),
				exit("exit-" + index, x + 40, 64, 1000, x + 60, 64, 1000)));
		}
		assertTimeout(Duration.ofSeconds(5), () -> {
			for (int tick = 0; tick < 5; tick++) {
				final var allowed = TrafficSpacingResolver.resolveAllowedSpeeds(vehicles);
				assertEquals(20_000, allowed.size());
				assertTrue(allowed.values().stream().allMatch(speed -> speed == 36.0));
			}
		});
	}

	private static void assertCarsClearTheNetworkWithoutOverlap(List<TrafficVehicle> vehicles) {
		for (int tick = 0; tick < 1000 && !vehicles.isEmpty(); tick++) {
			final var allowed = TrafficSpacingResolver.resolveAllowedSpeeds(vehicles);
			vehicles.removeIf(vehicle -> vehicle.tick(0.05, allowed.get(vehicle)));
			for (int first = 0; first < vehicles.size(); first++) {
				final TrafficMaterializationIndex occupied = new TrafficMaterializationIndex(List.of());
				// Remove the index's two-meter gap to compare the physical bodies only.
				occupied.add(vehicles.get(first).currentPosition(), vehicles.get(first).definition().lengthMeters() - 2.0);
				for (int second = first + 1; second < vehicles.size(); second++) {
					assertTrue(!occupied.isOccupied(vehicles.get(second).currentPosition(), vehicles.get(second).definition().lengthMeters() - 2.0),
						"Vehicles must yield before their bodies enter the common exit; tick=" + tick);
				}
			}
		}
		assertTrue(vehicles.isEmpty());
	}

	private static TrafficVehicle car(long id, TrafficRouteSegment approach, TrafficRouteSegment exit) {
		return new TrafficVehicle(new UUID(0, id), new TrafficVehicleDefinition("car", "car", 4.5, 36, 1, "car", null, null),
			new TrafficRoute(List.of(approach, exit)), "spawn", "despawn", 0, 0);
	}

	private static TrafficRouteSegment exit(String id, double x, double y, double z, double endX, double endY, double endZ) {
		return new TrafficRouteSegment(id, 20, 36, x, y, z, endX, endY, endZ, false, true);
	}

	private static TrafficVehicle approachingCar(long id, double distanceToMerge, double angle) {
		final double radians = Math.toRadians(angle);
		final TrafficRouteSegment approach = new TrafficRouteSegment("approach-" + id, distanceToMerge, 36,
			-distanceToMerge * Math.cos(radians), 64, -distanceToMerge * Math.sin(radians), 0, 64, 0);
		final TrafficRouteSegment exit = new TrafficRouteSegment("exit", 20, 36, 0, 64, 0, 20, 64, 0, false, true);
		return new TrafficVehicle(new UUID(0, id),
			new TrafficVehicleDefinition("car", "car", 4.5, 36, 1, "car", null, null),
			new TrafficRoute(List.of(approach, exit)), "spawn", "despawn", 0, 0);
	}
}
