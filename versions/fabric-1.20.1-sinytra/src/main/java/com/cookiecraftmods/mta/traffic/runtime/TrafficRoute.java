package com.cookiecraftmods.mta.traffic.runtime;

import java.util.List;

public record TrafficRoute(List<TrafficRouteSegment> segments) {
	public TrafficRoute {
		segments = List.copyOf(segments);
	}

	public double totalLengthMeters() {
		return segments.stream().mapToDouble(TrafficRouteSegment::lengthMeters).sum();
	}
}
