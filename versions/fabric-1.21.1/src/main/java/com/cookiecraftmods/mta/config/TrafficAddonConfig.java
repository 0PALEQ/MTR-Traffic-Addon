package com.cookiecraftmods.mta.config;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class TrafficAddonConfig {
	private static final String FILE_NAME = MTRTrafficAddon.MOD_ID + ".properties";
	private static final String TRAFFIC_VEHICLE_VISIBILITY_DISTANCE_BLOCKS = "trafficVehicleVisibilityDistanceBlocks";
	private static final String TRAFFIC_VEHICLE_SIMULATION_DISTANCE_BLOCKS = "trafficVehicleSimulationDistanceBlocks";
	private static final String TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS = "trafficVehicleMaterializationMarginChunks";
	private static final String TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS = "trafficVehicleUnrenderedLifetimeSeconds";
	private static final String AUTO_DISTANCE_VALUE = "auto";
	private static final double MIN_TRAFFIC_VEHICLE_VISIBILITY_DISTANCE_BLOCKS = 16.0D;
	private static final double MAX_TRAFFIC_VEHICLE_VISIBILITY_DISTANCE_BLOCKS = 1024.0D;
	private static final double MIN_TRAFFIC_VEHICLE_SIMULATION_DISTANCE_BLOCKS = 32.0D;
	private static final double MAX_TRAFFIC_VEHICLE_SIMULATION_DISTANCE_BLOCKS = 2048.0D;
	private static final int DEFAULT_TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS = 2;
	private static final int MIN_TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS = 0;
	private static final int MAX_TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS = 16;
	private static final int DEFAULT_TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS = 30;
	private static final int MIN_TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS = 0;
	private static final int MAX_TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS = 600;
	private static Double fixedTrafficVehicleVisibilityDistanceBlocks;
	private static Double fixedTrafficVehicleSimulationDistanceBlocks;
	private static int trafficVehicleMaterializationMarginChunks = DEFAULT_TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS;
	private static int trafficVehicleUnrenderedLifetimeSeconds = DEFAULT_TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS;
	private static boolean loaded;

	private TrafficAddonConfig() {
	}

	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;

		final Path path = configPath();
		final Properties properties = defaultProperties();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				properties.load(reader);
			} catch (IOException e) {
				MTRTrafficAddon.LOGGER.warn("Could not read {}; using default traffic distance config", path, e);
			}
		}

		fixedTrafficVehicleVisibilityDistanceBlocks = readOptionalDouble(
			properties,
			TRAFFIC_VEHICLE_VISIBILITY_DISTANCE_BLOCKS,
			MIN_TRAFFIC_VEHICLE_VISIBILITY_DISTANCE_BLOCKS,
			MAX_TRAFFIC_VEHICLE_VISIBILITY_DISTANCE_BLOCKS
		);
		fixedTrafficVehicleSimulationDistanceBlocks = readOptionalDouble(
			properties,
			TRAFFIC_VEHICLE_SIMULATION_DISTANCE_BLOCKS,
			MIN_TRAFFIC_VEHICLE_SIMULATION_DISTANCE_BLOCKS,
			MAX_TRAFFIC_VEHICLE_SIMULATION_DISTANCE_BLOCKS
		);
		trafficVehicleMaterializationMarginChunks = readInt(
			properties,
			TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS,
			DEFAULT_TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS,
			MIN_TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS,
			MAX_TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS
		);
		trafficVehicleUnrenderedLifetimeSeconds = readInt(
			properties,
			TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS,
			DEFAULT_TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS,
			MIN_TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS,
			MAX_TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS
		);

		properties.setProperty(TRAFFIC_VEHICLE_VISIBILITY_DISTANCE_BLOCKS, distanceString(fixedTrafficVehicleVisibilityDistanceBlocks));
		properties.setProperty(TRAFFIC_VEHICLE_SIMULATION_DISTANCE_BLOCKS, distanceString(fixedTrafficVehicleSimulationDistanceBlocks));
		properties.setProperty(TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS, Integer.toString(trafficVehicleMaterializationMarginChunks));
		properties.setProperty(TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS, Integer.toString(trafficVehicleUnrenderedLifetimeSeconds));
		writeDefaults(path, properties);
	}

	public static double trafficVehicleVisibilityDistanceBlocks(int renderDistanceChunks) {
		load();
		return fixedTrafficVehicleVisibilityDistanceBlocks == null
			? automaticVisibilityDistanceBlocks(renderDistanceChunks)
			: fixedTrafficVehicleVisibilityDistanceBlocks;
	}

	public static double trafficVehicleSimulationDistanceBlocks(int renderDistanceChunks) {
		load();
		final double visibilityDistanceBlocks = trafficVehicleVisibilityDistanceBlocks(renderDistanceChunks);
		final double simulationDistanceBlocks = fixedTrafficVehicleSimulationDistanceBlocks == null
			? automaticSimulationDistanceBlocks(renderDistanceChunks)
			: fixedTrafficVehicleSimulationDistanceBlocks;
		return Math.max(simulationDistanceBlocks, visibilityDistanceBlocks);
	}

	public static int trafficVehicleMaterializationMarginChunks() {
		load();
		return trafficVehicleMaterializationMarginChunks;
	}

	public static int trafficVehicleUnrenderedLifetimeSeconds() {
		load();
		return trafficVehicleUnrenderedLifetimeSeconds;
	}

	private static Properties defaultProperties() {
		final Properties properties = new Properties();
		properties.setProperty(TRAFFIC_VEHICLE_VISIBILITY_DISTANCE_BLOCKS, AUTO_DISTANCE_VALUE);
		properties.setProperty(TRAFFIC_VEHICLE_SIMULATION_DISTANCE_BLOCKS, AUTO_DISTANCE_VALUE);
		properties.setProperty(TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS, Integer.toString(DEFAULT_TRAFFIC_VEHICLE_MATERIALIZATION_MARGIN_CHUNKS));
		properties.setProperty(TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS, Integer.toString(DEFAULT_TRAFFIC_VEHICLE_UNRENDERED_LIFETIME_SECONDS));
		return properties;
	}

	private static void writeDefaults(Path path, Properties properties) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				properties.store(writer, "MTR Traffic Addon config");
			}
		} catch (IOException e) {
			MTRTrafficAddon.LOGGER.warn("Could not write {}", path, e);
		}
	}

	private static Double readOptionalDouble(Properties properties, String key, double minValue, double maxValue) {
		final String rawValue = properties.getProperty(key);
		if (rawValue == null || rawValue.isBlank()) {
			return null;
		}
		if (AUTO_DISTANCE_VALUE.equalsIgnoreCase(rawValue.trim())) {
			return null;
		}

		try {
			final double value = Double.parseDouble(rawValue.trim());
			if (!Double.isFinite(value)) {
				return null;
			}
			return Math.min(maxValue, Math.max(minValue, value));
		} catch (NumberFormatException e) {
			MTRTrafficAddon.LOGGER.warn("Invalid {} value {}; using auto", key, rawValue);
			return null;
		}
	}

	private static int readInt(Properties properties, String key, int defaultValue, int minValue, int maxValue) {
		final String rawValue = properties.getProperty(key);
		if (rawValue == null || rawValue.isBlank()) {
			return defaultValue;
		}

		try {
			final int value = Integer.parseInt(rawValue.trim());
			return Math.min(maxValue, Math.max(minValue, value));
		} catch (NumberFormatException e) {
			MTRTrafficAddon.LOGGER.warn("Invalid {} value {}; using {}", key, rawValue, defaultValue);
			return defaultValue;
		}
	}

	private static double automaticVisibilityDistanceBlocks(int renderDistanceChunks) {
		return Math.max(2, renderDistanceChunks - 2) * 16.0D;
	}

	private static double automaticSimulationDistanceBlocks(int renderDistanceChunks) {
		return automaticVisibilityDistanceBlocks(renderDistanceChunks) + trafficVehicleMaterializationMarginChunks * 16.0D;
	}

	private static String distanceString(Double value) {
		return value == null ? AUTO_DISTANCE_VALUE : doubleString(value);
	}

	private static String doubleString(double value) {
		if (value == Math.rint(value)) {
			return Long.toString(Math.round(value));
		}
		return Double.toString(value);
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}
}
