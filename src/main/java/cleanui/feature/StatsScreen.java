package cleanui.feature;

import cleanui.skyblock.StatParser;
import cleanui.ui.Anim;
import cleanui.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Stats & Equipment": your live model, your gear in the menu's own column order, and the stat
 * categories ("Combat Stats", ...) as tabs whose stats are listed in their Hypixel colours.
 * Gear clicks go to the real menu (unequip etc.); tabs only switch what's shown.
 */
public final class StatsScreen extends MenuPage {
	private static final int MODEL_W = 84, MODEL_H = 150, GEAR_X = MODEL_W + 8, SLOT = 22, GEAR_ROWS = 7;
	private static final int TAB_H = 16, STAT_STEP = 12;

	/** One stat category item and its parsed lines (name colour taken from the lore's own styling). */
	private record Category(MenuItem item, List<Line> lines) {}

	private record Line(StatParser.Stat stat, int color) {}

	private final Map<Integer, Anim> hover = new HashMap<>();
	private int tab;

	public StatsScreen(AbstractContainerScreen<?> original) {
		super(original.getTitle().getString(), new ItemStack(Items.ARMOR_STAND), 1, original);
	}

	/** "Stats & Equipment" (also the older "Your Equipment and Stats"), else null. */
	public static StatsScreen forTitle(String title, AbstractContainerScreen<?> original) {
		return title.contains("Stats") && title.contains("Equipment") ? new StatsScreen(original) : null;
	}

	@Override
	protected String hint() {
		return "Click gear to manage it; tabs switch stat groups.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(originalMenuButton(bx(), by() + BH - 18));
	}

	private List<Category> categories(List<MenuItem> items) {
		List<Category> cats = new ArrayList<>();
		for (MenuItem item : items) {
			if (!item.name().endsWith("Stats")) continue;
			List<Line> lines = new ArrayList<>();
			ItemLore lore = item.stack().get(DataComponents.LORE);
			if (lore != null)
				for (Component c : lore.lines()) {
					StatParser.Stat s = StatParser.stat(ChatFormatting.stripFormatting(c.getString()));
					if (s != null) lines.add(new Line(s, Theme.colorOf(c)));
				}
			if (!lines.isEmpty()) cats.add(new Category(item, lines));
		}
		return cats;
	}

	/** Gear and pet: everything else that's named, in the menu's column order (armor column, then equipment, ...). */
	private List<MenuItem> gear(List<MenuItem> items, List<Category> cats) {
		List<MenuItem> gear = new ArrayList<>();
		for (MenuItem item : items)
			if (!item.name().isEmpty() && !NAV.contains(item.name()) && cats.stream().noneMatch(c -> c.item() == item)) gear.add(item);
		gear.sort(Comparator.comparingInt((MenuItem i) -> i.slot() % 9).thenComparingInt(i -> i.slot() / 9));
		return gear;
	}

	/** Each menu column becomes a gear column (armor, equipment, ...), packed top-down: {x, y} per gear item. */
	private List<int[]> gearPos(List<MenuItem> gear) {
		List<int[]> pos = new ArrayList<>();
		int col = -1, lastMenuCol = -1, row = 0;
		for (MenuItem item : gear) {
			if (item.slot() % 9 != lastMenuCol || row == GEAR_ROWS) {
				col++;
				row = 0;
				lastMenuCol = item.slot() % 9;
			}
			pos.add(new int[]{bx() + GEAR_X + col * (SLOT + 4), by() + row++ * (SLOT + 3)});
		}
		return pos;
	}

	private int statsX(List<int[]> pos) {
		int right = pos.stream().mapToInt(p -> p[0] + SLOT).max().orElse(bx() + GEAR_X);
		return right + 10;
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		List<Category> cats = categories(items);
		List<MenuItem> gear = gear(items, cats);
		if (giveUpIfUnreadable(cats.isEmpty() && gear.isEmpty())) return;
		ItemStack tooltip = null;

		// live model, like the Overview's profile card; it can't fade, so it appears once the frame is mostly in
		int x = bx(), y = by();
		Theme.box(g, x, y, MODEL_W, MODEL_H, Theme.ROW, a, Theme.LINE, a);
		if (a > 0.5f)
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 4, y + 16, x + MODEL_W - 4, y + MODEL_H - 4, 48, 0.0625f, mouseX, mouseY, minecraft.player);
		String name = Theme.fit(font, minecraft.player.getName().getString(), MODEL_W - 8);
		g.text(font, name, x + (MODEL_W - font.width(name)) / 2, y + 5, Theme.argb(Theme.TITLE, a), true);

