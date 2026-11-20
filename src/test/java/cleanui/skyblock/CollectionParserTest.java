package cleanui.skyblock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollectionParserTest {
	@Test
	void collectionWithTierAndProgress() {
		var e = CollectionParser.parse("Wheat XI", List.of(
				"Progress to Wheat XII: 45.2%", "               55,000/100k", "", "Total Collected: 1,234,567", "", "Click to view!"));
		assertEquals("Wheat", e.name());
		assertEquals(11, e.tier());
		assertEquals(0.452f, e.percent(), 1e-4);
		assertEquals("55,000", e.done());
		assertEquals("100k", e.total());
		assertEquals("1,234,567", e.collected());
		assertFalse(e.locked());
	}

	@Test
	void multiWordNames() {
		var e = CollectionParser.parse("Nether Wart IX", List.of("Total collected: 2.5M"));
		assertEquals("Nether Wart", e.name());
		assertEquals(9, e.tier());
		assertEquals("2.5M", e.collected());
	}

	@Test
	void categoryWithCountOnly() {
		var e = CollectionParser.parse("Farming Collections", List.of("View your Farming Collections!", "", "12/15"));
		assertEquals("Farming Collections", e.name());
		assertEquals(-1, e.tier());
		assertEquals(0.8f, e.percent(), 1e-4); // from 12/15
	}

	@Test
	void maxedAndLocked() {
		assertTrue(CollectionParser.parse("Cactus X", List.of("Collection maxed out!")).maxed());
		var locked = CollectionParser.parse("Gemstone", List.of("Find this item to add it to your", "collection and unlock rewards!"));
		assertTrue(locked.locked());
	}

	@Test
	void maxedCountIsNotMaxed() {
		var e = CollectionParser.parse("Combat Collections", List.of("Collections Maxed Out: 4/11"));
		assertFalse(e.maxed());
		assertEquals("Maxed Out", e.amountLabel());
		assertEquals("4", e.done());
		assertEquals("Unlocked", CollectionParser.parse("Farming Collections", List.of("Collections Unlocked: 12/15")).amountLabel());
	}

	@Test
	void petLevels() {
		var e = CollectionParser.parse("[Lvl 87] Golden Dragon", List.of("Legendary", "Progress to Level 88: 41.2%", "1.2M/3.4M"));
		assertEquals("Golden Dragon", e.name());
		assertEquals(87, e.tier());
		assertTrue(e.arabic());
		assertEquals(0.412f, e.percent(), 1e-4);
		assertTrue(CollectionParser.parse("[Lvl 100] Ender Dragon", List.of("MAX LEVEL")).maxed());
	}

	@Test
	void levelFromLore() {
		var healer = CollectionParser.parse("Healer", List.of("Healer Level 24", "Progress to Level 25: 38%", "SELECTED"));
		assertEquals(24, healer.tier());
		assertTrue(healer.arabic());
		assertEquals(0.38f, healer.percent(), 1e-4);
		assertEquals(30, CollectionParser.parse("Catacombs", List.of("Catacombs Level: XXX")).tier());
		// a "Progress to Level" line is not the current level
		assertEquals(-1, CollectionParser.parse("Thing", List.of("Progress to Level 5: 10%")).tier());
	}

	@Test
	void collectionItemIds() {
		assertEquals("nether_wart", CollectionItems.idFor("Nether Wart"));
		assertEquals("melon_slice", CollectionItems.idFor("Melon"));
		assertEquals("mutton", CollectionItems.idFor("Mutton"));
		assertEquals("cocoa_beans", CollectionItems.idFor("Cocoa Beans"));
	}

	@Test
	void romanRoundTrip() {
		for (int n = 1; n <= 60; n++) assertEquals(n, SkillParser.level(CollectionParser.roman(n)));
		assertEquals("XI", CollectionParser.roman(11));
	}

	@Test
	void ratioIgnoresSuffixes() {
		assertNull(CollectionParser.ratio("5k", "10k"));
		assertEquals(0.5f, CollectionParser.ratio("5", "10"), 1e-6);
	}
}
