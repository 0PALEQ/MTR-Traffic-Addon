package com.cookiecraftmods.mta.traffic.network;

import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;
import com.cookiecraftmods.mta.traffic.runtime.TrafficVehiclePosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public final class SpatialVehicleIndex {
	private static final int GRID_SIZE_BLOCKS = 512;
	private final Map<Long, List<TrafficVehicle>> grid = new HashMap<>();
	private final double gridSizeBlocks;

	SpatialVehicleIndex(Collection<TrafficVehicle> vehicles, double gridSizeBlocks) {
		this.gridSizeBlocks = gridSizeBlocks;
		for (TrafficVehicle vehicle : vehicles) {
			final TrafficVehiclePosition position = vehicle.currentPosition();
			final long cellKey = cellKey(position.x(), position.z());
			grid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(vehicle);
		}
	}

	public List<TrafficVehicle> queryNearby(double x, double z, double maxDistanceSquared) {
		final List<TrafficVehicle> result = new ArrayList<>();
		final double maxDistance = Math.sqrt(maxDistanceSquared);
		final int radius = (int) Math.ceil(maxDistance / gridSizeBlocks);

		final long centerCellX = (long) (x / gridSizeBlocks);
		final long centerCellZ = (long) (z / gridSizeBlocks);

		for (long dx = -radius; dx <= radius; dx++) {
			for (long dz = -radius; dz <= radius; dz++) {
				final long cellKey = cellKey(centerCellX + dx, centerCellZ + dz);
				final List<TrafficVehicle> cellVehicles = grid.get(cellKey);
				if (cellVehicles != null) {
					for (TrafficVehicle vehicle : cellVehicles) {
						final TrafficVehiclePosition position = vehicle.currentPosition();
						final double dx2 = position.x() - x;
						final double dz2 = position.z() - z;
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
		return cellKey((long) (x / gridSizeBlocks), (long) (z / gridSizeBlocks));
	}

	private static long cellKey(long cellX, long cellZ) {
		return (cellX & 0xFFFFFFFFL) | ((cellZ & 0xFFFFFFFFL) << 32);
	}

	public static SpatialVehicleIndex build(Collection<TrafficVehicle> vehicles) {
		return new SpatialVehicleIndex(vehicles, GRID_SIZE_BLOCKS);
	}
}
