package cleanui.feature;

import cleanui.data.SkyblockItems;
import cleanui.skyblock.LoadoutParser;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hypixel's "Loadouts" menu. Left: what you're wearing now (you in that armor, your equipment,
 * pet and the menu's action buttons). Right: every loadout as a card with you wearing its armor,
 * the one in use green, empty ones grey. Clicks go to the real menu (left: equip, right: edit).
 * <p>
 * Layout from the real menu (game log, 2026-11-21): action buttons in column 0, equipment in
 * column 1, armor in column 2 (helmet to boots), pet in column 3, loadouts in columns 5-7. Each
 * loadout lists its armor by name ("Helmet: Necrotic Wise Dragon Helmet"); the actual items come
 * from Hypixel's public item list ({@link SkyblockItems}).
 */
public final class LoadoutsScreen extends MenuPage {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int LEFT_W = 150, GRID_X = LEFT_W + 10, GRID_W = BW - GRID_X, GRID_COLS = 6, GAP = 4, BUTTON_H = 18;
	private static final int ACTIVE = 0x4CC36A, READY = 0x4F7BD9, EMPTY = 0x3A4150;

	private static final long BRIDGE_MS = 1500, PRESS_MS = 220;
	private final Map<Integer, MenuItem> lastLoadouts = new HashMap<>();
	private final Map<Integer, Long> lastSeen = new HashMap<>(), pressedAt = new HashMap<>();
	private final Map<Integer, SkinModel> models = new HashMap<>();
	/**
	 * The loadout you just picked: green straight away, since Hypixel may not update the menu's text
	 * after equipping. Dropped as soon as the menu names a different loadout in use than before the click.
	 */
	private int pendingSlot = -1, activeAtClick = -1;
	private long clickLogAt = -1;
	private final Map<Integer, Anim> hover = new HashMap<>();

	/** What the menu holds, sorted by column. */
	private record Layout(List<MenuItem> actions, List<MenuItem> equipment, ItemStack[] armor, List<MenuItem> armorItems,
			MenuItem pet, List<MenuItem> loadouts, int prevSlot, int nextSlot) {}

	public LoadoutsScreen(AbstractContainerScreen<?> original) {
		super("Loadouts", new ItemStack(Items.ARMOR_STAND), 1, original);
	}

	public static LoadoutsScreen forTitle(String title, AbstractContainerScreen<?> original) {
		return title.endsWith("Loadouts") ? new LoadoutsScreen(original) : null;
	}

	@Override
	protected String hint() {
		return "Click a loadout to use it.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(originalMenuButton(bx() + BW - 86, by() + BH - BUTTON_H));
	}

	private static boolean pane(MenuItem i) {
		return TrackParser.isPane(BuiltInRegistries.ITEM.getKey(i.stack().getItem()).toString());
	}

	private Layout layout() {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		if (clickLogAt > 0 && System.currentTimeMillis() >= clickLogAt) { // what Hypixel made of the click
			clickLogAt = -1;
			logMenuNow(items, "2s after a loadout click");
		}
		List<MenuItem> actions = new ArrayList<>(), equipment = new ArrayList<>(), armorItems = new ArrayList<>(), loadouts = new ArrayList<>();
		ItemStack[] armor = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
		MenuItem pet = null;
		int prev = -1, next = -1;
		for (MenuItem i : items) {
			String name = i.name();
			int col = i.slot() % 9, row = i.slot() / 9;
			if (name.contains("Previous Page")) prev = i.slot();
			else if (name.contains("Next Page")) next = i.slot();
			else if (name.isEmpty() || NAV.contains(name) || row < 1 || row > 4) continue;
			else if (col >= 5 && col <= 7) loadouts.add(i);
			else if (pane(i)) continue; // empty spots on the left
			else if (col == 0) actions.add(i);
			else if (col == 1) equipment.add(i);
			else if (col == 2) {
				armorItems.add(i);
				armor[row - 1] = i.stack();
			} else if (col == 3 && pet == null) pet = i;
		}
		// Hypixel empties a loadout's slot for a moment while it handles a click: keep showing what
		// was there (briefly), so the card doesn't blink out
		long now = System.currentTimeMillis();
		for (MenuItem lo : loadouts) {
			lastLoadouts.put(lo.slot(), lo);
			lastSeen.put(lo.slot(), now);
		}
		for (Map.Entry<Integer, MenuItem> e : lastLoadouts.entrySet())
			if (loadouts.stream().noneMatch(lo -> lo.slot() == e.getKey()) && now - lastSeen.get(e.getKey()) < BRIDGE_MS) loadouts.add(e.getValue());
		loadouts.sort((p, q) -> Integer.compare(index(p.slot()), index(q.slot())));
		return new Layout(actions, equipment, armor, armorItems, pet, loadouts, prev, next);
	}

	/** A loadout's fixed place in the grid, from its menu slot: Loadout 1 is always first, and so on. */
	private static int index(int slot) {
		return (slot / 9 - 1) * 3 + (slot % 9 - 5);
	}

	private static LoadoutParser.Loadout parsed(MenuItem i) {
		return LoadoutParser.parse(i.lore());
	}

	/** The loadout in use: the only one you can edit but not equip. */
	private static boolean isActive(MenuItem i) {
		LoadoutParser.Loadout l = parsed(i);
		return l != null && l.active();
	}

	private static boolean usable(MenuItem i) {
		LoadoutParser.Loadout l = parsed(i);
		return l != null ? !l.empty() : !pane(i);
	}

	/** The loadout's armor as real items (from their names); its own icon as the helmet until the item list has loaded. */
	private static ItemStack[] armorOf(MenuItem lo) {
		LoadoutParser.Loadout l = parsed(lo);
		ItemStack[] out = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
		if (l == null) {
			out[0] = lo.stack();
			return out;
		}
		for (int p = 0; p < 4; p++) {
			ItemStack s = l.armor()[p] != null ? SkyblockItems.stack(l.armor()[p]) : null;
			if (s != null) out[p] = s;
		}
		if (out[0].isEmpty() && l.armor()[0] != null) out[0] = lo.stack();
		return out;
	}

	private float hoverOf(int key, boolean over) {
		Anim hv = hover.computeIfAbsent(key, k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		return hv.get();
	}

	private SkinModel model(int key) {
		return models.computeIfAbsent(key, k -> new SkinModel(minecraft.level, minecraft.player.getGameProfile()));
	}

	private void dress(SkinModel m, ItemStack[] armor) {
		for (int p = 0; p < 4; p++) m.setItemSlot(ARMOR[p], armor[p].isEmpty() ? ItemStack.EMPTY : armor[p].copy());
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Layout l = layout();
		if (giveUpIfUnreadable(l.loadouts().isEmpty() && l.armorItems().isEmpty())) return;
		ItemStack tooltip = extractShown(g, l, mouseX, mouseY, a);
		ItemStack t = extractGrid(g, l, mouseX, mouseY, a);
		if (t != null) tooltip = t;
		int py = by() + BH - BUTTON_H;
		String page = original.getTitle().getString().replaceAll("^\\((\\d+/\\d+)\\).*$", "$1");
		if (l.prevSlot() >= 0 || l.nextSlot() >= 0) {
			pageButton(g, bx() + GRID_X, py, "‹", l.prevSlot() >= 0, mouseX, mouseY, a);
			String label = page.contains("/") ? "Page " + page : "";
			g.text(font, label, bx() + GRID_X + 28, py + 5, Theme.argb(Theme.TEXT_MUTED, a), true);
			pageButton(g, nextX(label), py, "›", l.nextSlot() >= 0, mouseX, mouseY, a);
		}
		if (tooltip != null) Theme.itemTooltip(g, tooltip, mouseX, mouseY);
	}

	private int nextX(String label) {
		return bx() + GRID_X + 34 + font.width(label);
	}

	/** Left: the shown loadout as you, equipment column left of you, armor right, pet and actions below. */
	private ItemStack extractShown(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY, float a) {
		int x = bx(), y = by(), h = BH - BUTTON_H - 6 - 26;
		ItemStack tooltip = null;
		Theme.divider(g, font, "WEARING NOW", x, y, LEFT_W, a);
		int cy = y + 12;
		Theme.box(g, x, cy, LEFT_W, h - 12, Theme.ROW, a, Theme.mix(Theme.LINE, ACTIVE, 0.5f), a);
		SkinModel m = model(-1);
		dress(m, l.armor());
		if (a > 0.5f)
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 30, cy + 4, x + LEFT_W - 30, cy + h - 30, 52, 0.0625f, mouseX, mouseY, m);
		// equipment on the left edge, armor on the right edge, 4 slots each
		for (int i = 0; i < 4; i++) {
			int sy = cy + 6 + i * 22;
			if (i < l.equipment().size()) tooltip = slot(g, l.equipment().get(i).stack(), x + 6, sy, mouseX, mouseY, a, tooltip);
			if (i < l.armorItems().size()) tooltip = slot(g, l.armorItems().get(i).stack(), x + LEFT_W - 24, sy, mouseX, mouseY, a, tooltip);
		}
		if (l.pet() != null) {
			int py = cy + h - 34;
			tooltip = slot(g, l.pet().stack(), x + 6, py, mouseX, mouseY, a, tooltip);
			g.text(font, Theme.fit(font, l.pet().name(), LEFT_W - 34), x + 28, py + 5, Theme.argb(Theme.colorOf(l.pet().stack().getHoverName()), a), true);
		}
		// action buttons (the menu's column 0), as icon chips under the card
		for (int i = 0; i < l.actions().size(); i++) {
			int bx = x + i * 26, by = y + h + 4;
			boolean over = mouseX >= bx && mouseX < bx + 22 && mouseY >= by && mouseY < by + 18;
			if (over) tooltip = l.actions().get(i).stack();
			float hf = hoverOf(1000 + i, over);
			Theme.box(g, bx, by, 22, 18, Theme.mix(Theme.ROW, Theme.ROW_HOVER, hf), a, Theme.mix(Theme.LINE, Theme.FRAME, 0.8f * hf), a);
			Theme.item(g, l.actions().get(i).stack(), bx + 5, by + 3, 12, a);
		}
		return tooltip;
	}

	private ItemStack slot(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int mouseX, int mouseY, float a, ItemStack tooltip) {
		Theme.slot(g, stack, x, y, 1f, a);
		return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18 ? stack : tooltip;
	}

	private int cardW() {
		return (GRID_W - (GRID_COLS - 1) * GAP) / GRID_COLS;
	}

	private int cardH(int rows) {
		return (BH - BUTTON_H - 6 - (rows - 1) * GAP) / rows;
	}

	private int[] cardPos(int i, int rows) {
		return new int[]{bx() + GRID_X + (i % GRID_COLS) * (cardW() + GAP), by() + (i / GRID_COLS) * (cardH(rows) + GAP)};
	}

	/** Right: a card per loadout with you wearing it; green = active, blue = ready, grey = empty/locked. */
	private ItemStack extractGrid(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY, float a) {
		int rows = rows(l), cw = cardW(), ch = cardH(rows);
		int menuActive = menuActive(l);
		if (pendingSlot >= 0 && menuActive != activeAtClick) pendingSlot = -1; // Hypixel updated it: trust the menu again
		ItemStack tooltip = null;
		for (MenuItem lo : l.loadouts()) {
			int idx = index(lo.slot());
			int[] p = cardPos(idx, rows);
			boolean active = pendingSlot >= 0 ? lo.slot() == pendingSlot : isActive(lo), usable = usable(lo);
			int color = active ? ACTIVE : usable ? READY : EMPTY;
			boolean over = mouseX >= p[0] && mouseX < p[0] + cw && mouseY >= p[1] && mouseY < p[1] + ch;
			if (over) tooltip = lo.stack();
			float hf = hoverOf(lo.slot(), over), press = press(lo.slot());
			// click: the card swells a few pixels and settles back
			int grow = Math.round(3 * press), x = p[0] - grow, w = cw + 2 * grow, h = ch + 2 * grow;
			int y = p[1] - grow - Math.round(2 * hf);
			Theme.box(g, x, y, w, h, Theme.mix(Theme.mix(Theme.ROW, color, usable ? 0.38f : 0.15f), 0xFFFFFF, 0.06f * hf + 0.12f * press), a,
					Theme.mix(Theme.mix(color, Theme.LINE, 0.3f), 0xFFFFFF, 0.35f * hf + 0.5f * press), a);
			if (active) g.fill(x + 1, y + 1, x + w - 1, y + 3, Theme.argb(ACTIVE, a));
			if (usable) {
				SkinModel m = model(lo.slot());
				dress(m, armorOf(lo));
				if (a > 0.5f)
					InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 2, y + 4, x + w - 2, y + h - 14,
							Math.round(Math.min(40, ch / 3) * (1 + 0.08f * press)), 0.0625f, mouseX, mouseY, m);
			} else Theme.item(g, lo.stack(), x + (w - 16) / 2, y + (h - 16) / 2 - 6, 16, a);
			// "Loadout 3" → "3": the cards are narrow, the full name is in the tooltip
			String name = Theme.fit(font, lo.name().replaceFirst("(?i)^(Locked )?Loadout\\s*", ""), w - 4);
			if (name.isEmpty()) name = usable ? String.valueOf(idx + 1) : "Locked";
			g.text(font, name, x + (w - font.width(name)) / 2, y + h - 11, Theme.argb(active ? Theme.TITLE : usable ? Theme.TEXT : Theme.TEXT_MUTED, a), true);
		}
		return tooltip;
	}

	/** Slot of the loadout the menu's text says is in use, or -1. */
	private static int menuActive(Layout l) {
		return l.loadouts().stream().filter(LoadoutsScreen::isActive).mapToInt(MenuItem::slot).findFirst().orElse(-1);
	}

	/** 0 → 1 → 0 over {@link #PRESS_MS} after a card is clicked. */
	private float press(int slot) {
		Long at = pressedAt.get(slot);
		if (at == null) return 0f;
		float t = (System.currentTimeMillis() - at) / (float) PRESS_MS;
		return t >= 1f ? 0f : (float) Math.sin(Math.PI * t);
	}

	/** Grid rows from the highest loadout position (12 loadouts → 2 rows of 6), not from how many are visible. */
	private static int rows(Layout l) {
		int max = l.loadouts().stream().mapToInt(lo -> index(lo.slot())).max().orElse(0);
		return Math.max(1, max / GRID_COLS + 1);
	}

	private void pageButton(GuiGraphicsExtractor g, int x, int y, String arrow, boolean on, int mouseX, int mouseY, float a) {
		boolean over = on && mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + BUTTON_H;
		Theme.box(g, x, y, 22, BUTTON_H, over ? Theme.ROW_HOVER : Theme.ROW, a, on ? (over ? Theme.FRAME : Theme.LINE) : Theme.SLOT, a);
		g.text(font, arrow, x + (22 - font.width(arrow)) / 2, y + 5, Theme.argb(on ? Theme.TEXT : Theme.TEXT_MUTED, a), true);
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		Layout l = layout();
		double mx = event.x(), my = event.y();
		int py = by() + BH - BUTTON_H;
		String page = original.getTitle().getString().replaceAll("^\\((\\d+/\\d+)\\).*$", "$1");
		String label = page.contains("/") ? "Page " + page : "";
		if (my >= py && my < py + BUTTON_H) {
			if (l.prevSlot() >= 0 && mx >= bx() + GRID_X && mx < bx() + GRID_X + 22) { click(l.prevSlot(), 0); return true; }
			if (l.nextSlot() >= 0 && mx >= nextX(label) && mx < nextX(label) + 22) { click(l.nextSlot(), 0); return true; }
		}
		int rows = rows(l);
		for (MenuItem lo : l.loadouts()) {
			int[] p = cardPos(index(lo.slot()), rows);
			if (mx >= p[0] && mx < p[0] + cardW() && my >= p[1] && my < p[1] + cardH(rows)) {
				pressedAt.put(lo.slot(), System.currentTimeMillis());
				if (event.button() == 0 && usable(lo) && !isActive(lo)) { // left-click equips (right-click edits)
					activeAtClick = menuActive(l);
					pendingSlot = lo.slot();
				}
				clickLogAt = System.currentTimeMillis() + 2000;
				clickAs(lo.slot(), event);
				return true;
			}
		}
		// equipment / armor / pet / actions on the left: the real menu decides what a click does
		int x = bx(), cy = by() + 12, h = BH - BUTTON_H - 6 - 26;
		for (int i = 0; i < 4; i++) {
			int sy = cy + 6 + i * 22;
			if (i < l.equipment().size() && mx >= x + 6 && mx < x + 24 && my >= sy && my < sy + 18) { clickAs(l.equipment().get(i).slot(), event); return true; }
			if (i < l.armorItems().size() && mx >= x + LEFT_W - 24 && mx < x + LEFT_W - 6 && my >= sy && my < sy + 18) { clickAs(l.armorItems().get(i).slot(), event); return true; }
		}
		if (l.pet() != null && mx >= x + 6 && mx < x + 24 && my >= cy + h - 34 && my < cy + h - 16) { clickAs(l.pet().slot(), event); return true; }
		for (int i = 0; i < l.actions().size(); i++) {
			int bx = x + i * 26, by = by() + h + 4;
			if (mx >= bx && mx < bx + 22 && my >= by && my < by + 18) { clickAs(l.actions().get(i).slot(), event); return true; }
		}
		return false;
	}
}
