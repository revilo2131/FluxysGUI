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
 * <li>Hub ("Slayer"): a mastery banner, your ongoing quest with a Cancel button, and a card per boss
 * in its own colour: bobbing icon, level pips that light up in turn, XP bar (XP from the Slayer
 * Leaderboards item), rank and best tier slain. Locked bosses say what unlocks them.</li>
 * <li>Boss page ("Revenant Horror"): level and XP from the Boss Leveling Rewards item, then tiers
 * I-V as cards that rise in one after another, coloured by difficulty, with Hypixel's details.</li>
 * </ul>
 * Everything is read from the lore; every click goes to the real menu.
 */
public final class SlayerScreen extends MenuPage {
	public static final String HUB_TITLE = "Slayer";
	private static final int GAP = 6, BUTTON_H = 18, ORIGINAL_W = 86;
	private static final int BANNER_H = 26, QUEST_H = 20, CARD_H = 56;
	private static final int HEAD_H = 46, TIER_Y = HEAD_H + 6, TIER_H = 118;
	private static final long STAGGER_MS = 70, RISE_MS = 280;
	private static final String QUEST_ITEM = "Ongoing Slayer Quest", LEADERBOARD_ITEM = "Slayer Leaderboards";
	private static final String BUFF_ITEM = "Global Combat Wisdom Buff", LEVELING_ITEM = "Boss Leveling Rewards";

	private enum Kind { BOSS, TIER, BUTTON, CANCEL }

	/** A clickable area on the page; order drives the staggered entrance. */
	private record Box(MenuItem item, Kind kind, int x, int y, int w, int h, int order) {
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
		addRenderableWidget(originalMenuButton(bx() + BW - ORIGINAL_W, by() + BH - BUTTON_H));
	}

	// ---- shared --------------------------------------------------------------------------------

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

	private float barOf(int key, float target) {
		Anim b = bars.computeIfAbsent(key, k -> new Anim(0f, 4f));
		b.target(target);
		return b.get();
	}

