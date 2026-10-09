package com.cookiecraftmods.mta.client.debug;

final class ClientTrafficDebugTrack {
	private static final long PLAYBACK_DELAY_NANOS = 200_000_000L;
	private static final int MAX_SAMPLES = 32;
	private static final long MAX_INTERPOLATION_GAP_NANOS = 250_000_000L;
	private long playbackNanos;
	private long lastRenderNanos;
	private final Sample[] samples = new Sample[MAX_SAMPLES];
	private int head;
	private int size;

	private record Sample(ClientTrafficDebugSnapshot snapshot, long timeNanos) { }

	ClientTrafficDebugTrack(ClientTrafficDebugSnapshot snapshot, long updatedAtNanos) {
		this(snapshot, updatedAtNanos, updatedAtNanos);
	}

	ClientTrafficDebugTrack(ClientTrafficDebugSnapshot snapshot, long receivedAtNanos, long simulationNanos) {
		playbackNanos = simulationNanos - PLAYBACK_DELAY_NANOS;
		lastRenderNanos = receivedAtNanos;
		samples[size++] = new Sample(snapshot, simulationNanos);
	}

	void update(ClientTrafficDebugSnapshot snapshot, long updatedAtNanos) {
		final Sample latest = sample(size - 1);
		if (updatedAtNanos <= latest.timeNanos()) {
			return;
		}
		if (size == MAX_SAMPLES) {
			samples[head] = null;
			head = (head + 1) % MAX_SAMPLES;
			size--;
		}
		samples[(head + size++) % MAX_SAMPLES] = new Sample(snapshot, updatedAtNanos);
	}

	ClientTrafficDebugRenderState interpolate(long nowNanos) {
		// Advance by elapsed render time, never by packet arrival time. Clamp on
		// underrun so delayed/catch-up snapshots resume without skipping ahead.
		playbackNanos = Math.min(sample(size - 1).timeNanos(),
			playbackNanos + Math.max(0L, nowNanos - lastRenderNanos));
		lastRenderNanos = Math.max(lastRenderNanos, nowNanos);
		return interpolateAt(playbackNanos);
	}

	ClientTrafficDebugRenderState interpolateAt(long simulationNanos) {
		while (size > 1 && sample(1).timeNanos() <= simulationNanos) {
			samples[head] = null;
			head = (head + 1) % MAX_SAMPLES;
			size--;
		}
		final Sample first = sample(0);
		final Sample next = size > 1 ? sample(1) : first;
		final Sample second = next.timeNanos() - first.timeNanos() <= MAX_INTERPOLATION_GAP_NANOS ? next : first;
		final double progress = second == first ? 0.0D : Math.max(0.0D, Math.min(1.0D,
			(double) (simulationNanos - first.timeNanos()) / (second.timeNanos() - first.timeNanos())));
		final ClientTrafficDebugSnapshot from = first.snapshot();
		final ClientTrafficDebugSnapshot to = second.snapshot();
		// Hold when the buffer runs dry instead of predicting past stops/corners
		// and subsequently pulling the vehicle backwards.
		return new ClientTrafficDebugRenderState(
			to.id(), to.visualId(), to.vehicleType(), to.lengthMeters(),
			lerp(from.x(), to.x(), progress),
			lerp(from.y(), to.y(), progress),
			lerp(from.z(), to.z(), progress),
			from.yawDegrees() + angleDelta(from.yawDegrees(), to.yawDegrees()) * (float) progress,
			(float) lerp(from.pitchDegrees(), to.pitchDegrees(), progress),
			lerp(from.speedKph(), to.speedKph(), progress)
		);
	}

	private Sample sample(int index) {
		return samples[(head + index) % MAX_SAMPLES];
	}

	private static double lerp(double from, double to, double progress) {
		return from + (to - from) * progress;
	}

	private static float angleDelta(float start, float end) {
		float delta = (end - start) % 360.0F;
		if (delta > 180.0F) {
			delta -= 360.0F;
		} else if (delta < -180.0F) {
			delta += 360.0F;
		}
		return delta;
	}
}
