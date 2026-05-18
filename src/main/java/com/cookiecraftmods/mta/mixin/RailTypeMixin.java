package com.cookiecraftmods.mta.mixin;

import com.cookiecraftmods.mta.traffic.point.connector.TrafficConnectorStyles;
import org.mtr.core.data.Rail;
import org.mtr.mod.data.RailType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RailType.class)
public class RailTypeMixin {
	@Inject(method = "getRailColor", at = @At("HEAD"), cancellable = true, remap = false)
	private static void mta$overrideTrafficConnectorRailColor(Rail rail, CallbackInfoReturnable<Integer> cir) {
		if (rail == null) {
			return;
		}

		//Do something with the goddamn color
		if (rail.getStyles().contains(TrafficConnectorStyles.SPAWN_STYLE)) {
			cir.setReturnValue(TrafficConnectorStyles.SPAWN_COLOR);
		} else if (rail.getStyles().contains(TrafficConnectorStyles.DESPAWN_STYLE)) {
			cir.setReturnValue(TrafficConnectorStyles.DESPAWN_COLOR);
		}
	}
}
