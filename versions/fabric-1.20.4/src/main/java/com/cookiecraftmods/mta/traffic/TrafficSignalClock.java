package com.cookiecraftmods.mta.traffic;

public final class TrafficSignalClock {
	public static final long TICK_MILLIS = 50L;
	private static long epochNanos = System.nanoTime();

	private TrafficSignalClock() {
	}

	public static synchronized long currentTick() {
		return Math.max(0L, (System.nanoTime() - epochNanos) / (TICK_MILLIS * 1_000_000L));
	}

	public static synchronized void reset() {
		epochNanos = System.nanoTime();
	}
}
