package cleanui.skyblock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static cleanui.skyblock.SkyblockTime.DAY;
import static cleanui.skyblock.SkyblockTime.EPOCH;

/**
 * Fixed SkyBlock event schedule, computed from the clock.
 * Dates from hypixelskyblock.minecraft.wiki "Events". Mayor-perk events are left out:
 * they depend on who won the election, which this mod doesn't read.
 */
public final class Events {
	/** {@code slot} picks the event's palette color. */
	public record Event(String name, int slot, Schedule schedule) {}

	public sealed interface Schedule {}

	/** Yearly window, inclusive, months 0-based. An end before the start wraps into the next year. */
	public record Yearly(int startMonth, int startDay, int endMonth, int endDay) implements Schedule {}

	/** Every 3 SkyBlock days (= every real hour), starting on days where dayIndex % 3 == offset. */
	public record EveryThreeDays(int offset, long duration) implements Schedule {}

	public record Occurrence(Event event, long start, long end) {
		public boolean active(long now) {
			return start <= now && now < end;
		}
	}

	public static final List<Event> ALL = List.of(
			// slot = color index: 0 purple, 1 blue, 2 pink, 3 red, 4 orange, 5 green, 6 teal
			new Event("Hoppity's Hunt", 2, new Yearly(0, 1, 2, 31)),
			new Event("Traveling Zoo", 6, new Yearly(3, 1, 3, 3)),
			new Event("Traveling Zoo", 6, new Yearly(9, 1, 9, 3)),
			new Event("Mayor Election", 1, new Yearly(5, 27, 2, 27)),
			new Event("Spooky Festival", 4, new Yearly(7, 29, 7, 31)),
			new Event("Season of Jerry", 1, new Yearly(11, 24, 11, 26)),
			new Event("New Year Celebration", 3, new Yearly(11, 29, 11, 31)),
			new Event("Dark Auction", 0, new EveryThreeDays(0, 0)), // starts XX:55 real time
			new Event("Jacob's Contest", 5, new EveryThreeDays(1, DAY)) // XX:15 to XX:35 real time
	);

	private Events() {}

	/** The occurrence active at {@code now}, or else the next one to start. */
	public static Occurrence next(Event e, long now) {
		return switch (e.schedule()) {
			case Yearly y -> {
				int year = SkyblockTime.at(now).year();
				for (int yr = year - 1; ; yr++) {
					long start = SkyblockTime.start(yr, y.startMonth(), y.startDay());
					boolean wraps = y.endMonth() * 31 + y.endDay() < y.startMonth() * 31 + y.startDay();
					long end = SkyblockTime.start(wraps ? yr + 1 : yr, y.endMonth(), y.endDay()) + DAY;
					if (end > now) yield new Occurrence(e, start, end);
				}
			}
			case EveryThreeDays t -> {
				long day = Math.floorDiv(now - EPOCH, DAY);
				long start = EPOCH + (day - Math.floorMod(day - t.offset(), 3)) * DAY;
				if (start + t.duration() <= now) start += 3 * DAY;
				yield new Occurrence(e, start, start + t.duration());
			}
		};
	}

	/** Active events first, then upcoming by start time. */
	public static List<Occurrence> upcoming(long now) {
		List<Occurrence> list = new ArrayList<>();
		for (Event e : ALL) list.add(next(e, now));
		list.sort(Comparator.comparingLong(Occurrence::start));
		return list;
	}

	/** Yearly events overlapping one SkyBlock day. Hourly events are left out: they'd be on every day. */
	public static List<Event> on(int year, int month, int day) {
		long dayStart = SkyblockTime.start(year, month, day);
		List<Event> list = new ArrayList<>();
		for (Event e : ALL)
			if (e.schedule() instanceof Yearly && next(e, dayStart).start() < dayStart + DAY) list.add(e);
		return list;
	}
}
