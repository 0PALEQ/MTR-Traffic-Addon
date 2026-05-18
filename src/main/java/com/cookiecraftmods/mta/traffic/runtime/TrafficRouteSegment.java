package com.cookiecraftmods.mta.traffic.runtime;

import java.util.List;

public record TrafficRouteSegment(
	String connectorId,
	double lengthMeters,
	double speedLimitKph,
	double startX,
	double startY,
	double startZ,
	double endX,
	double endY,
	double endZ,
	boolean spawnConnector,
	boolean despawnConnector,
	List<Long> signalColors,
	List<TrafficPathPoint> path
) {
	public TrafficRouteSegment {
		signalColors = signalColors == null ? List.of() : List.copyOf(signalColors);
		path = path == null || path.size() < 2 ? List.of(
			new TrafficPathPoint(startX, startY, startZ),
			new TrafficPathPoint(endX, endY, endZ)
		) : List.copyOf(path);
	}

	public TrafficRouteSegment(
		String connectorId,
		double lengthMeters,
		double speedLimitKph,
		double startX,
		double startY,
		double startZ,
		double endX,
		double endY,
		double endZ,
		boolean spawnConnector,
		boolean despawnConnector
	) {
		this(connectorId, lengthMeters, speedLimitKph, startX, startY, startZ, endX, endY, endZ, spawnConnector, despawnConnector, null, null);
	}

	public TrafficRouteSegment(
		String connectorId,
		double lengthMeters,
		double speedLimitKph,
		double startX,
		double startY,
		double startZ,
		double endX,
		double endY,
		double endZ
	) {
		this(connectorId, lengthMeters, speedLimitKph, startX, startY, startZ, endX, endY, endZ, false, false, null, null);
	}

	public String directedConnectorId() {
		return connectorId + "|" + startX + "," + startY + "," + startZ + "->" + endX + "," + endY + "," + endZ;
	}
}
