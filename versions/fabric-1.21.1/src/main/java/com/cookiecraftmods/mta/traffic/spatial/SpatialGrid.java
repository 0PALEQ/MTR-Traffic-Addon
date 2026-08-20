package com.cookiecraftmods.mta.traffic.spatial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpatialGrid<T> {
	private final double cellSize;
	private final Map<Long, List<T>> cells = new HashMap<>();

	public SpatialGrid(double cellSize) {
		if (!Double.isFinite(cellSize) || cellSize <= 0.0D) {
			throw new IllegalArgumentException("Cell size must be positive");
		}
		this.cellSize = cellSize;
	}

	public void add(double x, double z, T value) {
		cells.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(value);
	}

	public List<T> cell(long cellX, long cellZ) {
		return cells.get(key(cellX, cellZ));
	}

	public long coordinate(double value) {
		return (long) Math.floor(value / cellSize);
	}

	public int radius(double distance) {
		return (int) Math.ceil(distance / cellSize);
	}

	private long key(double x, double z) {
		return key(coordinate(x), coordinate(z));
	}

	private static long key(long cellX, long cellZ) {
		return (cellX & 0xFFFFFFFFL) | ((cellZ & 0xFFFFFFFFL) << 32);
	}
}

