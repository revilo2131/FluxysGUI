package cleanui.feature;

import cleanui.feature.NucleusFinale.Scene;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

import static cleanui.feature.NucleusFinale.*;

/** The flat nucleus finales: the reactor and the constellation. */
final class FinaleScenes {
	private static final float TAU = (float) (Math.PI * 2);

	private FinaleScenes() {}

	// ================= REACTOR =================

	private static final float[] ROD_X = {-50, -25, 0, 25, 50};

	/**
	 * A steel reactor: a glass core of plasma, five fuel rods below it piped into the core, a power
	 * bar, hazard stripes and warning lights. Each crystal drops into its rod, fills it, and the
	 * power climbs 20%. Finale: CORE CRITICAL, the alarm, power past 100% to 999%, the core goes
	 * white and cracks, implodes to a point, and detonates.
	 */
	static void reactor(Scene s) {
		GuiGraphicsExtractor g = s.g();
		float k = s.u() * 0.82f, cx = s.cx(), cy = s.cy() - 4 * s.u();
		boolean fin = s.finale();
		long ft = s.ft(), after = s.after();
		float[] level = new float[5];
		float power = 0;
		for (int i = 0; i < 5; i++) {
			float f = s.fill()[i];
			level[i] = f >= 1 ? Math.min(1f, (f - 1) * 2.2f) : 0f;
			power += level[i] / 5;
		}
		float crit = fin ? s.span(LAND + 100, 2800) : 0f;
		float implode = fin ? s.span(2800, CLIMAX) : 0f;
		float a = s.a() * (after > 0 ? Math.max(0f, 1 - after / 350f) : 1f);
		if (after > 0) debris(s, cx, cy, k, after);
		if (a <= 0.01f) return;
		boolean flash = fin && after < 0 && (ft / 250) % 2 == 0 && crit > 0;
		// red alarm wash at the screen edges
		if (crit > 0) {
			int sw = g.guiWidth(), sh = g.guiHeight();
			float p = (flash ? 0.55f : 0.3f) * Math.min(1f, crit * 3) * a;
			g.fillGradient(0, 0, sw, sh / 4, argb(0xFF2010, p), argb(0xFF2010, 0));
			g.fillGradient(0, sh * 3 / 4, sw, sh, argb(0xFF2010, 0), argb(0xFF2010, p));
		}
		// the frame: plate, bevel, bolts, hazard stripes top and bottom
		rect(g, cx, cy, k, -76, -66, 76, 63, argb(0x15171C, a));
		g.fillGradient(px(cx, k, -74), py(cy, k, -64), px(cx, k, 74), py(cy, k, 61), argb(0x3C424C, a), argb(0x23272E, a));
		rect(g, cx, cy, k, -74, -64, 74, -63, argb(0x5E6672, a));
		hazard(g, cx, cy, k, -58, a, flash);
		hazard(g, cx, cy, k, 53, a, flash);
		for (int sx = -1; sx <= 1; sx += 2)
			for (int sy = -1; sy <= 1; sy += 2) bolt(g, cx + sx * 69 * k, cy + (sy < 0 ? -50 : 47) * k, 2.4f * k, a);
		// warning lights in the top corners
		for (int sx = -1; sx <= 1; sx += 2) {
			float lx = cx + sx * 66 * k, ly = cy - 66 * k;
			boolean on = crit > 0 && (ft / 250 + (sx > 0 ? 1 : 0)) % 2 == 0;
			ScathaRoulette.ellipse(g, lx, ly, 6 * k, 4 * k, argb(on ? 0xFF4030 : 0x5A1810, a), argb(on ? 0xB01808 : 0x2A0A08, a));
			if (crit > 0) {
				double an = ft / 180.0 * sx;
				float len = 46 * k;
				line(g, lx, ly, lx + (float) Math.cos(an) * len, ly + (float) Math.sin(an) * len * 0.6f, 7 * k, argb(0xFF3020, 0.25f * a));
				glow(g, lx, ly, 14 * k, 0xFF3020, (on ? 0.7f : 0.2f) * a);
			}
		}
		// pipes from the rods to the core, with energy pulses running up the full ones
		float coreX = cx, coreY = cy - 20 * k;
		for (int i = 0; i < 5; i++) {
			float rx = cx + ROD_X[i] * k, ry = cy + 22 * k;
			double an = Math.atan2(ry - coreY, rx - coreX);
			float ex = coreX + (float) Math.cos(an) * 33 * k, ey = coreY + (float) Math.sin(an) * 33 * k;
			line(g, rx, ry, ex, ey, 6 * k, argb(0x5A616C, a));
			line(g, rx, ry, ex, ey, 2.6f * k, argb(level[i] > 0 ? mix(0x101418, COLORS[i], 0.6f * level[i]) : 0x101418, a));
			if (level[i] > 0)
				for (int d = 0; d < 3; d++) {
					float t = ((s.now() / (fin ? 260f : 700f)) + d / 3f) % 1f;
					glow(g, rx + (ex - rx) * t, ry + (ey - ry) * t, 3.5f * k, COLORS[i], level[i] * a);
				}
		}
		core(s, coreX, coreY, k, power, crit, implode, a);
		for (int i = 0; i < 5; i++) rod(s, i, cx + ROD_X[i] * k, cy + 22 * k, k, level[i], a);
		// the power bar
		powerBar(s, cx, cy + 70 * k, k, power, crit, a, flash);
		if (crit > 0 && after < 0) {
			float pop = 1 + Math.max(0f, 1 - (ft - LAND - 100) / 200f);
			if (flash || crit < 0.05f) caption(g, s.font(), "⚠ CORE CRITICAL ⚠", cx, cy - 82 * k, 1.6f * pop, 0xFF3A2A, a);
		}
	}

