package com.cookiecraftmods.mta.traffic.rail;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.TrafficManager;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraph;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphEdge;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrNodeKey;
import com.cookiecraftmods.mta.traffic.runtime.TrafficPathPoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.core.data.TwoPositionsBase;
import org.mtr.mod.block.BlockNode;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MtaExclusiveRailRegistry {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<MtaExclusiveRailDefinition>>() { }.getType();
	private static final Map<String, MtaExclusiveRailDefinition> RAILS = new LinkedHashMap<>();
	private static final String EDGE_ID_PREFIX = "mta_exclusive:";
	private static final double SAMPLE_SPACING_METERS = 1.0D;
	private static boolean initialized;
	private static MinecraftServer currentServer;

	private MtaExclusiveRailRegistry() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			currentServer = server;
			load(server);
			applyNodeStates(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			save(server);
			currentServer = null;
			RAILS.clear();
		});
		initialized = true;
	}

	public static Collection<MtaExclusiveRailDefinition> getDefinitions() {
		return List.copyOf(RAILS.values());
	}

	public static boolean hasRail(ServerLevel level, BlockPos start, BlockPos end) {
		final String dimensionId = level.dimension().location().toString();
		return RAILS.containsKey(id(dimensionId, start, end)) || RAILS.containsKey(id(dimensionId, end, start));
	}

	public static void createRail(ServerLevel level, BlockPos start, BlockPos end, Angle startAngle, Angle endAngle, int speedLimitKph) {
		final String dimensionId = level.dimension().location().toString();
		final String id = id(dimensionId, start, end);
		RAILS.put(id, new MtaExclusiveRailDefinition(
			id,
			dimensionId,
			start.getX(),
			start.getY(),
			start.getZ(),
			end.getX(),
			end.getY(),
			end.getZ(),
			startAngle.name(),
			endAngle.name(),
			speedLimitKph
		));
		setNodeConnected(level, start, true);
		setNodeConnected(level, end, true);
		save(level.getServer());
		TrafficManager.onExclusiveRailGraphChanged(dimensionId);
	}

	public static boolean removeRail(ServerLevel level, BlockPos start, BlockPos end) {
		final String dimensionId = level.dimension().location().toString();
		final boolean removed = RAILS.remove(id(dimensionId, start, end)) != null || RAILS.remove(id(dimensionId, end, start)) != null;
		if (removed) {
			if (!hasRailTouching(dimensionId, start)) {
				setNodeConnected(level, start, false);
			}
			if (!hasRailTouching(dimensionId, end)) {
				setNodeConnected(level, end, false);
			}
			save(level.getServer());
			TrafficManager.onExclusiveRailGraphChanged(dimensionId);
		}
		return removed;
	}

	private static boolean hasRailTouching(String dimensionId, BlockPos node) {
		final long nodeKey = node.asLong();
		for (MtaExclusiveRailDefinition definition : RAILS.values()) {
			if (definition.dimensionId().equals(dimensionId) && (blockPosKey(definition.startX(), definition.startY(), definition.startZ()) == nodeKey || blockPosKey(definition.endX(), definition.endY(), definition.endZ()) == nodeKey)) {
				return true;
			}
		}
		return false;
	}

	private static long blockPosKey(long x, long y, long z) {
		return BlockPos.asLong((int) x, (int) y, (int) z);
	}

	private static void applyNodeStates(MinecraftServer server) {
		for (MtaExclusiveRailDefinition definition : RAILS.values()) {
			final ServerLevel level = levelFor(server, definition.dimensionId());
			if (level == null) {
				continue;
			}
			setNodeConnected(level, new BlockPos((int) definition.startX(), (int) definition.startY(), (int) definition.startZ()), true);
			setNodeConnected(level, new BlockPos((int) definition.endX(), (int) definition.endY(), (int) definition.endZ()), true);
		}
	}

	private static ServerLevel levelFor(MinecraftServer server, String dimensionId) {
		try {
			return server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimensionId)));
		} catch (Exception ignored) {
			return null;
		}
	}

	private static void setNodeConnected(ServerLevel level, BlockPos pos, boolean connected) {
		final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof BlockNode) || !state.hasProperty((net.minecraft.world.level.block.state.properties.Property<Boolean>) BlockNode.IS_CONNECTED.data)) {
			return;
		}
		level.setBlock(pos, state.setValue((net.minecraft.world.level.block.state.properties.Property<Boolean>) BlockNode.IS_CONNECTED.data, connected), net.minecraft.world.level.block.Block.UPDATE_ALL);
	}

	public static MtrGraph appendToGraph(String dimensionId, MtrGraph graph) {
		if (dimensionId == null || graph == null) {
			return graph;
		}

		final Map<MtrNodeKey, List<MtrGraphEdge>> adjacency = new LinkedHashMap<>();
		final List<MtrGraphEdge> edges = new ArrayList<>();
		for (MtrGraphEdge edge : graph.edges()) {
			if (edge.railId().startsWith(EDGE_ID_PREFIX)) {
				continue;
			}
			addEdge(adjacency, edges, edge);
		}

		RAILS.values().stream()
			.filter(definition -> definition.dimensionId().equals(dimensionId))
			.map(MtaExclusiveRailRegistry::toGraphEdge)
			.forEach(edge -> addEdge(adjacency, edges, edge));

		return new MtrGraph(Map.copyOf(adjacency), List.copyOf(edges));
	}

	public static String signature(String dimensionId) {
		if (dimensionId == null) {
			return "";
		}

		final StringBuilder builder = new StringBuilder();
		RAILS.values().stream()
			.filter(definition -> definition.dimensionId().equals(dimensionId))
			.sorted(Comparator.comparing(MtaExclusiveRailDefinition::id))
			.forEach(definition -> builder
				.append(definition.id()).append('@')
				.append(definition.startAngle()).append("->")
				.append(definition.endAngle()).append(':')
				.append(definition.speedLimitKph()).append(';'));
		return builder.toString();
	}

	private static void addEdge(Map<MtrNodeKey, List<MtrGraphEdge>> adjacency, List<MtrGraphEdge> edges, MtrGraphEdge edge) {
		edges.add(edge);
		adjacency.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
		adjacency.computeIfAbsent(edge.to(), ignored -> new ArrayList<>());
	}

	private static MtrGraphEdge toGraphEdge(MtaExclusiveRailDefinition definition) {
		final MtrNodeKey start = new MtrNodeKey(definition.endX(), definition.endY(), definition.endZ());
		final MtrNodeKey end = new MtrNodeKey(definition.startX(), definition.startY(), definition.startZ());
		final RailPath railPath = createRailPath(definition);
		final List<TrafficPathPoint> path = new ArrayList<>(railPath.points());
		java.util.Collections.reverse(path);
		return new MtrGraphEdge(
			EDGE_ID_PREFIX + TwoPositionsBase.getHexIdRaw(
				new Position(start.x(), start.y(), start.z()),
				new Position(end.x(), end.y(), end.z())
			),
			start,
			end,
			railPath.lengthMeters(),
			Math.max(1, definition.speedLimitKph()),
			List.of(),
			path
		);
	}

	private static RailPath createRailPath(MtaExclusiveRailDefinition definition) {
		try {
			final Position start = new Position(definition.startX(), definition.startY(), definition.startZ());
			final Position end = new Position(definition.endX(), definition.endY(), definition.endZ());
			final RailMath railMath = new RailMath(
				start,
				Angle.valueOf(definition.startAngle()),
				end,
				Angle.valueOf(definition.endAngle()),
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
			return new RailPath(length, points);
		} catch (Exception ignored) {
			final double length = distance(
				definition.startX(),
				definition.startY(),
				definition.startZ(),
				definition.endX(),
				definition.endY(),
				definition.endZ()
			);
			return new RailPath(length, List.of(
				new TrafficPathPoint(definition.startX(), definition.startY(), definition.startZ()),
				new TrafficPathPoint(definition.endX(), definition.endY(), definition.endZ())
			));
		}
	}

	private static double distance(Position start, Position end) {
		return distance(start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ());
	}

	private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
		final double dx = x1 - x2;
		final double dy = y1 - y2;
		final double dz = z1 - z2;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static String id(String dimensionId, BlockPos start, BlockPos end) {
		return dimensionId + "|mta_rail|" + start.asLong() + "|" + end.asLong();
	}

	private static void load(MinecraftServer server) {
		RAILS.clear();
		final Path path = savePath(server);
		if (!Files.exists(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			final List<MtaExclusiveRailDefinition> definitions = GSON.fromJson(reader, LIST_TYPE);
			if (definitions != null) {
				for (MtaExclusiveRailDefinition definition : definitions) {
					RAILS.put(definition.id(), definition);
				}
			}
		} catch (Exception e) {
			MTRTrafficAddon.LOGGER.error("Failed to load MTA exclusive rails", e);
		}
	}

	private static void save(MinecraftServer server) {
		try {
			final Path path = savePath(server);
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(new ArrayList<>(RAILS.values()), writer);
			}
		} catch (Exception e) {
			MTRTrafficAddon.LOGGER.error("Failed to save MTA exclusive rails", e);
		}
	}

	private static Path savePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MTRTrafficAddon.MOD_ID).resolve("mta_exclusive_rails.json");
	}

	private record RailPath(double lengthMeters, List<TrafficPathPoint> points) {
	}
}
