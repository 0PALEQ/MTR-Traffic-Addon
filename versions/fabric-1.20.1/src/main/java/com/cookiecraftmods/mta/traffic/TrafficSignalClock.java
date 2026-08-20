package com.cookiecraftmods.mta.traffic;

public final class TrafficSignalClock {
	public static final long TICK_MILLIS = 50L;
	private static long tick;
	private static long wallMillis;

	private TrafficSignalClock() {
	}

	public static synchronized long currentTick() {
		final long nowMillis = System.currentTimeMillis();
		if (wallMillis <= 0L) {
			wallMillis = nowMillis;
			return tick;
		}

		final long elapsedTicks = Math.max(0L, (nowMillis - wallMillis) / TICK_MILLIS);
		if (elapsedTicks > 0L) {
			tick += elapsedTicks;
			wallMillis += elapsedTicks * TICK_MILLIS;
		}
		return tick;
	}

	public static synchronized void syncToServerTick(long serverTick) {
		currentTick();
		if (serverTick > tick) {
			tick = serverTick;
			wallMillis = System.currentTimeMillis();
		}
	}

	public static synchronized void reset() {
		tick = 0L;
		wallMillis = System.currentTimeMillis();
	}
}

