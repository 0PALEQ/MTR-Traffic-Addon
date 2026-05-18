package com.cookiecraftmods.mta.client.dashboard;

import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import net.minecraft.core.BlockPos;

public record ClientTrafficDashboardEntry(
	String id,
	TrafficPointType type,
	BlockPos blockPos,
	int group,
	boolean enabled,
	int maxVehicles,
	int spawnIntervalTicks,
	int targetGroup,
	int activeVehicles,
	BlockPos connectorStartPos,
	BlockPos connectorEndPos,
	java.util.List<String> vehiclePool
) {
	public boolean hasConnectorRoute() {
		return connectorStartPos != null && connectorEndPos != null;
	}

	public java.util.List<String> effectiveVehiclePool() {
		return vehiclePool == null ? java.util.List.of() : java.util.List.copyOf(vehiclePool);
	}
}
