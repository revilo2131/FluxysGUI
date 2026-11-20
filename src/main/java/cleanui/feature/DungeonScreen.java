package cleanui.feature;

import cleanui.data.Config;
import cleanui.skyblock.CollectionParser;
import cleanui.skyblock.SkillParser;
import cleanui.ui.Anim;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A "main track + parts" page shown in place of Hypixel's menu: one main card, its parts as rows
 * under it (two columns when there are many), and everything else in a side column. Every entry
 * has a progress bar. Used for Dungeoneering (Catacombs + classes) and SkyBlock Leveling (level +
 * task categories). Clicks go to the real menu, so picking a class works as usual.
 */
public final class DungeonScreen extends MenuPage {
	public static final String MENU_TITLE = "Dungeoneering", LEVELING_TITLE = "SkyBlock Leveling";
	/** Classes in display order, with their colors (Theme color index). */
	private static final List<String> CLASSES = List.of("Healer", "Mage", "Berserk", "Archer", "Tank");
	private static final int[] CLASS_COLOR = {2, 1, 3, 5, 4};
	private static final int MAIN_W = 296, SIDE_X = MAIN_W + 10, SIDE_W = BW - SIDE_X;
	private static final int CARD_H = 52, ROW_H = 22, ROW_STEP = 24, ROWS_PER_COLUMN = 5;

	private record Cell(MenuItem item, CollectionParser.Entry entry, int x, int y, int w, int h, int color) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final boolean leveling;
	private final Map<Integer, Anim> hover = new HashMap<>(), bars = new HashMap<>();

	private DungeonScreen(boolean leveling, AbstractContainerScreen<?> original) {
		super(leveling ? "SkyBlock Leveling" : "Dungeoneering",
				new ItemStack(leveling ? Items.EXPERIENCE_BOTTLE : Items.SKELETON_SKULL), 1, original);
		this.leveling = leveling;
	}

	public static DungeonScreen forTitle(String title, AbstractContainerScreen<?> original) {
		if (Config.get().skills && title.equals(MENU_TITLE)) return new DungeonScreen(false, original);
		if (Config.get().skills && title.equals(LEVELING_TITLE)) return new DungeonScreen(true, original);
		return null;
	}

	@Override
	protected String hint() {
		return leveling ? "Hover a category to see its tasks." : "Click a class to select it.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(originalMenuButton(bx() + BW - 86, by() + BH - 18));
	}

	private boolean isMain(MenuItem item) {
		if (!leveling) return item.name().startsWith("Catacombs");
		return item.name().contains("SkyBlock Level") || item.lore().stream().anyMatch(l -> l.contains("Progress to Level"));
	}

	/** Main card first, then its parts (classes in order / categories with progress), then the rest. */
	private List<Cell> cells() {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		MenuItem main = null;
		MenuItem[] classes = new MenuItem[CLASSES.size()];
		List<MenuItem> parts = new ArrayList<>(), rest = new ArrayList<>();
		for (MenuItem item : items) {
			String name = item.name();
			if (name.isEmpty() || NAV.contains(name)) continue;
			if (main == null && isMain(item)) main = item;
			else if (!leveling) {
				int ci = CLASSES.indexOf(name.split(" ")[0]);
				if (ci >= 0 && classes[ci] == null) classes[ci] = item;
				else rest.add(item);
			} else if (CollectionParser.parse(name, item.lore()).percent() != null) parts.add(item);
			else rest.add(item);
		}
		// leveling: task categories are the parts; other progress items (Rewards, Emblems) go aside
		if (leveling && parts.stream().anyMatch(p -> p.name().contains("Task"))) {
			rest.addAll(0, parts.stream().filter(p -> !p.name().contains("Task")).toList());
			parts.removeIf(p -> !p.name().contains("Task"));
		}
		List<Cell> cells = new ArrayList<>();
		int x = bx(), y = by();
		if (main != null) cells.add(cell(main, x, y, MAIN_W, CARD_H, Theme.color(3)));
		List<Integer> colors = new ArrayList<>();
		if (!leveling)
			for (int i = 0; i < classes.length; i++)
				if (classes[i] != null) {
					parts.add(classes[i]);
					colors.add(Theme.color(CLASS_COLOR[i]));
				}
		// many parts: two columns
		int columns = parts.size() > ROWS_PER_COLUMN ? 2 : 1, w = columns == 1 ? MAIN_W : (MAIN_W - 6) / 2;
		for (int i = 0; i < Math.min(parts.size(), columns * ROWS_PER_COLUMN); i++) {
			int color = leveling ? Theme.color(i) : colors.get(i);
			cells.add(cell(parts.get(i), x + (i % columns) * (w + 6), y + CARD_H + 6 + (i / columns) * ROW_STEP, w, ROW_H, color));
		}
		int sy = y + 14;
		int side = Theme.mix(Theme.FRAME_DIM, Theme.FRAME, 0.3f);
		for (MenuItem r : rest) {
			if (sy + ROW_H > by() + BH - 22) break; // leave room for "Original menu"
			cells.add(cell(r, x + SIDE_X, sy, SIDE_W, ROW_H, side));
			sy += ROW_STEP;
		}
		return cells;
	}

