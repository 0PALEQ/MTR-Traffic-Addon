package com.cookiecraftmods.mta.traffic.mtr.graph;

import com.cookiecraftmods.mta.traffic.runtime.TrafficRoute;
import com.cookiecraftmods.mta.traffic.runtime.TrafficRouteSegment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

public final class MtrGraphPathFinder {
	private MtrGraphPathFinder() {
	}

	public static Optional<TrafficRoute> findRoute(MtrGraph graph, MtrNodeKey start, MtrNodeKey goal) {
		return findShortestRoute(graph, start, goal).map(MtrGraphRouteResult::route);
	}

	public static Optional<MtrGraphRouteResult> findWeightedRoute(MtrGraph graph, MtrNodeKey start, MtrNodeKey goal, MtrGraphTrafficSnapshot trafficSnapshot) {
		return findRoute(graph, start, goal, edge -> trafficSnapshot.edgeCostSeconds(edge));
	}

	public static Optional<MtrGraphRouteResult> findShortestRoute(MtrGraph graph, MtrNodeKey start, MtrNodeKey goal) {
		return findRoute(graph, start, goal, MtrGraphEdge::lengthMeters);
	}

	private static Optional<MtrGraphRouteResult> findRoute(MtrGraph graph, MtrNodeKey start, MtrNodeKey goal, EdgeCost edgeCost) {
		if (start.equals(goal)) {
			return Optional.empty();
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
				return Optional.of(reconstructRoute(previousEdgeByNode, start, goal, currentState.score().cost()));
			}

			for (MtrGraphEdge edge : graph.adjacency().getOrDefault(currentState.node(), List.of())) {
				final PathScore nextScore = new PathScore(currentState.score().cost() + edgeCost.cost(edge), currentState.score().hops() + 1);
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

	public static Optional<MtrNodeKey> findNearestNode(MtrGraph graph, MtrNodeKey target, int maxDistance) {
		MtrNodeKey bestNode = null;
		double bestDistance = Double.MAX_VALUE;

		for (MtrNodeKey node : graph.adjacency().keySet()) {
			final double distance = distance(node, target);
			if (distance <= maxDistance && distance < bestDistance) {
				bestDistance = distance;
				bestNode = node;
			}
		}

		return Optional.ofNullable(bestNode);
	}

	public static Optional<MtrGraphEdge> findEdge(MtrGraph graph, MtrNodeKey from, MtrNodeKey to) {
		return graph.adjacency().getOrDefault(from, List.of()).stream()
			.filter(edge -> edge.to().equals(to))
			.findFirst();
	}

	private static MtrGraphRouteResult reconstructRoute(Map<MtrNodeKey, MtrGraphEdge> previousEdgeByNode, MtrNodeKey start, MtrNodeKey goal, double totalCostSeconds) {
		final List<TrafficRouteSegment> reversedSegments = new ArrayList<>();
		MtrNodeKey current = goal;

		while (!current.equals(start)) {
			final MtrGraphEdge edge = previousEdgeByNode.get(current);
			if (edge == null) {
				break;
			}

			reversedSegments.add(new TrafficRouteSegment(
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
			));
			current = edge.from();
		}

		final List<TrafficRouteSegment> segments = new ArrayList<>(reversedSegments.size());
		for (int i = reversedSegments.size() - 1; i >= 0; i--) {
			segments.add(reversedSegments.get(i));
		}

		return new MtrGraphRouteResult(new TrafficRoute(segments), totalCostSeconds);
	}

	private static double distance(MtrNodeKey a, MtrNodeKey b) {
		final double dx = a.x() - b.x();
		final double dy = a.y() - b.y();
		final double dz = a.z() - b.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private record PathState(
		MtrNodeKey node,
		PathScore score
	) {
	}

	private record PathScore(
		double cost,
		int hops
	) implements Comparable<PathScore> {
		@Override
		public int compareTo(PathScore other) {
			final int costComparison = Double.compare(cost, other.cost);
			return costComparison == 0 ? Integer.compare(hops, other.hops) : costComparison;
		}
	}

	@FunctionalInterface
	private interface EdgeCost {
		double cost(MtrGraphEdge edge);
	}
}
