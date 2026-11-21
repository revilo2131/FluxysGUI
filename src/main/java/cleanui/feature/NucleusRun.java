package cleanui.feature;

import cleanui.data.Config;
import cleanui.data.SkyblockItems;
import cleanui.skyblock.NucleusParser;
import cleanui.skyblock.NucleusParser.Loot;
import cleanui.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Crystal Nucleus runs, from chat: a crystal tracker with a run timer, a fly-in when you find a
 * crystal, a note + toast when you place one, beams converging when the fifth goes in, the loot
 * bundle revealed on a reel, then a stats card (time, splits, PB, profit). A new PB can bring in
 * the buff Scatha. All drawn on the HUD, so you keep playing. Settings: the Scatha page.
 */
public final class NucleusRun {
	private static final List<String> NAMES = NucleusParser.CRYSTALS;
	private static final int[] COLORS = {0xFFAA00, 0xAA55FF, 0x55FF55, 0x55FFFF, 0xFFFF55};
	/** Each crystal's note (note block steps, 12 = F#4): together they make a chord. */
	private static final int[] NOTE = {12, 15, 19, 22, 24};
	private static final int FLY_MS = 1600, TOAST_MS = 1600, BEAMS_MS = 2200;
	private static final int FIRST = 500, BIG_EACH = 1700, SPIN = 1300, SMALL_EACH = 90, STATS_MS = 7500;

	private static final NucleusParser parser = new NucleusParser();
	// the run
	private static final boolean[] found = new boolean[5], placed = new boolean[5];
	private static final long[] foundAt = new long[5];
	private static long runStart = -1, runEnd = -1;
	private static int foundCount, placedCount, sessionRuns;
	private static String profit;
	// animations (start times; -1 = not running)
	private static int flyCrystal = -1, flyCount, toastCrystal = -1;
	private static long flyStart, toastStart, beamsStart = -1, lootStart = -1;
	private static List<Loot> bigs = List.of(), smalls = List.of(), all = List.of();
	private static List<String> extras = List.of();
	private static long runTime = -1;
	private static boolean pb;
	// what the tick already played
	private static int bigsDone, smallsDone, lastReelCell, beamStage;
	private static boolean statsDone;
	// /nucleustest: chat lines to feed at these times
	private record Fake(long at, String line, int color) {}
	private static final List<Fake> test = new ArrayList<>();

	private NucleusRun() {}

	// ---- chat ----

	/** Every chat message: split into lines (with each line's first colour) and read. */
	public static void onChat(Component message) {
		List<String> texts = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		int[] col = {-1};
		message.visit((style, text) -> {
			for (char ch : text.toCharArray()) {
				if (ch == '\n') {
					texts.add(cur.toString());
					colors.add(col[0]);
					cur.setLength(0);
					col[0] = -1;
					continue;
				}
				cur.append(ch);
				if (col[0] < 0 && !Character.isWhitespace(ch) && style.getColor() != null) col[0] = style.getColor().getValue();
			}
			return Optional.empty();
		}, Style.EMPTY);
		texts.add(cur.toString());
		colors.add(col[0]);
		long now = System.currentTimeMillis();
		for (int i = 0; i < texts.size(); i++) {
			String raw = texts.get(i);
			int color = colors.get(i) >= 0 ? colors.get(i) : legacyColor(raw);
			handle(parser.line(ChatFormatting.stripFormatting(raw), color), now);
		}
	}

	/** Colour from a "§5" style code at the start of a line, for servers that send codes in the text. */
	private static int legacyColor(String raw) {
		for (int i = 0; i + 1 < raw.length(); i++)
			if (raw.charAt(i) == '§') {
				ChatFormatting f = ChatFormatting.getByCode(raw.charAt(i + 1));
				net.minecraft.network.chat.TextColor c = f == null ? null : net.minecraft.network.chat.TextColor.fromLegacyFormat(f);
				if (c != null) return c.getValue();
			}
		return -1;
	}

