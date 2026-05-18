package com.cookiecraftmods.mta.traffic.mtr.dto;

import java.util.List;

public record MtrUpdateRail(
	MtrPosition position1,
	String angle1,
	MtrPosition position2,
	String angle2,
	String shape,
	double verticalRadius,
	List<String> styles,
	int speedLimit1,
	int speedLimit2,
	boolean isPlatform,
	boolean isSiding,
	boolean canAccelerate,
	boolean canTurnBack,
	boolean canConnectRemotely,
	boolean canHaveSignal,
	List<Integer> signalColors,
	String transportMode,
	boolean stylesMigratedLegacy
) {
}
