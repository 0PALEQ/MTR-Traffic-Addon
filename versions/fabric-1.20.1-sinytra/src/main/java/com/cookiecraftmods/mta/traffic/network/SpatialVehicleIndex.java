package com.cookiecraftmods.mta.traffic.network;

import com.cookiecraftmods.mta.traffic.spatial.SpatialGrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class SpatialVehicleIndex {
	private static final int GRID_SIZE_BLOCKS = 512;
	private final SpatialGrid<TrafficNetworkVehicleSnapshot> grid;

	SpatialVehicleIndex(Collection<TrafficNetworkVehicleSnapshot> vehicles, double gridSizeBlocks) {
		grid = new SpatialGrid<>(gridSizeBlocks);
		for (TrafficNetworkVehicleSnapshot vehicle : vehicles) {
			grid.add(vehicle.x(), vehicle.z(), vehicle);
		}
	}

	public List<TrafficNetworkVehicleSnapshot> queryNearby(String dimensionId, double x, double z, double maxDistanceSquared) {
		final List<TrafficNetworkVehicleSnapshot> result = new ArrayList<>();
		final int radius = grid.radius(Math.sqrt(maxDistanceSquared));
		final long centerCellX = grid.coordinate(x);
		final long centerCellZ = grid.coordinate(z);

		for (long dx = -radius; dx <= radius; dx++) {
			for (long dz = -radius; dz <= radius; dz++) {
				final List<TrafficNetworkVehicleSnapshot> cellVehicles = grid.cell(centerCellX + dx, centerCellZ + dz);
				if (cellVehicles == null) {
					continue;
				}
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

		return result;
	}

	public static SpatialVehicleIndex build(Collection<TrafficNetworkVehicleSnapshot> vehicles) {
		return new SpatialVehicleIndex(vehicles, GRID_SIZE_BLOCKS);
	}
}

