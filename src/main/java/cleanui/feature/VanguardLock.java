package cleanui.feature;

import cleanui.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * Vanguard corpse loot as a lock being picked: a cut-away brass pin tumbler (springs, driver and
 * key pins, shear line, plug and keyway, tension wrench, hook pick). The pick wedges under each
 * pin, wiggles, pushes it up and it clicks to the shear line; every pin set climbs a rarity.
 * 6/6 = Frostbitten Dye, 5/6 = Shattered Locket, 4/6 = Caged Wisp or Skeleton Key, fewer = no rare
 * drop: the pick jams, tugs, can't come out and the lock drops away. From 4/6 up the pick still
 * jams on the next pin (not on 6/6) and then the part of the lock that holds that drop opens:
 * the whole housing (dye), the front plate (locket), the end cap (wisp) or a drawer under the
 * keyway (key), and the drop pops out with its name and value.
 * Timing and chat are run by {@link VanguardSnake}; this draws and sounds it. Units: the lock is
 * 140 x 90 units, drawn at {@code u} px per unit.
 */
final class VanguardLock {
	static final int PICK_IN = 400, PICK_START = 800, PIN_MS = 650;
	private static final int MOVE = 180, WEDGE_END = 430, PUSH_END = 560, JAM_PUSH_END = 700;
	private static final int TURN_MS = 400, OPEN_MS = 700, POP_DELAY = 500, HOLD_MS = 5200;
	private static final int TUG_END = 1500, AWAY_END = 2300;
	private static final int PINS = 6;
	private static final int[] KEY_LEN = {12, 9, 14, 10, 13, 11};
	private static final float SHEAR = 50, KEYWAY = 72, DRIVER = 14, PIN_W = 10, DEPTH = 5;
	private static final int[] TIER = {0x9A9A9A, 0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF};
	private static final String[] TIER_NAME = {"", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
	private static final int BRASS = 0xC8A040, BRASS_HI = 0xF2D886, BRASS_LO = 0x7A5A18, STEEL = 0xB8C0C8, HOLE = 0x0E0C0A;

	private static int noDropPins = 2; // how far a run with no rare drop gets (picked when it starts)
	private static int sounds;         // what's been played (bit flags per moment)
	// drawing state for the current frame
	private static GuiGraphicsExtractor g;
	private static float lx, ly, u;
	private static int alpha = 255;

	private VanguardLock() {}

	static void reset() {
		noDropPins = 1 + ScathaSlots.RNG.nextInt(3);
		sounds = 0;
	}

	/** Preview: how far a run with no drop gets. */
	static void forcePins(int pins) {
		if (pins > 0) noDropPins = pins;
	}

	// ---- outcome and timing ----

	/** Pins picked: 6 dye, 5 locket, 4 wisp/key, else 1-3. */
	static int picks(List<VanguardSnake.Drop> got) {
		VanguardSnake.Drop best = best(got);
		if (best == null) return noDropPins;
		return switch (best.name()) {
			case "Frostbitten Dye" -> 6;
			case "Shattered Locket" -> 5;
			default -> 4;
		};
	}

	/** The drop the lock is about: the rarest one if several dropped. */
	static VanguardSnake.Drop best(List<VanguardSnake.Drop> got) {
		VanguardSnake.Drop best = null;
		for (VanguardSnake.Drop d : got) if (best == null || VanguardSnake.DROPS.indexOf(d) < VanguardSnake.DROPS.indexOf(best)) best = d;
		return best;
	}

	private static long pinStart(int i) {
		return PICK_START + (long) i * PIN_MS;
	}

	/** When the drop's part of the lock starts opening (-1: it doesn't, the pick fails). */
	static long open(List<VanguardSnake.Drop> got) {
		int n = picks(got);
		if (n < 4 || best(got) == null) return -1;
		return n == PINS ? pinStart(PINS) + TURN_MS : pinStart(n) + 1000;
	}

	static long popup(List<VanguardSnake.Drop> got) {
		long o = open(got);
		return o < 0 ? -1 : o + POP_DELAY;
	}

	static long end(List<VanguardSnake.Drop> got) {
		long p = popup(got);
		return p >= 0 ? p + HOLD_MS : pinStart(picks(got)) + AWAY_END + 200;
	}

	// ---- sounds ----

	private static boolean once(int bit) {
		if ((sounds & bit) != 0) return false;
		sounds |= bit;
		return true;
	}

	static void tick(long t, List<VanguardSnake.Drop> got) {
		int n = picks(got);
		if (t >= 0 && once(1)) ScathaSlots.sound(SoundEvents.VAULT_ACTIVATE, 1f, 0.9f);
		if (t >= PICK_IN && once(2)) ScathaSlots.sound(SoundEvents.TRIPWIRE_CLICK_ON, 1.4f, 0.8f);
		for (int i = 0; i < Math.min(PINS, n + 1); i++) {
			long b = pinStart(i);
			if (t >= b + MOVE && once(4 << (i * 3))) ScathaSlots.play(SoundEvents.NOTE_BLOCK_HAT, 1.6f, 0.4f); // wedge
			if (i < n && t >= b + PUSH_END && once(8 << (i * 3))) { // click: the pin sets, the rarity climbs
				ScathaSlots.sound(SoundEvents.LEVER_CLICK, 1.3f + 0.08f * i, 0.9f);
				ScathaSlots.play(SoundEvents.NOTE_BLOCK_PLING, (float) Math.pow(2, (6 + i * 3 - 12) / 12.0), 0.7f);
			}
			if (i == n && t >= b + WEDGE_END + 120 && once(16 << (i * 3))) ScathaSlots.sound(SoundEvents.CHEST_LOCKED, 1f, 1f); // jammed
		}
		long o = open(got), p = popup(got);
		if (n == PINS && t >= pinStart(PINS) && once(1 << 25)) ScathaSlots.sound(SoundEvents.IRON_TRAPDOOR_OPEN, 0.7f, 1f); // the plug turns
		if (o >= 0 && t >= o && once(1 << 26)) ScathaSlots.sound(SoundEvents.VAULT_OPEN_SHUTTER, 1f, 1f);
		if (p >= 0 && t >= p && once(1 << 27)) {
			ScathaSlots.sound(SoundEvents.VAULT_EJECT_ITEM, 1f, 1f);
			if (cleanui.data.Config.get().vanguardConfetti) ScathaSlots.confettiOnly(2);
			VanguardSnake.dropSound(best(got));
		}
		if (o < 0) { // failed: tugs, then it drops away
			long b = pinStart(n);
			for (int k = 0; k < 3; k++)
				if (t >= b + 780 + k * 250 && once(1 << (28 + k))) ScathaSlots.sound(SoundEvents.CHAIN_HIT, 0.9f + 0.1f * k, 0.9f);
			if (t >= b + TUG_END && once(1 << 31)) ScathaSlots.sound(SoundEvents.VAULT_REJECT_REWARDED_PLAYER, 0.9f, 1f);
		}
	}

	// ---- drawing ----

	private static float smooth(float k) {
		k = Math.max(0f, Math.min(1f, k));
		return k * k * (3 - 2 * k);
	}

	private static int col(int rgb) {
		return alpha << 24 | rgb;
	}

	private static void rect(float x0, float y0, float x1, float y1, int rgb) {
		g.fill(Math.round(lx + x0 * u), Math.round(ly + y0 * u), Math.round(lx + x1 * u), Math.round(ly + y1 * u), col(rgb));
	}

	/** A brass block: lit top edge, dark bottom edge, soft vertical shading, faint brushed lines. */
	private static void brass(float x0, float y0, float x1, float y1, int base) {
		g.fillGradient(Math.round(lx + x0 * u), Math.round(ly + y0 * u), Math.round(lx + x1 * u), Math.round(ly + y1 * u),
				col(Theme.mix(base, BRASS_HI, 0.35f)), col(Theme.mix(base, BRASS_LO, 0.45f)));
		for (float y = y0 + 3; y < y1 - 2; y += 3.5f) rect(x0 + 1, y, x1 - 1, y + 0.3f, Theme.mix(base, BRASS_HI, 0.25f)); // brushed
		rect(x0, y0, x1, y0 + 0.8f, Theme.mix(base, 0xFFFFFF, 0.45f));
		rect(x0, y0, x0 + 0.6f, y1, Theme.mix(base, 0xFFFFFF, 0.25f));
		rect(x0, y1 - 0.8f, x1, y1, BRASS_LO);
	}

	/** The block's depth, drawn before its front: a lit top face and a shaded right side, going back up-right. */
	private static void depth(float x0, float y0, float x1, float y1, int base) {
		int d = Math.round(DEPTH * u), X0 = Math.round(lx + x0 * u), Y0 = Math.round(ly + y0 * u), X1 = Math.round(lx + x1 * u), Y1 = Math.round(ly + y1 * u);
		for (int i = 1; i <= d; i++) {
			float k = i / (float) d;
			g.fill(X0 + i, Y0 - i, X1 + i, Y0 - i + 1, col(Theme.mix(Theme.mix(base, BRASS_HI, 0.55f), base, k * 0.5f)));
			g.fillGradient(X1 + i - 1, Y0 - i + 1, X1 + i, Y1 - i + 1, col(Theme.mix(base, BRASS_LO, 0.45f + 0.2f * k)), col(Theme.mix(base, 0x1A1006, 0.7f)));
		}
	}

	/**
	 * A round part seen from the side, lit from the left: vertical strips (a standing pin) or
	 * horizontal bands (the plug), bright a third of the way across, dark at the edges.
	 */
	private static void cylinder(float x0, float y0, float x1, float y1, int base, boolean standing) {
		int strips = 7;
		for (int s = 0; s < strips; s++) {
			float a = s / (float) strips, b = (s + 1) / (float) strips, mid = (a + b) / 2;
			float light = (float) Math.sin(Math.PI * Math.min(1, mid * 1.4f)) * 0.9f - 0.25f; // peak a third across
			int c = light > 0 ? Theme.mix(base, BRASS_HI, light * 0.7f) : Theme.mix(base, BRASS_LO, -light * 1.6f);
			if (standing) rect(x0 + (x1 - x0) * a, y0, x0 + (x1 - x0) * b, y1, c);
			else rect(x0, y0 + (y1 - y0) * a, x1, y0 + (y1 - y0) * b, c);
		}
	}

	/** A slotted screw head (units, radius r). */
	private static void screw(float x, float y, float r, double slot) {
		ScathaRoulette.ellipse(g, lx + x * u, ly + y * u, (r + 0.6f) * u, (r + 0.6f) * u, col(BRASS_LO), col(0x2A1E08));
		ScathaRoulette.ellipse(g, lx + x * u, ly + y * u, r * u, r * u, col(Theme.mix(BRASS, BRASS_HI, 0.5f)), col(BRASS_LO));
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(lx + x * u, ly + y * u);
		pose.rotate((float) slot);
		g.fill(Math.round(-r * u), -1, Math.round(r * u), 1, col(0x2A1E08));
		pose.popMatrix();
	}

	static void render(GuiGraphicsExtractor graphics, long t, long end, List<VanguardSnake.Drop> got) {
		g = graphics;
		Font font = Minecraft.getInstance().font;
		int sw = g.guiWidth(), sh = g.guiHeight(), n = picks(got);
		u = Math.min(sw / 200f, sh * 0.36f / 90f);
		float fadeIn = Math.min(1f, t / 300f), fadeOut = Math.min(1f, (end - t) / 400f);
		long o = open(got), p = popup(got), jamAt = pinStart(n);
		boolean failing = o < 0;
		// failed: after the tugs the whole lock drops away
		float away = failing ? smooth((t - jamAt - TUG_END) / (float) (AWAY_END - TUG_END)) : 0f;
		alpha = Math.round(255 * fadeIn * fadeOut * (1 - away));
		if (alpha <= 3) return;
		float shake = 0;
		if (failing && t > jamAt + WEDGE_END && t < jamAt + TUG_END) shake = (float) Math.sin(t * 0.7) * 0.8f;
		lx = sw / 2f - 70 * u + shake * u;
		ly = sh / 2f - 45 * u + 25 + away * 70 * u; // low enough for the banner and the drop above it

		g.fill(0, 0, sw, sh, Math.round(120 * fadeIn * fadeOut) << 24); // dim behind
		float openK = o >= 0 ? smooth((t - o) / (float) OPEN_MS) : 0f;
		VanguardSnake.Drop drop = best(got);
		String part = o >= 0 && drop != null ? drop.name() : "";

		// a warm light behind, and the lock's shadow on the "floor"
		for (int r = 6; r >= 1; r--) // soft: stacked faint ellipses, brightest in the middle
			ScathaRoulette.ellipse(g, lx + 70 * u, ly + 45 * u, 20 * r * u, 12 * r * u, Math.round(9 * alpha / 255f) << 24 | 0xFFD890, Math.round(9 * alpha / 255f) << 24 | 0xFFD890);
		ScathaRoulette.ellipse(g, lx + 74 * u, ly + 96 * u, 82 * u, 7 * u, Math.round(150 * alpha / 255f) << 24, Math.round(40 * alpha / 255f) << 24);

		// housing above the shear line, with the pin chambers (dye: it lifts away)
		float lift = "Frostbitten Dye".equals(part) ? openK * 46 : 0;
		ly -= lift * u;
		int solid = alpha;
		if (lift > 0) alpha = Math.round(solid * (1 - 0.85f * openK)); // fades as it lifts off
		depth(0, 0, 140, SHEAR, BRASS);
		brass(0, 0, 140, SHEAR, BRASS);
		for (int i = 0; i < PINS; i++) {
			float x = pinX(i);
			rect(x - 1, 3, x + PIN_W + 1, SHEAR, HOLE);
			rect(x - 1, 3, x + 0.6f, SHEAR, 0x000000);                          // the chamber's shadowed wall
			rect(x + PIN_W, 3, x + PIN_W + 1, SHEAR, 0x2A2010);                // and its lit wall
			brass(x - 1.5f, 0.6f, x + PIN_W + 1.5f, 3, Theme.mix(BRASS, BRASS_HI, 0.2f)); // cap over the chamber
			if (i < PINS - 1) { // machined groove between chambers
				rect(x + PIN_W + 3.6f, 4, x + PIN_W + 4.2f, SHEAR - 2, BRASS_LO);
				rect(x + PIN_W + 4.2f, 4, x + PIN_W + 4.6f, SHEAR - 2, BRASS_HI);
			}
		}
		screw(7, 8, 2.4f, 0.6);
		screw(133, 8, 2.4f, 2.1);
		screw(7, 42, 2.4f, 1.3);
		screw(133, 42, 2.4f, 0.2);
		ly += lift * u;
		// plug: a cylinder lying in the shell, pin chambers through it, the keyway under (dye: glows; turn: a highlight sweeps down it)
		cylinder(14, SHEAR, 126, 80, Theme.mix(BRASS, 0x000000, 0.04f), false);
		rect(14, SHEAR, 126, SHEAR + 0.5f, 0x2A1E08);
		if (n == PINS && t >= pinStart(PINS)) {
			float turn = smooth((t - pinStart(PINS)) / (float) TURN_MS);
			rect(14, SHEAR + 30 * turn - 3, 126, SHEAR + 30 * turn, 0xFFF0B0);
		}
		for (int i = 0; i < PINS; i++) { // the pin chambers through the plug
			rect(pinX(i) - 1, SHEAR, pinX(i) + PIN_W + 1, KEYWAY, HOLE);
			rect(pinX(i) - 1, SHEAR, pinX(i) + 0.6f, KEYWAY, 0x000000);
		}
		// keyway: dark slot with a warded profile and light catching its lower lip
		rect(14, KEYWAY, 126, 80, HOLE);
		g.fillGradient(Math.round(lx + 14 * u), Math.round(ly + KEYWAY * u), Math.round(lx + 126 * u), Math.round(ly + (KEYWAY + 3) * u), col(0x000000), col(HOLE));
		for (float x = 20; x < 124; x += 14) rect(x, KEYWAY + 2.5f, x + 6, KEYWAY + 3.4f, 0x2A2418); // wards
		rect(14, 79.4f, 126, 80, Theme.mix(BRASS, BRASS_HI, 0.3f));
		// pins and springs, in their chambers
		for (int i = 0; i < PINS; i++) drawPin(i, t, n, failing, lift);
		alpha = solid;
		// shear line
		rect(14, SHEAR - 0.4f, 126, SHEAR + 0.4f, 0x2A1E08);
		// bottom lip (key: it drops away as a drawer, opening a slot under the keyway)
		float drawer = "Skeleton Key".equals(part) ? openK * 16 : 0;
		if (drawer > 0) rect(14, 80, 126, 80 + drawer, 0x1A140C);
		depth(0, 80 + drawer, 140, 90 + drawer, BRASS);
		brass(0, 80 + drawer, 140, 90 + drawer, BRASS);
		engrave(Minecraft.getInstance().font, "VANGUARD", 70, 82 + drawer);
		if ("Frostbitten Dye".equals(part)) // the plug glows icy as the housing lifts
			g.fill(Math.round(lx + 14 * u), Math.round(ly + SHEAR * u), Math.round(lx + 126 * u), Math.round(ly + 80 * u), Math.round(140 * openK * alpha / 255f) << 24 | 0xBFEFFF);
		// front plate on the left, with the keyway hole (locket: it swings open like a door)
		if ("Shattered Locket".equals(part)) {
			rect(0, SHEAR, 14, 80, 0x1A140C);
			float w = 14 * (float) Math.cos(openK * Math.PI / 2 * 0.92);
			brass(-w * 0.15f, SHEAR, w, 80, Theme.mix(BRASS, 0x000000, 0.2f * openK));
		} else {
			brass(0, SHEAR, 14, 80, BRASS);
			rect(3.4f, KEYWAY - 0.6f, 14, 80.6f, BRASS_LO); // bevel round the keyway hole
			rect(4, KEYWAY, 14, 80, HOLE);
			rivet(7, 56);
			rivet(7, 66);
		}
		// end cap on the right, knurled (wisp: it pops off, spins away and falls)
		if ("Caged Wisp".equals(part)) {
			rect(126, SHEAR, 140, 80, 0x1A140C);
			float cx = 18 * openK, cy = -16 * openK + 95 * openK * openK; // pops off and drops
			endCap(126 + cx, SHEAR + cy, Theme.mix(BRASS, 0x000000, 0.15f));
		} else endCap(126, SHEAR, BRASS);
		// tension wrench (turns with the plug on 6/6) and the hook pick
		float wrenchTurn = n == PINS && t >= pinStart(PINS) ? smooth((t - pinStart(PINS)) / (float) TURN_MS) * 5 : 0;
		rect(6, 76, 18, 78, 0x3A3A40);
		rect(6, 76, 18, 76.6f, 0xA8B0B8);
		rect(6 + wrenchTurn, 78, 9 + wrenchTurn, 98, 0x3A3A40);
		rect(6 + wrenchTurn, 78, 7 + wrenchTurn, 98, 0x8A929A);
		drawPick(t, n, failing);

		// glow from the opened part
		if (o >= 0 && openK > 0) {
			float[] src = source(part);
			ScathaRoulette.ellipse(g, lx + src[0] * u, ly + src[1] * u, 18 * u * openK, 18 * u * openK,
					Math.round(110 * openK) << 24 | 0xFFF2B0, 0);
		}
		drawMeter(font, t, n, failing);
		if (failing && t > jamAt + 700) caption(font, away > 0 ? "the lock won't give..." : "STUCK!", sw / 2f, ly + 100 * u, 1.6f, away > 0 ? 0xAAAAAA : 0xFF5555);
		if (p >= 0 && t >= p) drawPopup(font, t - p, drop, part, sw, sh, n);
	}

	/** The right end cap at (x, y), 14 x 30 units, with depth and diagonal knurling. */
	private static void endCap(float x, float y, int base) {
		depth(x, y, x + 14, y + 30, base);
		brass(x, y, x + 14, y + 30, base);
		for (float k = -30; k < 14; k += 2.4f) // knurl: short diagonal grooves
			for (float s = 0; s < 30; s += 1) {
				float px = x + k + s * 0.5f;
				if (px > x + 1.5f && px < x + 12.5f && (int) (s / 2) % 2 == 0) rect(px, y + 1.5f + s * 0.9f, px + 0.6f, y + 2.1f + s * 0.9f, BRASS_LO);
			}
	}

	private static void rivet(float x, float y) {
		ScathaRoulette.ellipse(g, lx + x * u, ly + y * u, 1.6f * u, 1.6f * u, col(BRASS_HI), col(BRASS_LO));
	}

	/** Stamped text: dark letters with a light edge under them (units). */
	private static void engrave(Font font, String text, float x, float y) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(lx + x * u, ly + y * u);
		float s = u * 0.75f;
		pose.scale(s, s);
		int w = font.width(text);
		g.text(font, text, -w / 2, 1, col(Theme.mix(BRASS, BRASS_HI, 0.6f)), false);
		g.text(font, text, -w / 2, 0, col(0x4A3410), false);
		pose.popMatrix();
	}