	private static MenuItem named(List<MenuItem> items, String name) {
		return items.stream().filter(i -> i.name().equals(name)).findFirst().orElse(null);
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

	private static final int CHIP_W = 22;

	/** "Boss Leveling Rewards" → "Leveling", "Sven Packmaster RNG Meter" → "RNG Meter": fits a button. */
	private static String shortLabel(String name) {
		Boss b = SlayerParser.boss(name);
		String s = b != null ? name.replace(b.name(), "") : name;
		// whole words only: "Auto-Slayer" keeps its "Slayer"
		return s.replaceAll("(?<![\\w-])(Boss|Slayer|Global|Combat|Rewards)(?![\\w-])", "").replaceAll("\\s+", " ").strip();
	}

	private static String toggle(List<String> lore) {
		return lore.contains("Currently: Enabled") ? "ON" : lore.contains("Currently: Disabled") ? "OFF" : null;
	}

	private static boolean clickable(MenuItem i) {
		return i.lore().stream().anyMatch(l -> l.startsWith("Click"));
	}

	/**
	 * The menu's other items along the bottom, beside "Original menu": clickable ones as labelled
	 * buttons, info-only ones (Leaderboards, Wisdom Buff: their numbers are on the boss cards
	 * already) as icon chips you hover for Hypixel's text.
	 */
	private List<Box> buttons(List<MenuItem> others, int order) {
		List<MenuItem> sorted = new ArrayList<>(others);
		sorted.sort((p, q) -> Boolean.compare(!clickable(p), !clickable(q)));
		List<Box> out = new ArrayList<>();
		int x = bx(), end = bx() + BW - ORIGINAL_W - GAP;
		for (MenuItem i : sorted) {
			String state = toggle(i.lore());
			// icon (20) + label + right padding (5), plus the ON/OFF tag; must match extractButton
			int w = clickable(i) ? 27 + font.width(shortLabel(i.name())) + (state != null ? font.width(state) + 6 : 0) : CHIP_W;
			if (x + w > end) w = CHIP_W; // no room for the label: icon only
			if (x + w > end) break;
			out.add(new Box(i, Kind.BUTTON, x, by() + BH - BUTTON_H, w, BUTTON_H, order + out.size()));
			x += w + 4;
		}
		return out;
	}

	/** Button: icon and short name; toggles ("Currently: Enabled") get an ON/OFF tag; chips are icon only. */
	private void extractButton(GuiGraphicsExtractor g, Box b, float h, float a) {
		boolean chip = b.w() == CHIP_W;
		Theme.box(g, b.x(), b.y(), b.w(), b.h(), Theme.mix(chip ? Theme.PANEL : Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, Theme.FRAME, 0.8f * h), a);
		Theme.item(g, b.item().stack(), b.x() + (chip ? 5 : 4), b.y() + 3, 12, a);
		if (chip) return;
		String state = toggle(b.item().lore());
		int right = b.x() + b.w() - 5, tw = 0;
		if (state != null) {
			tw = font.width(state) + 6;
			g.text(font, state, right - font.width(state), b.y() + 5, Theme.argb(state.equals("ON") ? Theme.color(5) : Theme.color(3), a), true);
		}
		g.text(font, Theme.fit(font, shortLabel(b.item().name()), right - (b.x() + 20) - tw), b.x() + 20, b.y() + 5, Theme.argb(Theme.TEXT, a), true);
	}

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
		Map<Boss, SlayerParser.Stats> stats = Map.of();
		if (boss == null) {
			MenuItem lb = named(items, LEADERBOARD_ITEM), buff = named(items, BUFF_ITEM);
			stats = SlayerParser.stats(lb != null ? lb.lore() : List.of(), buff != null ? buff.lore() : List.of());
			extractBanner(g, boxes, a);
			MenuItem quest = named(items, QUEST_ITEM);
			if (quest != null) extractQuest(g, quest, boxes, mouseX, mouseY, a);
		} else extractBossHeader(g, items, a);
		for (Box b : boxes) {
			boolean over = b.contains(mouseX, mouseY);
			if (over) hovered = b;
			float h = hoverOf(b.item().slot(), over), r = rise(b.order()), ba = a * r;
			if (ba <= 0.01f) continue;
			switch (b.kind()) {
				case BUTTON -> extractButton(g, b, h, ba);
				case BOSS -> extractBossCard(g, b, stats.get(SlayerParser.boss(b.item().name())), h, Math.round((1 - r) * 10), ba);
				case TIER -> extractTier(g, b, h, Math.round((1 - r) * 14), ba);
				case CANCEL -> extractCancel(g, b, h, ba);
			}
		}
		if (hovered != null) Theme.itemTooltip(g, hovered.item().stack(), mouseX, mouseY);
	}

	// ---- hub -----------------------------------------------------------------------------------

	private List<Box> hubBoxes(List<MenuItem> items) {
		List<MenuItem> bosses = new ArrayList<>(), others = new ArrayList<>();
		MenuItem quest = null;
		for (MenuItem i : items) {
			String name = i.name();
			if (name.isEmpty() || NAV.contains(name) || name.startsWith("Not released")) continue;
			if (name.equals(QUEST_ITEM)) quest = i;
			else if (SlayerParser.boss(name) != null && SlayerParser.tier(name) < 0 && !name.contains("RNG")) bosses.add(i);
			else others.add(i);
		}
		bosses.sort((p, q) -> Integer.compare(SlayerParser.BOSSES.indexOf(SlayerParser.boss(p.name())), SlayerParser.BOSSES.indexOf(SlayerParser.boss(q.name()))));
		List<Box> out = new ArrayList<>();
		int top = by() + BANNER_H + GAP;
		if (quest != null) {
			// the strip itself is drawn by extractQuest; only its Cancel button is clickable
			out.add(new Box(quest, Kind.CANCEL, bx() + BW - 64, top + 3, 60, QUEST_H - 6, 1));
			top += QUEST_H + GAP;
		}
		int cols = 3, w = (BW - (cols - 1) * GAP) / cols;
		for (int i = 0; i < bosses.size(); i++)
			out.add(new Box(bosses.get(i), Kind.BOSS, bx() + (i % cols) * (w + GAP), top + (i / cols) * (CARD_H + GAP), w, CARD_H, i + 2));
		out.addAll(buttons(others, bosses.size() + 2));
		return out;
	}

