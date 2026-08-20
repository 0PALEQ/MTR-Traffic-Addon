package com.cookiecraftmods.mta.traffic.mtr.dto;

import java.util.List;

public record MtrRail(
	MtrPosition position1,
	String angle1,
	MtrPosition position2,
	String angle2,
	String shape,
	double verticalRadius,
	int tiltPoints,
	double tiltAngleDegrees1,
	double tiltAngleDistance1a,
	double tiltAngleDegrees1a,
	double tiltAngleDegrees1b,
	double tiltAngleDistance1b,
	double tiltAngleDegreesMiddle,
	double tiltAngleDistance2b,
	double tiltAngleDegrees2b,
	double tiltAngleDegrees2a,
	double tiltAngleDistance2a,
	double tiltAngleDegrees2,
	int speedLimit1,
	int speedLimit2,
	boolean isPlatform,
	boolean isSiding,
	boolean canAccelerate,
	boolean canTurnBack,
	boolean canConnectRemotely,
	boolean canHaveSignal,
	List<Long> signalColors,
	String transportMode,
	boolean stylesMigratedLegacy
) {
	public List<Long> effectiveSignalColors() {
		return signalColors == null ? List.of() : List.copyOf(signalColors);
	}
}
