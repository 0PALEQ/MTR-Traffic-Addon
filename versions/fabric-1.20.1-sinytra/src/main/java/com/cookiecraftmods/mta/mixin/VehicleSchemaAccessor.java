package com.cookiecraftmods.mta.mixin;

import org.mtr.core.generated.data.VehicleSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VehicleSchema.class, remap = false)
public interface VehicleSchemaAccessor {
	@Accessor("railProgress")
	double mta$getRailProgress();

	@Accessor("speed")
	double mta$getSpeed();
}
