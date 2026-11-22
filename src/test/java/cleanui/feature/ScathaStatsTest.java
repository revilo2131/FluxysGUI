package cleanui.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScathaStatsTest {
	@Test
	void readsWormsAndScathas() {
		assertArrayEquals(new float[]{0, 5}, ScathaStats.parse("[Lv119] Stoneworm 5/5❤"));
		assertArrayEquals(new float[]{0, 5}, ScathaStats.parse("[Lv5] Worm 5/5❤"));
		assertArrayEquals(new float[]{1, 3}, ScathaStats.parse("[Lv10] Scatha 3/10❤"));
		assertArrayEquals(new float[]{1, 1500}, ScathaStats.parse("[Lv10] Scatha 1.5k/2k❤"));
	}

	@Test
	void ignoresOtherMobs() {
		assertNull(ScathaStats.parse("[Lv50] Flaming Worm 1,000/1,000❤"));
		assertNull(ScathaStats.parse("[Lv5] Goblin 50/50❤"));
		assertNull(ScathaStats.parse("Worm"));
	}

	@Test
	void readsBestiaryKills() {
		assertArrayEquals(new long[]{0, 12345}, ScathaStats.fromBestiary("[Lv119] Stoneworm", java.util.List.of("Mob Stats:", "Your [Lv119] Stoneworm Stats:", "Kills: 12,345", "Deaths: 0")));
		assertArrayEquals(new long[]{1, 396}, ScathaStats.fromBestiary("[Lv181] Scatha", java.util.List.of("Kills: 396")));
		assertNull(ScathaStats.fromBestiary("Stoneworm XV", java.util.List.of("Kills: 1,050"))); // the family: both added up
		assertNull(ScathaStats.fromBestiary("Flaming Worm IV", java.util.List.of("Kills: 50")));
		assertNull(ScathaStats.fromBestiary("[Lv119] Stoneworm", java.util.List.of("Kill this mob to add it")));
	}
}
