package com.cookiecraftmods.mta.traffic.mtr.dto;

import java.util.List;

public record MtrDataRequest(
	String clientId,
	MtrPosition clientPosition,
	int requestRadius,
	List<Long> existingStationIds,
	List<Long> existingPlatformIds,
	List<Long> existingSidingIds,
	List<Long> existingSimplifiedRouteIds,
	List<Long> existingDepotIds,
	List<String> existingRailIds,
	List<Long> existingHomeIds,
	List<Long> existingLandmarkIds
) {
}
