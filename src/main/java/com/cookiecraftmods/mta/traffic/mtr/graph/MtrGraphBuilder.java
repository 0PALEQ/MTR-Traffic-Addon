package com.cookiecraftmods.mta.traffic.mtr.graph;

import com.cookiecraftmods.mta.traffic.mtr.dto.MtrPosition;
import com.cookiecraftmods.mta.traffic.mtr.dto.MtrRail;
import com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MtrGraphBuilder {
	private static final double SAMPLE_SPACING_METERS = 1.0D;

	private MtrGraphBuilder() {
	}

	public static MtrGraph build(List<MtrRail> rails) {
		final Map<MtrNodeKey, List<MtrGraphEdge>> adjacency = new LinkedHashMap<>();
		final List<MtrGraphEdge> edges = new ArrayList<>();

		for (MtrRail rail : rails) {
			final MtrNodeKey position1 = new MtrNodeKey(rail.position1().x(), rail.position1().y(), rail.position1().z());
			final MtrNodeKey position2 = new MtrNodeKey(rail.position2().x(), rail.position2().y(), rail.position2().z());
			final RailPath railPath = createRailPath(rail);

			if (rail.speedLimit1() > 0) {
				addEdge(adjacency, edges, rail, position1, position2, railPath.lengthMeters(), rail.speedLimit1(), railPath.points());
			}
			if (rail.speedLimit2() > 0) {
				final List<TrafficPathPoint> reversedPath = new ArrayList<>(railPath.points());
				java.util.Collections.reverse(reversedPath);
				addEdge(adjacency, edges, rail, position2, position1, railPath.lengthMeters(), rail.speedLimit2(), reversedPath);
			}
		}

		return new MtrGraph(adjacency, edges);
	}

	private static void addEdge(Map<MtrNodeKey, List<MtrGraphEdge>> adjacency, List<MtrGraphEdge> edges, MtrRail rail, MtrNodeKey from, MtrNodeKey to, double lengthMeters, double speedLimitKph, List<TrafficPathPoint> path) {
		final MtrGraphEdge edge = new MtrGraphEdge(
			createRailId(rail),
			from,
			to,
			lengthMeters,
			speedLimitKph,
			rail.effectiveSignalColors(),
			path
		);
		edges.add(edge);
		adjacency.computeIfAbsent(from, ignored -> new ArrayList<>()).add(edge);
		adjacency.computeIfAbsent(to, ignored -> new ArrayList<>());
	}

	private static double measureDistance(MtrPosition a, MtrPosition b) {
		final double dx = a.x() - b.x();
		final double dy = a.y() - b.y();
		final double dz = a.z() - b.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static RailPath createRailPath(MtrRail rail) {
		try {
			final RailMath railMath = new RailMath(
				new Position(rail.position1().x(), rail.position1().y(), rail.position1().z()),
				Angle.valueOf(rail.angle1()),
				new Position(rail.position2().x(), rail.position2().y(), rail.position2().z()),
				Angle.valueOf(rail.angle2()),
				Rail.Shape.valueOf(rail.shape()),
				rail.verticalRadius()
			);
			final double length = Math.max(railMath.getLength(), measureDistance(rail.position1(), rail.position2()));
			final int samples = Math.max(2, (int) Math.ceil(length / SAMPLE_SPACING_METERS) + 1);
			final List<TrafficPathPoint> points = new ArrayList<>(samples);
			for (int i = 0; i < samples; i++) {
				final double distance = length * i / (samples - 1.0D);
				final Vector position = railMath.getPosition(distance, false);
				points.add(new TrafficPathPoint(position.x(), position.y(), position.z()));
			}
			return new RailPath(length, points);
		} catch (Exception ignored) {
			return new RailPath(
				measureDistance(rail.position1(), rail.position2()),
				List.of(
					new TrafficPathPoint(rail.position1().x(), rail.position1().y(), rail.position1().z()),
					new TrafficPathPoint(rail.position2().x(), rail.position2().y(), rail.position2().z())
				)
			);
		}
	}

	private static String createRailId(MtrRail rail) {
		return rail.position1().x() + "," + rail.position1().y() + "," + rail.position1().z()
			+ "->"
			+ rail.position2().x() + "," + rail.position2().y() + "," + rail.position2().z();
	}

	private record RailPath(double lengthMeters, List<TrafficPathPoint> points) {
	}
}
