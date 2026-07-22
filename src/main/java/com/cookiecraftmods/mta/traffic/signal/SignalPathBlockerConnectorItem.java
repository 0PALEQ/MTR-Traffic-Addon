package com.cookiecraftmods.mta.traffic.signal;

import net.minecraft.network.chat.Component;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.holder.TooltipContext;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.item.ItemSignalModifier;
import org.mtr.mod.packet.PacketUpdateData;

import java.util.List;

public final class SignalPathBlockerConnectorItem extends ItemSignalModifier {
	public SignalPathBlockerConnectorItem() {
		// The color is unused because this item overrides the native signal modification.
		super(true, 0, new ItemSettings());
	}

	@Override
	public void addTooltips(ItemStack itemStack, World world, List<MutableText> tooltips, TooltipContext tooltipContext) {
		super.addTooltips(itemStack, world, tooltips, tooltipContext);
		tooltips.add(TextHelper.translatable("tooltip.mtr-traffic-addon.signal_path_blocker_connector.select"));
		tooltips.add(TextHelper.translatable("tooltip.mtr-traffic-addon.signal_path_blocker_connector.toggle"));
	}

	@Override
	protected void onConnect(World world, ItemStack itemStack, TransportMode transportMode, BlockState state1, BlockState state2, BlockPos pos1, BlockPos pos2, Angle angle1, Angle angle2, ServerPlayerEntity player) {
		getRail(world, pos1, pos2, player, rail -> toggleRail(world, rail, player));
	}

	private static void toggleRail(World world, Rail rail, ServerPlayerEntity player) {
		final boolean blocked = !SignalPathBlocker.isBlocked(rail);
		PacketUpdateData.sendDirectlyToServerRail(ServerWorld.cast(world), SignalPathBlocker.copyWithBlockedState(rail, blocked));
		if (player != null) {
			player.data.displayClientMessage(Component.translatable(blocked
				? "message.mtr-traffic-addon.signal_path_blocker_connector.blocked"
				: "message.mtr-traffic-addon.signal_path_blocker_connector.unblocked"), true);
		}
	}
}
