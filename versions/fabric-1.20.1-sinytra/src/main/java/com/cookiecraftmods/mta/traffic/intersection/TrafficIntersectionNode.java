package com.cookiecraftmods.mta.traffic.intersection;

public record TrafficIntersectionNode(
	long x,
	long y,
	long z,
	TrafficIntersectionNodeType type,
	int number
) {
}
