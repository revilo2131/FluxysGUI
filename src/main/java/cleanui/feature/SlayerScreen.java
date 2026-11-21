package cleanui.feature;

import cleanui.skyblock.CollectionParser;
import cleanui.skyblock.SkillParser;
import cleanui.skyblock.SlayerParser;
import cleanui.skyblock.SlayerParser.Boss;
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
 * Slayer, shown in place of Maddox's menus.
 * <ul>
 * <li>Hub ("Slayer"): a mastery banner (total slayer level, bosses maxed) and a card per boss in its
 * own colour, with a bobbing icon, level pips that light up in turn, and an XP bar.</li>
 * <li>Boss page ("Sven Packmaster"): the boss's level and XP, then tiers I-V as cards that rise in one
 * after another, coloured by difficulty, with Hypixel's own details (health, damage, cost, reward).</li>
 * </ul>
 * Everything is read from the lore; clicks go to the real menu.
 */
public final class SlayerScreen extends MenuPage {
	public static final String HUB_TITLE = "Slayer";
	private static final int GAP = 6, BUTTON_H = 18, BUTTON_W = 104;
	private static final int BANNER_H = 30, CARD_H = 68;
	private static final int HEAD_H = 46, TIER_Y = HEAD_H + 6, TIER_H = 118;
	private static final long STAGGER_MS = 70, RISE_MS = 280;

	/** A clickable area: a boss card, a tier, or a side button. */
	private record Box(MenuItem item, int x, int y, int w, int h, int order) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/** Null on the hub; the boss on a boss page. */
	private final Boss boss;
	private final long openedAt = System.currentTimeMillis();
	private final Map<Integer, Anim> hover = new HashMap<>(), bars = new HashMap<>();

	private SlayerScreen(Boss boss, AbstractContainerScreen<?> original) {
		super(boss == null ? "Slayer" : boss.name(), new ItemStack(boss == null ? Items.GOLDEN_SWORD : Items.IRON_SWORD), boss == null ? 1 : 2, original);
		this.boss = boss;
	}

	public static SlayerScreen forTitle(String title, AbstractContainerScreen<?> original) {
		if (title.equals(HUB_TITLE)) return new SlayerScreen(null, original);
		Boss b = SlayerParser.boss(title);
		// a boss's own menu is titled exactly by its name; "... RNG Meter" etc. go elsewhere
		return b != null && title.equals(b.name()) ? new SlayerScreen(b, original) : null;
	}

	@Override
	protected String crumb() {
		return boss == null ? super.crumb() : "Slayer";
	}

	@Override
	protected String hint() {
		return boss == null ? "Pick a boss to see its tiers." : "Click a tier to start that quest.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(originalMenuButton(bx() + BW - 86, by() + BH - BUTTON_H));
	}

	// ---- shared bits -------------------------------------------------------------------------

	/** 0..1 eased entrance for the n-th element: each starts a little after the previous one. */
	private float rise(int order) {
		float p = Math.clamp((System.currentTimeMillis() - openedAt - order * STAGGER_MS) / (float) RISE_MS, 0f, 1f);
		return 1f - (1f - p) * (1f - p) * (1f - p);
	}

