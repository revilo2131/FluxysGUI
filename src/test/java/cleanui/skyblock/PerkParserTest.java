package cleanui.skyblock;

import cleanui.skyblock.PerkParser.State;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PerkParserTest {
	@Test
	void perkStates() {
		var p = PerkParser.perk("minecraft:emerald", List.of("Level 12/50", "", "Grants +240 Mining Speed."));
		assertEquals(12, p.level());
		assertEquals(50, p.max());
		assertEquals(State.UNLOCKED, p.state());
		assertEquals(State.MAXED, PerkParser.perk("minecraft:diamond", List.of("Level 50/50")).state());
		assertEquals(State.LOCKED, PerkParser.perk("minecraft:coal", List.of("Level 0/50")).state());
		assertEquals(State.LOCKED, PerkParser.perk("minecraft:emerald", List.of("Requires HOTM Tier 5 to unlock.")).state());
	}

	@Test
	void tiersAndTokens() {
		assertEquals(7, PerkParser.tier("Tier 7"));
		assertEquals(-1, PerkParser.tier("Mining Speed"));
		assertEquals(3, PerkParser.tokens(List.of("Your tokens", "Token of the Mountain: 3")));
		assertEquals(-1, PerkParser.tokens(List.of("nothing")));
	}
}
