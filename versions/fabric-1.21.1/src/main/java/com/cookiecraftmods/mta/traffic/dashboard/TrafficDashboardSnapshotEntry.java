package com.cookiecraftmods.mta.traffic.dashboard;

import com.cookiecraftmods.mta.traffic.point.TrafficPointType;

import java.util.List;

public record TrafficDashboardSnapshotEntry(
	String id,
	String name,
	TrafficPointType type,
	long x,
	long y,
	long z,
	boolean enabled,
	int spawnIntervalTicks,
	int activeVehicles,
	Long connectorStartX,
	Long connectorStartY,
	Long connectorStartZ,
	Long connectorEndX,
	Long connectorEndY,
	Long connectorEndZ,
	List<String> vehiclePool
) {
}
