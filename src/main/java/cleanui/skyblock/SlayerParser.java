package cleanui.skyblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Hypixel's Slayer menus (Maddox's "Slayer" hub and each boss's own menu, e.g. "Sven
 * Packmaster"). Everything comes from the lore, not slots, since the layout isn't known for sure.
 */
public final class SlayerParser {
	/** A slayer boss: its display name, slayer type ("Wolf"), colour and max level. */
	public record Boss(String name, String type, int color, int maxLevel) {}

	/** Boss/progress card data: level (-1 unknown), XP "a"/"b" (null if absent), locked. */
	public record Progress(int level, String done, String total, Float percent, boolean locked) {}

	public static final List<Boss> BOSSES = List.of(
			new Boss("Revenant Horror", "Zombie", 0x6CD655, 9),
			new Boss("Tarantula Broodfather", "Spider", 0xD9534F, 9),
			new Boss("Sven Packmaster", "Wolf", 0xC9D6E8, 9),
			new Boss("Voidgloom Seraph", "Enderman", 0xB36BFF, 9),
			new Boss("Inferno Demonlord", "Blaze", 0xFF8C2B, 9),
			new Boss("Riftstalker Bloodfiend", "Vampire", 0xE0245E, 5));

	/** Hypixel's difficulty colours for tiers I..V. */
	private static final int[] TIER_COLORS = {0x55FF55, 0xFFFF55, 0xFF5555, 0xAA0000, 0xAA00AA};

	private static final Pattern SLAYER_LVL = Pattern.compile("Slayer:?\\s+LVL\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
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
		for (String raw : lore) {
			String line = raw.strip();
			Matcher m;
			if ((m = SLAYER_LVL.matcher(line)).find()) level = Integer.parseInt(m.group(1));
			else if (level < 0 && !line.startsWith("Requires") && (m = ANY_LVL.matcher(line)).find()) level = Integer.parseInt(m.group(1));
			boolean xpLine = line.contains("XP") || line.startsWith("Progress");
			if (done == null && xpLine && (m = AMOUNT.matcher(line)).find()) {
				done = m.group(1);
				total = m.group(2);
			}
			if (percent == null && line.startsWith("Progress") && (m = PERCENT.matcher(line)).find())
				percent = Math.clamp(Float.parseFloat(m.group(1)) / 100f, 0f, 1f);
			if (line.startsWith("Requires") || line.equalsIgnoreCase("LOCKED") || line.contains("Not unlocked")) locked = true;
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

	public static int tierColor(int tier) {
		return TIER_COLORS[Math.clamp(tier - 1, 0, TIER_COLORS.length - 1)];
	}
}
