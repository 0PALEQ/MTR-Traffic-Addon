package com.cookiecraftmods.mta.compat;

import net.fabricmc.loader.api.FabricLoader;

public final class MagicCompat {
	private static final String MAGIC_MOD_ID = "jme";
	private static final String MAGIC_REROUTE_MIXIN_CLASS = "org.justnoone.jme.mixin.VehicleAlternativePlatformRerouteMixin";
	private static final boolean MAGIC_LOADED = FabricLoader.getInstance().isModLoaded(MAGIC_MOD_ID);

	private MagicCompat() {
	}

	public static boolean isMagicLoaded() {
		return MAGIC_LOADED;
	}

	public static boolean shouldSuppressMtrTrafficBlockersForCurrentCall(double additionalDistance, boolean preReserve, boolean doNotReserve) {
		if (!MAGIC_LOADED || additionalDistance != 0.0D || !preReserve || doNotReserve) {
			return false;
		}

		for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
			if (MAGIC_REROUTE_MIXIN_CLASS.equals(stackTraceElement.getClassName())) {
				return true;
			}
		}
		return false;
	}
}
