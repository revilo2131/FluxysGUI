package cleanui.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * The Crystal Nucleus finale: each crystal you place is set into something (a gauntlet's knuckle,
 * an altar pillar, a reactor rod, a star, a crown, a golem's chest) and the fifth sets it off: a
 * build-up, a climax, and the loot bursting out. Placing crystals 1-4 shows the thing small at the
 * top of the screen with the new crystal dropping into place; the fifth plays it big.
 * <p>
 * Timeline (ms after the placement): the crystal lands at {@link #LAND}; in the finale the build-up
 * runs to {@link #CLIMAX}, then the burst until {@link #FINALE_MS}, when the loot panel takes over.
 * The scenes are drawn in {@link FinaleScenes} (2D) and {@link FinaleModels} (3D); this holds the
 * shared timing, sounds and drawing helpers.
 */
final class NucleusFinale {
	enum Style {
		GAUNTLET("Gauntlet", "THE NUCLEUS GAUNTLET", "SNAP!", 0xFFC83D),
		ALTAR("Altar", "THE ANCIENT ALTAR", "THE RITUAL IS COMPLETE", 0xC77DFF),
		REACTOR("Reactor", "NUCLEUS REACTOR", "MELTDOWN!", 0xFF5533),
		CONSTELLATION("Constellation", "THE CONSTELLATION", "THE STARS ALIGN", 0x9FD8FF),
		CROWN("Crown", "THE MINING CROWN", "KING OF THE NUCLEUS", 0xFFD54A),
		GOLEM("Golem", "THE CRYSTAL GOLEM", "THE GOLEM AWAKENS", 0x55FFAA);

		final String label, title, climax;
		final int rgb;

		Style(String label, String title, String climax, int rgb) {
			this.label = label;
			this.title = title;
			this.climax = climax;
			this.rgb = rgb;
		}
	}

	static final int[] COLORS = {0xFFAA00, 0xAA55FF, 0x55FF55, 0x55AAFF, 0xFFFF55}; // Amber, Amethyst, Jade, Sapphire, Topaz
	static final String[] NAMES = {"Amber", "Amethyst", "Jade", "Sapphire", "Topaz"};
	/** Each crystal's note (note block steps, 12 = F#4): together they make a chord. */
	static final int[] NOTE = {12, 15, 19, 22, 24};
	static final int LAND = 650, PLACE_MS = 2700, CLIMAX = 3400, FINALE_MS = 6800; // ~3 s after the climax for the best drop
	private static final java.util.Random RNG = new java.util.Random();

	private NucleusFinale() {}

	/** The setting's style, or a random one for "RANDOM" (and anything unknown). */
	static Style pick(String setting) {
		for (Style s : Style.values()) if (s.name().equals(setting)) return s;
		return Style.values()[RNG.nextInt(Style.values().length)];
	}

	/**
	 * One frame of a scene. {@code fill[i]}: crystal i is 0 = empty, 0..1 = dropping in, 1..2 = just
	 * landed (the ring pulse), 2 = set. {@code ft}: ms into the finale, or -1 while placing 1-4.
	 */
	record Scene(GuiGraphicsExtractor g, Font font, float cx, float cy, float u, float[] fill, long ft, float a, long now) {
		boolean finale() {
			return ft >= 0;
		}

		/** Build-up 0..1 from the fifth landing to the climax. */
		float charge() {
			return ft < 0 ? 0f : clamp((ft - LAND) / (float) (CLIMAX - LAND));
		}

		/** ms since the climax (negative before). */
		long after() {
			return ft < 0 ? -1 : ft - CLIMAX;
		}

		/** 0..1 over [from, to] ms of the finale (0 while placing). */
		float span(int from, int to) {
			return ft < 0 ? 0f : clamp((ft - from) / (float) (to - from));
		}

		int placed() {
			int n = 0;
			for (float f : fill) if (f >= 1) n++;
			return n;
		}
	}

	// ---- drawing entry points ----

	/** Crystal {@code newest} (1-4 placed so far) was placed {@code t} ms ago: the scene, small, at the top. */
	static void drawPlace(GuiGraphicsExtractor g, Style style, boolean[] placed, int newest, long t) {
		int sw = g.guiWidth(), sh = g.guiHeight();
		float a = Math.min(1f, t / 200f) * Math.min(1f, (PLACE_MS - t) / 400f);
		if (a <= 0) return;
		float u = unit(sw, sh) * 0.48f;
		float cy = sh * 0.27f;
		float[] fill = fills(placed, newest, t);
		Font font = Minecraft.getInstance().font;
		draw(new Scene(g, font, sw / 2f, cy, u, fill, -1, a, System.currentTimeMillis()), style);
		int n = 0;
		for (boolean p : placed) if (p) n++;
		String text = NAMES[newest].toUpperCase() + "  " + n + "/5";
		caption(g, font, text, sw / 2f, cy + 76 * u + 6, 1.4f, COLORS[newest], a);
	}

	/** The most valuable drop of the run, popped up at the climax: icon, name, "≈ 1.2M coins", rarity colour. */
	record Best(ItemStack icon, String name, String value, int rgb, int tier) {}

	/** The finale, {@code ft} ms in, big in the middle; {@code loot} bursts out at the climax and {@code best} (or null) pops up. */
	static void drawFinale(GuiGraphicsExtractor g, Style style, boolean[] placed, int last, long ft, Best best, List<ItemStack> loot, List<ItemStack> stones) {
		int sw = g.guiWidth(), sh = g.guiHeight();
		float a = Math.min(1f, ft / 200f) * Math.min(1f, (FINALE_MS - ft) / 500f);
		if (a <= 0) return;
		float u = unit(sw, sh);
		float[] fill = fills(placed, last, ft);
		Font font = Minecraft.getInstance().font;
		Scene s = new Scene(g, font, sw / 2f, sh / 2f + 4, u, fill, ft, a, System.currentTimeMillis());
		// darken the world a little so the scene glows
		g.fill(0, 0, sw, sh, Math.round(120 * a * Math.min(1f, ft / 400f)) << 24);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		float shake = shake(style, ft) * u;
		if (shake > 0) pose.translate((RNG.nextFloat() - 0.5f) * 2 * shake, (RNG.nextFloat() - 0.5f) * 2 * shake);
		draw(s, style);
		pose.popMatrix();
		// the title fades in at the top, the climax slams in after
		float titleA = a * Math.min(1f, ft / 400f) * (ft < CLIMAX ? 1f : Math.max(0f, 1 - (ft - CLIMAX) / 300f));
		if (titleA > 0) caption(g, font, style.title, sw / 2f, 14, 1.6f, style.rgb, titleA);
		if (ft >= CLIMAX) burst(s, style, loot.isEmpty() ? stones : loot);
		if (ft >= CLIMAX && best != null) popup(s, best);
	}

	private static float unit(int sw, int sh) {
		return Math.min(sw, sh * 1.25f) / 200f;
	}

	private static float[] fills(boolean[] placed, int newest, long t) {
		float[] fill = new float[5];
		for (int i = 0; i < 5; i++) fill[i] = placed[i] ? 2f : 0f;
		if (newest >= 0) fill[newest] = t < LAND ? Math.max(0.001f, t / (float) LAND) : Math.min(1.999f, 1 + (t - LAND) / 450f); // stays just under 2: still "the newest"
		return fill;
	}

	private static void draw(Scene s, Style style) {
		switch (style) {
			case GAUNTLET -> FinaleModels.gauntlet(s);
			case ALTAR -> FinaleModels.altar(s);
			case REACTOR -> FinaleScenes.reactor(s);
			case CONSTELLATION -> FinaleScenes.constellation(s);
			case CROWN -> FinaleModels.crown(s);
			case GOLEM -> FinaleModels.golem(s);
		}
	}

	/** Screen shake (in units) through the build-up and the hit. */
	private static float shake(Style style, long ft) {
		float hit = ft >= CLIMAX ? Math.max(0f, 1 - (ft - CLIMAX) / 600f) * 4f : 0f;
		float build = switch (style) {
			case GAUNTLET -> ft > 2300 && ft < CLIMAX ? 0.6f + 1.4f * (ft - 2300) / 1100f : ft > 700 && ft < 1500 ? 0.3f : 0f;
			case REACTOR -> ft > 700 && ft < 2800 ? 0.3f + 2.2f * (ft - 700) / 2100f : 0f;
			case GOLEM -> ft > 700 && ft < 1600 ? 0.8f : ft > 2300 && ft < 2700 ? 1.2f : 0f;
			case ALTAR -> ft > 2500 && ft < CLIMAX ? 1f : 0f;
			default -> 0f;
		};
		return build + hit;
	}

	// ---- the climax: flash, shockwave, the loot flying out ----

	private static void burst(Scene s, Style style, List<ItemStack> items) {
		GuiGraphicsExtractor g = s.g();
		long t = s.after();
		int sw = g.guiWidth(), sh = g.guiHeight();
		float a = s.a();
		// white flash
		if (t < 350) g.fill(0, 0, sw, sh, Math.round(230 * (1 - t / 350f) * a) << 24 | 0xFFFFFF);
		// shockwave rings in the crystal colours
		float reach = (float) Math.hypot(sw, sh) / 2;
		for (int k = 0; k < 3; k++) {
			float r = (t - k * 110) / 900f;
			if (r <= 0 || r >= 1) continue;
			ring(g, s.cx(), s.cy(), 16 + r * reach, 2.5f * (1 - r) + 0.8f, COLORS[(k * 2) % 5], (1 - r) * a);
		}
		// the loot: a fountain out of the middle, falling and spinning
		int n = items.size();
		for (int k = 0; k < n; k++) {
			float d = (t - k * 35) / 1000f;
			if (d <= 0) continue;
			double ang = -Math.PI / 2 + (n == 1 ? 0 : (k / (float) (n - 1) - 0.5) * 2.6);
			float speed = s.u() * (95 + 30 * ((k * 37) % 7) / 6f);
			float x = s.cx() + (float) Math.cos(ang) * speed * d;
			float y = s.cy() + (float) Math.sin(ang) * speed * d + 70 * s.u() * d * d;
			float size = Math.min(1f, d * 5) * (1.4f + 0.3f * ((k * 13) % 3));
			float spin = (float) Math.cos(d * 9 + k);
			float fade = Math.min(1f, (FINALE_MS - s.ft()) / 700f);
			if (fade <= 0) continue;
			glow(g, x, y, 9 * size, 0xFFFFFF, 0.25f * fade * a);
			ScathaSlots.item(g, items.get(k), x, y, size * Math.max(0.15f, Math.abs(spin)) * fade, size * fade);
		}
		// the climax words
		float slam = 1 + Math.max(0f, 1 - t / 180f) * 1.6f;
		float ca = a * Math.min(1f, (FINALE_MS - s.ft()) / 600f);
		caption(g, s.font(), style.climax, sw / 2f, sh * 0.16f, 2.6f * slam, style.rgb, ca);
	}

	/**
	 * The best drop, popped up in the middle once the flash clears: it spins in big over turning
	 * rays in its rarity colour, then its name and value slam in under it.
	 */
	private static void popup(Scene s, Best best) {
		GuiGraphicsExtractor g = s.g();
		long t = s.after() - 250;
		if (t < 0) return;
		int sw = g.guiWidth(), sh = g.guiHeight();
		float a = s.a() * Math.min(1f, t / 150f);
		float x = sw / 2f, y = sh * 0.48f, u = s.u();
		// backdrop, rays and halo in the drop's colour
		glow(g, x, y + 14 * u, 46 * u, 0x000000, 1.6f * a); // dark enough to read over any scene
		int rays = 10 + 4 * best.tier();
		for (int k = 0; k < rays; k++) {
			double an = k * Math.PI * 2 / rays + t / 1400.0;
			float len = (40 + 20 * best.tier()) * u * ease(t / 400f);
			line(g, x, y, x + (float) Math.cos(an) * len, y + (float) Math.sin(an) * len, 5 * u, argb(best.rgb(), 0.18f * a));
		}
		glow(g, x, y, 32 * u, best.rgb(), 0.6f * a);
		float ringR = ease(t / 500f);
		if (ringR < 1) ring(g, x, y, (10 + 60 * ringR) * u, 1.5f, best.rgb(), (1 - ringR) * a);
		// the item: pops from nothing past full size and settles, spinning to face you
		float pop = Math.min(1f, t / 350f);
		float size = 4.2f * (pop < 1 ? pop * (1 + 0.35f * (float) Math.sin(pop * Math.PI)) : 1f);
		float spin = (float) Math.cos(Math.max(0, 1 - t / 700f) * 7);
		float bob = (float) Math.sin(t / 380.0) * 2;
		ScathaSlots.item(g, best.icon(), x, y + bob, size * Math.max(0.15f, Math.abs(spin)), size);
		// name and value
		if (t > 450) {
			long tt = t - 450;
			float slam = 1 + Math.max(0f, 1 - tt / 160f) * 1.2f;
			caption(g, s.font(), best.name(), x, y + 26 * u, 1.8f * slam, best.rgb(), a);
			if (tt > 200) caption(g, s.font(), best.value(), x, y + 26 * u + 20, 1.3f, 0xFFE38A, a * Math.min(1f, (tt - 200) / 200f));
		}
	}

	// ---- shared drawing helpers ----

	static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	static float ease(float v) {
		v = clamp(v);
		return v * v * (3 - 2 * v);
	}

	static int argb(int rgb, float a) {
		return Math.max(0, Math.min(255, Math.round(255 * a))) << 24 | (rgb & 0xFFFFFF);
	}

	static int mix(int a, int b, float t) {
		t = clamp(t);
		int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}

	/** A soft round glow: stacked see-through circles. */
	static void glow(GuiGraphicsExtractor g, float x, float y, float r, int rgb, float a) {
		if (a <= 0.01f || r < 1) return;
		for (int k = 5; k >= 1; k--) {
			float rr = r * k / 5f;
			int c = argb(rgb, a * 0.22f);
			ScathaRoulette.ellipse(g, x, y, rr, rr, c, c);
		}
	}

	/** A soft glow squashed into an ellipse (for floors seen at an angle). */
	static void glowOval(GuiGraphicsExtractor g, float x, float y, float rx, float ry, int rgb, float a) {
		if (a <= 0.01f || rx < 1) return;
		for (int k = 5; k >= 1; k--) {
			int c = argb(rgb, a * 0.22f);
			ScathaRoulette.ellipse(g, x, y, rx * k / 5f, ry * k / 5f, c, c);
		}
	}

	/** A straight line of any width between two points. */
	static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float w, int argb) {
		float dx = x2 - x1, dy = y2 - y1, len = (float) Math.hypot(dx, dy);
		if (len < 0.1f || (argb >>> 24) == 0) return;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x1, y1);
		pose.rotate((float) Math.atan2(dy, dx));
		pose.translate(0, -w / 2);
		pose.scale(len / 64f, w / 8f);
		g.fill(0, 0, 64, 8, argb);
		pose.popMatrix();
	}

	/** A glowing beam: wide faint colour, a narrower brighter one, a white core. */
	static void beam(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float w, int rgb, float a) {
		line(g, x1, y1, x2, y2, w * 3, argb(rgb, 0.18f * a));
		line(g, x1, y1, x2, y2, w * 1.6f, argb(rgb, 0.5f * a));
		line(g, x1, y1, x2, y2, Math.max(0.6f, w * 0.5f), argb(0xFFFFFF, 0.85f * a));
	}

	/** Lightning between two points: a jagged line that re-forms every 70 ms. */
	static void arc(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int seed, long now, int rgb, float a) {
		if (a <= 0.01f) return;
		java.util.Random r = new java.util.Random(seed * 31L + now / 70);
		int segs = 7;
		float len = (float) Math.hypot(x2 - x1, y2 - y1), nx = -(y2 - y1) / Math.max(1, len), ny = (x2 - x1) / Math.max(1, len);
		float px = x1, py = y1;
		for (int k = 1; k <= segs; k++) {
			float f = k / (float) segs;
			float off = k == segs ? 0 : (r.nextFloat() - 0.5f) * len * 0.22f;
			float qx = x1 + (x2 - x1) * f + nx * off, qy = y1 + (y2 - y1) * f + ny * off;
			line(g, px, py, qx, qy, 3.2f, argb(rgb, 0.3f * a));
			line(g, px, py, qx, qy, 1.1f, argb(mix(rgb, 0xFFFFFF, 0.7f), a));
			px = qx;
			py = qy;
		}
	}

	/** A thin circle outline from short dashes. */
	static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, float w, int rgb, float a) {
		ringOval(g, cx, cy, r, r, w, rgb, a);
	}

	static void ringOval(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, float w, int rgb, float a) {
		if (a <= 0.01f) return;
		int n = Math.max(24, Math.min(96, Math.round(Math.max(rx, ry) / 2.5f)));
		int c = argb(rgb, a);
		for (int k = 0; k < n; k++) {
			double a0 = k * Math.PI * 2 / n, a1 = (k + 1) * Math.PI * 2 / n;
			line(g, cx + (float) Math.cos(a0) * rx, cy + (float) Math.sin(a0) * ry, cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry, w, c);
		}
	}

	/**
	 * A cut gem in crystal i's colour, {@code r} px: dark rim, four lit facets, a bright table and a
	 * glint; {@code glow} 0..1 adds a halo and a twinkle.
	 */
	static void gem(GuiGraphicsExtractor g, int i, float x, float y, float r, float glow, float a, long now) {
		int rgb = COLORS[i];
		glow(g, x, y, r * (2.2f + 1.4f * glow), rgb, (0.25f + 0.55f * glow) * a);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(r / 6f, r / 6f * 1.18f);
		pose.rotate((float) (Math.PI / 4));
		g.fill(-5, -5, 5, 5, argb(mix(rgb, 0x000000, 0.65f), a));                         // rim
		g.fill(-4, -4, 0, 0, argb(mix(rgb, 0xFFFFFF, 0.45f), a));                         // top facet
		g.fill(0, -4, 4, 0, argb(mix(rgb, 0xFFFFFF, 0.1f), a));                           // right
		g.fill(-4, 0, 0, 4, argb(mix(rgb, 0xFFFFFF, 0.22f), a));                          // left
		g.fill(0, 0, 4, 4, argb(mix(rgb, 0x000000, 0.35f), a));                           // bottom
		g.fill(-2, -2, 2, 2, argb(mix(rgb, 0xFFFFFF, 0.3f + 0.4f * glow), a));            // table
		g.fill(-3, -3, -1, -2, argb(0xFFFFFF, 0.9f * a));                                // glint
		pose.popMatrix();
		if (glow > 0.3f) { // a four-point twinkle
			float tw = (0.6f + 0.4f * (float) Math.sin(now / 120.0 + i * 1.7)) * glow;
			float len = r * (1.2f + 1.6f * tw);
			int c = argb(0xFFFFFF, 0.75f * tw * a);
			line(g, x - len, y, x + len, y, 1f, c);
			line(g, x, y - len, x, y + len, 1f, c);
		}
	}

	/** An empty socket: a dark hole with a lit lower lip. */
	static void socket(GuiGraphicsExtractor g, float x, float y, float r, float a) {
		ScathaRoulette.ellipse(g, x, y, r * 1.15f, r * 1.15f, argb(0x3A3020, a), argb(0xC8A060, a));
		ScathaRoulette.ellipse(g, x, y, r * 0.9f, r * 0.9f, argb(0x050308, a), argb(0x1A1420, a));
	}

	/**
	 * Crystal i in its place at (x, y), by its fill: empty socket, dropping in from above with a
	 * trail, the landing ring, then set and glowing ({@code glow} on top of the base shine).
	 */
	static void stone(Scene s, int i, float x, float y, float r, float glow) {
		GuiGraphicsExtractor g = s.g();
		float f = s.fill()[i];
		socket(g, x, y, r, s.a());
		if (f <= 0) return;
		if (f < 1) {
			float e = 1 - (1 - f) * (1 - f);
			float drop = (1 - e) * 70 * s.u();
			for (int k = 1; k <= 4; k++) // the trail
				gem(g, i, x, y - drop - k * 5 * s.u() * (1 - e), r * (1 - k * 0.16f), 0, s.a() * 0.18f * (5 - k) / 4f, s.now());
			gem(g, i, x, y - drop, r * (1 + 0.6f * (1 - e)), 0.6f, s.a(), s.now());
			return;
		}
		float pulse = f < 2 ? 2 - f : 0; // 1 → 0 right after landing
		if (pulse > 0) {
			ring(g, x, y, r * (1.3f + 3.5f * (1 - pulse)), 1.5f, COLORS[i], pulse * s.a());
			glow(g, x, y, r * 5 * (1 - pulse * 0.5f), COLORS[i], 0.5f * pulse * s.a());
		}
		gem(g, i, x, y, r, Math.min(1f, 0.35f + glow + pulse * 0.6f), s.a(), s.now());
	}

	static void caption(GuiGraphicsExtractor g, Font font, String text, float x, float y, float size, int rgb, float a) {
		if (a <= 0.02f) return;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size, size);
		g.text(font, text, -font.width(text) / 2, 0, argb(rgb, a), true);
		pose.popMatrix();
	}

	// ---- sounds: cues run once per placement / finale ----

	private static long cueBits;
	private static int cueIndex;

	/** A new placement or finale: its cues can play again. */
	static void resetCues() {
		cueBits = 0;
	}

	private static boolean cue(long t, int at) {
		long bit = 1L << cueIndex++;
		if (t < at || (cueBits & bit) != 0) return false;
		cueBits |= bit;
		return t - at < 1500; // joined late (preview, lag): don't fire a pile of stale sounds
	}

	private static void s(SoundEvent e, float pitch, float vol) {
		ScathaSlots.sound(e, pitch, vol);
	}

	private static void h(Holder<SoundEvent> e, float pitch, float vol) {
		ScathaSlots.play(e, pitch, vol);
	}

	private static void note(Holder<SoundEvent> e, int step, float vol) {
		h(e, (float) Math.pow(2, (Math.max(0, Math.min(24, step)) - 12) / 12.0), vol);
	}

	private static void chord(Holder<SoundEvent> e, int shift, float vol) {
		for (int n : NOTE) note(e, n + shift, vol);
	}

	/** Placement sounds: the crystal landing, in the scene's own voice. */
	static void tickPlace(Style style, int crystal, long t) {
		cueIndex = 0;
		if (!cue(t, LAND)) return;
		note(SoundEvents.NOTE_BLOCK_BELL, NOTE[crystal], 0.8f);
		switch (style) {
			case GAUNTLET -> {
				h(SoundEvents.ARMOR_EQUIP_GOLD, 1.2f, 1f);
				s(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 1f);
			}
			case ALTAR -> {
				s(SoundEvents.END_PORTAL_FRAME_FILL, 1f, 1f);
				s(SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f, 0.7f);
			}
			case REACTOR -> {
				s(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1f, 0.9f);
				s(SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.5f);
			}
			case CONSTELLATION -> {
				s(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.3f, 0.7f);
				note(SoundEvents.NOTE_BLOCK_CHIME, NOTE[crystal], 0.7f);
			}
			case CROWN -> {
				s(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1f);
				s(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.1f, 0.7f);
			}
			case GOLEM -> {
				s(SoundEvents.HEAVY_CORE_PLACE, 0.8f, 1f);
				s(SoundEvents.STONE_PLACE, 0.6f, 1f);
			}
		}
	}

	/** The finale's sounds, {@code t} ms in. */
	static void tickFinale(Style style, long t) {
		cueIndex = 0;
		if (cue(t, LAND)) {
			chord(SoundEvents.NOTE_BLOCK_BELL, 0, 0.6f);
			s(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1f);
		}
		switch (style) {
			case GAUNTLET -> {
				if (cue(t, LAND)) h(SoundEvents.ARMOR_EQUIP_GOLD, 1f, 1f);
				if (cue(t, 750)) s(SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.8f);
				for (int k = 0; k < 4; k++) if (cue(t, 800 + k * 230)) s(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.5f + k * 0.1f, 0.5f);
				if (cue(t, 1500)) s(SoundEvents.IRON_GOLEM_REPAIR, 0.6f, 0.9f);
				if (cue(t, 2300)) {
					h(SoundEvents.ARMOR_EQUIP_GOLD, 0.7f, 1f);
					s(SoundEvents.BEACON_ACTIVATE, 0.6f, 1f);
				}
				for (int k = 0; k < 5; k++) if (cue(t, 2450 + k * 170)) note(SoundEvents.NOTE_BLOCK_PLING, NOTE[k], 0.7f);
				if (cue(t, CLIMAX)) {
					note(SoundEvents.NOTE_BLOCK_SNARE, 24, 1f);
					s(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.3f, 0.7f);
					h(SoundEvents.GENERIC_EXPLODE, 1.2f, 0.7f);
					s(SoundEvents.TOTEM_USE, 1.2f, 0.6f);
				}
			}
			case ALTAR -> {
				if (cue(t, LAND)) s(SoundEvents.END_PORTAL_FRAME_FILL, 0.9f, 1f);
				if (cue(t, 750)) s(SoundEvents.EVOKER_PREPARE_SUMMON, 0.8f, 0.9f);
				if (cue(t, 1500)) {
					s(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1f);
					s(SoundEvents.BELL_RESONATE, 1f, 0.8f);
				}
				if (cue(t, 2000)) s(SoundEvents.PORTAL_TRIGGER, 1.6f, 0.5f);
				if (cue(t, 2500)) {
					s(SoundEvents.BEACON_ACTIVATE, 0.8f, 1f);
					s(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 0.8f);
				}
				if (cue(t, CLIMAX)) {
					s(SoundEvents.END_PORTAL_SPAWN, 1f, 0.8f);
					s(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 0.5f);
				}
			}
			case REACTOR -> {
				if (cue(t, LAND)) s(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.8f, 1f);
				for (int k = 0; k < 9; k++) if (cue(t, 700 + k * 240)) note(SoundEvents.NOTE_BLOCK_BIT, k % 2 == 0 ? 19 : 14, 0.6f); // the alarm
				for (int k = 0; k < 3; k++) if (cue(t, 750 + k * 650)) s(SoundEvents.BEACON_AMBIENT, 1f + k * 0.4f, 1f);
				if (cue(t, 2000)) s(SoundEvents.GLASS_BREAK, 0.7f, 0.9f);
				if (cue(t, 2800)) s(SoundEvents.BEACON_DEACTIVATE, 0.5f, 1f);
				if (cue(t, CLIMAX)) {
					h(SoundEvents.GENERIC_EXPLODE, 0.7f, 1f);
					s(SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.8f, 0.8f);
					s(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 0.8f);
				}
			}
			case CONSTELLATION -> {
				if (cue(t, LAND)) s(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.2f, 0.8f);
				for (int k = 0; k < 5; k++) if (cue(t, 750 + k * 140)) note(SoundEvents.NOTE_BLOCK_CHIME, NOTE[k], 0.7f);
				if (cue(t, 1500)) s(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1f);
				if (cue(t, 2300)) s(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8f, 0.8f);
				if (cue(t, 3150)) s(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1f);
				if (cue(t, CLIMAX)) {
					s(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
					s(SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, 1f, 1f);
				}
			}
			case CROWN -> {
				if (cue(t, 750)) s(SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.8f);
				for (int k = 0; k < 3; k++)
					if (cue(t, 900 + k * 650)) {
						s(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f + k * 0.2f, 1f);
						chord(SoundEvents.NOTE_BLOCK_FLUTE, -12 + k * 3, 0.35f);
					}
				if (cue(t, 3000)) s(SoundEvents.BELL_BLOCK, 1f, 1f);
				if (cue(t, CLIMAX)) {
					s(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
					s(SoundEvents.TOTEM_USE, 1.3f, 0.6f);
					s(SoundEvents.PLAYER_LEVELUP, 1f, 0.8f);
				}
			}
			case GOLEM -> {
				if (cue(t, LAND)) s(SoundEvents.HEAVY_CORE_PLACE, 0.7f, 1f);
				for (int k = 0; k < 3; k++) if (cue(t, 750 + k * 300)) s(SoundEvents.DEEPSLATE_BREAK, 0.5f + k * 0.05f, 1f);
				for (int k = 0; k < 2; k++) if (cue(t, 900 + k * 450)) s(SoundEvents.WARDEN_HEARTBEAT, 1f, 1f);
				if (cue(t, 1600)) s(SoundEvents.BEACON_ACTIVATE, 0.6f, 1f);
				if (cue(t, 2300)) {
					s(SoundEvents.RAVAGER_ROAR, 0.7f, 1f);
					s(SoundEvents.WARDEN_ROAR, 1f, 0.5f);
				}
				if (cue(t, 3250)) s(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.8f, 1f);
				if (cue(t, CLIMAX)) {
					h(SoundEvents.GENERIC_EXPLODE, 0.6f, 1f);
					s(SoundEvents.IRON_GOLEM_ATTACK, 0.6f, 1f);
				}
			}
		}
		if (cue(t, CLIMAX)) ScathaSlots.confettiOnly(2);
		if (cue(t, CLIMAX + 250)) chord(SoundEvents.NOTE_BLOCK_BELL, 0, 0.6f);
	}
}
