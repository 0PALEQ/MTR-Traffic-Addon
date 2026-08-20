package com.cookiecraftmods.mta.client.debug;

final class ClientTrafficDebugTrack {
	private static final long DEFAULT_CORRECTION_NANOS = 125_000_000L;
	private static final long MIN_CORRECTION_NANOS = 50_000_000L;
	private static final long MAX_CORRECTION_NANOS = 250_000_000L;
	private static final long MAX_EXTRAPOLATION_NANOS = 350_000_000L;
	private ClientTrafficDebugSnapshot currentSnapshot;
	private long updatedAtNanos;
	private long correctionWindowNanos = DEFAULT_CORRECTION_NANOS;
	private double correctionX;
	private double correctionY;
	private double correctionZ;
	private float correctionYaw;
	private float correctionPitch;
	private double correctionSpeedKph;

	ClientTrafficDebugTrack(ClientTrafficDebugSnapshot snapshot, long updatedAtNanos) {
		this.currentSnapshot = snapshot;
		this.updatedAtNanos = updatedAtNanos;
	}

	void update(ClientTrafficDebugSnapshot snapshot, long updatedAtNanos) {
		final ClientTrafficDebugRenderState renderedState = interpolate(updatedAtNanos);
		final long updateIntervalNanos = Math.max(1L, updatedAtNanos - this.updatedAtNanos);
		correctionWindowNanos = Math.max(MIN_CORRECTION_NANOS, Math.min(MAX_CORRECTION_NANOS, updateIntervalNanos / 2L));
		correctionX = renderedState.x() - snapshot.x();
		correctionY = renderedState.y() - snapshot.y();
		correctionZ = renderedState.z() - snapshot.z();
		correctionYaw = angleDelta(snapshot.yawDegrees(), renderedState.yawDegrees());
		correctionPitch = renderedState.pitchDegrees() - snapshot.pitchDegrees();
		correctionSpeedKph = renderedState.speedKph() - snapshot.speedKph();
		currentSnapshot = snapshot;
		this.updatedAtNanos = updatedAtNanos;
	}

	ClientTrafficDebugRenderState interpolate(long nowNanos) {
		final long elapsedNanos = Math.max(0L, nowNanos - updatedAtNanos);
		final double correctionFactor = 1.0D - Math.min(1.0D, (double) elapsedNanos / Math.max(1L, correctionWindowNanos));
		final double extrapolationSeconds = Math.min(MAX_EXTRAPOLATION_NANOS, elapsedNanos) / 1_000_000_000.0D;
		final float renderedYaw = currentSnapshot.yawDegrees() + correctionYaw * (float) correctionFactor;
		final float renderedPitch = currentSnapshot.pitchDegrees() + correctionPitch * (float) correctionFactor;
		final double renderedSpeedKph = Math.max(0.0D, currentSnapshot.speedKph() + correctionSpeedKph * correctionFactor);
		final double distanceMeters = renderedSpeedKph / 3.6D * extrapolationSeconds;
		final double yawRadians = Math.toRadians(renderedYaw);
		final double pitchRadians = Math.toRadians(renderedPitch);
		final double horizontalDistance = Math.cos(pitchRadians) * distanceMeters;
		final double x = currentSnapshot.x() + Math.cos(yawRadians) * horizontalDistance + correctionX * correctionFactor;
		final double y = currentSnapshot.y() + Math.sin(pitchRadians) * distanceMeters + correctionY * correctionFactor;
		final double z = currentSnapshot.z() + Math.sin(yawRadians) * horizontalDistance + correctionZ * correctionFactor;
		return new ClientTrafficDebugRenderState(
			currentSnapshot.id(),
			currentSnapshot.visualId(),
			currentSnapshot.vehicleType(),
			currentSnapshot.lengthMeters(),
			x,
			y,
			z,
			renderedYaw,
			renderedPitch,
			renderedSpeedKph
		);
	}

	private static float angleDelta(float start, float end) {
		float delta = end - start;
		while (delta > 180.0F) {
			delta -= 360.0F;
		}
		while (delta < -180.0F) {
			delta += 360.0F;
		}
		return delta;
	}
}
