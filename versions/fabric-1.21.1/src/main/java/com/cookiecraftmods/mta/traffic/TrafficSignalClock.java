package com.cookiecraftmods.mta.traffic;

public final class TrafficSignalClock {
	public static final long TICK_MILLIS = 50L;
	private static long tick;

	private TrafficSignalClock() {
	}

	public static synchronized long currentTick() {
		return tick;
	}

	public static synchronized void syncToServerTick(long serverTick) {
		tick = Math.max(0L, serverTick);
	}

	public static synchronized void reset() {
		tick = 0L;
	}
}

