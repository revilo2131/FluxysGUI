package cleanui.skyblock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads tier items in Hypixel's level menus ("Farming Skill", "Wheat Collection"): each tier is
 * an item named "<Name> <tier>", and its glass pane color says whether it's done, in progress
 * or locked (green / yellow / red).
 */
public final class TrackParser {
	public enum State { DONE, CURRENT, LOCKED }

	/** One tier: its number and whether the numeral was arabic ("Farming 25") or roman. */
	public record Tier(String name, int tier, boolean arabic) {}

	private static final Pattern TIER = Pattern.compile("^(.+?) ([IVXLCDM]+|\\d+)$");

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

	public static boolean isPane(String itemId) {
		return itemId.endsWith("glass_pane");
	}
}
