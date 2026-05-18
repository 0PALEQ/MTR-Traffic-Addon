package com.cookiecraftmods.mta.traffic.mtr;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.mtr.dto.MtrDataResponse;
import com.cookiecraftmods.mta.traffic.mtr.dto.MtrPosition;
import com.cookiecraftmods.mta.traffic.mtr.dto.MtrUpdateRail;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraph;
import com.cookiecraftmods.mta.traffic.mtr.graph.MtrGraphBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import org.mtr.core.data.Position;
import org.mtr.core.operation.DataRequest;
import org.mtr.core.operation.DataResponse;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.mod.Init;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class MtrApiClient {
	private static final Gson GSON = new GsonBuilder().create();
	private static final long WARNING_INTERVAL_MILLIS = 5000;
	private static final long REQUEST_RADIUS_BLOCKS = 192;
	private static long lastWarningMillis;

	//Delete log spam later when everything works fine

	public void fetchGraphNearPlayer(ServerPlayer player, Consumer<Optional<MtrGraph>> callback) {
		final MtrPosition playerPosition = new MtrPosition(player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ());
		try {
			final DataRequest dataRequest = new DataRequest(
				player.getUUID(),
				new Position(playerPosition.x(), playerPosition.y(), playerPosition.z()),
				REQUEST_RADIUS_BLOCKS
			);

			Init.sendMessageC2S(
				OperationProcessor.GET_DATA,
				new org.mtr.mapping.holder.MinecraftServer(player.getServer()),
				new org.mtr.mapping.holder.World(player.level()),
				dataRequest,
				dataResponse -> handleDataResponse(player.getUUID(), playerPosition, dataResponse, callback),
				DataResponse.class
			);
		} catch (Exception e) {
			logWarning("Could not request MTR graph snapshot through internal MTR operation bus: {}", e.getMessage());
			callback.accept(Optional.empty());
		}
	}

	public boolean createRail(Object dimension, MtrUpdateRail rail) {
		MTRTrafficAddon.LOGGER.warn("createRail is not implemented for the internal MTR operation bus yet");
		return false;
	}

	private static void handleDataResponse(UUID playerId, MtrPosition playerPosition, DataResponse dataResponse, Consumer<Optional<MtrGraph>> callback) {
		try {
			if (dataResponse == null) {
				logWarning("MTR internal graph request returned null response for player {} near {}", playerId, playerPosition);
				callback.accept(Optional.empty());
				return;
			}

			final String responseJson = org.mtr.core.tool.Utilities.getJsonObjectFromData(dataResponse).toString();
			final MtrDataResponse parsedResponse = GSON.fromJson(responseJson, MtrDataResponse.class);
			if (parsedResponse == null || parsedResponse.rails() == null || parsedResponse.rails().isEmpty()) {
				logWarning("MTR internal graph request returned no rails near {}", playerPosition);
				callback.accept(Optional.empty());
				return;
			}

			callback.accept(Optional.of(MtrGraphBuilder.build(parsedResponse.rails())));
		} catch (Exception e) {
			logWarning("Could not parse MTR internal graph response near {}: {}", playerPosition, e.getMessage());
			callback.accept(Optional.empty());
		}
	}

	private static void logWarning(String message, Object... args) {
		final long now = System.currentTimeMillis();
		if (now - lastWarningMillis < WARNING_INTERVAL_MILLIS) {
			return;
		}
		lastWarningMillis = now;
		MTRTrafficAddon.LOGGER.warn(message, args);
	}
}
