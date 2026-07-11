package com.cookiecraftmods.mta.traffic.tollgate.entity;

import com.cookiecraftmods.mta.init.ModBlockEntities;
import com.cookiecraftmods.mta.init.ModBlocks;
import com.cookiecraftmods.mta.traffic.TrafficManager;
import com.cookiecraftmods.mta.traffic.intersection.TrafficIntersectionRegistry;
import com.cookiecraftmods.mta.traffic.tollgate.TollgateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public class TollgateBlockEntity extends BlockEntity {
	private static final int UPDATE_INTERVAL_TICKS = 5;
	private static final int MAX_CONNECTED_BAR_LENGTH = 7;

	public TollgateBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(ModBlockEntities.TOLLGATE, blockPos, blockState);
	}

	public void serverTick() {
		if (!(level instanceof ServerLevel serverLevel) || serverLevel.getGameTime() % UPDATE_INTERVAL_TICKS != 0 || !getBlockState().hasProperty(TollgateBlock.CLOSED)) {
			return;
		}
		final String dimensionId = serverLevel.dimension().location().toString();
		final Optional<Boolean> managedState = getBlockState().is(ModBlocks.TOLLGATE_POLE)
			? managedPoleState(serverLevel, dimensionId, getBlockState().getValue(TollgateBlock.FACING))
			: TrafficIntersectionRegistry.trainTollgateStateNear(dimensionId, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), TrafficManager.signalTick());
		if (managedState.isEmpty()) {
			return;
		}
		final boolean shouldClose = managedState.get();
		updateClosedState(serverLevel, worldPosition, shouldClose);
		if (getBlockState().is(ModBlocks.TOLLGATE_POLE)) {
			final Direction direction = getBlockState().getValue(TollgateBlock.FACING);
			updateConnectedBars(serverLevel, direction, shouldClose);
			updateConnectedBars(serverLevel, direction.getOpposite(), shouldClose);
		}
	}

	private Optional<Boolean> managedPoleState(ServerLevel serverLevel, String dimensionId, Direction direction) {
		Optional<Boolean> managedState = TrafficIntersectionRegistry.trainTollgateStateNear(dimensionId, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), TrafficManager.signalTick());
		if (managedState.isPresent()) {
			return managedState;
		}
		managedState = connectedBarManagedState(serverLevel, dimensionId, direction);
		if (managedState.isPresent()) {
			return managedState;
		}
		return connectedBarManagedState(serverLevel, dimensionId, direction.getOpposite());
	}

	private Optional<Boolean> connectedBarManagedState(ServerLevel serverLevel, String dimensionId, Direction direction) {
		for (int i = 1; i <= MAX_CONNECTED_BAR_LENGTH; i++) {
			final BlockPos barPos = worldPosition.relative(direction, i);
			final BlockState barState = serverLevel.getBlockState(barPos);
			if (!barState.is(ModBlocks.TOLLGATE_BAR)) {
				break;
			}
			final Optional<Boolean> managedState = TrafficIntersectionRegistry.trainTollgateStateNear(dimensionId, barPos.getX(), barPos.getY(), barPos.getZ(), TrafficManager.signalTick());
			if (managedState.isPresent()) {
				return managedState;
			}
		}
		return Optional.empty();
	}

	private void updateConnectedBars(ServerLevel serverLevel, Direction direction, boolean shouldClose) {
		for (int i = 1; i <= MAX_CONNECTED_BAR_LENGTH; i++) {
			final BlockPos barPos = worldPosition.relative(direction, i);
			final BlockState barState = serverLevel.getBlockState(barPos);
			if (!barState.is(ModBlocks.TOLLGATE_BAR)) {
				break;
			}
			updateBarState(serverLevel, barPos, direction, shouldClose);
		}
	}

	private static void updateBarState(ServerLevel serverLevel, BlockPos pos, Direction direction, boolean shouldClose) {
		final BlockState state = serverLevel.getBlockState(pos);
		if (!state.hasProperty(TollgateBlock.CLOSED) || !state.hasProperty(TollgateBlock.FACING)) {
			return;
		}
		BlockState updated = state;
		if (updated.getValue(TollgateBlock.FACING) != direction) {
			updated = updated.setValue(TollgateBlock.FACING, direction);
		}
		if (updated.getValue(TollgateBlock.CLOSED) != shouldClose) {
			updated = updated.setValue(TollgateBlock.CLOSED, shouldClose);
		}
		if (updated != state) {
			serverLevel.setBlock(pos, updated, Block.UPDATE_ALL);
		}
	}

	private static void updateClosedState(ServerLevel serverLevel, BlockPos pos, boolean shouldClose) {
		final BlockState state = serverLevel.getBlockState(pos);
		if (state.hasProperty(TollgateBlock.CLOSED) && state.getValue(TollgateBlock.CLOSED) != shouldClose) {
			serverLevel.setBlock(pos, state.setValue(TollgateBlock.CLOSED, shouldClose), Block.UPDATE_ALL);
		}
	}
}
