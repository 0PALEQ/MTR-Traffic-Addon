package com.cookiecraftmods.mta.traffic.dashboard.item;

import com.cookiecraftmods.mta.traffic.dashboard.network.TrafficDashboardNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

//Dont' touch
public class TrafficDashboardItem extends Item {
	public TrafficDashboardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			TrafficDashboardNetworking.sendSnapshot(serverPlayer);
		}

		return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), level.isClientSide);
	}
}
