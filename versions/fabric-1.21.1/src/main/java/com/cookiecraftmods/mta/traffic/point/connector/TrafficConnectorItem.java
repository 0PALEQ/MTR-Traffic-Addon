package com.cookiecraftmods.mta.traffic.point.connector;

import com.cookiecraftmods.mta.traffic.point.TrafficPointType;
import com.cookiecraftmods.mta.traffic.point.TrafficSavedPointRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.mtr.block.BlockNode;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.data.RailType;
import org.mtr.item.ItemRailModifier;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.packet.PacketUpdateData;

public class TrafficConnectorItem extends ItemRailModifier {
	private final TrafficPointType pointType;

	public TrafficConnectorItem(TrafficPointType pointType) {
		super(true, true, true, false, RailType.SIDING, new Item.Properties());
		this.pointType = pointType;
	}

	@Override
	protected void onConnect(Level world, ItemStack itemStack, TransportMode transportMode, BlockState state1, BlockState state2, BlockPos pos1, BlockPos pos2, Angle angle1, Angle angle2, ServerPlayer player) {
		final Rail rail = createRail(player == null ? null : player.getUUID(), transportMode, state1, state2, pos1, pos2, angle1, angle2);
		if (rail == null) {
			return;
		}

		final Rail styledRail = Rail.copy(rail, ObjectArrayList.of(TrafficConnectorStyles.DEFAULT_STYLE, TrafficConnectorStyles.styleFor(pointType)));

		world.setBlockAndUpdate(pos1, state1.setValue(BlockNode.IS_CONNECTED, true));
		world.setBlockAndUpdate(pos2, state2.setValue(BlockNode.IS_CONNECTED, true));
		PacketUpdateData.sendDirectlyToServerRail((ServerLevel) world, styledRail);
		TrafficSavedPointRegistry.createConnectorPoint((ServerLevel) world, pointType, pos1, pos2);
	}
}
