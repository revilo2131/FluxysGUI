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
 * Dungeoneering, shown in place of Hypixel's menu: Catacombs as the main card, the five classes
 * under it (selected class outlined), and everything else in a side column. Every entry has a
 * progress bar. Clicks go to the real menu, so picking a class works as usual.
 */
public final class DungeonScreen extends MenuPage {
	public static final String MENU_TITLE = "Dungeoneering";
	/** Classes in display order, with their colors (Theme color index). */
	private static final List<String> CLASSES = List.of("Healer", "Mage", "Berserk", "Archer", "Tank");
	private static final int[] CLASS_COLOR = {2, 1, 3, 5, 4};
	private static final int MAIN_W = 296, SIDE_X = MAIN_W + 10, SIDE_W = BW - SIDE_X;
	private static final int CARD_H = 52, ROW_H = 22, ROW_STEP = 24;

	private record Cell(MenuItem item, CollectionParser.Entry entry, int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final Map<Integer, Anim> hover = new HashMap<>(), bars = new HashMap<>();

	public DungeonScreen(AbstractContainerScreen<?> original) {
		super("Dungeoneering", new ItemStack(Items.SKELETON_SKULL), 1, original);
	}

	public static DungeonScreen forTitle(String title, AbstractContainerScreen<?> original) {
		return Config.get().skills && title.equals(MENU_TITLE) ? new DungeonScreen(original) : null;
	}

	@Override
	protected String hint() {
		return "Click a class to select it.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(originalMenuButton(bx() + BW - 86, by() + BH - 18));
	}

	/** Catacombs first, then classes in order, then everything else; laid out for this frame. */
	private List<Cell> cells() {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		MenuItem catacombs = null;
		MenuItem[] classes = new MenuItem[CLASSES.size()];
		List<MenuItem> rest = new ArrayList<>();
		for (MenuItem item : items) {
			String name = item.name();
			if (name.isEmpty() || NAV.contains(name) || name.equals("Not Coming Soon")) continue;
			int ci = CLASSES.indexOf(name.split(" ")[0]);
			if (catacombs == null && name.contains("Catacombs")) catacombs = item; // "The Catacombs 21"
			else if (ci >= 0 && classes[ci] == null) classes[ci] = item;
			else rest.add(item);
		}
		List<Cell> cells = new ArrayList<>();
		int x = bx(), y = by();
		if (catacombs != null) cells.add(cell(catacombs, x, y, MAIN_W, CARD_H));
		int ry = y + CARD_H + 6;
		for (MenuItem c : classes)
			if (c != null) {
				cells.add(cell(c, x, ry, MAIN_W, ROW_H));
				ry += ROW_STEP;
			}
		int sy = y + 14;
		for (MenuItem r : rest) {
			if (sy + ROW_H > by() + BH - 22) break; // leave room for "Original menu"
			cells.add(cell(r, x + SIDE_X, sy, SIDE_W, ROW_H));
			sy += ROW_STEP;
		}
		return cells;
	}

	private static Cell cell(MenuItem item, int x, int y, int w, int h) {
		return new Cell(item, CollectionParser.parse(item.name(), item.lore()), x, y, w, h);
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

	/** Catacombs: framed slot, name ···· level badge, progress line, XP inside an angled bar. */
	private void extractMain(GuiGraphicsExtractor g, Cell c, boolean over, float a) {
		CollectionParser.Entry e = c.entry();
		float h = hoverOf(c, over), p = barOf(c);
		int x = c.x(), y = c.y(), w = c.w(), color = Theme.color(3);
		Theme.box(g, x, y, w, c.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, color, 0.7f * h), a);
		g.fill(x + 1, y + 1, x + 3 + Math.round(2 * h), y + c.h() - 1, Theme.argb(color, a));
		Theme.slot(g, c.item().stack(), x + 8, y + 7, 1f + 0.12f * h, a);
		int tx = x + 32, right = x + w - 8;
		int bw = e.tier() >= 0 ? Theme.badge(g, font, String.valueOf(e.tier()), right, y + 6, Theme.ACCENT, a) : 0;
		g.text(font, e.name(), tx, y + 7, Theme.argb(Theme.TITLE, a), true);
		Theme.leader(g, tx + font.width(e.name()) + 3, right - bw - 3, y + 13, a);
		if (e.percent() != null)
			Theme.textLeader(g, font, "Progress to " + (e.tier() + 1), Theme.TEXT_MUTED, Math.round(e.percent() * 100) + "%", Theme.TEXT, tx, y + 19, right - tx, a);
		String xp = e.done() != null ? SkillParser.compact(e.done()) + " / " + SkillParser.compact(e.total()) : null;
		Theme.bar(g, font, x + 8, y + c.h() - 15, w - 16, 11, p, Theme.mix(color, 0x000000, 0.2f), xp, a);
	}

	/** Class or side entry: stripe, item, name ···· level badge, bar along the bottom. */
	private void extractRow(GuiGraphicsExtractor g, Cell c, boolean over, float a) {
		CollectionParser.Entry e = c.entry();
		float h = hoverOf(c, over), p = barOf(c);
		int ci = CLASSES.indexOf(e.name().split(" ")[0]);
		int color = ci >= 0 ? Theme.color(CLASS_COLOR[ci]) : Theme.mix(Theme.FRAME_DIM, Theme.FRAME, 0.3f);
		boolean selected = c.item().lore().stream().anyMatch(l -> l.contains("SELECTED"));
		int x = c.x(), y = c.y(), w = c.w();
		Theme.box(g, x, y, w, c.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a,
				selected ? Theme.FRAME : Theme.mix(Theme.LINE, color, 0.7f * h), a);
		g.fill(x + 1, y + 1, x + 3 + Math.round(2 * h), y + c.h() - 1, Theme.argb(color, a));
		Theme.item(g, c.item().stack(), x + 6, y + 3, Math.round(13 + 2 * h), a);
		int tx = x + 24, right = x + w - 6;
		int bw = 0;
		if (e.tier() >= 0) bw = Theme.badge(g, font, String.valueOf(e.tier()), right, y + 4, color, a);
		else if (e.done() != null) { // side entries: "12/30"
			String v = SkillParser.compact(e.done()) + "/" + SkillParser.compact(e.total());
			g.text(font, v, right - font.width(v), y + 5, Theme.argb(Theme.ACCENT, a), true);
			bw = font.width(v);
		}
		String name = Theme.fit(font, e.name(), right - tx - bw - 6);
		g.text(font, name, tx, y + 5, Theme.argb(Theme.TEXT, a), true);
		if (selected && ci >= 0) Theme.tag(g, font, "SELECTED", tx + font.width(name) + 6, y + 4, Theme.FRAME, a);
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
