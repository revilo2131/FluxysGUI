package cleanui.mixin;

import cleanui.feature.NucleusRun;
import cleanui.feature.ScathaSlots;
import cleanui.feature.VanguardSnake;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nucleus finale (beams, loot, stats) and the Vanguard snake: the whole HUD (vanilla and other
 * mods' parts) is replaced by the animation over a blurred world. Done here rather than with a
 * screen, so you can still walk.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void cleanui$takeover(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
		boolean nucleus = NucleusRun.takeover(), snake = VanguardSnake.takeover();
		if (!nucleus && !snake) return;
		// one blur per frame: an open menu does its own, so only blur when none is open
		if (Minecraft.getInstance().gui.screen() == null) g.blurBeforeThisStratum();
		if (nucleus) NucleusRun.render(g);
		if (snake) VanguardSnake.render(g);
		ScathaSlots.render(g); // confetti
		ci.cancel();
	}
}
