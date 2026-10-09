package com.cookiecraftmods.mta.traffic;

import com.cookiecraftmods.mta.traffic.intersection.*;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightSignalState;
import com.cookiecraftmods.mta.traffic.runtime.*;
import com.cookiecraftmods.mta.traffic.vehicle.TrafficVehicleDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.tool.Angle;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrafficRealtimeTest {
    @AfterEach
    void reset() throws Exception {
        TrafficManager.clearAllVehicles();
        field(TrafficManager.class, "lastTrafficSimulationTick").setLong(null, 0);
        field(TrafficManager.class, "vehicleSimulationNanos").setLong(null, 0);
        field(TrafficManager.class, "lastServerTick").setLong(null, 0);
        field(TrafficManager.class, "lastTrafficSimulationWallMillis").setLong(null, 0);
        ((Map<?, ?>) field(TrafficManager.class, "MTR_VEHICLE_OCCUPANCY").get(null)).clear();
        ((Map<?, ?>) field(TrafficManager.class, "MTR_VEHICLE_PATH_STATES").get(null)).clear();
        ((Map<?, ?>) field(TrafficIntersectionRegistry.class, "DEFINITIONS").get(null)).clear();
        ((Map<?, ?>) field(TrafficIntersectionRegistry.class, "AUTO_SIGNAL_STATES").get(null)).clear();
        TrafficSignalClock.reset();
    }

    @Test
    void movesTheSameDistanceWithNormalSlowAndStalledServerTicks() throws Exception {
        for (int serverTicksPerSecond : new int[]{20, 5, 0}) {
            TrafficManager.clearAllVehicles();
            field(TrafficManager.class, "lastTrafficSimulationTick").setLong(null, 0);
            final TrafficVehicle car = car();
            activeVehicles().add(car);
            for (int tick = 1; tick <= 100; tick++) {
                field(TrafficManager.class, "lastServerTick").setLong(null, tick * serverTicksPerSecond / 20);
                TrafficManager.simulateUntil(tick);
            }
            assertEquals(50.0, car.distanceOnSegmentMeters(), 0.00001, "Server TPS: " + serverTicksPerSecond);
        }
    }

    @Test
    void snapshotTimeTracksCompletedStepsWhenWallClockTicksAreSkipped() throws Exception {
        field(TrafficManager.class, "vehicleSimulationNanos").setLong(null, 0);
        activeVehicles().add(car());
        TrafficManager.simulateUntil(20);
        assertEquals(1_000_000_000L, TrafficManager.getActiveNetworkFrame().simulationNanos());
        TrafficManager.simulateUntil(200);
        final TrafficManager.NetworkFrame frame = TrafficManager.getActiveNetworkFrame();
        assertEquals(2_000_000_000L, frame.simulationNanos());
        assertEquals(20.0D, frame.vehicles().get(0).x(), 0.00001);
        TrafficManager.simulateUntil(200);
        assertSame(frame, TrafficManager.getActiveNetworkFrame());
    }

    @Test
    void catchesUpSchedulingJitterWithoutOneLargeMovementStep() throws Exception {
        final TrafficVehicle car = car();
        activeVehicles().add(car);
        TrafficManager.simulateUntil(1);
        TrafficManager.simulateUntil(7);
        TrafficManager.simulateUntil(20);
        assertEquals(10.0, car.distanceOnSegmentMeters(), 0.00001);
        TrafficManager.simulateUntil(20);
        assertEquals(10.0, car.distanceOnSegmentMeters(), 0.00001);
        TrafficManager.simulateUntil(200);
        assertEquals(20.0, car.distanceOnSegmentMeters(), 0.00001, "Suspend recovery is bounded to one second");
    }

    @Test
    void catchUpKeepsSpacingBehindAStoppedCar() throws Exception {
        final TrafficVehicle following = car();
        final TrafficVehicle stopped = new TrafficVehicle(UUID.randomUUID(),
            new TrafficVehicleDefinition("stopped", "car", 4, 0, 1, "test", null, null),
            following.route(), "spawn", "despawn", 20, 0);
        activeVehicles().add(following);
        activeVehicles().add(stopped);
        for (int tick = 20; tick <= 200; tick += 20) {
            TrafficManager.simulateUntil(tick);
            assertTrue(stopped.distanceOnSegmentMeters() - following.distanceOnSegmentMeters() >= 6.0,
                "Keep vehicle lengths plus two meters of clearance during catch-up");
        }
    }

    @Test
    void clockAdvancesWithoutMinecraftTicksAndResetsForNewWorld() throws Exception {
        field(TrafficManager.class, "lastServerTick").setLong(null, 0);
        field(TrafficSignalClock.class, "epochNanos").setLong(null, System.nanoTime() - 2_000_000_000L);
        assertTrue(TrafficSignalClock.currentTick() >= 40);
        TrafficSignalClock.reset();
        assertTrue(TrafficSignalClock.currentTick() < 2);
    }

    @Test
    void delayedSimulationDoesNotDisableBusBlockers() throws Exception {
        field(TrafficManager.class, "lastTrafficSimulationWallMillis").setLong(null, System.currentTimeMillis() - 10_000);
        field(TrafficManager.class, "lastServerTick").setLong(null, 1000);
        assertTrue(TrafficManager.trafficTicksAreFreshForMtr());
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertsMtrMetersPerMillisecondToKilometersPerHour() throws Exception {
        final PathData path = new PathData(null, 0, 0, 0, 0, 100,
            new Position(0, 64, 0), Angle.E, new Position(100, 64, 0), Angle.W);
        TrafficManager.recordMtrVehicle(123, List.of(path), 20, 0.01, 12);
        final var method = TrafficManager.class.getDeclaredMethod("mtrSignalVehicles");
        method.setAccessible(true);
        final List<TrafficManager.MtrSignalVehicle> buses = (List<TrafficManager.MtrSignalVehicle>) method.invoke(null);
        assertEquals(36.0, buses.get(0).speedKph(), 0.00001);
    }

    @Test
    @SuppressWarnings("unchecked")
    void autoSignalsFailClosedAndYellowExpiresAfterSixtyRealtimeTicks() throws Exception {
        final TrafficIntersectionDefinition intersection = new TrafficIntersectionDefinition(
            "test", "test", "minecraft:overworld", 0, 60, 0, 20, 70, 20,
            true, false, TrafficIntersectionLevel.CROSSING, TrafficIntersectionSignalMode.AUTO,
            300, List.of(1, 2), List.of(),
            List.of(new TrafficIntersectionGroup("one", 300, List.of(1)),
                new TrafficIntersectionGroup("two", 300, List.of(2))),
            List.of(new TrafficIntersectionNode(0, 64, 10, TrafficIntersectionNodeType.IN, 1),
                new TrafficIntersectionNode(20, 64, 10, TrafficIntersectionNodeType.IN, 2)));
        ((Map<String, TrafficIntersectionDefinition>) field(TrafficIntersectionRegistry.class, "DEFINITIONS").get(null))
            .put("test", intersection);
        assertEquals(TrafficLightSignalState.RED, TrafficIntersectionRegistry.signalState("test", 1, 100).orElseThrow());
        final Class<?> stateClass = Class.forName(TrafficIntersectionRegistry.class.getName() + "$AutoSignalState");
        final var constructor = stateClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object state = constructor.newInstance();
        field(stateClass, "activeGroupIndex").setInt(state, 0);
        field(stateClass, "lastTick").setLong(state, 100);
        ((Map<String, Object>) field(TrafficIntersectionRegistry.class, "AUTO_SIGNAL_STATES").get(null)).put("test", state);
        assertEquals(TrafficLightSignalState.GREEN, TrafficIntersectionRegistry.signalState("test", 1, 100).orElseThrow());
        assertEquals(TrafficLightSignalState.RED, TrafficIntersectionRegistry.signalState("test", 1, 140).orElseThrow());
        final var beginYellow = TrafficIntersectionRegistry.class.getDeclaredMethod("beginAutoYellow", List.class, stateClass, long.class);
        beginYellow.setAccessible(true);
        beginYellow.invoke(null, intersection.groups(), state, 100L);
        field(stateClass, "lastTick").setLong(state, 159);
        assertEquals(TrafficLightSignalState.YELLOW, TrafficIntersectionRegistry.signalState("test", 1, 159).orElseThrow());
        field(stateClass, "lastTick").setLong(state, 160);
        assertEquals(TrafficLightSignalState.RED, TrafficIntersectionRegistry.signalState("test", 1, 160).orElseThrow());
    }

    private static TrafficVehicle car() {
        return new TrafficVehicle(UUID.randomUUID(),
            new TrafficVehicleDefinition("test", "car", 4, 36, 1, "test", null, null),
            new TrafficRoute(List.of(new TrafficRouteSegment("road", 1000, 36, 0, 64, 0, 1000, 64, 0))),
            "spawn", "despawn", 0, 36);
    }

    @SuppressWarnings("unchecked")
    private static List<TrafficVehicle> activeVehicles() throws Exception {
        return (List<TrafficVehicle>) field(TrafficManager.class, "ACTIVE_VEHICLES").get(null);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
