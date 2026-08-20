package com.cookiecraftmods.mta.mixin;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.generated.data.RailSchema;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RailSchema.class, remap = false)
public interface RailSchemaAccessor {
	@Accessor("position1")
	Position mta$getPosition1();

	@Accessor("angle1")
	Angle mta$getAngle1();

	@Accessor("position2")
	Position mta$getPosition2();

	@Accessor("angle2")
	Angle mta$getAngle2();

	@Accessor("shape")
	Rail.Shape mta$getShape();

	@Accessor("verticalRadius")
	double mta$getVerticalRadius();

	@Accessor("speedLimit1")
	long mta$getSpeedLimit1();

	@Accessor("speedLimit2")
	long mta$getSpeedLimit2();

	@Accessor("signalColors")
	LongArrayList mta$getSignalColors();
}