	private float hoverOf(int slot, boolean over) {
		Anim hv = hover.computeIfAbsent(slot, k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		return hv.get();
	}

	private float barOf(int slot, float target) {
		Anim b = bars.computeIfAbsent(slot, k -> new Anim(0f, 4f));
		b.target(target);
		return b.get();
	}

	/** Level pips: one per level, lit in the boss colour one after another as the page opens. */
	private void pips(GuiGraphicsExtractor g, int x, int y, int w, int level, int max, int color, float h, float a) {
		int pw = (w - (max - 1) * 3) / max;
		float lit = level * Math.clamp((System.currentTimeMillis() - openedAt - 150) / (90f * Math.max(1, level)), 0f, 1f);
		for (int i = 0; i < max; i++) {
			int px = x + i * (pw + 3);
			g.fill(px, y, px + pw, y + 5, Theme.argb(Theme.SLOT, a));
			float on = Math.clamp(lit - i, 0f, 1f);
			if (on > 0) g.fill(px, y, px + pw, y + 5, Theme.argb(Theme.mix(color, 0xFFFFFF, 0.25f * h), a * on));
		}
	}

	/** Buttons that aren't bosses or tiers (Auto-Slayer, Leveling Rewards, Drops, RNG Meter...), along the bottom. */
	private List<Box> buttons(List<MenuItem> others, int order) {
		List<Box> out = new ArrayList<>();
		int max = (BW - 86 - GAP) / (BUTTON_W + GAP);
		for (int i = 0; i < Math.min(max, others.size()); i++)
			out.add(new Box(others.get(i), bx() + i * (BUTTON_W + GAP), by() + BH - BUTTON_H, BUTTON_W, BUTTON_H, order + i));
		return out;
	}

	private void extractButton(GuiGraphicsExtractor g, Box b, float h, float a) {
		Theme.box(g, b.x(), b.y(), b.w(), b.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, Theme.FRAME, 0.8f * h), a);
		Theme.item(g, b.item().stack(), b.x() + 4, b.y() + 3, 12, a);
		g.text(font, Theme.fit(font, b.item().name(), b.w() - 24), b.x() + 20, b.y() + 5, Theme.argb(Theme.TEXT, a), true);
	}

	/** Everything clickable on this page, in draw order. */
	private List<Box> boxes(List<MenuItem> items) {
		return boss == null ? hubBoxes(items) : bossBoxes(items);
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		List<Box> boxes = boxes(items);
		if (giveUpIfUnreadable(boxes.isEmpty())) return;
		Box hovered = null;
		if (boss == null) extractBanner(g, boxes, a);
		else extractBossHeader(g, items, a);
		for (Box b : boxes) {
			boolean over = b.contains(mouseX, mouseY);
			if (over) hovered = b;
			float h = hoverOf(b.item().slot(), over), r = rise(b.order());
			float ba = a * r;
			if (ba <= 0.01f) continue;
			if (b.h() == BUTTON_H) extractButton(g, b, h, ba);
			else if (boss == null) extractBossCard(g, b, h, Math.round((1 - r) * 10), ba);
			else extractTier(g, b, h, Math.round((1 - r) * 14), ba);
		}
		if (hovered != null) Theme.itemTooltip(g, hovered.item().stack(), mouseX, mouseY);
	}

	// ---- hub -----------------------------------------------------------------------------------

	private List<Box> hubBoxes(List<MenuItem> items) {
		List<MenuItem> bosses = new ArrayList<>(), others = new ArrayList<>();
		for (MenuItem i : items) {
			if (i.name().isEmpty() || NAV.contains(i.name())) continue;
			Boss b = SlayerParser.boss(i.name());
			if (b != null && SlayerParser.tier(i.name()) < 0 && !i.name().contains("RNG")) bosses.add(i);
			else others.add(i);
		}
		// Hypixel's own order, but by our boss list so the colours read left to right like a rainbow
		bosses.sort((p, q) -> Integer.compare(SlayerParser.BOSSES.indexOf(SlayerParser.boss(p.name())), SlayerParser.BOSSES.indexOf(SlayerParser.boss(q.name()))));
		List<Box> out = new ArrayList<>();
		int cols = 3, w = (BW - (cols - 1) * GAP) / cols, top = by() + BANNER_H + GAP;
		for (int i = 0; i < bosses.size(); i++)
			out.add(new Box(bosses.get(i), bx() + (i % cols) * (w + GAP), top + (i / cols) * (CARD_H + GAP), w, CARD_H, i + 1));
		out.addAll(buttons(others, bosses.size() + 1));
		return out;
	}

