package com.cookiecraftmods.mta.traffic.mtr.graph;

import com.cookiecraftmods.mta.traffic.runtime.TrafficVehicle;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public record MtrGraphTrafficSnapshot(
	Map<String, Integer> vehicleCountByConnector,
	Map<String, Double> occupiedMetersByConnector
) {
	private static final MtrGraphTrafficSnapshot EMPTY = new MtrGraphTrafficSnapshot(Map.of(), Map.of());

	public static MtrGraphTrafficSnapshot empty() {
		return EMPTY;
	}

	public static MtrGraphTrafficSnapshot fromVehicles(Collection<TrafficVehicle> vehicles) {
		if (vehicles.isEmpty()) {
			return empty();
		}

		final Map<String, Integer> vehicleCountByConnector = new HashMap<>();
		final Map<String, Double> occupiedMetersByConnector = new HashMap<>();

		for (TrafficVehicle vehicle : vehicles) {
			final String connectorId = vehicle.currentConnectorId().orElse(null);
			if (connectorId == null) {
				continue;
			}

			vehicleCountByConnector.merge(connectorId, 1, Integer::sum);
			occupiedMetersByConnector.merge(connectorId, Math.max(vehicle.definition().lengthMeters(), 0.0D), Double::sum);
		}

		return new MtrGraphTrafficSnapshot(Map.copyOf(vehicleCountByConnector), Map.copyOf(occupiedMetersByConnector));
	}

	public double edgeCostSeconds(MtrGraphEdge edge) {
		final double baseTravelTimeSeconds = edge.travelTimeSeconds();
		final int vehicleCount = vehicleCountByConnector.getOrDefault(edge.directedConnectorId(), 0);
		final double occupiedMeters = occupiedMetersByConnector.getOrDefault(edge.directedConnectorId(), 0.0D);
		final double occupancyRatio = occupiedMeters / Math.max(edge.lengthMeters(), 1.0D);
		final double congestionMultiplier = 1.0D + Math.min(4.0D, occupancyRatio * 1.5D + vehicleCount * 0.35D);
		return baseTravelTimeSeconds * congestionMultiplier;
	}
}