	/** "SLAYER MASTERY": all boss levels added up, a segment per boss in its colour, and how many are maxed. */
	private void extractBanner(GuiGraphicsExtractor g, List<Box> boxes, float a) {
		int total = 0, max = 0, maxed = 0;
		Map<Boss, Integer> levels = new HashMap<>();
		for (Box b : boxes) {
			if (b.kind() != Kind.BOSS) continue;
			Boss bs = SlayerParser.boss(b.item().name());
			int lvl = Math.max(0, SlayerParser.progress(b.item().lore()).level());
			levels.put(bs, lvl);
			total += lvl;
			max += bs.maxLevel();
			if (lvl >= bs.maxLevel()) maxed++;
		}
		int x = bx(), y = by(), w = BW;
		float ra = a * rise(0);
		Theme.box(g, x, y, w, BANNER_H, Theme.ROW, ra, Theme.mix(Theme.LINE, Theme.ACCENT, 0.5f), ra);
		g.text(font, "SLAYER MASTERY", x + 8, y + 5, Theme.argb(Theme.ACCENT, ra), true);
		String lv = total + " / " + max + " levels";
		int lx = x + 8 + font.width("SLAYER MASTERY") + 8;
		g.text(font, lv, lx, y + 5, Theme.argb(Theme.TEXT, ra), true);
		int bw = Theme.badge(g, font, maxed + " maxed", x + w - 8, y + 4, maxed > 0 ? Theme.ACCENT : Theme.FRAME_DIM, ra);
		Theme.leader(g, lx + font.width(lv) + 4, x + w - 8 - bw - 4, y + 11, ra);
		int segW = (w - 16 - (SlayerParser.BOSSES.size() - 1) * 2) / SlayerParser.BOSSES.size();
		for (int i = 0; i < SlayerParser.BOSSES.size(); i++) {
			Boss bs = SlayerParser.BOSSES.get(i);
			int sx = x + 8 + i * (segW + 2);
			g.fill(sx, y + BANNER_H - 7, sx + segW, y + BANNER_H - 4, Theme.argb(Theme.SLOT, ra));
			float f = barOf(-100 - i, levels.getOrDefault(bs, 0) / (float) bs.maxLevel());
			g.fill(sx, y + BANNER_H - 7, sx + Math.round(segW * f), y + BANNER_H - 4, Theme.argb(bs.color(), ra));
		}
	}

