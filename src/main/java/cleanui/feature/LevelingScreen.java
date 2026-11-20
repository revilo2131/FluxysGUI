package cleanui.feature;

import cleanui.data.Config;
import cleanui.skyblock.CollectionParser;
import cleanui.skyblock.LevelParser;
import cleanui.skyblock.LevelParser.State;
import cleanui.skyblock.SkillParser;
import cleanui.ui.Anim;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SkyBlock Leveling, shown in place of Hypixel's menu: a level card with progress to the next
 * level, the "level road" of upcoming reward levels (current, next, milestone), totals, and the
 * menu's other buttons on the side. Clicks go to the real menu.
 */
public final class LevelingScreen extends MenuPage {
	public static final String MENU_TITLE = "SkyBlock Leveling";
	private static final int MAIN_W = 296, SIDE_X = MAIN_W + 10, SIDE_W = BW - SIDE_X;
	private static final int CARD_H = 52, ROAD_Y = 58, TILE_Y = 70, TILE_H = 40, STATS_Y = 118;
	private static final int ROW_H = 22, ROW_STEP = 24;
	private static final int GREEN = 5, MILESTONE = 0xFFD54A;

	private record Box(MenuItem item, int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record Layout(MenuItem ranking, LevelParser.Summary summary, List<LevelParser.Step> steps, List<Box> boxes) {}

	private final Map<Integer, Anim> hover = new HashMap<>();
	private final Anim fill = new Anim(0f, 5f);

	public LevelingScreen(AbstractContainerScreen<?> original) {
		super("SkyBlock Leveling", new ItemStack(Items.EXPERIENCE_BOTTLE), 1, original);
	}

	public static LevelingScreen forTitle(String title, AbstractContainerScreen<?> original) {
		return Config.get().skills && title.equals(MENU_TITLE) ? new LevelingScreen(original) : null;
	}

	@Override
	protected String hint() {
		return "Hover a level to see its rewards.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(originalMenuButton(bx() + BW - 86, by() + BH - 18));
	}

	/** Ranking item, "Level N" steps as road tiles (boxes first), everything else as side rows. */
	private Layout layout() {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		MenuItem ranking = null;
		LevelParser.Summary summary = null;
		List<MenuItem> levels = new ArrayList<>(), rest = new ArrayList<>();
		for (MenuItem item : items) {
			String name = item.name();
			if (name.isEmpty() || NAV.contains(name)) continue;
			LevelParser.Summary s = summary == null ? LevelParser.summary(item.lore()) : null;
			if (s != null) {
				ranking = item;
				summary = s;
			} else if (LevelParser.step(name, item.lore()) != null) levels.add(item);
			else rest.add(item);
		}
		levels.sort(Comparator.comparingInt(i -> LevelParser.step(i.name(), i.lore()).level()));
		List<LevelParser.Step> steps = new ArrayList<>();
		List<Box> boxes = new ArrayList<>();
		int n = levels.size(), tw = n == 0 ? 0 : (MAIN_W - (n - 1) * 4) / n;
		for (int i = 0; i < n; i++) {
			MenuItem l = levels.get(i);
			steps.add(LevelParser.step(l.name(), l.lore()));
			boxes.add(new Box(l, bx() + i * (tw + 4), by() + TILE_Y, tw, TILE_H));
		}
		int sy = by() + 14;
		for (MenuItem r : rest) {
			if (sy + ROW_H > by() + BH - 22) break; // leave room for "Original menu"
			boxes.add(new Box(r, bx() + SIDE_X, sy, SIDE_W, ROW_H));
			sy += ROW_STEP;
		}
		return new Layout(ranking, summary, steps, boxes);
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Layout l = layout();
		if (giveUpIfUnreadable(l.summary() == null && l.steps().isEmpty())) return;
		LevelParser.Step next = l.steps().stream().filter(s -> s.state() == State.NEXT).findFirst().orElse(null);
		LevelParser.Step milestone = l.steps().stream().filter(s -> s.state() == State.MILESTONE).findFirst().orElse(null);
		extractCard(g, l, next, mouseX, mouseY, a);
		Theme.divider(g, font, "LEVEL ROAD", bx(), by() + ROAD_Y, MAIN_W, a);
		Theme.divider(g, font, "OTHER", bx() + SIDE_X, by(), SIDE_W, a);
		Box hovered = null;
		for (int i = 0; i < l.boxes().size(); i++) {
			Box b = l.boxes().get(i);
			boolean over = b.contains(mouseX, mouseY);
			if (over) hovered = b;
			float h = hoverOf(b, over);
			if (i < l.steps().size()) extractTile(g, b, l.steps().get(i), h, a);
			else extractRow(g, b, h, a);
		}
		extractStats(g, l.summary(), milestone, a);
		if (hovered != null) Theme.itemTooltip(g, hovered.item().stack(), mouseX, mouseY);
	}

	private float hoverOf(Box b, boolean over) {
		Anim hv = hover.computeIfAbsent(b.item().slot(), k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		return hv.get();
	}

	/** Level badge, progress to the next level, XP bar; hover shows the ranking item. */
	private void extractCard(GuiGraphicsExtractor g, Layout l, LevelParser.Step next, int mouseX, int mouseY, float a) {
		int x = bx(), y = by(), w = MAIN_W, color = Theme.color(GREEN);
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CARD_H;
		Theme.box(g, x, y, w, CARD_H, over ? Theme.ROW_HOVER : Theme.ROW, a, over ? color : Theme.LINE, a);
		g.fill(x + 1, y + 1, x + 3, y + CARD_H - 1, Theme.argb(color, a));
		Theme.slot(g, l.ranking() != null ? l.ranking().stack() : new ItemStack(Items.EXPERIENCE_BOTTLE), x + 8, y + 7, 1f, a);
		int tx = x + 32, right = x + w - 8;
		int level = l.summary() != null ? l.summary().level() : next != null ? next.level() - 1 : -1;
		int bw = level >= 0 ? Theme.badge(g, font, String.valueOf(level), right, y + 6, Theme.ACCENT, a) : 0;
		g.text(font, "SkyBlock Level", tx, y + 7, Theme.argb(Theme.TITLE, a), true);
		Theme.leader(g, tx + font.width("SkyBlock Level") + 3, right - bw - 3, y + 13, a);
		fill.target(next != null && next.percent() != null ? next.percent() : 0f);
		String xp = null;
		if (next != null && next.percent() != null) {
			Theme.textLeader(g, font, "Progress to " + next.level(), Theme.TEXT_MUTED, Math.round(next.percent() * 100) + "%", Theme.TEXT, tx, y + 19, right - tx, a);
			xp = SkillParser.compact(next.done()) + " / " + SkillParser.compact(next.total()) + " XP";
		}
		Theme.bar(g, font, x + 8, y + CARD_H - 15, w - 16, 11, fill.get(), Theme.mix(color, 0x000000, 0.2f), xp, a);
		if (over && l.ranking() != null) Theme.itemTooltip(g, l.ranking().stack(), mouseX, mouseY);
	}

	/** Road tile: level number, first reward, and a fill line (full = reached, partial = next). */
	private void extractTile(GuiGraphicsExtractor g, Box b, LevelParser.Step s, float h, float a) {
		int color = switch (s.state()) {
			case CURRENT -> Theme.color(GREEN);
			case NEXT -> Theme.ACCENT;
			case MILESTONE -> MILESTONE;
			case LOCKED -> Theme.FRAME_DIM;
		};
		boolean dim = s.state() == State.LOCKED;
		int x = b.x(), y = b.y(), w = b.w();
		Theme.box(g, x, y, w, b.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a,
				dim ? Theme.mix(Theme.LINE, Theme.FRAME, 0.8f * h) : Theme.mix(color, Theme.TEXT, 0.3f * h), a);
		String num = String.valueOf(s.level());
		int numColor = dim ? Theme.TEXT_MUTED : s.state() == State.MILESTONE ? MILESTONE : Theme.TITLE;
		g.text(font, num, x + (w - font.width(num)) / 2, y + 5, Theme.argb(numColor, a), true);
		if (s.state() == State.MILESTONE) g.text(font, "★", x + w - 9, y + 3, Theme.argb(MILESTONE, a), true);
		if (!s.rewards().isEmpty()) {
			String r = Theme.fit(font, shortReward(s.rewards().getFirst()) + (s.rewards().size() > 1 ? " +" + (s.rewards().size() - 1) : ""), w - 6);
			g.text(font, r, x + (w - font.width(r)) / 2, y + 17, Theme.argb(Theme.TEXT_MUTED, a), true);
		}
		float p = switch (s.state()) {
			case CURRENT -> 1f;
			case NEXT -> s.percent() != null ? s.percent() : 0f;
			default -> 0f;
		};
		g.fill(x + 4, y + b.h() - 6, x + w - 4, y + b.h() - 4, Theme.argb(Theme.SLOT, a));
		g.fill(x + 4, y + b.h() - 6, x + 4 + Math.round((w - 8) * p), y + b.h() - 4, Theme.argb(color, a));
	}

	/** "+5 Health" → "+5 HP", "Soulflow Emblem ⸎" → "Emblem": fits a 46px tile; the tooltip has the full text. */
	private static String shortReward(String r) {
		if (r.contains("Emblem")) return "Emblem";
		return r.replace("Health", "HP").replace("Strength", "Str").replace("Defense", "Def")
				.replace("Intelligence", "Int").replace("Magic Find", "MF");
	}

	/** Total XP, share of all SkyBlock XP tasks done, and the next milestone. */
	private void extractStats(GuiGraphicsExtractor g, LevelParser.Summary s, LevelParser.Step milestone, float a) {
		int x = bx(), y = by() + STATS_Y;
		Theme.divider(g, font, "PROGRESS", x, y, MAIN_W, a);
		int line = y + 13;
		if (s != null && s.xp() != null) {
			Theme.textLeader(g, font, "Total SkyBlock XP", Theme.TEXT, s.xp(), Theme.ACCENT, x, line, MAIN_W, a);
			line += 13;
		}
		if (s != null && s.tasks() != null) {
			Theme.textLeader(g, font, "XP tasks completed", Theme.TEXT, String.format("%.1f%%", s.tasks() * 100), Theme.ACCENT, x, line, MAIN_W, a);
			g.fill(x, line + 10, x + MAIN_W, line + 12, Theme.argb(Theme.SLOT, a));
			g.fill(x, line + 10, x + Math.round(MAIN_W * s.tasks()), line + 12, Theme.argb(Theme.color(GREEN), a));
			line += 17;
		}
		if (milestone != null)
			Theme.textLeader(g, font, "Next milestone: Level " + milestone.level(), Theme.TEXT, milestone.left() != null ? milestone.left() + " left" : "",
					MILESTONE, x, line, MAIN_W, a);
	}

	/** Side button: stripe, item, name ···· x/y or ON/OFF, bar if it has progress. */
	private void extractRow(GuiGraphicsExtractor g, Box b, float h, float a) {
		CollectionParser.Entry e = CollectionParser.parse(b.item().name(), b.item().lore());
		int color = Theme.mix(Theme.FRAME_DIM, Theme.FRAME, 0.3f), x = b.x(), y = b.y(), w = b.w();
		Theme.box(g, x, y, w, b.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, color, 0.7f * h), a);
		g.fill(x + 1, y + 1, x + 3 + Math.round(2 * h), y + b.h() - 1, Theme.argb(color, a));
		Theme.item(g, b.item().stack(), x + 6, y + 3, Math.round(13 + 2 * h), a);
		int tx = x + 24, right = x + w - 6, vw = 0;
		String value = e.done() != null ? SkillParser.compact(e.done()) + "/" + SkillParser.compact(e.total()) : null;
		int valueColor = Theme.ACCENT;
		if (b.item().lore().contains("ENABLED")) { value = "ON"; valueColor = Theme.color(GREEN); }
		else if (b.item().lore().contains("DISABLED")) { value = "OFF"; valueColor = Theme.color(3); }
		if (value != null) {
			g.text(font, value, right - font.width(value), y + 5, Theme.argb(valueColor, a), true);
			vw = font.width(value) + 6;
		}
		g.text(font, Theme.fit(font, e.name().replace("⚑ ", ""), right - tx - vw), tx, y + 5, Theme.argb(Theme.TEXT, a), true);
		if (e.percent() != null) {
			g.fill(tx, y + b.h() - 4, right, y + b.h() - 2, Theme.argb(Theme.SLOT, a));
			g.fill(tx, y + b.h() - 4, tx + Math.round((right - tx) * e.percent()), y + b.h() - 2, Theme.argb(color, a));
		}
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		Layout l = layout();
		if (l.ranking() != null && event.x() >= bx() && event.x() < bx() + MAIN_W && event.y() >= by() && event.y() < by() + CARD_H) {
			click(l.ranking().slot(), event.button());
			return true;
		}
		for (Box b : l.boxes())
			if (b.contains(event.x(), event.y())) {
				click(b.item().slot(), event.button());
				return true;
			}
		return false;
	}
}