	private static float pinX(int i) {
		return 22 + i * 18;
	}

	/** Where the drop comes out of the lock (units). */
	private static float[] source(String part) {
		return switch (part) {
			case "Shattered Locket" -> new float[]{7, 65};
			case "Caged Wisp" -> new float[]{133, 65};
			case "Skeleton Key" -> new float[]{70, 88};
			default -> new float[]{70, 62};
		};
	}

	/**
	 * One pin stack. Before the pick reaches it: at rest. Wedged: nudged up a little, wiggling.
	 * Pushed: key and driver pin rise together until the driver's bottom meets the shear line;
	 * click: the driver stays up, the key pin drops back. The jam pin only gets halfway and shivers.
	 */
	private static void drawPin(int i, long t, int n, boolean failing, float housingLift) {
		float x = pinX(i), k = KEY_LEN[i], full = KEYWAY - k - SHEAR;
		long b = pinStart(i), dt = t - b;
		float keyLift = 0, driverLift = 0;
		boolean set = false;
		if (dt >= MOVE && (i < n || i == n && i < PINS)) {
			float jiggle = (float) Math.sin(t * 0.09) * 0.7f;
			if (dt < WEDGE_END) keyLift = 2 + jiggle;
			else if (i < n) {
				if (dt < PUSH_END) keyLift = 2 + (full - 2) * smooth((dt - WEDGE_END) / (float) (PUSH_END - WEDGE_END));
				else {
					set = true;
					keyLift = full * (1 - smooth((dt - PUSH_END) / 90f));
				}
			} else keyLift = full * 0.45f * smooth((dt - WEDGE_END) / (float) (JAM_PUSH_END - WEDGE_END)) + (dt > JAM_PUSH_END ? jiggle * 0.6f : 0);
			driverLift = set ? full : keyLift;
		}
		// in the housing (lifts with it on 6/6): spring, then driver pin
		float driverBottom = KEYWAY - k - driverLift, driverTop = driverBottom - DRIVER;
		ly -= housingLift * u;
		// spring: slanted steel coils, each lit on top and dark underneath, squeezing as the pin rises
		float coilTop = 3.4f, coilBottom = Math.min(driverTop, SHEAR);
		for (int c = 0; c < 9; c++) {
			float y = coilTop + (coilBottom - coilTop) * c / 9f, h = Math.max(0.7f, (coilBottom - coilTop) / 9f * 0.55f);
			rect(x + 0.4f, y, x + PIN_W / 2, y + h, Theme.mix(STEEL, 0x000000, 0.25f));
			rect(x + PIN_W / 2, y + 0.4f, x + PIN_W - 0.4f, y + h + 0.4f, STEEL);
			rect(x + 1.2f, y, x + PIN_W - 1.2f, y + 0.35f, 0xF0F4F8);
		}
		boolean flash = set && t - b - PUSH_END < 200;
		int driverColor = flash ? 0xFFE89A : BRASS;
		if (driverTop < SHEAR) cylinder(x, driverTop, x + PIN_W, Math.min(driverBottom, SHEAR), driverColor, true);
		if (driverTop < SHEAR) rect(x, driverTop, x + PIN_W, driverTop + 0.6f, BRASS_HI);
		ly += housingLift * u;
		if (driverBottom > SHEAR) cylinder(x, Math.max(driverTop, SHEAR), x + PIN_W, driverBottom, driverColor, true); // below the line: in the plug
		// key pin in the plug: a slightly redder brass, chamfered top, rounded tip down
		float kb = KEYWAY - keyLift, kt = kb - k;
		int keyColor = Theme.mix(BRASS, 0xD08850, 0.3f);
		cylinder(x + 0.5f, kt + 0.6f, x + PIN_W - 0.5f, kb - 2, keyColor, true);
		cylinder(x + 1.2f, kt, x + PIN_W - 1.2f, kt + 0.6f, Theme.mix(keyColor, BRASS_HI, 0.4f), true);
		cylinder(x + 1.6f, kb - 2, x + PIN_W - 1.6f, kb - 0.8f, keyColor, true);
		cylinder(x + 3, kb - 0.8f, x + PIN_W - 3, kb, Theme.mix(keyColor, BRASS_LO, 0.3f), true);
		if (flash) rect(x - 1, SHEAR - 0.8f, x + PIN_W + 1, SHEAR + 0.8f, 0x8CFF8C); // set: the shear line lights green
	}

