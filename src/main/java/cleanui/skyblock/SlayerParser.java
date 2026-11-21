package cleanui.skyblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Hypixel's Slayer menus (Maddox's "Slayer" hub and each boss's own menu, e.g. "Sven
 * Packmaster"). Everything comes from the lore, not slots, since the layout isn't known for sure.
 */
public final class SlayerParser {
	/** A slayer boss: its display name, slayer type ("Wolf"), colour, and total XP needed for each level 1..max. */
	public record Boss(String name, String type, int color, long[] levelXp) {
		public int maxLevel() {
			return levelXp.length;
		}

		/** Total XP for the level after {@code level}, or -1 when maxed. */
		public long nextLevelXp(int level) {
			return level >= 0 && level < levelXp.length ? levelXp[level] : -1;
		}
	}

	/** One boss line from the hub's info items: XP and rank (leaderboard), best tier slain (wisdom buff). */
	public record Stats(String xp, String top, String bestTier) {}

	/** The "Ongoing Slayer Quest" item: "Sven Packmaster IV" and what to do next. */
	public record Quest(String boss, String objective) {}

	/** Boss/progress card data: level (-1 unknown), XP "a"/"b" (null if absent), locked. */
	public record Progress(int level, String done, String total, Float percent, boolean locked) {}

	// Hypixel's slayer XP table (total XP per level)
	private static final long[] ZOMBIE_XP = {5, 15, 200, 1_000, 5_000, 20_000, 100_000, 400_000, 1_000_000};
	private static final long[] SPIDER_XP = {5, 25, 200, 1_000, 5_000, 20_000, 100_000, 400_000, 1_000_000};
	private static final long[] WOLF_XP = {10, 30, 250, 1_500, 5_000, 20_000, 100_000, 400_000, 1_000_000};
	private static final long[] VAMPIRE_XP = {20, 75, 240, 840, 2_400};

	public static final List<Boss> BOSSES = List.of(
			new Boss("Revenant Horror", "Zombie", 0x6CD655, ZOMBIE_XP),
			new Boss("Tarantula Broodfather", "Spider", 0xD9534F, SPIDER_XP),
			new Boss("Sven Packmaster", "Wolf", 0xC9D6E8, WOLF_XP),
			new Boss("Voidgloom Seraph", "Enderman", 0xB36BFF, WOLF_XP),
			new Boss("Inferno Demonlord", "Blaze", 0xFF8C2B, WOLF_XP),
			new Boss("Riftstalker Bloodfiend", "Vampire", 0xE0245E, VAMPIRE_XP));

	/** Hypixel's difficulty colours for tiers I..V. */
	private static final int[] TIER_COLORS = {0x55FF55, 0xFFFF55, 0xFF5555, 0xAA0000, 0xAA00AA};