	private static void handle(NucleusParser.Event e, long at) {
		if (e == null) return;
		switch (e) {
			case NucleusParser.Found f -> found(NAMES.indexOf(f.crystal()), f.count(), at);
			case NucleusParser.Placed p -> placed(NAMES.indexOf(p.crystal()), at);
			case NucleusParser.Profit p -> profit = p.amount();
			case NucleusParser.Bundle b -> bundle(b, at);
		}
	}

	private static void resetRun(long start) {
		java.util.Arrays.fill(found, false);
		java.util.Arrays.fill(placed, false);
		runStart = start;
		runEnd = -1;
		foundCount = placedCount = 0;
		profit = null;
		beamsStart = lootStart = -1;
		runTime = -1;
	}

	private static void found(int i, int n, long at) {
		if (i < 0) return;
		if (n == 1 || runEnd >= 0) resetRun(n == 1 ? at : -1); // a new run; joined mid-run = no timer
		found[i] = true;
		foundAt[i] = at;
		foundCount = n;
		if (!Config.get().nucleusAnimations) return;
		flyCrystal = i;
		flyCount = n;
		flyStart = at;
		note(SoundEvents.NOTE_BLOCK_BELL, NOTE[i], 0.9f);
		note(SoundEvents.NOTE_BLOCK_CHIME, Math.min(24, NOTE[i] + 7), 0.5f);
		if (n == 5) for (int k = 0; k < 5; k++) note(SoundEvents.NOTE_BLOCK_PLING, NOTE[k], 0.5f); // all five: the chord
	}

	private static void placed(int i, long at) {
		if (i < 0 || placed[i]) return;
		placed[i] = true;
		placedCount++;
		if (Config.get().nucleusAnimations) {
			toastCrystal = i;
			toastStart = at;
			// climbs one step per crystal placed
			note(SoundEvents.NOTE_BLOCK_HARP, 4 + placedCount * 3, 0.9f);
			note(SoundEvents.NOTE_BLOCK_BELL, NOTE[i], 0.6f);
		}
		if (placedCount == 5) finish(at);
	}

	/** The fifth crystal is in: beams converge, and the loot reveal follows. */
	private static void finish(long at) {
		if (beamsStart >= 0) return;
		runEnd = at;
		beamsStart = Config.get().nucleusAnimations ? at : at - BEAMS_MS;
		beamStage = 0;
	}

	private static void bundle(NucleusParser.Bundle b, long at) {
		finish(at); // placements we missed (placed in an earlier session) still end the run here
		sessionRuns++;
		runTime = runStart >= 0 ? runEnd - runStart : -1;
		long best = Config.get().nucleusBestMs;
		pb = runTime > 0 && (best <= 0 || runTime < best);
		if (pb) {
			Config.get().nucleusBestMs = runTime;
			Config.get().save();
		}
		if (!Config.get().nucleusLoot) {
			resetRun(-1);
			return;
		}
		all = b.items();
		bigs = all.stream().filter(NucleusRun::big).toList();
		smalls = all.stream().filter(l -> !big(l)).toList();
		extras = b.extras();
		lootStart = beamsStart + BEAMS_MS;
		bigsDone = smallsDone = 0;
		lastReelCell = -1;
		statsDone = false;
	}

	/** The loot worth a spin: anything that isn't a Fine/Flawed gemstone. */
	private static boolean big(Loot l) {
		return !l.name().endsWith("Gemstone");
	}

	private static int tierOf(int color) {
		return switch (color & 0xFFFFFF) {
			case 0xFFAA00, 0xFF55FF -> 2; // legendary, mythic
			case 0xAA00AA -> 1;           // epic
			default -> 0;
		};
	}

	// ---- timing ----

	private static long smallStart() {
		return lootStart + FIRST + (long) bigs.size() * BIG_EACH;
	}

	private static long statsAt() {
		return smallStart() + (long) smalls.size() * SMALL_EACH + 500;
	}

	private static long lootEnd() {
		return statsAt() + STATS_MS;
	}