	private static int px(float cx, float k, float x) {
		return Math.round(cx + x * k);
	}

	private static int py(float cy, float k, float y) {
		return Math.round(cy + y * k);
	}

	private static void rect(GuiGraphicsExtractor g, float cx, float cy, float k, float x0, float y0, float x1, float y1, int argb) {
		g.fill(px(cx, k, x0), py(cy, k, y0), px(cx, k, x1), py(cy, k, y1), argb);
	}

	/** A yellow and black diagonal stripe band across the frame at y..y+8. */
	private static void hazard(GuiGraphicsExtractor g, float cx, float cy, float k, float y, float a, boolean flash) {
		rect(g, cx, cy, k, -72, y, 72, y + 8, argb(0x141414, a));
		int yellow = flash ? 0xFFD23A : 0xD8A820;
		for (float x = -72; x <= 64; x += 8) line(g, cx + x * k, cy + (y + 8) * k, cx + (x + 8) * k, cy + y * k, 3.4f * k, argb(yellow, a));
		rect(g, cx, cy, k, -74, y - 1, -72, y + 9, argb(0x15171C, a));
		rect(g, cx, cy, k, 72, y - 1, 74, y + 9, argb(0x15171C, a));
	}

	private static void bolt(GuiGraphicsExtractor g, float x, float y, float r, float a) {
		ScathaRoulette.ellipse(g, x, y, r, r, argb(0xA8B0BC, a), argb(0x4A505A, a));
		line(g, x - r * 0.7f, y, x + r * 0.7f, y, Math.max(0.6f, r * 0.35f), argb(0x2A2E36, a));
	}

