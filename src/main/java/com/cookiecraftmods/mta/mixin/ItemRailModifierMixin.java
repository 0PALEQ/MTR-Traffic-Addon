package com.cookiecraftmods.mta.mixin;

import com.cookiecraftmods.mta.traffic.rail.MtaExclusiveRailRegistry;
import net.minecraft.server.level.ServerLevel;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.holder.World;
import org.mtr.mod.item.ItemRailModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRailModifier.class)
public class ItemRailModifierMixin {
	@Inject(method = "onRemove", at = @At("HEAD"), remap = false)
	private void mta$removeExclusiveTrafficRail(World world, BlockPos pos1, BlockPos pos2, ServerPlayerEntity player, CallbackInfo ci) {
		MtaExclusiveRailRegistry.removeRail(
			(ServerLevel) ServerWorld.cast(world).data,
			new net.minecraft.core.BlockPos(pos1.getX(), pos1.getY(), pos1.getZ()),
			new net.minecraft.core.BlockPos(pos2.getX(), pos2.getY(), pos2.getZ())
		);
	}
}
