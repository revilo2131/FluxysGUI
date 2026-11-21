package cleanui.skyblock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NucleusParserTest {
	private static List<NucleusParser.Event> feed(NucleusParser p, String... lines) {
		List<NucleusParser.Event> out = new ArrayList<>();
		for (String l : lines) {
			NucleusParser.Event e = p.line(l, l.startsWith("Flawed") ? 0x55FF55 : 0xAA00AA);
			if (e != null) out.add(e);
		}
		return out;
	}

	@Test
	void foundAndPlaced() {
		NucleusParser p = new NucleusParser();
		var events = feed(p, "-----------", "✦ CRYSTAL FOUND (3/5)", "Sapphire Crystal", "",
				"Place this Crystal in the Crystal Nucleus and", "✦ You placed the Sapphire Crystal!", "You have already obtained this Crystal!!");
		assertEquals(List.of(new NucleusParser.Found("Sapphire", 3), new NucleusParser.Placed("Sapphire")), events);
	}

	@Test
	void lootBundle() {
		NucleusParser p = new NucleusParser();
		var events = feed(p, "CRYSTAL NUCLEUS LOOT BUNDLE", "REWARDS", "Jaderald", "Pickonimbus 2000", "❈ Fine Amber Gemstone",
				"Flawed Jade Gemstone x66", "+800 HOTM Exp", "[SkyHanni] Profit for Crystal Nucleus Run: 1.5M", "--------", "Pick it up near the Nucleus Vault!");
		assertEquals(new NucleusParser.Profit("1.5M"), events.get(0));
		var b = (NucleusParser.Bundle) events.get(1);
		assertEquals(4, b.items().size());
		assertEquals(new NucleusParser.Loot("Jaderald", 1, 0xAA00AA), b.items().get(0));
		assertEquals("Fine Amber Gemstone", b.items().get(2).name());
		assertEquals(new NucleusParser.Loot("Flawed Jade Gemstone", 66, 0x55FF55), b.items().get(3));
		assertEquals(List.of("+800 HOTM Exp"), b.extras());
	}
}
