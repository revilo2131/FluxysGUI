package cleanui.feature;

import cleanui.skyblock.TrackParser;
import cleanui.ui.Anim;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel's Wardrobe: each of the page's 9 loadouts as a card with you (your skin) wearing that
 * armor, coloured by state: green equipped, blue ready, red empty, dark when locked. Clicking a
 * card clicks that loadout's button in the real menu (equip / unequip). Layout per Hypixel: armor
 * in rows 0-3 (one column per loadout), the loadout buttons ("Slot 2: Ready") in row 4.
 */
public final class WardrobeScreen extends MenuPage {
	private static final Pattern SLOT = Pattern.compile("^Slot (\\d+): (\\w+)");
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int COLS = 9, GAP = 4, BUTTON_ROW = 4, BUTTON_H = 18;
	private static final int EQUIPPED = 0x4CC36A, READY = 0x4F7BD9, EMPTY = 0xC0504D, LOCKED = 0x3A4150;

	private enum State { EQUIPPED, READY, EMPTY, LOCKED }

	private final Map<Integer, SkinModel> models = new HashMap<>();
	private final Map<Integer, Anim> hover = new HashMap<>();
	private int prevSlot = -1, nextSlot = -1;

	public WardrobeScreen(AbstractContainerScreen<?> original) {
		super("Wardrobe", new ItemStack(Items.LEATHER_CHESTPLATE), 1, original);
	}

	public static WardrobeScreen forTitle(String title, AbstractContainerScreen<?> original) {
		return title.startsWith("Wardrobe") ? new WardrobeScreen(original) : null;
	}

	@Override
	protected String hint() {
		return "Click a loadout to equip it.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(originalMenuButton(bx() + BW - 86, by() + BH - BUTTON_H));
	}

	private static int cardW() {
		return (BW - (COLS - 1) * GAP) / COLS;
	}

	private int cardX(int col) {
		return bx() + col * (cardW() + GAP);
	}

	private static int cardH() {
		return BH - BUTTON_H - 6;
	}

	/** The real menu's item in a slot, or null if empty / not there. */
	private MenuItem at(Map<Integer, MenuItem> bySlot, int slot) {
		return bySlot.get(slot);
	}

	private static State state(MenuItem button) {
		if (button == null) return State.LOCKED;
		Matcher m = SLOT.matcher(button.name());
		String s = m.find() ? m.group(2) : "";
		return switch (s) {
			case "Equipped" -> State.EQUIPPED;
			case "Ready" -> State.READY;
			case "Empty" -> State.EMPTY;
			case "Locked" -> State.LOCKED;
			// unknown wording: ready if it can be clicked to equip, else empty
			default -> button.lore().stream().anyMatch(l -> l.contains("equip")) ? State.READY : State.EMPTY;
		};
	}

	private static int color(State s) {
		return switch (s) {
			case EQUIPPED -> EQUIPPED;
			case READY -> READY;
			case EMPTY -> EMPTY;
			case LOCKED -> LOCKED;
		};
	}

