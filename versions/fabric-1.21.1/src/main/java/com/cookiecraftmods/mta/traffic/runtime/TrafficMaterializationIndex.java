package com.cookiecraftmods.mta.traffic.runtime;

import com.cookiecraftmods.mta.traffic.spatial.SpatialGrid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Simulation-thread-local occupancy, updated as each virtual vehicle is accepted. */
public final class TrafficMaterializationIndex {
	private static final double CELL_SIZE_METERS = 16.0D;
	private static final double LONGITUDINAL_BUFFER_METERS = 2.0D;
	private static final double HALF_WIDTH_METERS = 0.75D;
	private static final double VERTICAL_CLEARANCE_METERS = 2.0D;
	private static final long MAX_FOOTPRINT_CELLS = 256L;
	private final SpatialGrid<Footprint> grid = new SpatialGrid<>(CELL_SIZE_METERS);
	private final Map<SegmentKey, List<TrafficVehicle>> vehiclesBySegment = new HashMap<>();
	private final Map<Node, List<TrafficVehicle>> vehiclesByNode = new HashMap<>();
	private final List<Footprint> footprints = new ArrayList<>();
	private final List<Footprint> oversizedFootprints = new ArrayList<>();
	private long queryId;

	public TrafficMaterializationIndex(Collection<TrafficVehicle> vehicles) {
		vehicles.forEach(this::add);
	}

	public void add(TrafficVehicle vehicle) {
		vehicle.currentSegment().ifPresent(segment -> {
			vehiclesBySegment.computeIfAbsent(SegmentKey.of(segment), ignored -> new ArrayList<>()).add(vehicle);
			final Node start = Node.start(segment);
			final Node end = Node.end(segment);
			vehiclesByNode.computeIfAbsent(start, ignored -> new ArrayList<>()).add(vehicle);
			if (!start.equals(end)) {
				vehiclesByNode.computeIfAbsent(end, ignored -> new ArrayList<>()).add(vehicle);
			}
		});
		add(vehicle.currentPosition(), vehicle.definition().lengthMeters());
	}

	public List<TrafficVehicle> vehiclesOnSegment(TrafficRouteSegment segment) {
		return vehiclesBySegment.getOrDefault(SegmentKey.of(segment), List.of());
	}

	public List<TrafficVehicle> vehiclesAtNode(double x, double y, double z) {
		return vehiclesByNode.getOrDefault(new Node(x, y, z), List.of());
	}

	public void add(TrafficVehiclePosition position, double lengthMeters) {
		final Footprint footprint = Footprint.of(position, lengthMeters);
		if (footprint == null) {
			return;
		}
		footprints.add(footprint);
		final long minX = grid.coordinate(footprint.minX);
		final long maxX = grid.coordinate(footprint.maxX);
		final long minZ = grid.coordinate(footprint.minZ);
		final long maxZ = grid.coordinate(footprint.maxZ);
		if (tooManyCells(minX, maxX, minZ, maxZ)) {
			// Very long custom vehicles cannot cause an unbounded grid allocation.
			oversizedFootprints.add(footprint);
			return;
		}
		for (long x = minX; x <= maxX; x++) {
			for (long z = minZ; z <= maxZ; z++) {
				grid.add(x * CELL_SIZE_METERS, z * CELL_SIZE_METERS, footprint);
			}
		}
	}

	public boolean isOccupied(TrafficVehiclePosition position, double lengthMeters) {
		final Footprint candidate = Footprint.of(position, lengthMeters);
		if (candidate == null) {
			return true;
		}
		final long minX = grid.coordinate(candidate.minX);
		final long maxX = grid.coordinate(candidate.maxX);
		final long minZ = grid.coordinate(candidate.minZ);
		final long maxZ = grid.coordinate(candidate.maxZ);
		if (tooManyCells(minX, maxX, minZ, maxZ)) {
			return footprints.stream().anyMatch(candidate::overlaps);
		}
		final long currentQuery = ++queryId;
		for (long x = minX; x <= maxX; x++) {
			for (long z = minZ; z <= maxZ; z++) {
				final List<Footprint> cell = grid.cell(x, z);
				if (cell == null) {
					continue;
				}
				for (Footprint footprint : cell) {
					if (footprint.lastQueryId == currentQuery) {
						continue;
					}
					footprint.lastQueryId = currentQuery;
					if (candidate.overlaps(footprint)) {
						return true;
					}
				}
			}
		}
		return oversizedFootprints.stream().anyMatch(candidate::overlaps);
	}

