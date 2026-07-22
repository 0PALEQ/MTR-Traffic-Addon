package com.cookiecraftmods.mta.mixin;

import com.cookiecraftmods.mta.traffic.signal.SignalPathBlocker;
import org.mtr.core.data.Rail;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BiConsumer;

@Mixin(SidingPathFinder.class)
public abstract class SidingPathFinderMixin {
	@Redirect(
		method = "getConnections(JLorg/mtr/core/path/SidingPathFinder$PositionAndAngle;Ljava/lang/Long;)Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;",
		at = @At(
			value = "INVOKE",
			target = "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap;forEach(Ljava/util/function/BiConsumer;)V"
		),
		remap = false
	)
	@SuppressWarnings({"rawtypes", "unchecked"})
	private void mta$skipPathBlockedRails(Object2ObjectOpenHashMap rails, BiConsumer consumer) {
		rails.forEach((position, value) -> {
			if (!(value instanceof Rail rail) || !SignalPathBlocker.isBlocked(rail)) {
				consumer.accept(position, value);
			}
		});
	}
}
