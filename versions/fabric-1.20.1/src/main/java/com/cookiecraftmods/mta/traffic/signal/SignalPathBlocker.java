package com.cookiecraftmods.mta.traffic.signal;

import org.mtr.core.data.Rail;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class SignalPathBlocker {
	public static final String MTR_STYLE = "mta_signal_path_blocker";
	public static final String MTA_STYLE = "mta_traffic_path_blocker";
	public static final int RAIL_COLOR = 0xFFE53935;

	private SignalPathBlocker() {
	}

	public static boolean isBlocked(Rail rail, String style) {
		return rail != null && rail.getStyles().contains(style);
	}

	public static Rail copyWithBlockedState(Rail rail, String style, boolean blocked) {
		final ObjectArrayList<String> styles = new ObjectArrayList<>();
		rail.getStyles().forEach(existingStyle -> {
			if (!style.equals(existingStyle)) {
				styles.add(existingStyle);
			}
		});
		if (blocked) {
			styles.add(style);
		}

		final Rail updatedRail = Rail.copy(rail, styles);
		updatedRail.copySignalColors(rail);
		return updatedRail;
	}
}
