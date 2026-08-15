package com.cookiecraftmods.mta.traffic.mtr.graph;

import com.cookiecraftmods.mta.traffic.runtime.TrafficRoute;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRouteSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class MtrGraphRouteBuilder {
	private static final int MAX_ROUTE_SEGMENTS = 8;

	private MtrGraphRouteBuilder() {
	}

	public static Optional<TrafficRoute> createRandomRoute(MtrGraph graph) {
		if (graph.isEmpty()) {
			return Optional.empty();
		}

		final List<MtrGraphEdge> seeds = graph.edges();
		final MtrGraphEdge firstEdge = seeds.get(ThreadLocalRandom.current().nextInt(seeds.size()));
		final List<TrafficRouteSegment> routeSegments = new ArrayList<>();
		routeSegments.add(toSegment(firstEdge));

		MtrNodeKey currentNode = firstEdge.to();
		MtrNodeKey previousNode = firstEdge.from();

		for (int i = 1; i < MAX_ROUTE_SEGMENTS; i++) {
			final MtrNodeKey previousNodeSnapshot = previousNode;
			final List<MtrGraphEdge> candidates = graph.adjacency().getOrDefault(currentNode, List.of()).stream()
				.filter(edge -> !edge.to().equals(previousNodeSnapshot))
				.toList();

			if (candidates.isEmpty()) {
				break;
			}

			final MtrGraphEdge nextEdge = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
			routeSegments.add(toSegment(nextEdge));
			previousNode = currentNode;
			currentNode = nextEdge.to();
		}

		return routeSegments.size() >= 2 ? Optional.of(new TrafficRoute(routeSegments)) : Optional.empty();
	}

	private static TrafficRouteSegment toSegment(MtrGraphEdge edge) {
		return new TrafficRouteSegment(
			edge.railId(),
			edge.lengthMeters(),
			edge.speedLimitKph(),
			edge.from().x(),
			edge.from().y(),
			edge.from().z(),
			edge.to().x(),
			edge.to().y(),
			edge.to().z(),
			false,
			false,
			edge.signalColors(),
			edge.path()
		);
	}
}
