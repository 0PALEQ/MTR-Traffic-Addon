package com.cookiecraftmods.mta.traffic.mtr.dto;

import java.util.List;

public record MtrUpdateDataRequest(
	List<?> stations,
	List<?> platforms,
	List<?> sidings,
	List<?> routes,
	List<?> depots,
	List<?> lifts,
	List<MtrUpdateRail> rails,
	List<?> signalModifications
) {
	public static MtrUpdateDataRequest singleRail(MtrUpdateRail rail) {
		return new MtrUpdateDataRequest(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(rail), List.of());
	}
}
