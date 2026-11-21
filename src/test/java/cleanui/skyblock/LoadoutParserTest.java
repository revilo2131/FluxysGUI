package cleanui.skyblock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Lore copied from the real "(1/3) Loadouts" menu (game log, 2026-11-21). */
class LoadoutParserTest {
	@Test
	void loadouts() {
		var one = LoadoutParser.parse(List.of("Helmet: Necrotic Wise Dragon Helmet", "Chestplate: Loving Wise Dragon Chestplate",
				"Leggings: Necrotic Wise Dragon Leggings", "Boots: Necrotic Wise Dragon Boots", "", "Necklace: Menacing Bone Necklace", "",
				"Pet: None", "HOTM: None", "", "Left-click to equip!", "Right-click to edit"));
		assertEquals("Loving Wise Dragon Chestplate", one.armor()[1]);
		assertNull(one.pet());
		assertFalse(one.active());
		assertFalse(one.empty());

		var inUse = LoadoutParser.parse(List.of("Helmet: Jaded Mineral Helmet", "Chestplate: Jaded Glossy Mineral Chestplate",
				"Leggings: Jaded Glossy Mineral Leggings", "Boots: Jaded Glossy Mineral Boots", "", "Pet: [Lvl 96] Armadillo", "", "Right-click to edit"));
		assertTrue(inUse.active());
		assertEquals("[Lvl 96] Armadillo", inUse.pet());

		var blank = LoadoutParser.parse(List.of("Helmet: None", "Chestplate: None", "Leggings: None", "Boots: None", "", "Pet: None", "",
				"Right-click to edit", "", "You must customize this loadout", "before you can equip it!"));
		assertTrue(blank.empty());
		assertFalse(blank.active());
		assertNull(blank.armor()[0]);

		assertNull(LoadoutParser.parse(List.of("Choose your selected Power Stone.", "Current: Sighted")));
	}

	@Test
	void equippedFromChat() {
		assertEquals("Loadout 5", LoadoutParser.equipped("You equipped Loadout 5!")); // real chat line, game log 2026-11-21
		assertEquals("Loadout 10", LoadoutParser.equipped("You equipped Loadout 10!"));
		assertNull(LoadoutParser.equipped("You equipped Divan's Helmet!"));
		assertNull(LoadoutParser.equipped("Guild > Someone: You equipped Loadout 5!"));
	}

	@Test
	void lookupNames() {
		assertEquals(List.of("Necrotic Wise Dragon Helmet", "Wise Dragon Helmet", "Dragon Helmet"), LoadoutParser.candidates("Necrotic Wise Dragon Helmet"));
		assertEquals("Shadow Assassin Helmet", LoadoutParser.candidates("  Ancient Shadow Assassin Helmet").get(1));
		assertEquals("Helmet of Divan", LoadoutParser.candidates("Jaded Helmet of Divan").get(1));
		assertEquals("Mastiff Crown", LoadoutParser.candidates("Ancient Mastiff Crown ✪✪").get(1));
	}
}
