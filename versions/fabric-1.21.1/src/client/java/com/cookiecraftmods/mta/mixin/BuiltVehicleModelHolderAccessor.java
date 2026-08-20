package com.cookiecraftmods.mta.mixin;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.model.BuiltVehicleModelHolder;
import org.mtr.model.NewOptimizedModel;
import org.mtr.resource.PartCondition;
import org.mtr.resource.RenderStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BuiltVehicleModelHolder.class, remap = false)
public interface BuiltVehicleModelHolderAccessor {
	@Accessor("builtModels")
	Object2ObjectOpenHashMap<PartCondition, Object2ObjectOpenHashMap<RenderStage, ObjectArrayList<NewOptimizedModel>>> mta$getBuiltModels();

	@Accessor("builtDoorModelDetailsList")
	ObjectArrayList<BuiltVehicleModelHolder.BuiltDoorModelDetails> mta$getBuiltDoorModelDetailsList();
}
