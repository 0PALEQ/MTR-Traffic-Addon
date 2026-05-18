package com.cookiecraftmods.mta.traffic.intersection;

import java.util.List;

public record TrafficIntersectionGroup(
	String name,
	Integer greenDurationTicks,
	List<Integer> nodeNumbers
) {
	public TrafficIntersectionGroup {
		name = name == null || name.isBlank() ? "Group" : name;
		greenDurationTicks = greenDurationTicks == null || greenDurationTicks <= 0 ? 100 : greenDurationTicks;
		nodeNumbers = nodeNumbers == null ? List.of() : List.copyOf(nodeNumbers);
	}

	public int effectiveGreenDurationTicks() {
		return greenDurationTicks == null || greenDurationTicks <= 0 ? 100 : greenDurationTicks;
	}
}