	/** The core: steel housing ring with bolts, a turning segment ring, glass, plasma, swirling sparks. */
	private static void core(Scene s, float x, float y, float k, float power, float crit, float implode, float a) {
		GuiGraphicsExtractor g = s.g();
		int plasma = plasmaColor(s);
		float heat = Math.max(crit, implode);
		int hot = mix(plasma, 0xFFFFFF, heat * 0.85f);
		glow(g, x, y, (36 + 30 * power + 40 * crit) * k, hot, (0.25f + 0.35f * power + 0.3f * crit) * a * (1 - implode * 0.6f));
		ScathaRoulette.ellipse(g, x, y, 34 * k, 34 * k, argb(0xA0A8B4, a), argb(0x3E444E, a));
		ScathaRoulette.ellipse(g, x, y, 30 * k, 30 * k, argb(0x2A2E36, a), argb(0x4A505A, a));
		for (int b = 0; b < 12; b++) {
			double an = b * TAU / 12;
			bolt(g, x + (float) Math.cos(an) * 32 * k, y + (float) Math.sin(an) * 32 * k, 1.3f * k, a);
		}
		// turning ring segments: faster with power, frantic when critical
		double turn = s.now() / 1000.0 * (0.4 + 3 * power + 9 * crit);
		for (int d = 0; d < 8; d++) {
			double a0 = turn + d * TAU / 8;
			for (int j = 0; j < 4; j++) {
				double t0 = a0 + j * 0.09, t1 = t0 + 0.09;
				line(g, x + (float) Math.cos(t0) * 27 * k, y + (float) Math.sin(t0) * 27 * k, x + (float) Math.cos(t1) * 27 * k, y + (float) Math.sin(t1) * 27 * k,
						2.6f * k, argb(mix(0x303640, hot, 0.3f + 0.7f * power), a));
			}
		}
		// glass and plasma
		ScathaRoulette.ellipse(g, x, y, 24 * k, 24 * k, argb(0x0A0C14, a), argb(0x151A2A, a));
		float r = 22 * k * (0.35f + 0.65f * power) * (1 - implode * 0.92f);
		if (power > 0) {
			glow(g, x, y, r * 1.3f, hot, 0.9f * a);
			ScathaRoulette.ellipse(g, x, y, r * 0.7f, r * 0.7f, argb(mix(hot, 0xFFFFFF, 0.3f), 0.85f * a), argb(hot, 0.85f * a));
			ScathaRoulette.ellipse(g, x, y, r * 0.3f, r * 0.3f, argb(0xFFFFFF, a), argb(0xFFFFFF, a));
			Random rand = new Random(3);
			for (int p = 0; p < 16; p++) { // sparks swirling in the plasma
				float rr = (6 + rand.nextFloat() * 15) * k * (1 - implode * 0.9f);
				double an = rand.nextFloat() * TAU + s.now() / (300.0 + rand.nextInt(500)) * (1 + 4 * crit);
				int c = COLORS[p % 5];
				if (s.fill()[p % 5] < 1) continue;
				float sx = x + (float) Math.cos(an) * rr, sy = y + (float) Math.sin(an) * rr;
				g.fill(Math.round(sx), Math.round(sy), Math.round(sx + 1.2f * k), Math.round(sy + 1.2f * k), argb(mix(c, 0xFFFFFF, 0.4f), power * a));
			}
		}
		// the glass cracks as it overloads
		float cracks = s.finale() ? s.span(2000, 2800) : 0f;
		if (cracks > 0) {
			Random rand = new Random(9);
			for (int c = 0; c < 6; c++) {
				double an = rand.nextFloat() * TAU;
				float px0 = x + (float) Math.cos(an) * 6 * k, py0 = y + (float) Math.sin(an) * 6 * k;
				for (int j = 0; j < 4 && j < cracks * 4; j++) {
					an += (rand.nextFloat() - 0.5f) * 1.2f;
					float px1 = px0 + (float) Math.cos(an) * 5 * k, py1 = py0 + (float) Math.sin(an) * 5 * k;
					line(g, px0, py0, px1, py1, 0.9f * k, argb(0xFFFFFF, 0.9f * a));
					px0 = px1;
					py0 = py1;
				}
			}
		}
		// implosion: streaks rushing in
		if (implode > 0)
			for (int d = 0; d < 18; d++) {
				double an = d * TAU / 18 + d;
				float far = (90 - 80 * implode) * k, near = far * 0.55f;
				line(g, x + (float) Math.cos(an) * far, y + (float) Math.sin(an) * far, x + (float) Math.cos(an) * near, y + (float) Math.sin(an) * near,
						1.2f * k, argb(0xFFFFFF, implode * a));
			}
		// glass shine
		line(g, x - 15 * k, y - 12 * k, x - 7 * k, y - 18 * k, 1.6f * k, argb(0xFFFFFF, 0.3f * a));
	}

	/** The colour of the plasma: the placed crystals' colours, flowing from one to the next. */
	private static int plasmaColor(Scene s) {
		int[] on = new int[5];
		int n = 0;
		for (int i = 0; i < 5; i++) if (s.fill()[i] >= 1) on[n++] = COLORS[i];
		if (n == 0) return 0x405060;
		float t = (s.now() / 900f) % n;
		return mix(on[(int) t], on[((int) t + 1) % n], t - (int) t);
	}

