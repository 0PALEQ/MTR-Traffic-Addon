package com.cookiecraftmods.mta.client.debug;

import java.util.List;
import java.util.UUID;

public final class ClientTrafficDebugTrackTest {
	private static final UUID ID = new UUID(0, 1);

	public static void main(String[] args) {
		ClientTrafficPlaybackRecoveryTest.main(args);
		final ClientTrafficDebugTrack track = new ClientTrafficDebugTrack(snapshot(0, 0, 36), 0);
		track.update(snapshot(10, 90, 36), 100_000_000L);
		final ClientTrafficDebugRenderState before = track.interpolate(50_000_000L);
		for (int i = 2; i < 10_000; i++) {
			track.update(snapshot(i % 3, -90, 36), i * 100_000_000L);
			if (i < 32) {
				final ClientTrafficDebugRenderState after = track.interpolate(50_000_000L);
				check(before.x(), after.x());
				check(before.yawDegrees(), after.yawDegrees());
			}
		}
		check(1, track.interpolate(999_850_000_000L).x());
		check(-90, track.interpolate(999_850_000_000L).yawDegrees());
		final ClientTrafficDebugTrack stop = new ClientTrafficDebugTrack(snapshot(0, 0, 100), 0);
		stop.update(snapshot(1, 0, 0), 100_000_000L);
		double previous = 0;
		for (long time = 0; time <= 200_000_000L; time += 1_000_000L) {
			final double position = stop.interpolate(time).x();
			if (position < previous || position > 1) {
				throw new AssertionError("Stop interpolation overshot or reversed");
			}
			previous = position;
		}
		check(1, previous);
		final ClientTrafficDebugTrack yaw = new ClientTrafficDebugTrack(snapshot(0, 179, 36), 0);
		yaw.update(snapshot(1, -179, 36), 100_000_000L);
		check(180, yaw.interpolate(50_000_000L).yawDegrees());
		checksDelayedCreationAndRemoval();
		checksClockContinuity();
		System.out.println("1.21 client movement, lifecycle, and overflow checks passed");
	}

	private static void checksDelayedCreationAndRemoval() {
		ClientTrafficDebugState.clear();
		ClientTrafficDebugState.replaceAt(1, List.of(snapshot(0, 0, 36)), 1_000_000_000L, 0);
		if (!ClientTrafficDebugState.allInterpolatedAt(0).isEmpty()) {
			throw new AssertionError("Spawn appeared ahead of playback");
		}
		ClientTrafficDebugState.replaceAt(2, List.of(snapshot(1, 0, 0)), 1_100_000_000L, 100_000_000L);
		check(0, ClientTrafficDebugState.allInterpolatedAt(200_000_000L).iterator().next().x());
		ClientTrafficDebugState.replaceAt(3, List.of(), 1_200_000_000L, 200_000_000L);
		ClientTrafficDebugState.replaceAt(4, List.of(snapshot(99, 0, 0)), 900_000_000L, 200_000_000L);
		check(1, ClientTrafficDebugState.allInterpolatedAt(300_000_000L).iterator().next().x());
		if (!ClientTrafficDebugState.allInterpolatedAt(400_000_000L).isEmpty()) {
			throw new AssertionError("Despawn did not follow playback");
		}
		ClientTrafficDebugState.clear();
		ClientTrafficDebugState.replaceAt(0, List.of(snapshot(7, 0, 0)), 0, 0);
		check(7, ClientTrafficDebugState.allInterpolatedAt(200_000_000L).iterator().next().x());
		ClientTrafficDebugState.clear();
	}

	private static void checksClockContinuity() {
		final ClientTrafficPlaybackClock clock = new ClientTrafficPlaybackClock();
		clock.observe(0, 0);
		for (long time = 0; time <= 1_000_000_000L; time += 10_000_000L) {
			clock.advance(time);
		}
		final long before = clock.advance(1_000_000_000L);
		clock.observe(1_000_000_000L, 1_000_000_000L);
		check(before, clock.advance(1_000_000_000L));
		final long after = clock.advance(1_010_000_000L);
		if (after < before || after - before > 11_500_000L) {
			throw new AssertionError("Clock snapped on recovery");
		}
	}

	private static ClientTrafficDebugSnapshot snapshot(double x, float yaw, double speed) {
		return new ClientTrafficDebugSnapshot(ID, "test", "car", 4, x, 0, 0, yaw, 0, speed);
	}

	private static void check(double expected, double actual) {
		if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.000001) {
			throw new AssertionError("Expected " + expected + ", got " + actual);
		}
	}
}
