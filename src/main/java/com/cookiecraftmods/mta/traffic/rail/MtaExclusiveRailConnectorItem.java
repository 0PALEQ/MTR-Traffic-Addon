package com.cookiecraftmods.mta.traffic.rail;

import net.minecraft.server.level.ServerLevel;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.holder.World;
import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemRailModifier;

public class MtaExclusiveRailConnectorItem extends ItemRailModifier {
	private static final boolean ENABLE_UNFINISHED_CONNECTORS = Boolean.getBoolean("mta.enableUnfinishedTrafficConnectors");
	private final int speedLimitKph;

	public MtaExclusiveRailConnectorItem(int speedLimitKph) {
		super(true, true, true, false, RailType.OBSIDIAN, new ItemSettings());
		this.speedLimitKph = speedLimitKph;
	}

	@Override
	protected void onConnect(World world, ItemStack itemStack, TransportMode transportMode, BlockState state1, BlockState state2, BlockPos pos1, BlockPos pos2, Angle angle1, Angle angle2, ServerPlayerEntity player) {
		final ServerLevel level = (ServerLevel) ServerWorld.cast(world).data;
		final net.minecraft.core.BlockPos start = new net.minecraft.core.BlockPos(pos1.getX(), pos1.getY(), pos1.getZ());
		final net.minecraft.core.BlockPos end = new net.minecraft.core.BlockPos(pos2.getX(), pos2.getY(), pos2.getZ());
		if (MtaExclusiveRailRegistry.hasRail(level, start, end)) {
			MtaExclusiveRailRegistry.removeRail(level, start, end);
			return;
		}

		if (!ENABLE_UNFINISHED_CONNECTORS) {
			if (player != null) {
				player.sendMessage(Text.of("MTA Traffic Connectors are unfinished and disabled in this build."), true);
			}
			return;
		}

		final Rail rail = createRail(player == null ? null : player.getUuid(), transportMode, state1, state2, pos1, pos2, angle1, angle2);
		if (rail == null) {
			return;
		}

		MtaExclusiveRailRegistry.createRail(
			level,
			start,
			end,
			angle1,
			angle2,
			speedLimitKph
		);
	}

	@Override
	protected void onRemove(World world, BlockPos pos1, BlockPos pos2, ServerPlayerEntity player) {
		MtaExclusiveRailRegistry.removeRail(
			(ServerLevel) ServerWorld.cast(world).data,
			new net.minecraft.core.BlockPos(pos1.getX(), pos1.getY(), pos1.getZ()),
			new net.minecraft.core.BlockPos(pos2.getX(), pos2.getY(), pos2.getZ())
		);
	}
}
