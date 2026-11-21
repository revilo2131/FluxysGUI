package cleanui.mixin;

import cleanui.feature.MenuStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the Clean UI panel over the chest texture of restyled Hypixel menus. */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin extends AbstractContainerScreen<ChestMenu> {
	private ContainerScreenMixin(ChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void cleanui$restyle(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (MenuStyle.active(this)) {
			MenuStyle.background(g, leftPos, topPos, imageWidth, imageHeight, menu, minecraft.player.getInventory());
			cleanui.feature.MenuPage.logUnpaged(title.getString(), menu, minecraft.player.getInventory()); // record its layout once
		}
	}
}
