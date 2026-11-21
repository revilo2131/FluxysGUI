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
 * crystal; each one placed drops into the run's finale ({@link NucleusFinale}), and the fifth sets it
 * off, with the most valuable drop popping up at the climax. A new PB can bring in the buff Scatha.
 * All drawn on the HUD, so you keep playing. Settings: the Nucleus page.
 */
public final class NucleusRun {
	private static final List<String> NAMES = NucleusParser.CRYSTALS;
	private static final int[] COLORS = {0xFFAA00, 0xAA55FF, 0x55FF55, 0x55FFFF, 0xFFFF55};
	/** Each crystal's note (note block steps, 12 = F#4): together they make a chord. */
	private static final int[] NOTE = {12, 15, 19, 22, 24};
	private static final int FLY_MS = 1600, BEAMS_MS = NucleusFinale.FINALE_MS;
	/** Loot worth at least this pops up at the climax (Jaderald, Divan fragments and up). */
	static final double POPUP_MIN = 750_000;

	private static final NucleusParser parser = new NucleusParser();
	// the run
	private static final boolean[] found = new boolean[5], placed = new boolean[5];
	private static long runStart = -1, runEnd = -1;
	private static int foundCount, placedCount, sessionRuns;
	// animations (start times; -1 = not running)
	private static int flyCrystal = -1, flyCount, lastPlaced = -1;
	private static long flyStart, placeStart, beamsStart = -1;
	private static NucleusFinale.Style style; // this run's finale (picked at the first placement)
	private static String forceStyle;         // preview: always this one
	private static List<Loot> all = List.of();
	private static NucleusFinale.Best best; // the most valuable drop, picked at the climax (null = none worth a popup)
	private static boolean bestPicked, pbDone;
	private static long runTime = -1;
	private static boolean pb;
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
			case NucleusParser.Profit p -> {} // SkyHanni's profit line: not shown any more
			case NucleusParser.Bundle b -> bundle(b, at);
		}
	}

	private static void resetRun(long start) {
		java.util.Arrays.fill(found, false);
		java.util.Arrays.fill(placed, false);
		runStart = start;
		runEnd = -1;
		foundCount = placedCount = 0;
		beamsStart = -1;
		all = List.of();
		runTime = -1;
		style = null;
		lastPlaced = -1;
	}

	private static void found(int i, int n, long at) {
		if (i < 0) return;
		if (n == 1 || runEnd >= 0) resetRun(n == 1 ? at : -1); // a new run; joined mid-run = no timer
		SkyblockItems.id("Jaderald"); // start loading the item list and prices now, for the best-drop popup
		cleanui.data.Networth.price("JADERALD");
		found[i] = true;
		foundCount = n;
		if (!Config.get().nucleusAnimations) return;
		flyCrystal = i;
		flyCount = n;
		flyStart = at;
		if (System.currentTimeMillis() - at > 1000) return; // replayed late (a test started part-way): no pile of old sounds
		note(SoundEvents.NOTE_BLOCK_BELL, NOTE[i], 0.9f);
		note(SoundEvents.NOTE_BLOCK_CHIME, Math.min(24, NOTE[i] + 7), 0.5f);
		if (n == 5) for (int k = 0; k < 5; k++) note(SoundEvents.NOTE_BLOCK_PLING, NOTE[k], 0.5f); // all five: the chord
	}

	private static void placed(int i, long at) {
		if (i < 0 || placed[i]) return;
		placed[i] = true;
		placedCount++;
		lastPlaced = i;
		placeStart = at;
		if (style == null) style = NucleusFinale.pick(forceStyle != null ? forceStyle : Config.get().nucleusFinale);
		NucleusFinale.resetCues();
		if (Config.get().nucleusAnimations && System.currentTimeMillis() - at < 1000)
			note(SoundEvents.NOTE_BLOCK_HARP, 4 + placedCount * 3, 0.7f); // climbs one step per crystal
		if (placedCount == 5) finish(at);
	}

	/** The fifth crystal is in: the finale plays, and the loot reveal follows. */
	private static void finish(long at) {
		if (beamsStart >= 0) return;
		runEnd = at;
		if (style == null) style = NucleusFinale.pick(forceStyle != null ? forceStyle : Config.get().nucleusFinale);
		beamsStart = Config.get().nucleusAnimations ? at : at - BEAMS_MS;
		best = null;
		bestPicked = pbDone = pb = false;
		NucleusFinale.resetCues();
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
		all = b.items();
		for (Loot l : all) priceOf(l); // start the price lookups before the climax
	}

	/** What a drop is worth (price × count), or 0 when not known (yet). */
	private static double priceOf(Loot l) {
		String id = SkyblockItems.id(l.name());
		Double p = id == null ? null : cleanui.data.Networth.price(id);
		return p == null ? 0 : p * l.count();
	}

	/** The single most valuable drop, if it's worth a popup. */
	private static NucleusFinale.Best pickBest() {
		Loot top = null;
		double topValue = 0;
		for (Loot l : all) {
			double v = priceOf(l);
			if (v > topValue) {
				top = l;
				topValue = v;
			}
		}
		if (top == null || topValue < POPUP_MIN) return null;
		String value = "≈ " + cleanui.skyblock.SkillParser.compact(String.valueOf(Math.round(topValue))) + " coins";
		String name = top.name() + (top.count() > 1 ? " x" + top.count() : "");
		return new NucleusFinale.Best(icon(top), name, value, top.color() >= 0 ? top.color() : 0xFFAA00, tierOf(top.color()));
	}

	private static int tierOf(int color) {
		return switch (color & 0xFFFFFF) {
			case 0xFFAA00, 0xFF55FF -> 2; // legendary, mythic
			case 0xAA00AA -> 1;           // epic
			default -> 0;
		};
	}

	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		while (!test.isEmpty() && test.get(0).at() <= now) {
			Fake f = test.remove(0);
			handle(parser.line(f.line(), f.color()), f.at());
		}
		if (Config.get().nucleusAnimations && style != null) {
			if (beamsStart >= 0 && now - beamsStart < BEAMS_MS) NucleusFinale.tickFinale(style, now - beamsStart);
			else if (beamsStart < 0 && lastPlaced >= 0 && now - placeStart < NucleusFinale.PLACE_MS) NucleusFinale.tickPlace(style, lastPlaced, now - placeStart);
		}
		if (beamsStart < 0) return;
		// the climax: the most valuable drop pops up (prices have had the whole finale to load)
		// (item list / prices still loading: keep trying for a moment, then give up for this run)
		if (!bestPicked && now - beamsStart >= NucleusFinale.CLIMAX) {
			best = Config.get().nucleusLoot ? pickBest() : null;
			bestPicked = best != null || now - beamsStart > NucleusFinale.CLIMAX + 1200;
			if (best != null && Config.get().nucleusAnimations) ScathaSlots.sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.1f, 0.8f);
		}
		// after the finale: a new PB gets its celebration
		if (!pbDone && pb && now - beamsStart >= BEAMS_MS) {
			pbDone = true;
			ScathaSlots.celebrateNow(2);
			if (Config.get().nucleusPbWalkIn) ScathaSlots.walkInFor("NEW PB! " + time(runTime));
		}
	}

	private static void note(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, int step, float volume) {
		ScathaSlots.play(sound, (float) Math.pow(2, (Math.max(0, Math.min(24, step)) - 12) / 12.0), volume);
	}

	/**
	 * The finale is on: the HUD is swapped for it over a blurred world
	 * (HudMixin), with "Blur while rolling" on. Found/placed animations stay on the normal HUD.
	 */
	public static boolean takeover() {
		if (!Config.get().nucleusFocus) return false;
		long now = System.currentTimeMillis();
		return beamsStart >= 0 && Config.get().nucleusAnimations && now - beamsStart < BEAMS_MS;
	}

	// ---- drawing ----

	public static void render(GuiGraphicsExtractor g) {
		long now = System.currentTimeMillis();
		Font font = Minecraft.getInstance().font;
		if (Config.get().nucleusTracker && (foundCount > 0 || placedCount > 0) && beamsStart < 0) drawTracker(g, font, now);
		if (Config.get().nucleusAnimations) {
			if (beamsStart < 0 && style != null && lastPlaced >= 0 && now - placeStart < NucleusFinale.PLACE_MS)
				NucleusFinale.drawPlace(g, style, placed, lastPlaced, now - placeStart);
			if (flyCrystal >= 0 && now - flyStart < FLY_MS) drawFly(g, font, now);
			if (beamsStart >= 0 && style != null && now - beamsStart < BEAMS_MS) {
				List<ItemStack> loot = all.stream().map(NucleusRun::icon).toList();
				List<ItemStack> stones = java.util.stream.IntStream.range(0, 5).mapToObj(NucleusRun::crystal).toList();
				NucleusFinale.drawFinale(g, style, placed, lastPlaced, now - beamsStart, best, loot, stones);
			}
		}
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
		test(msAgo, null);
	}

	/** When the test run's fifth crystal goes in (ms after it starts): {@code test(FINALE_AT - 400)} jumps to the finale. */
	public static final long FINALE_AT = 5 * 1900 + 300 + 4 * 1500;

	/** A test run with this finale ({@code null} = the setting's). */
	public static void test(long msAgo, String finale) {
		forceStyle = finale;
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
			if (k < 4) t += 1500; // time to watch each crystal drop in
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
