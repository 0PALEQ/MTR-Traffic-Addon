package com.cookiecraftmods.mta.client.dashboard;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class TrafficDashboardClient {
	private static final List<ClientTrafficDashboardEntry> ENTRIES = new ArrayList<>();
	private static final List<ClientTrafficIntersectionEntry> INTERSECTIONS = new ArrayList<>();

	private TrafficDashboardClient() {
	}

	public static void openOrUpdate(List<ClientTrafficDashboardEntry> entries, List<ClientTrafficIntersectionEntry> intersections) {
		ENTRIES.clear();
		ENTRIES.addAll(entries);
		INTERSECTIONS.clear();
		INTERSECTIONS.addAll(intersections);
		final Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof TrafficDashboardScreen trafficDashboardScreen) {
			trafficDashboardScreen.updateEntries(entries, intersections);
		} else {
			minecraft.setScreen(new TrafficDashboardScreen(entries, intersections));
		}
	}

	public static void clear() {
		ENTRIES.clear();
		INTERSECTIONS.clear();
	}
}