	/** A fuel rod: steel caps, a glass tube filling with the crystal's colour, bubbles, the crystal on top. */
	private static void rod(Scene s, int i, float x, float y, float k, float level, float a) {
		GuiGraphicsExtractor g = s.g();
		int c = COLORS[i];
		if (level > 0) glow(g, x, y + 16 * k, 16 * k, c, 0.35f * level * a);
		g.fillGradient(Math.round(x - 8 * k), Math.round(y), Math.round(x + 8 * k), Math.round(y + 5 * k), argb(0xB4BCC8, a), argb(0x5A616C, a));
		g.fillGradient(Math.round(x - 8 * k), Math.round(y + 33 * k), Math.round(x + 8 * k), Math.round(y + 38 * k), argb(0x8A929E, a), argb(0x3A4048, a));
		g.fill(Math.round(x - 5.5f * k), Math.round(y + 5 * k), Math.round(x + 5.5f * k), Math.round(y + 33 * k), argb(0x6A727E, a));
		g.fill(Math.round(x - 4.5f * k), Math.round(y + 5 * k), Math.round(x + 4.5f * k), Math.round(y + 33 * k), argb(0x080A10, a));
		if (level > 0) {
			float top = y + 33 * k - 28 * k * level;
			g.fillGradient(Math.round(x - 4.5f * k), Math.round(top), Math.round(x + 4.5f * k), Math.round(y + 33 * k), argb(mix(c, 0xFFFFFF, 0.35f), 0.95f * a), argb(mix(c, 0x000000, 0.3f), 0.95f * a));
			g.fill(Math.round(x - 4.5f * k), Math.round(top), Math.round(x + 4.5f * k), Math.round(top + Math.max(1, 0.8f * k)), argb(0xFFFFFF, 0.8f * a));
			Random r = new Random(i * 17L);
			for (int b = 0; b < 5; b++) {
				float d = ((s.now() + r.nextInt(1200)) % 1200) / 1200f;
				float by = y + 33 * k - d * (y + 33 * k - top), bx = x + (r.nextFloat() - 0.5f) * 6 * k;
				if (by > top) g.fill(Math.round(bx), Math.round(by), Math.round(bx + 1.2f * k), Math.round(by + 1.2f * k), argb(0xFFFFFF, 0.7f * a));
			}
		}
		g.fill(Math.round(x - 3.5f * k), Math.round(y + 6 * k), Math.round(x - 2.5f * k), Math.round(y + 31 * k), argb(0xFFFFFF, 0.18f * a)); // glass shine
		stone(s, i, x, y + 2.5f * k, 3.6f * k, s.finale() ? 0.4f + 0.6f * s.charge() : 0f);
	}

	/** POWER nn%: 20 segments green to red; in the finale it overloads, red, past 100% to 999%. */
	private static void powerBar(Scene s, float cx, float y, float k, float power, float crit, float a, boolean flash) {
		GuiGraphicsExtractor g = s.g();
		float x0 = cx - 70 * k, w = 140 * k, h = 8 * k;
		g.fill(Math.round(x0 - k), Math.round(y - k), Math.round(x0 + w + k), Math.round(y + h + k), argb(0x5A616C, a));
		g.fill(Math.round(x0), Math.round(y), Math.round(x0 + w), Math.round(y + h), argb(0x0A0C10, a));
		int lit = Math.round(power * 20);
		for (int seg = 0; seg < 20; seg++) {
			if (seg >= lit && crit <= 0) break;
			float f = seg / 19f;
			int c = crit > 0 ? (flash ? 0xFF5040 : 0xC02010) : f < 0.5f ? mix(0x40E060, 0xF0E040, f * 2) : mix(0xF0E040, 0xFF5030, (f - 0.5f) * 2);
			float sx = x0 + seg * w / 20 + k * 0.6f;
			g.fill(Math.round(sx), Math.round(y + k), Math.round(sx + w / 20 - k * 1.2f), Math.round(y + h - k), argb(c, a));
		}
		int pct = crit > 0 ? Math.round(100 + 899 * crit * crit) : Math.round(power * 100);
		if (crit > 0) { // spilling over the end of the bar
			float over = Math.min(1f, crit * 1.4f) * 22 * k;
			g.fill(Math.round(x0 + w + k), Math.round(y + k), Math.round(x0 + w + k + over), Math.round(y + h - k), argb(0xFF3020, a));
			glow(g, x0 + w + over, y + h / 2, 6 * k, 0xFFB040, a);
		}
		String text = "POWER " + pct + "%";
		caption(g, s.font(), text, cx, y + h + 2 * k, Math.max(0.75f, k * 0.6f), crit > 0 ? 0xFF5040 : 0xE0E6EE, a);
	}

