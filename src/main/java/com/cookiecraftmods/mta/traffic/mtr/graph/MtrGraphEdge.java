package com.cookiecraftmods.mta.traffic.mtr.graph;

import com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint;

import java.util.Collections;
import java.util.List;

//This is broken af

public record MtrGraphEdge(
	String railId,
	MtrNodeKey from,
	MtrNodeKey to,
	double lengthMeters,
	double speedLimitKph,
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

	public String directedConnectorId() {
		return railId + "|" + from.x() + "," + from.y() + "," + from.z() + "->" + to.x() + "," + to.y() + "," + to.z();
	}

	public MtrGraphEdge reversed() {
		final java.util.ArrayList<TrafficPathPoint> reversedPath = new java.util.ArrayList<>(path);
		Collections.reverse(reversedPath);
		return new MtrGraphEdge(railId, to, from, lengthMeters, speedLimitKph, signalColors, reversedPath);
	}
}
