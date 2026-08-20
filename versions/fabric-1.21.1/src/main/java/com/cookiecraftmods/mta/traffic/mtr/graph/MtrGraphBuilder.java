package com.cookiecraftmods.mta.traffic.mtr.graph;

import com.cookiecraftmods.mta.mixin.RailSchemaAccessor;
import com.cookiecraftmods.mta.traffic.mtr.dto.MtrRail;
import com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint;
import com.cookiecraftmods.mta.traffic.signal.SignalPathBlocker;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.core.data.TwoPositionsBase;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MtrGraphBuilder {
	private static final double SAMPLE_SPACING_METERS = 1.0D;
	private static final int MAX_SAMPLES_PER_RAIL = 4097;

	private MtrGraphBuilder() {
	}

	public static MtrGraph build(List<MtrRail> rails) {
		final List<RailSnapshot> snapshots = new ArrayList<>(rails.size());
		for (MtrRail rail : rails) {
			snapshots.add(snapshotRail(rail));
		}
		return buildFromRailSnapshots(snapshots);
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
				position1.getX(), position1.getY(), position1.getZ(), accessor.mta$getAngle1().name(),
				position2.getX(), position2.getY(), position2.getZ(), accessor.mta$getAngle2().name(),
				accessor.mta$getShape().name(), accessor.mta$getVerticalRadius(),
				rail.getTiltPoints(), rail.getTiltAngleDegrees1(), rail.getTiltAngleDistance1a(),
				rail.getTiltAngleDegrees1a(), rail.getTiltAngleDegrees1b(), rail.getTiltAngleDistance1b(),
				rail.getTiltAngleDegreesMiddle(), rail.getTiltAngleDistance2b(), rail.getTiltAngleDegrees2b(),
				rail.getTiltAngleDegrees2a(), rail.getTiltAngleDistance2a(), rail.getTiltAngleDegrees2(),
				accessor.mta$getSpeedLimit1(), accessor.mta$getSpeedLimit2(),
				SignalPathBlocker.isBlocked(rail, SignalPathBlocker.MTA_STYLE), signalColors
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
				addEdge(adjacency, edges, position1, position2, railPath.lengthMeters(), rail.speedLimit1(), rail.mtaPathBlocked(), rail.signalColors(), railPath.points());
			}
			if (rail.speedLimit2() > 0) {
				final List<TrafficPathPoint> reversedPath = new ArrayList<>(railPath.points());
				Collections.reverse(reversedPath);
				addEdge(adjacency, edges, position2, position1, railPath.lengthMeters(), rail.speedLimit2(), rail.mtaPathBlocked(), rail.signalColors(), reversedPath);
			}
		}

		return new MtrGraph(adjacency, edges);
	}

	private static void addEdge(Map<MtrNodeKey, List<MtrGraphEdge>> adjacency, List<MtrGraphEdge> edges, MtrNodeKey from, MtrNodeKey to, double lengthMeters, double speedLimitKph, boolean mtaPathBlocked, List<Long> signalColors, List<TrafficPathPoint> path) {
		final MtrGraphEdge edge = new MtrGraphEdge(
			createRailId(from, to),
			from,
			to,
			lengthMeters,
			speedLimitKph,
			mtaPathBlocked,
			signalColors,
			path
		);
		edges.add(edge);
		adjacency.computeIfAbsent(from, ignored -> new ArrayList<>()).add(edge);
		adjacency.computeIfAbsent(to, ignored -> new ArrayList<>());
	}

	private static RailPath createRailPath(RailSnapshot rail) {
		final Position position1 = new Position(rail.x1(), rail.y1(), rail.z1());
		final Position position2 = new Position(rail.x2(), rail.y2(), rail.z2());
		try {
			final RailMath railMath = new RailMath(
				position1,
				Angle.valueOf(rail.angle1()),
				position2,
				Angle.valueOf(rail.angle2()),
				Rail.Shape.valueOf(rail.shape()),
				rail.verticalRadius(),
				rail.tiltPoints(),
				rail.tiltAngleDegrees1(),
				rail.tiltAngleDistance1a(),
				rail.tiltAngleDegrees1a(),
				rail.tiltAngleDegrees1b(),
				rail.tiltAngleDistance1b(),
				rail.tiltAngleDegreesMiddle(),
				rail.tiltAngleDistance2b(),
				rail.tiltAngleDegrees2b(),
				rail.tiltAngleDegrees2a(),
				rail.tiltAngleDistance2a(),
				rail.tiltAngleDegrees2()
			);
			// Rail canonicalizes endpoints before constructing its RailMath instance,
			// but RailMath itself preserves the order supplied here. These snapshots
			// contain the original schema order, so sampling with reverse=false already
			// produces position1 -> position2 geometry.
			final double railMathLength = railMath.getLength();
			final double length = effectiveLength(railMathLength, measureDistance(position1, position2));
			return new RailPath(length, sampleRailPath(railMath, railMathLength, length, position1, position2));
		} catch (IllegalArgumentException ignored) {
			final double directDistance = effectiveLength(Double.NaN, measureDistance(position1, position2));
			return new RailPath(
				directDistance,
				List.of(
					new TrafficPathPoint(position1.getX(), position1.getY(), position1.getZ()),
					new TrafficPathPoint(position2.getX(), position2.getY(), position2.getZ())
				)
			);
		}
	}

	private static RailSnapshot snapshotRail(MtrRail rail) {
		return new RailSnapshot(
			rail.position1().x(), rail.position1().y(), rail.position1().z(), rail.angle1(),
			rail.position2().x(), rail.position2().y(), rail.position2().z(), rail.angle2(),
			rail.shape(), rail.verticalRadius(),
			rail.tiltPoints(), rail.tiltAngleDegrees1(), rail.tiltAngleDistance1a(),
			rail.tiltAngleDegrees1a(), rail.tiltAngleDegrees1b(), rail.tiltAngleDistance1b(),
			rail.tiltAngleDegreesMiddle(), rail.tiltAngleDistance2b(), rail.tiltAngleDegrees2b(),
			rail.tiltAngleDegrees2a(), rail.tiltAngleDistance2a(), rail.tiltAngleDegrees2(),
			rail.speedLimit1(), rail.speedLimit2(), false, rail.effectiveSignalColors()
		);
	}

	private static double measureDistance(Position a, Position b) {
		final double dx = a.getX() - b.getX();
		final double dy = a.getY() - b.getY();
		final double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static List<TrafficPathPoint> sampleRailPath(RailMath railMath, double railMathLength, double traversalLength, Position position1, Position position2) {
		if (!Double.isFinite(railMathLength) || railMathLength <= 0.0D) {
			throw new IllegalArgumentException("Invalid MTR rail geometry length");
		}
		final int samples = sampleCount(traversalLength);
		final List<TrafficPathPoint> points = new ArrayList<>(samples);
		for (int i = 0; i < samples; i++) {
			final double distance = railMathLength * i / (samples - 1.0D);
			final Vector position = railMath.getPosition(distance, false);
			if (!Double.isFinite(position.x()) || !Double.isFinite(position.y()) || !Double.isFinite(position.z())) {
				throw new IllegalArgumentException("Invalid MTR rail geometry position");
			}
			points.add(new TrafficPathPoint(position.x(), position.y(), position.z()));
		}

		// Keep the graph invariant explicit even if MTR changes its internal
		// endpoint handling again: every edge path must begin at from and end at to.
		final TrafficPathPoint first = points.get(0);
		final TrafficPathPoint last = points.get(points.size() - 1);
		final double forwardError = distanceSquared(first, position1) + distanceSquared(last, position2);
		final double reverseError = distanceSquared(first, position2) + distanceSquared(last, position1);
		if (reverseError < forwardError) {
			java.util.Collections.reverse(points);
		}
		return points;
	}

	private static double distanceSquared(TrafficPathPoint point, Position position) {
		final double dx = point.x() - position.getX();
		final double dy = point.y() - position.getY();
		final double dz = point.z() - position.getZ();
		return dx * dx + dy * dy + dz * dz;
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
		String angle1,
		long x2,
		long y2,
		long z2,
		String angle2,
		String shape,
		double verticalRadius,
		int tiltPoints,
		double tiltAngleDegrees1,
		double tiltAngleDistance1a,
		double tiltAngleDegrees1a,
		double tiltAngleDegrees1b,
		double tiltAngleDistance1b,
		double tiltAngleDegreesMiddle,
		double tiltAngleDistance2b,
		double tiltAngleDegrees2b,
		double tiltAngleDegrees2a,
		double tiltAngleDistance2a,
		double tiltAngleDegrees2,
		long speedLimit1,
		long speedLimit2,
		boolean mtaPathBlocked,
		List<Long> signalColors
	) {
		public RailSnapshot {
			signalColors = signalColors == null ? List.of() : List.copyOf(signalColors);
		}
	}
}
