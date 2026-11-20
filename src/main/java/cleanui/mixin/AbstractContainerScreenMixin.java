package cleanui.mixin;

import cleanui.feature.MenuStyle;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Labels and item tooltips for restyled Hypixel chest menus. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
	@Shadow protected int titleLabelX;
	@Shadow protected int titleLabelY;
	@Shadow protected int inventoryLabelX;
	@Shadow protected int inventoryLabelY;
	@Shadow @Final protected Component playerInventoryTitle;

	private AbstractContainerScreenMixin(Component title) {
		super(title);
	}

	/** Title in the theme's title color, "Inventory" muted; vanilla's dark gray is unreadable on navy. */
	@Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
	private void cleanui$labels(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		if (!MenuStyle.active(this)) return;
		g.text(font, title, titleLabelX, titleLabelY, Theme.argb(Theme.TITLE, 1f), true);
		g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Theme.argb(Theme.TEXT_MUTED, 1f), true);
		ci.cancel();
	}

	/** Item tooltips get the Clean UI frame unless the item sets its own style. */
	@ModifyArg(method = "extractTooltip", index = 5, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V"))
	private Identifier cleanui$tooltipStyle(Identifier style) {
		return style == null && MenuStyle.active(this) ? Theme.tooltipStyle() : style;
	}
}
