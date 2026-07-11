package com.cookiecraftmods.mta.traffic.intersection;

import java.util.List;

public record TrafficIntersectionDefinition(
	String id,
	String name,
	String dimensionId,
	long minX,
	long minY,
	long minZ,
	long maxX,
	long maxY,
	long maxZ,
	Boolean enabled,
	Boolean autoDetectNodes,
	TrafficIntersectionLevel level,
	TrafficIntersectionSignalMode signalMode,
	Integer phaseDurationTicks,
	List<Integer> phaseOrder,
	List<Integer> trainNodeNumbers,
	List<TrafficIntersectionGroup> groups,
	List<TrafficIntersectionNode> nodes
) {
	public TrafficIntersectionDefinition {
		phaseOrder = phaseOrder == null ? List.of() : List.copyOf(phaseOrder);
		trainNodeNumbers = trainNodeNumbers == null ? List.of() : List.copyOf(trainNodeNumbers);
		groups = groups == null ? List.of() : List.copyOf(groups);
		nodes = nodes == null ? List.of() : List.copyOf(nodes);
	}

	public boolean isEnabled() {
		return enabled == null || enabled;
	}

	public String effectiveName() {
		return name == null || name.isBlank() ? "Intersection @ " + Math.round((minX + maxX) / 2.0D) + "," + Math.round((minZ + maxZ) / 2.0D) : name;
	}

	public int effectivePhaseDurationTicks() {
		return phaseDurationTicks == null || phaseDurationTicks <= 0 ? 300 : Math.max(300, phaseDurationTicks);
	}

	public boolean effectiveAutoDetectNodes() {
		return autoDetectNodes == null || autoDetectNodes;
	}

	public TrafficIntersectionLevel effectiveLevel() {
		return level == null ? TrafficIntersectionLevel.CROSSING : level;
	}

	public TrafficIntersectionSignalMode effectiveSignalMode() {
		return signalMode == null ? TrafficIntersectionSignalMode.MANUAL : signalMode;
	}

	public boolean contains(double x, double y, double z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}
}
