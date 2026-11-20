package cleanui.skyblock;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one skill item from Hypixel's "Your Skills" menu (name + lore, formatting stripped).
 * Lenient on purpose: anything that doesn't look like a skill returns null, and the
 * caller falls back to the vanilla menu if nothing parses.
 */
public final class SkillParser {
	public record Skill(String name, int level, float progress, String current, String needed, boolean maxed) {}

	public static final Pattern NAME = Pattern.compile("^([A-Z][A-Za-z]+)(?: ([IVXLCDM]+|\\d+))?$");
	private static final Pattern PROGRESS = Pattern.compile("Progress to (?:[A-Za-z]+ )?Level \\S+: ([\\d.]+)%", Pattern.CASE_INSENSITIVE);
	private static final Pattern AMOUNT = Pattern.compile("([\\d,.]+[kKmMbB]?)/([\\d,.]+[kKmMbB]?)\\s*$");
	private static final Pattern MAX = Pattern.compile("\\bmax(ed)?\\b", Pattern.CASE_INSENSITIVE);

	private SkillParser() {}

	public static Skill parse(String name, List<String> lore) {
		Matcher n = NAME.matcher(name.strip());
		if (!n.matches()) return null;
		int level = n.group(2) == null ? 0 : level(n.group(2));

		Float progress = null;
		String current = null, needed = null;
		boolean maxed = false;
		for (String raw : lore) {
			String line = raw.strip();
			Matcher p = PROGRESS.matcher(line);
			if (p.find()) progress = Float.parseFloat(p.group(1)) / 100f;
			Matcher a = AMOUNT.matcher(line);
			if (a.find() && current == null) {
				current = a.group(1);
				needed = a.group(2);
			}
			if (MAX.matcher(line).find()) maxed = true;
		}
		if (progress == null && !maxed) return null; // not a skill item
		return new Skill(n.group(1), level, maxed && progress == null ? 1f : Math.clamp(progress, 0f, 1f),
				current, needed, maxed && progress == null);
	}

	/** "2,070,294.5" → "2.07M", "800k" → "800k", "26,529" → "26.5k". Unparseable input is returned as is. */
	public static String compact(String amount) {
		if (amount == null) return null;
		String s = amount.replace(",", "").strip();
		double mult = switch (Character.toLowerCase(s.isEmpty() ? ' ' : s.charAt(s.length() - 1))) {
			case 'k' -> 1e3; case 'm' -> 1e6; case 'b' -> 1e9; default -> 1;
		};
		double v;
		try {
			v = Double.parseDouble(mult == 1 ? s : s.substring(0, s.length() - 1)) * mult;
		} catch (NumberFormatException e) {
			return amount;
		}
		String[] units = {"", "k", "M", "B"};
		int u = 0;
		while (v >= 1000 && u < units.length - 1) { v /= 1000; u++; }
		String n = v >= 100 || u == 0 ? String.format("%.0f", v) : v >= 10 ? String.format("%.1f", v) : String.format("%.2f", v);
		if (n.contains(".")) n = n.replaceAll("0+$", "").replaceAll("\\.$", "");
		return n + units[u];
	}

	/** "XXIV" or "24" → 24. */
	static int level(String s) {
		if (Character.isDigit(s.charAt(0))) return Integer.parseInt(s);
		int total = 0, prev = 0;
		for (int i = s.length() - 1; i >= 0; i--) {
			int v = switch (s.charAt(i)) {
				case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
				case 'C' -> 100; case 'D' -> 500; default -> 1000;
			};
			total += v < prev ? -v : v;
			prev = Math.max(prev, v);
		}
		return total;
	}
}