	/** "SLAYER MASTERY": all boss levels added up, as a bar, plus how many bosses are maxed. */
	private void extractBanner(GuiGraphicsExtractor g, List<Box> boxes, float a) {
		int total = 0, max = 0, maxed = 0;
		for (Box b : boxes) {
			if (b.h() != CARD_H) continue;
			Boss bs = SlayerParser.boss(b.item().name());
			int lvl = Math.max(0, SlayerParser.progress(b.item().lore()).level());
			total += lvl;
			max += bs.maxLevel();
			if (lvl >= bs.maxLevel()) maxed++;
		}
		int x = bx(), y = by(), w = BW;
		float r = rise(0), ra = a * r;
		Theme.box(g, x, y, w, BANNER_H, Theme.ROW, ra, Theme.mix(Theme.LINE, Theme.ACCENT, 0.5f), ra);
		g.text(font, "SLAYER MASTERY", x + 8, y + 5, Theme.argb(Theme.ACCENT, ra), true);
		String lv = total + " / " + max + " levels";
		g.text(font, lv, x + 8 + font.width("SLAYER MASTERY") + 8, y + 5, Theme.argb(Theme.TEXT, ra), true);
		String m = maxed + " maxed";
		int bw = Theme.badge(g, font, m, x + w - 8, y + 4, maxed > 0 ? Theme.ACCENT : Theme.FRAME_DIM, ra);
		Theme.leader(g, x + 8 + font.width("SLAYER MASTERY") + 8 + font.width(lv) + 4, x + w - 8 - bw - 4, y + 11, ra);
		// one segment per boss, each in its colour, filled by that boss's level
		int segW = (w - 16 - (SlayerParser.BOSSES.size() - 1) * 2) / SlayerParser.BOSSES.size();
		for (int i = 0; i < SlayerParser.BOSSES.size(); i++) {
			Boss bs = SlayerParser.BOSSES.get(i);
			int lvl = 0;
			for (Box b : boxes) if (b.h() == CARD_H && SlayerParser.boss(b.item().name()) == bs) lvl = Math.max(0, SlayerParser.progress(b.item().lore()).level());
			int sx = x + 8 + i * (segW + 2);
			g.fill(sx, y + BANNER_H - 9, sx + segW, y + BANNER_H - 5, Theme.argb(Theme.SLOT, ra));
			float f = barOf(-100 - i, lvl / (float) bs.maxLevel());
			g.fill(sx, y + BANNER_H - 9, sx + Math.round(segW * f), y + BANNER_H - 5, Theme.argb(bs.color(), ra));
		}
	}

	/** Boss card: tinted by the boss colour, bobbing icon, name, level badge, pips, XP bar; locked ones dimmed. */
	private void extractBossCard(GuiGraphicsExtractor g, Box b, float h, int drop, float a) {
		Boss bs = SlayerParser.boss(b.item().name());
		SlayerParser.Progress p = SlayerParser.progress(b.item().lore());
		boolean maxed = p.level() >= bs.maxLevel(), locked = p.locked() && p.level() <= 0;
		int x = b.x(), y = b.y() + drop - Math.round(2 * h), w = b.w(), color = locked ? Theme.TEXT_MUTED : bs.color();
		int border = maxed ? Theme.ACCENT : Theme.mix(Theme.mix(Theme.LINE, color, 0.45f), color, h);
		Theme.box(g, x, y, w, CARD_H, Theme.mix(Theme.mix(Theme.ROW, color, 0.08f + 0.06f * h), Theme.ROW_HOVER, 0.4f * h), a, border, a);
		g.fill(x + 1, y + 1, x + w - 1, y + 3, Theme.argb(color, a)); // colour cap
		// icon: gentle bob, its own phase per card so they don't move in lockstep
		double t = System.nanoTime() / 1e9;
		int bob = locked ? 0 : (int) Math.round(Math.sin(t * 2.2 + b.order()) * 1.5);
		Theme.slot(g, b.item().stack(), x + 8, y + 10 + bob, 1.15f + 0.15f * h, a);
		int tx = x + 32, right = x + w - 7;
		String lvl = p.level() >= 0 ? "LVL " + p.level() : locked ? "LOCKED" : "?";
		// name and slayer type get full lines; the level badge sits at the end of the pip row
		String name = Theme.fit(font, bs.name(), right - tx);
		if (maxed) Theme.shimmer(g, font, name, tx, y + 8, a);
		else g.text(font, name, tx, y + 8, Theme.argb(locked ? Theme.TEXT_MUTED : Theme.TITLE, a), true);
		g.text(font, Theme.fit(font, bs.type() + " Slayer", right - tx), tx, y + 20, Theme.argb(Theme.TEXT_MUTED, a), true);
		int bw = Theme.badge(g, font, lvl, right, y + 33, maxed ? Theme.ACCENT : color, a);
		pips(g, x + 8, y + 36, w - 16 - bw - 6, Math.max(0, p.level()), bs.maxLevel(), maxed ? Theme.ACCENT : color, h, a);
		String xp = maxed ? "MAXED" : p.done() != null ? SkillParser.compact(p.done()) + " / " + SkillParser.compact(p.total()) + " XP"
				: locked ? "Locked" : "";
		float f = barOf(b.item().slot(), maxed ? 1f : p.percent() != null ? p.percent() : 0f);
		Theme.bar(g, font, x + 8, y + CARD_H - 17, w - 16, 10, f, maxed ? Theme.ACCENT : Theme.mix(color, 0x000000, 0.25f), xp, a);
	}

