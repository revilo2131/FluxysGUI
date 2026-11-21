package cleanui.feature;

import cleanui.data.Config;
import cleanui.data.SkyblockItems;
import cleanui.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Vanguard corpse loot: a blocky 3D ouroboros (the snake biting its own tail) spins in a ring for
 * 3 s. With a rare drop it stops head-up, a katana slashes down through the bite, the two halves
 * fall apart and the drops burst out of the middle. Without one it spins 3 s more while its eyes
 * slowly glow red, then fades. Drawn on the HUD (in place of it, over a blurred world, with "Blur
 * while rolling" on), so you can keep moving.
 */
public final class VanguardSnake {
	private static final Identifier TEX = Identifier.fromNamespaceAndPath("cleanui", "textures/gui/ouroboros.png");
	/** The rare Vanguard corpse drops, rarest first: name (item list name for the icon), Hypixel id (for its price), chat pattern. */
	record Drop(String name, String id, Pattern chat) {}

	static final List<Drop> DROPS = List.of(
			new Drop("Frostbitten Dye", "DYE_FROSTBITTEN", Pattern.compile("Frostbitten Dye")),
			new Drop("Shattered Locket", "SHATTERED_PENDANT", Pattern.compile("Shattered Locket")),
			new Drop("Caged Wisp", "CAGED_WISP", Pattern.compile("Caged Wisp")),
			new Drop("Skeleton Key", "SKELETON_KEY", Pattern.compile("Skeleton Key(?!chain)")));

	// ms after the loot message
	private static final int DECIDE = 300, SPIN_END = 3000, SLASH_MS = 450, CUT = 3320, SPLIT_MS = 650, POP = 3550;
	private static final int END_WIN = 9500, END_LOSE = 6000, FADE_MS = 500, GLOW_MS = 900, DISSOLVE_MS = 1100;
	private static final float R = 26; // ring radius, model units
	private static final int SEGMENTS = 30;
	private static final double SPEED = 2 * Math.PI / 2200; // radians per ms: a turn every 2.2 s
	private static final double THETA_FROM = 0.16, THETA_TO = 2 * Math.PI - 0.1; // the body, from behind the head round to the tail tip

	private static long start = -1;
	private static final List<Drop> got = new ArrayList<>();
	private static double stopAt; // win: total turn, a whole number of turns, so the head ends at the top
	private static int stage; // sounds already played
	private static boolean lock; // this corpse plays the lockpick (VanguardLock), not the snake

	private VanguardSnake() {}

	// ---- chat ----

	/** The corpse loot message starts it; drops named in it (or right after it) make it a win. */
	public static void onChat(Component message) {
		if (!Config.get().vanguardSnake) return;
		String s = ChatFormatting.stripFormatting(message.getString());
		long now = System.currentTimeMillis();
		// "VANGUARD CORPSE LOOT!" heads the rewards (exact case: SkyHanni's "Profit for Vanguard Corpse" mustn't start it)
		if (s.contains("VANGUARD CORPSE LOOT") && (start < 0 || now - start > 1500)) begin(now);
		if (start >= 0 && now - start < DECIDE + 700)
			for (Drop d : DROPS) if (!got.contains(d) && d.chat().matcher(s).find()) got.add(d);
	}

	private static void begin(long now) {
		begin(now, Config.get().vanguardLockpick);
	}

	/** {@code lockpick}: this corpse plays the lock being picked instead of the snake. */
	private static void begin(long now, boolean lockpick) {
		start = now;
		got.clear();
		stage = 0;
		lock = lockpick;
		VanguardLock.reset();
		for (Drop d : DROPS) cleanui.data.Networth.price(d.id()); // start loading the values now
	}

	/** /testvanguard [dye|locket|wisp|key]: -1 = no drop, else that drop (index into DROPS). */
	public static void test(int drop) {
		begin(System.currentTimeMillis());
		if (drop >= 0) got.add(DROPS.get(drop));
	}

	/** /testlock [dye|locket|wisp|key]: the lockpick, whatever the setting. */
	public static void testLock(int drop) {
		begin(System.currentTimeMillis(), true);
		if (drop >= 0) got.add(DROPS.get(drop));
	}

	/** Preview: started {@code msAgo} ago, with these drops (indexes into the list). */
	public static void preview(long msAgo, int... drops) {
		begin(System.currentTimeMillis() - msAgo, false);
		for (int i : drops) got.add(DROPS.get(i));
	}

	/** Preview of the lockpick; {@code noDropPins} sets how far a run without a drop gets. */
	public static void previewLock(long msAgo, int noDropPins, int... drops) {
		begin(System.currentTimeMillis() - msAgo, true);
		VanguardLock.forcePins(noDropPins);
		for (int i : drops) got.add(DROPS.get(i));
	}

	private static boolean win() {
		return !got.isEmpty();
	}

	private static long now() {
		return System.currentTimeMillis() - start;
	}

	private static long end() {
		if (lock) return VanguardLock.end(got);
		return win() ? END_WIN : END_LOSE;
	}

	/**
	 * Each rare drop's own sound as it pops out. Wisp / key: a bright magic sparkle. Locket: menacing
	 * (the Elder Guardian curse, a low growl, deep bass). Dye: heavenly (a beacon swell and a high
	 * bell and chime chord shimmering over it).
	 */
	static void dropSound(Drop d) {
		if (d == null) return;
		switch (d.name()) {
			case "Frostbitten Dye" -> {
				ScathaSlots.sound(SoundEvents.BEACON_ACTIVATE, 1.3f, 1f);
				ScathaSlots.sound(SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.7f);
				ScathaSlots.sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.8f, 0.8f);
				for (int n : new int[]{18, 22, 25}) { // a high major chord, bells over chimes
					ScathaSlots.play(SoundEvents.NOTE_BLOCK_BELL, step(Math.min(24, n)), 0.7f);
					ScathaSlots.play(SoundEvents.NOTE_BLOCK_CHIME, step(Math.min(24, n - 12)), 0.5f);
				}
			}
			case "Shattered Locket" -> {
				ScathaSlots.sound(SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.9f);
				ScathaSlots.sound(SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 0.45f);
				ScathaSlots.sound(SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 0.6f);
				ScathaSlots.play(SoundEvents.NOTE_BLOCK_BASS, step(0), 1f);
				ScathaSlots.play(SoundEvents.NOTE_BLOCK_BASS, step(1), 0.8f); // a semitone clash
			}
			default -> { // Caged Wisp, Skeleton Key
				ScathaSlots.sound(SoundEvents.ALLAY_ITEM_GIVEN, 1.2f, 1f);
				ScathaSlots.sound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.5f, 0.8f);
				ScathaSlots.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f, 0.7f);
				ScathaSlots.play(SoundEvents.NOTE_BLOCK_CHIME, step(19), 0.7f);
			}
		}
	}

	/** Note block pitch for a step (12 = F#4). */
	private static float step(int n) {
		return (float) Math.pow(2, (n - 12) / 12.0);
	}

	/** A drop's value from the price list, like "≈ 400M coins", or null if not known (yet). */
	static String value(Drop d) {
		Double p = cleanui.data.Networth.price(d.id());
		return p == null || p <= 0 ? null : "≈ " + cleanui.skyblock.SkillParser.compact(String.valueOf(Math.round(p))) + " coins";
	}

	/** Swapped in for the whole HUD over a blurred world while it runs (HudMixin). */
	public static boolean takeover() {
		return start >= 0 && Config.get().scathaFocus;
	}

	// ---- motion ----

	/** How far the ring has turned. A win eases to a stop with the head at the top by SPIN_END. */
	private static double spin(long t) {
		if (!win() || t < DECIDE) return SPEED * t;
		double at = SPEED * DECIDE;
		if (stopAt == 0) stopAt = Math.ceil((at + SPEED * (SPIN_END - DECIDE) / 2.2) / (2 * Math.PI)) * 2 * Math.PI - cutAngle(); // bite on the diagonal
		double s = Math.min(1, (t - DECIDE) / (double) (SPIN_END - DECIDE));
		return at + (stopAt - at) * (1 - Math.pow(1 - s, 3));
	}

	public static void tick(Minecraft mc) {
		if (start < 0) return;
		long t = now();
		if (t > end()) {
			start = -1;
			stopAt = 0;
			return;
		}
		if (lock) {
			VanguardLock.tick(t, got);
			return;
		}
		if (stage == 0 && t >= 0) {
			stage = 1;
			// the Wither spawn, cut to ~3 s: quick fade in, fade out at the end
			if (Config.get().scathaSounds) mc.getSoundManager().play(new FadingSound(SoundEvents.WITHER_SPAWN, 1f, 0.9f, 60, 3, 12));
		}
		if (win()) {
			if (stage == 1 && t >= SPIN_END) {
				stage = 2;
				ScathaSlots.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1f);
			}
			if (stage == 2 && t >= CUT) {
				stage = 3;
				ScathaSlots.sound(SoundEvents.WITHER_DEATH, 1f, 0.9f);
				ScathaSlots.sound(SoundEvents.PLAYER_ATTACK_CRIT, 0.7f, 1f);
			}
			if (stage == 3 && t >= POP) {
				stage = 4;
				ScathaSlots.confettiOnly(2);
				dropSound(VanguardLock.best(got));
			}
		} else {
			// sad trombone: four notes sliding down, the last one wobbling and fading
			while (stage - 1 < SAD.length && t >= SPIN_END + SAD[stage - 1][0]) {
				int[] n = SAD[stage - 1];
				ScathaSlots.play(SoundEvents.NOTE_BLOCK_DIDGERIDOO, (float) Math.pow(2, (n[1] - 12) / 12.0), n[2] / 100f);
				ScathaSlots.play(SoundEvents.NOTE_BLOCK_BASS, (float) Math.pow(2, (n[1] - 12) / 12.0), n[2] / 160f);
				stage++;
			}
		}
	}

	/** Sad trombone after the spin when nothing dropped: {ms after SPIN_END, note step (12 = F#4), volume %}. */
	private static final int[][] SAD = {{0, 13, 90}, {420, 12, 90}, {840, 11, 90}, {1260, 10, 95}, {1460, 10, 70}, {1660, 10, 50}, {1860, 10, 30}};

	// ---- drawing ----

	public static void render(GuiGraphicsExtractor g) {
		if (start < 0) return;
		long t = now(), e = end();
		if (t > e) return;
		if (lock) {
			VanguardLock.render(g, t, e, got);
			return;
		}
		Font font = Minecraft.getInstance().font;
		int sw = g.guiWidth(), sh = g.guiHeight();
		float alpha = Math.min(1f, t / 250f) * Math.min(1f, (e - t) / (float) FADE_MS);
		float base = Math.min(sh, sw) * 0.55f / (2 * (R + 6));
		// the cut lands with a zoom punch and a shake
		float hit = win() && t >= CUT ? (float) Math.exp(-(t - CUT) / 140.0) : 0f;
		float scale = base * (1 + 0.12f * hit);
		float shakeX = hit * 7 * (float) Math.sin(t * 0.9), shakeY = hit * 5 * (float) Math.cos(t * 1.3);
		float cx = sw / 2f + shakeX, cy = sh / 2f - 10 + shakeY, ringR = (R + 3) * scale;
		// eyes glow / aura turns red once it's clear nothing dropped
		float dread = !win() && t >= SPIN_END ? smooth((t - SPIN_END) / (float) GLOW_MS) : 0f;

		vignette(g, sw, sh, alpha, dread);
		aura(g, cx, cy, ringR, t, alpha, dread);

		// cut: the halves slide apart, tilt out and keep drifting while the drops shine
		float split = win() && t >= CUT ? Math.min(1f, (t - CUT) / (float) SPLIT_MS) : 0f;
		float ease = 1 - (1 - split) * (1 - split), drift = win() && t > CUT + SPLIT_MS ? (t - CUT - SPLIT_MS) / 1000f : 0f;
		float sep = 13 * ease + 4 * drift, tilt = 0.45f * ease + 0.08f * drift, fall = 6 * ease * ease + 5 * drift * drift;

		float sway = (float) Math.sin(t / 700.0) * 0.35f * (1 - split * 0.6f);
		Matrix4f view = new Matrix4f().rotateX(0.22f).rotateY(sway);
		BlockModel m = new BlockModel(TEX, 64, view, cx, cy, scale);
		Matrix4f root = new Matrix4f().rotateZ((float) spin(t)); // counter-clockwise: head first
		Matrix4f right = new Matrix4f(root).translate(sep, -fall, 0).rotateZ(-tilt);
		Matrix4f left = new Matrix4f(root).translate(-sep, -fall, 0).rotateZ(tilt);

		// no drop: in its last moments the snake crumbles away from the tail to the head
		float crumble = !win() && t > END_LOSE - DISSOLVE_MS ? (t - (END_LOSE - DISSOLVE_MS)) / (float) DISSOLVE_MS : 0f;
		// the body, round the ring from behind the head to the tail tip in the mouth, thinning to the tail;
		// it builds itself in from the head round to the tail as it appears
		double step = (THETA_TO - THETA_FROM) / SEGMENTS;
		for (int k = 0; k < SEGMENTS; k++) {
			float appear = smooth((t - 60 - k * 14) / 180f);
			float gone = Math.max(0f, Math.min(1f, crumble * 1.8f - (1 - k / (float) SEGMENTS) * 0.8f));
			float size = appear * (1 - gone);
			if (size <= 0.02f) continue;
			double th = THETA_FROM + step * (k + 0.5);
			float thick = (1f - 0.58f * (float) Math.pow(k / (float) SEGMENTS, 1.5)) * size;
			float len = (float) (step * R) * (1.18f - 0.1f * (1 - thick)) * size;
			Matrix4f half = th < Math.PI ? right : left; // the head side goes right, the tail side left
			Matrix4f seg = new Matrix4f(half).translate(R * (float) Math.sin(th), R * (float) Math.cos(th), 0).rotateZ((float) -th).scale(1, thick, thick);
			m.box(seg, -len / 2, -3, -3, len, 6, 6, 0, 0, 6, 6, 6, 0);
		}
		// head at the top facing left (toward the tail), jaws open round the tail tip, fangs down
		float headSize = smooth(t / 200f) * (1 - Math.max(0f, Math.min(1f, crumble * 1.8f - 0.8f)));
		Matrix4f head = new Matrix4f(right).translate(0, R, 0).scale(Math.max(0.01f, headSize));
		Matrix4f upper = new Matrix4f(head).translate(3, -1, 0).rotateZ(-0.28f).translate(-3, 1, 0); // hinged at the back of the head
		Matrix4f jaw = new Matrix4f(head).translate(3, -1, 0).rotateZ(0.38f).translate(-3, 1, 0);
		m.box(upper, -6, -3, -4.5f, 10, 7, 9, 0, 16, 0);
		m.box(jaw, -5, -5.5f, -4, 9, 2, 8, 0, 40, 0);
		m.box(upper, -4.6f, -5.6f, 2.2f, 1, 3, 1, 40, 40, 0);
		m.box(upper, -4.6f, -5.6f, -3.2f, 1, 3, 1, 40, 40, 0);
		float[] eye = m.project(upper, -3.5f, 2f, 4.7f);
		// aim the slash through where the bite really is on screen (it stops near the diagonal; the 3D tilt moves it a little)
		float[] bite = m.project(root, R * (float) Math.sin(THETA_TO), R * (float) Math.cos(THETA_TO), 0);
		float bl = (float) Math.hypot(bite[0] - cx, bite[1] - cy);
		float[] dir = bl < 1 ? cutDir(sw, sh) : new float[]{(bite[0] - cx) / bl, (bite[1] - cy) / bl};

		if (win() && t >= CUT) drawCut(g, cx, cy, ringR, t, dir);
		m.draw(g, alpha);
		if (!win()) {
			drawGlow(g, eye, scale, t, alpha * (1 - crumble), dread);
			if (crumble > 0) ash(g, cx, cy, ringR, t, crumble);
			if (t > SPIN_END + 500) caption(g, font, "nothing this time...", cx, cy + ringR + 26, 1.2f, 0xAAAAAA,
					alpha * Math.min(1f, (t - SPIN_END - 500) / 600f));
		}
		if (win() && t >= SPIN_END && t < SPIN_END + SLASH_MS + 160) drawKatana(g, cx, cy, ringR, t, dir);
		if (win() && t >= POP) drawDrops(g, font, cx, cy, ringR, scale, t, alpha);
	}

	private static float smooth(float k) {
		k = Math.max(0f, Math.min(1f, k));
		return k * k * (3 - 2 * k);
	}

	/** Dark bands at the top and bottom of the screen; pulsing red when nothing dropped. */
	private static void vignette(GuiGraphicsExtractor g, int sw, int sh, float alpha, float dread) {
		int band = sh / 3, dark = Math.round(150 * alpha) << 24;
		g.fillGradient(0, 0, sw, band, dark, 0);
		g.fillGradient(0, sh - band, sw, sh, 0, dark);
		if (dread > 0) {
			int red = Math.round(55 * dread * alpha * (0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 220.0)));
			g.fill(0, 0, sw, sh, red << 24 | 0x500000);
		}
	}

	/**
	 * Behind the snake: a soft glacite glow, a ring of frost lights turning the other way, and
	 * snowflakes orbiting and drifting (all from time, no state). Turns blood red with dread.
	 */
	private static void aura(GuiGraphicsExtractor g, float cx, float cy, float ringR, long t, float alpha, float dread) {
		int ice = Theme.mix(0x8FE3FF, 0xFF2A2A, dread), white = Theme.mix(0xFFFFFF, 0xFFB0B0, dread);
		ScathaRoulette.ellipse(g, cx, cy, ringR * 1.45f, ringR * 1.45f, Math.round(46 * alpha) << 24 | ice, Math.round(18 * alpha) << 24 | ice);
		double turn = -t / 2600.0;
		for (int i = 0; i < 56; i++) {
			double a = turn + i * Math.PI * 2 / 56;
			float r = ringR * 1.2f, x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
			int on = Math.round((i % 4 == 0 ? 200 : 90) * alpha);
			g.fill(Math.round(x) - 1, Math.round(y) - 1, Math.round(x) + 1, Math.round(y) + 1, on << 24 | (i % 4 == 0 ? white : ice));
		}
		for (int i = 0; i < 30; i++) {
			double a = i * 2.4 + t / 1600.0 * (i % 2 == 0 ? 1 : -0.7);
			float r = ringR * (1.05f + 0.6f * ((i * 37) % 10) / 10f) + 6 * (float) Math.sin(t / 500.0 + i);
			float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r * 0.9f;
			int fa = Math.round(alpha * (140 + 100 * (float) Math.sin(t / 300.0 + i * 1.7)) / 1.2f);
			int c = Math.max(0, fa) << 24 | white;
			g.fill(Math.round(x) - 2, Math.round(y), Math.round(x) + 3, Math.round(y) + 1, c); // a little snowflake
			g.fill(Math.round(x), Math.round(y) - 2, Math.round(x) + 1, Math.round(y) + 3, c);
		}
	}

	/** Red eye glow fading in once it's clear there's no drop, pulsing slowly. */
	private static void drawGlow(GuiGraphicsExtractor g, float[] eye, float scale, long t, float alpha, float dread) {
		if (dread <= 0) return;
		float glow = dread * (0.8f + 0.2f * (float) Math.sin(t / 180.0)) * alpha;
		for (int i = 7; i >= 1; i--) {
			float r = i * 2.4f * scale / 3f;
			int a = Math.round(glow * (i == 1 ? 255 : 80f / i));
			ScathaRoulette.ellipse(g, eye[0], eye[1], r, r, a << 24 | (i == 1 ? 0xFFE0E0 : 0xFF2020), a << 24 | 0xFF2020);
		}
		// a red streak trailing from the eye
		int a = Math.round(140 * glow);
		g.fillGradient(Math.round(eye[0]) - 1, Math.round(eye[1]), Math.round(eye[0]) + 1, Math.round(eye[1] + 10 * scale), a << 24 | 0xFF3030, 0);
	}

	/** Embers rising off the ring as it crumbles. */
	private static void ash(GuiGraphicsExtractor g, float cx, float cy, float ringR, long t, float crumble) {
		for (int i = 0; i < 40; i++) {
			double a = i * 2.39;
			float rise = ((t / 900f + i * 0.13f) % 1f), x = cx + (float) Math.cos(a) * ringR, y = cy + (float) Math.sin(a) * ringR - rise * 40;
			int al = Math.round(200 * crumble * (1 - rise));
			g.fill(Math.round(x), Math.round(y), Math.round(x) + 2, Math.round(y) + 2, al << 24 | (i % 3 == 0 ? 0xFF6A2A : 0x3A1A1A));
		}
	}

	/** The cut runs from the ring's centre toward the top-right corner of the screen: unit {x, y} (screen). */
	private static float[] cutDir(int sw, int sh) {
		float dx = sw / 2f, dy = -(sh / 2f - 10);
		float l = (float) Math.hypot(dx, dy);
		return new float[]{dx / l, dy / l};
	}

	/** The cut's angle from straight up, clockwise: where the snake stops its head (the bite) for the cut. */
	private static double cutAngle() {
		var w = Minecraft.getInstance().getWindow();
		float[] d = cutDir(w.getGuiScaledWidth(), w.getGuiScaledHeight());
		return Math.atan2(d[0], -d[1]);
	}

	/**
	 * The katana, handle and all, flies corner to corner: in from beyond the bottom-left corner, tip
	 * first, slicing up through the ring and its bite, out past the top-right corner, with motion
	 * ghosts and a glowing streak left along its path.
	 */
	private static void drawKatana(GuiGraphicsExtractor g, float cx, float cy, float ringR, long t, float[] d) {
		int sw = g.guiWidth(), sh = g.guiHeight();
		float len = ringR * 1.5f + 60, reach = (float) Math.hypot(sw, sh) / 2 + len;
		float phi = (float) Math.atan2(d[1], d[0]); // direction of travel, screen angle
		float u = Math.min(1f, (t - SPIN_END) / (float) SLASH_MS);
		// streak along the path behind the sword: brighter toward it
		float along = -reach + 2 * reach * u * u; // where the sword's middle is, along the cut line
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate(phi);
		for (int i = 0; i < 12; i++) {
			float a0 = -reach + (along + len / 2 + reach) * i / 12f, a1 = -reach + (along + len / 2 + reach) * (i + 1) / 12f;
			int al = Math.round(150 * (i / 11f) * (i / 11f) * (u < 1 ? 1 : 0.5f));
			g.fill(Math.round(a0), -5, Math.round(a1), 5, (al / 3) << 24 | 0xBFEFFF);
			g.fill(Math.round(a0), -1, Math.round(a1), 1, al << 24 | 0xFFFFFF);
		}
		pose.popMatrix();
		for (int ghost = 3; ghost >= 0; ghost--) {
			float gu = Math.min(1f, (t - SPIN_END - ghost * 22) / (float) SLASH_MS);
			if (gu < 0) continue;
			float mid = -reach + 2 * reach * gu * gu;
			float px = cx + d[0] * (mid - len / 2), py = cy + d[1] * (mid - len / 2); // pommel trails the blade
			katana(g, px, py, phi + (float) Math.PI / 2, len, ghost == 0 ? 255 : 70 / ghost);
		}
	}

	/** A katana pointing up from its pommel at (x, y), leaning by {@code angle}, {@code len} px long. */
	private static void katana(GuiGraphicsExtractor g, float x, float y, float angle, float len, int a) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.rotate(angle - (float) Math.PI / 2); // local +x runs up the sword
		int handle = Math.round(len * 0.22f), w = Math.max(2, Math.round(len * 0.018f)), blade = Math.round(len);
		// handle with its wrap, guard (tsuba), blade with a bright edge and a curved tip
		g.fill(0, -w, handle, w, a << 24 | 0x1A1418);
		for (int i = 2; i < handle - 2; i += 6) g.fill(i, -w, i + 3, w, a << 24 | 0x6A1E2E);
		g.fill(handle, -w * 3, handle + 4, w * 3, a << 24 | 0xC8A040);
		g.fill(handle + 1, -w * 3, handle + 2, w * 3, a << 24 | 0xFFE08A);
		g.fillGradient(handle + 4, -w, blade - 8, w, a << 24 | 0xF4F8FC, a << 24 | 0x98A4B0);
		g.fill(handle + 4, -w, blade - 8, -w + 1, a << 24 | 0xFFFFFF);
		for (int i = 0; i < 8; i++) g.fill(blade - 8 + i, -w + i / 3, blade - 7 + i, w - i / 2, a << 24 | 0xE4EAF0);
		pose.popMatrix();
	}

	/** The cut: a flash, a bright line corner to corner through the ring that flares and fades, sparks where it cut. */
	private static void drawCut(GuiGraphicsExtractor g, float cx, float cy, float ringR, long t, float[] d) {
		float k = (t - CUT) / 700f;
		if (k > 1) return;
		float fade = 1 - k;
		int sw = g.guiWidth(), sh = g.guiHeight();
		int reach = Math.round((float) Math.hypot(sw, sh));
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate((float) Math.atan2(d[1], d[0]));
		g.fill(-reach, -9, reach, 9, Math.round(50 * fade) << 24 | 0xBFE6FF);
		g.fill(-reach, -2, reach, 2, Math.round(255 * fade) << 24 | 0xFFFFFF);
		pose.popMatrix();
		if (k < 0.12f) g.fill(0, 0, sw, sh, Math.round(170 * (1 - k / 0.12f)) << 24 | 0xFFFFFF);
		for (int end = -1; end <= 1; end += 2) // sparks bursting from the bite and the far side
			for (int i = 0; i < 18; i++) {
				double a = i * Math.PI * 2 / 18 + end;
				float dist = 8 + 70 * (1 - (1 - k) * (1 - k)) * (0.6f + 0.4f * ((i * 7) % 5) / 4f);
				float x = cx + end * d[0] * ringR + (float) Math.cos(a) * dist, y = cy + end * d[1] * ringR + (float) Math.sin(a) * dist + 30 * k * k;
				g.fill(Math.round(x) - 1, Math.round(y) - 1, Math.round(x) + 2, Math.round(y) + 2, Math.round(255 * fade) << 24 | (i % 3 == 0 ? 0xFFFFFF : 0x9FE8FF));
			}
	}


	/**
	 * The drops burst out of the middle of the ring with a shockwave, each in a beam of light with
	 * spinning rays, names below; "VANGUARD DROP!" slams in above.
	 */
	private static void drawDrops(GuiGraphicsExtractor g, Font font, float cx, float cy, float ringR, float scale, long t, float alpha) {
		long since = t - POP;
		// shockwave
		if (since < 600) {
			float k = since / 600f, r = 10 + ringR * 1.7f * (1 - (1 - k) * (1 - k));
			for (int i = 0; i < 60; i++) {
				double a = i * Math.PI * 2 / 60;
				int al = Math.round(230 * (1 - k));
				g.fill(Math.round(cx + (float) Math.cos(a) * r) - 1, Math.round(cy + (float) Math.sin(a) * r) - 1,
						Math.round(cx + (float) Math.cos(a) * r) + 2, Math.round(cy + (float) Math.sin(a) * r) + 2, al << 24 | 0xFFE08A);
			}
		}
		caption(g, font, "VANGUARD DROP!", cx, cy - ringR - 46, 2.6f + Math.max(0f, 1 - since / 200f) * 2f,
				Theme.mix(0xFFAA00, 0xFFFFFF, (float) Math.max(0, Math.sin(since / 240.0)) * 0.4f), alpha);
		Matrix3x2fStack pose = g.pose();
		int n = got.size();
		for (int i = 0; i < n; i++) {
			float k = Math.min(1f, Math.max(0f, (since - i * 220) / 600f));
			if (k <= 0) continue;
			float s = k - 1, back = 1 + 2.6f * s * s * s + 1.6f * s * s; // overshoot
			float tx = cx + (i - (n - 1) / 2f) * 120, ty = cy - 4; // room for the names
			float x = cx + (tx - cx) * back, y = cy + (ty - cy) * back - 6 * (float) Math.sin(t / 300.0 + i);
			// beam of light
			int bw = Math.round(22 * k), beam = Math.round((110 + 40 * (float) Math.sin(t / 200.0 + i)) * alpha * k);
			g.fillGradient(Math.round(x) - bw, Math.round(y - ringR * 1.4f), Math.round(x) + bw, Math.round(y + 10), 0, beam << 24 | 0xFFE6A0);
			// rays
			for (int r = 0; r < 12; r++) {
				pose.pushMatrix();
				pose.translate(x, y);
				pose.rotate((float) (r * Math.PI / 6 + t / 900.0));
				g.fill(0, -1, Math.round(34 * back * scale / 4), 1, Math.round(100 * alpha * k) << 24 | 0xFFD060);
				pose.popMatrix();
			}
			float size = 3.2f * back * scale / 4.5f;
			float spin = (float) Math.cos(Math.max(0, 1 - k) * 6);
			ScathaSlots.item(g, icon(got.get(i)), x, y, size * Math.max(0.15f, Math.abs(spin)), size);
			if (k >= 1) {
				caption(g, font, got.get(i).name(), x, y + 12 * size, 1.3f, 0xFFAA00, alpha);
				String value = value(got.get(i));
				if (value != null) caption(g, font, value, x, y + 12 * size + 14, 1.1f, 0xFFE38A, alpha);
			}
		}
	}

	private static void caption(GuiGraphicsExtractor g, Font font, String text, float x, float y, float size, int rgb, float alpha) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size, size);
		g.text(font, text, -font.width(text) / 2, 0, Math.max(4, Math.round(255 * alpha)) << 24 | rgb, true);
		pose.popMatrix();
	}

	static ItemStack icon(Drop d) {
		ItemStack real = SkyblockItems.stack(d.name());
		return real != null ? real : new ItemStack(Items.DYE.lightBlue()); // Frostbitten Dye isn't in Hypixel's item list
	}
}
