package cleanui.skyblock;

import cleanui.skyblock.TrackParser.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackParserTest {
	@Test
	void tiers() {
		assertEquals(new TrackParser.Tier("Wheat", 11, false), TrackParser.tier("Wheat XI"));
		assertEquals(new TrackParser.Tier("Farming", 25, true), TrackParser.tier("Farming 25"));
		assertEquals("Nether Wart", TrackParser.tier("Nether Wart IX").name());
		assertNull(TrackParser.tier("Go Back"));
	}

	@Test
	void paneStates() {
		assertEquals(State.DONE, TrackParser.state("minecraft:lime_stained_glass_pane"));
		assertEquals(State.CURRENT, TrackParser.state("minecraft:yellow_stained_glass_pane"));
		assertEquals(State.LOCKED, TrackParser.state("minecraft:red_stained_glass_pane"));
		assertTrue(TrackParser.isPane("minecraft:red_stained_glass_pane"));
		assertFalse(TrackParser.isPane("minecraft:wheat"));
	}

	/** Lore from the real "Combat Skill" menu (game log, 2026-11-21). */
	@Test
	void skillLoreStates() {
		var done = java.util.List.of("Rewards:", "  Warrior XXV", "    Deal 96➜100% more damage to mobs.", "", "UNLOCKED");
		var current = java.util.List.of("Rewards:", "  Warrior XXVIII", "    Deal 108➜112% more damage to mobs.", "",
				"Progress: 66.8%", "                          667,615.3/1M");
		var locked = java.util.List.of("Rewards:", "  Warrior XXXIV", "    Deal 132➜136% more damage to mobs.");
		assertEquals(State.DONE, TrackParser.stateFromLore(done));
		assertEquals(State.CURRENT, TrackParser.stateFromLore(current));
		assertEquals(State.LOCKED, TrackParser.stateFromLore(locked));
		assertNull(TrackParser.progress(done)); // the "100%" in the reward text is not progress
		var p = TrackParser.progress(current);
		assertEquals(0.668f, p.percent(), 1e-4);
		assertEquals("667,615.3", p.done());
		assertEquals("1M", p.total());
	}

	@Test
	void skillXp() {
		assertEquals(111_672_425L, TrackParser.xpFor(60));
		assertEquals(55_172_425L, TrackParser.xpFor(50));
		assertEquals(60, TrackParser.maxLevel("Combat"));
		assertEquals(-1, TrackParser.maxLevel("Runecrafting"));
	}
}