	/** Pick tip position along the keyway (units) and how far it's lifted, from the timeline. */
	private static void drawPick(long t, int n, boolean failing) {
		float tip;
		if (t < PICK_IN) tip = -80;
		else if (t < PICK_START) tip = -80 + (pinX(0) + PIN_W / 2 + 80) * smooth((t - PICK_IN) / (float) (PICK_START - PICK_IN));
		else {
			int i = (int) Math.min(n, (t - PICK_START) / PIN_MS);
			if (i >= PINS) i = PINS - 1;
			long dt = t - pinStart(i);
			float from = pinX(Math.max(0, i - 1)) + PIN_W / 2, to = pinX(i) + PIN_W / 2;
			tip = i == 0 ? to : from + (to - from) * smooth(dt / (float) MOVE);
		}
		// lift: follows the key pin it's working on
		int i = (int) Math.max(0, Math.min(Math.min(n, PINS - 1), (t - PICK_START) / PIN_MS));
		long dt = t - pinStart(i);
		float lift = 0;
		if (t >= PICK_START && dt >= MOVE) {
			float full = KEYWAY - KEY_LEN[i] - SHEAR;
			if (dt < WEDGE_END) lift = 2 + (float) Math.sin(t * 0.09) * 0.7f;
			else if (i < n && dt < PUSH_END) lift = 2 + (full - 2) * smooth((dt - WEDGE_END) / (float) (PUSH_END - WEDGE_END));
			else if (i == n) lift = full * 0.45f;
		}
		// failing: stuck, it tugs back a few times but can't come out
		if (failing && t > pinStart(n) + JAM_PUSH_END) {
			float tug = (t - pinStart(n) - JAM_PUSH_END) / 250f;
			tip -= 5 * (float) Math.max(0, Math.sin(tug * Math.PI)) * (tug < 3.2f ? 1 : 0);
		}
		float y = KEYWAY + 5 - lift * 0.35f;
		rect(-90, y, tip - 3, y + 1.6f, 0x8A929A);       // shaft: polished steel, bright top edge, dark under
		rect(-90, y, tip - 3, y + 0.5f, 0xEEF2F6);
		rect(-90, y + 1.2f, tip - 3, y + 1.6f, 0x50585E);
		rect(tip - 4, y - 4 - lift * 0.65f, tip, y + 1.6f, 0x8A929A); // hook, pressing up on the key pin
		rect(tip - 4, y - 4 - lift * 0.65f, tip - 2.6f, y + 1.6f, 0xEEF2F6);
		rect(tip - 4, y - 4.4f - lift * 0.65f, tip, y - 4 - lift * 0.65f, 0xFFFFFF);
		// handle (out of the lock): dark grip with a red wrap, a ring where it meets the shaft
		rect(-124, y - 2, -90, y + 3.6f, 0x2A1418);
		for (float wx = -122; wx < -92; wx += 3) rect(wx, y - 2, wx + 1.4f, y + 3.6f, 0x7A2A3A);
		rect(-124, y - 2, -90, y - 1.4f, 0xB06070);
		rect(-91.5f, y - 2.6f, -89.5f, y + 4.2f, 0xC8A040);
	}

