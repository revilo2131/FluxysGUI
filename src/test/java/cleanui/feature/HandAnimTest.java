package cleanui.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandAnimTest {
	@Test
	void swingTicks() {
		assertEquals(6, HandAnim.swingTicks(6, 100));
		assertEquals(3, HandAnim.swingTicks(6, 200)); // twice as fast
		assertEquals(12, HandAnim.swingTicks(6, 50));
		assertEquals(1, HandAnim.swingTicks(6, 1000)); // never 0
	}

	@Test
	void equipProgress() {
		assertEquals(0f, HandAnim.equipProgress(0, 100));
		assertEquals(1f, HandAnim.equipProgress(300, 100));
		assertEquals(1f, HandAnim.equipProgress(150, 200)); // double speed: done in half the time
		assertEquals(0.875f, HandAnim.equipProgress(150, 100), 1e-6); // eased: 1 - 0.5^3
	}
}