	/** "Slot 12: Ready" → "12"; else the column's number on this page. */
	private static String number(MenuItem button, int col) {
		if (button != null) {
			Matcher m = SLOT.matcher(button.name());
			if (m.find()) return m.group(1);
		}
		return String.valueOf(col + 1);
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Map<Integer, MenuItem> bySlot = new HashMap<>();
		var items = menuItems();
		logMenuOnce(items);
		prevSlot = nextSlot = -1;
		for (MenuItem i : items) {
			bySlot.put(i.slot(), i);
			if (i.name().equals("Previous Page")) prevSlot = i.slot();
			if (i.name().equals("Next Page")) nextSlot = i.slot();
		}
		if (giveUpIfUnreadable(items.isEmpty())) return;
		MenuItem tooltip = null;
		int w = cardW(), h = cardH(), y = by();
		for (int col = 0; col < COLS; col++) {
			MenuItem button = at(bySlot, BUTTON_ROW * 9 + col);
			State st = state(button);
			int x = cardX(col), color = color(st);
			boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
			if (over && button != null) tooltip = button;
			Anim hv = hover.computeIfAbsent(col, k -> new Anim(0f, 18f));
			hv.target(over ? 1f : 0f);
			float hf = hv.get();
			int lift = Math.round(2 * hf);
			Theme.box(g, x, y - lift, w, h, Theme.mix(Theme.mix(Theme.ROW, color, st == State.LOCKED ? 0.15f : 0.38f), 0xFFFFFF, 0.06f * hf), a,
					Theme.mix(Theme.mix(color, Theme.LINE, 0.3f), 0xFFFFFF, 0.35f * hf), a);
			if (st == State.EQUIPPED) g.fill(x + 1, y - lift + 1, x + w - 1, y - lift + 3, Theme.argb(EQUIPPED, a)); // cap on the one you wear

			if (st != State.LOCKED) {
				SkinModel model = models.computeIfAbsent(col, k -> new SkinModel(minecraft.level, minecraft.player.getGameProfile()));
				boolean any = false;
				for (int piece = 0; piece < 4; piece++) {
					MenuItem it = at(bySlot, piece * 9 + col);
					boolean real = it != null && !TrackParser.isPane(BuiltInRegistries.ITEM.getKey(it.stack().getItem()).toString());
					model.setItemSlot(ARMOR[piece], real ? it.stack().copy() : ItemStack.EMPTY);
					any |= real;
					if (real && over && mouseY >= y + 8 + piece * 30 && mouseY < y + 38 + piece * 30) tooltip = it; // hover a piece's height for its tooltip
				}
				// models can't fade; they appear once the frame is mostly in. Empty loadouts are just you, dimmed by the card.
				if (a > 0.5f)
					InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 2, y - lift + 6, x + w - 2, y - lift + h - 20, 46, 0.0625f, mouseX, mouseY, model);
				if (!any && st == State.EMPTY) g.fill(x + 1, y - lift + 1, x + w - 1, y - lift + h - 20, Theme.argb(Theme.PANEL, a * 0.45f));
			} else {
				String lock = "LOCKED";
				g.text(font, Theme.fit(font, lock, w - 4), x + (w - Math.min(font.width(lock), w - 4)) / 2, y + h / 2 - 4, Theme.argb(Theme.TEXT_MUTED, a), true);
			}
			String num = number(button, col);
			g.text(font, num, x + (w - font.width(num)) / 2, y - lift + h - 14, Theme.argb(st == State.LOCKED ? Theme.TEXT_MUTED : Theme.TITLE, a), true);
		}

		// paging: ‹ Page 1/2 ›, from the menu title "Wardrobe (1/2)"
		String page = original.getTitle().getString().replaceAll("^.*\\((\\d+/\\d+)\\).*$", "$1");
		int py = by() + BH - BUTTON_H;
		pageButton(g, bx(), py, "‹", prevSlot >= 0, mouseX, mouseY, a);
		String label = page.contains("/") ? "Page " + page : "";
		g.text(font, label, bx() + 28, py + 5, Theme.argb(Theme.TEXT_MUTED, a), true);
		pageButton(g, bx() + 34 + font.width(label), py, "›", nextSlot >= 0, mouseX, mouseY, a);
		if (tooltip != null) Theme.itemTooltip(g, tooltip.stack(), mouseX, mouseY);
	}

	private void pageButton(GuiGraphicsExtractor g, int x, int y, String arrow, boolean on, int mouseX, int mouseY, float a) {
		boolean over = on && mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + BUTTON_H;
		Theme.box(g, x, y, 22, BUTTON_H, over ? Theme.ROW_HOVER : Theme.ROW, a, on ? (over ? Theme.FRAME : Theme.LINE) : Theme.SLOT, a);
		g.text(font, arrow, x + (22 - font.width(arrow)) / 2, y + 5, Theme.argb(on ? Theme.TEXT : Theme.TEXT_MUTED, a), true);
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		int py = by() + BH - BUTTON_H;
		String page = original.getTitle().getString().replaceAll("^.*\\((\\d+/\\d+)\\).*$", "$1");
		int nextX = bx() + 34 + font.width(page.contains("/") ? "Page " + page : "");
		if (event.y() >= py && event.y() < py + BUTTON_H) {
			if (prevSlot >= 0 && event.x() >= bx() && event.x() < bx() + 22) { click(prevSlot, 0); return true; }
			if (nextSlot >= 0 && event.x() >= nextX && event.x() < nextX + 22) { click(nextSlot, 0); return true; }
		}
		int w = cardW(), h = cardH();
		for (int col = 0; col < COLS; col++) {
			int x = cardX(col);
			if (event.x() >= x && event.x() < x + w && event.y() >= by() && event.y() < by() + h) {
				// the loadout's own button (equip / unequip) in the real menu
				clickAs(BUTTON_ROW * 9 + col, event);
				return true;
			}
		}
		return false;
	}
}
