package com.cookiecraftmods.mta.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PathData.class, remap = false)
public class PathDataMixin {
	// PathData endpoints and Position coordinates are final, including after updateData.
	// Keep the cache on the path so discarded routes don't remain in a global map.
	@Unique
	private volatile String mta$forwardHexId;
	@Unique
	private volatile String mta$reverseHexId;

	@WrapOperation(method = "getHexId", at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/TwoPositionsBase;getHexIdRaw(Lorg/mtr/core/data/Position;Lorg/mtr/core/data/Position;)Ljava/lang/String;"), require = 2)
	private String mta$cachedHexId(Position from, Position to, Operation<String> original, boolean reverse) {
		String id = reverse ? mta$reverseHexId : mta$forwardHexId;
		if (id == null) {
			id = original.call(from, to);
			if (reverse) {
				mta$reverseHexId = id;
			} else {
				mta$forwardHexId = id;
			}
		}
		return id;
	}
}
