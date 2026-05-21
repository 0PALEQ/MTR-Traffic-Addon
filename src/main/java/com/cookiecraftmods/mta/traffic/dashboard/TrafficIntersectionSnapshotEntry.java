package com.cookiecraftmods.mta.traffic.dashboard;

import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionGroup;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionSignalMode;

import java.util.List;

public record TrafficIntersectionSnapshotEntry(
	String id,
	String name,
	long minX,
	long minY,
	long minZ,
	long maxX,
	long maxY,
	long maxZ,
	boolean enabled,
	boolean autoDetectNodes,
	TrafficIntersectionSignalMode signalMode,
	int phaseDurationTicks,
	List<Integer> phaseOrder,
	List<TrafficIntersectionGroup> groups,
	List<TrafficIntersectionNode> nodes
) {
	public TrafficIntersectionSnapshotEntry {
		phaseOrder = phaseOrder == null ? List.of() : List.copyOf(phaseOrder);
		groups = groups == null ? List.of() : List.copyOf(groups);
		nodes = nodes == null ? List.of() : List.copyOf(nodes);
	}
}
