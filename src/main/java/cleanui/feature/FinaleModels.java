package cleanui.feature;

import cleanui.feature.NucleusFinale.Scene;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static cleanui.feature.NucleusFinale.*;

/**
 * The blocky 3D nucleus finales (gauntlet, golem, crown, altar), drawn with {@link BlockModel} from
 * a swatch atlas ({@code finale.png}: 14 bevelled materials, each a 32×16 skin-layout patch). The
 * crystals, cracks, eyes and energy are 2D on top, placed by projecting model points.
 */
final class FinaleModels {
	private static final Identifier TEX = Identifier.fromNamespaceAndPath("cleanui", "textures/gui/finale.png");
	private static final int GOLD = 0, GOLD_DARK = 1, STONE = 2, STONE_DARK = 3, MOSS = 4, MARBLE = 5, OBSIDIAN = 6,
			BLACK = 9, RUNE = 10, BRIGHT = 11, VELVET = 12, ERMINE = 13;
	private static final float TAU = (float) (Math.PI * 2), STEP = TAU / 5;

	private FinaleModels() {}

	private static void box(BlockModel m, Matrix4f at, float x, float y, float z, float w, float h, float d, int swatch) {
		m.box(at, x, y, z, w, h, d, (swatch % 4) * 32, (swatch / 4) * 16, 8, 8, 8, 0);
	}

	private static float[] at(BlockModel m, Matrix4f mat, float x, float y, float z) {
		return m.project(mat, x, y, z);
	}

	/** A model point's screen position. */
	private record P(float x, float y, float depth) {
		static P of(float[] p) {
			return new P(p[0], p[1], p[2]);
		}
	}

	// ================= GAUNTLET =================

	/** Knuckles left to right, then the back of the hand: which crystal sits where. */
	private static final int[] KNUCKLE = {0, 2, 3, 4};
	private static final float[][] FINGER = {{6f, 5f, 4f}, {6.6f, 5.4f, 4.4f}, {6.2f, 5.2f, 4.2f}, {5f, 4.2f, 3.4f}};