	// ---- boss page -----------------------------------------------------------------------------

	private List<Box> bossBoxes(List<MenuItem> items) {
		List<MenuItem> tiers = new ArrayList<>(), others = new ArrayList<>();
		for (MenuItem i : items) {
			if (i.name().isEmpty() || NAV.contains(i.name()) || i.name().equals(boss.name())) continue;
			if (SlayerParser.tier(i.name()) > 0) tiers.add(i);
			else others.add(i);
		}
		tiers.sort((p, q) -> Integer.compare(SlayerParser.tier(p.name()), SlayerParser.tier(q.name())));
		List<Box> out = new ArrayList<>();
		int n = Math.max(1, tiers.size()), w = (BW - (n - 1) * GAP) / n;
		for (int i = 0; i < tiers.size(); i++)
			out.add(new Box(tiers.get(i), bx() + i * (w + GAP), by() + TIER_Y, w, TIER_H, i + 1));
		out.addAll(buttons(others, tiers.size() + 1));
		return out;
	}

	/** The boss item in this menu (its name exactly), for level and XP; may be missing. */
	private MenuItem bossItem(List<MenuItem> items) {
		return items.stream().filter(i -> i.name().equals(boss.name())).findFirst().orElse(null);
	}

	/** Header in the boss colour: big icon, name, slayer type, level badge, pips and XP bar. */
	private void extractBossHeader(GuiGraphicsExtractor g, List<MenuItem> items, float a) {
		MenuItem item = bossItem(items);
		SlayerParser.Progress p = item != null ? SlayerParser.progress(item.lore()) : new SlayerParser.Progress(-1, null, null, null, false);
		boolean maxed = p.level() >= boss.maxLevel();
		int x = bx(), y = by(), w = BW, color = boss.color();
		float ra = a * rise(0);
		Theme.box(g, x, y, w, HEAD_H, Theme.mix(Theme.ROW, color, 0.1f), ra, maxed ? Theme.ACCENT : Theme.mix(Theme.LINE, color, 0.6f), ra);
		g.fill(x + 1, y + 1, x + 4, y + HEAD_H - 1, Theme.argb(color, ra));
		ItemStack icon = item != null ? item.stack() : new ItemStack(Items.IRON_SWORD);
		double t = System.nanoTime() / 1e9;
		Theme.slot(g, icon, x + 10, y + 12 + (int) Math.round(Math.sin(t * 2.0) * 1.5), 1.35f, ra);
		int tx = x + 38, right = x + w - 8;
		String lvl = p.level() >= 0 ? "LVL " + p.level() + " / " + boss.maxLevel() : "LVL ?";
		int bw = Theme.badge(g, font, lvl, right, y + 6, maxed ? Theme.ACCENT : color, ra);
		if (maxed) Theme.shimmer(g, font, boss.name(), tx, y + 7, ra);
		else g.text(font, boss.name(), tx, y + 7, Theme.argb(Theme.TITLE, ra), true);
		g.text(font, boss.type() + " Slayer", tx + font.width(boss.name()) + 8, y + 7, Theme.argb(Theme.TEXT_MUTED, ra), true);
		Theme.leader(g, tx + font.width(boss.name()) + 8 + font.width(boss.type() + " Slayer") + 4, right - bw - 4, y + 13, ra);
		pips(g, tx, y + 20, 120, Math.max(0, p.level()), boss.maxLevel(), maxed ? Theme.ACCENT : color, 0f, ra);
		String xp = maxed ? "MAXED" : p.done() != null ? SkillParser.compact(p.done()) + " / " + SkillParser.compact(p.total()) + " XP to LVL " + (p.level() + 1) : null;
		float f = barOf(-1, maxed ? 1f : p.percent() != null ? p.percent() : 0f);
		Theme.bar(g, font, tx, y + HEAD_H - 16, right - tx, 10, f, maxed ? Theme.ACCENT : Theme.mix(color, 0x000000, 0.2f), xp, ra);
	}

