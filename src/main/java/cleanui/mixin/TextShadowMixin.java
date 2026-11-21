package cleanui.mixin;

import cleanui.ui.CleanScreen;
import cleanui.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Light themes (Parchment) draw dark ink; Minecraft's drop shadow under dark text smears it. While a
 * Clean UI page is open and the theme is light, text is drawn without the shadow. Nothing else changes.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class TextShadowMixin {
	@ModifyVariable(method = {
			"text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
			"text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"},
			at = @At("HEAD"), argsOnly = true)
	private boolean cleanui$noShadowOnLightThemes(boolean shadow) {
		return shadow && (Theme.textShadow || !(Minecraft.getInstance().gui.screen() instanceof CleanScreen));
	}
}
