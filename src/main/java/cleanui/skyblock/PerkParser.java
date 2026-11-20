package cleanui.skyblock;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads perk items in Hypixel's Heart of the Mountain / Forest trees: the perk's level
 * ("Level 12/50"), and whether it's locked, unlocked or maxed. Loose: unknown wording
 * just means no level is shown.
 */
public final class PerkParser {
	public enum State { LOCKED, UNLOCKED, MAXED }

	public record Perk(int level, int max, State state) {}

	private static final Pattern LEVEL = Pattern.compile("Level (\\d+)\\s*/\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern TOKENS = Pattern.compile("Token(?:s)? of the (?:Mountain|Forest):?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern TIER = Pattern.compile("^Tier (\\d+)$");

	private PerkParser() {}

	/** {@code itemId} like "minecraft:emerald"; coal/bedrock items are Hypixel's locked perks. */
	public static Perk perk(String itemId, List<String> lore) {
		int level = -1, max = -1;
		boolean locked = itemId.contains("coal") || itemId.contains("bedrock");
		for (String line : lore) {
			Matcher m = LEVEL.matcher(line);
			if (m.find() && level < 0) {
				level = Integer.parseInt(m.group(1));
				max = Integer.parseInt(m.group(2));
			}
			String l = line.toLowerCase(Locale.ROOT);
			if (l.contains("requires") && l.contains("unlock")) locked = true;
		}
		State state = locked ? State.LOCKED : level >= 0 && level >= max ? State.MAXED : State.UNLOCKED;
		return new Perk(level, max, state);
	}

	/** "Tier 7" → 7, else -1. These items label the tree's rows. */
	public static int tier(String name) {
		Matcher m = TIER.matcher(name.strip());
		return m.matches() ? Integer.parseInt(m.group(1)) : -1;
	}

	/** Tokens left, from any lore line like "Token of the Mountain: 3", else -1. */
	public static int tokens(List<String> lore) {
		for (String line : lore) {
			Matcher m = TOKENS.matcher(line);
			if (m.find()) return Integer.parseInt(m.group(1));
		}
		return -1;
	}
}
