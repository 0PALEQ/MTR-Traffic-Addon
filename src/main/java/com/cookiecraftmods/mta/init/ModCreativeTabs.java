package com.cookiecraftmods.mta.init;

import com.cookiecraftmods.mta.MTRTrafficAddon;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTabs {
	public static final CreativeModeTab MAIN = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		new ResourceLocation(MTRTrafficAddon.MOD_ID, "main"),
		CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
			.title(Component.translatable("itemGroup.mtr-traffic-addon.main"))
			.icon(() -> new ItemStack(ModItems.TRAFFIC_SPAWN_CONNECTOR))
			.displayItems((parameters, output) -> {
				output.accept(ModItems.TRAFFIC_SPAWN_CONNECTOR);
				output.accept(ModItems.TRAFFIC_DESPAWN_CONNECTOR);
				output.accept(ModItems.TRAFFIC_DASHBOARD);
			})
			.build()
	);

	private ModCreativeTabs() {
	}

	public static void initialize() {
	}
}