	/** Tier card: big numeral in the difficulty colour, the tier's item, Hypixel's details, and a start strip. */
	private void extractTier(GuiGraphicsExtractor g, Box b, float h, int drop, float a) {
		int tier = SlayerParser.tier(b.item().name()), color = SlayerParser.tierColor(tier);
		SlayerParser.Progress p = SlayerParser.progress(b.item().lore());
		boolean locked = p.locked();
		int x = b.x(), y = b.y() + drop - Math.round(3 * h), w = b.w(), shown = locked ? Theme.TEXT_MUTED : color;
		Theme.box(g, x, y, w, TIER_H, Theme.mix(Theme.mix(Theme.ROW, shown, 0.07f + 0.08f * h), Theme.ROW_HOVER, 0.3f * h), a,
				Theme.mix(Theme.mix(Theme.LINE, shown, 0.5f), shown, h), a);
		g.fill(x + 1, y + 1, x + w - 1, y + 3, Theme.argb(shown, a));
		String numeral = CollectionParser.roman(tier);
		// big numeral: drawn at 2x
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + 8, y + 9);
		pose.scale(2f, 2f);
		g.text(font, numeral, 0, 0, Theme.argb(shown, a), true);
		pose.popMatrix();
		Theme.item(g, b.item().stack(), x + w - 24, y + 8, Math.round(16 + 2 * h), a);
		g.text(font, "TIER " + numeral, x + 8, y + 29, Theme.argb(Theme.TEXT_MUTED, a), true);
		int ly = y + 42;
		for (String[] kv : SlayerParser.details(b.item().lore(), 4)) {
			g.text(font, Theme.fit(font, kv[0], w - 12), x + 6, ly, Theme.argb(Theme.TEXT_MUTED, a), true);
			g.text(font, Theme.fit(font, kv[1], w - 12), x + 6, ly + 9, Theme.argb(locked ? Theme.TEXT_MUTED : Theme.TEXT, a), true);
			ly += 19;
			if (ly > y + TIER_H - 26) break;
		}
		String action = locked ? "LOCKED" : "START";
		int sy = y + TIER_H - 15;
		Theme.box(g, x + 5, sy, w - 10, 11, Theme.mix(Theme.SLOT, shown, locked ? 0.05f : 0.2f + 0.3f * h), a, shown, a);
		g.text(font, action, x + (w - font.width(action)) / 2, sy + 2, Theme.argb(locked ? Theme.TEXT_MUTED : Theme.mix(shown, 0xFFFFFF, 0.4f), a), false);
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		for (Box b : boxes(menuItems()))
			if (b.contains(event.x(), event.y())) {
				clickAs(b.item().slot(), event);
				return true;
			}
		return false;
	}
}