	private static boolean tooManyCells(long minX, long maxX, long minZ, long maxZ) {
		final long width = maxX - minX + 1L;
		final long height = maxZ - minZ + 1L;
		return width <= 0L || height <= 0L || width > MAX_FOOTPRINT_CELLS || height > MAX_FOOTPRINT_CELLS
			|| width * height > MAX_FOOTPRINT_CELLS;
	}

	private record Node(double x, double y, double z) implements Comparable<Node> {
		static Node start(TrafficRouteSegment segment) {
			return new Node(segment.startX(), segment.startY(), segment.startZ());
		}

		static Node end(TrafficRouteSegment segment) {
			return new Node(segment.endX(), segment.endY(), segment.endZ());
		}

		@Override
		public int compareTo(Node other) {
			final int xComparison = Double.compare(x, other.x);
			final int yComparison = Double.compare(y, other.y);
			return xComparison != 0 ? xComparison : yComparison != 0 ? yComparison : Double.compare(z, other.z);
		}
	}

	private record SegmentKey(Node first, Node second) {
		static SegmentKey of(TrafficRouteSegment segment) {
			final Node start = Node.start(segment);
			final Node end = Node.end(segment);
			return start.compareTo(end) <= 0 ? new SegmentKey(start, end) : new SegmentKey(end, start);
		}
	}

	private static final class Footprint {
		private final double x, y, z, forwardX, forwardZ, halfLength, minX, maxX, minZ, maxZ;
		private long lastQueryId;

		private Footprint(TrafficVehiclePosition position, double lengthMeters) {
			x = position.x();
			y = position.y();
			z = position.z();
			final double yaw = Math.toRadians(position.yawDegrees());
			forwardX = Math.cos(yaw);
			forwardZ = Math.sin(yaw);
			halfLength = Math.max(0.0D, lengthMeters) * 0.5D + LONGITUDINAL_BUFFER_METERS * 0.5D;
			final double extentX = Math.abs(forwardX) * halfLength + Math.abs(forwardZ) * HALF_WIDTH_METERS;
			final double extentZ = Math.abs(forwardZ) * halfLength + Math.abs(forwardX) * HALF_WIDTH_METERS;
			minX = x - extentX;
			maxX = x + extentX;
			minZ = z - extentZ;
			maxZ = z + extentZ;
		}

		static Footprint of(TrafficVehiclePosition position, double lengthMeters) {
			return position != null && Double.isFinite(position.x()) && Double.isFinite(position.y())
				&& Double.isFinite(position.z()) && Float.isFinite(position.yawDegrees()) && Double.isFinite(lengthMeters)
				? new Footprint(position, lengthMeters) : null;
		}

		boolean overlaps(Footprint other) {
			if (Math.abs(y - other.y) > VERTICAL_CLEARANCE_METERS || maxX <= other.minX || minX >= other.maxX
				|| maxZ <= other.minZ || minZ >= other.maxZ) {
				return false;
			}
			// Oriented rectangles detect adjoining/crossing rails without blocking parallel lanes.
			return !separated(other, forwardX, forwardZ) && !separated(other, -forwardZ, forwardX)
				&& !separated(other, other.forwardX, other.forwardZ) && !separated(other, -other.forwardZ, other.forwardX);
		}

		private boolean separated(Footprint other, double axisX, double axisZ) {
			final double distance = Math.abs((other.x - x) * axisX + (other.z - z) * axisZ);
			final double radius = projectedRadius(axisX, axisZ) + other.projectedRadius(axisX, axisZ);
			return distance >= radius;
		}

		private double projectedRadius(double axisX, double axisZ) {
			return Math.abs(axisX * forwardX + axisZ * forwardZ) * halfLength
				+ Math.abs(-axisX * forwardZ + axisZ * forwardX) * HALF_WIDTH_METERS;
		}
	}
}
