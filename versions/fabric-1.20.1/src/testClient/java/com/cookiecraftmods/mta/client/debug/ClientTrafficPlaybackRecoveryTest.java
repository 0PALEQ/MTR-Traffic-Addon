package com.cookiecraftmods.mta.client.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientTrafficPlaybackRecoveryTest {
	public static void main(String[] args) {
		final List<String> failures = new ArrayList<>();
		check(failures, "18-minute render pause", ClientTrafficPlaybackRecoveryTest::longRenderPause);
		check(failures, "one frame per second", ClientTrafficPlaybackRecoveryTest::lowFrameRate);
		check(failures, "new vehicles move after a render pause", ClientTrafficPlaybackRecoveryTest::spawnAfterPause);
		check(failures, "despawn history is bounded without rendering", ClientTrafficPlaybackRecoveryTest::retentionWithoutRendering);
		check(failures, "two hours of continuous playback", ClientTrafficPlaybackRecoveryTest::continuousPlayback);
		check(failures, "overflow preserves consecutive geometry", ClientTrafficPlaybackRecoveryTest::overflow);
		check(failures, "missing snapshots do not cut across a turn", ClientTrafficPlaybackRecoveryTest::missingSnapshots);
		if (!failures.isEmpty()) {
			throw new AssertionError(String.join("\n", failures));
		}
		System.out.println("Long-session playback recovery checks passed");
	}

	private static void longRenderPause() {
		final ClientTrafficPlaybackClock clock = new ClientTrafficPlaybackClock();
		clock.observe(0, 0);
		clock.advance(0);
		for (long time = 100_000_000L; time <= 1_080_000_000_000L; time += 100_000_000L) {
			clock.observe(time, time);
		}
		assertLive(1_080_000_000_000L, clock.advance(1_080_000_000_000L));
	}

	private static void lowFrameRate() {
		for (long frameInterval : new long[]{400_000_000L, 1_000_000_000L}) {
			final ClientTrafficPlaybackClock clock = new ClientTrafficPlaybackClock();
			clock.observe(0, 0);
			long previous = clock.advance(0);
			for (long time = 100_000_000L; time <= 60_000_000_000L; time += 100_000_000L) {
				clock.observe(time, time);
				if (time % frameInterval == 0) {
					final long current = clock.advance(time);
					assertLive(time, current);
					assertNear(200_000_000L, time - current);
					if (current < previous) {
						throw new AssertionError("Playback reversed");
					}
					previous = current;
				}
			}
		}
	}

	private static void spawnAfterPause() {
		ClientTrafficDebugState.clear();
		try {
			final UUID newId = new UUID(0, 2);
			ClientTrafficDebugState.replaceAt(0, List.of(snapshot(0, 0, 0)), 0, 0);
			ClientTrafficDebugState.allInterpolatedAt(0);
			for (int i = 1; i <= 10_800; i++) {
				final var car = snapshot(i, 0, 0);
				final var spawned = new ClientTrafficDebugSnapshot(newId, "test", "car", 4, i - 10_790, 0, 10, 0, 0, 36);
				ClientTrafficDebugState.replaceAt(i, i < 10_790 ? List.of(car) : List.of(car, spawned),
					i * 100_000_000L, i * 100_000_000L);
			}
			final var resumed = ClientTrafficDebugState.allInterpolatedAt(1_080_000_000_000L);
			assertNear(8, resumed.stream().filter(car -> car.id().equals(newId)).findFirst().orElseThrow().x());
			final var moved = ClientTrafficDebugState.allInterpolatedAt(1_080_100_000_000L);
			assertNear(9, moved.stream().filter(car -> car.id().equals(newId)).findFirst().orElseThrow().x());
		} finally {
			ClientTrafficDebugState.clear();
		}
	}

	private static void retentionWithoutRendering() {
		ClientTrafficDebugState.clear();
		try {
			for (int i = 0; i < 10_800; i++) {
				final var car = new ClientTrafficDebugSnapshot(new UUID(0, i), "test", "car", 4, i, 0, 0, 0, 0, 36);
				ClientTrafficDebugState.replaceAt(i, List.of(car), i * 100_000_000L, i * 100_000_000L);
			}
			final var field = ClientTrafficDebugState.class.getDeclaredField("TRACKS");
			field.setAccessible(true);
			final int retained = ((java.util.Map<?, ?>) field.get(null)).size();
			if (retained > 12) {
				throw new AssertionError("Retained " + retained + " departed vehicles without rendering");
			}
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		} finally {
			ClientTrafficDebugState.clear();
		}
	}

	private static void continuousPlayback() {
		final ClientTrafficPlaybackClock clock = new ClientTrafficPlaybackClock();
		final ClientTrafficDebugTrack track = new ClientTrafficDebugTrack(snapshot(0, 0, 0), 0);
		clock.observe(0, 0);
		clock.advance(0);
		for (int i = 1; i <= 72_000; i++) {
			final long time = i * 100_000_000L;
			clock.observe(time, time);
			track.update(snapshot(i, 0, 0), time);
			final long playback = clock.advance(time);
			assertNear(200_000_000L, time - playback);
			assertNear(Math.max(0, i - 2), track.interpolateAt(playback).x());
		}
	}

	private static void overflow() {
		final ClientTrafficDebugTrack track = new ClientTrafficDebugTrack(snapshot(0, 0, 0), 0);
		track.update(snapshot(1, 0, 0), 100_000_000L);
		for (int i = 2; i <= 100; i++) {
			track.update(snapshot(10, i - 2, 90), i * 100_000_000L);
		}
		final ClientTrafficDebugRenderState expired = track.interpolateAt(6_000_000_000L);
		assertNear(10, expired.x());
		assertNear(67, expired.z());
		final ClientTrafficDebugRenderState recent = track.interpolateAt(9_950_000_000L);
		assertNear(10, recent.x());
		assertNear(97.5, recent.z());
		assertNear(90, recent.yawDegrees());
	}

	private static void missingSnapshots() {
		final ClientTrafficDebugTrack track = new ClientTrafficDebugTrack(snapshot(0, 0, 0), 0);
		track.update(snapshot(10, 10, 90), 5_000_000_000L);
		final ClientTrafficDebugRenderState duringGap = track.interpolateAt(2_500_000_000L);
		assertNear(0, duringGap.x());
		assertNear(0, duringGap.z());
		assertNear(0, duringGap.yawDegrees());
		final ClientTrafficDebugRenderState recovered = track.interpolateAt(5_000_000_000L);
		assertNear(10, recovered.x());
		assertNear(10, recovered.z());
		assertNear(90, recovered.yawDegrees());
		track.update(snapshot(10, 11, 90), 5_100_000_000L);
		assertNear(10.5, track.interpolateAt(5_050_000_000L).z());
	}

	private static ClientTrafficDebugSnapshot snapshot(double x, double z, float yaw) {
		return new ClientTrafficDebugSnapshot(new UUID(0, 1), "test", "car", 4, x, 0, z, yaw, 0, 36);
	}

	private static void assertLive(long latest, long playback) {
		if (playback > latest || latest - playback > 1_000_000_000L) {
			throw new AssertionError("Playback is " + (latest - playback) / 1_000_000L + " ms behind live traffic");
		}
	}

	private static void assertNear(double expected, double actual) {
		if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.000001) {
			throw new AssertionError("Expected " + expected + ", got " + actual);
		}
	}

	private static void check(List<String> failures, String name, Runnable test) {
		try {
			test.run();
		} catch (AssertionError error) {
			failures.add(name + ": " + error.getMessage());
		}
	}
}
