package com.cookiecraftmods.mta.traffic.point.connector;

import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import com.cookiecraftmods.mta.traffic.point.TrafficSavedPointRegistry;
import net.minecraft.server.level.ServerLevel;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.holder.World;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemRailModifier;
import org.mtr.mod.packet.PacketUpdateData;

public class TrafficConnectorItem extends ItemRailModifier {
	private final TrafficPointType pointType;

	public TrafficConnectorItem(TrafficPointType pointType) {
		super(true, true, true, false, RailType.SIDING, new ItemSettings());
		this.pointType = pointType;
	}

	@Override
	protected void onConnect(World world, ItemStack itemStack, TransportMode transportMode, BlockState state1, BlockState state2, BlockPos pos1, BlockPos pos2, Angle angle1, Angle angle2, ServerPlayerEntity player) {
		final Rail rail = createRail(player == null ? null : player.getUuid(), transportMode, state1, state2, pos1, pos2, angle1, angle2);
		if (rail == null) {
			return;
		}

		final Rail styledRail = Rail.copy(rail, ObjectArrayList.of(TrafficConnectorStyles.DEFAULT_STYLE, TrafficConnectorStyles.styleFor(pointType)));

		world.setBlockState(pos1, state1.with(new Property<>(BlockNode.IS_CONNECTED.data), true));
		world.setBlockState(pos2, state2.with(new Property<>(BlockNode.IS_CONNECTED.data), true));
		PacketUpdateData.sendDirectlyToServerRail(ServerWorld.cast(world), styledRail);
		TrafficSavedPointRegistry.createConnectorPoint((ServerLevel) ServerWorld.cast(world).data, pointType, new net.minecraft.core.BlockPos(pos1.getX(), pos1.getY(), pos1.getZ()), new net.minecraft.core.BlockPos(pos2.getX(), pos2.getY(), pos2.getZ()));
	}
}
