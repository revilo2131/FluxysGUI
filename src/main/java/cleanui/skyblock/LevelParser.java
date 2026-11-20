package cleanui.skyblock;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads Hypixel's "SkyBlock Leveling" menu: the ranking item and the "Level N" reward steps. */
public final class LevelParser {
	public enum State { CURRENT, NEXT, MILESTONE, LOCKED }

	/** From "Your SkyBlock Level Ranking": level, total XP ("13,233"), share of all tasks done. */
	public record Summary(int level, String xp, Float tasks) {}

	/** One "Level N" item; done/total only for the next level, left only for a milestone. */
	public record Step(int level, State state, List<String> rewards, String done, String total, Float percent, String left) {}

	private static final Pattern NAME = Pattern.compile("^Level (\\d+)$");
	private static final Pattern YOUR_LEVEL = Pattern.compile("Your level: (\\d+)");
	private static final Pattern HAVE = Pattern.compile("You have: ([\\d,.]+[kKmM]?) XP");
	private static final Pattern TASKS = Pattern.compile("completed ([\\d.]+)%");
	private static final Pattern XP = Pattern.compile("([\\d,.]+[kKmM]?)\\s*/\\s*([\\d,.]+[kKmM]?) XP");
	private static final Pattern LEFT = Pattern.compile("XP Left to Gain: (.+)$");

	/** Prefix colour per 40 levels (wiki "SkyBlock Levels" → Prefix Color), Minecraft colour codes. */
	private static final int[] COLORS = {0xAAAAAA, 0xFFFFFF, 0xFFFF55, 0x55FF55, 0x00AA00, 0x55FFFF, 0x00AAAA,
			0x5555FF, 0xFF55FF, 0xAA00AA, 0xFFAA00, 0xFF5555, 0xAA0000};
	private static final String[] COLOR_NAMES = {"Gray", "White", "Yellow", "Green", "Dark Green", "Aqua", "Cyan",
			"Blue", "Pink", "Purple", "Gold", "Red", "Dark Red"};
	public static final int COLOR_STEP = 40;

	private LevelParser() {}

	/** The [level] colour Hypixel shows in chat and tab. */
	public static int color(int level) {
		return COLORS[Math.clamp(level / COLOR_STEP, 0, COLORS.length - 1)];
	}

	public static String colorName(int level) {
		return COLOR_NAMES[Math.clamp(level / COLOR_STEP, 0, COLOR_NAMES.length - 1)];
	}

	/** The next level that unlocks a new colour (132 → 160), or -1 past the last one. */
	public static int nextColorLevel(int level) {
		int next = (level / COLOR_STEP + 1) * COLOR_STEP;
		return next / COLOR_STEP < COLORS.length ? next : -1;
	}

	/** XP still needed to reach {@code target}; every SkyBlock level is 100 XP. */
	public static long xpLeft(Summary s, int target) {
		return Math.max(0, target * 100L - PerkParser.amount(s.xp()));
	}

	/** Null unless the lore has "Your level: N". */
	public static Summary summary(List<String> lore) {
		int level = -1;
		String xp = null;
		Float tasks = null;
		for (String line : lore) {
			Matcher m;
			if ((m = YOUR_LEVEL.matcher(line)).find()) level = Integer.parseInt(m.group(1));
			if ((m = HAVE.matcher(line)).find()) xp = m.group(1);
			if ((m = TASKS.matcher(line)).find()) tasks = Float.parseFloat(m.group(1)) / 100f;
		}
		return level < 0 ? null : new Summary(level, xp, tasks);
	}

	/** Null unless the item is named "Level N". */
	public static Step step(String name, List<String> lore) {
		Matcher n = NAME.matcher(name.strip());
		if (!n.matches()) return null;
		State state = State.LOCKED;
		List<String> rewards = new ArrayList<>();
		String done = null, total = null, left = null;
		boolean inRewards = false;
		for (String raw : lore) {
			String line = raw.strip();
			Matcher m;
			if (line.equals("Your Level")) state = State.CURRENT;
			else if (line.equals("Next Level")) state = State.NEXT;
			else if (line.equals("Next Milestone Level")) state = State.MILESTONE;
			if (line.startsWith("Reward")) inRewards = true;
			else if (line.isEmpty()) inRewards = false;
			else if (inRewards) rewards.add(line);
			if ((m = XP.matcher(line)).find()) {
				done = m.group(1);
				total = m.group(2);
			}
			if ((m = LEFT.matcher(line)).find()) left = m.group(1);
		}
		Float percent = done != null ? CollectionParser.ratio(done, total) : null;
		return new Step(Integer.parseInt(n.group(1)), state, rewards, done, total, percent, left);
	}
}
