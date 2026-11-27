package cleanui.mixin;

import cleanui.feature.HandAnim;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hand animations: your own swing faster or slower (client-side, looks only). */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
	private void cleanui$swingSpeed(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(HandAnim.swingDuration(this, cir.getReturnValue()));
	}
}
