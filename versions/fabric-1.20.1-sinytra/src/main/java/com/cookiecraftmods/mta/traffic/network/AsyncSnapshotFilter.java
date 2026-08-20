package com.cookiecraftmods.mta.traffic.network;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AsyncSnapshotFilter {
	private static final class PlayerSnapshotCache {
		volatile SnapshotResult snapshot;
	}

	public record PlayerViewSnapshot(UUID playerId, String dimensionId, double x, double z) {
	}

	private record SnapshotJob(
		List<PlayerViewSnapshot> players,
		List<TrafficNetworkVehicleSnapshot> vehicles,
		double maxDistanceBlocks,
		long sequence,
		long lifecycleGeneration
	) {
	}

	private static final ConcurrentHashMap<UUID, PlayerSnapshotCache> PLAYER_SNAPSHOTS = new ConcurrentHashMap<>();
	private static final AtomicReference<SnapshotJob> PENDING_JOB = new AtomicReference<>();
	private static final AtomicBoolean DRAIN_SCHEDULED = new AtomicBoolean();
	private static ExecutorService executor;
	private static volatile boolean acceptingTasks;
	private static volatile long lifecycleGeneration;

	private AsyncSnapshotFilter() {
	}

	public static synchronized void submitAsync(
		Collection<PlayerViewSnapshot> players,
		Collection<TrafficNetworkVehicleSnapshot> vehicles,
		double maxDistanceBlocks,
		long sequence
	) {
		if (!acceptingTasks) {
			return;
		}
		final List<PlayerViewSnapshot> immutablePlayers = List.copyOf(players);
		for (PlayerViewSnapshot player : immutablePlayers) {
			PLAYER_SNAPSHOTS.computeIfAbsent(player.playerId(), ignored -> new PlayerSnapshotCache());
		}
		PENDING_JOB.set(new SnapshotJob(immutablePlayers, List.copyOf(vehicles), maxDistanceBlocks, sequence, lifecycleGeneration));
		scheduleDrain();
	}

	private static void scheduleDrain() {
		if (!DRAIN_SCHEDULED.compareAndSet(false, true)) {
			return;
		}
		try {
			final long drainGeneration = lifecycleGeneration;
			executor().execute(() -> drainLatestJobs(drainGeneration));
		} catch (RejectedExecutionException ignored) {
			DRAIN_SCHEDULED.set(false);
		}
	}

	private static void drainLatestJobs(long drainGeneration) {
		try {
			SnapshotJob job;
			while (drainGeneration == lifecycleGeneration && (job = PENDING_JOB.getAndSet(null)) != null) {
				if (job.lifecycleGeneration() == drainGeneration) {
					process(job);
				}
			}
		} catch (Exception e) {
			MTRTrafficAddon.LOGGER.error("Error in async traffic snapshot filter", e);
		} finally {
			if (drainGeneration == lifecycleGeneration) {
				DRAIN_SCHEDULED.set(false);
				if (PENDING_JOB.get() != null) {
					scheduleDrain();
				}
			}
		}
	}

	private static void process(SnapshotJob job) {
		if (job.lifecycleGeneration() != lifecycleGeneration) {
			return;
		}
		final SpatialVehicleIndex spatialIndex = SpatialVehicleIndex.build(job.vehicles());
		final double maxDistanceSquared = job.maxDistanceBlocks() * job.maxDistanceBlocks();

		for (PlayerViewSnapshot player : job.players()) {
			if (job.lifecycleGeneration() != lifecycleGeneration) {
				return;
			}
			final PlayerSnapshotCache cache = PLAYER_SNAPSHOTS.get(player.playerId());
			if (cache == null) {
				continue;
			}
			final List<TrafficNetworkVehicleSnapshot> visibleVehicles = spatialIndex.queryNearby(
				player.dimensionId(), player.x(), player.z(), maxDistanceSquared
			);
			final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
			try {
				buffer.writeLong(job.sequence());
				buffer.writeVarInt(visibleVehicles.size());
				final List<UUID> vehicleIds = new ArrayList<>(visibleVehicles.size());
				for (TrafficNetworkVehicleSnapshot vehicle : visibleVehicles) {
					buffer.writeUUID(vehicle.id());
					buffer.writeUtf(vehicle.visualId());
					buffer.writeUtf(vehicle.vehicleType());
					buffer.writeDouble(vehicle.lengthMeters());
					buffer.writeDouble(vehicle.x());
					buffer.writeDouble(vehicle.y());
					buffer.writeDouble(vehicle.z());
					buffer.writeFloat(vehicle.yawDegrees());
					buffer.writeFloat(vehicle.pitchDegrees());
					buffer.writeDouble(vehicle.speedKph());
					vehicleIds.add(vehicle.id());
				}

				final byte[] encoded = new byte[buffer.readableBytes()];
				buffer.getBytes(buffer.readerIndex(), encoded);
				final SnapshotResult previousSnapshot = cache.snapshot;
				if (job.lifecycleGeneration() == lifecycleGeneration && (previousSnapshot == null || job.sequence() >= previousSnapshot.sequence())) {
					cache.snapshot = new SnapshotResult(encoded, List.copyOf(vehicleIds), job.sequence());
				}
			} finally {
				buffer.release();
			}
		}
	}

	private static synchronized ExecutorService executor() {
		if (!acceptingTasks) {
			throw new RejectedExecutionException("Traffic snapshot encoder is stopped");
		}
		if (executor == null || executor.isShutdown() || executor.isTerminated()) {
			executor = Executors.newSingleThreadExecutor(runnable -> {
				final Thread thread = new Thread(runnable, "MTR Traffic Addon Snapshot Encoder");
				thread.setDaemon(true);
				return thread;
			});
		}
		return executor;
	}

	public static SnapshotResult getSnapshot(UUID playerId) {
		final PlayerSnapshotCache cache = PLAYER_SNAPSHOTS.get(playerId);
		return cache == null ? null : cache.snapshot;
	}

	public record SnapshotResult(byte[] buffer, List<UUID> vehicleIds, long sequence) {
	}

	public static synchronized void start() {
		lifecycleGeneration++;
		PENDING_JOB.set(null);
		DRAIN_SCHEDULED.set(false);
		PLAYER_SNAPSHOTS.clear();
		acceptingTasks = true;
	}

	public static void clearPlayer(UUID playerUUID) {
		PLAYER_SNAPSHOTS.remove(playerUUID);
	}

	public static synchronized void shutdown() {
		acceptingTasks = false;
		lifecycleGeneration++;
		final ExecutorService currentExecutor = executor;
		executor = null;
		PENDING_JOB.set(null);
		DRAIN_SCHEDULED.set(false);
		if (currentExecutor != null) {
			currentExecutor.shutdownNow();
		}
		PLAYER_SNAPSHOTS.clear();
	}
}
