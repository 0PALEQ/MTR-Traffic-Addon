package com.cookiecraftmods.mta.client.dashboard;

import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionNode;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionGroup;

import java.util.List;

public record ClientTrafficIntersectionEntry(
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
	int phaseDurationTicks,
	List<Integer> phaseOrder,
	List<TrafficIntersectionGroup> groups,
	List<TrafficIntersectionNode> nodes
) {
	public ClientTrafficIntersectionEntry {
		phaseOrder = phaseOrder == null ? List.of() : List.copyOf(phaseOrder);
		groups = groups == null ? List.of() : List.copyOf(groups);
		nodes = nodes == null ? List.of() : List.copyOf(nodes);
	}

	public long centerX() {
		return Math.round((minX + maxX) / 2.0D);
	}

	public long centerZ() {
		return Math.round((minZ + maxZ) / 2.0D);
	}

	public String effectiveName() {
		return name == null || name.isBlank() ? "Intersection @ " + centerX() + "," + centerZ() : name;
	}
}