	/**
	 * A golden gauntlet seen from the back of the hand: four knuckle stones and a big one in the
	 * middle. Finale: stones blaze, lightning arcs between them, the hand slowly clenches into a
	 * fist and rises while the energy builds, then the fingers fly open: the snap.
	 */
	static void gauntlet(Scene s) {
		GuiGraphicsExtractor g = s.g();
		float u = s.u();
		boolean fin = s.finale();
		long ft = s.ft(), after = s.after();
		float clench = fin ? ease(s.span(1500, 2300)) : 0f;
		float open = after >= 0 ? ease(after / 160f) : 0f;
		float curl = clench * (1 - open), spread = open * 0.3f;
		float raise = fin ? ease(s.span(1500, 2400)) : 0f;
		float pull = fin && after < 0 ? ease(s.span(3000, CLIMAX)) : 0f;
		float yaw = (float) (0.32 * Math.sin(s.now() / 1500.0)) * (fin ? 1 - s.span(LAND, 1500) : 1);
		Matrix4f view = new Matrix4f().rotateX(0.1f - 0.18f * pull + 0.08f * open).rotateY(yaw);
		float scale = 2.05f * u * (1 + 0.12f * raise + 0.08f * open);
		float ox = s.cx(), oy = s.cy() + 6 * u - raise * 8 * u;
		BlockModel m = new BlockModel(TEX, 128, view, ox, oy, scale);
		Matrix4f root = new Matrix4f().translate(0, -6, 0);
		// cuff
		box(m, root, -6.5f, -16, -5, 13, 14, 10, GOLD_DARK);
		box(m, root, -7.2f, -3.6f, -5.7f, 14.4f, 2.6f, 11.4f, GOLD);
		box(m, root, -7f, -16.6f, -5.5f, 14, 2, 11, GOLD);
		box(m, root, -1.5f, -14.5f, 4.9f, 3, 10.5f, 1, BRIGHT);
		box(m, root, -4.6f, -12f, 4.8f, 1.4f, 7f, 0.8f, GOLD);
		box(m, root, 3.2f, -12f, 4.8f, 1.4f, 7f, 0.8f, GOLD);
		// back of the hand: cross bars behind the big bezel, the knuckle band, the bezels
		Matrix4f hand = new Matrix4f(root);
		box(m, hand, -8, -1, -3, 16, 14, 6, GOLD);
		box(m, hand, -0.7f, -0.6f, 2.9f, 1.4f, 11f, 0.7f, BRIGHT);
		box(m, hand, -7f, 4.8f, 2.9f, 14f, 1.4f, 0.7f, BRIGHT);
		box(m, hand, -3.2f, 2.3f, 3.2f, 6.4f, 6.4f, 1f, GOLD_DARK);
		box(m, hand, -8.4f, 10.4f, -3.4f, 16.8f, 3f, 6.8f, GOLD_DARK);
		for (int f = 0; f < 4; f++) box(m, hand, -6 + f * 4 - 1.7f, 10.5f, 3.2f, 3.4f, 3.4f, 1f, BRIGHT);
		// fingers: three segments each, curling away from us into a fist
		for (int f = 0; f < 4; f++) {
			float fx = -6 + f * 4, dir = (f - 1.5f) / 1.5f;
			float[] len = FINGER[f];
			Matrix4f f1 = new Matrix4f(hand).translate(fx, 13, 0).rotateZ(-dir * spread).rotateX(-curl * 1.45f - 0.08f * (1 - curl) + open * 0.15f);
			box(m, f1, -1.6f, 0, -1.6f, 3.2f, len[0], 3.2f, GOLD);
			box(m, f1, -1.85f, len[0] - 1.1f, -1.85f, 3.7f, 1.3f, 3.7f, GOLD_DARK);
			Matrix4f f2 = new Matrix4f(f1).translate(0, len[0], 0).rotateX(-curl * 1.7f);
			box(m, f2, -1.5f, 0, -1.5f, 3, len[1], 3, GOLD);
			box(m, f2, -1.7f, len[1] - 1f, -1.7f, 3.4f, 1.1f, 3.4f, GOLD_DARK);
			Matrix4f f3 = new Matrix4f(f2).translate(0, len[1], 0).rotateX(-curl * 1.3f);
			box(m, f3, -1.4f, 0, -1.4f, 2.8f, len[2], 2.8f, GOLD);
			box(m, f3, -1f, len[2] - 0.4f, -1f, 2f, 1.2f, 2f, BRIGHT);
		}
		// thumb, on the left (a right hand seen from the back)
		Matrix4f t1 = new Matrix4f(hand).translate(-8, 3.5f, 0.5f - curl * 1.5f).rotateZ(0.95f - curl * 0.8f + open * 0.25f).rotateX(-curl * 1.3f); // tucks in along the side for the fist
		box(m, t1, -1.7f, 0, -1.7f, 3.4f, 6, 3.4f, GOLD);
		box(m, t1, -1.9f, 4.9f, -1.9f, 3.8f, 1.2f, 3.8f, GOLD_DARK);
		Matrix4f t2 = new Matrix4f(t1).translate(0, 6, 0).rotateZ(-0.35f - curl * 0.5f).rotateX(-curl * 0.8f);
		box(m, t2, -1.5f, 0, -1.5f, 3, 4.6f, 3, GOLD);
		box(m, t2, -1f, 4.2f, -1f, 2f, 1.1f, 2f, BRIGHT);
		// a dark backing glow, the hand, then the energy and stones on top
		P fist = P.of(at(m, hand, 0, 10, 0));
		float build = fin && after < 0 ? s.span(2300, CLIMAX) : 0f;
		if (build > 0) glow(g, fist.x(), fist.y(), (24 + 50 * build) * u, COLORS[(int) (s.now() / 140 % 5)], 0.45f * build * s.a());
		m.draw(g, s.a());
		P[] st = new P[5];
		for (int k = 0; k < 4; k++) st[KNUCKLE[k]] = P.of(at(m, hand, -6 + k * 4, 12.2f, 4.4f));
		st[1] = P.of(at(m, hand, 0, 5.5f, 4.4f));
		float charge = s.charge();
		float arcs = fin && after < 0 ? s.span(LAND, 1000) : 0f;
		if (arcs > 0) {
			for (int k = 0; k < 3; k++) {
				P a = st[KNUCKLE[k]], b = st[KNUCKLE[k + 1]];
				arc(g, a.x(), a.y(), b.x(), b.y(), k, s.now(), mix(COLORS[KNUCKLE[k]], COLORS[KNUCKLE[k + 1]], 0.5f), arcs * s.a());
			}
			for (int k = 0; k < 4; k++) {
				P a = st[KNUCKLE[k]];
				arc(g, a.x(), a.y(), st[1].x(), st[1].y(), 10 + k, s.now(), mix(COLORS[KNUCKLE[k]], COLORS[1], 0.5f), arcs * (0.4f + 0.6f * charge) * s.a());
			}
		}
		if (build > 0) // crackling out of the fist
			for (int k = 0; k < 6; k++) {
				double an = k * TAU / 6 + s.now() / 300.0;
				float r = (30 + 20 * build) * u;
				arc(g, fist.x(), fist.y(), fist.x() + (float) Math.cos(an) * r, fist.y() + (float) Math.sin(an) * r, 20 + k, s.now(), COLORS[k % 5], build * s.a());
			}
		for (int k = 0; k < 5; k++) stone(s, k, st[k].x(), st[k].y(), (k == 1 ? 2.5f : 1.55f) * scale, fin ? 0.3f + 0.7f * charge : 0f);
		if (after >= 0 && after < 1400) { // five beams of colour out of the open hand
			float r = after / 1400f;
			for (int k = 0; k < 5; k++) {
				double an = -Math.PI / 2 + (k - 2) * 0.42;
				float len = (60 + 260 * ease(r * 2)) * u;
				beam(g, fist.x(), fist.y(), fist.x() + (float) Math.cos(an) * len, fist.y() + (float) Math.sin(an) * len, 3 * u, COLORS[k], (1 - r) * s.a());
			}
		}
	}

