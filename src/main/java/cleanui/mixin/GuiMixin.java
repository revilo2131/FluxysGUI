package cleanui.mixin;

import cleanui.CleanUi;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps a Hypixel menu for its Clean UI page before it is ever shown, so moving between
 * menus never draws a frame of the plain chest screen (the "black flicker").
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen cleanui$swapForPage(Screen screen) {
		return CleanUi.swap(screen);
	}
}
