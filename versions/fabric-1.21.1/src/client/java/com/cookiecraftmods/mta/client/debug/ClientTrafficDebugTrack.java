package com.cookiecraftmods.mta.client.debug;

final class ClientTrafficDebugTrack {
	private static final int MAX_SAMPLES = 32;
	private static final long MAX_INTERPOLATION_GAP_NANOS = 250_000_000L;
	private final Sample[] samples = new Sample[MAX_SAMPLES];
	private int head;
	private int size;

	private record Sample(ClientTrafficDebugSnapshot snapshot, long timeNanos) { }

	ClientTrafficDebugTrack(ClientTrafficDebugSnapshot snapshot, long simulationNanos) {
		samples[size++] = new Sample(snapshot, simulationNanos);
	}

	boolean hasSampleAt(long playbackNanos) {
		return sample(0).timeNanos() <= playbackNanos;
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

	ClientTrafficDebugRenderState interpolate(long playbackNanos) {
		while (size > 1 && sample(1).timeNanos() <= playbackNanos) {
			samples[head] = null;
			head = (head + 1) % MAX_SAMPLES;
			size--;
		}
		final Sample first = sample(0);
		final Sample next = size > 1 ? sample(1) : first;
		final Sample second = next.timeNanos() - first.timeNanos() <= MAX_INTERPOLATION_GAP_NANOS ? next : first;
		final double progress = second == first ? 0.0D : Math.max(0.0D, Math.min(1.0D,
			(double) (playbackNanos - first.timeNanos()) / (second.timeNanos() - first.timeNanos())));
		final ClientTrafficDebugSnapshot from = first.snapshot();
		final ClientTrafficDebugSnapshot to = second.snapshot();
		final double seconds = (second.timeNanos() - first.timeNanos()) / 1_000_000_000.0D;
		final double fromYaw = Math.toRadians(from.yawDegrees());
		final double toYaw = Math.toRadians(to.yawDegrees());
		final double fromPitch = Math.toRadians(from.pitchDegrees());
		final double toPitch = Math.toRadians(to.pitchDegrees());
		final double fromDistance = Math.max(0, from.speedKph()) / 3.6D * seconds;
		final double toDistance = Math.max(0, to.speedKph()) / 3.6D * seconds;
		return new ClientTrafficDebugRenderState(
			to.id(), to.visualId(), to.vehicleType(), to.lengthMeters(),
			smoothPosition(from.x(), to.x(), Math.cos(fromYaw) * Math.cos(fromPitch) * fromDistance, Math.cos(toYaw) * Math.cos(toPitch) * toDistance, progress),
			smoothPosition(from.y(), to.y(), Math.sin(fromPitch) * fromDistance, Math.sin(toPitch) * toDistance, progress),
			smoothPosition(from.z(), to.z(), Math.sin(fromYaw) * Math.cos(fromPitch) * fromDistance, Math.sin(toYaw) * Math.cos(toPitch) * toDistance, progress),
			from.yawDegrees() + angleDelta(from.yawDegrees(), to.yawDegrees()) * (float) progress,
			(float) lerp(from.pitchDegrees(), to.pitchDegrees(), progress),
			lerp(from.speedKph(), to.speedKph(), progress)
		);
	}

	// Cubic endpoint velocities smooth acceleration and turns. Limit tangents
	// per axis so inconsistent headings or a stop cannot overshoot either sample.
	private static double smoothPosition(double from, double to, double fromTangent, double toTangent, double t) {
		final double delta = to - from;
		if (Math.abs(delta) < 1.0E-9D) {
			return from;
		}
		double a = Math.max(0.0D, fromTangent / delta);
		double b = Math.max(0.0D, toTangent / delta);
		final double magnitude = Math.hypot(a, b);
		if (magnitude > 3.0D) {
			a *= 3.0D / magnitude;
			b *= 3.0D / magnitude;
		}
		final double t2 = t * t;
		final double t3 = t2 * t;
		return from + delta * ((-2 * t3 + 3 * t2)
			+ (t3 - 2 * t2 + t) * a + (t3 - t2) * b);
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