	// ================= GOLEM =================

	/** Each crystal's crack across the chest: points in the torso's front plane (x, y), from its socket out. */
	private static final float[][][] CRACKS = new float[5][][];
	static {
		java.util.Random r = new java.util.Random(11);
		for (int k = 0; k < 5; k++) {
			double th = Math.PI / 2 + k * STEP;
			float x = (float) Math.cos(th) * 4.2f, y = 24.5f + (float) Math.sin(th) * 4.2f;
			float[][] pts = new float[8][];
			pts[0] = new float[]{x, y};
			for (int j = 1; j < 8; j++) {
				th += (r.nextFloat() - 0.5f) * 1.1f;
				x = Math.max(-10.5f, Math.min(10.5f, x + (float) Math.cos(th) * 2.1f));
				y = Math.max(12.5f, Math.min(33.5f, y + (float) Math.sin(th) * 2.1f));
				pts[j] = new float[]{x, y};
			}
			CRACKS[k] = pts;
		}
	}

	/**
	 * A dormant stone golem, hunched, five sockets in its chest. Each crystal cracks its body with
	 * light. Finale: cracks race across it, pebbles fall, the eyes ignite, it rises and roars, lifts
	 * both fists overhead and slams the ground.
	 */
	static void golem(Scene s) {
		GuiGraphicsExtractor g = s.g();
		float u = s.u();
		boolean fin = s.finale();
		long after = s.after();
		float spread = fin ? s.span(LAND, 1600) : 0f;
		float lift = fin ? ease(s.span(1600, 2200)) : 0f;
		float stand = fin ? ease(s.span(2200, 2800)) : 0f;
		float roar = fin ? (float) Math.sin(Math.PI * s.span(2300, 2900)) : 0f;
		float up = ease(s.span(2300, 2900));
		float slam = s.span(3150, CLIMAX);
		slam = slam * slam * slam;
		float arm = -2.8f * up + (2.8f - 0.35f) * slam; // shoulder angle: 0 hanging, -2.8 overhead, -0.35 smashed down in front
		float c = 1 - stand;
		float yaw = -0.38f + (float) (0.06 * Math.sin(s.now() / 1700.0));
		Matrix4f view = new Matrix4f().rotateX(0.2f).rotateY(yaw);
		float scale = 2.0f * u;
		BlockModel m = new BlockModel(TEX, 128, view, s.cx(), s.cy() + 4 * u, scale);
		Matrix4f root = new Matrix4f().translate(0, -21, 0);
		// legs and feet
		for (int sx = -1; sx <= 1; sx += 2) {
			float x0 = sx < 0 ? -8.5f : 2.5f;
			box(m, root, x0 + 0.5f, 0, -3.5f, 6, 12, 7, STONE_DARK);
			box(m, root, x0, 0, -4.6f, 7, 2.6f, 9.2f, STONE);
		}
		Matrix4f body = new Matrix4f(root).translate(0, 12 - 3 * c, 0).rotateX(0.45f * c).translate(0, -12, 0);
		box(m, body, -11, 12, -6, 22, 22, 12, MOSS);
		box(m, body, -9.5f, 11.6f, -6.4f, 19, 4, 12.8f, STONE_DARK);
		box(m, body, -7.6f, 18f, 5.9f, 15.2f, 13f, 0.8f, OBSIDIAN);
		box(m, body, -14f, 28.5f, -6.6f, 6.5f, 6.5f, 13.2f, STONE);
		box(m, body, 7.5f, 28.5f, -6.6f, 6.5f, 6.5f, 13.2f, STONE);
		// head: bowed while dormant, lifts, tips back to roar
		float nod = 0.55f * (1 - lift) - 0.3f * roar;
		Matrix4f head = new Matrix4f(body).translate(0, 34, 0).rotateX(nod).translate(0, -34, 0);
		box(m, head, -5, 34, -5, 10, 10, 10, STONE);
		box(m, head, -5.6f, 40.4f, 4f, 11.2f, 2f, 2.2f, STONE_DARK);
		box(m, head, -1f, 36.4f, 4.6f, 2f, 3.6f, 1.6f, STONE_DARK);
		box(m, head, -4f, 33.2f - 2.2f * roar, 3f, 8f, 2.4f, 3f, STONE_DARK);
		// arms from the shoulders
		Matrix4f[] fists = new Matrix4f[2];
		for (int i = 0; i < 2; i++) {
			int sx = i == 0 ? -1 : 1;
			Matrix4f a = new Matrix4f(body).translate(sx * 13.8f, 31, 0).rotateZ(sx * (0.1f + 0.1f * c)).rotateX(arm);
			box(m, a, -3, -14, -3.5f, 6, 14, 7, STONE);
			box(m, a, -3.3f, -26, -3.8f, 6.6f, 12, 7.6f, MOSS);
			box(m, a, -3.9f, -32.5f, -4.4f, 7.8f, 6.5f, 8.8f, STONE_DARK);
			fists[i] = a;
		}
		// ground shadow, the stone body, then the light
		P feet = P.of(at(m, root, 0, 0, 0));
		ScathaRoulette.ellipse(g, feet.x(), feet.y() + 2 * u, 34 * u, 7 * u, argb(0x000000, 0.35f * s.a()), argb(0x000000, 0.1f * s.a()));
		m.draw(g, s.a());
		// cracks: short from each set crystal; the finale spreads them all the way
		for (int k = 0; k < 5; k++) {
			float f = s.fill()[k];
			if (f < 1) continue;
			float len = Math.min(1f, (f - 1) * 2) * 0.32f + 0.68f * spread;
			crack(s, m, body, CRACKS[k], 6.05f, len, COLORS[k], fin ? 0.6f + 0.4f * s.charge() : 0.6f);
		}
		if (spread > 0.3f) { // the head cracks too, from the eyes down
			float hl = (spread - 0.3f) / 0.7f;
			crack(s, m, head, new float[][]{{-2.5f, 38}, {-3, 36.5f}, {-2.2f, 35.2f}, {-3.4f, 34.2f}}, 5.05f, hl, 0x55FFAA, 0.8f);
			crack(s, m, head, new float[][]{{2.5f, 38}, {3.2f, 36.8f}, {2.4f, 35.4f}, {3.2f, 34.1f}}, 5.05f, hl, 0x55FFAA, 0.8f);
		}
		for (int k = 0; k < 5; k++) {
			double th = Math.PI / 2 + k * STEP;
			P p = P.of(at(m, body, (float) Math.cos(th) * 4.2f, 24.5f + (float) Math.sin(th) * 4.2f, 6.9f));
			stone(s, k, p.x(), p.y(), 1.5f * scale, fin ? 0.3f + 0.7f * s.charge() : 0f);
		}
		// eyes: embers while dormant, ignite in the finale
		float eyes = fin ? 0.15f + 0.85f * ease(s.span(1600, 2000)) : 0.12f;
		for (int sx = -1; sx <= 1; sx += 2) {
			P e = P.of(at(m, head, sx * 2.5f, 38.6f, 5.1f));
			glow(g, e.x(), e.y(), (3 + 9 * eyes) * u, 0x55FFAA, 0.7f * eyes * s.a());
			line(g, e.x() - 2.2f * u, e.y(), e.x() + 2.2f * u, e.y(), 1.4f * u, argb(mix(0x55FFAA, 0xFFFFFF, eyes), (0.4f + 0.6f * eyes) * s.a()));
		}
		// pebbles shaking loose while it wakes
		if (fin && s.ft() > 700 && s.ft() < 2600) {
			java.util.Random r = new java.util.Random(s.ft() / 400);
			for (int k = 0; k < 8; k++) {
				float px = feet.x() + (r.nextFloat() - 0.5f) * 40 * u, py0 = feet.y() - (10 + r.nextFloat() * 50) * u;
				float d = (s.ft() % 400) / 400f;
				float py = py0 + d * d * 40 * u;
				g.fill(Math.round(px), Math.round(py), Math.round(px + 1.6f * u), Math.round(py + 1.6f * u), argb(0x6E6E74, (1 - d) * s.a()));
			}
		}
		// the slam: dust from both fists and cracks in the ground
		if (after >= 0 && after < 1600) {
			float r = after / 1600f;
			for (Matrix4f fm : fists) {
				P fp = P.of(at(m, fm, 0, -32, 0));
				glowOval(g, fp.x(), fp.y(), (12 + 60 * ease(r * 1.5f)) * u, (4 + 14 * ease(r * 1.5f)) * u, 0xB8B0A0, 0.55f * (1 - r) * s.a());
				for (int k = 0; k < 6; k++) {
					double an = Math.PI + k * Math.PI / 5;
					float len = (10 + 40 * ease(r * 3)) * u;
					float ex = fp.x() + (float) Math.cos(an) * len, ey = fp.y() - (float) Math.sin(an) * len * 0.25f;
					line(g, fp.x(), fp.y(), ex, ey, 1.4f * u, argb(0x55FFAA, (1 - r) * s.a()));
				}
			}
		}
	}

