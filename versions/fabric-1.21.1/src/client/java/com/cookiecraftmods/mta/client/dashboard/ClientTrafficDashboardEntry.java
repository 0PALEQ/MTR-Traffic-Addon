package com.cookiecraftmods.mta.client.dashboard;

import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import net.minecraft.core.BlockPos;

public record ClientTrafficDashboardEntry(
	String id,
	String name,
	TrafficPointType type,
	BlockPos blockPos,
	boolean enabled,
	int spawnIntervalTicks,
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

	public String effectiveName() {
		return name == null || name.isBlank() ? defaultName() : name;
	}

	public String defaultName() {
		return (type == TrafficPointType.SPAWN ? "Spawn" : "Despawn") + " @ " + blockPos.getX() + "," + blockPos.getZ();
	}
}
