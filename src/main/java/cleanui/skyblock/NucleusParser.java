package cleanui.skyblock;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Crystal Nucleus run from chat, line by line (a chat message may hold several lines,
 * or Hypixel may send them one per message; both work). Lines come plain, with the colour of
 * their first coloured part (for loot rarity).
 *
 * <pre>
 * ✦ CRYSTAL FOUND (1/5)
 *   Amber Crystal
 * ✦ You placed the Amber Crystal!
 * CRYSTAL NUCLEUS LOOT BUNDLE / REWARDS / Jaderald / Fine Amber Gemstone / Flawed Jade Gemstone x66 / +800 HOTM Exp
 * [SkyHanni] Profit for Crystal Nucleus Run: 1.5M
 * Pick it up near the Nucleus Vault!
 * </pre>
 */
public final class NucleusParser {
	public static final List<String> CRYSTALS = List.of("Amber", "Amethyst", "Jade", "Sapphire", "Topaz");

	public sealed interface Event {}
	public record Found(String crystal, int count) implements Event {}
	public record Placed(String crystal) implements Event {}
	/** One reward line: name, stack size (1 if none), rgb colour (rarity) or -1. */
	public record Loot(String name, int count, int color) {}
	public record Bundle(List<Loot> items, List<String> extras) implements Event {}
	public record Profit(String amount) implements Event {}

	private static final Pattern FOUND = Pattern.compile("CRYSTAL FOUND \\((\\d)/5\\)");
	private static final Pattern NAME = Pattern.compile("^(Amber|Amethyst|Jade|Sapphire|Topaz) Crystal$");
	private static final Pattern PLACED = Pattern.compile("You placed the (Amber|Amethyst|Jade|Sapphire|Topaz) Crystal!");
	private static final Pattern PROFIT = Pattern.compile("Profit for Crystal Nucleus Run: (\\S+)");
	private static final Pattern STACK = Pattern.compile("^(.+?)(?: x([\\d,]+))?$");

	private int pendingFound;
	private List<Loot> loot;
	private List<String> extras;

	/** Feed one line; returns what it completed, or null. */
	public Event line(String raw, int color) {
		String s = raw.strip();
		if (s.isEmpty()) return null;
		Matcher m;
		if ((m = FOUND.matcher(s)).find()) {
			pendingFound = Integer.parseInt(m.group(1));
			return null;
		}
		if (pendingFound > 0 && (m = NAME.matcher(s)).matches()) {
			int n = pendingFound;
			pendingFound = 0;
			return new Found(m.group(1), n);
		}
		if ((m = PLACED.matcher(s)).find()) return new Placed(m.group(1));
		if ((m = PROFIT.matcher(s)).find()) return new Profit(m.group(1));
		if (s.contains("CRYSTAL NUCLEUS LOOT BUNDLE")) {
			loot = new ArrayList<>();
			extras = new ArrayList<>();
			return null;
		}
		if (loot == null) return null;
		if (s.startsWith("Pick it up near the Nucleus Vault") || s.startsWith("CRYSTAL FOUND")) {
			Bundle b = new Bundle(loot, extras);
			loot = null;
			return b;
		}
		if (s.equals("REWARDS") || s.startsWith("[") || s.chars().allMatch(c -> c == '-' || c == '▬' || c == ' ')) return null;
		if (s.startsWith("+")) { // "+800 HOTM Exp"
			extras.add(s);
			return null;
		}
		String name = s.replaceFirst("^[^A-Za-z0-9]+", ""); // the little gem icons in front
		m = STACK.matcher(name);
		if (!m.matches() || name.isEmpty()) return null;
		loot.add(new Loot(m.group(1).strip(), m.group(2) == null ? 1 : Integer.parseInt(m.group(2).replace(",", "")), color));
		return null;
	}
}
