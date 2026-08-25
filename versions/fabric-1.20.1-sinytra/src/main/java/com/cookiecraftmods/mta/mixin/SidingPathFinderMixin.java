package com.cookiecraftmods.mta.mixin;

import com.cookiecraftmods.mta.traffic.signal.SignalPathBlocker;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SidingPathFinder.class)
public abstract class SidingPathFinderMixin {
	@Inject(method = "lambda$getConnections$0", at = @At("HEAD"), cancellable = true, remap = false)
	private static void mta$skipPathBlockedRail(
		@Coerce Object currentPositionAndAngle,
		ObjectArrayList<?> connections,
		Position nextPosition,
		Rail rail,
		CallbackInfo ci
	) {
		if (SignalPathBlocker.isBlocked(rail, SignalPathBlocker.MTR_STYLE)) {
			ci.cancel();
		}
	}
}
