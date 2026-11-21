package cleanui.skyblock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads stat lines from Hypixel's stat items: "❤ Health 1,245", "Crit Chance: 32.5%", "☘ Farming Fortune +250". */
public final class StatParser {
	public record Stat(String name, String value) {}

	/** Optional symbol, then 1-4 Capitalised words (stat names are; sentences aren't), then the number. */
	private static final Pattern STAT = Pattern.compile("^((?:[^A-Za-z\\d\\s]\\s?)?[A-Z][A-Za-z']*(?: [A-Z][A-Za-z']*){0,3}):?\\s+([+-]?[\\d,]+(?:\\.\\d+)?[%x]?)$");

	private StatParser() {}

	/** Null unless the whole line is "<name> <number>" (so sentences and "Click to view!" aren't stats). */
	public static Stat stat(String line) {
		Matcher m = STAT.matcher(line.strip());
		return m.matches() ? new Stat(m.group(1).strip(), m.group(2)) : null;
	}
}
