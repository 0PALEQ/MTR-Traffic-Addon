package com.cookiecraftmods.mta.traffic.signal;

import org.mtr.core.data.Rail;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class SignalPathBlocker {
	public static final String STYLE = "mta_signal_path_blocker";
	public static final int RAIL_COLOR = 0xFFE53935;

	private SignalPathBlocker() {
	}

	public static boolean isBlocked(Rail rail) {
		return rail != null && rail.getStyles().contains(STYLE);
	}

	public static Rail copyWithBlockedState(Rail rail, boolean blocked) {
		final ObjectArrayList<String> styles = new ObjectArrayList<>();
		rail.getStyles().forEach(style -> {
			if (!STYLE.equals(style)) {
				styles.add(style);
			}
		});
		if (blocked) {
			styles.add(STYLE);
		}

		final Rail updatedRail = Rail.copy(rail, styles);
		updatedRail.copySignalColors(rail);
		return updatedRail;
	}
}
