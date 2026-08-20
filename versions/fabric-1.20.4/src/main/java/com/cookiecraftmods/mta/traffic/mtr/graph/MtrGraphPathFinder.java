
package com.cookiecraftmods.mta.traffic.mtr.graph;

import com.cookiecraftmods.mta.traffic.runtime.TrafficRoute;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRouteSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

public final class MtrGraphPathFinder {
	private MtrGraphPathFinder() {
	}

	public static Optional<MtrGraphRouteResult> findFastestRoute(MtrGraph graph, MtrNodeKey start, MtrNodeKey goal) {
		if (start.equals(goal)) {
			return Optional.of(new MtrGraphRouteResult(new TrafficRoute(List.of()), 0.0D));
		}

		final PriorityQueue<PathState> openSet = new PriorityQueue<>(Comparator.comparing(PathState::score));
		final Map<MtrNodeKey, PathScore> bestScoreByNode = new HashMap<>();
		final Map<MtrNodeKey, MtrGraphEdge> previousEdgeByNode = new HashMap<>();

		openSet.add(new PathState(start, new PathScore(0.0D, 0)));
		bestScoreByNode.put(start, new PathScore(0.0D, 0));

		while (!openSet.isEmpty()) {
			final PathState currentState = openSet.poll();
			final PathScore bestKnownScore = bestScoreByNode.get(currentState.node());
			if (bestKnownScore != null && currentState.score().compareTo(bestKnownScore) > 0) {
				continue;
			}

			if (currentState.node().equals(goal)) {
				return Optional.of(reconstructRoute(previousEdgeByNode, start, goal, currentState.score().travelTimeSeconds()));
			}

			for (MtrGraphEdge edge : graph.adjacency().getOrDefault(currentState.node(), List.of())) {
				if (edge.mtaPathBlocked()) {
					continue;
				}
				final PathScore nextScore = new PathScore(currentState.score().travelTimeSeconds() + edge.travelTimeSeconds(), currentState.score().hops() + 1);
				final PathScore existingScore = bestScoreByNode.get(edge.to());
				if (existingScore == null || nextScore.compareTo(existingScore) < 0) {
					bestScoreByNode.put(edge.to(), nextScore);
					previousEdgeByNode.put(edge.to(), edge);
					openSet.add(new PathState(edge.to(), nextScore));
				}
			}
		}

		return Optional.empty();
	}

	private static MtrGraphRouteResult reconstructRoute(Map<MtrNodeKey, MtrGraphEdge> previousEdgeByNode, MtrNodeKey start, MtrNodeKey goal, double travelTimeSeconds) {
		final List<TrafficRouteSegment> reversedSegments = new ArrayList<>();
		MtrNodeKey current = goal;

		while (!current.equals(start)) {
			final MtrGraphEdge edge = previousEdgeByNode.get(current);
			if (edge == null) {
				throw new IllegalStateException("Missing route edge for graph node " + current);
			}

			reversedSegments.add(edge.toRouteSegment());
			current = edge.from();
		}

		Collections.reverse(reversedSegments);
		return new MtrGraphRouteResult(new TrafficRoute(reversedSegments), travelTimeSeconds);
	}

	private record PathState(
		MtrNodeKey node,
		PathScore score
	) {
	}

	private record PathScore(
		double travelTimeSeconds,
		int hops
	) implements Comparable<PathScore> {
		@Override
		public int compareTo(PathScore other) {
			final int timeComparison = Double.compare(travelTimeSeconds, other.travelTimeSeconds);
			return timeComparison == 0 ? Integer.compare(hops, other.hops) : timeComparison;
		}
	}
}