	private static Cell cell(MenuItem item, int x, int y, int w, int h, int color) {
		return new Cell(item, CollectionParser.parse(item.name(), item.lore()), x, y, w, h, color);
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<Cell> cells = cells();
		if (giveUpIfUnreadable(cells.isEmpty())) return;
		Theme.divider(g, font, "OTHER", bx() + SIDE_X, by(), SIDE_W, a);
		Cell hovered = null;
		for (Cell c : cells) {
			boolean over = c.contains(mouseX, mouseY);
			if (over) hovered = c;
			if (c.h() == CARD_H) extractMain(g, c, over, a);
			else extractRow(g, c, over, a);
		}
		if (hovered != null) Theme.itemTooltip(g, hovered.item().stack(), mouseX, mouseY);
	}

	private float hoverOf(Cell c, boolean over) {
		Anim hv = hover.computeIfAbsent(c.item().slot(), k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		return hv.get();
	}

	/** Bar fill; animates in from 0 when the page opens. */
	private float barOf(Cell c) {
		Anim bar = bars.computeIfAbsent(c.item().slot(), k -> new Anim(0f, 5f));
		CollectionParser.Entry e = c.entry();
		bar.target(e.maxed() ? 1f : e.percent() == null ? 0f : e.percent());
		return bar.get();
	}

	/** Main card: framed slot, name ···· level badge, progress line, XP inside an angled bar. */
	private void extractMain(GuiGraphicsExtractor g, Cell c, boolean over, float a) {
		CollectionParser.Entry e = c.entry();
		float h = hoverOf(c, over), p = barOf(c);
		int x = c.x(), y = c.y(), w = c.w(), color = c.color();
		Theme.box(g, x, y, w, c.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, color, 0.7f * h), a);
		g.fill(x + 1, y + 1, x + 3 + Math.round(2 * h), y + c.h() - 1, Theme.argb(color, a));
		Theme.slot(g, c.item().stack(), x + 8, y + 7, 1f + 0.12f * h, a);
		int tx = x + 32, right = x + w - 8;
		int tier = e.tier() >= 0 ? e.tier() : CollectionParser.levelFromProgress(c.item().lore());
		int bw = tier >= 0 ? Theme.badge(g, font, String.valueOf(tier), right, y + 6, Theme.ACCENT, a) : 0;
		g.text(font, e.name(), tx, y + 7, Theme.argb(Theme.TITLE, a), true);
		Theme.leader(g, tx + font.width(e.name()) + 3, right - bw - 3, y + 13, a);
		if (e.percent() != null)
			Theme.textLeader(g, font, "Progress to " + (tier + 1), Theme.TEXT_MUTED, Math.round(e.percent() * 100) + "%", Theme.TEXT, tx, y + 19, right - tx, a);
		String xp = e.done() != null ? SkillParser.compact(e.done()) + " / " + SkillParser.compact(e.total()) : null;
		Theme.bar(g, font, x + 8, y + c.h() - 15, w - 16, 11, p, Theme.mix(color, 0x000000, 0.2f), xp, a);
	}

	/** Part or side entry: stripe, item, name ···· level badge or x/y, bar along the bottom. */
	private void extractRow(GuiGraphicsExtractor g, Cell c, boolean over, float a) {
		CollectionParser.Entry e = c.entry();
		float h = hoverOf(c, over), p = barOf(c);
		int color = c.color();
		boolean selected = c.item().lore().stream().anyMatch(l -> l.contains("SELECTED"));
		int x = c.x(), y = c.y(), w = c.w();
		Theme.box(g, x, y, w, c.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a,
				selected ? Theme.FRAME : Theme.mix(Theme.LINE, color, 0.7f * h), a);
		g.fill(x + 1, y + 1, x + 3 + Math.round(2 * h), y + c.h() - 1, Theme.argb(color, a));
		Theme.item(g, c.item().stack(), x + 6, y + 3, Math.round(13 + 2 * h), a);
		int tx = x + 24, right = x + w - 6;
		int bw = 0;
		if (e.tier() >= 0) bw = Theme.badge(g, font, String.valueOf(e.tier()), right, y + 4, color, a);
		else if (e.done() != null) { // "12/30"
			String v = SkillParser.compact(e.done()) + "/" + SkillParser.compact(e.total());
			g.text(font, v, right - font.width(v), y + 5, Theme.argb(Theme.ACCENT, a), true);
			bw = font.width(v);
		} else if (e.percent() != null) {
			String v = Math.round(e.percent() * 100) + "%";
			g.text(font, v, right - font.width(v), y + 5, Theme.argb(Theme.ACCENT, a), true);
			bw = font.width(v);
		}
		String name = Theme.fit(font, e.name(), right - tx - bw - 6);
		g.text(font, name, tx, y + 5, Theme.argb(Theme.TEXT, a), true);
		if (selected && !leveling) Theme.tag(g, font, "SELECTED", tx + font.width(name) + 6, y + 4, Theme.FRAME, a);
		g.fill(tx, y + c.h() - 4, right, y + c.h() - 2, Theme.argb(Theme.SLOT, a));
		g.fill(tx, y + c.h() - 4, tx + Math.round((right - tx) * p), y + c.h() - 2, Theme.argb(color, a));
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		for (Cell c : cells())
			if (c.contains(event.x(), event.y())) {
				click(c.item().slot(), event.button());
				return true;
			}
		return false;
	}
}
