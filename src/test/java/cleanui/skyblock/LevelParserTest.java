package cleanui.skyblock;

import cleanui.skyblock.LevelParser.State;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Lore copied from the real menu (game log, 2026-11-21). */
class LevelParserTest {
	@Test
	void summary() {
		var s = LevelParser.summary(List.of("Classic Mode", "", "Your level: 132", "You have: 13,233 XP", "",
				"You have completed 22.8% of the", "total SkyBlock XP Tasks."));
		assertEquals(132, s.level());
		assertEquals("13,233", s.xp());
		assertEquals(0.228f, s.tasks(), 1e-4);
		assertNull(LevelParser.summary(List.of("Click to view!")));
	}

	@Test
	void steps() {
		var cur = LevelParser.step("Level 132", List.of("Your Level", "", "Reward:", " +5 Health", "", "UNLOCKED", "", "Click to view rewards!"));
		assertEquals(State.CURRENT, cur.state());
		assertEquals(List.of("+5 Health"), cur.rewards());

		var next = LevelParser.step("Level 133", List.of("Next Level", "", "Reward:", " +5 Health", "", "Progress to Level Up:",
				"                          33/100 XP", "", "Click to view rewards!"));
		assertEquals(State.NEXT, next.state());
		assertEquals("33", next.done());
		assertEquals(0.33f, next.percent(), 1e-4);

		var mile = LevelParser.step("Level 140", List.of("Next Milestone Level", "", "Rewards:", " Soulflow Emblem ⸎", " +1 Strength",
				" +5 Health", "", "XP Left to Gain: 767 XP (8 Levels)", "", "Click to view rewards!"));
		assertEquals(State.MILESTONE, mile.state());
		assertEquals(3, mile.rewards().size());
		assertEquals("767 XP (8 Levels)", mile.left());

		assertEquals(State.LOCKED, LevelParser.step("Level 134", List.of("Reward:", " +5 Health")).state());
		assertNull(LevelParser.step("Leveling Rewards", List.of()));
	}
}
