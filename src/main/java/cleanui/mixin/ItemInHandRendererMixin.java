package cleanui.mixin;

import cleanui.feature.HandAnim;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hand animations: redraw the main-hand item with a swing of 0, then add ours just before it's drawn. */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
	@Unique
	private boolean cleanui$redrawing;

	@Shadow
	private void submitArmWithItem(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
	                               ItemStack stack, float equipProgress, PoseStack pose, SubmitNodeCollector nodes, int light) {}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void cleanui$redraw(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
	                            ItemStack stack, float equipProgress, PoseStack pose, SubmitNodeCollector nodes, int light, CallbackInfo ci) {
		if (cleanui$redrawing || !HandAnim.applies(player, hand, stack)) return;
		ci.cancel();
		cleanui$redrawing = true;
		try {
			float equip = HandAnim.begin(player, swingProgress, stack, equipProgress);
			submitArmWithItem(player, partialTick, pitch, hand, 0f, stack, equip, pose, nodes, light);
		} finally {
			cleanui$redrawing = false;
			HandAnim.end();
		}
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	private void cleanui$animate(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
	                             ItemStack stack, float equipProgress, PoseStack pose, SubmitNodeCollector nodes, int light, CallbackInfo ci) {
		if (cleanui$redrawing) HandAnim.apply(pose);
	}
}
