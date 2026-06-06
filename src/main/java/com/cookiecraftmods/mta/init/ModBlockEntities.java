package com.cookiecraftmods.mta.init;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.lights.block.entity.TrafficLightBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<TrafficLightBlockEntity> TRAFFIC_LIGHT = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		new ResourceLocation(MTRTrafficAddon.MOD_ID, "traffic_light"),
		FabricBlockEntityTypeBuilder.create(
			TrafficLightBlockEntity::new,
			ModBlocks.TRAFFIC_LIGHTS_POLE,
			ModBlocks.TRAFFIC_LIGHTS_VERTICAL_POLE,
			ModBlocks.TRAFFIC_LIGHTS_PRIMARY,
			ModBlocks.PEDESTRIAN_LIGHTS,
			ModBlocks.PEDESTRIAN_LIGHTS_POLE
		).build()
	);

	private ModBlockEntities() {
	}

	public static void initialize() {
	}
}
