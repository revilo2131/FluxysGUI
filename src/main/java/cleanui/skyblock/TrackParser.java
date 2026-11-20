package cleanui.skyblock;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads tier items in Hypixel's level menus ("Combat Skill", "Wheat Collection"): each tier is an
 * item named "<Name> <tier>". Skill menus mark reached tiers "UNLOCKED" and the one in progress
 * with a "Progress:" line; older menus say it with the glass pane colour (green / yellow / red).
 */
public final class TrackParser {
	public enum State { DONE, CURRENT, LOCKED }

	/** One tier: its number and whether the numeral was arabic ("Farming 25") or roman. */
	public record Tier(String name, int tier, boolean arabic) {}

	/** The tier in progress: "Progress: 66.8%" and the "667,615.3/1M" line under it. */
	public record Progress(Float percent, String done, String total) {}

	private static final Pattern TIER = Pattern.compile("^(.+?) ([IVXLCDM]+|\\d+)$");
	private static final Pattern PERCENT = Pattern.compile("([\\d.]+)%");
	private static final Pattern AMOUNT = Pattern.compile("([\\d,.]+[kKmMbB]?)\\s*/\\s*([\\d,.]+[kKmMbB]?)");

	/** XP for each skill level 1..60 (Hypixel's standard table; Runecrafting/Social/Hunting differ). */
	private static final long[] SKILL_XP = {50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500, 5_000, 7_500, 10_000, 15_000,
			20_000, 30_000, 50_000, 75_000, 100_000, 200_000, 300_000, 400_000, 500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000,
			1_100_000, 1_200_000, 1_300_000, 1_400_000, 1_500_000, 1_600_000, 1_700_000, 1_800_000, 1_900_000, 2_000_000, 2_100_000,
			2_200_000, 2_300_000, 2_400_000, 2_500_000, 2_600_000, 2_750_000, 2_900_000, 3_100_000, 3_400_000, 3_700_000, 4_000_000,
			4_300_000, 4_600_000, 4_900_000, 5_200_000, 5_500_000, 5_800_000, 6_100_000, 6_400_000, 6_700_000, 7_000_000};
	/** Max level of skills on the standard XP table. */
	private static final Map<String, Integer> MAX_LEVEL = Map.of("Farming", 60, "Mining", 60, "Combat", 60, "Foraging", 54,
			"Fishing", 50, "Enchanting", 60, "Alchemy", 50, "Carpentry", 50, "Taming", 60);

	private TrackParser() {}

	/** "Wheat XI" → Tier(Wheat, 11); null if the name has no trailing tier. */
	public static Tier tier(String name) {
		Matcher m = TIER.matcher(name.strip());
		if (!m.matches()) return null;
		return new Tier(m.group(1), SkillParser.level(m.group(2)), Character.isDigit(m.group(2).charAt(0)));
	}

	/** From the item id: green/lime panes are done, yellow/orange in progress, red/gray/black locked. */
	public static State state(String itemId) {
		if (itemId.contains("lime") || itemId.contains("green")) return State.DONE;
		if (itemId.contains("yellow") || itemId.contains("orange")) return State.CURRENT;
		if (itemId.contains("red") || itemId.contains("gray") || itemId.contains("black")) return State.LOCKED;
		return State.DONE; // a non-pane tier item (e.g. an enchanted item) means reached
	}

	/** From the lore (skill menus): "UNLOCKED" → done, a "Progress:" line → current, else locked. */
	public static State stateFromLore(List<String> lore) {
		if (lore.stream().anyMatch(l -> l.strip().equals("UNLOCKED"))) return State.DONE;
		return progress(lore) != null ? State.CURRENT : State.LOCKED;
	}

	public static boolean marksUnlocked(List<String> lore) {
		return lore.stream().anyMatch(l -> l.strip().equals("UNLOCKED"));
	}

	/** The "Progress: N%" line and the amount on it or just below, or null (reward text is ignored). */
	public static Progress progress(List<String> lore) {
		for (int i = 0; i < lore.size(); i++) {
			String line = lore.get(i).strip();
			if (!line.startsWith("Progress")) continue;
			Matcher p = PERCENT.matcher(line);
			Float percent = p.find() ? Math.clamp(Float.parseFloat(p.group(1)) / 100f, 0f, 1f) : null;
			for (int j = i; j < Math.min(i + 2, lore.size()); j++) {
				Matcher a = AMOUNT.matcher(lore.get(j));
				if (a.find()) return new Progress(percent, a.group(1), a.group(2));
			}
			return new Progress(percent, null, null);
		}
		return null;
	}

	/** Max level for a skill on the standard XP table, else -1. */
	public static int maxLevel(String skill) {
		return MAX_LEVEL.getOrDefault(skill, -1);
	}

	/** Total XP needed to reach {@code level} from 0 on the standard table. */
	public static long xpFor(int level) {
		long sum = 0;
		for (int i = 0; i < Math.min(level, SKILL_XP.length); i++) sum += SKILL_XP[i];
		return sum;
	}

	/** Skill XP so far: everything up to {@code level} plus "667,615.3" into the next one. */
	public static long totalXp(int level, String done) {
		return xpFor(level) + (done != null ? PerkParser.amount(done) : 0);
	}

	public static boolean isPane(String itemId) {
		return itemId.endsWith("glass_pane");
	}
}
