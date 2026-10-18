package com.cookiecraftmods.mta.mixin;

import org.mtr.core.generated.operation.DeleteDataResponseSchema;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DeleteDataResponseSchema.class, remap = false)
public interface DeleteDataResponseAccessor {
	@Accessor("railIds")
	ObjectArrayList<String> mta$getDeletedRailIds();
}