	/** Six segments up the right side, lit one per pin set, in the rarity it's reached. */
	private static void drawMeter(Font font, long t, int n, boolean failing) {
		int setCount = 0;
		for (int i = 0; i < n; i++) if (t >= pinStart(i) + PUSH_END) setCount++;
		float mx = 150, my0 = 84;
		for (int s = 0; s < PINS; s++) {
			float y1 = my0 - s * 13, y0 = y1 - 11;
			boolean lit = s < setCount;
			rect(mx, y0, mx + 10, y1, lit ? TIER[s + 1] : 0x1A1A1E);
			rect(mx, y0, mx + 10, y0 + 0.8f, lit ? 0xFFFFFF : 0x2A2A30);
		}
		String count = setCount + "/6", tier = TIER_NAME[setCount];
		int c = TIER[setCount];
		caption(font, count, lx + 155 * u, ly - 10 * u, 1.6f, setCount == 0 ? 0x9A9A9A : c);
		if (setCount > 0) caption(font, tier, lx + 155 * u, ly - 18 * u, 1f, c);
	}

	/** The drop: out of its part of the lock, up above it, big and spinning in, with name and value. */
	private static void drawPopup(Font font, long since, VanguardSnake.Drop drop, String part, int sw, int sh, int n) {
		float k = smooth(since / 650f), s = Math.min(1f, since / 650f) - 1, back = 1 + 2.4f * s * s * s + 1.4f * s * s;
		float[] src = source(part);
		float sx = lx + src[0] * u, sy = ly + src[1] * u, tx = sw / 2f, ty = ly - 32 * u;
		float x = sx + (tx - sx) * k, y = sy + (ty - sy) * k - (k >= 1 ? 4 * (float) Math.sin(since / 300.0) : 0);
		// beam + rays
		int beam = Math.round((120 + 40 * (float) Math.sin(since / 200.0)) * k);
		g.fillGradient(Math.round(x - 22 * k), Math.round(y - 140), Math.round(x + 22 * k), Math.round(y + 10), 0, beam << 24 | 0xFFE6A0);
		Matrix3x2fStack pose = g.pose();
		for (int r = 0; r < 12; r++) {
			pose.pushMatrix();
			pose.translate(x, y);
			pose.rotate((float) (r * Math.PI / 6 + since / 900.0));
			g.fill(0, -1, Math.round(40 * back), 1, Math.round(100 * k) << 24 | 0xFFD060);
			pose.popMatrix();
		}
		float size = 3.4f * Math.max(0.2f, back);
		float spin = (float) Math.cos(Math.max(0, 1 - k) * 6);
		ScathaSlots.item(g, VanguardSnake.icon(drop), x, y, size * Math.max(0.15f, Math.abs(spin)), size);
		caption(font, n + "/6 PICKED!", sw / 2f, ly - 66 * u, 2.4f + Math.max(0f, 1 - since / 200f) * 1.6f, TIER[n]);
		if (k >= 1) {
			caption(font, drop.name(), x, y + 30, 1.6f, 0xFFAA00);
			String value = VanguardSnake.value(drop);
			if (value != null) caption(font, value, x, y + 46, 1.2f, 0xFFE38A);
		}
	}

	private static void caption(Font font, String text, float x, float y, float size, int rgb) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size, size);
		g.text(font, text, -font.width(text) / 2, 0, Math.max(4, alpha) << 24 | rgb, true);
		pose.popMatrix();
	}
}
