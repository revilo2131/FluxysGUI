package cleanui.skyblock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Lore is guessed from Hypixel's usual wording; the real menus get logged on first open. */
class SlayerParserTest {
	@Test
	void bosses() {
		assertEquals("Sven Packmaster", SlayerParser.boss("Sven Packmaster").name());
		assertEquals("Revenant Horror", SlayerParser.boss("Revenant Horror IV").name());
		assertEquals("Voidgloom Seraph", SlayerParser.boss("Enderman Slayer LVL 3").name());
		assertNull(SlayerParser.boss("Auto-Slayer"));
		assertEquals(5, SlayerParser.boss("Riftstalker Bloodfiend").maxLevel());
	}

	@Test
	void progress() {
		var p = SlayerParser.progress(List.of("Abhorrent Zombie stuck between", "life and death.", "", "Zombie Slayer: LVL 7",
				"Progress to LVL 8: 61.2%", "Zombie Slayer XP: 245,000/400,000", "", "Click to view boss!"));
		assertEquals(7, p.level());
		assertEquals("245,000", p.done());
		assertEquals(0.612f, p.percent(), 1e-4);
		assertFalse(p.locked());

		// a requirement's LVL is not the boss's level
		var locked = SlayerParser.progress(List.of("Requires Combat LVL 24", "LOCKED"));
		assertEquals(-1, locked.level());
		assertTrue(locked.locked());

		var maxed = SlayerParser.progress(List.of("Wolf Slayer: LVL 9", "Wolf Slayer XP: 2,000,000/2M"));
		assertEquals(1f, maxed.percent());
	}

	@Test
	void tiersAndDetails() {
		assertEquals(3, SlayerParser.tier("Sven Packmaster III"));
		assertEquals(-1, SlayerParser.tier("Boss Leveling Rewards"));
		var d = SlayerParser.details(List.of("Smells like wet dog.", "", "Health: 2,000❤", "Damage: 60 per second",
				"Reward: 25 Wolf Slayer XP", "Cost to start: 10,000 coins", "", "Click to slay!"), 4);
		assertEquals(4, d.size());
		assertArrayEquals(new String[]{"Health", "2,000❤"}, d.get(0));
		assertArrayEquals(new String[]{"Cost to start", "10,000 coins"}, d.get(3));
	}
}
