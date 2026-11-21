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

	/** Lore copied from the real menus (game log, 2026-11-21). */
	@Test
	void realMenus() {
		var hub = SlayerParser.progress(List.of(" Undead", "", "Abhorrent Zombie stuck between", "life and death for an eternity.", "",
				"Zombie Slayer: LVL 6", "", "Click to view boss!"));
		assertEquals(6, hub.level());
		assertFalse(hub.locked());
		assertTrue(SlayerParser.progress(List.of(" Infernal", "", "Slay Voidgloom Seraph III!")).locked());
		assertEquals("Only inside The Rift!", SlayerParser.requirement(List.of("A half-vampire,", "", "Only inside The Rift!")));

		var leveling = SlayerParser.progress(List.of("Zombie Slayer LVL", "", "1. Kill boss to get XP", "2. Gain LVL from XP", "",
				"Current LVL: 6", "", "Zombie Slayer XP to LVL 7:", "                     25,730/100k", "", "Click to view levels!"));
		assertEquals(6, leveling.level());
		assertEquals("25,730", leveling.done());
		assertEquals(0.2573f, leveling.percent(), 1e-4);

		var stats = SlayerParser.stats(List.of("Ultimate bragging rights", "", "Revenant Horror: 25,730 (Top 15.3%)", "Voidgloom Seraph: N/A"),
				List.of("Highest slain tiers", "Revenant Horror: Tier IV", "Riftstalker Bloodfiend: No kills"));
		var rev = stats.get(SlayerParser.boss("Revenant Horror"));
		assertEquals("25,730", rev.xp());
		assertEquals("Top 15.3%", rev.top());
		assertEquals("Tier IV", rev.bestTier());
		assertNull(stats.get(SlayerParser.boss("Voidgloom Seraph")));

		var q = SlayerParser.quest(List.of("You have an active Slayer quest.", "", "Boss: Sven Packmaster IV", "Kill Wolves to spawn the boss!"));
		assertEquals("Sven Packmaster IV", q.boss());
		assertEquals("Kill Wolves to spawn the boss!", q.objective());
		assertEquals(100_000, SlayerParser.boss("Revenant Horror").nextLevelXp(6));
		assertEquals(-1, SlayerParser.boss("Revenant Horror").nextLevelXp(9));
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
