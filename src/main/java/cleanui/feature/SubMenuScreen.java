package cleanui.feature;

import cleanui.ui.Anim;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A menu opened from inside a Clean UI page (e.g. Reset or Swap from Heart of the Mountain),
 * whatever its title. The layout comes from what's in it: a menu with Confirm/Cancel becomes a
 * dialog card with Hypixel's own text; anything else keeps every button in its vanilla spot.
 * Clicks go to the real menu 1:1.
 */
public final class SubMenuScreen extends MenuPage {
	private static final Pattern CONFIRM = Pattern.compile("(?i)^(confirm|yes|accept)\\b.*");
	private static final Pattern CANCEL = Pattern.compile("(?i)^(cancel|no|decline)\\b.*");
	private static final int COLS = 9, COL_STEP = 47, TILE_W = 44, TILE_H = 28, ROW_STEP = 31, LABEL_H = 11;
	private static final int DIALOG_W = 260, BUTTON_W = 110, BUTTON_H = 22;

	private record Tile(MenuItem item, int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final String parent;
	private final Map<Integer, Anim> hover = new HashMap<>();

	public SubMenuScreen(String parent, ItemStack icon, AbstractContainerScreen<?> original) {
		super(original.getTitle().getString(), icon, 2, original);
		this.parent = parent;
	}

	@Override
	protected String crumb() {
		return parent;
	}

	@Override
	protected String hint() {
		return "Hover for details; clicks go to Hypixel's menu.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(originalMenuButton(bx() + BW - 86, by() + BH - 18));
	}

	private static boolean isConfirm(MenuItem i) {
		String id = BuiltInRegistries.ITEM.getKey(i.stack().getItem()).toString();
		return CONFIRM.matcher(i.name()).matches() || (id.contains("lime") || id.contains("green")) && !i.name().isEmpty() && !CANCEL.matcher(i.name()).matches();
	}

	private static boolean isCancel(MenuItem i) {
		String id = BuiltInRegistries.ITEM.getKey(i.stack().getItem()).toString();
		return CANCEL.matcher(i.name()).matches() || id.contains("red") && !i.name().isEmpty() && !CONFIRM.matcher(i.name()).matches();
	}

	/** Dialog parts if this menu is a confirmation: {subject, confirm, cancel}; else null. */
	private MenuItem[] dialog(List<MenuItem> items) {
		MenuItem confirm = null, cancel = null, subject = null;
		for (MenuItem i : items) {
			if (i.name().isEmpty() || i.name().equals("Close")) continue;
			if (confirm == null && isConfirm(i)) confirm = i;
			else if (cancel == null && isCancel(i)) cancel = i;
			else if (subject == null) subject = i;
		}
		return confirm != null ? new MenuItem[]{subject, confirm, cancel} : null;
	}

	/** Dialog: the two buttons; grid: every named item at its vanilla spot. */
	private List<Tile> tiles(List<MenuItem> items, MenuItem[] dlg) {
		List<Tile> tiles = new ArrayList<>();
		if (dlg != null) {
			int cx = bx() + BW / 2, by = by() + 132;
			tiles.add(new Tile(dlg[1], cx - BUTTON_W - 6, by, BUTTON_W, BUTTON_H));
			if (dlg[2] != null) tiles.add(new Tile(dlg[2], cx + 6, by, BUTTON_W, BUTTON_H));
			return tiles;
		}
		// vanilla columns; rows that are empty in the menu collapse to a small gap
		int gx = bx() + (BW - (COLS * COL_STEP - 3)) / 2;
		Map<Integer, List<MenuItem>> rows = new java.util.TreeMap<>();
		for (MenuItem i : items)
			if (!i.name().isEmpty() && !NAV.contains(i.name())) rows.computeIfAbsent(i.slot() / COLS, k -> new ArrayList<>()).add(i);
		int y = by() + 4, lastRow = -1;
		for (Map.Entry<Integer, List<MenuItem>> row : rows.entrySet()) {
			if (lastRow >= 0 && row.getKey() > lastRow + 1) y += 8;
			for (MenuItem i : row.getValue()) tiles.add(new Tile(i, gx + (i.slot() % COLS) * COL_STEP, y, TILE_W, TILE_H));
			y += ROW_STEP + LABEL_H;
			lastRow = row.getKey();
		}
		return tiles;
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		if (giveUpIfUnreadable(items.isEmpty())) return;
		MenuItem[] dlg = dialog(items);
		if (dlg != null) extractDialogCard(g, dlg[0], a);
		Tile hovered = null;
		List<Tile> all = tiles(items, dlg);
		for (Tile t : all) {
			boolean over = t.contains(mouseX, mouseY);
			if (over) hovered = t;
			float h = hoverOf(t, over);
			if (dlg != null) extractButton(g, t, t.item() == dlg[1], h, a);
			else extractTile(g, t, all, h, a);
		}
		if (hovered != null) Theme.itemTooltip(g, hovered.item().stack(), mouseX, mouseY);
	}

	private float hoverOf(Tile t, boolean over) {
		Anim hv = hover.computeIfAbsent(t.item().slot(), k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		return hv.get();
	}

	/** Centered card: what's being confirmed, with its full text in Hypixel's colors. */
	private void extractDialogCard(GuiGraphicsExtractor g, MenuItem subject, float a) {
		int x = bx() + (BW - DIALOG_W) / 2, y = by() + 4, h = 120;
		Theme.box(g, x, y, DIALOG_W, h, Theme.ROW, a, Theme.mix(Theme.LINE, Theme.FRAME, 0.5f), a);
		if (subject == null) {
			g.text(font, "Are you sure?", x + 10, y + 10, Theme.argb(Theme.TITLE, a), true);
			return;
		}
		Theme.slot(g, subject.stack(), x + 8, y + 8, 1f, a);
		g.text(font, subject.stack().getHoverName(), x + 32, y + 13, Theme.argb(Theme.TITLE, a), true);
		Theme.divider(g, x + 6, y + 32, DIALOG_W - 12, a);
		ItemLore lore = subject.stack().get(DataComponents.LORE);
		if (lore == null) return;
		int line = 0;
		for (Component c : lore.lines()) {
			if (c.getString().isBlank()) continue;
			if (line >= 7) break;
			List<FormattedCharSequence> parts = font.split(c, DIALOG_W - 20);
			if (!parts.isEmpty()) g.text(font, parts.getFirst(), x + 10, y + 40 + line++ * 11, Theme.argb(Theme.TEXT, a), true);
		}
	}

	/** Big Confirm (green) / Cancel (red) button. */
	private void extractButton(GuiGraphicsExtractor g, Tile t, boolean confirm, float h, float a) {
		int color = confirm ? Theme.color(5) : Theme.color(3);
		Theme.box(g, t.x(), t.y(), t.w(), t.h(), Theme.mix(Theme.ROW, color, 0.18f + 0.22f * h), a, Theme.mix(color, Theme.TEXT, 0.3f * h), a);
		Theme.item(g, t.item().stack(), t.x() + 6, t.y() + 3, 16, a);
		String label = Theme.fit(font, t.item().name(), t.w() - 30);
		g.text(font, label, t.x() + 26 + (t.w() - 30 - font.width(label)) / 2, t.y() + 7, Theme.argb(Theme.TEXT, a), true);
	}

	/**
	 * Grid button: its real item (growing a little on hover) and its name underneath, which
	 * may spread into empty neighbouring columns. A "SELECTED" entry is outlined.
	 */
	private void extractTile(GuiGraphicsExtractor g, Tile t, List<Tile> all, float h, float a) {
		boolean selected = t.item().lore().stream().anyMatch(l -> l.contains("SELECTED"));
		Theme.box(g, t.x(), t.y(), t.w(), t.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a,
				selected ? Theme.FRAME : Theme.mix(Theme.LINE, Theme.FRAME, 0.8f * h), a);
		int size = Math.round(18 + 3 * h);
		Theme.item(g, t.item().stack(), t.x() + (t.w() - size) / 2, t.y() + (t.h() - size) / 2 - Math.round(h), size, a);
		boolean leftFree = all.stream().noneMatch(o -> o.y() == t.y() && o.x() == t.x() - COL_STEP);
		boolean rightFree = all.stream().noneMatch(o -> o.y() == t.y() && o.x() == t.x() + COL_STEP);
		int room = t.w() + (leftFree ? COL_STEP - 4 : 0) + (rightFree ? COL_STEP - 4 : 0);
		String label = Theme.fit(font, t.item().name(), room);
		g.text(font, label, t.x() + (t.w() - font.width(label)) / 2, t.y() + t.h() + 2,
				Theme.argb(selected ? Theme.TITLE : Theme.TEXT_MUTED, a), true);
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		List<MenuItem> items = menuItems();
		for (Tile t : tiles(items, dialog(items)))
			if (t.contains(event.x(), event.y())) {
				clickAs(t.item().slot(), event);
				return true;
			}
		return false;
	}
}
