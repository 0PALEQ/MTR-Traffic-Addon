package com.cookiecraftmods.mta.traffic.rail;

public record MtaExclusiveRailDefinition(
	String id,
	String dimensionId,
	long startX,
	long startY,
	long startZ,
	long endX,
	long endY,
	long endZ,
	String startAngle,
	String endAngle,
	int speedLimitKph
) {
}
