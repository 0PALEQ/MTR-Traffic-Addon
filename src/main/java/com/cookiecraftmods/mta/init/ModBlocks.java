package com.cookiecraftmods.mta.init;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsPedestrianBlock;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsPoleTopBlock;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsPrimaryBlock;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsVerticalPoleBlock;
import com.cookiecraftmods.mta.traffic.tollgate.TollgateBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Block TRAFFIC_LIGHTS_POLE_BOTTOM = registerBlock("traffic_lights_pole_bottom", new Block(poleProperties()));
	public static final Block TRAFFIC_LIGHTS_POLE = registerBlock("traffic_lights_pole", new TrafficLightsPoleTopBlock(poleProperties().lightLevel(TrafficLightsPoleTopBlock::lightLevel)));
	public static final Block TRAFFIC_LIGHTS_VERTICAL_POLE = registerBlock("traffic_lights_vertical_pole", new TrafficLightsVerticalPoleBlock(poleProperties().lightLevel(TrafficLightsVerticalPoleBlock::lightLevel)));
	
	public static final Block TRAFFIC_LIGHTS_PRIMARY = registerBlock("traffic_lights_primary", new TrafficLightsPrimaryBlock(poleProperties().lightLevel(TrafficLightsPrimaryBlock::lightLevel)));
	
	// to bedzie potem zmienione bo wygląda i działa jak gowno
	public static final Block PEDESTRIAN_LIGHTS = registerBlock("pedestrian_lights", new TrafficLightsPedestrianBlock(poleProperties().lightLevel(TrafficLightsPedestrianBlock::lightLevel)));
	public static final Block PEDESTRIAN_LIGHTS_POLE = registerBlock("pedestrian_lights_pole", new TrafficLightsPedestrianBlock(poleProperties().lightLevel(TrafficLightsPedestrianBlock::lightLevel)));
	public static final Block TOLLGATE_POLE = registerBlock("tollgate_pole", new TollgateBlock(poleProperties(), true));
	public static final Block TOLLGATE_BAR = registerBlock("tollgate_bar", new TollgateBlock(poleProperties(), false));

	private ModBlocks() {
	}

	public static void initialize() {
	}

	// Wspólne ustawienia dla wszystkich słupów
	private static BlockBehaviour.Properties poleProperties() {
		return BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.strength(1.5F, 6.0F)
			.sound(SoundType.METAL)
			.noOcclusion();
	}

	// Rejestracja bloków w grze
	private static Block registerBlock(String path, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(MTRTrafficAddon.MOD_ID, path), block);
	}
}