	/** Chunks of the reactor flying out after it blows. */
	private static void debris(Scene s, float cx, float cy, float k, long after) {
		GuiGraphicsExtractor g = s.g();
		float t = after / 1000f;
		if (t > 1.6f) return;
		Random r = new Random(5);
		for (int d = 0; d < 22; d++) {
			double an = r.nextFloat() * TAU;
			float speed = (60 + r.nextFloat() * 120) * k, size = (2 + r.nextFloat() * 5) * k;
			float x = cx + (float) Math.cos(an) * speed * t, y = cy - 20 * k + (float) Math.sin(an) * speed * t + 60 * k * t * t;
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(x, y);
			pose.rotate(t * (4 + d));
			int c = d % 3 == 0 ? 0xFFB040 : d % 3 == 1 ? 0x4A505A : 0x2A2E36;
			g.fill(Math.round(-size), Math.round(-size * 0.6f), Math.round(size), Math.round(size * 0.6f), argb(c, Math.max(0f, 1 - t / 1.6f) * s.a()));
			pose.popMatrix();
		}
	}

	// ================= CONSTELLATION =================

	/** The five stars, in units of the sky: a pickaxe (head tips, top, handle). Index = crystal. */
	private static final float[][] STAR = {{-1f, -0.36f}, {0f, -0.72f}, {0f, 0.14f}, {1f, -0.36f}, {0f, 0.95f}};
	private static final int[][] LINES = {{0, 1}, {1, 3}, {1, 2}, {2, 4}};

