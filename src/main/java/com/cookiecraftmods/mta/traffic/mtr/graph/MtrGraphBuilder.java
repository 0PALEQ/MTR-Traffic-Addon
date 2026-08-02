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
	private static final int MAX_SAMPLES_PER_RAIL = 4097;

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
		return buildFromRailSnapshots(snapshotRails(rails));
	}

	public static List<RailSnapshot> snapshotRails(Collection<Rail> rails) {
		final List<RailSnapshot> snapshots = new ArrayList<>(rails.size());
		for (Rail rail : rails) {
			final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
			final Position position1 = accessor.mta$getPosition1();
			final Position position2 = accessor.mta$getPosition2();
			final List<Long> signalColors = new ArrayList<>();
			accessor.mta$getSignalColors().forEach((long signalColor) -> signalColors.add(signalColor));
			snapshots.add(new RailSnapshot(
				position1.getX(), position1.getY(), position1.getZ(), accessor.mta$getAngle1(),
				position2.getX(), position2.getY(), position2.getZ(), accessor.mta$getAngle2(),
				accessor.mta$getShape(), accessor.mta$getVerticalRadius(),
				accessor.mta$getSpeedLimit1(), accessor.mta$getSpeedLimit2(), signalColors
			));
		}
		return List.copyOf(snapshots);
	}

	public static MtrGraph buildFromRailSnapshots(Collection<RailSnapshot> rails) {
		final Map<MtrNodeKey, List<MtrGraphEdge>> adjacency = new LinkedHashMap<>();
		final List<MtrGraphEdge> edges = new ArrayList<>();

		for (RailSnapshot rail : rails) {
			final MtrNodeKey position1 = new MtrNodeKey(rail.x1(), rail.y1(), rail.z1());
			final MtrNodeKey position2 = new MtrNodeKey(rail.x2(), rail.y2(), rail.z2());
			final RailPath railPath = createRailPath(rail);

			if (rail.speedLimit1() > 0) {
				addEdge(adjacency, edges, position1, position2, railPath.lengthMeters(), rail.speedLimit1(), rail.signalColors(), railPath.points());
			}
			if (rail.speedLimit2() > 0) {
				final List<TrafficPathPoint> reversedPath = new ArrayList<>(railPath.points());
				java.util.Collections.reverse(reversedPath);
				addEdge(adjacency, edges, position2, position1, railPath.lengthMeters(), rail.speedLimit2(), rail.signalColors(), reversedPath);
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
			final double length = effectiveLength(railMath.getLength(), measureDistance(rail.position1(), rail.position2()));
			final int samples = sampleCount(length);
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

	private static RailPath createRailPath(RailSnapshot rail) {
		final Position position1 = new Position(rail.x1(), rail.y1(), rail.z1());
		final Position position2 = new Position(rail.x2(), rail.y2(), rail.z2());
		try {
			final RailMath railMath = new RailMath(
				position1,
				rail.angle1(),
				position2,
				rail.angle2(),
				rail.shape(),
				rail.verticalRadius()
			);
			final double length = effectiveLength(railMath.getLength(), measureDistance(position1, position2));
			final int samples = sampleCount(length);
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

	private static double effectiveLength(double railMathLength, double directDistance) {
		final double finiteDirectDistance = Double.isFinite(directDistance) && directDistance >= 0.0D ? directDistance : 0.0D;
		return Double.isFinite(railMathLength) && railMathLength >= 0.0D
			? Math.max(railMathLength, finiteDirectDistance)
			: finiteDirectDistance;
	}

	private static int sampleCount(double length) {
		final double requestedSamples = Math.ceil(length / SAMPLE_SPACING_METERS) + 1.0D;
		return (int) Math.max(2.0D, Math.min(MAX_SAMPLES_PER_RAIL, requestedSamples));
	}

	private static String createRailId(MtrNodeKey from, MtrNodeKey to) {
		return TwoPositionsBase.getHexIdRaw(
			new Position(from.x(), from.y(), from.z()),
			new Position(to.x(), to.y(), to.z())
		);
	}

	private record RailPath(double lengthMeters, List<TrafficPathPoint> points) {
	}

	public record RailSnapshot(
		long x1,
		long y1,
		long z1,
		Angle angle1,
		long x2,
		long y2,
		long z2,
		Angle angle2,
		Rail.Shape shape,
		double verticalRadius,
		long speedLimit1,
		long speedLimit2,
		List<Long> signalColors
	) {
		public RailSnapshot {
			signalColors = signalColors == null ? List.of() : List.copyOf(signalColors);
		}
	}
}
