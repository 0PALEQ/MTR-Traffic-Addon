package com.cookiecraftmods.mta.traffic;

import com.cookiecraftmods.mta.traffic.runtime.TrafficRouteSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Vehicle;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TrafficBlockerTest {
	private final PathData path = new PathData(null, 0, 0, 0, 0, 100,
		new Position(0, 64, 0), Angle.E, new Position(100, 64, 0), Angle.W);
	private Object previousTraffic;
	private Object previousTick;

	@BeforeEach
	void setup() throws Exception {
		previousTraffic = field("activeTrafficByConnector").get(null);
		previousTick = field("lastTrafficTickWallMillis").get(null);
		field("activeTrafficByConnector").set(null, Map.of());
		field("lastTrafficTickWallMillis").set(null, System.currentTimeMillis());
	}

	@AfterEach
	void restore() throws Exception {
		field("activeTrafficByConnector").set(null, previousTraffic);
		field("lastTrafficTickWallMillis").set(null, previousTick);
	}

	@Test
	void findsForwardAndReverseTrafficWithoutChangingStoppingMargins() throws Exception {
		addTraffic(false, 25);
		assertEquals(7.5D, blockedDistance(10, 100));
		addTraffic(true, 25);
		assertEquals(57.5D, blockedDistance(10, 100));
	}

	@Test
	void ignoresVehiclesBehindOrOutsideLookaheadAndFailsOpenWhenStale() throws Exception {
		assertEquals(-1.0D, blockedDistance(10, 100));
		addTraffic(false, 25);
		assertEquals(-1.0D, blockedDistance(50, 100));
		assertEquals(-1.0D, blockedDistance(0, 10));
		field("lastTrafficTickWallMillis").set(null, 0L);
		assertEquals(-1.0D, blockedDistance(10, 100));
	}

	@Test
	void vehicleMixinPreservesImmediateAndCloserMtrStops() throws Exception {
		addTraffic(false, 25);
		assertEquals(0.0D, applyVehicleBlocker(0.0D));
		assertEquals(3.0D, applyVehicleBlocker(3.0D));
		assertEquals(7.5D, applyVehicleBlocker(50.0D));
		assertEquals(7.5D, applyVehicleBlocker(-1.0D));
		field("activeTrafficByConnector").set(null, Map.of());
		assertEquals(50.0D, applyVehicleBlocker(50.0D));
		assertEquals(-1.0D, applyVehicleBlocker(-1.0D));
	}

	private double blockedDistance(double progress, double lookahead) {
		return TrafficManager.mtrVehicleBlockedDistance(List.of(path), 0, progress, lookahead, 4);
	}

	private void addTraffic(boolean reverse, double progress) throws Exception {
		final TrafficRouteSegment segment = new TrafficRouteSegment(path.getHexId(reverse), 100, 40,
			reverse ? 100 : 0, 64, 0, reverse ? 0 : 100, 64, 0);
		final var constructor = Class.forName(TrafficManager.class.getName() + "$IndexedTrafficVehicle")
			.getDeclaredConstructor(TrafficRouteSegment.class, String.class, double.class, double.class);
		constructor.setAccessible(true);
		final Object vehicle = constructor.newInstance(segment, path.getHexId(!reverse), progress, 4.0D);
		field("activeTrafficByConnector").set(null, Map.of(path.getHexId(reverse), List.of(vehicle)));
	}

	private double applyVehicleBlocker(double mtrDistance) throws Exception {
		final JsonObject data = new JsonObject();
		final JsonArray paths = new JsonArray();
		paths.add(Utilities.getJsonObjectFromData(path));
		data.add("path", paths);
		final Vehicle vehicle = new Vehicle(new JsonReader(data));
		// Invoke the handler after Fabric has applied it to the real MTR Vehicle class.
		final Method handler = Arrays.stream(Vehicle.class.getDeclaredMethods())
			.filter(method -> method.getName().contains("mta$includeTrafficVehicles"))
			.findFirst().orElseThrow();
		handler.setAccessible(true);
		final CallbackInfoReturnable<Double> result = new CallbackInfoReturnable<>("railBlockedDistance", true, mtrDistance);
		handler.invoke(vehicle, 0, 10.0D, 100.0D, null, false, false, result);
		return result.getReturnValueD();
	}

	private static Field field(String name) throws Exception {
		final Field field = TrafficManager.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
