package com.cookiecraftmods.mta.client.rail;

import com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint;
import net.minecraft.core.BlockPos;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientMtaExclusiveRailState {
	private static final Map<String, ClientMtaExclusiveRail> RAILS = new ConcurrentHashMap<>();
	private static final double SAMPLE_SPACING_METERS = 1.0D;

	private ClientMtaExclusiveRailState() {
	}

	public static void replace(Collection<ClientMtaExclusiveRail> rails) {
		RAILS.clear();
		for (ClientMtaExclusiveRail rail : rails) {
			RAILS.put(rail.id(), rail);
		}
	}

	public static Collection<ClientMtaExclusiveRail> all() {
		return List.copyOf(RAILS.values());
	}

	public static ClientMtaExclusiveRail create(String id, BlockPos start, BlockPos end, String startAngle, String endAngle, int speedLimitKph) {
		return new ClientMtaExclusiveRail(id, start, end, speedLimitKph, createPath(start, end, startAngle, endAngle), createRenderRail(start, end, startAngle, endAngle, speedLimitKph));
	}

	private static List<TrafficPathPoint> createPath(BlockPos start, BlockPos end, String startAngle, String endAngle) {
		try {
			final RailMath railMath = new RailMath(
				new Position(start.getX(), start.getY(), start.getZ()),
				Angle.valueOf(startAngle),
				new Position(end.getX(), end.getY(), end.getZ()),
				Angle.valueOf(endAngle),
				Rail.Shape.QUADRATIC,
				0.0D
			);
			final double length = Math.max(railMath.getLength(), distance(start, end));
			final int samples = Math.max(2, (int) Math.ceil(length / SAMPLE_SPACING_METERS) + 1);
			final List<TrafficPathPoint> points = new ArrayList<>(samples);
			for (int i = 0; i < samples; i++) {
				final double sampleDistance = length * i / (samples - 1.0D);
				final Vector position = railMath.getPosition(sampleDistance, false);
				points.add(new TrafficPathPoint(position.x(), position.y(), position.z()));
			}
			return List.copyOf(points);
		} catch (Exception ignored) {
			return List.of(
				new TrafficPathPoint(start.getX(), start.getY(), start.getZ()),
				new TrafficPathPoint(end.getX(), end.getY(), end.getZ())
			);
		}
	}

	private static double distance(BlockPos start, BlockPos end) {
		final double dx = start.getX() - end.getX();
		final double dy = start.getY() - end.getY();
		final double dz = start.getZ() - end.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static Rail createRenderRail(BlockPos start, BlockPos end, String startAngle, String endAngle, int speedLimitKph) {
		try {
			return Rail.newRail(
				new Position(start.getX(), start.getY(), start.getZ()),
				Angle.valueOf(startAngle),
				new Position(end.getX(), end.getY(), end.getZ()),
				Angle.valueOf(endAngle),
				Rail.Shape.QUADRATIC,
				0.0D,
				new ObjectArrayList<>(),
				0,
				Math.max(1, speedLimitKph),
				false,
				false,
				true,
				false,
				true,
				TransportMode.TRAIN
			);
		} catch (Exception ignored) {
			return null;
		}
	}

	public record ClientMtaExclusiveRail(
		String id,
		BlockPos start,
		BlockPos end,
		int speedLimitKph,
		List<TrafficPathPoint> path,
		Rail renderRail
	) {
		public ClientMtaExclusiveRail {
			path = path == null ? List.of() : List.copyOf(path);
		}
	}
}
