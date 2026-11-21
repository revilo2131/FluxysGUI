package cleanui.feature;

import cleanui.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2fStack;

import java.util.Random;
import java.util.Set;

/**
 * Scatha roulette: a European wheel seen at an angle. On a kill the ball is thrown against the
 * spin, rolls on the track, drops in, bounces across the pockets and settles. Green (0) = the pet
 * dropped. Timing, outcome, chat hiding and the celebration are run by {@link ScathaSlots};
 * this class is the wheel's motion, sounds and drawing. Everything is drawn around (0, 0).
 *
 * 3D: the wheel is a flat disc squashed vertically by {@link #TILT}. Pockets, dividers and numbers
 * are drawn in the disc's own plane (pose: scale y by TILT, then rotate), so they foreshorten
 * like the real thing; rims and the cone are stacked ellipses for height.
 */
final class ScathaRoulette {
	/** European wheel order, clockwise from the green zero. */
	private static final int[] ORDER = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1,
			20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
	private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
	private static final int N = ORDER.length;
	private static final double STEP = 2 * Math.PI / N;

	private static final float TILT = 0.42f, DEPTH = 9;
	private static final float R_OUT = 100, R_RIM = 92, R_TRACK = 85, R_POCKET_OUT = 77, R_POCKET_IN = 60, R_CONE = 56;
	private static final float R_BALL = (R_POCKET_OUT + R_POCKET_IN) / 2 + 2;

	/** ms: ball thrown, drops off the track, settles in its pocket. */
	static final int THROW = 600, DROP = 3700, LAND = 5400;
	/** On green, ms after landing: portal opens, pet starts rising, takes this long, then the text and confetti. */
	private static final int OPEN_MS = 600, PET_RISE = 450, RISE_MS = 900;
	static final int SUMMON = 1900;
	/** Size for layout, and where the result line goes (below the wheel). */
	static final int W = Math.round(2 * R_OUT), H = Math.round(2 * R_OUT * TILT + DEPTH + 34);
	static final int MESSAGE_Y = Math.round(R_OUT * TILT + DEPTH + 10);

	private static final int GREEN = 0x0E8A42, RED_C = 0xB8161C, BLACK = 0x161618, GOLD = 0xD9AC3C, WOOD = 0x5A2A12;

	private static int target; // index into ORDER where the ball ends
	private static int lastPocket, lastBounce, summonSound;

	private ScathaRoulette() {}

	static void reset() {
		target = 1;
		offset = 0;
		summonSound = 0;
		lastPocket = Integer.MIN_VALUE;
		lastBounce = 0;
	}

	/** Green for a pet, otherwise any red or black pocket. */
	static void decide(boolean win, Random rng) {
		target = win ? 0 : 1 + rng.nextInt(N - 1);
		offset = 0;
		double atLand = wheel(LAND) + target * STEP;
		offset = Math.floorMod(Math.round((Math.PI / 2 - atLand) * 1e6), Math.round(2 * Math.PI * 1e6)) / 1e6; // forward only, < 1 turn
	}

	static String pocketName() {
		int n = ORDER[target];
		return n == 0 ? "Green" : (RED.contains(n) ? "Red " : "Black ") + n;
	}

	static int pocketTextColor() {
		int n = ORDER[target];
		return n == 0 ? 0x55FF55 : RED.contains(n) ? 0xFF5555 : 0xDDDDDD;
	}

	// ---- motion ----

	/** Wheel angle (radians) at t ms: starts quick, slows to a steady turn. */
	private static double wheel(long t) {
		double s = t / 1000.0, tau = 3.0;
		// eased in after the outcome is known, so the ball's pocket stops at the front, facing you
		float k = Math.max(0f, Math.min(1f, (t - 400) / (float) (LAND - 400)));
		return 0.35 * s + 1.1 * tau * (1 - Math.exp(-s / tau)) + offset * k * k * (3 - 2 * k);
	}

	private static double offset;

	private static float landProgress(long t) {
		return Math.max(0f, Math.min(1f, (t - THROW) / (float) (LAND - THROW)));
	}

