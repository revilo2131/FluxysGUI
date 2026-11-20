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
	void realTamingLore() {
		// from the live Hypixel menu (Nov 2026)
		var s = SkillParser.parse("Taming XLVIII", List.of(
				"Level up pets to earn Taming EXP!", "",
				"Progress to Level XLIX: 56%",
				"                    2,070,294.5/3.7M"));
		assertEquals(48, s.level());
		assertEquals(0.56f, s.progress(), 1e-4);
		assertEquals("2.07M", SkillParser.compact(s.current()));
		assertEquals("3.7M", SkillParser.compact(s.needed()));
	}

	@Test
	void dungeoneeringHasNoProgressInThisMenu() {
		// from the live menu: just a description, so it isn't a parsed skill
		assertNull(SkillParser.parse("Dungeoneering", List.of("Complete Dungeons to level up your", "classes!", "", "Click to view!")));
	}

	@Test
	void compactNumbers() {
		assertEquals("800k", SkillParser.compact("800k"));
		assertEquals("26.5k", SkillParser.compact("26,529"));
		assertEquals("50", SkillParser.compact("50"));
		assertEquals("1.2B", SkillParser.compact("1,200,000,000"));
		assertEquals("12.5M", SkillParser.compact("12.5M"));
		assertEquals("?", SkillParser.compact("?"));
	}

	@Test
	void romanNumerals() {
		assertEquals(4, SkillParser.level("IV"));
		assertEquals(9, SkillParser.level("IX"));
		assertEquals(49, SkillParser.level("XLIX"));
		assertEquals(60, SkillParser.level("LX"));
	}
}
