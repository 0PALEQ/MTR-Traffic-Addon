package com.cookiecraftmods.mta.mixin;

import com.cookiecraftmods.mta.compat.MagicCompat;
import com.cookiecraftmods.mta.traffic.TrafficManager;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.data.VehiclePosition;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Vehicle.class)
public abstract class VehicleMixin {
	@Shadow
	@Final
	public VehicleExtraData vehicleExtraData;

	@Inject(method = "railBlockedDistance", at = @At("RETURN"), cancellable = true, remap = false)
	private void mta$includeTrafficVehicles(
		int startIndex,
		double railProgress,
		double additionalDistance,
		ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions,
		boolean preReserve,
		boolean doNotReserve,
		CallbackInfoReturnable<Double> cir
	) {
		if (MagicCompat.shouldSuppressMtrTrafficBlockersForCurrentCall(additionalDistance, preReserve, doNotReserve)) {
			return;
		}

		final List<PathData> path = vehicleExtraData == null ? List.of() : vehicleExtraData.immutablePath;
		final double mtaBlockedDistance = TrafficManager.mtrVehicleBlockedDistance(path, startIndex, railProgress, additionalDistance, 4);
		if (mtaBlockedDistance < 0.0D) {
			return;
		}

		final double mtrBlockedDistance = cir.getReturnValueD();
		if (mtrBlockedDistance < 0.0D || mtaBlockedDistance < mtrBlockedDistance) {
			cir.setReturnValue(mtaBlockedDistance);
		}
	}

	@Inject(method = "simulate", at = @At("RETURN"), remap = false)
	private void mta$recordMtrVehicle(
		long elapsed,
		ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions,
		Long2LongAVLTreeMap currentlyRidingEntities,
		CallbackInfo ci
	) {
		final Vehicle vehicle = (Vehicle) (Object) this;
		final VehicleSchemaAccessor vehicleSchemaAccessor = (VehicleSchemaAccessor) this;
		final List<PathData> path = vehicleExtraData == null ? List.of() : vehicleExtraData.immutablePath;
		final double vehicleLengthMeters = vehicleExtraData == null ? 0.0D : vehicleExtraData.getTotalVehicleLength();
		TrafficManager.recordMtrVehicle(vehicle.getId(), path, vehicleSchemaAccessor.mta$getRailProgress(), vehicleSchemaAccessor.mta$getSpeed(), vehicleLengthMeters);
	}
}
