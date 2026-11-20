package cleanui.skyblock;

/**
 * SkyBlock calendar math. Pure arithmetic on the real clock: no server data.
 * Source: hypixelskyblock.minecraft.wiki "Time Systems".
 */
public final class SkyblockTime {
	/** Year 1, Early Spring 1st, 00:00 (Jun 11 2019 17:55 UTC), in unix seconds. */
	public static final long EPOCH = 1560275700L;
	public static final long DAY = 1200; // 20 real minutes
	public static final long MONTH = DAY * 31;
	public static final long YEAR = MONTH * 12; // 124 real hours

	public static final String[] MONTHS = {
			"Early Spring", "Spring", "Late Spring",
			"Early Summer", "Summer", "Late Summer",
			"Early Autumn", "Autumn", "Late Autumn",
			"Early Winter", "Winter", "Late Winter",
	};
	private static final String[] SEASONS = {"Spring", "Summer", "Autumn", "Winter"};

	private SkyblockTime() {}

	/** A SkyBlock date. {@code month} is 0-based, {@code day} 1-based (1..31). */
	public record Date(int year, int month, int day, int hour, int minute) {
		public String monthName() {
			return MONTHS[month];
		}

		public String season() {
			return SEASONS[month / 3];
		}

		/** e.g. "Late Spring 14th, Year 412" */
		public String format() {
			return monthName() + " " + ordinal(day) + ", Year " + year;
		}

		/** e.g. "2:30 pm". Minutes step by 10 like the in-game clock. */
		public String clock() {
			int h = hour % 12 == 0 ? 12 : hour % 12;
			return String.format("%d:%02d %s", h, minute, hour < 12 ? "am" : "pm");
		}
	}

	public static long now() {
		return System.currentTimeMillis() / 1000;
	}

	public static Date at(long unixSeconds) {
		long s = unixSeconds - EPOCH;
		int year = (int) Math.floorDiv(s, YEAR) + 1;
		long inYear = Math.floorMod(s, YEAR);
		long inDay = inYear % DAY;
		int minute = (int) (inDay % 50 * 60 / 50) / 10 * 10; // 1 SkyBlock hour = 50 real seconds
		return new Date(year, (int) (inYear / MONTH), (int) (inYear % MONTH / DAY) + 1, (int) (inDay / 50), minute);
	}

	/** Unix second at which the given SkyBlock day starts. */
	public static long start(int year, int month, int day) {
		return EPOCH + (year - 1) * YEAR + month * MONTH + (day - 1) * DAY;
	}

	public static String ordinal(int n) {
		if (n % 100 >= 11 && n % 100 <= 13) return n + "th";
		return n + switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
	}

	/** Compact duration: "45s", "12m", "2h 13m", "1d 4h". */
	public static String duration(long seconds) {
		if (seconds < 60) return Math.max(seconds, 0) + "s";
		long m = seconds / 60, h = m / 60, d = h / 24;
		if (d > 0) return d + "d " + h % 24 + "h";
		if (h > 0) return h + "h " + m % 60 + "m";
		return m + "m";
	}
}