	/** Ball angle: thrown hard against the spin, ends exactly over its pocket and then rides with the wheel. */
	private static double ballAngle(long t) {
		double rest = 1 - landProgress(t);
		return wheel(t) + target * STEP - 2 * Math.PI * 5 * rest * rest * rest;
	}

	private static float bounceU(long t) {
		return Math.max(0f, Math.min(1f, (t - DROP) / (float) (LAND - DROP)));
	}

	/** Ball distance from the centre: on the track, then dropping in with bounces off the pocket dividers. */
	private static float ballRadius(long t) {
		if (t < DROP) return R_TRACK;
		float u = bounceU(t);
		return R_BALL + (R_TRACK - R_BALL) * (1 - u) * Math.abs((float) Math.cos(u * 3 * Math.PI));
	}

	/** How high the ball hops (screen px) while bouncing. */
	private static float ballHop(long t) {
		if (t < DROP || t >= LAND) return 0;
		float u = bounceU(t);
		return 6 * (1 - u) * Math.abs((float) Math.sin(u * 3 * Math.PI));
	}

	/** Rolling clicks while the ball runs over the pockets, a clack on each bounce; on green, the summoning. */
	static void update(long t, boolean win) {
		if (win && t >= LAND && summonSound < 1) {
			summonSound = 1;
			ScathaSlots.sound(SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 0.9f);
			ScathaSlots.sound(SoundEvents.PORTAL_TRIGGER, 1.4f, 0.35f);
		}
		if (win && t >= LAND + PET_RISE && summonSound < 2) {
			summonSound = 2;
			ScathaSlots.sound(SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.8f);
		}
		if (win && t >= LAND + PET_RISE + RISE_MS && summonSound < 3) {
			summonSound = 3;
			ScathaSlots.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1f);
			ScathaSlots.sound(SoundEvents.BEACON_ACTIVATE, 1.5f, 0.6f);
		}
		if (t < THROW || t >= LAND) return;
		if (t - THROW < 60) ScathaSlots.sound(SoundEvents.BAMBOO_HIT, 1.6f, 0.6f); // the throw
		int pocket = (int) Math.floor((ballAngle(t) - wheel(t)) / STEP);
		if (lastPocket != Integer.MIN_VALUE && pocket != lastPocket)
			ScathaSlots.play(SoundEvents.NOTE_BLOCK_HAT, 1.7f + ScathaSlots.RNG.nextFloat() * 0.2f, t < DROP ? 0.25f : 0.4f);
		lastPocket = pocket;
		int bounce = (int) Math.floor(bounceU(t) * 3 + 0.5f);
		if (t >= DROP && bounce != lastBounce) {
			ScathaSlots.sound(SoundEvents.BONE_BLOCK_HIT, 1.4f + 0.15f * bounce, 0.8f - 0.15f * bounce);
			lastBounce = bounce;
		}
	}

	// ---- drawing ----

	/**
	 * The summoning on green: the gold circle in the middle of the wheel opens into a swirling portal
	 * in the pet's rarity colour, with a beam and rising sparks, and the Scatha pet spins up out of it.
	 */
	private static void drawSummon(GuiGraphicsExtractor g, long t, long since, float open, int rim) {
		float pr = (R_CONE + 1) * open, cy = 0;
		int deep = Theme.mix(0x000000, rim, 0.12f), light = Theme.mix(rim, 0xFFFFFF, 0.45f);
		// light beam up from the portal
		int beamW = Math.round(pr * 0.55f);
		g.fillGradient(-beamW, Math.round(cy - 95), beamW, Math.round(cy), 0x00000000 | rim, (int) (110 * open) << 24 | light);
		// the portal: glow, rim, dark inside, swirling arms
		ellipse(g, 0, cy, pr + 5, (pr + 5) * TILT, (int) (90 * open) << 24 | rim, (int) (40 * open) << 24 | rim);
		ellipse(g, 0, cy, pr, pr * TILT, 0xFF000000 | Theme.mix(rim, 0xFFFFFF, 0.3f), 0xFF000000 | Theme.mix(rim, 0x000000, 0.3f));
		ellipse(g, 0, cy, pr - 2.5f, (pr - 2.5f) * TILT, 0xFF000000 | deep, 0xFF000000 | Theme.mix(deep, rim, 0.35f));
		double spin = t / 260.0;
		for (int k = 0; k < 4; k++)
			for (int j = 0; j < 18; j++) {
				double a = spin + k * Math.PI / 2 + j * 0.3;
				float r = (pr - 4) * (1 - j / 19f);
				seg(g, 0, cy, a, r - 1.8f, r + 1.8f, 2f, (int) (200 * (1 - j / 19f) * open) << 24 | light);
			}
		// sparks drifting up
		for (int i = 0; i < 26; i++) {
			float phase = (float) ((t / 1100.0 + i * 0.37) % 1.0);
			float sx = (float) Math.cos(i * 2.4) * pr * 0.75f, sy = cy + (float) Math.sin(i * 2.4) * pr * 0.75f * TILT - phase * 60;
			int alpha = Math.round(230 * (1 - phase) * open);
			g.fill(Math.round(sx), Math.round(sy), Math.round(sx) + 1, Math.round(sy) + 1, alpha << 24 | (i % 3 == 0 ? 0xFFFFFF : light));
		}
		// the pet: spins up out of the portal (hidden below the portal's middle), slows to face you, then hovers
		float rise = Math.max(0f, Math.min(1f, (since - PET_RISE) / (float) RISE_MS));
		if (rise <= 0) return;
		float up = backOut(rise), turn = (1 - smooth(rise)) * 3.5f * (float) Math.PI;
		float bob = rise >= 1 ? (float) Math.sin((since - PET_RISE - RISE_MS) / 280.0) * 2 : 0;
		float petY = cy + 6 - up * 48 + bob, size = 2.8f;
		ellipse(g, 0, petY, 22, 22, 0x48000000 | light, 0x00000000 | light); // glow behind it
		g.enableScissor(-80, Math.round(cy - 200), 80, Math.round(cy));
		// spin by narrowing and widening; never mirrored (a flipped item isn't drawn at all)
		ScathaSlots.item(g, ScathaSlots.scathaHead(), 0, petY, size * Math.max(0.12f, Math.abs((float) Math.cos(turn))), size);
		g.disableScissor();
	}

	private static float smooth(float k) {
		k = Math.max(0f, Math.min(1f, k));
		return k * k * (3 - 2 * k);
	}

	/** Ease-out with a little overshoot: the pet pops up and settles. */
	private static float backOut(float k) {
		float s = k - 1, c1 = 1.4f;
		return 1 + (c1 + 1) * s * s * s + c1 * s * s;
	}

	/** {@code portal} = the pet's rarity colour (RGB), used for the portal on green. */
	static void draw(GuiGraphicsExtractor g, Font font, long t, boolean revealed, int accent, boolean win, int portal) {
		double w = wheel(t);
		// table shadow and the bowl's wooden side (a cylinder: lower ellipse + band)
		ellipse(g, 0, DEPTH + 4, R_OUT + 6, (R_OUT + 6) * TILT, 0x50000000, 0x10000000);
		ellipse(g, 0, DEPTH, R_OUT, R_OUT * TILT, 0xFF000000 | Theme.mix(WOOD, 0x000000, 0.45f), 0xFF000000 | Theme.mix(WOOD, 0x000000, 0.7f));
		g.fillGradient(Math.round(-R_OUT), 0, Math.round(R_OUT), Math.round(DEPTH), 0xFF000000 | Theme.mix(WOOD, 0x000000, 0.3f), 0xFF000000 | Theme.mix(WOOD, 0x000000, 0.6f));
		// top of the rim (wood), then the dark polished ball track
		ellipse(g, 0, 0, R_OUT, R_OUT * TILT, 0xFF000000 | Theme.mix(WOOD, 0x000000, 0.2f), 0xFF000000 | Theme.mix(WOOD, 0xFFFFFF, 0.12f));
		ellipse(g, 0, 0, R_RIM, R_RIM * TILT, 0xFF000000 | GOLD, 0xFF000000 | Theme.mix(GOLD, 0x000000, 0.3f));
		ellipse(g, 0, 0, R_RIM - 1.5f, (R_RIM - 1.5f) * TILT, 0xFF0C0C0E, 0xFF2A2A30);
		// track sheen (lit far side)
		for (int i = 0; i < 24; i++) {
			double a = -Math.PI / 2 + (i - 12) * 0.06;
			seg(g, 0, 0, a, R_POCKET_OUT + 5, R_RIM - 3, 3.2f, (int) (28 * Math.cos((i - 12) / 8.0)) << 24 | 0xFFFFFF);
		}
		// pocket ring: gold under, then wedges, dividers and numbers turning with the wheel
		ellipse(g, 0, 0, R_POCKET_OUT + 2, (R_POCKET_OUT + 2) * TILT, 0xFF000000 | GOLD, 0xFF000000 | Theme.mix(GOLD, 0x000000, 0.35f));
		for (int i = 0; i < N; i++) {
			double a = w + i * STEP;
			int n = ORDER[i], base = n == 0 ? GREEN : RED.contains(n) ? RED_C : BLACK;
			float light = 0.78f + 0.22f * (float) Math.cos(a + Math.PI / 3); // lit from the front-left
			int c = 0xFF000000 | Theme.mix(0x000000, base, light);
			if (i == target && t >= LAND) c = 0xFF000000 | Theme.mix(base, 0xFFFFFF, 0.25f + 0.2f * (float) Math.sin(t / 120.0));
			float midIn = (R_POCKET_IN + R_POCKET_OUT) / 2;
			seg(g, 0, 0, a, R_POCKET_IN, midIn, (float) (STEP / 2 * (R_POCKET_IN + midIn) / 2) + 0.5f, c);
			seg(g, 0, 0, a, midIn - 0.5f, R_POCKET_OUT, (float) (STEP / 2 * (midIn + R_POCKET_OUT) / 2) + 0.6f, c);
		}
		for (int i = 0; i < N; i++) {
			double a = w + (i + 0.5) * STEP;
			seg(g, 0, 0, a, R_POCKET_IN - 2, R_POCKET_OUT + 1, 0.55f, 0xFF000000 | Theme.mix(GOLD, 0xFFFFFF, 0.2f));
		}
		for (int i = 0; i < N; i++) number(g, font, w + i * STEP, String.valueOf(ORDER[i]));
		// raised gold cone: stacked ellipses getting smaller and higher, with gold frets on its base
		ellipse(g, 0, 0, R_POCKET_IN, R_POCKET_IN * TILT, 0xFF000000 | Theme.mix(GOLD, 0x000000, 0.25f), 0xFF000000 | GOLD);
		for (int i = 0; i < N; i++)
			seg(g, 0, 0, w + (i + 0.5) * STEP, R_CONE - 3, R_POCKET_IN, 0.6f, 0xFF000000 | Theme.mix(GOLD, 0xFFFFFF, 0.35f));
		// on green the gold circle itself becomes the portal: the cone flattens and the turret sinks as it opens
		long since = win && t >= LAND ? t - LAND : -1;
		float open = since < 0 ? 0f : smooth(since / (float) OPEN_MS);
		int steps = 7;
		float step = 2.6f * (1 - open);
		for (int k = 0; k < steps; k++) {
			float r = R_CONE * (1 - k / (float) steps * 0.78f), lift = k * step;
			float lit = k / (float) (steps - 1);
			ellipse(g, 0, -lift, r, r * TILT, 0xFF000000 | Theme.mix(GOLD, 0xFFF2C0, 0.15f + 0.5f * lit), 0xFF000000 | Theme.mix(GOLD, 0x5A3A00, 0.45f - 0.3f * lit));
		}
		// cone shine
		float top = -(steps - 1) * step;
		ellipse(g, -6, top - 1.5f, 6, 6 * TILT, 0x60FFFFFF, 0x00FFFFFF);
		// turret: post, four arms that turn with the wheel, knobs, cap
		if (open < 1) {
			float sink = 1 - open, armY = top - 15 * sink, arm = 22 * sink;
			g.fillGradient(-3, Math.round(armY), 3, Math.round(top), 0xFF000000 | Theme.mix(GOLD, 0xFFFFFF, 0.25f), 0xFF000000 | Theme.mix(GOLD, 0x000000, 0.4f));
			g.fill(-3, Math.round(armY), -1, Math.round(top), 0x40FFFFFF);
			for (int k = 0; k < 4; k++) {
				double a = w + k * Math.PI / 2;
				seg(g, 0, armY, a, 0, arm, 1.6f, 0xFF000000 | Theme.mix(GOLD, 0x000000, 0.15f + 0.2f * (float) Math.max(0, -Math.sin(a))));
				int kx = Math.round(arm * (float) Math.cos(a)), ky = Math.round(armY + arm * (float) Math.sin(a) * TILT);
				ScathaSlots.ball(g, kx, ky - 1, 2, GOLD);
			}
			ScathaSlots.ball(g, 0, Math.round(armY) - 2, 4, GOLD);
		}
		if (since >= 0) drawSummon(g, t, since, open, portal);

		// the ball, with its shadow on the wheel
		if (t >= THROW) {
			double a = ballAngle(t);
			float r = ballRadius(t), hop = ballHop(t);
			int bx = Math.round(r * (float) Math.cos(a)), by = Math.round(r * (float) Math.sin(a) * TILT);
			ellipse(g, bx + 1, by + 1.5f, 4, 4 * TILT, 0x70000000, 0x30000000);
			ScathaSlots.ball(g, bx, Math.round(by - 3 - hop), 3, 0xF6F4EE);
		}
		// title above the wheel (gone while the pet comes out: it needs the room)
		String title = "SCATHA ROULETTE";
		if (since < 0) g.text(font, title, -font.width(title) / 2, Math.round(-R_OUT * TILT - 18), 0xFF000000 | (revealed ? Theme.mix(accent, 0xFFFFFF, 0.3f) : 0xFFE38A), true);
	}

	/** A number painted on its pocket, lying in the wheel's plane, reading outward. */
	private static void number(GuiGraphicsExtractor g, Font font, double a, String text) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(1, TILT);
		pose.rotate((float) a);
		pose.translate(R_POCKET_OUT - 6.5f, 0);
		pose.rotate((float) (Math.PI / 2));
		pose.scale(0.55f, 0.55f);
		g.text(font, text, -font.width(text) / 2, -4, 0xFFF4E6C0, false);
		pose.popMatrix();
	}

	/** A rectangle in the wheel's plane: from radius r0 to r1 at angle a, half-width hw (tangential). */
	private static void seg(GuiGraphicsExtractor g, float cx, float cy, double a, float r0, float r1, float hw, int argb) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(1, TILT);
		pose.rotate((float) a);
		pose.scale(0.25f, 0.25f); // quarter units: fill takes ints
		g.fill(Math.round(r0 * 4), Math.round(-hw * 4), Math.round(r1 * 4), Math.round(hw * 4), argb);
		pose.popMatrix();
	}

	/** Filled ellipse from half-pixel rows, shaded top colour to bottom colour. */
	static void ellipse(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, int top, int bottom) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(0.5f, 0.5f);
		int rows = Math.max(1, Math.round(ry * 2));
		for (int row = -rows; row < rows; row++) {
			float v = (row + 0.5f) / rows;
			float half = rx * (float) Math.sqrt(Math.max(0, 1 - v * v));
			int c = lerpArgb(top, bottom, (v + 1) / 2);
			g.fill(Math.round((cx - half) * 2), Math.round(cy * 2) + row, Math.round((cx + half) * 2), Math.round(cy * 2) + row + 1, c);
		}
		pose.popMatrix();
	}

	private static int lerpArgb(int a, int b, float t) {
		int alpha = Math.round((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t);
		return alpha << 24 | Theme.mix(a & 0xFFFFFF, b & 0xFFFFFF, t);
	}
}