	/** Reel position (cells) for big item k at time now: eases out onto cell 16. */
	private static float reel(int k, long now) {
		float s = Math.max(0f, Math.min(1f, (now - (lootStart + FIRST + (long) k * BIG_EACH)) / (float) SPIN));
		return 16 * (1 - (1 - s) * (1 - s) * (1 - s));
	}

	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		while (!test.isEmpty() && test.get(0).at() <= now) {
			Fake f = test.remove(0);
			handle(parser.line(f.line(), f.color()), f.at());
		}
		if (beamsStart >= 0 && Config.get().nucleusAnimations) {
			if (beamStage == 0 && now >= beamsStart) {
				beamStage = 1;
				ScathaSlots.sound(SoundEvents.BEACON_ACTIVATE, 1.2f, 1f);
			}
			if (beamStage == 1 && now >= beamsStart + 1000) {
				beamStage = 2;
				ScathaSlots.sound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1f);
				ScathaSlots.sound(SoundEvents.TOTEM_USE, 1.3f, 0.5f);
				for (int k = 0; k < 5; k++) note(SoundEvents.NOTE_BLOCK_BELL, NOTE[k], 0.7f);
			}
		}
		if (lootStart < 0) return;
		// big items: ticks while the reel runs, then the landing with its rarity's confetti
		while (bigsDone < bigs.size()) {
			int k = bigsDone;
			long land = lootStart + FIRST + (long) k * BIG_EACH + SPIN;
			if (now >= lootStart + FIRST + (long) k * BIG_EACH && now < land) {
				int cell = (int) reel(k, now);
				if (cell != lastReelCell) ScathaSlots.play(SoundEvents.NOTE_BLOCK_HAT, 1.5f, 0.4f);
				lastReelCell = cell;
			}
			if (now < land) break;
			bigsDone++;
			lastReelCell = -1;
			ScathaSlots.play(SoundEvents.NOTE_BLOCK_BELL, 1.4f, 0.8f);
			ScathaSlots.celebrateNow(tierOf(bigs.get(k).color()));
		}
		// the gemstones pop in quickly, rising
		while (smallsDone < smalls.size() && now >= smallStart() + (long) smallsDone * SMALL_EACH) {
			note(SoundEvents.NOTE_BLOCK_XYLOPHONE, Math.min(24, 6 + smallsDone), 0.4f);
			smallsDone++;
		}
		if (!statsDone && now >= statsAt()) {
			statsDone = true;
			ScathaSlots.play(SoundEvents.NOTE_BLOCK_PLING, 1f, 0.7f);
			if (pb) {
				ScathaSlots.celebrateNow(2);
				if (Config.get().nucleusPbWalkIn) ScathaSlots.walkInFor("NEW PB! " + time(runTime));
			}
		}
		if (now > lootEnd()) {
			lootStart = -1;
			resetRun(-1);
		}
	}

	private static void note(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, int step, float volume) {
		ScathaSlots.play(sound, (float) Math.pow(2, (Math.max(0, Math.min(24, step)) - 12) / 12.0), volume);
	}

	/**
	 * The finale (beams, then loot and stats) is on: the HUD is swapped for it over a blurred world
	 * (HudMixin), with "Blur while rolling" on. Found/placed animations stay on the normal HUD.
	 */
	public static boolean takeover() {
		if (!Config.get().nucleusFocus) return false;
		long now = System.currentTimeMillis();
		return (beamsStart >= 0 && Config.get().nucleusAnimations && now - beamsStart < BEAMS_MS) || lootStart >= 0;
	}

	// ---- drawing ----

	public static void render(GuiGraphicsExtractor g) {
		long now = System.currentTimeMillis();
		Font font = Minecraft.getInstance().font;
		if (Config.get().nucleusTracker && (foundCount > 0 || placedCount > 0) && beamsStart < 0) drawTracker(g, font, now);
		if (Config.get().nucleusAnimations) {
			if (toastCrystal >= 0 && now - toastStart < TOAST_MS && beamsStart < 0) drawToast(g, font, now);
			if (flyCrystal >= 0 && now - flyStart < FLY_MS) drawFly(g, font, now);
			if (beamsStart >= 0 && now - beamsStart < BEAMS_MS) drawBeams(g, now);
		}
		if (lootStart >= 0 && now >= lootStart) drawLoot(g, font, now);
	}

	private static ItemStack crystal(int i) {
		// Hypixel's item list spells it "Saphhire Crystal" (SAPPHIRE_CRYSTAL)
		ItemStack real = SkyblockItems.stack(i == 3 ? "Saphhire Crystal" : NAMES.get(i) + " Crystal");
		return real != null ? real : new ItemStack(Items.STAINED_GLASS.asList().get(new int[]{1, 10, 5, 3, 4}[i]));
	}

	private static ItemStack icon(Loot l) {
		ItemStack real = SkyblockItems.stack(l.name());
		return real != null ? real : new ItemStack(Items.AMETHYST_SHARD);
	}

	private static final int TRACKER_W = 5 * 22 + 8, TRACKER_H = 44;

	private static float trackerScale() {
		return Config.get().nucleusTrackerScale / 100f;
	}

	/** Top-left of the tracker on screen, from the Position setting (left, right or top of the screen). */
	private static float[] trackerAt(GuiGraphicsExtractor g) {
		float s = trackerScale();
		return switch (Config.get().nucleusTrackerPos) {
			case "RIGHT" -> new float[]{g.guiWidth() - 6 - TRACKER_W * s, g.guiHeight() / 2f - 30};
			case "TOP" -> new float[]{g.guiWidth() / 2f - TRACKER_W * s / 2, 6};
			default -> new float[]{6, g.guiHeight() / 2f - 30};
		};
	}

	/** Where crystal slot i's middle is on screen (the fly-in lands there). */
	private static float[] slotCentre(GuiGraphicsExtractor g, int i) {
		float[] o = trackerAt(g);
		float s = trackerScale();
		return new float[]{o[0] + (5 + i * 22 + 10) * s, o[1] + 26 * s};
	}

	/**
	 * Five crystal slots (found: icon, placed: glowing + tick) and, with the timer on, the run time
	 * coloured against your PB. Drawn at its own size and place (settings).
	 */
	private static void drawTracker(GuiGraphicsExtractor g, Font font, long now) {
		float[] o = trackerAt(g);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(o[0], o[1]);
		pose.scale(trackerScale(), trackerScale());
		int x = 0, y = 0, w = TRACKER_W, h = TRACKER_H;
		Theme.box(g, x, y, w, h, Theme.PANEL, 0.85f, Theme.LINE, 1f);
		g.text(font, "NUCLEUS", x + 5, y + 4, 0xFFFF55FF, true);
		long best = Config.get().nucleusBestMs;
		boolean timerOn = Config.get().nucleusTimer;
		String timer = runStart >= 0 ? time(now - runStart) : "--:--";
		int tc = 0xFFFFFFFF;
		if (runStart >= 0 && best > 0) {
			float pace = (now - runStart) / (float) best;
			tc = pace < 0.8f ? 0xFF55FF55 : pace < 1f ? 0xFFFFFF55 : 0xFFFF5555; // ahead, close, behind your PB
		}
		if (timerOn) g.text(font, timer, x + w - 5 - font.width(timer), y + 4, tc, true);
		for (int i = 0; i < 5; i++) {
			int sx = 5 + i * 22, sy = y + 16;
			boolean missingLast = foundCount == 4 && !found[i];
			float pulse = missingLast ? 0.5f + 0.5f * (float) Math.sin(now / 160.0) : 0f;
			int border = placed[i] ? COLORS[i] : found[i] ? Theme.mix(COLORS[i], 0x000000, 0.3f) : Theme.mix(0x303040, COLORS[i], 0.3f + 0.6f * pulse);
			Theme.box(g, sx, sy, 20, 20, placed[i] ? Theme.mix(0x101018, COLORS[i], 0.35f) : 0x101018, 1f, border, 1f);
			boolean flying = flyCrystal == i && now - flyStart < FLY_MS;
			if (found[i] && !flying) ScathaSlots.item(g, crystal(i), sx + 10, sy + 10, 1f, 1f);
			if (placed[i]) g.text(font, "✔", sx + 13, sy + 12, 0xFF55FF55, true);
		}
		if (best > 0 && timerOn) Theme.label(g, font, "PB " + time(best), x + 5, y + h + 2, 1f);
		pose.popMatrix();
	}

	/** "Amber placed 3/5" sliding in by the tracker (above it, or below when it sits at the top). */
	private static void drawToast(GuiGraphicsExtractor g, Font font, long now) {
		float t = (now - toastStart) / (float) TOAST_MS;
		float slide = Math.min(1f, t * 6), fade = t > 0.75f ? (1 - t) / 0.25f : 1f;
		String text = NAMES.get(toastCrystal) + " placed  " + placedCount + "/5";
		float[] o = trackerAt(g);
		boolean top = "TOP".equals(Config.get().nucleusTrackerPos);
		int x = Math.round(o[0]) + Math.round((1 - slide) * -60), y = top ? Math.round(o[1] + (TRACKER_H + 14) * trackerScale()) : Math.round(o[1]) - 14;
		int alpha = Math.max(4, Math.round(255 * fade));
		g.text(font, text, x + 2, y, alpha << 24 | COLORS[toastCrystal], true);
	}

	/**
	 * Crystal found: a white flash, the crystal pops up big in the middle spinning, shards burst out
	 * in its colour, "AMBER CRYSTAL 1/5" slams in, then the crystal flies into its tracker slot.
	 */
	private static void drawFly(GuiGraphicsExtractor g, Font font, long now) {
		long t = now - flyStart;
		int sw = g.guiWidth(), sh = g.guiHeight(), color = COLORS[flyCrystal];
		if (t < 160) g.fill(0, 0, sw, sh, Math.round(70 * (1 - t / 160f)) << 24 | 0xFFFFFF);
		float cx = sw / 2f, cy = sh / 2f - 20;
		// shards
		float burst = Math.min(1f, t / 700f), out = 1 - (1 - burst) * (1 - burst);
		Matrix3x2fStack pose = g.pose();
		if (t < 900)
			for (int k = 0; k < 16; k++) {
				double a = k * Math.PI * 2 / 16 + flyCrystal;
				float d = 14 + 110 * out * (0.7f + 0.3f * ((k * 7) % 5) / 4f);
				int alpha = Math.round(230 * (1 - t / 900f));
				pose.pushMatrix();
				pose.translate(cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d);
				pose.rotate((float) a + t / 200f);
				g.fill(-3, -1, 3, 1, alpha << 24 | (k % 3 == 0 ? 0xFFFFFF : color));
				pose.popMatrix();
			}
		// the crystal: pops in, spins, then flies to its slot
		float pop = Math.min(1f, t / 220f), fly = Math.max(0f, Math.min(1f, (t - 1000) / 550f));
		float ease = fly * fly * (3 - 2 * fly);
		float size = (pop < 1 ? 4.6f * pop : 4f) * (1 - ease) + 1f * ease;
		float[] slot = slotCentre(g, flyCrystal);
		float tx = slot[0], ty = slot[1];
		float x = cx + (tx - cx) * ease, y = cy + (ty - cy) * ease;
		float spin = (float) Math.cos(t / 140.0);
		ScathaSlots.item(g, crystal(flyCrystal), x, y, size * Math.max(0.15f, Math.abs(spin)), size);
		// the caption
		if (t < 1300) {
			String name = NAMES.get(flyCrystal).toUpperCase() + " CRYSTAL";
			String count = flyCount + "/5" + (flyCount == 5 ? "  ALL FOUND!" : "");
			float slam = 1 + Math.max(0f, 1 - t / 180f) * 1.5f;
			int alpha = t > 1000 ? Math.max(4, Math.round(255 * (1 - (t - 1000) / 300f))) : 255;
			pose.pushMatrix();
			pose.translate(cx, cy + 40);
			pose.scale(2f * slam, 2f * slam);
			g.text(font, name, -font.width(name) / 2, 0, alpha << 24 | color, true);
			pose.popMatrix();
			pose.pushMatrix();
			pose.translate(cx, cy + 62);
			pose.scale(1.4f, 1.4f);
			g.text(font, count, -font.width(count) / 2, 0, alpha << 24 | 0xFFFFFF, true);
			pose.popMatrix();
		}
	}

	/** Fifth crystal in: five beams in the crystal colours sweep in to the centre, then a flash. */
	private static void drawBeams(GuiGraphicsExtractor g, long now) {
		long t = now - beamsStart;
		int sw = g.guiWidth(), sh = g.guiHeight();
		float cx = sw / 2f, cy = sh / 2f - 10, reach = (float) Math.hypot(sw, sh) / 2;
		float in = Math.min(1f, t / 1000f), fade = t < 1000 ? 1f : Math.max(0f, 1 - (t - 1000) / 1200f);
		g.fill(0, 0, sw, sh, Math.round(110 * fade) << 24);
		Matrix3x2fStack pose = g.pose();
		for (int k = 0; k < 5; k++) {
			double a = -Math.PI / 2 + k * Math.PI * 2 / 5 + t / 900.0;
			float len = reach * (1 - in) + 0, from = reach; // beams grow in from the edge to the centre
			int alpha = Math.round(220 * fade);
			pose.pushMatrix();
			pose.translate(cx, cy);
			pose.rotate((float) a);
			float width = 3 + 4 * in;
			g.fillGradient(Math.round(len), Math.round(-width), Math.round(from), Math.round(width), alpha << 24 | COLORS[k], (alpha / 3) << 24 | COLORS[k]);
			g.fill(Math.round(len), -1, Math.round(from), 1, alpha << 24 | 0xFFFFFF);
			pose.popMatrix();
		}
		if (t >= 1000) { // converge: flash and a ring blowing out
			float r = (t - 1000) / 1200f;
			g.fill(0, 0, sw, sh, Math.round(200 * Math.max(0f, 1 - r * 3)) << 24 | 0xFFFFFF);
			float ring = 20 + r * reach;
			for (int k = 0; k < 40; k++) {
				double a = k * Math.PI * 2 / 40;
				int c = COLORS[k % 5];
				int alpha = Math.round(255 * (1 - r));
				g.fill(Math.round(cx + (float) Math.cos(a) * ring) - 2, Math.round(cy + (float) Math.sin(a) * ring) - 2,
						Math.round(cx + (float) Math.cos(a) * ring) + 2, Math.round(cy + (float) Math.sin(a) * ring) + 2, alpha << 24 | c);
			}
		}
	}

	/** Loot panel (left of centre): reel for each big item, then the gemstones; stats card (right) after. */
	private static void drawLoot(GuiGraphicsExtractor g, Font font, long now) {
		int sw = g.guiWidth(), sh = g.guiHeight();
		float fade = now > lootEnd() - 400 ? Math.max(0f, (lootEnd() - now) / 400f) : Math.min(1f, (now - lootStart) / 250f);
		int pw = 210, px = sw / 2 - pw - 6, py = Math.max(8, sh / 2 - 110);
		int rows = (smalls.size() + 7) / 8;
		int ph = 34 + bigs.size() * 16 + 30 + rows * 20 + 14 + extras.size() * 10 + (profit != null ? 10 : 0);
		Theme.box(g, px, py, pw, ph, Theme.PANEL, 0.92f * fade, 0xFF55FF, fade);
		title(g, font, "NUCLEUS LOOT", px + pw / 2, py + 5, 0xFF55FF, fade);
		int y = py + 18;
		// the current big item's reel
		int k = Math.min(bigsDone, bigs.size() - 1);
		if (!bigs.isEmpty() && bigsDone < bigs.size()) {
			drawReel(g, font, k, px + 5, y, pw - 10, now);
		}
		y += 28;
		// big items revealed so far
		for (int i = 0; i < bigsDone; i++) {
			Loot l = bigs.get(i);
			ScathaSlots.item(g, icon(l), px + 13, y + 6, 0.75f, 0.75f);
			g.text(font, l.name() + (l.count() > 1 ? " x" + l.count() : ""), px + 24, y + 2, Theme.argb(l.color() >= 0 ? l.color() : 0xFFFFFF, fade), true);
			y += 16;
		}
		// the gemstones, as a grid of icons with counts
		for (int i = 0; i < smallsDone; i++) {
			Loot l = smalls.get(i);
			int gx = px + 6 + (i % 8) * 25, gy = y + 4 + (i / 8) * 20;
			ScathaSlots.item(g, icon(l), gx + 9, gy + 8, 1f, 1f);
			if (l.count() > 1) {
				String c = String.valueOf(l.count());
				g.text(font, c, gx + 18 - font.width(c), gy + 10, Theme.argb(l.color() >= 0 ? l.color() : 0xFFFFFF, fade), true);
			}
		}
		y += 8 + rows * 20;
		if (smallsDone == smalls.size()) {
			for (String ex : extras) {
				g.text(font, ex, px + 6, y, Theme.argb(0xAA00AA, fade), true);
				y += 10;
			}
			if (profit != null) g.text(font, "Profit: " + profit, px + 6, y, Theme.argb(0xFFAA00, fade), true);
		}
		if (now >= statsAt()) drawStats(g, font, sw / 2 + 6, py, now, fade);
	}

	/** One big item spinning on a reel of the run's loot, easing onto the real item. */
	private static void drawReel(GuiGraphicsExtractor g, Font font, int k, int x, int y, int w, long now) {
		int h = 22;
		Theme.box(g, x, y, w, h, 0x140E30, 1f, 0xFFC83D, 1f);
		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		float p = reel(k, now);
		for (int c = (int) Math.floor(p) - 1; c <= (int) Math.floor(p) + 1; c++) {
			Loot l = c == 16 ? bigs.get(k) : all.get(Math.floorMod(c * 5 + k * 3, all.size()));
			int cy = y + Math.round((c - p) * h);
			ScathaSlots.item(g, icon(l), x + 14, cy + h / 2f, 1f, 1f);
			g.text(font, l.name(), x + 28, cy + 7, 0xFF000000 | (l.color() >= 0 ? l.color() : 0xFFFFFF), true);
		}
		g.disableScissor();
	}

	/** Run time, splits per crystal, PB (NEW PB! when beaten), runs this session, profit. */
	private static void drawStats(GuiGraphicsExtractor g, Font font, int x, int y, long now, float fade) {
		float in = Math.min(1f, (now - statsAt()) / 300f) * fade;
		int w = 170, h = 34 + 5 * 11 + 40;
		x += Math.round((1 - Math.min(1f, (now - statsAt()) / 300f)) * 30);
		Theme.box(g, x, y, w, h, Theme.PANEL, 0.92f * in, pb ? 0xFFAA00 : Theme.LINE, in);
		title(g, font, pb ? "NEW PB!" : "RUN COMPLETE", x + w / 2, y + 5, pb ? 0xFFAA00 : 0x55FF55, in);
		int ly = y + 20;
		Theme.textLeader(g, font, "Time", Theme.TEXT_MUTED, runTime > 0 ? time(runTime) : "--", 0xFFFFFF, x + 6, ly, w - 12, in);
		ly += 14;
		// splits in the order found
		Integer[] order = {0, 1, 2, 3, 4};
		java.util.Arrays.sort(order, (a, b) -> Long.compare(found[a] ? foundAt[a] : Long.MAX_VALUE, found[b] ? foundAt[b] : Long.MAX_VALUE));
		for (int i : order) {
			String split = found[i] && runStart >= 0 ? "+" + time(foundAt[i] - runStart) : "--";
			Theme.textLeader(g, font, NAMES.get(i), COLORS[i], split, 0xDDDDDD, x + 6, ly, w - 12, in);
			ly += 11;
		}
		ly += 4;
		long best = Config.get().nucleusBestMs;
		Theme.textLeader(g, font, "PB", Theme.TEXT_MUTED, best > 0 ? time(best) : "--", 0xFFAA00, x + 6, ly, w - 12, in);
		ly += 11;
		Theme.textLeader(g, font, "Runs this session", Theme.TEXT_MUTED, String.valueOf(sessionRuns), 0xFFFFFF, x + 6, ly, w - 12, in);
		ly += 11;
		if (profit != null) Theme.textLeader(g, font, "Profit", Theme.TEXT_MUTED, profit, 0xFFAA00, x + 6, ly, w - 12, in);
	}

	private static void title(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int rgb, float a) {
		g.text(font, text, cx - font.width(text) / 2, y, Theme.argb(rgb, a), true);
	}

	static int sessionRuns() {
		return sessionRuns;
	}

	/** m:ss.t */
	static String time(long ms) {
		long s = ms / 1000;
		return s / 60 + ":" + String.format("%02d", s % 60) + "." + (ms % 1000) / 100;
	}

	// ---- test ----

	/**
	 * /nucleustest: a whole run from fake chat lines (through the real parser): five crystals found,
	 * all placed, the loot bundle. {@code msAgo} > 0 starts it that far in (for preview screenshots).
	 */
	public static void test(long msAgo) {
		test.clear();
		long t0 = System.currentTimeMillis() - msAgo, t = 0;
		int[] order = {0, 1, 3, 4, 2}; // Amber, Amethyst, Sapphire, Topaz, Jade
		for (int k = 0; k < 5; k++) {
			test.add(new Fake(t0 + t, "✦ CRYSTAL FOUND (" + (k + 1) + "/5)", 0xAA00AA));
			test.add(new Fake(t0 + t, NAMES.get(order[k]) + " Crystal", COLORS[order[k]]));
			t += 1900;
		}
		t += 300;
		for (int k = 0; k < 5; k++) {
			test.add(new Fake(t0 + t, "✦ You placed the " + NAMES.get(order[k]) + " Crystal!", 0xAA00AA));
			t += 500;
		}
		String[][] loot = {{"Jaderald", "AA00AA"}, {"Pickonimbus 2000", "AA00AA"}, {"Precious Pearl", "AA00AA"},
				{"Fine Amber Gemstone", "5555FF"}, {"Fine Amethyst Gemstone", "5555FF"}, {"Fine Jade Gemstone", "5555FF"},
				{"Fine Jasper Gemstone", "5555FF"}, {"Fine Ruby Gemstone", "5555FF"}, {"Fine Sapphire Gemstone", "5555FF"},
				{"Fine Topaz Gemstone", "5555FF"}, {"Flawed Amber Gemstone x30", "55FF55"}, {"Flawed Amethyst Gemstone x30", "55FF55"},
				{"Flawed Jade Gemstone x66", "55FF55"}, {"Flawed Jasper Gemstone x44", "55FF55"}, {"Flawed Ruby Gemstone x52", "55FF55"},
				{"Flawed Sapphire Gemstone x6", "55FF55"}, {"+800 HOTM Exp", "AA00AA"}};
		test.add(new Fake(t0 + t, "CRYSTAL NUCLEUS LOOT BUNDLE", 0xAA00AA));
		test.add(new Fake(t0 + t, "REWARDS", 0x55FF55));
		for (String[] l : loot) test.add(new Fake(t0 + t, l[0], Integer.parseInt(l[1], 16)));
		test.add(new Fake(t0 + t, "[SkyHanni] Profit for Crystal Nucleus Run: 1.5M", 0xFFFF55));
		test.add(new Fake(t0 + t, "Pick it up near the Nucleus Vault!", 0xAAAAAA));
	}
}
