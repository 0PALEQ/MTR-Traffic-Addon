package com.cookiecraftmods.mta.client.debug;

final class ClientTrafficPlaybackClock {
	private static final long PLAYBACK_DELAY_NANOS = 200_000_000L;
	static final long MAX_PLAYBACK_LAG_NANOS = 1_000_000_000L;
	private long latestSampleNanos = Long.MIN_VALUE;
	private long playbackNanos;
	private long lastRenderNanos;
	private double playbackRate = 1.0D;

	boolean observe(long simulationNanos, long receivedAtNanos) {
		if (simulationNanos < latestSampleNanos) {
			return false;
		}
		if (latestSampleNanos == Long.MIN_VALUE) {
			playbackNanos = simulationNanos - PLAYBACK_DELAY_NANOS;
			lastRenderNanos = receivedAtNanos;
		}
		latestSampleNanos = simulationNanos;
		return true;
	}

	long advance(long nowNanos) {
		if (latestSampleNanos == Long.MIN_VALUE) {
			return 0L;
		}
		final long elapsed = Math.max(0L, nowNanos - lastRenderNanos);
		lastRenderNanos = Math.max(lastRenderNanos, nowNanos);
		final long bufferedNanos = latestSampleNanos - playbackNanos;
		if (bufferedNanos > MAX_PLAYBACK_LAG_NANOS) {
			playbackNanos = latestSampleNanos - PLAYBACK_DELAY_NANOS;
			playbackRate = 1.0D;
			return playbackNanos;
		}
		final long remainingBufferNanos = Math.max(0L, bufferedNanos - elapsed);
		final double targetRate = remainingBufferNanos > PLAYBACK_DELAY_NANOS * 2L ? 1.15D
			: bufferedNanos < PLAYBACK_DELAY_NANOS / 4L ? 0.9D : 1.0D;
		final double maxRateChange = elapsed / 1_000_000_000.0D * 0.5D;
		playbackRate += Math.max(-maxRateChange, Math.min(maxRateChange, targetRate - playbackRate));
		playbackNanos = Math.min(latestSampleNanos, playbackNanos + (long) (elapsed * playbackRate));
		return playbackNanos;
	}

	void clear() {
		latestSampleNanos = Long.MIN_VALUE;
		playbackNanos = 0L;
		lastRenderNanos = 0L;
		playbackRate = 1.0D;
	}
}
