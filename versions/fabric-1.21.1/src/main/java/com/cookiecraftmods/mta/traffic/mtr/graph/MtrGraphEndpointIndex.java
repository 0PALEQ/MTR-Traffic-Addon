package com.cookiecraftmods.mta.traffic.mtr.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MtrGraphEndpointIndex {
	private static final long CELL_SIZE = 128;
	private static final long MAX_QUERY_CELLS = 4096;
	private final List<MtrGraphEdge> edges;
	private final Map<Cell, List<MtrGraphEdge>> cells = new HashMap<>();
	private record Cell(long x, long z) { }

	public MtrGraphEndpointIndex(MtrGraph graph) {
		edges = graph.edges();
		for (MtrGraphEdge edge : edges) {
			final Cell from = cell(edge.from());
			final Cell to = cell(edge.to());
			cells.computeIfAbsent(from, ignored -> new ArrayList<>()).add(edge);
			if (!from.equals(to)) {
				cells.computeIfAbsent(to, ignored -> new ArrayList<>()).add(edge);
			}
		}
	}

	public Collection<MtrGraphEdge> query(long minX, long minZ, long maxX, long maxZ) {
		final long startX = Math.floorDiv(minX, CELL_SIZE);
		final long startZ = Math.floorDiv(minZ, CELL_SIZE);
		final long endX = Math.floorDiv(maxX, CELL_SIZE);
		final long endZ = Math.floorDiv(maxZ, CELL_SIZE);
		final long width = endX - startX + 1;
		final long height = endZ - startZ + 1;
		if (width <= 0 || height <= 0) {
			return List.of();
		}
		if (width > MAX_QUERY_CELLS || height > MAX_QUERY_CELLS || width * height > MAX_QUERY_CELLS) {
			return edges;
		}
		final Set<MtrGraphEdge> result = Collections.newSetFromMap(new IdentityHashMap<>());
		for (long x = startX; x <= endX; x++) {
			for (long z = startZ; z <= endZ; z++) {
				result.addAll(cells.getOrDefault(new Cell(x, z), List.of()));
			}
		}
		return result;
	}

	private static Cell cell(MtrNodeKey node) {
		return new Cell(Math.floorDiv(node.x(), CELL_SIZE), Math.floorDiv(node.z(), CELL_SIZE));
	}
}
