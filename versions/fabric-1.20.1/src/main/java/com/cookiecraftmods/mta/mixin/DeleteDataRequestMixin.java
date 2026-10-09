package com.cookiecraftmods.mta.mixin;

import com.cookiecraftmods.mta.traffic.TrafficManager;
import org.mtr.core.operation.DeleteDataRequest;
import org.mtr.core.operation.DeleteDataResponse;
import org.mtr.core.simulation.Simulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DeleteDataRequest.class, remap = false)
public abstract class DeleteDataRequestMixin {
	@Inject(method = "delete(Lorg/mtr/core/simulation/Simulator;)Lorg/mtr/core/operation/DeleteDataResponse;", at = @At("RETURN"), remap = false)
	private void mta$removeDeletedConnectors(Simulator simulator, CallbackInfoReturnable<DeleteDataResponse> cir) {
		TrafficManager.onMtrRailsDeleted(simulator.dimension,
			((DeleteDataResponseAccessor) (Object) cir.getReturnValue()).mta$getDeletedRailIds());
	}
}
