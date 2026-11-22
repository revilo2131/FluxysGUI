package cleanui.skyblock;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tunnel Vision (the HOTM 6 pickaxe ability: rarer spawns, worms included, for 30 s) from Hypixel's
 * chat lines: "You used your Tunnel Vision Pickaxe Ability!", "Your Tunnel Vision has expired!",
 * "Tunnel Vision is now available!", and "...ability is on cooldown for 45s." when you try too early.
 */
public final class AbilityParser {
	public enum Kind { USED, EXPIRED, AVAILABLE, COOLDOWN }

	/** What a chat line says; {@code seconds} is the cooldown left for COOLDOWN, else 0. */
	public record Event(Kind kind, int seconds) {}

	private static final Pattern USED = Pattern.compile("You used your Tunnel Vision\\b");
	private static final Pattern EXPIRED = Pattern.compile("Your Tunnel Vision (?:has )?expired");
	private static final Pattern AVAILABLE = Pattern.compile("Tunnel Vision is (?:now )?available");
	private static final Pattern COOLDOWN = Pattern.compile("(?:[Aa]bility|Tunnel Vision) is on cooldown for (\\d+)s");
	/** Pickaxe lore: "Cooldown: 110s" under the ability. */
	private static final Pattern LORE_COOLDOWN = Pattern.compile("Cooldown: (\\d+)s");

	private AbilityParser() {}

	/** The Tunnel Vision event in a plain (unformatted) chat line, or null. */
	public static Event parse(String plain) {
		if (plain == null) return null;
		if (USED.matcher(plain).find()) return new Event(Kind.USED, 0);
		if (EXPIRED.matcher(plain).find()) return new Event(Kind.EXPIRED, 0);
		if (AVAILABLE.matcher(plain).find()) return new Event(Kind.AVAILABLE, 0);
		Matcher m = COOLDOWN.matcher(plain);
		if (m.find()) return new Event(Kind.COOLDOWN, Integer.parseInt(m.group(1)));
		return null;
	}

	/** Tunnel Vision's cooldown in seconds from a pickaxe's lore (a "Cooldown: Ns" line soon after "Tunnel Vision"), or -1. */
	public static int loreCooldown(List<String> lore) {
		for (int i = 0; i < lore.size(); i++) {
			if (!lore.get(i).contains("Tunnel Vision")) continue;
			for (int j = i + 1; j < Math.min(lore.size(), i + 8); j++) {
				Matcher m = LORE_COOLDOWN.matcher(lore.get(j));
				if (m.find()) return Integer.parseInt(m.group(1));
			}
		}
		return -1;
	}
}