	/** Ongoing quest strip in the quest boss's colour: "ONGOING QUEST ☠ Sven Packmaster IV · Kill Wolves…". */
	private void extractQuest(GuiGraphicsExtractor g, MenuItem quest, List<Box> boxes, int mouseX, int mouseY, float a) {
		SlayerParser.Quest q = SlayerParser.quest(quest.lore());
		Boss qb = q != null ? SlayerParser.boss(q.boss()) : null;
		int color = qb != null ? qb.color() : Theme.ACCENT, x = bx(), y = by() + BANNER_H + GAP, w = BW;
		float ra = a * rise(1);
		// slow breathing border: the quest is live
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 1e9 * 2.5);
		Theme.box(g, x, y, w, QUEST_H, Theme.mix(Theme.ROW, color, 0.1f), ra, Theme.mix(Theme.mix(Theme.LINE, color, 0.5f), color, pulse), ra);
		g.fill(x + 1, y + 1, x + 3, y + QUEST_H - 1, Theme.argb(color, ra));
		g.text(font, "ONGOING QUEST", x + 8, y + 6, Theme.argb(Theme.ACCENT, ra), true);
		int tx = x + 8 + font.width("ONGOING QUEST") + 8, right = x + w - 70;
		String bossText = q != null ? "☠ " + q.boss() : "Active quest";
		g.text(font, Theme.fit(font, bossText, right - tx), tx, y + 6, Theme.argb(color, ra), true);
		int ox = tx + font.width(bossText) + 8;
		if (q != null && ox < right) g.text(font, Theme.fit(font, q.objective(), right - ox), ox, y + 6, Theme.argb(Theme.TEXT_MUTED, ra), true);
	}

	/** The quest strip's Cancel button: red, so it reads as the one destructive action here. */
	private void extractCancel(GuiGraphicsExtractor g, Box b, float h, float a) {
		int red = Theme.color(3);
		Theme.box(g, b.x(), b.y(), b.w(), b.h(), Theme.mix(Theme.ROW, red, 0.18f + 0.3f * h), a, red, a);
		String t = "CANCEL";
		g.text(font, t, b.x() + (b.w() - font.width(t)) / 2, b.y() + (b.h() - 8) / 2, Theme.argb(Theme.mix(red, 0xFFFFFF, 0.5f + 0.4f * h), a), false);
	}

	/** Boss card: tinted by the boss colour, bobbing icon, name, type with rank, pips + level badge, XP bar. */
	private void extractBossCard(GuiGraphicsExtractor g, Box b, SlayerParser.Stats st, float h, int drop, float a) {
		Boss bs = SlayerParser.boss(b.item().name());
		SlayerParser.Progress p = SlayerParser.progress(b.item().lore());
		boolean maxed = p.level() >= bs.maxLevel(), locked = p.locked() && p.level() < 0;
		int x = b.x(), y = b.y() + drop - Math.round(2 * h), w = b.w(), color = locked ? Theme.TEXT_MUTED : bs.color();
		int border = maxed ? Theme.ACCENT : Theme.mix(Theme.mix(Theme.LINE, color, 0.45f), color, h);
		Theme.box(g, x, y, w, CARD_H, Theme.mix(Theme.mix(Theme.ROW, color, 0.08f + 0.06f * h), Theme.ROW_HOVER, 0.4f * h), a, border, a);
		g.fill(x + 1, y + 1, x + w - 1, y + 3, Theme.argb(color, a)); // colour cap
		double t = System.nanoTime() / 1e9;
		int bob = locked ? 0 : (int) Math.round(Math.sin(t * 2.2 + b.order()) * 1.5); // own phase per card
		Theme.slot(g, b.item().stack(), x + 7, y + 8 + bob, 1.15f + 0.15f * h, a);
		int tx = x + 31, right = x + w - 7;
		String name = Theme.fit(font, bs.name(), right - tx);
		if (maxed) Theme.shimmer(g, font, name, tx, y + 7, a);
		else g.text(font, name, tx, y + 7, Theme.argb(locked ? Theme.TEXT_MUTED : Theme.TITLE, a), true);
		String type = bs.type() + " Slayer";
		g.text(font, type, tx, y + 18, Theme.argb(Theme.TEXT_MUTED, a), true);
		// rank and best tier, e.g. "Top 15.3%" (hover the card for the boss's own text)
		String rank = st != null && st.top() != null ? st.top() : st != null && st.bestTier() != null ? "Best " + st.bestTier() : null;
		if (rank != null && tx + font.width(type) + 6 + font.width(rank) <= right)
			g.text(font, rank, right - font.width(rank), y + 18, Theme.argb(Theme.mix(color, Theme.TEXT, 0.3f), a), true);
		String lvl = locked ? "LOCKED" : p.level() >= 0 ? "LVL " + p.level() : "?";
		int bw = Theme.badge(g, font, lvl, right, y + 28, maxed ? Theme.ACCENT : color, a);
		pips(g, x + 7, y + 31, w - 14 - bw - 6, Math.max(0, p.level()), bs.maxLevel(), maxed ? Theme.ACCENT : color, h, a);

		// XP: the hub has none itself; the Slayer Leaderboards item lists it per boss
		long xp = st != null && st.xp() != null ? Long.parseLong(st.xp().replace(",", "")) : -1, need = bs.nextLevelXp(Math.max(0, p.level()));
		String text;
		float fill;
		if (locked) {
			String req = SlayerParser.requirement(b.item().lore());
			text = req != null ? req : "Locked";
			fill = 0f;
		} else if (maxed) {
			text = "MAXED";
			fill = 1f;
		} else if (xp >= 0 && need > 0) {
			text = SkillParser.compact(String.valueOf(xp)) + " / " + SkillParser.compact(String.valueOf(need)) + " XP";
			fill = Math.min(1f, xp / (float) need);
		} else {
			text = need > 0 ? "Next: " + SkillParser.compact(String.valueOf(need)) + " XP" : "";
			fill = 0f;
		}
		Theme.bar(g, font, x + 7, y + CARD_H - 15, w - 14, 10, barOf(b.item().slot(), fill), maxed ? Theme.ACCENT : Theme.mix(color, 0x000000, 0.25f),
				Theme.fit(font, text, w - 26), a);
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
			out.add(new Box(tiers.get(i), Kind.TIER, bx() + i * (w + GAP), by() + TIER_Y, w, TIER_H, i + 1));
		out.addAll(buttons(others, tiers.size() + 1));
		return out;
	}

	/** Header in the boss colour: icon, name, level badge, pips, XP bar (from the Boss Leveling Rewards item). */
	private void extractBossHeader(GuiGraphicsExtractor g, List<MenuItem> items, float a) {
		MenuItem leveling = named(items, LEVELING_ITEM);
		SlayerParser.Progress p = leveling != null ? SlayerParser.progress(leveling.lore()) : new SlayerParser.Progress(-1, null, null, null, false);
		boolean maxed = p.level() >= boss.maxLevel();
		int x = bx(), y = by(), w = BW, color = boss.color();
		float ra = a * rise(0);
		Theme.box(g, x, y, w, HEAD_H, Theme.mix(Theme.ROW, color, 0.1f), ra, maxed ? Theme.ACCENT : Theme.mix(Theme.LINE, color, 0.6f), ra);
		g.fill(x + 1, y + 1, x + 4, y + HEAD_H - 1, Theme.argb(color, ra));
		// the boss's own item (the tiers show it), bobbing
		ItemStack icon = items.stream().filter(i -> SlayerParser.tier(i.name()) > 0).map(MenuItem::stack).findFirst().orElse(new ItemStack(Items.IRON_SWORD));
		double t = System.nanoTime() / 1e9;
		Theme.slot(g, icon, x + 10, y + 12 + (int) Math.round(Math.sin(t * 2.0) * 1.5), 1.35f, ra);
		int tx = x + 38, right = x + w - 8;
		String lvl = p.level() >= 0 ? "LVL " + p.level() + " / " + boss.maxLevel() : "LVL ?";
		int bw = Theme.badge(g, font, lvl, right, y + 6, maxed ? Theme.ACCENT : color, ra);
		if (maxed) Theme.shimmer(g, font, boss.name(), tx, y + 7, ra);
		else g.text(font, boss.name(), tx, y + 7, Theme.argb(Theme.TITLE, ra), true);
		String type = boss.type() + " Slayer";
		g.text(font, type, tx + font.width(boss.name()) + 8, y + 7, Theme.argb(Theme.TEXT_MUTED, ra), true);
		Theme.leader(g, tx + font.width(boss.name()) + 8 + font.width(type) + 4, right - bw - 4, y + 13, ra);
		pips(g, tx, y + 20, 120, Math.max(0, p.level()), boss.maxLevel(), maxed ? Theme.ACCENT : color, 0f, ra);
		String xp = maxed ? "MAXED" : p.done() != null ? SkillParser.compact(p.done()) + " / " + SkillParser.compact(p.total()) + " XP to LVL " + (p.level() + 1) : null;
		Theme.bar(g, font, tx, y + HEAD_H - 16, right - tx, 10, barOf(-1, maxed ? 1f : p.percent() != null ? p.percent() : 0f),
				maxed ? Theme.ACCENT : Theme.mix(color, 0x000000, 0.2f), xp, ra);
	}

	/** Tier card: big numeral in the difficulty colour, difficulty word, Hypixel's details, and a start strip. */
	private void extractTier(GuiGraphicsExtractor g, Box b, float h, int drop, float a) {
		List<String> lore = b.item().lore();
		int tier = SlayerParser.tier(b.item().name()), color = SlayerParser.tierColor(tier);
		boolean locked = SlayerParser.progress(lore).locked();
		boolean busy = lore.stream().anyMatch(l -> l.contains("Cancel it first")); // another quest is running
		int x = b.x(), y = b.y() + drop - Math.round(3 * h), w = b.w(), shown = locked ? Theme.TEXT_MUTED : color;
		Theme.box(g, x, y, w, TIER_H, Theme.mix(Theme.mix(Theme.ROW, shown, 0.07f + 0.08f * h), Theme.ROW_HOVER, 0.3f * h), a,
				Theme.mix(Theme.mix(Theme.LINE, shown, 0.5f), shown, h), a);
		g.fill(x + 1, y + 1, x + w - 1, y + 3, Theme.argb(shown, a));
		String numeral = CollectionParser.roman(tier);
		var pose = g.pose(); // big numeral at 2x
		pose.pushMatrix();
		pose.translate(x + 8, y + 9);
		pose.scale(2f, 2f);
		g.text(font, numeral, 0, 0, Theme.argb(shown, a), true);
		pose.popMatrix();
		Theme.item(g, b.item().stack(), x + w - 24, y + 8, Math.round(16 + 2 * h), a);
		// Hypixel's difficulty word ("Beginner", "Deadly") is the first lore line
		String difficulty = !lore.isEmpty() && !lore.getFirst().isBlank() && !lore.getFirst().contains(":") ? lore.getFirst().strip() : "Tier " + numeral;
		g.text(font, Theme.fit(font, difficulty.toUpperCase(), w - 12), x + 8, y + 29, Theme.argb(Theme.mix(shown, Theme.TEXT_MUTED, 0.5f), a), true);
		int ly = y + 42;
		if (locked) {
			String req = SlayerParser.requirement(lore);
			if (req != null)
				for (var line : font.split(net.minecraft.network.chat.Component.literal(req), w - 12)) {
					g.text(font, line, x + 6, ly, Theme.argb(Theme.TEXT_MUTED, a), true);
					ly += 10;
				}
		} else
			for (String[] kv : SlayerParser.details(lore, 4)) {
				if (ly > y + TIER_H - 34) break;
				g.text(font, Theme.fit(font, kv[0], w - 12), x + 6, ly, Theme.argb(Theme.TEXT_MUTED, a), true);
				g.text(font, Theme.fit(font, kv[1], w - 12), x + 6, ly + 9, Theme.argb(Theme.TEXT, a), true);
				ly += 19;
			}
		String action = locked ? "LOCKED" : busy ? "IN QUEST" : "START";
		int sy = y + TIER_H - 15, strip = locked || busy ? Theme.TEXT_MUTED : shown;
		Theme.box(g, x + 5, sy, w - 10, 11, Theme.mix(Theme.SLOT, strip, locked || busy ? 0.05f : 0.2f + 0.3f * h), a, strip, a);
		String fitted = Theme.fit(font, action, w - 14);
		g.text(font, fitted, x + (w - font.width(fitted)) / 2, sy + 2, Theme.argb(locked || busy ? Theme.TEXT_MUTED : Theme.mix(shown, 0xFFFFFF, 0.4f), a), false);
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
