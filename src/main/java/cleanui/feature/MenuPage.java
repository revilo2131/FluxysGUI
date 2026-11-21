package cleanui.feature;

import cleanui.ui.Button;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A page drawn in place of one of Hypixel's chest menus. The real menu stays open underneath:
 * the page reads its items, and passes each click on as exactly one normal slot click.
 * If the menu can't be read it hands back the original screen, so the player is never stuck.
 */
public abstract class MenuPage extends Window {
	/** The vanilla screen the user asked to see; the menu hook leaves this one alone. */
	public static Screen showingOriginal;

	private static final long FALLBACK_MS = 2500;
	private static final Logger LOGGER = LoggerFactory.getLogger("cleanui");
	private static final Set<String> LOGGED_MENUS = new HashSet<>();
	/** Menu items that are navigation, not content. */
	protected static final Set<String> NAV = Set.of("Go Back", "Close", "Next Page", "Previous Page");

	protected final AbstractContainerScreen<?> original;
	private final long openedAt = System.currentTimeMillis();

	protected MenuPage(String title, ItemStack icon, int depth, AbstractContainerScreen<?> original) {
		super(title, icon, depth);
		this.original = original;
	}

	/** "Original menu" button: Hypixel's own menu instead of this page. */
	protected Button originalMenuButton(int x, int y) {
		return new Button(x, y, 86, 18, Component.literal("Original menu"), this::showOriginal)
				.tooltip(Component.literal("Show Hypixel's own menu instead.")).body();
	}

	protected void showOriginal() {
		showingOriginal = original;
		minecraft.setScreenAndShow(original);
	}

	/**
	 * Call once per frame with whether anything was read. After a while with nothing,
	 * switches to the original menu and returns true (stop drawing).
	 */
	protected boolean giveUpIfUnreadable(boolean nothingRead) {
		if (nothingRead && System.currentTimeMillis() - openedAt > FALLBACK_MS) {
			showOriginal();
			return true;
		}
		return false;
	}

	/** Back = the menu's own "Go Back" button; if it has none, /sbmenu (same as typing it). */
	@Override
	protected void goBack() {
		for (MenuItem item : menuItems())
			if (item.name().equals("Go Back")) {
				click(item.slot(), 0);
				return;
			}
		minecraft.getConnection().sendCommand("sbmenu");
	}

	/**
	 * Logs every item in this menu (name + tooltip) once per menu title per session, so the
	 * exact wording can be checked in latest.log when a page doesn't read a menu right.
	 */
	protected void logMenuOnce(List<MenuItem> items) {
		String title = getTitle().getString() + " / " + original.getTitle().getString();
		// wait a second: Hypixel fills big menus over several packets, the first frame sees only part
		if (items.isEmpty() || System.currentTimeMillis() - openedAt < 1000 || !LOGGED_MENUS.add(title)) return;
		StringBuilder sb = new StringBuilder("[Clean UI] Menu '" + original.getTitle().getString() + "':");
		for (MenuItem item : items) sb.append("\n  slot ").append(item.slot()).append(" '").append(item.name()).append("' ").append(item.lore());
		LOGGER.info(sb.toString());
	}

	private static final java.util.Map<String, Long> FIRST_SEEN = new java.util.HashMap<>();

	/**
	 * Same log for menus without a page (called every frame from the chest screen), so their
	 * layout is known before a page is built. Waits a second after the menu is first seen.
	 */
	public static void logUnpaged(String title, net.minecraft.world.inventory.AbstractContainerMenu menu, net.minecraft.world.Container playerInventory) {
		if (LOGGED_MENUS.contains(title)) return;
		long first = FIRST_SEEN.computeIfAbsent(title, k -> System.currentTimeMillis());
		if (System.currentTimeMillis() - first < 1000) return;
		LOGGED_MENUS.add(title);
		StringBuilder sb = new StringBuilder("[Clean UI] Menu '" + title + "':");
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			if (slot.container == playerInventory || slot.getItem().isEmpty()) continue;
			MenuItem item = new MenuItem(i, slot.getItem());
			sb.append("\n  slot ").append(i).append(" '").append(item.name()).append("' ").append(item.lore());
		}
		LOGGER.info(sb.toString());
	}

	/** One click on that menu slot as the player made it: shift-click stays a shift-click. */
	protected void clickAs(int menuSlot, net.minecraft.client.input.MouseButtonEvent event) {
		boolean shift = (event.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
		minecraft.gameMode.handleContainerInput(original.getMenu().containerId, menuSlot, event.button(),
				shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, minecraft.player);
	}

	/** Exactly one normal click on that menu slot, same as clicking it in the chest. */
	protected void click(int menuSlot, int button) {
		minecraft.gameMode.handleContainerInput(original.getMenu().containerId, menuSlot, button, ContainerInput.PICKUP, minecraft.player);
	}

	/** The menu's own slots (not the player inventory), as (menu slot index, item) pairs. */
	protected List<MenuItem> menuItems() {
		List<MenuItem> list = new ArrayList<>();
		List<Slot> slots = original.getMenu().slots;
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			if (slot.container == minecraft.player.getInventory() || slot.getItem().isEmpty()) continue;
			list.add(new MenuItem(i, slot.getItem()));
		}
		return list;
	}

	protected record MenuItem(int slot, ItemStack stack) {
		String name() {
			return ChatFormatting.stripFormatting(stack.getHoverName().getString()).strip();
		}

		List<String> lore() {
			ItemLore lore = stack.get(DataComponents.LORE);
			List<String> lines = new ArrayList<>();
			if (lore != null) for (Component c : lore.lines()) lines.add(ChatFormatting.stripFormatting(c.getString()));
			return lines;
		}
	}

	@Override
	protected void closed() {
		minecraft.player.closeContainer(); // closes the real menu on the server too
	}
}
