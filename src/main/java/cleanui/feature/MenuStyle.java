package cleanui.feature;

import cleanui.CleanUi;
import cleanui.data.Config;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Clean UI look for every other Hypixel chest menu (Wardrobe, Trades, Bank, Recipe Book...).
 * Only drawing changes: the screen is still Minecraft's own chest screen, so clicking,
 * dragging and shift-clicking work exactly as before. Used by the chest screen mixins.
 */
public final class MenuStyle {
	private MenuStyle() {}

	public static boolean active(Screen screen) {
		return screen instanceof ContainerScreen && screen != MenuPage.showingOriginal && Config.get().restyleMenus
				&& (CleanUi.onHypixel() || Boolean.getBoolean("cleanui.preview"));
	}

	/** Covers the chest texture with the Clean UI panel and draws a framed box behind every slot. */
	public static void background(GuiGraphicsExtractor g, int left, int top, int w, int h, AbstractContainerMenu menu, Container playerInventory) {
		g.fill(left - 2, top - 2, left + w + 2, top + h + 2, Theme.argb(Theme.PANEL, 1f)); // hide the vanilla texture
		Theme.panel(g, left - 2, top - 2, w + 4, h + 4, 1f);
		for (Slot slot : menu.slots) {
			boolean own = slot.container == playerInventory;
			Theme.box(g, left + slot.x - 1, top + slot.y - 1, 18, 18, Theme.SLOT, 1f,
					own ? Theme.LINE : Theme.mix(Theme.LINE, Theme.FRAME, 0.3f), 1f);
		}
	}
}
