package cleanui.skyblock;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads items in Hypixel's Collections menus (a category like "Farming", or a collection like
 * "Wheat XI"). Deliberately loose, since the exact wording isn't pinned down: it picks up a
 * trailing tier numeral, the first percentage, an "a/b" amount and a "Total Collected" line
 * wherever they appear, and leaves out whatever isn't there.
 */
public final class CollectionParser {
	/**
	 * {@code tier} is -1 when the name has no numeral; null fields weren't found.
	 * {@code amountLabel} is the wording before an "a/b" amount, e.g. "Maxed Out" from
	 * "Collections Maxed Out: 4/11".
	 */
	public record Entry(String name, int tier, boolean arabic, Float percent, String done, String total, String amountLabel,
			String collected, boolean maxed) {
		/** No tier, no progress, nothing collected: not found yet (or not a collection at all). */
		public boolean locked() {
			return tier < 0 && percent == null && collected == null && done == null && !maxed;
		}
	}

	private static final Pattern TIER = Pattern.compile("^(.+?) ([IVXLCDM]+|\\d+)$");
	private static final Pattern PET = Pattern.compile("^\\[Lvl (\\d+)] (.+)$");
	/** "Healer Level 24" or "Catacombs Level: XXIV" in the lore, for items without a tier in their name. */
	private static final Pattern LORE_LEVEL = Pattern.compile("(?:^|\\s)Level:?\\s+\\[?([IVXLCDM]+|\\d+)]?\\s*$");
	private static final Pattern NEXT_LEVEL = Pattern.compile("Progress to Level (\\d+)");
	private static final Pattern PERCENT = Pattern.compile("([\\d.]+)%");
	private static final Pattern AMOUNT = Pattern.compile("([\\d,.]+[kKmMbB]?)\\s*/\\s*([\\d,.]+[kKmMbB]?)\\s*$");
	private static final Pattern COLLECTED = Pattern.compile("Total [Cc]ollected:?\\s*([\\d,.]+[kKmMbB]?)");
	private static final Pattern MAXED = Pattern.compile("\\bmax(ed)?\\b", Pattern.CASE_INSENSITIVE);

	private CollectionParser() {}

	public static Entry parse(String name, List<String> lore) {
		String n = name.strip();
		int tier = -1;
		boolean arabic = false;
		Matcher t = PET.matcher(n);
		if (t.matches()) { // "[Lvl 100] Golden Dragon"
			tier = Integer.parseInt(t.group(1));
			n = t.group(2);
			arabic = true;
		} else if ((t = TIER.matcher(n)).matches()) {
			n = t.group(1);
			tier = SkillParser.level(t.group(2));
			arabic = Character.isDigit(t.group(2).charAt(0));
		}
		Float percent = null;
		String done = null, total = null, amountLabel = null, collected = null;
		boolean maxed = false;
		for (String raw : lore) {
			String line = raw.strip();
			Matcher m;
			boolean hasNumber = false;
			if ((m = PERCENT.matcher(line)).find()) {
				hasNumber = true;
				if (percent == null) percent = Math.clamp(Float.parseFloat(m.group(1)) / 100f, 0f, 1f);
			}
			if ((m = AMOUNT.matcher(line)).find()) {
				hasNumber = true;
				if (done == null) {
					done = m.group(1);
					total = m.group(2);
					int colon = line.lastIndexOf(':', m.start());
					if (colon > 0) amountLabel = line.substring(0, colon).replaceFirst("^Collections? ", "").strip();
				}
			}
			if (collected == null && (m = COLLECTED.matcher(line)).find()) collected = m.group(1);
			if (tier < 0 && !line.startsWith("Progress") && (m = LORE_LEVEL.matcher(line)).find()) {
				tier = SkillParser.level(m.group(1));
				arabic = Character.isDigit(m.group(1).charAt(0));
			}
			// "Collection Maxed Out!" means maxed; "Collections Maxed Out: 4/11" is just a count
			if (!hasNumber && MAXED.matcher(line).find()) maxed = true;
		}
		if (percent == null && done != null) percent = ratio(done, total);
		return new Entry(n, tier, arabic, percent, done, total, amountLabel, collected, maxed);
	}

	/** "Progress to Level 313" → 312, for a level shown only as the next one; else -1. */
	public static int levelFromProgress(List<String> lore) {
		for (String line : lore) {
			Matcher m = NEXT_LEVEL.matcher(line);
			if (m.find()) return Integer.parseInt(m.group(1)) - 1;
		}
		return -1;
	}

	/** 11 → "XI" (tiers are shown the way Hypixel writes them). */
	public static String roman(int n) {
		int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++)
			while (n >= values[i]) {
				sb.append(symbols[i]);
				n -= values[i];
			}
		return sb.isEmpty() ? "0" : sb.toString();
	}

	/** "12" / "15" → 0.8; null if either isn't a plain number. */
	static Float ratio(String done, String total) {
		try {
			double d = Double.parseDouble(done.replace(",", "")), t = Double.parseDouble(total.replace(",", ""));
			return t <= 0 ? null : (float) Math.clamp(d / t, 0, 1);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
