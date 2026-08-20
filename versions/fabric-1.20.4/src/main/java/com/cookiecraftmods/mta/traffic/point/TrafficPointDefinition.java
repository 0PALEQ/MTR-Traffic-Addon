package com.cookiecraftmods.mta.traffic.point;

import java.util.List;

public record TrafficPointDefinition(
	String id,
	TrafficPointType type,
	long x,
	long y,
	long z,
	Boolean enabled,
	Integer spawnIntervalTicks,
	Long connectorStartX,
	Long connectorStartY,
	Long connectorStartZ,
	Long connectorEndX,
	Long connectorEndY,
	Long connectorEndZ,
	String name,
	List<String> vehiclePool
) {
	public boolean isEnabled() {
		return enabled == null || enabled;
	}

	public int effectiveSpawnIntervalTicks() {
		return spawnIntervalTicks == null ? 40 : spawnIntervalTicks;
	}

	public boolean hasConnectorRoute() {
		return connectorStartX != null && connectorStartY != null && connectorStartZ != null && connectorEndX != null && connectorEndY != null && connectorEndZ != null;
	}

	public List<String> effectiveVehiclePool() {
		return vehiclePool == null ? List.of() : List.copyOf(vehiclePool);
	}

	public String effectiveName() {
		return name == null || name.isBlank() ? defaultName() : name;
	}

	public String defaultName() {
		return (type == TrafficPointType.SPAWN ? "Spawn" : "Despawn") + " @ " + x + "," + z;
	}
}
