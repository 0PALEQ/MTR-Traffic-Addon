package com.cookiecraftmods.mta.mixin;

import com.cookiecraftmods.mta.traffic.TrafficManager;
import org.mtr.core.simulation.Simulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Simulator.class, remap = false)
public class SimulatorMixin {
	@Inject(method = "tick()V", at = @At("RETURN"), remap = false)
	private void mta$publishFullRailGraph(CallbackInfo ci) {
		final Simulator simulator = (Simulator) (Object) this;
		TrafficManager.updateFullMtrRailGraph(simulator.dimension, simulator.rails);
	}
}
