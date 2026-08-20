package com.cookiecraftmods.mta.traffic.mtr.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record MtrGraph(
	Map<MtrNodeKey, List<MtrGraphEdge>> adjacency,
	List<MtrGraphEdge> edges,
	Map<String, List<MtrGraphEdge>> edgesByRailId,
	Map<MtrNodeKey, Integer> incidentEdgeCounts
) {
	public MtrGraph(Map<MtrNodeKey, List<MtrGraphEdge>> adjacency, List<MtrGraphEdge> edges) {
		this(adjacency, edges, indexEdgesByRailId(edges), indexIncidentEdgeCounts(edges));
	}

	public MtrGraph(Map<MtrNodeKey, List<MtrGraphEdge>> adjacency, List<MtrGraphEdge> edges, Map<String, List<MtrGraphEdge>> edgesByRailId) {
		this(adjacency, edges, edgesByRailId, indexIncidentEdgeCounts(edges));
	}

	public MtrGraph {
		final Map<MtrNodeKey, List<MtrGraphEdge>> immutableAdjacency = new LinkedHashMap<>();
		adjacency.forEach((node, nodeEdges) -> immutableAdjacency.put(node, List.copyOf(nodeEdges)));
		adjacency = Map.copyOf(immutableAdjacency);
		edges = List.copyOf(edges);
		final Map<String, List<MtrGraphEdge>> immutableRailIndex = new LinkedHashMap<>();
		edgesByRailId.forEach((railId, indexedEdges) -> immutableRailIndex.put(railId, List.copyOf(indexedEdges)));
		edgesByRailId = Map.copyOf(immutableRailIndex);
		incidentEdgeCounts = Map.copyOf(incidentEdgeCounts);
	}

	public boolean isEmpty() {
		return edges.isEmpty();
	}

	public Optional<MtrGraphEdge> findEdge(MtrNodeKey from, MtrNodeKey to) {
		return adjacency.getOrDefault(from, List.of()).stream()
			.filter(edge -> edge.to().equals(to))
			.findFirst();
	}

	private static Map<String, List<MtrGraphEdge>> indexEdgesByRailId(List<MtrGraphEdge> edges) {
		final Map<String, List<MtrGraphEdge>> mutableIndex = new LinkedHashMap<>();
		for (MtrGraphEdge edge : edges) {
			mutableIndex.computeIfAbsent(edge.railId(), ignored -> new ArrayList<>(2)).add(edge);
		}
		final Map<String, List<MtrGraphEdge>> immutableIndex = new LinkedHashMap<>();
		mutableIndex.forEach((railId, indexedEdges) -> immutableIndex.put(railId, List.copyOf(indexedEdges)));
		return immutableIndex;
	}

	private static Map<MtrNodeKey, Integer> indexIncidentEdgeCounts(List<MtrGraphEdge> edges) {
		final Map<MtrNodeKey, Integer> mutableIndex = new LinkedHashMap<>();
		for (MtrGraphEdge edge : edges) {
			mutableIndex.merge(edge.from(), 1, Integer::sum);
			if (!edge.to().equals(edge.from())) {
				mutableIndex.merge(edge.to(), 1, Integer::sum);
			}
		}
		return mutableIndex;
	}
}
