package com.cookiecraftmods.mta.traffic.tollgate;

import com.cookiecraftmods.mta.traffic.tollgate.entity.TollgateBlockEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

public class TollgateBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<TollgateBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		propertiesCodec(),
		Codec.BOOL.fieldOf("pole").forGetter(block -> block.pole)
	).apply(instance, TollgateBlock::new));
	public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
	private final boolean pole;

	public TollgateBlock(Properties properties, boolean pole) {
		super(properties);
		this.pole = pole;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST).setValue(CLOSED, true));
	}

	@Override
	protected MapCodec<? extends TollgateBlock> codec() {
		return CODEC;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return pole ? RenderShape.MODEL : RenderShape.INVISIBLE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		final Direction clickedFace = context.getClickedFace();
		final Direction facing = clickedFace.getAxis().isHorizontal() ? clickedFace : context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, facing).setValue(CLOSED, true);
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
		builder.add(FACING, CLOSED);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new TollgateBlockEntity(blockPos, blockState);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return level.isClientSide ? null : (tickerLevel, pos, blockState, blockEntity) -> {
			if (blockEntity instanceof TollgateBlockEntity tollgateBlockEntity) {
				tollgateBlockEntity.serverTick();
			}
		};
	}
}
