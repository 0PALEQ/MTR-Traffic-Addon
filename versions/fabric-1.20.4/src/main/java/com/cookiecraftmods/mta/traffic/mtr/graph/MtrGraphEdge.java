
package com.cookiecraftmods.mta.traffic.mtr.graph;

import com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRouteSegment;

import java.util.List;

public record MtrGraphEdge(
	String railId,
	MtrNodeKey from,
	MtrNodeKey to,
	double lengthMeters,
	double speedLimitKph,
	boolean mtaPathBlocked,
	List<Long> signalColors,
	List<TrafficPathPoint> path
) {
	public MtrGraphEdge {
		signalColors = signalColors == null ? List.of() : List.copyOf(signalColors);
		path = path == null ? List.of() : List.copyOf(path);
	}

	public double travelTimeSeconds() {
		final double speedMetersPerSecond = Math.max(speedLimitKph, 1.0D) / 3.6D;
		return Math.max(lengthMeters, 0.0D) / speedMetersPerSecond;
	}

	public TrafficRouteSegment toRouteSegment() {
		return toRouteSegment(false, false);
	}

	public TrafficRouteSegment toRouteSegment(boolean spawnConnector, boolean despawnConnector) {
		return new TrafficRouteSegment(
			railId,
			lengthMeters,
			speedLimitKph,
			from.x(),
			from.y(),
			from.z(),
			to.x(),
			to.y(),
			to.z(),
			spawnConnector,
			despawnConnector,
			signalColors,
			path
		);
	}

	public String directedConnectorId() {
		return railId + "|" + from.x() + "," + from.y() + "," + from.z() + "->" + to.x() + "," + to.y() + "," + to.z();
	}
}

