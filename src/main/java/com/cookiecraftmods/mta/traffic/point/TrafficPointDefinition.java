package com.cookiecraftmods.mta.traffic.point;

public record TrafficPointDefinition(
	String id,
	TrafficPointType type,
	long x,
	long y,
	long z,
	int group,
	Boolean enabled,
	Integer maxVehicles,
	Integer spawnIntervalTicks,
	Integer targetGroup,
	Long connectorStartX,
	Long connectorStartY,
	Long connectorStartZ,
	Long connectorEndX,
	Long connectorEndY,
	Long connectorEndZ,
	String name,
	java.util.List<String> vehiclePool
) {
	public TrafficPointDefinition(
		String id,
		TrafficPointType type,
		long x,
		long y,
		long z,
		int group,
		Boolean enabled,
		Integer maxVehicles,
		Integer spawnIntervalTicks,
		Integer targetGroup,
		Long connectorStartX,
		Long connectorStartY,
		Long connectorStartZ,
		Long connectorEndX,
		Long connectorEndY,
		Long connectorEndZ,
		java.util.List<String> vehiclePool
	) {
		this(id, type, x, y, z, group, enabled, maxVehicles, spawnIntervalTicks, targetGroup, connectorStartX, connectorStartY, connectorStartZ, connectorEndX, connectorEndY, connectorEndZ, null, vehiclePool);
	}

	public TrafficPointDefinition(
		String id,
		TrafficPointType type,
		long x,
		long y,
		long z,
		int group,
		Boolean enabled,
		Integer maxVehicles,
		Integer spawnIntervalTicks,
		Integer targetGroup
	) {
		this(id, type, x, y, z, group, enabled, maxVehicles, spawnIntervalTicks, targetGroup, null, null, null, null, null, null, null, java.util.List.of());
	}

	public boolean isEnabled() {
		return enabled == null || enabled;
	}

	public int effectiveMaxVehicles() {
		return maxVehicles == null ? 1 : maxVehicles;
	}

	public int effectiveSpawnIntervalTicks() {
		return spawnIntervalTicks == null ? 40 : spawnIntervalTicks;
	}

	public int effectiveTargetGroup() {
		return targetGroup == null ? group : targetGroup;
	}

	public boolean hasConnectorRoute() {
		return connectorStartX != null && connectorStartY != null && connectorStartZ != null && connectorEndX != null && connectorEndY != null && connectorEndZ != null;
	}

	public java.util.List<String> effectiveVehiclePool() {
		return vehiclePool == null ? java.util.List.of() : java.util.List.copyOf(vehiclePool);
	}

	public String effectiveName() {
		return name == null || name.isBlank() ? defaultName() : name;
	}

	public String defaultName() {
		return (type == TrafficPointType.SPAWN ? "Spawn" : "Despawn") + " @ " + x + "," + z;
	}
}
