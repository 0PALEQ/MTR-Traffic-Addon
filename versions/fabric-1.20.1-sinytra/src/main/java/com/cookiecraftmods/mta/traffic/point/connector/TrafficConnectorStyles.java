package com.cookiecraftmods.mta.traffic.point.connector;

import com.cookiecraftmods.mta.traffic.point.TrafficPointType;

public final class TrafficConnectorStyles {
	public static final String DEFAULT_STYLE = "default";
	public static final String SPAWN_STYLE = "mta_spawn_connector";
	public static final String DESPAWN_STYLE = "mta_despawn_connector";
	public static final int SPAWN_COLOR = 0xFF7BC67B;
	public static final int DESPAWN_COLOR = 0xFFE08A8A;

	private TrafficConnectorStyles() {
	}

	public static String styleFor(TrafficPointType pointType) {
		return pointType == TrafficPointType.SPAWN ? SPAWN_STYLE : DESPAWN_STYLE;
	}
}
