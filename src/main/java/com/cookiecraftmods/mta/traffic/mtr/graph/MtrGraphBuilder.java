package com.cookiecraftmods.mta.traffic.mtr.graph;

import com.cookiecraftmods.mta.mixin.RailSchemaAccessor;
import com.cookiecraftmods.mta.traffic.mtr.dto.MtrPosition;
import com.cookiecraftmods.mta.traffic.mtr.dto.MtrRail;
import com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.core.data.TwoPositionsBase;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;

import java.util.ArrayList;
import java.util.Collection;
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
				addEdge(adjacency, edges, position1, position2, railPath.lengthMeters(), rail.speedLimit1(), rail.effectiveSignalColors(), railPath.points());
			}
			if (rail.speedLimit2() > 0) {
				final List<TrafficPathPoint> reversedPath = new ArrayList<>(railPath.points());
				java.util.Collections.reverse(reversedPath);
				addEdge(adjacency, edges, position2, position1, railPath.lengthMeters(), rail.speedLimit2(), rail.effectiveSignalColors(), reversedPath);
			}
		}

		return new MtrGraph(adjacency, edges);
	}

	public static MtrGraph buildFromRails(Collection<Rail> rails) {
		final Map<MtrNodeKey, List<MtrGraphEdge>> adjacency = new LinkedHashMap<>();
		final List<MtrGraphEdge> edges = new ArrayList<>();

		for (Rail rail : rails) {
			final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
			final Position rawPosition1 = accessor.mta$getPosition1();
			final Position rawPosition2 = accessor.mta$getPosition2();
			final MtrNodeKey position1 = new MtrNodeKey(rawPosition1.getX(), rawPosition1.getY(), rawPosition1.getZ());
			final MtrNodeKey position2 = new MtrNodeKey(rawPosition2.getX(), rawPosition2.getY(), rawPosition2.getZ());
			final RailPath railPath = createRailPath(accessor);
			final List<Long> signalColors = new ArrayList<>();
			accessor.mta$getSignalColors().forEach((long signalColor) -> signalColors.add(signalColor));

			if (accessor.mta$getSpeedLimit1() > 0) {
				addEdge(adjacency, edges, position1, position2, railPath.lengthMeters(), accessor.mta$getSpeedLimit1(), signalColors, railPath.points());
			}
			if (accessor.mta$getSpeedLimit2() > 0) {
				final List<TrafficPathPoint> reversedPath = new ArrayList<>(railPath.points());
				java.util.Collections.reverse(reversedPath);
				addEdge(adjacency, edges, position2, position1, railPath.lengthMeters(), accessor.mta$getSpeedLimit2(), signalColors, reversedPath);
			}
		}

		return new MtrGraph(adjacency, edges);
	}

	private static void addEdge(Map<MtrNodeKey, List<MtrGraphEdge>> adjacency, List<MtrGraphEdge> edges, MtrNodeKey from, MtrNodeKey to, double lengthMeters, double speedLimitKph, List<Long> signalColors, List<TrafficPathPoint> path) {
		final MtrGraphEdge edge = new MtrGraphEdge(
			createRailId(from, to),
			from,
			to,
			lengthMeters,
			speedLimitKph,
			signalColors,
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

	private static RailPath createRailPath(RailSchemaAccessor accessor) {
		final Position position1 = accessor.mta$getPosition1();
		final Position position2 = accessor.mta$getPosition2();
		try {
			final RailMath railMath = new RailMath(
				position1,
				accessor.mta$getAngle1(),
				position2,
				accessor.mta$getAngle2(),
				accessor.mta$getShape(),
				accessor.mta$getVerticalRadius()
			);
			final double length = Math.max(railMath.getLength(), measureDistance(position1, position2));
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
				measureDistance(position1, position2),
				List.of(
					new TrafficPathPoint(position1.getX(), position1.getY(), position1.getZ()),
					new TrafficPathPoint(position2.getX(), position2.getY(), position2.getZ())
				)
			);
		}
	}

	private static double measureDistance(Position a, Position b) {
		final double dx = a.getX() - b.getX();
		final double dy = a.getY() - b.getY();
		final double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static String createRailId(MtrNodeKey from, MtrNodeKey to) {
		return TwoPositionsBase.getHexIdRaw(
			new Position(from.x(), from.y(), from.z()),
			new Position(to.x(), to.y(), to.z())
		);
	}

	private record RailPath(double lengthMeters, List<TrafficPathPoint> points) {
	}
}
