package cleanui.skyblock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AbilityParserTest {
	@Test
	void chatLines() {
		assertEquals(new AbilityParser.Event(AbilityParser.Kind.USED, 0), AbilityParser.parse("You used your Tunnel Vision Pickaxe Ability!"));
		assertEquals(AbilityParser.Kind.EXPIRED, AbilityParser.parse("Your Tunnel Vision has expired!").kind());
		assertEquals(AbilityParser.Kind.AVAILABLE, AbilityParser.parse("Tunnel Vision is now available!").kind());
		assertEquals(new AbilityParser.Event(AbilityParser.Kind.COOLDOWN, 45), AbilityParser.parse("Your pickaxe ability is on cooldown for 45s."));
		assertEquals(new AbilityParser.Event(AbilityParser.Kind.COOLDOWN, 7), AbilityParser.parse("This ability is on cooldown for 7s."));
	}

	@Test
	void otherLinesIgnored() {
		assertNull(AbilityParser.parse("You used your Mining Speed Boost Pickaxe Ability!"));
		assertNull(AbilityParser.parse("Mining Speed Boost is now available!"));
		assertNull(AbilityParser.parse("[MVP+] Someone: tunnel vision is op"));
		assertNull(AbilityParser.parse(null));
	}

	@Test
	void cooldownFromLore() {
		assertEquals(110, AbilityParser.loreCooldown(List.of("Mining Speed 1,500", "", "Ability: Tunnel Vision RIGHT CLICK", "Grants rarer spawns.", "Cooldown: 110s")));
		assertEquals(-1, AbilityParser.loreCooldown(List.of("Ability: Mining Speed Boost", "Cooldown: 120s")));
	}
}
