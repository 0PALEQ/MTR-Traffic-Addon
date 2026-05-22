package com.cookiecraftmods.mta.traffic.lights;

public record TrafficLightBinding(
	String dimensionId,
	long x,
	long y,
	long z,
	String intersectionId,
	int nodeNumber
) {
}
