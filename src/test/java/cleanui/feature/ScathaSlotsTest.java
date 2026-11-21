package cleanui.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScathaSlotsTest {
	@Test
	void scathaHealthFromNametag() {
		assertEquals(10, ScathaSlots.Tag.health("[Lv10] Scatha 10/10❤"));
		assertEquals(3, ScathaSlots.Tag.health("[Lv10] Scatha 3/10❤"));
		assertEquals(2, ScathaSlots.Tag.health("Scatha 2❤"));
		assertEquals(1500, ScathaSlots.Tag.health("[Lv10] Scatha 1.5k/2k❤"));
		assertEquals(-1, ScathaSlots.Tag.health("[Lv5] Worm 5/5❤"));
	}

	@Test
	void noScathaAfterAnythingElse() {
		java.util.Random rng = new java.util.Random(1);
		boolean sawTwoScathas = false;
		for (int n = 0; n < 20000; n++) {
			int[] r = ScathaSlots.Roll.miss(rng, 8, 0.35f);
			for (int i = 1; i < 3; i++)
				if (r[i - 1] != 0) assertEquals(true, r[i] != 0, "Scatha after a non-Scatha: " + java.util.Arrays.toString(r));
			assertEquals(false, r[0] == 0 && r[1] == 0 && r[2] == 0);
			sawTwoScathas |= r[0] == 0 && r[1] == 0;
		}
		assertEquals(true, sawTwoScathas); // near misses still happen
	}

	@Test
	void petRarityFromDropLine() {
		assertEquals(0, ScathaSlots.Tag.rarity("§6§lPET DROP! §9Scatha §b(+300% ✯ Magic Find)"));
		assertEquals(1, ScathaSlots.Tag.rarity("§6§lPET DROP! §5Scatha"));
		assertEquals(2, ScathaSlots.Tag.rarity("§6§lPET DROP! §6§lScatha"));
		assertEquals(-1, ScathaSlots.Tag.rarity("PET DROP! Scatha"));
		assertEquals(1, ScathaSlots.Tag.rarityOf(0xAA00AA));
	}
}
