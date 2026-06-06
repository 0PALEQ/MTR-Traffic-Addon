package com.cookiecraftmods.mta.client.debug;

final class ClientTrafficDebugTrack {
	private ClientTrafficDebugSnapshot previousSnapshot;
	private ClientTrafficDebugSnapshot currentSnapshot;
	private long updatedAtNanos;

	ClientTrafficDebugTrack(ClientTrafficDebugSnapshot snapshot, long updatedAtNanos) {
		this.previousSnapshot = snapshot;
		this.currentSnapshot = snapshot;
		this.updatedAtNanos = updatedAtNanos;
	}

	void update(ClientTrafficDebugSnapshot snapshot, long updatedAtNanos) {
		previousSnapshot = currentSnapshot;
		currentSnapshot = snapshot;
		this.updatedAtNanos = updatedAtNanos;
	}

	ClientTrafficDebugRenderState interpolate(long nowNanos, long interpolationWindowNanos) {
		final double progress = interpolationWindowNanos <= 0 ? 1.0D : Math.min(1.0D, Math.max(0.0D, (double) (nowNanos - updatedAtNanos) / interpolationWindowNanos));
		return new ClientTrafficDebugRenderState(
			currentSnapshot.id(),
			currentSnapshot.visualId(),
			currentSnapshot.vehicleType(),
			currentSnapshot.lengthMeters(),
			lerp(previousSnapshot.x(), currentSnapshot.x(), progress),
			lerp(previousSnapshot.y(), currentSnapshot.y(), progress),
			lerp(previousSnapshot.z(), currentSnapshot.z(), progress),
			lerpAngle(previousSnapshot.yawDegrees(), currentSnapshot.yawDegrees(), progress),
			(float) lerp(previousSnapshot.pitchDegrees(), currentSnapshot.pitchDegrees(), progress),
			lerp(previousSnapshot.speedKph(), currentSnapshot.speedKph(), progress)
		);
	}

	private static double lerp(double start, double end, double progress) {
		return start + (end - start) * progress;
	}

	private static float lerpAngle(float start, float end, double progress) {
		float delta = end - start;
		while (delta > 180.0F) {
			delta -= 360.0F;
		}
		while (delta < -180.0F) {
			delta += 360.0F;
		}
		return (float) (start + delta * progress);
	}
}
