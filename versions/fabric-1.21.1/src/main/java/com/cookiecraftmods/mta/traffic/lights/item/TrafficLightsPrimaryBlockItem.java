package com.cookiecraftmods.mta.traffic.lights.item;

import com.cookiecraftmods.mta.init.ModBlocks;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightSignalState;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsPoleTopBlock;
import com.cookiecraftmods.mta.traffic.lights.block.TrafficLightsVerticalPoleBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TrafficLightsPrimaryBlockItem extends BlockItem {
	public TrafficLightsPrimaryBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		final Level level = context.getLevel();
		final BlockState state = level.getBlockState(context.getClickedPos());
		if (state.is(ModBlocks.TRAFFIC_LIGHTS_VERTICAL_POLE)) {
			if (!level.isClientSide) {
				final boolean alreadyHadLights = state.getValue(TrafficLightsVerticalPoleBlock.HAS_LIGHTS);
				level.setBlock(context.getClickedPos(), state
					.setValue(TrafficLightsVerticalPoleBlock.FACING, attachFacing(context))
					.setValue(TrafficLightsVerticalPoleBlock.HAS_LIGHTS, true)
					.setValue(TrafficLightsVerticalPoleBlock.SIGNAL, alreadyHadLights ? state.getValue(TrafficLightsVerticalPoleBlock.SIGNAL) : TrafficLightSignalState.OFF), Block.UPDATE_ALL);
				if (!alreadyHadLights && (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild)) {
					context.getItemInHand().shrink(1);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		if (state.is(ModBlocks.TRAFFIC_LIGHTS_POLE)) {
			if (!level.isClientSide) {
				final boolean alreadyHadLights = state.getValue(TrafficLightsPoleTopBlock.HAS_LIGHTS);
				level.setBlock(context.getClickedPos(), state
					.setValue(TrafficLightsPoleTopBlock.FACING, attachFacing(context))
					.setValue(TrafficLightsPoleTopBlock.HAS_LIGHTS, true)
					.setValue(TrafficLightsPoleTopBlock.SIGNAL, alreadyHadLights ? state.getValue(TrafficLightsPoleTopBlock.SIGNAL) : TrafficLightSignalState.OFF), Block.UPDATE_ALL);
				if (!alreadyHadLights && (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild)) {
					context.getItemInHand().shrink(1);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return super.useOn(context);
	}

	private static Direction attachFacing(UseOnContext context) {
		final Direction clickedFace = context.getClickedFace();
		return clickedFace.getAxis().isHorizontal() ? clickedFace : context.getHorizontalDirection().getOpposite();
	}
}
