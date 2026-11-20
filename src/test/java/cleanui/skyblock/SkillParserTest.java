package cleanui.skyblock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillParserTest {
	@Test
	void inProgressSkill() {
		var s = SkillParser.parse("Farming XXIII", List.of(
				"Harvest crops and shear sheep to earn Farming XP!",
				"",
				"Progress to Level XXIV: 3.3%",
				"                         26,529/800k",
				"",
				"Click to view!"));
		assertNotNull(s);
		assertEquals("Farming", s.name());
		assertEquals(23, s.level());
		assertEquals(0.033f, s.progress(), 1e-4);
		assertEquals("26,529", s.current());
		assertEquals("800k", s.needed());
		assertFalse(s.maxed());
	}

	@Test
	void numericLevelsSetting() {
		var s = SkillParser.parse("Mining 50", List.of("Progress to Level 51: 12.5%", "1.2M/4M"));
		assertEquals(50, s.level());
		assertEquals("1.2M", s.current());
	}

	@Test
	void maxedSkill() {
		var s = SkillParser.parse("Alchemy L", List.of("Brew potions to earn Alchemy XP!", "", "MAX SKILL LEVEL REACHED!"));
		assertNotNull(s);
		assertEquals(50, s.level());
		assertTrue(s.maxed());
		assertEquals(1f, s.progress());
	}

	@Test
	void maxedInAnyCase() {
		var s = SkillParser.parse("Mining LX", List.of("Max Skill level reached!"));
		assertNotNull(s);
		assertTrue(s.maxed());
		assertEquals(60, s.level());
	}

	@Test
	void levelZero() {
		var s = SkillParser.parse("Carpentry", List.of("Progress to Level I: 0%", "0/50"));
		assertEquals(0, s.level());
	}

	@Test
	void nonSkillItemsAreIgnored() {
		assertNull(SkillParser.parse("Go Back", List.of("To SkyBlock Menu")));
		assertNull(SkillParser.parse("Your Skills", List.of("View your Skill progression")));
		assertNull(SkillParser.parse("Close", List.of()));
	}

	@Test
	void romanNumerals() {
		assertEquals(4, SkillParser.level("IV"));
		assertEquals(9, SkillParser.level("IX"));
		assertEquals(49, SkillParser.level("XLIX"));
		assertEquals(60, SkillParser.level("LX"));
	}
}
