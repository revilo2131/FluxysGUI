package cleanui.skyblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatParserTest {
	@Test
	void statLines() {
		assertEquals(new StatParser.Stat("❤ Health", "1,245"), StatParser.stat(" ❤ Health 1,245"));
		assertEquals(new StatParser.Stat("☣ Crit Chance", "32.5%"), StatParser.stat("☣ Crit Chance: 32.5%"));
		assertEquals(new StatParser.Stat("☘ Farming Fortune", "+250"), StatParser.stat("☘ Farming Fortune +250"));
		assertEquals(new StatParser.Stat("Strength", "450"), StatParser.stat("Strength 450"));
		assertNull(StatParser.stat("Click to view!"));
		assertNull(StatParser.stat("Your tokens are refunded too."));
		assertNull(StatParser.stat("Gives you a better chance at 3"));
	}
}
