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
}
