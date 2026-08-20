package com.cookiecraftmods.mta.traffic.mtr;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.mtr.dto.MtrDataResponse;
import com.cookiecraftmods.mta.traffic.mtr.dto.MtrPosition;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraph;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mtr.MTR;
import org.mtr.core.data.Position;
import org.mtr.core.operation.DataRequest;
import org.mtr.core.operation.DataResponse;
import org.mtr.core.servlet.OperationProcessor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

public final class MtrApiClient {
	private static final Gson GSON = new GsonBuilder().create();
	private static final long WARNING_INTERVAL_MILLIS = 5000;
	private static final long REQUEST_RADIUS_BLOCKS = 8192;
	private static long lastWarningMillis;
	private ExecutorService graphExecutor;
	private boolean acceptingTasks;
	private long lifecycleGeneration;

	public void fetchGraphNearPlayer(ServerPlayer player, Consumer<Optional<MtrGraph>> callback) {
		final MtrPosition playerPosition = new MtrPosition(player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ());
		final MinecraftServer server = player.getServer();
		final long requestGeneration = requestGeneration();
		if (requestGeneration < 0L) {
			callback.accept(Optional.empty());
			return;
		}
		try {
			final DataRequest dataRequest = new DataRequest(
				player.getUUID(),
				new Position(playerPosition.x(), playerPosition.y(), playerPosition.z()),
				REQUEST_RADIUS_BLOCKS
			);

			MTR.sendMessageC2S(
				OperationProcessor.GET_DATA,
				player.getServer(),
				player.level(),
				dataRequest,
				dataResponse -> handleDataResponse(server, requestGeneration, player.getUUID(), playerPosition, dataResponse, callback),
				DataResponse.class
			);
		} catch (Exception e) {
			logWarning("Could not request MTR graph snapshot through internal MTR operation bus: {}", e.getMessage());
			dispatchResult(server, requestGeneration, null, callback, Optional.empty());
		}
	}

	public synchronized void start() {
		lifecycleGeneration++;
		acceptingTasks = true;
	}

	public synchronized void shutdown() {
		acceptingTasks = false;
		lifecycleGeneration++;
		final ExecutorService executor = graphExecutor;
		graphExecutor = null;
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	private void handleDataResponse(MinecraftServer server, long requestGeneration, UUID playerId, MtrPosition playerPosition, DataResponse dataResponse, Consumer<Optional<MtrGraph>> callback) {
		if (!acceptsResult(requestGeneration, null)) {
			return;
		}
		if (dataResponse == null) {
			logWarning("MTR internal graph request returned null response for player {} near {}", playerId, playerPosition);
			dispatchResult(server, requestGeneration, null, callback, Optional.empty());
			return;
		}

		try {
			final ExecutorService executor = executor(requestGeneration);
			executor.execute(() -> dispatchResult(server, requestGeneration, executor, callback, buildGraph(playerPosition, dataResponse)));
		} catch (RejectedExecutionException e) {
			logWarning("Could not schedule MTR graph processing near {}: {}", playerPosition, e.getMessage());
			dispatchResult(server, requestGeneration, null, callback, Optional.empty());
		}
	}

	private static Optional<MtrGraph> buildGraph(MtrPosition playerPosition, DataResponse dataResponse) {
		try {
			final String responseJson = org.mtr.core.tool.Utilities.getJsonObjectFromData(dataResponse).toString();
			final MtrDataResponse parsedResponse = GSON.fromJson(responseJson, MtrDataResponse.class);
			if (parsedResponse == null || parsedResponse.rails() == null || parsedResponse.rails().isEmpty()) {
				logWarning("MTR internal graph request returned no rails near {}", playerPosition);
				return Optional.empty();
			}

			return Optional.of(MtrGraphBuilder.build(parsedResponse.rails()));
		} catch (Exception e) {
			logWarning("Could not parse or build MTR internal graph response near {}: {}", playerPosition, e.getMessage());
			return Optional.empty();
		}
	}

	private void dispatchResult(MinecraftServer server, long requestGeneration, ExecutorService sourceExecutor, Consumer<Optional<MtrGraph>> callback, Optional<MtrGraph> result) {
		if (!acceptsResult(requestGeneration, sourceExecutor)) {
			return;
		}
		server.execute(() -> {
			if (acceptsResult(requestGeneration, sourceExecutor)) {
				callback.accept(result);
			}
		});
	}

	private synchronized long requestGeneration() {
		return acceptingTasks ? lifecycleGeneration : -1L;
	}

	private synchronized boolean acceptsResult(long requestGeneration, ExecutorService sourceExecutor) {
		return acceptingTasks && requestGeneration == lifecycleGeneration
			&& (sourceExecutor == null || sourceExecutor == graphExecutor && !sourceExecutor.isShutdown());
	}

	private synchronized ExecutorService executor(long requestGeneration) {
		if (!acceptingTasks || requestGeneration != lifecycleGeneration) {
			throw new RejectedExecutionException("MTR graph processor is stopped");
		}
		if (graphExecutor == null || graphExecutor.isShutdown() || graphExecutor.isTerminated()) {
			graphExecutor = Executors.newSingleThreadExecutor(runnable -> {
				final Thread thread = new Thread(runnable, "MTR Traffic Addon Graph Builder");
				thread.setDaemon(true);
				return thread;
			});
		}
		return graphExecutor;
	}

	private static void logWarning(String message, Object... args) {
		final long now = System.currentTimeMillis();
		if (now - lastWarningMillis < WARNING_INTERVAL_MILLIS) {
			return;
		}
		lastWarningMillis = now;
		MTRTrafficAddon.LOGGER.debug(message, args);
	}
}
