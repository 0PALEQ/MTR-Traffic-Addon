package com.cookiecraftmods.mta.traffic.mtr.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record MtrGraph(
	Map<MtrNodeKey, List<MtrGraphEdge>> adjacency,
	List<MtrGraphEdge> edges,
	Map<String, List<MtrGraphEdge>> edgesByRailId
) {
	public MtrGraph(Map<MtrNodeKey, List<MtrGraphEdge>> adjacency, List<MtrGraphEdge> edges) {
		this(adjacency, edges, indexEdgesByRailId(edges));
	}

	public MtrGraph {
		final Map<MtrNodeKey, List<MtrGraphEdge>> immutableAdjacency = new LinkedHashMap<>();
		adjacency.forEach((node, nodeEdges) -> immutableAdjacency.put(node, List.copyOf(nodeEdges)));
		adjacency = Map.copyOf(immutableAdjacency);
		edges = List.copyOf(edges);
		edgesByRailId = Map.copyOf(edgesByRailId);
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
}