	/**
	 * A patch of night sky. Each crystal is a falling star that settles into place, joined to its
	 * neighbours: a pickaxe. Finale: the outline fills with stars, the pickaxe lights up, winds back
	 * and swings, and the stars rain down.
	 */
	static void constellation(Scene s) {
		GuiGraphicsExtractor g = s.g();
		float u = s.u(), cx = s.cx(), cy = s.cy() - 6 * u, a = s.a();
		boolean fin = s.finale();
		long after = s.after();
		float sky = 92 * u, unit = 50 * u;
		// the sky: soft dark disc, two nebulae, twinkling stars
		glowOval(g, cx, cy, sky * 1.25f, sky * 1.08f, 0x050818, 1.6f * a); // soft dark edge
		ScathaRoulette.ellipse(g, cx, cy, sky * 0.86f, sky * 0.74f, argb(0x070B22, 0.8f * a), argb(0x0B0820, 0.8f * a));
		glowOval(g, cx - 30 * u, cy - 20 * u, 46 * u, 26 * u, 0x6A3AB0, 0.22f * a);
		glowOval(g, cx + 36 * u, cy + 22 * u, 40 * u, 22 * u, 0x2A5AB0, 0.2f * a);
		Random r = new Random(42);
		for (int k = 0; k < 90; k++) {
			double an = r.nextFloat() * TAU;
			float d = (float) Math.sqrt(r.nextFloat()) * 0.95f;
			float x = cx + (float) Math.cos(an) * d * sky * 0.86f, y = cy + (float) Math.sin(an) * d * sky * 0.74f;
			float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(s.now() / (350.0 + k * 9) + k));
			float sz = r.nextFloat() < 0.15f ? 1.4f * u : 0.7f * u;
			g.fill(Math.round(x), Math.round(y), Math.round(x + Math.max(1, sz)), Math.round(y + Math.max(1, sz)), argb(0xDDE6FF, tw * a));
		}
		// a shooting star now and then
		long cycle = s.now() % 3100;
		if (cycle < 700) {
			float p = cycle / 700f;
			float x = cx - 60 * u + 120 * u * p, y = cy - 50 * u + 30 * u * p;
			line(g, x - 22 * u, y - 5.5f * u, x, y, 1, argb(0xFFFFFF, 0.6f * (1 - p) * a));
		}
		// the pickaxe turns about the bottom of its handle: winds back, then swings
		float wind = fin ? ease(s.span(1900, 2700)) : 0f;
		float swing = fin ? s.span(3050, CLIMAX) : 0f;
		float angle = -0.55f * wind + 1.9f * swing * swing * swing;
		float gx = cx + STAR[4][0] * unit, gy = cy + STAR[4][1] * unit;
		float life = fin ? s.span(1500, 2300) : 0f;
		float fade = after > 0 ? Math.max(0f, 1 - after / 500f) : 1f;
		if (swing > 0.3f) // motion trail of the swing
			for (int ghost = 3; ghost >= 1; ghost--) drawPick(s, cx, cy, unit, gx, gy, angle - ghost * 0.22f * swing, life, 0.15f * a * fade / ghost, false);
		drawPick(s, cx, cy, unit, gx, gy, angle, life, a * fade, true);
		// the stars rain down after the strike
		if (after > 0 && after < 2000) {
			Random rr = new Random(77);
			for (int k = 0; k < 36; k++) {
				float start = rr.nextFloat() * 900;
				float p = (after - start) / 900f;
				if (p <= 0 || p >= 1) continue;
				float x0 = cx + (rr.nextFloat() - 0.5f) * 260 * u, y0 = cy - 120 * u;
				float x = x0 + 50 * u * p, y = y0 + 240 * u * p;
				int c = COLORS[k % 5];
				line(g, x - 10 * u, y - 24 * u, x, y, 1.4f, argb(c, 0.7f * (1 - p) * s.a()));
				glow(g, x, y, 3 * u, c, (1 - p) * s.a());
			}
		}
	}

	/** The constellation (lines, star outline, the five stars) turned by {@code angle} about the grip. */
	private static void drawPick(Scene s, float cx, float cy, float unit, float gx, float gy, float angle, float life, float a, boolean stars) {
		GuiGraphicsExtractor g = s.g();
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(gx, gy);
		pose.rotate(angle);
		pose.translate(-gx, -gy);
		float u = s.u();
		boolean fin = s.finale();
		// the outline fills in with small stars, then the whole pickaxe glows as light
		float fillIn = fin ? s.span(LAND, 1500) : 0f;
		if (fillIn > 0) {
			Random r = new Random(13);
			int n = 46;
			for (int k = 0; k < n && k < fillIn * n; k++) {
				float[] p = outline(k / (float) n, r.nextFloat() - 0.5f);
				float x = cx + p[0] * unit, y = cy + p[1] * unit;
				float tw = 0.6f + 0.4f * (float) Math.sin(s.now() / 200.0 + k);
				glow(g, x, y, 2.6f * u, 0xBFE4FF, 0.5f * tw * a);
				g.fill(Math.round(x), Math.round(y), Math.round(x + Math.max(1, u)), Math.round(y + Math.max(1, u)), argb(0xFFFFFF, tw * a));
			}
		}
		if (life > 0) { // a pickaxe of light: head and handle as glowing beams
			int head = 0x9FD8FF, handle = 0xFFD8A0;
			for (int[] ln : LINES) {
				float[] p0 = STAR[ln[0]], p1 = STAR[ln[1]];
				boolean isHandle = ln[0] == 2 || ln[1] == 2;
				beam(g, cx + p0[0] * unit, cy + p0[1] * unit, cx + p1[0] * unit, cy + p1[1] * unit, (2 + 3 * life) * u, isHandle ? handle : head, life * a);
			}
		}
		// lines between placed stars: drawn out from the older star toward the new one
		for (int[] ln : LINES) {
			float fa = s.fill()[ln[0]], fb = s.fill()[ln[1]];
			if (fa < 1 || fb < 1) continue;
			float p = Math.min(Math.min(1f, (fa - 1) * 1.8f), Math.min(1f, (fb - 1) * 1.8f));
			if (fin) p = 1;
			int from = fa >= fb ? ln[0] : ln[1], to = from == ln[0] ? ln[1] : ln[0];
			float x0 = cx + STAR[from][0] * unit, y0 = cy + STAR[from][1] * unit;
			float x1 = cx + STAR[to][0] * unit, y1 = cy + STAR[to][1] * unit;
			line(g, x0, y0, x0 + (x1 - x0) * p, y0 + (y1 - y0) * p, 3 * u, argb(mix(COLORS[from], COLORS[to], 0.5f), 0.2f * a));
			line(g, x0, y0, x0 + (x1 - x0) * p, y0 + (y1 - y0) * p, Math.max(1, 0.7f * u), argb(0xEAF4FF, 0.85f * a));
		}
		if (stars) for (int i = 0; i < 5; i++) star(s, i, cx + STAR[i][0] * unit, cy + STAR[i][1] * unit, a);
		pose.popMatrix();
	}

	/** A point on the pickaxe's outline: t 0..1 along head then handle, {@code side} -0.5..0.5 across its width. */
	private static float[] outline(float t, float side) {
		if (t < 0.6f) { // the head: an arc from tip to tip through the top
			float p = t / 0.6f;
			float x = -1 + 2 * p;
			float y = -0.36f - 0.36f * (1 - x * x) + side * 0.16f * (1 - Math.abs(x) * 0.7f);
			return new float[]{x, y};
		}
		float p = (t - 0.6f) / 0.4f; // the handle
		return new float[]{side * 0.12f, -0.62f + 1.57f * p};
	}

	/** A main star: a falling star while dropping in, then a bright four-point star in its colour. */
	private static void star(Scene s, int i, float x, float y, float a) {
		GuiGraphicsExtractor g = s.g();
		float u = s.u(), f = s.fill()[i];
		if (f <= 0) { // not yet: a faint dot where it will be
			g.fill(Math.round(x), Math.round(y), Math.round(x + Math.max(1, u)), Math.round(y + Math.max(1, u)), argb(0x8090B0, 0.4f * a));
			return;
		}
		int c = COLORS[i];
		if (f < 1) { // a meteor sliding in from the upper right
			float e = 1 - (1 - f) * (1 - f);
			float mx = x + (1 - e) * 70 * u, my = y - (1 - e) * 60 * u;
			line(g, mx, my, mx + 26 * u * (1 - e) + 4 * u, my - 22 * u * (1 - e) - 3 * u, 1.6f * u, argb(c, 0.6f * a));
			glow(g, mx, my, 6 * u, c, a);
			g.fill(Math.round(mx - u), Math.round(my - u), Math.round(mx + u), Math.round(my + u), argb(0xFFFFFF, a));
			return;
		}
		float pulse = f < 2 ? 2 - f : 0;
		float charge = s.charge();
		float tw = 0.8f + 0.2f * (float) Math.sin(s.now() / 160.0 + i * 2);
		float size = (1 + 0.6f * pulse + 0.5f * charge) * tw;
		if (pulse > 0) ring(g, x, y, (4 + 16 * (1 - pulse)) * u, 1.2f, c, pulse * a);
		glow(g, x, y, (9 + 6 * charge) * u * size, c, 0.8f * a);
		float len = 7 * u * size, diag = 3.5f * u * size;
		line(g, x - len, y, x + len, y, Math.max(1, 0.9f * u), argb(mix(c, 0xFFFFFF, 0.6f), a));
		line(g, x, y - len, x, y + len, Math.max(1, 0.9f * u), argb(mix(c, 0xFFFFFF, 0.6f), a));
		line(g, x - diag, y - diag, x + diag, y + diag, Math.max(1, 0.6f * u), argb(c, 0.7f * a));
		line(g, x - diag, y + diag, x + diag, y - diag, Math.max(1, 0.6f * u), argb(c, 0.7f * a));
		ScathaRoulette.ellipse(g, x, y, 1.8f * u * size, 1.8f * u * size, argb(0xFFFFFF, a), argb(mix(c, 0xFFFFFF, 0.6f), a));
	}
}
