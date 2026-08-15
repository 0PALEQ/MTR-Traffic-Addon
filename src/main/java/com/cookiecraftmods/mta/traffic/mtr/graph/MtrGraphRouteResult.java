package com.cookiecraftmods.mta.traffic.mtr.graph;

import com.cookiecraftmods.mta.traffic.runtime.TrafficRoute;

public record MtrGraphRouteResult(
	TrafficRoute route,
	double totalCostSeconds
) {
}
