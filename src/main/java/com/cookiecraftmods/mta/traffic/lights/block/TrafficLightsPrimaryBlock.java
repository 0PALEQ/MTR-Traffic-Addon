package com.cookiecraftmods.mta.traffic.lights.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import com.cookiecraftmods.mta.traffic.lights.block.entity.TrafficLightBlockEntity;
import org.jetbrains.annotations.Nullable;

public class TrafficLightsPrimaryBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final EnumProperty<TrafficLightSignalState> SIGNAL = EnumProperty.create("signal", TrafficLightSignalState.class);

	public TrafficLightsPrimaryBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST).setValue(SIGNAL, TrafficLightSignalState.OFF));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		final Direction clickedFace = context.getClickedFace();
		final Direction facing = clickedFace.getAxis().isHorizontal() ? clickedFace : context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, facing).setValue(SIGNAL, TrafficLightSignalState.OFF);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SIGNAL);
	}

	public static int lightLevel(BlockState state) {
		return state.getValue(SIGNAL).isLit() ? 10 : 0;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new TrafficLightBlockEntity(blockPos, blockState);
	}
}
