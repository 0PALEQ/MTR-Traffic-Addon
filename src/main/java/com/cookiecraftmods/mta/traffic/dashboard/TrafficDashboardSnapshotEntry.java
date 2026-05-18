package com.cookiecraftmods.mta.traffic.dashboard;

import com.cookiecraftmods.mta.traffic.point.TrafficPointType;

public record TrafficDashboardSnapshotEntry(
	String id,
	TrafficPointType type,
	long x,
	long y,
	long z,
	int group,
	boolean enabled,
	int maxVehicles,
	int spawnIntervalTicks,
	int targetGroup,
	int activeVehicles,
	Long connectorStartX,
	Long connectorStartY,
	Long connectorStartZ,
	Long connectorEndX,
	Long connectorEndY,
	Long connectorEndZ,
	java.util.List<String> vehiclePool
) {
}
