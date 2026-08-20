package com.cookiecraftmods.mta.traffic.lights.block.entity;

import com.cookiecraftmods.mta.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TrafficLightBlockEntity extends BlockEntity {
	public TrafficLightBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(ModBlockEntities.TRAFFIC_LIGHT, blockPos, blockState);
	}
}