	private static final Pattern SLAYER_LVL = Pattern.compile("(?:Slayer|Current):?\\s+LVL:?\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern LEADERBOARD = Pattern.compile("^(.+?): ([\\d,]+) \\((Top [\\d.]+%)\\)$");
	private static final Pattern BEST_TIER = Pattern.compile("^(.+?): (Tier [IVX]+)$");
	private static final Pattern ANY_LVL = Pattern.compile("\\bLVL\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern AMOUNT = Pattern.compile("([\\d,.]+[kKmM]?)\\s*/\\s*([\\d,.]+[kKmM]?)");
	private static final Pattern PERCENT = Pattern.compile("([\\d.]+)%");
	private static final Pattern KEY_VALUE = Pattern.compile("^([A-Z][A-Za-z ]{1,24}):\\s*(.+)$");

	private SlayerParser() {}

	/** The boss an item or menu title is about ("Sven Packmaster", "Sven Packmaster III", "Wolf Slayer"), or null. */
	public static Boss boss(String text) {
		String t = text.toLowerCase(Locale.ROOT);
		for (Boss b : BOSSES)
			if (t.contains(b.name().toLowerCase(Locale.ROOT)) || t.contains(b.type().toLowerCase(Locale.ROOT) + " slayer")) return b;
		return null;
	}

	/**
	 * Level, XP and lock state from a boss item's lore. "Wolf Slayer: LVL 7" beats a bare "LVL 3"
	 * (which can be a requirement); XP is the first "a/b" on a line about XP or progress.
	 */
	public static Progress progress(List<String> lore) {
		int level = -1;
		String done = null, total = null;
		Float percent = null;
		boolean locked = false;
		String prev = "";
		for (String raw : lore) {
			String line = raw.strip();
			Matcher m;
			if ((m = SLAYER_LVL.matcher(line)).find()) level = Integer.parseInt(m.group(1));
			else if (level < 0 && !line.startsWith("Requires") && !line.startsWith("Unlock") && (m = ANY_LVL.matcher(line)).find())
				level = Integer.parseInt(m.group(1));
			// "Zombie Slayer XP to LVL 7:" puts the amount on the next line
			boolean xpLine = line.contains("XP") || line.startsWith("Progress") || prev.contains("XP");
			if (done == null && xpLine && (m = AMOUNT.matcher(line)).find()) {
				done = m.group(1);
				total = m.group(2);
			}
			if (percent == null && line.startsWith("Progress") && (m = PERCENT.matcher(line)).find())
				percent = Math.clamp(Float.parseFloat(m.group(1)) / 100f, 0f, 1f);
			// "Requires …", "Only inside The Rift!", "Slay Voidgloom Seraph III!" (Inferno's unlock)
			if (line.startsWith("Requires") || line.equalsIgnoreCase("LOCKED") || line.contains("Not unlocked")
					|| line.startsWith("Only inside") || line.startsWith("Slay ") && line.endsWith("!")) locked = true;
			prev = line;
		}
		if (percent == null && done != null) {
			long need = PerkParser.amount(total);
			if (need > 0) percent = Math.min(1f, (float) PerkParser.amount(done) / need);
		}
		return new Progress(level, done, total, percent, locked);
	}

	/** "Health: 2,000❤" style lines from a tier's lore, at most {@code max}, in order. */
	public static List<String[]> details(List<String> lore, int max) {
		List<String[]> out = new ArrayList<>();
		for (String raw : lore) {
			Matcher m = KEY_VALUE.matcher(raw.strip());
			if (m.matches() && !m.group(1).startsWith("Click")) out.add(new String[]{m.group(1).strip(), m.group(2).strip()});
			if (out.size() == max) break;
		}
		return out;
	}

	/** Tier I..V from an item name ending in a numeral ("Sven Packmaster III" → 3), else -1. */
	public static int tier(String name) {
		TrackParser.Tier t = TrackParser.tier(name);
		return t != null && t.tier() >= 1 && t.tier() <= 5 && boss(t.name()) != null ? t.tier() : -1;
	}

	/**
	 * Per-boss info from the hub's items: "Revenant Horror: 25,730 (Top 15.3%)" (Slayer Leaderboards,
	 * the number is your XP) and "Revenant Horror: Tier IV" (Global Combat Wisdom Buff).
	 */
	public static Map<Boss, Stats> stats(List<String> leaderboardLore, List<String> buffLore) {
		Map<Boss, Stats> out = new HashMap<>();
		for (String line : leaderboardLore) {
			Matcher m = LEADERBOARD.matcher(line.strip());
			Boss b = m.matches() ? boss(m.group(1)) : null;
			if (b != null) out.put(b, new Stats(m.group(2), m.group(3), null));
		}
		for (String line : buffLore) {
			Matcher m = BEST_TIER.matcher(line.strip());
			Boss b = m.matches() ? boss(m.group(1)) : null;
			if (b == null) continue;
			Stats s = out.get(b);
			out.put(b, new Stats(s != null ? s.xp() : null, s != null ? s.top() : null, m.group(2)));
		}
		return out;
	}

	/** "Boss: Sven Packmaster IV" and the line after it, from the Ongoing Slayer Quest item; null if none. */
	public static Quest quest(List<String> lore) {
		for (int i = 0; i < lore.size(); i++) {
			String line = lore.get(i).strip();
			if (line.startsWith("Boss: "))
				return new Quest(line.substring(6).strip(), i + 1 < lore.size() ? lore.get(i + 1).strip() : "");
		}
		return null;
	}

	/** The line that says why a boss/tier is locked ("Slay Voidgloom Seraph III!"), else null. */
	public static String requirement(List<String> lore) {
		for (String raw : lore) {
			String line = raw.strip();
			if (line.startsWith("Requires") || line.startsWith("Only inside") || line.startsWith("Slay ") && line.endsWith("!")) return line;
		}
		return null;
	}

	public static int tierColor(int tier) {
		return TIER_COLORS[Math.clamp(tier - 1, 0, TIER_COLORS.length - 1)];
	}
}
