package cleanui.skyblock;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads RNG Meter menus ("Crystal Nucleus RNG Meter"): each drop's odds and meter progress toward it. */
public final class RngParser {
	/** rarity: "Pray RNGesus"; chance: "0.472%"; fill: how full the meter is for this drop (0..1). */
	public record Drop(String rarity, String chance, float fill, String done, String total, boolean selected) {}

	private static final Pattern ODDS = Pattern.compile("Odds: (.+?) \\((.+)\\)");
	private static final Pattern XP = Pattern.compile("XP: ([\\d,.]+[kKmMbB]?)\\s*/\\s*([\\d,.]+[kKmMbB]?)");
	private static final Pattern PER_RUN = Pattern.compile("with ([\\d,.]+[kKmM]?) [A-Za-z ]*XP");
	/** Rarity name for drops that are still locked; their "chance" holds the requirement text. */
	public static final String LOCKED = "Locked";
	private static final List<String> ORDER = List.of("Common", "Uncommon", "Rare", "Extraordinary", "Pray RNGesus", "RNGesus Incarnate");
	/** Hypixel's colours for the odds names. */
	private static final Map<String, Integer> RARITY = Map.of("Common", 0xFFFFFF, "Uncommon", 0x55FF55, "Rare", 0x5555FF,
			"Extraordinary", 0xAA00AA, "Pray RNGesus", 0xFF55FF, "RNGesus Incarnate", 0xFF5555);

	private RngParser() {}

	/** Null unless the lore has an "Odds:" line (so the header and buttons aren't drops). */
	public static Drop drop(List<String> lore) {
		String rarity = null, chance = null, done = null, total = null;
		for (String line : lore) {
			Matcher m;
			if ((m = ODDS.matcher(line)).find()) {
				rarity = m.group(1);
				String[] odds = m.group(2).split(" ");
				chance = odds[odds.length - 1]; // "0.0055% 0.0061%": the last is the boosted one
			}
			if ((m = XP.matcher(line)).find()) {
				done = m.group(1);
				total = m.group(2);
			}
		}
		if (rarity == null) {
			// "???" drops you haven't unlocked: no odds, just "Requires Zombie Slayer 7!"
			String req = lore.stream().map(String::strip).filter(l -> l.startsWith("Requires")).findFirst().orElse(null);
			return req != null ? new Drop(LOCKED, req, 0f, null, null, false) : null;
		}
		boolean selected = lore.stream().anyMatch(l -> l.strip().equals("SELECTED"));
		TrackParser.Progress p = TrackParser.progress(lore); // the selected drop: "Progress: 5%" + "50,000/1M"
		if (p != null && p.done() != null) {
			done = p.done();
			total = p.total();
		}
		long need = total != null ? PerkParser.amount(total) : 0; // amount() reads "1M" / "2,500,000"
		float fill = need > 0 ? Math.min(1f, (float) PerkParser.amount(done) / need) : 0f;
		return new Drop(rarity, chance, fill, done, total, selected);
	}

	/** "...fills / with 1,000 Nucleus XP every time you..." → 1000; -1 if the header doesn't say. */
	public static long perRun(List<String> headerLore) {
		for (String line : headerLore) {
			Matcher m = PER_RUN.matcher(line);
			if (m.find()) return PerkParser.amount(m.group(1));
		}
		return -1;
	}

	/** Runs until the meter is full for this drop (0 = full now), or -1 if unknown. */
	public static long runsLeft(Drop d, long perRun) {
		if (d.done() == null || perRun <= 0) return -1;
		long left = PerkParser.amount(d.total()) - PerkParser.amount(d.done());
		return left <= 0 ? 0 : (left + perRun - 1) / perRun;
	}

	/** Rarity order for grouping, rarest last; unknown names sort after the known ones. */
	public static int rank(String rarity) {
		int i = ORDER.indexOf(rarity);
		return i < 0 ? ORDER.size() : i;
	}

	public static int color(String rarity) {
		return RARITY.getOrDefault(rarity, 0xAAAAAA);
	}

	/** "Selected Drop" → the line after it in the header or Reset lore, or null. */
	public static String selected(List<String> lore) {
		for (int i = 0; i + 1 < lore.size(); i++)
			if (lore.get(i).strip().equals("Selected Drop")) return lore.get(i + 1).strip();
		return null;
	}
}
