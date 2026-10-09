package com.cookiecraftmods.mta.client.debug;

import java.util.UUID;

public final class ClientTrafficDebugTrackTest {
	private static final UUID ID = new UUID(0, 1);

	public static void main(String[] args) {
		ClientTrafficPlaybackRecoveryTest.main(args);
		final ClientTrafficDebugTrack burst = new ClientTrafficDebugTrack(snapshot(0, 0), 0, 5_000_000_000L);
		burst.interpolate(1_000_000_000L);
		for (int i = 1; i <= 10; i++) {
			burst.update(snapshot(i, 0), 5_000_000_000L + i * 100_000_000L);
		}
		check(0, burst.interpolate(1_000_000_000L).x());
		for (int frame = 1; frame <= 100; frame++) {
			check(frame / 10.0, burst.interpolate(1_000_000_000L + frame * 10_000_000L).x());
		}
		burst.update(snapshot(99, 0), 6_000_000_000L);
		check(10, burst.interpolate(2_000_000_000L).x());
		final ClientTrafficDebugTrack overflow = new ClientTrafficDebugTrack(snapshot(0, 0), 0);
		for (int i = 1; i <= 100; i++) {
			overflow.update(snapshot(i, 0), i * 100_000_000L);
		}
		check(69, overflow.interpolate(200_000_000L).x());
		check(69, overflow.interpolate(210_000_000L).x());
		check(99.5, overflow.interpolateAt(9_950_000_000L).x());
		final ClientTrafficDebugTrack straight = new ClientTrafficDebugTrack(snapshot(0, 0), 0);
		for (int tick = 1; tick <= 20; tick++) {
			straight.update(snapshot(tick, 0), tick * 100_000_000L);
			if (tick >= 2) {
				for (int frame = 0; frame < 10; frame++) {
					check(tick - 2 + frame / 10.0, straight.interpolate(tick * 100_000_000L + frame * 10_000_000L).x());
				}
			}
		}
		final ClientTrafficDebugTrack turn = new ClientTrafficDebugTrack(snapshot(0, 179), 0);
		turn.update(snapshot(1, -179), 100_000_000L);
		check(180, turn.interpolate(250_000_000L).yawDegrees());
		check(0.5, turn.interpolate(250_000_000L).x());
		final ClientTrafficDebugTrack jitter = new ClientTrafficDebugTrack(snapshot(0, 0), 0);
		jitter.update(snapshot(1, 0), 100_000_000L);
		double before = jitter.interpolate(260_000_000L).x();
		jitter.update(snapshot(2, 0), 260_000_000L);
		check(before, jitter.interpolate(260_000_000L).x());
		check(2, jitter.interpolate(2_000_000_000L).x());
		jitter.update(snapshot(3, 0), 2_000_000_000L);
		double previous = 2;
		for (long time = 2_000_000_000L; time <= 2_300_000_000L; time += 10_000_000L) {
			double position = jitter.interpolate(time).x();
			if (!Double.isFinite(position) || position < previous || position > 3) {
				throw new AssertionError("Invalid recovery position: " + position);
			}
			previous = position;
		}
		System.out.println("Client movement regression checks passed");
		checksOverflowDuringAnActiveTurn();
		checksSharedClockRecovery();
		checksStaleFramesAndWorldChanges();
	}

	private static void checksOverflowDuringAnActiveTurn() {
		final ClientTrafficDebugTrack track = new ClientTrafficDebugTrack(snapshot(0, 0), 0);
		track.update(snapshot(10, 90), 100_000_000L);
		final ClientTrafficDebugRenderState before = track.interpolateAt(50_000_000L);
		for (int i = 2; i < 10_000; i++) {
			track.update(snapshot(i % 3, -90), i * 100_000_000L);
			if (i < 32) {
				final ClientTrafficDebugRenderState after = track.interpolateAt(50_000_000L);
				check(before.x(), after.x());
				check(before.yawDegrees(), after.yawDegrees());
			}
		}
		check(1, track.interpolateAt(999_850_000_000L).x());
		check(-90, track.interpolateAt(999_850_000_000L).yawDegrees());
	}

	private static void checksSharedClockRecovery() {
		final ClientTrafficPlaybackClock clock = new ClientTrafficPlaybackClock();
		clock.observe(5_000_000_000L, 0L);
		clock.observe(5_100_000_000L, 100_000_000L);
		for (long time = 0; time <= 1_000_000_000L; time += 10_000_000L) {
			clock.advance(time);
		}
		final long before = clock.advance(1_000_000_000L);
		clock.observe(6_000_000_000L, 1_000_000_000L);
		check(before, clock.advance(1_000_000_000L));
		long previous = before;
		long latest = 6_000_000_000L;
		for (long time = 1_010_000_000L; time <= 11_000_000_000L; time += 10_000_000L) {
			if (time % 100_000_000L == 0L) {
				latest = 5_000_000_000L + time;
				clock.observe(latest, time);
				check(previous, clock.advance(time - 10_000_000L));
			}
			final long current = clock.advance(time);
			if (current < previous || current - previous > 11_500_000L || current > latest) {
				throw new AssertionError("Playback jumped, reversed, or extrapolated");
			}
			previous = current;
		}
		if (latest - previous > 450_000_000L) {
			throw new AssertionError("Playback did not recover its buffer delay");
		}
		if (clock.observe(latest - 1, 11_000_000_000L)) {
			throw new AssertionError("Accepted a stale simulation sample");
		}
		clock.clear();
		clock.observe(0, 20_000_000_000L);
		check(-200_000_000L, clock.advance(20_000_000_000L));
	}

	private static void checksStaleFramesAndWorldChanges() {
		ClientTrafficDebugState.clear();
		ClientTrafficDebugState.replace(1, java.util.List.of(snapshot(0, 0)), 1_000_000_000L);
		ClientTrafficDebugState.replace(2, java.util.List.of(snapshot(100, 0)), 900_000_000L);
		check(0, ClientTrafficDebugState.allInterpolated().iterator().next().x());
		ClientTrafficDebugState.replace(0, java.util.List.of(), 2_000_000_000L);
		check(0, ClientTrafficDebugState.allInterpolated().iterator().next().x());
		ClientTrafficDebugState.clear();
		ClientTrafficDebugState.replace(0, java.util.List.of(snapshot(7, 0)), 0);
		check(7, ClientTrafficDebugState.allInterpolated().iterator().next().x());
		ClientTrafficDebugState.clear();
		System.out.println("Shared timeline, overflow, and stale-frame checks passed");
	}

	private static ClientTrafficDebugSnapshot snapshot(double x, float yaw) {
		return new ClientTrafficDebugSnapshot(ID, "test", "car", 4, x, 0, 0, yaw, 0, 36);
	}

	private static void check(double expected, double actual) {
		if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.000001) {
			throw new AssertionError("Expected " + expected + ", got " + actual);
		}
	}
}
