package com.cookiecraftmods.mta.traffic.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.item.ItemSignalModifier;
import org.mtr.packet.PacketUpdateData;

import java.util.List;

public final class SignalPathBlockerConnectorItem extends ItemSignalModifier {
	private final String style;
	private final String translationPath;

	public SignalPathBlockerConnectorItem(String style, String translationPath) {
		// The color is unused because this item overrides the native signal modification.
		super(true, 0, new Item.Properties());
		this.style = style;
		this.translationPath = translationPath;
	}

	@Override
	public void appendHoverText(ItemStack itemStack, Item.TooltipContext tooltipContext, List<Component> tooltips, TooltipFlag tooltipFlag) {
		super.appendHoverText(itemStack, tooltipContext, tooltips, tooltipFlag);
		tooltips.add(Component.translatable("tooltip.mtr-traffic-addon." + translationPath + ".select"));
		tooltips.add(Component.translatable("tooltip.mtr-traffic-addon." + translationPath + ".toggle"));
	}

	@Override
	protected void onConnect(Level world, ItemStack itemStack, TransportMode transportMode, BlockState state1, BlockState state2, BlockPos pos1, BlockPos pos2, Angle angle1, Angle angle2, ServerPlayer player) {
		getRail(world, pos1, pos2, player, rail -> toggleRail(world, rail, player));
	}

	private void toggleRail(Level world, Rail rail, ServerPlayer player) {
		final boolean blocked = !SignalPathBlocker.isBlocked(rail, style);
		PacketUpdateData.sendDirectlyToServerRail((ServerLevel) world, SignalPathBlocker.copyWithBlockedState(rail, style, blocked));
		if (player != null) {
			player.displayClientMessage(Component.translatable(blocked
				? "message.mtr-traffic-addon." + translationPath + ".blocked"
				: "message.mtr-traffic-addon." + translationPath + ".unblocked"), true);
		}
	}
}