		List<int[]> pos = gearPos(gear);
		for (int i = 0; i < gear.size(); i++) {
			int gx = pos.get(i)[0], gy = pos.get(i)[1];
			boolean over = mouseX >= gx && mouseX < gx + SLOT && mouseY >= gy && mouseY < gy + SLOT;
			if (over) tooltip = gear.get(i).stack();
			float h = hoverOf(gear.get(i).slot(), over);
			Theme.slot(g, gear.get(i).stack(), gx + 2, gy + 2, 1f + 0.12f * h, a);
		}

		if (!cats.isEmpty()) {
			ItemStack t = extractStats(g, cats, statsX(pos), mouseX, mouseY, a);
			if (t != null) tooltip = t;
		}
		if (tooltip != null) Theme.itemTooltip(g, tooltip, mouseX, mouseY);
	}

	private float hoverOf(int slot, boolean over) {
		Anim hv = hover.computeIfAbsent(slot, k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		return hv.get();
	}

	/** Tabs for the categories, then the chosen one's stats in two columns. Returns a hovered tab's item. */
	private ItemStack extractStats(GuiGraphicsExtractor g, List<Category> cats, int x, int mouseX, int mouseY, float a) {
		int w = bx() + BW - x, y = by(), tabW = (w - (cats.size() - 1) * 3) / cats.size();
		tab = Math.clamp(tab, 0, cats.size() - 1);
		ItemStack tooltip = null;
		for (int i = 0; i < cats.size(); i++) {
			int tx = x + i * (tabW + 3);
			boolean over = mouseX >= tx && mouseX < tx + tabW && mouseY >= y && mouseY < y + TAB_H, on = i == tab;
			if (over) tooltip = cats.get(i).item().stack();
			float h = hoverOf(1000 + i, over);
			Theme.box(g, tx, y, tabW, TAB_H, on ? Theme.mix(Theme.ROW, Theme.FRAME, 0.2f) : Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a,
					on ? Theme.FRAME : Theme.LINE, a);
			String label = Theme.fit(font, cats.get(i).item().name().replace(" Stats", ""), tabW - 6);
			g.text(font, label, tx + (tabW - font.width(label)) / 2, y + 4, Theme.argb(on ? Theme.TITLE : Theme.TEXT, a), true);
		}
		Category cat = cats.get(tab);
		int top = y + TAB_H + 6, colW = (w - 10) / 2, maxRows = (by() + BH - top) / STAT_STEP;
		int rows = Math.min(maxRows, (cat.lines().size() + 1) / 2); // split evenly over both columns
		// shortcut: stats past two full columns are cut off; add scrolling if a category ever gets that long
		for (int i = 0; i < Math.min(cat.lines().size(), rows * 2); i++) {
			Line l = cat.lines().get(i);
			int lx = x + (i / rows) * (colW + 10), ly = top + (i % rows) * STAT_STEP;
			String value = l.stat().value(), name = Theme.fit(font, l.stat().name(), colW - font.width(value) - 8);
			Theme.textLeader(g, font, name, l.color(), value, Theme.TEXT, lx, ly, colW, a);
		}
		return tooltip;
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		List<MenuItem> items = menuItems();
		List<Category> cats = categories(items);
		List<MenuItem> gear = gear(items, cats);
		List<int[]> pos = gearPos(gear);
		for (int i = 0; i < gear.size(); i++)
			if (event.x() >= pos.get(i)[0] && event.x() < pos.get(i)[0] + SLOT && event.y() >= pos.get(i)[1] && event.y() < pos.get(i)[1] + SLOT) {
				clickAs(gear.get(i).slot(), event);
				return true;
			}
		if (!cats.isEmpty()) {
			int x = statsX(pos), w = bx() + BW - x, tabW = (w - (cats.size() - 1) * 3) / cats.size();
			for (int i = 0; i < cats.size(); i++) {
				int tx = x + i * (tabW + 3);
				if (event.x() >= tx && event.x() < tx + tabW && event.y() >= by() && event.y() < by() + TAB_H) {
					tab = i;
					return true;
				}
			}
		}
		return false;
	}
}
