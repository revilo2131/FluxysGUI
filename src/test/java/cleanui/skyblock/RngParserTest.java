package cleanui.skyblock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Lore from the real "Crystal Nucleus RNG Meter" menu (game log, 2026-11-21). */
class RngParserTest {
	@Test
	void drops() {
		var pick = RngParser.drop(List.of("Odds: Rare (3.2551%)", "", "Nucleus XP: 50,000/4,000", "", "Click to select!"));
		assertEquals("Rare", pick.rarity());
		assertEquals("3.2551%", pick.chance());
		assertEquals(1f, pick.fill()); // 50k of 4k: capped
		assertFalse(pick.selected());

		var alloy = RngParser.drop(List.of("Odds: RNGesus Incarnate (0.0055% 0.0061%)", "", "Progress: 5%",
				"                          50,000/1M", "", "SELECTED"));
		assertEquals("0.0061%", alloy.chance());
		assertEquals(0.05f, alloy.fill(), 1e-4);
		assertTrue(alloy.selected());

		assertNull(RngParser.drop(List.of("Find drops by name!")));
		assertEquals(0xFF5555, RngParser.color("RNGesus Incarnate"));
	}

	@Test
	void selectedDrop() {
		assertEquals("Divan's Alloy", RngParser.selected(List.of("Resets the selected drop.", "", "Selected Drop", "Divan's Alloy", "")));
		assertNull(RngParser.selected(List.of("nothing")));
	}
}
