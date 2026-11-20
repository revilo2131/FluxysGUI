package cleanui.skyblock;

import org.junit.jupiter.api.Test;

import static cleanui.skyblock.SkyblockTime.*;
import static org.junit.jupiter.api.Assertions.*;

class SkyblockTimeTest {
	static Events.Event event(String name) {
		return Events.ALL.stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void epochIsYearOneEarlySpringFirst() {
		assertEquals(new Date(1, 0, 1, 0, 0), at(EPOCH));
		assertEquals("Early Spring 1st, Year 1", at(EPOCH).format());
		assertEquals("12:00 am", at(EPOCH).clock());
	}

	@Test
	void rollsOverDaysMonthsYears() {
		assertEquals(new Date(1, 0, 2, 0, 0), at(EPOCH + DAY));
		assertEquals(new Date(1, 1, 1, 0, 0), at(EPOCH + MONTH));
		assertEquals(new Date(2, 0, 1, 0, 0), at(EPOCH + YEAR));
		assertEquals(new Date(1, 0, 1, 12, 0), at(EPOCH + DAY / 2)); // noon
		assertEquals(start(3, 7, 29), EPOCH + 2 * YEAR + 7 * MONTH + 28 * DAY);
	}

	@Test
	void yearlyEvent() {
		Events.Occurrence spooky = Events.next(event("Spooky Festival"), EPOCH);
		assertEquals(start(1, 7, 29), spooky.start());
		assertEquals(start(1, 7, 31) + DAY, spooky.end());
		// once it's over, the next one is a year later
		assertEquals(start(2, 7, 29), Events.next(event("Spooky Festival"), spooky.end()).start());
	}

	@Test
	void wrappingEventIsActiveAcrossNewYear() {
		Events.Occurrence election = Events.next(event("Mayor Election"), EPOCH);
		assertTrue(election.active(EPOCH));
		assertEquals(start(0, 5, 27), election.start());
		assertEquals(start(1, 2, 27) + DAY, election.end());
	}

	@Test
	void hourlyEvents() {
		assertEquals(EPOCH + 3600, Events.next(event("Dark Auction"), EPOCH + 1).start());
		Events.Occurrence jacob = Events.next(event("Jacob's Contest"), EPOCH);
		assertEquals(EPOCH + DAY, jacob.start()); // XX:15 when the epoch is XX:55
		assertTrue(Events.next(event("Jacob's Contest"), EPOCH + DAY + 60).active(EPOCH + DAY + 60));
	}

	@Test
	void eventsOnADay() {
		assertTrue(Events.on(5, 7, 30).contains(event("Spooky Festival")));
		assertFalse(Events.on(5, 7, 28).contains(event("Spooky Festival")));
	}

	@Test
	void formatting() {
		assertEquals("2nd", ordinal(2));
		assertEquals("11th", ordinal(11));
		assertEquals("23rd", ordinal(23));
		assertEquals("2h 13m", duration(2 * 3600 + 13 * 60));
		assertEquals("1d 4h", duration(28 * 3600));
	}
}
