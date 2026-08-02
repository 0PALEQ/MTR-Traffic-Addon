package com.cookiecraftmods.mta.traffic.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpatialVehicleIndex {
	private static final int GRID_SIZE_BLOCKS = 512;
	private final Map<Long, List<TrafficNetworkVehicleSnapshot>> grid = new HashMap<>();
	private final double gridSizeBlocks;

	SpatialVehicleIndex(Collection<TrafficNetworkVehicleSnapshot> vehicles, double gridSizeBlocks) {
		this.gridSizeBlocks = gridSizeBlocks;
		for (TrafficNetworkVehicleSnapshot vehicle : vehicles) {
			final long cellKey = cellKey(vehicle.x(), vehicle.z());
			grid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(vehicle);
		}
	}

	public List<TrafficNetworkVehicleSnapshot> queryNearby(String dimensionId, double x, double z, double maxDistanceSquared) {
		final List<TrafficNetworkVehicleSnapshot> result = new ArrayList<>();
		final double maxDistance = Math.sqrt(maxDistanceSquared);
		final int radius = (int) Math.ceil(maxDistance / gridSizeBlocks);

		final long centerCellX = cellCoordinate(x);
		final long centerCellZ = cellCoordinate(z);

		for (long dx = -radius; dx <= radius; dx++) {
			for (long dz = -radius; dz <= radius; dz++) {
				final long cellKey = cellKey(centerCellX + dx, centerCellZ + dz);
				final List<TrafficNetworkVehicleSnapshot> cellVehicles = grid.get(cellKey);
				if (cellVehicles != null) {
					for (TrafficNetworkVehicleSnapshot vehicle : cellVehicles) {
						if (dimensionId != null && !dimensionId.equals(vehicle.dimensionId())) {
							continue;
						}
						final double dx2 = vehicle.x() - x;
						final double dz2 = vehicle.z() - z;
						if (dx2 * dx2 + dz2 * dz2 <= maxDistanceSquared) {
							result.add(vehicle);
						}
					}
				}
			}
		}

		return result;
	}

	private long cellKey(double x, double z) {
		return cellKey(cellCoordinate(x), cellCoordinate(z));
	}

	private long cellCoordinate(double coordinate) {
		return (long) Math.floor(coordinate / gridSizeBlocks);
	}

	private static long cellKey(long cellX, long cellZ) {
		return (cellX & 0xFFFFFFFFL) | ((cellZ & 0xFFFFFFFFL) << 32);
	}

	public static SpatialVehicleIndex build(Collection<TrafficNetworkVehicleSnapshot> vehicles) {
		return new SpatialVehicleIndex(vehicles, GRID_SIZE_BLOCKS);
	}
}