	/** A glowing crack along points in a part's front plane at depth z (torso 6.05, head 5.05), drawn to {@code len} 0..1. */
	private static void crack(Scene s, BlockModel m, Matrix4f part, float[][] pts, float z, float len, int rgb, float bright) {
		if (len <= 0) return;
		float total = (pts.length - 1) * Math.min(1f, len);
		float pulse = bright * (0.75f + 0.25f * (float) Math.sin(s.now() / 200.0));
		for (int j = 0; j < pts.length - 1 && j < total; j++) {
			float f = Math.min(1f, total - j);
			P a = P.of(at(m, part, pts[j][0], pts[j][1], z));
			P b = P.of(at(m, part, pts[j][0] + (pts[j + 1][0] - pts[j][0]) * f, pts[j][1] + (pts[j + 1][1] - pts[j][1]) * f, z));
			line(s.g(), a.x(), a.y(), b.x(), b.y(), 3.2f * s.u(), argb(rgb, 0.25f * pulse * s.a()));
			line(s.g(), a.x(), a.y(), b.x(), b.y(), 1.1f * s.u(), argb(mix(rgb, 0xFFFFFF, 0.5f), pulse * s.a()));
		}
	}

	// ================= CROWN =================

	/**
	 * A gold crown with an ermine rim, red velvet cap and five spikes, a crystal set under each.
	 * Placing turns the new socket to the front. Finale: it rises and spins faster and faster in
	 * golden rays, then slows to land facing you: KING OF THE NUCLEUS.
	 */
	static void crown(Scene s) {
		GuiGraphicsExtractor g = s.g();
		float u = s.u();
		boolean fin = s.finale();
		int last = newest(s);
		float base = -last * STEP;
		float spin = fin ? crownSpin(s.ft(), base) : base + (float) (0.28 * Math.sin(s.now() / 900.0));
		float rise = fin ? ease(s.span(LAND, 1500)) * 16 - ease(s.span(3000, CLIMAX)) * 5 : 0f;
		float bob = (float) Math.sin(s.now() / 500.0) * 1.2f;
		float cy = s.cy() - (rise + bob) * u;
		// golden rays and a halo behind it
		float rays = fin ? s.span(LAND, 1800) : 0f;
		if (rays > 0) {
			float fade = s.after() > 0 ? Math.max(0f, 1 - s.after() / 1500f) : 1f;
			for (int k = 0; k < 14; k++) {
				double an = k * TAU / 14 + s.now() / 2400.0;
				float len = (70 + 50 * rays) * u;
				line(g, s.cx(), cy, s.cx() + (float) Math.cos(an) * len, cy + (float) Math.sin(an) * len, (6 + 6 * rays) * u, argb(0xFFD54A, 0.12f * rays * fade * s.a()));
			}
			glow(g, s.cx(), cy, (40 + 30 * s.charge()) * u, 0xFFE38A, 0.5f * rays * fade * s.a());
		}
		Matrix4f view = new Matrix4f().rotateX(0.38f + 0.12f * (fin ? s.span(LAND, 1500) : 0f));
		float scale = 2.6f * u;
		BlockModel m = new BlockModel(TEX, 128, view, s.cx(), cy + 6 * u, scale);
		Matrix4f root = new Matrix4f().rotateY(spin).translate(0, -7, 0);
		int n = 24;
		float r = 12;
		for (int j = 0; j < n; j++) {
			Matrix4f sg = new Matrix4f(root).rotateY(j * TAU / n).translate(0, 0, r);
			box(m, sg, -1.7f, 0, -0.9f, 3.4f, 6, 1.8f, GOLD);
			box(m, sg, -1.76f, 5.5f, -1.05f, 3.52f, 1, 2.1f, BRIGHT);
			Matrix4f fur = new Matrix4f(root).rotateY(j * TAU / n).translate(0, 0, r + 0.5f);
			box(m, fur, -1.85f, -2.7f, -1.1f, 3.7f, 2.9f, 2.2f, ERMINE);
		}
		for (int k = 0; k < 5; k++) {
			Matrix4f sp = new Matrix4f(root).rotateY(k * STEP).translate(0, 0, r);
			box(m, sp, -2.3f, 6, -0.9f, 4.6f, 3, 1.8f, GOLD);
			box(m, sp, -1.6f, 9, -0.8f, 3.2f, 3, 1.6f, GOLD);
			box(m, sp, -1f, 12, -0.7f, 2f, 3, 1.4f, GOLD);
			box(m, sp, -1.3f, 15, -1.3f, 2.6f, 2.6f, 2.6f, BRIGHT);
			Matrix4f mid = new Matrix4f(root).rotateY(k * STEP + STEP / 2).translate(0, 0, r);
			box(m, mid, -1.2f, 6, -0.8f, 2.4f, 2.6f, 1.6f, GOLD);
			box(m, mid, -0.9f, 8.6f, -0.9f, 1.8f, 1.8f, 1.8f, BRIGHT);
		}
		box(m, root, -8, 3.5f, -8, 16, 4.5f, 16, VELVET);
		box(m, root, -6, 8, -6, 12, 2.6f, 12, VELVET);
		box(m, root, -3.5f, 10.6f, -3.5f, 7, 1.6f, 7, VELVET);
		box(m, root, -1.5f, 12.2f, -1.5f, 3, 3, 3, BRIGHT);
		box(m, root, -0.5f, 15.2f, -0.5f, 1, 4, 1, BRIGHT);
		box(m, root, -1.7f, 16.8f, -0.5f, 3.4f, 1, 1, BRIGHT);
		// crystals: the ones round the back first (the band hides them), then the model, then the front ones
		List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3, 4));
		P[] st = new P[5];
		for (int k = 0; k < 5; k++) st[k] = P.of(at(m, new Matrix4f(root).rotateY(k * STEP), 0, 3, r + 1.05f));
		order.sort(Comparator.comparingDouble(k -> st[k].depth()));
		float glowAmt = fin ? 0.3f + 0.7f * s.charge() : 0f;
		for (int k : order) if (st[k].depth() < 0) stone(s, k, st[k].x(), st[k].y(), 1.7f * scale, glowAmt);
		m.draw(g, s.a());
		for (int k : order) if (st[k].depth() >= 0) stone(s, k, st[k].x(), st[k].y(), 1.7f * scale, glowAmt);
		// sparkles circling
		if (fin) for (int k = 0; k < 10; k++) {
			double an = k * TAU / 10 + s.now() / 600.0;
			float x = s.cx() + (float) Math.cos(an) * 46 * u, y = cy + (float) Math.sin(an) * 12 * u - 4 * u;
			float tw = 0.5f + 0.5f * (float) Math.sin(s.now() / 150.0 + k);
			line(g, x - 2.5f * u * tw, y, x + 2.5f * u * tw, y, 1, argb(0xFFF4B0, tw * rays * s.a()));
			line(g, x, y - 2.5f * u * tw, x, y + 2.5f * u * tw, 1, argb(0xFFF4B0, tw * rays * s.a()));
		}
	}

	/** The last crystal placed (the one dropping in or just landed), or 0. */
	private static int newest(Scene s) {
		int best = 0;
		float low = 3;
		for (int k = 0; k < 5; k++) if (s.fill()[k] > 0 && s.fill()[k] < low) {
			low = s.fill()[k];
			best = k;
		}
		return best;
	}

	/**
	 * The crown's turn in the finale: speeds up (700-2000 ms) to 7.6 rad/s, cruises, then from 2300
	 * slows to land facing front exactly at the climax ({@code base} + whole turns).
	 */
	private static float crownSpin(long t, float base) {
		float w0 = 0.6f, w1 = 7.6f;
		float a = angle(Math.min(t, 2300), w0, w1) + base;
		if (t <= 2300) return a;
		float speed = w1, time = 1.1f;
		float target = base + TAU * (float) Math.ceil((a - base + speed * time * 0.25f) / TAU);
		float d = target - a, k = speed * time / d; // ease-out power matched to the speed coming in
		float p = Math.min(1f, (t - 2300) / 1100f);
		return a + d * (1 - (float) Math.pow(1 - p, k));
	}

	private static float angle(long t, float w0, float w1) {
		float s = t / 1000f, ramp;
		if (t < 700) ramp = 0;
		else if (t < 2000) ramp = (t - 700) / 1000f * ((t - 700) / 1300f) / 2;
		else ramp = 1.3f / 2 + (t - 2000) / 1000f;
		return w0 * s + (w1 - w0) * ramp;
	}

	// ================= ALTAR =================

	/**
	 * An ancient ritual circle seen from above: a carved stone disc with runes, five marble pillars
	 * turning slowly round it, a crystal floating over each. Each placed crystal lights a rune line
	 * to the middle. Finale: the circle spins up, the runes ignite, a pentagram draws itself, the
	 * crystals beam into an orb over the dais, and a pillar of light erupts.
	 */
	static void altar(Scene s) {
		GuiGraphicsExtractor g = s.g();
		float u = s.u();
		boolean fin = s.finale();
		long ft = s.ft(), after = s.after();
		float pitch = 0.62f, sinP = (float) Math.sin(pitch);
		float secs = fin ? Math.min(ft, CLIMAX + 600) / 1000f : 0f;
		float spin = (float) ((s.now() / 4000.0) % TAU) + 0.75f * secs * secs; // clock modulo a turn: a float of the raw clock has no precision left
		float scale = 2.2f * u;
		float ox = s.cx(), oy = s.cy() + 14 * u;
		Matrix4f view = new Matrix4f().rotateX(pitch);
		BlockModel m = new BlockModel(TEX, 128, view, ox, oy, scale);
		Matrix4f root = new Matrix4f().rotateY(spin);
		float rp = 18, rf = 27, a = s.a();
		float charge = s.charge();
		int lit = s.placed();
		// the floor: thickness, top, carved rings
		ScathaRoulette.ellipse(g, ox, oy + 3.5f * u, rf * scale, rf * scale * sinP, argb(0x2A2632, a), argb(0x15121A, a));
		ScathaRoulette.ellipse(g, ox, oy, rf * scale, rf * scale * sinP, argb(0x5A5664, a), argb(0x34303E, a));
		glowOval(g, ox, oy, rf * scale * 0.9f, rf * scale * sinP * 0.9f, 0xB070FF, (0.1f * lit / 5f + 0.5f * charge) * a);
		int ringRgb = mix(0x7A7484, 0xD8A8FF, charge);
		for (float rr : new float[]{0.95f, 0.72f, 0.38f})
			ringOval(g, ox, oy, rf * rr * scale, rf * rr * scale * sinP, 1.1f, ringRgb, (0.55f + 0.45f * charge) * a);
		// runes round the circle, turning with it
		for (int k = 0; k < 20; k++) {
			double an = spin + k * TAU / 20;
			float rr = rf * 0.835f;
			float x = ox + (float) Math.sin(an) * rr * scale, y = oy + (float) Math.cos(an) * rr * scale * sinP;
			boolean on = k < lit * 4 || charge > k / 20f;
			int c = on ? mix(0xC77DFF, 0xFFFFFF, charge) : 0x8A8494;
			float w = 2.2f * u;
			line(g, x - w, y, x + w, y, 0.9f * u, argb(c, (on ? 1f : 0.5f) * a));
			line(g, x + ((k % 3) - 1) * w * 0.6f, y - w * 0.6f, x + ((k % 3) - 1) * w * 0.6f, y + w * 0.6f, 0.9f * u, argb(c, (on ? 1f : 0.5f) * a));
			if (on && charge > 0) glow(g, x, y, 5 * u, 0xC77DFF, 0.35f * charge * a);
		}
		// rune lines: each placed crystal's pillar to the middle
		P[] base = new P[5];
		for (int k = 0; k < 5; k++) base[k] = P.of(at(m, new Matrix4f(root).rotateY(k * STEP).translate(0, 0, rp), 0, 0, 0));
		for (int k = 0; k < 5; k++) {
			float f = s.fill()[k];
			if (f < 1) continue;
			float p = fin ? 1f : Math.min(1f, (f - 1) * 1.6f);
			float ex = base[k].x() + (ox - base[k].x()) * p, ey = base[k].y() + (oy - base[k].y()) * p;
			beam(g, base[k].x(), base[k].y(), ex, ey, 1.1f * u, COLORS[k], a);
			float dot = (s.now() % 900) / 900f * p;
			glow(g, base[k].x() + (ox - base[k].x()) * dot, base[k].y() + (oy - base[k].y()) * dot, 4 * u, COLORS[k], 0.8f * a);
		}
		// the pentagram draws itself
		float star = fin ? s.span(1500, 2300) : 0f;
		if (star > 0) {
			int[] path = {0, 2, 4, 1, 3, 0};
			float total = star * 5;
			for (int j = 0; j < 5 && j < total; j++) {
				float f = Math.min(1f, total - j);
				P p0 = base[path[j]], p1 = base[path[j + 1]];
				beam(g, p0.x(), p0.y(), p0.x() + (p1.x() - p0.x()) * f, p0.y() + (p1.y() - p0.y()) * f, 1.3f * u, 0xD8A8FF, a);
			}
		}
		// the stone: pillars and dais
		for (int k = 0; k < 5; k++) {
			Matrix4f p = new Matrix4f(root).rotateY(k * STEP).translate(0, 0, rp);
			box(m, p, -3, 0, -3, 6, 1.6f, 6, RUNE);
			box(m, p, -2.2f, 1.6f, -2.2f, 4.4f, 11, 4.4f, MARBLE);
			box(m, p, -2.6f, 6.2f, -2.6f, 5.2f, 0.8f, 5.2f, RUNE);
			box(m, p, -3, 12.6f, -3, 6, 1.6f, 6, RUNE);
			box(m, p, -1.1f, 14.2f, -1.1f, 2.2f, 1, 2.2f, OBSIDIAN);
		}
		box(m, root, -4.5f, 0, -4.5f, 9, 1.6f, 9, OBSIDIAN);
		box(m, root, -2.8f, 1.6f, -2.8f, 5.6f, 1.2f, 5.6f, RUNE);
		m.draw(g, a);
		// crystals floating over their pillars, back to front
		P[] st = new P[5];
		for (int k = 0; k < 5; k++) {
			float bob = (float) Math.sin(s.now() / 420.0 + k * 1.3) * 0.8f;
			st[k] = P.of(at(m, new Matrix4f(root).rotateY(k * STEP).translate(0, 0, rp), 0, 17.5f + bob, 0));
		}
		List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3, 4));
		order.sort(Comparator.comparingDouble(k -> st[k].depth()));
		P orb = P.of(at(m, root, 0, 26, 0));
		float beams = fin ? s.span(1500, 2500) : 0f;
		for (int k : order) {
			if (beams > 0 && s.fill()[k] >= 1) {
				float ex = st[k].x() + (orb.x() - st[k].x()) * ease(beams), ey = st[k].y() + (orb.y() - st[k].y()) * ease(beams);
				beam(g, st[k].x(), st[k].y(), ex, ey, 1.2f * u, COLORS[k], a * (after > 0 ? Math.max(0f, 1 - after / 600f) : 1f));
			}
			stone(s, k, st[k].x(), st[k].y(), 1.6f * scale, fin ? 0.3f + 0.7f * charge : 0f);
		}
		if (beams > 0 && after < 600) {
			float orbA = ease(beams) * (after > 0 ? 1 - after / 600f : 1f);
			glow(g, orb.x(), orb.y(), (8 + 22 * charge) * u, 0xD8A8FF, 0.8f * orbA * a);
			glow(g, orb.x(), orb.y(), (3 + 8 * charge) * u, 0xFFFFFF, 0.9f * orbA * a);
		}
		// the pillar of light
		float pillar = fin ? s.span(2500, CLIMAX) : 0f;
		if (pillar > 0) {
			float fade = after > 0 ? Math.max(0f, 1 - after / 1200f) : 1f;
			float w = (2 + 14 * ease(pillar) + (after > 0 ? 10 * Math.min(1f, after / 300f) : 0)) * u;
			P dais = P.of(at(m, root, 0, 2.8f, 0));
			float top = -10;
			g.fillGradient(Math.round(dais.x() - w * 1.8f), Math.round(top), Math.round(dais.x() + w * 1.8f), Math.round(dais.y()), argb(0xC77DFF, 0.05f * fade * a), argb(0xC77DFF, 0.35f * fade * a));
			g.fillGradient(Math.round(dais.x() - w), Math.round(top), Math.round(dais.x() + w), Math.round(dais.y()), argb(0xE8D0FF, 0.25f * fade * a), argb(0xE8D0FF, 0.8f * fade * a));
			g.fill(Math.round(dais.x() - w * 0.35f), Math.round(top), Math.round(dais.x() + w * 0.35f), Math.round(dais.y()), argb(0xFFFFFF, 0.9f * fade * a));
			glowOval(g, dais.x(), dais.y(), w * 3, w * 3 * sinP, 0xE8D0FF, 0.7f * fade * a);
			java.util.Random r = new java.util.Random(7);
			for (int k = 0; k < 14; k++) { // motes rising up the beam
				float d = ((s.now() + r.nextInt(1000)) % 1000) / 1000f;
				float x = dais.x() + (r.nextFloat() - 0.5f) * w * 3;
				float y = dais.y() - d * (dais.y() - top);
				g.fill(Math.round(x), Math.round(y), Math.round(x + u), Math.round(y + u), argb(0xFFFFFF, (1 - d) * fade * a));
			}
		}
	}
}
