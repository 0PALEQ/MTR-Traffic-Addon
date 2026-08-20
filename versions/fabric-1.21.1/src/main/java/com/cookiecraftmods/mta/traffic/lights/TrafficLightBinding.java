package com.cookiecraftmods.mta.traffic.lights;

public record TrafficLightBinding(
	String dimensionId,
	long x,
	long y,
	long z,
	String intersectionId,
	int nodeNumber,
	TrafficLightBindingTargetType targetType,
	int targetNumber
) {
	public TrafficLightBinding(String dimensionId, long x, long y, long z, String intersectionId, int nodeNumber) {
		this(dimensionId, x, y, z, intersectionId, nodeNumber, TrafficLightBindingTargetType.NODE, nodeNumber);
	}

	public TrafficLightBinding {
		targetType = targetType == null ? TrafficLightBindingTargetType.NODE : targetType;
		if (targetNumber <= 0) {
			targetNumber = nodeNumber;
		}
		if (nodeNumber <= 0 && targetType == TrafficLightBindingTargetType.NODE) {
			nodeNumber = targetNumber;
		}
	}
}
