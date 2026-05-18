package com.cookiecraftmods.mta.traffic.mtr.graph;

import java.util.List;
import java.util.Map;

public record MtrGraph(
	Map<MtrNodeKey, List<MtrGraphEdge>> adjacency,
	List<MtrGraphEdge> edges
) {
	public boolean isEmpty() {
		return edges.isEmpty();
	}
}
