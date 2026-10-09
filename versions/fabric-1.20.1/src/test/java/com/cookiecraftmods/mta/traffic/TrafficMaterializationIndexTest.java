package com.cookiecraftmods.mta.traffic;

import com.cookiecraftmods.mta.traffic.runtime.TrafficMaterializationIndex;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRoute;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRouteSegment;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehiclePosition;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrafficMaterializationIndexTest {
	@Test
	void adjoiningRailsAndNewlyAcceptedVehiclesShareClearance() {
		final TrafficRouteSegment exit = new TrafficRouteSegment("exit", 100, 40, 10, 64, 0, 110, 64, 0);
		final TrafficVehicle car = car(exit, 1);
		final TrafficMaterializationIndex index = new TrafficMaterializationIndex(List.of(car));
		assertTrue(index.isOccupied(position(9, 64, 0, 0), 4.5), "A rail boundary cannot hide an overlapping car");
		assertFalse(index.isOccupied(position(-20, 64, 0, 0), 4.5));
		index.add(position(-20, 64, 0, 0), 4.5);
		assertTrue(index.isOccupied(position(-19, 64, 0, 0), 4.5), "Pending cars must reserve space immediately");
	}

	@Test
	void rotatedBodiesBlockCrossingRailsButNotParallelLanesOrBridges() {
		final TrafficMaterializationIndex index = new TrafficMaterializationIndex(List.of());
		index.add(position(0, 64, 4, 90), 12);
		assertTrue(index.isOccupied(position(0, 64, 0, 0), 4.5), "A bus body can cross the lane while its center is off it");
		assertFalse(index.isOccupied(position(0, 67, 0, 0), 4.5));
		final TrafficMaterializationIndex parallel = new TrafficMaterializationIndex(List.of());
		parallel.add(position(0, 64, 0, 0), 4.5);
		assertFalse(parallel.isOccupied(position(0, 64, 3, 0), 4.5));
	}

	@Test
	void indexesReversedRailsAndSharedEndpointsWithoutScanningOtherRoads() {
		final TrafficRouteSegment road = new TrafficRouteSegment("road", 10, 40, -20, 64, -5, -10, 64, -5);
		final TrafficRouteSegment reverse = new TrafficRouteSegment("reverse", 10, 40, -10, 64, -5, -20, 64, -5);
		final TrafficVehicle car = car(road, 5);
		final TrafficMaterializationIndex index = new TrafficMaterializationIndex(List.of(car));
		assertEquals(List.of(car), index.vehiclesOnSegment(reverse));
		assertEquals(List.of(car), index.vehiclesAtNode(-10, 64, -5));
		assertTrue(index.vehiclesAtNode(1000, 64, 1000).isEmpty());
		assertTrue(index.isOccupied(position(-16, 64, -5, 0), 4.5));
	}

	@Test
	void veryLongCustomVehiclesAndInvalidCandidatesCannotExplodeTheGrid() {
		assertTimeout(Duration.ofSeconds(1), () -> {
			final TrafficMaterializationIndex index = new TrafficMaterializationIndex(List.of());
			index.add(position(0, 64, 0, 0), 1_000_000);
			assertTrue(index.isOccupied(position(100_000, 64, 0, 0), 4.5));
			assertFalse(index.isOccupied(position(600_000, 64, 0, 0), 4.5));
			assertTrue(index.isOccupied(position(Double.NaN, 64, 0, 0), 4.5));
		});
	}

	@Test
	void clearanceQueriesStayLocalWithTwentyThousandDistantVehicles() {
		assertTimeout(Duration.ofSeconds(5), () -> {
			final TrafficMaterializationIndex index = new TrafficMaterializationIndex(List.of());
			for (int i = 0; i < 20_000; i++) {
				index.add(position(i * 32.0, 64, 1000, 0), 4.5);
			}
			for (int i = 0; i < 10_000; i++) {
				assertFalse(index.isOccupied(position(-10, 64, -10, 0), 4.5));
			}
		});
	}

	private static TrafficVehiclePosition position(double x, double y, double z, float yaw) {
		return new TrafficVehiclePosition(x, y, z, yaw, 0);
	}

	private static TrafficVehicle car(TrafficRouteSegment segment, double distance) {
		return new TrafficVehicle(new UUID(0, 5000),
			new TrafficVehicleDefinition("car", "car", 4.5, 40, 1, "car", null, null),
			new TrafficRoute(List.of(segment)), "spawn", "despawn", distance, 0);
	}
}