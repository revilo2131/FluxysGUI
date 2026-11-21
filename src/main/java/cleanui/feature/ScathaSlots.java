package cleanui.feature;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import cleanui.data.Config;
import cleanui.data.SkyblockItems;
import cleanui.ui.Theme;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scatha slots: every Scatha kill spins a slot machine. Three Scathas = the pet dropped
 * (driven by Hypixel's "PET DROP! Scatha" chat line). Chat is hidden while a Scatha is
 * low or the reels spin, so the drop message can't spoil the roll.
 */
public final class ScathaSlots {
	/** {@code item} = Hypixel item name (its real head); {@code fallback} shows until the item list has loaded. */
	private record Sym(String name, String item, Item fallback) {}

	private static final Sym[] SYMS = {
			new Sym("Scatha", null, Items.SILVERFISH_SPAWN_EGG),
			new Sym("Dwarven O's Block Bran", "Dwarven O's Block Bran", Items.COOKIE),
			new Sym("Ruby", "Fine Ruby Gemstone", Items.STAINED_GLASS.red()),
			new Sym("Amber", "Fine Amber Gemstone", Items.STAINED_GLASS.orange()),
			new Sym("Sapphire", "Fine Sapphire Gemstone", Items.STAINED_GLASS.lightBlue()),
			new Sym("Jade", "Fine Jade Gemstone", Items.STAINED_GLASS.lime()),
			new Sym("Amethyst", "Fine Amethyst Gemstone", Items.STAINED_GLASS.purple()),
			new Sym("Topaz", "Fine Topaz Gemstone", Items.STAINED_GLASS.yellow())};
	private static final int SCATHA = 0;
	// the Scatha pet's head (pets aren't in Hypixel's item list; texture from the NEU item repo, SCATHA;4)
	private static final String SCATHA_SKIN = "ewogICJ0aW1lc3RhbXAiIDogMTYyMDQ0NTc2NDQ1MSwKICAicHJvZmlsZUlkIiA6ICJmNDY0NTcxNDNkMTU0ZmEwOTkxNjBlNGJmNzI3ZGNiOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWxhcGFnbzA1IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2RmMDNhZDk2MDkyZjNmNzg5OTAyNDM2NzA5Y2RmNjlkZTZiNzI3YzEyMWIzYzJkYWVmOWZmYTFjY2FlZDE4NmMiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";
	private static ItemStack scathaHead;

	/** The Scatha pet's head, for icons elsewhere (settings page, Mining page button). */
	public static ItemStack scathaHead() {
		return icon(SYMS[SCATHA]);
	}

	private static ItemStack icon(Sym s) {
		if (s.item() == null) return scathaHead != null ? scathaHead : (scathaHead = SkyblockItems.head(SCATHA_SKIN));
		ItemStack real = SkyblockItems.stack(s.item());
		return real != null ? real : new ItemStack(s.fallback());
	}

	private static final float LOW_HP = 3;

	// reel timing (ms) and motion: constant spin, then an ease-out-back landing over DECEL
	private static final int[] STOP = {1300, 1900, 2500};
	private static final int TEASE_STOP = 4300, DECIDE = 400, DECEL = 840;
	private static final float SPEED = 15f; // cells per second while spinning
	private static final int CELL = 30, REEL_W = 34, STRIP = 32;
	private static final int DEPTH = 10; // cabinet depth (px) for the 3D look
	private static final float DRUM_DEG = 50; // how far the drum turns per symbol: we see 180°, so ~3.6 symbols

	static final Random RNG = new Random();
	private static final Map<Integer, Float> lastHp = new HashMap<>();
	private static final Map<Integer, Double> lastDist = new HashMap<>();
	private static final Set<Integer> counted = new HashSet<>();
	private static boolean lowNearby;

	// current spin
	private static long start = -1;
	private static boolean jackpot, decided;
	private static final int[] result = new int[3];
	private static final int[][] strips = new int[3][STRIP];
	private static final int[] lastCell = new int[3];
	private static final boolean[] rang = new boolean[3]; // reel's landing sound played
	private static long lastDrum;

	// after 3 Scathas: one more reel for the pet's rarity, read from the drop message
	private record Rarity(String name, int color, int[] confetti) {}

	private static final Rarity[] RARITIES = {
			new Rarity("RARE", 0x5555FF, new int[]{0x5555FF, 0x55AAFF, 0xAAD4FF}),
			new Rarity("EPIC", 0xAA00AA, new int[]{0xAA00AA, 0xCC55FF, 0xFF88FF, 0x7A2BD6}),
			new Rarity("LEGENDARY", 0xFFAA00, new int[]{0xFFAA00, 0xFFD700, 0xFFF1A0, 0xFFFFFF, 0xFF7A00})};
	private static final int[] RARITY_STRIP = {0, 1, 0, 2, 1, 0, 0, 2, 1}; // what flicks past while it spins
	private static final int RARITY_GAP = 900, RARITY_SPIN = 3000, RARITY_W = 96;
	private static int rarity = -1; // index into RARITIES, -1 = drop message didn't say
	private static boolean roulette; // this spin is the roulette wheel (Scatha settings), not the slot machine
	private static boolean none; // game "None": no machine, only the walk-in on a pet drop
	// "Then I scathe in": the buff Scatha walks in from the right instead of the rarity reel / portal
	/** ms: wait after the result, walk in, turn to face you, then (by POSE_MS after arriving) the double biceps is up. */
	private static final int WALK_DELAY = 500, WALK_MS = 1500, WALK_STEPS = 5, TURN_MS = 350, POSE_MS = 800;
	/** After the first double biceps: each pose held this long (the last BLEND_MS blending into the next). */
	private static final int HOLD_MS = 1800, BLEND_MS = 450, ROUTINE_MS = 7200;
	private static final BuffScatha.Look[] ROUTINE = {BuffScatha.DOUBLE_BICEPS, BuffScatha.AB_REVEAL, BuffScatha.LAT_SPREAD, BuffScatha.AB_REVEAL};
	private static int walkStep;
	private static String walkText; // set: a walk-in on its own (nucleus PB) showing this instead of the rarity

	/** This spin is a pet and the walk-in setting is on. */
	private static boolean walkIn() {
		return (Config.get().scathaWalkIn || walkText != null) && decided && won();
	}

	private static long walkStart() {
		return stopAt(2) + (none ? 0 : WALK_DELAY);
	}
	private static boolean rarityRang;
	private static int lastRarityCell, wave;

	private record Bit(float x, float y, float vx, float vy, float rot, float spin, int color, float size, long born) {}

	private static final java.util.List<Bit> confetti = new java.util.ArrayList<>();
	private static long lastFrame;

	private ScathaSlots() {}

	/** Hide chat: a Scatha nearby is on 3 hearts or less, or the machine is up. */
	public static boolean hideChat() {
		return (lowNearby && Config.get().scathaHideChat) || start >= 0;
	}

	/** Chat line hook: "PET DROP! Scatha" makes this (or a fresh) spin a jackpot, with the drop's rarity. */
	public static void onChat(Component message) {
		String s = ChatFormatting.stripFormatting(message.getString());
		if (!Config.get().scathaSlots || !s.contains("PET DROP!") || !s.contains("Scatha")) return;
		int r = Tag.rarity(message.getString());
		if (r < 0) // colour carried as a style, not § codes: the colour of the "Scatha" part
			r = message.visit((style, text) -> text.contains("Scatha") && style.getColor() != null
					? java.util.Optional.of(Tag.rarityOf(style.getColor().getValue())) : java.util.Optional.<Integer>empty(), Style.EMPTY).orElse(-1);
		if (start >= 0 && !decided) jackpot = true;
		else spin(true);
		rarity = r;
	}

	/** Every tick: read Scatha nametags for health and kills. */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			lastHp.clear();
			lastDist.clear();
			counted.clear();
			lowNearby = false;
			return;
		}
		Map<Integer, Float> seen = new HashMap<>();
		boolean low = false;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.getCustomName() == null) continue;
			float hp = Tag.health(ChatFormatting.stripFormatting(e.getCustomName().getString()));
			if (hp < 0) continue;
			int id = e.getId();
			// a Scatha we hadn't seen, right next to you = it just spawned from your mining
			if (!lastHp.containsKey(id) && e.distanceTo(mc.player) < SPAWN_RANGE) autoCoords(mc);
			seen.put(id, hp);
			lastDist.put(id, (double) e.distanceTo(mc.player));
			if (hp <= LOW_HP) low = true;
			LivingEntity mob = mobOf(mc, e);
			if (hp <= 0 || (mob != null && mob.isDeadOrDying())) kill(id);
		}
		// a low Scatha's tag vanishing close by = it died (Hypixel sometimes removes it without a death frame)
		for (Map.Entry<Integer, Float> was : lastHp.entrySet())
			if (!seen.containsKey(was.getKey()) && was.getValue() <= LOW_HP && lastDist.getOrDefault(was.getKey(), 99d) < 16)
				kill(was.getKey());
		counted.retainAll(seen.keySet());
		lastDist.keySet().retainAll(seen.keySet());
		lastHp.clear();
		lastHp.putAll(seen);
		lowNearby = low;
		if (!seen.isEmpty()) SkyblockItems.stack(SYMS[1].item()); // starts loading the real item heads before the first spin
		if (start >= 0) update();
		if (start < 0) {
			wantOverlay = false;
			if (mc.gui.screen() instanceof Overlay) mc.gui.setScreen(null);
		} else if (wantOverlay && mc.gui.screen() == null && Config.get().scathaFocus) { // in a menu it draws over the HUD until the menu closes
			wantOverlay = false;
			mc.gui.setScreen(new Overlay());
		}
	}

	private static final float SPAWN_RANGE = 20;
	private static final long COORDS_COOLDOWN_MS = 15_000;
	private static long lastCoords;

	/** Auto coords (Scatha settings): tell the party where the Scatha is, at most once per 15 s. */
	private static void autoCoords(Minecraft mc) {
		long nowMs = System.currentTimeMillis();
		if (!Config.get().scathaAutoCoords || !cleanui.CleanUi.onHypixel() || nowMs - lastCoords < COORDS_COOLDOWN_MS) return;
		lastCoords = nowMs;
		var p = mc.player.blockPosition();
		mc.getConnection().sendCommand("pc x: " + p.getX() + ", y: " + p.getY() + ", z: " + p.getZ());
	}

	/** Losing outcomes, kept apart (no Minecraft items) so tests can use it. */
	static final class Roll {
		/**
		 * Three reels that aren't a jackpot. Scathas (0) only line up from the left: once a reel
		 * lands on something else, the reels after it can't be a Scatha (no Scatha, O's Block, Scatha).
		 */
		static int[] miss(Random rng, int symbols, float scathaChance) {
			int[] r = new int[3];
			do for (int i = 0; i < 3; i++)
				r[i] = (i == 0 || r[i - 1] == 0) && rng.nextFloat() < scathaChance ? 0 : 1 + rng.nextInt(symbols - 1);
			while (r[0] == 0 && r[1] == 0 && r[2] == 0);
			return r;
		}
	}

	/** Nametag parsing, kept apart so tests can use it without loading Minecraft items. */
	static final class Tag {
		// "[Lv10] Scatha 3/10❤" (current health is the first number after the name)
		private static final Pattern TAG = Pattern.compile("Scatha\\D*?([\\d.,]+)([kKmM]?)(?:/[\\d.,]+[kKmM]?)?❤");

		private static final Pattern DROP_COLOR = Pattern.compile("§([0-9a-fA-F])(?:§[k-oK-O])*Scatha");

		/** Pet rarity from a drop line's § colour before "Scatha": 0 rare, 1 epic, 2 legendary, -1 unknown. */
		static int rarity(String raw) {
			Matcher m = DROP_COLOR.matcher(raw);
			if (!m.find()) return -1;
			return switch (Character.toLowerCase(m.group(1).charAt(0))) {
				case '9' -> 0;
				case '5' -> 1;
				case '6' -> 2;
				default -> -1;
			};
		}

		/** Same, from an RGB text colour (Minecraft's blue / dark purple / gold). */
		static int rarityOf(int rgb) {
			return switch (rgb & 0xFFFFFF) {
				case 0x5555FF -> 0;
				case 0xAA00AA -> 1;
				case 0xFFAA00 -> 2;
				default -> -1;
			};
		}

		/** A Scatha nametag's current health, or -1 if it isn't one. */
		static float health(String plainName) {
			Matcher m = TAG.matcher(plainName);
			if (!m.find()) return -1;
			try {
				float v = Float.parseFloat(m.group(1).replace(",", ""));
				return switch (m.group(2).toLowerCase()) {
					case "k" -> v * 1e3f;
					case "m" -> v * 1e6f;
					default -> v;
				};
			} catch (NumberFormatException ex) {
				return -1;
			}
		}
	}

	/** The mob a nametag belongs to: itself, or the closest non-armor-stand under the floating stand. */
	private static LivingEntity mobOf(Minecraft mc, Entity tag) {
		if (tag instanceof LivingEntity l && !(tag instanceof ArmorStand)) return l;
		LivingEntity best = null;
		double bestD = 9;
		for (Entity e : mc.level.entitiesForRendering())
			if (e instanceof LivingEntity l && !(e instanceof ArmorStand) && e != mc.player) {
				double dx = e.getX() - tag.getX(), dz = e.getZ() - tag.getZ(), dy = tag.getY() - e.getY();
				double d = dx * dx + dz * dz;
				if (dy > -1 && dy < 4 && d < 2.25 && d < bestD) {
					best = l;
					bestD = d;
				}
			}
		return best;
	}

	private static void kill(int id) {
		if (!counted.add(id) || !Config.get().scathaSlots || "NONE".equals(Config.get().scathaGame)) return;
		// a kill while the reels are still rolling waits its turn; one showing its result gets replaced
		if (start >= 0 && (now() < stopAt(2) || won())) return; // never cut a pet celebration short
		spin(false);
	}

	/** /scathaslots [win]: spin without a kill, to see and hear it. */
	public static void spin(boolean pet) {
		boolean noGame = "NONE".equals(Config.get().scathaGame);
		if (noGame && (!pet || !Config.get().scathaWalkIn)) return; // no game: only a pet drop's walk-in shows
		start = System.currentTimeMillis();
		jackpot = pet;
		decided = false;
		none = noGame;
		if (none) { // nothing to roll: the outcome is known now
			java.util.Arrays.fill(result, SCATHA);
			decided = true;
		}
		walkStep = -1;
		walkText = null;
		for (int[] s : strips) for (int i = 0; i < STRIP; i++) s[i] = randomSym(0.18f);
		java.util.Arrays.fill(lastCell, Integer.MIN_VALUE);
		java.util.Arrays.fill(rang, false);
		rarity = -1;
		roulette = "ROULETTE".equals(Config.get().scathaGame);
		ScathaRoulette.reset();
		ScathaMusic.reset();
		rarityRang = false;
		lastRarityCell = 0;
		wave = 0;
		confetti.clear();
		play(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.6f, 1f); // lever pull
		play(SoundEvents.UI_BUTTON_CLICK, 0.8f, 0.5f);
		SkyblockItems.stack(SYMS[1].item()); // load the real heads if nothing has yet
		wantOverlay = true; // opened next tick: /scathaslots runs while the chat box is still closing
	}

	private static boolean wantOverlay;

	/**
	 * While rolling: the world blurred behind the machine and the whole HUD hidden (like F1),
	 * so other mods' overlays and chat are gone too. Doesn't pause; Esc closes it.
	 */
	private static final class Overlay extends Screen {
		private final boolean hidHud;

		Overlay() {
			super(Component.literal("Scatha Slots"));
			Hud hud = Minecraft.getInstance().gui.hud;
			hidHud = !hud.isHidden();
			if (hidHud) hud.toggle();
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			super.extractRenderState(g, mouseX, mouseY, delta);
			NucleusRun.render(g);
			ScathaSlots.render(g);
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}

		@Override
		public void removed() {
			Hud hud = Minecraft.getInstance().gui.hud;
			if (hidHud && hud.isHidden()) hud.toggle();
		}
	}

	/** Drawn on the HUD only when the blur screen isn't up (e.g. a kill while a menu was open). */
	public static void renderHud(GuiGraphicsExtractor g) {
		if (!(Minecraft.getInstance().gui.screen() instanceof Overlay)) render(g);
	}

	private static int randomSym(float scathaChance) {
		return RNG.nextFloat() < scathaChance ? SCATHA : 1 + RNG.nextInt(SYMS.length - 1);
	}

	private static long now() {
		return System.currentTimeMillis() - start;
	}

	private static boolean tease() {
		return decided && result[0] == SCATHA && result[1] == SCATHA;
	}

	/** When a reel stops; for roulette every "reel" is the ball landing. */
	private static int stopAt(int reel) {
		if (none) return 0;
		if (roulette) return ScathaRoulette.LAND;
		return reel == 2 && tease() ? TEASE_STOP : STOP[reel];
	}

	/** Strip cell the reel lands on. */
	private static int landCell(int reel) {
		return land(stopAt(reel));
	}

	private static float position(int reel, long t) {
		return pos(stopAt(reel), t);
	}

	private static int land(int stop) {
		return (int) (SPEED * (stop - DECEL) / 1000f) + 3;
	}

	/** Reel position in cells {@code t} ms into a spin that stops at {@code stop} ms. */
	private static float pos(int stop, long t) {
		float a = SPEED * (stop - DECEL) / 1000f;
		if (t < stop - DECEL) return SPEED * Math.max(0, t) / 1000f;
		float s = Math.min(1f, (t - (stop - DECEL)) / (float) DECEL) - 1f, c1 = 1.2f, c3 = c1 + 1;
		return a + (land(stop) - a) * (1 + c3 * s * s * s + c1 * s * s); // ease-out-back: lands with a small bounce
	}

	/** When the rarity reel starts (after 3 Scathas), and when its result is shown. */
	private static long rarityStart() {
		return stopAt(2) + RARITY_GAP;
	}

	/** When the pet's rarity shows: after the rarity reel for slots, right as the ball lands for roulette. */
	private static long celebrate() {
		if (walkIn()) return walkStart() + WALK_MS + POSE_MS; // the rarity shows as he hits the pose
		if (roulette) return stopAt(2) + (decided && won() ? ScathaRoulette.SUMMON : 0); // the pet comes out of its portal first
		return rarity >= 0 ? rarityStart() + RARITY_SPIN : stopAt(2);
	}

	private static int rarityAt(int cell) {
		return cell == land(RARITY_SPIN) && rarity >= 0 ? rarity : RARITY_STRIP[Math.floorMod(cell, RARITY_STRIP.length)];
	}

	private static int symAt(int reel, int cell) {
		return decided && cell == landCell(reel) ? result[reel] : strips[reel][Math.floorMod(cell, STRIP)];
	}

	private static boolean won() {
		return result[0] == SCATHA && result[1] == SCATHA && result[2] == SCATHA;
	}

	/** Sounds and the outcome, driven by time. */
	private static void update() {
		long t = now();
		if (!decided && t >= DECIDE) {
			// pet drop chat comes with the kill, so by now we know; otherwise anything but 3 Scathas (near misses welcome)
			if (jackpot) java.util.Arrays.fill(result, SCATHA);
			else if (roulette) System.arraycopy(new int[]{1, 2, 3}, 0, result, 0, 3); // just "not a win"
			else System.arraycopy(Roll.miss(RNG, SYMS.length, 0.35f), 0, result, 0, 3);
			if (roulette) ScathaRoulette.decide(jackpot, RNG);
			decided = true;
		}
		if (roulette) {
			ScathaRoulette.update(t, decided && won() && !walkIn());
			if (t >= stopAt(2) && !rang[2]) {
				rang[2] = true;
				if (!won()) play(SoundEvents.NOTE_BLOCK_XYLOPHONE, 0.8f, 0.5f); // a win celebrates below
			}
		}
		boolean ticked = false;
		for (int r = 0; r < 3 && !roulette && !none; r++) {
			int cell = (int) Math.floor(position(r, t));
			if (lastCell[r] == Integer.MIN_VALUE) lastCell[r] = cell;
			if (t < stopAt(r) && cell != lastCell[r] && !ticked) {
				play(SoundEvents.NOTE_BLOCK_HAT, 1.4f + RNG.nextFloat() * 0.4f, 0.35f);
				ticked = true;
			}
			lastCell[r] = cell;
			if (t >= stopAt(r) && !rang[r]) reelStop(r);
		}
		// the last reel teases with a heartbeat that speeds up
		if (tease() && t > STOP[1] && t < TEASE_STOP && t - lastDrum > 520 - (t - STOP[1]) / 8) {
			lastDrum = t;
			play(SoundEvents.NOTE_BLOCK_BASEDRUM, 0.5f + (t - STOP[1]) / 4000f, 0.9f);
		}
		if (decided && won()) {
			// note block music: build-up from the moment the pet is certain, the drop as the rarity shows
			if (Config.get().scathaMusic) ScathaMusic.update(t, stopAt(2), celebrate(), tier());
			// walk-in: a heavy footstep on each stride
			if (walkIn() && t >= walkStart()) {
				int stepNow = (int) Math.min(WALK_STEPS, (t - walkStart()) * WALK_STEPS / WALK_MS);
				if (stepNow != walkStep && stepNow < WALK_STEPS) {
					sound(SoundEvents.RAVAGER_STEP, 0.7f, 1f);
					if (walkStep < 0) sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 1f);
				}
				walkStep = stepNow;
			}
			if (rarity >= 0 && !roulette && !walkIn() && t >= rarityStart() && t < celebrate()) {
				int cell = (int) Math.floor(pos(RARITY_SPIN, t - rarityStart()));
				if (cell != lastRarityCell) play(SoundEvents.NOTE_BLOCK_HAT, 1.2f + RNG.nextFloat() * 0.3f, 0.45f);
				lastRarityCell = cell;
			}
			if (t >= celebrate() && !rarityRang) {
				rarityRang = true;
				celebrateNow(tier());
			}
			// legendary: more confetti cannons and fireworks every 0.6s for a while
			if (rarityRang && tier() == 2 && wave < 7 && t >= celebrate() + 600L * (wave + 1)) {
				wave++;
				cannons(45);
				sound(wave % 2 == 0 ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST : SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.9f + RNG.nextFloat() * 0.3f, 1f);
			}
		}
		if (t > end()) start = -1;
	}

	/** Unknown rarity (drop message without a colour) celebrates like an epic. */
	private static int tier() {
		return rarity >= 0 ? rarity : 1;
	}

	private static boolean muted; // sounds off for a moment (confetti without its jingle)

	/** Just the confetti, no jingle: for moments that bring their own sound (Vanguard drops). */
	static void confettiOnly(int tier) {
		muted = true;
		try {
			celebrateNow(tier);
		} finally {
			muted = false;
		}
	}

	/** Confetti and sounds for a rarity tier (0 rare, 1 epic, 2 legendary); also used by nucleus runs. */
	static void celebrateNow(int tier) {
		int[] colors = RARITIES[tier].confetti();
		float cx = screenW() / 2f, cy = screenH() / 2f;
		boolean jingles = !Config.get().scathaMusic; // with music on, the drop is the sound; jingles would clash
		switch (tier) {
			case 0 -> { // rare: a little blue confetti
				spawn(45, cx, cy, -90, 140, 120, 260, colors);
				if (jingles) {
					play(SoundEvents.NOTE_BLOCK_CHIME, 1.2f, 0.8f);
					play(SoundEvents.NOTE_BLOCK_CHIME, 1.5f, 0.8f);
				}
				sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
			}
			case 1 -> { // epic: more, purple
				spawn(130, cx, cy, -90, 200, 150, 360, colors);
				for (int i = 0; i < 50; i++) confetti.add(bit(RNG.nextFloat() * screenW(), -10 - RNG.nextFloat() * 60, 90, 20, 20, 60, colors));
				if (jingles) {
					sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
					play(SoundEvents.NOTE_BLOCK_CHIME, 1.0f, 0.9f);
				}
				sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1f);
			}
			default -> { // legendary: everything
				spawn(220, cx, cy, -90, 360, 160, 460, colors);
				cannons(90);
				for (int i = 0; i < 80; i++) confetti.add(bit(RNG.nextFloat() * screenW(), -10 - RNG.nextFloat() * 120, 90, 20, 20, 60, colors));
				if (jingles) {
					sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
					sound(SoundEvents.PLAYER_LEVELUP, 1f, 1f);
				}
				sound(SoundEvents.TOTEM_USE, 1f, 0.7f);
				sound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
			}
		}
	}

	/** Confetti fired up and inwards from both bottom corners. */
	private static void cannons(int each) {
		int[] colors = RARITIES[2].confetti();
		spawn(each, 0, screenH(), -60, 25, 300, 520, colors);
		spawn(each, screenW(), screenH(), -120, 25, 300, 520, colors);
	}

	private static void spawn(int n, float x, float y, float angle, float spread, float vMin, float vMax, int[] colors) {
		for (int i = 0; i < n; i++) confetti.add(bit(x, y, angle, spread, vMin, vMax, colors));
	}

	private static Bit bit(float x, float y, float angle, float spread, float vMin, float vMax, int[] colors) {
		double a = Math.toRadians(angle + (RNG.nextFloat() - 0.5f) * spread);
		float v = vMin + RNG.nextFloat() * (vMax - vMin);
		return new Bit(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v, RNG.nextFloat() * 6.28f,
				(RNG.nextFloat() - 0.5f) * 14f, colors[RNG.nextInt(colors.length)], 2f + RNG.nextFloat() * 2.5f, System.currentTimeMillis());
	}

	private static int screenW() {
		return Minecraft.getInstance().getWindow().getGuiScaledWidth();
	}

	private static int screenH() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight();
	}

	static void sound(SoundEvent sound, float pitch, float volume) {
		if (Config.get().scathaSounds && !muted) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	private static void reelStop(int r) {
		rang[r] = true;
		play(SoundEvents.NOTE_BLOCK_BASEDRUM, 1f, 1f);
		play(SoundEvents.NOTE_BLOCK_BELL, result[r] == SCATHA ? 1.5f + 0.2f * r : 0.9f, 0.7f);
		if (r < 2) return;
		if (won()) { // 3 Scathas; the big sounds wait for the rarity
			sound(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.8f);
			boolean now = rarity < 0 && !walkIn(); // otherwise after the rarity reel or the walk-in
			if (now) celebrateNow(tier());
			rarityRang = now;
		} else if (tease()) play(SoundEvents.NOTE_BLOCK_PLING, 0.6f, 0.8f); // so close...
		else play(SoundEvents.NOTE_BLOCK_XYLOPHONE, 0.8f, 0.5f);
	}

	private static long end() {
		if (!(decided && won())) return stopAt(2) + 2200;
		return celebrate() + Math.max(new int[]{3500, 5000, 9000}[tier()], walkIn() ? ROUTINE_MS : 0); // the walk-in shows his whole routine
	}

	/** A note of the win music: needs both Sounds and Music on. */
	static void music(Holder<SoundEvent> sound, float pitch, float volume) {
		if (Config.get().scathaMusic) sound(sound.value(), pitch, volume);
	}

	static void play(Holder<SoundEvent> sound, float pitch, float volume) {
		sound(sound.value(), pitch, volume);
	}

	// ---- drawing ----

	public static void render(GuiGraphicsExtractor g) {
		if (start < 0) {
			drawConfetti(g); // nucleus runs throw confetti without a spin
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		long t = now(), e = end();
		boolean done = t >= stopAt(2), win = done && won();
		// pops in at the middle of the screen, as big as fits (up to 3x), shrinks away at the end
		float in = Math.min(1f, t / 250f), out = Math.min(1f, Math.max(0f, (e - t) / 300f));
		float show = 1 - (1 - in * out) * (1 - in * out);
		int w = roulette ? ScathaRoulette.W : 3 * REEL_W + 4 * 6, h = roulette ? ScathaRoulette.H : 3 * CELL + 12;
		float scale = Math.min(3f, Math.min(g.guiHeight() * 0.75f / (h + 90), g.guiWidth() * 0.8f / (w + 30))) * (0.4f + 0.6f * show);
		boolean revealed = win && t >= celebrate();
		// legendary: a white flash and the machine shakes for a moment
		float since = t - celebrate();
		float shake = revealed && tier() == 2 && since < 700 ? (1 - since / 700f) * 3f : 0f;
		Matrix3x2fStack root = g.pose();
		root.pushMatrix();
		root.translate(g.guiWidth() / 2f + (RNG.nextFloat() - 0.5f) * shake * scale, g.guiHeight() / 2f - 20 + (RNG.nextFloat() - 0.5f) * shake * scale);
		root.scale(scale, scale);
		int x = -w / 2, y = -h / 2;
		if (none) {
			// no machine: just the walk-in below
		} else if (roulette) {
			int accent = revealed ? RARITIES[tier()].color() : 0xFFC83D;
			ScathaRoulette.draw(g, font, t, revealed, accent, decided && won() && !walkIn(), RARITIES[tier()].color());
			if (done && (!win || (revealed && !walkIn()))) drawMessage(g, font, t, 0, ScathaRoulette.MESSAGE_Y, win, revealed, accent);
		} else draw(g, font, t, e, done, win, x, y, w, h);
		root.popMatrix();
		if (revealed && tier() == 2 && since < 500)
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), Math.round(200 * (1 - since / 500f)) << 24 | 0xFFF6D0);
		drawWalkIn(g, font, t);
		drawConfetti(g);
	}

	/**
	 * "Then I scathe in": the world dims and the buff Scatha (a 3D block model) stomps in from the
	 * right with the caption typing out above him, turns to face you, hits a double biceps, and the
	 * pet's rarity shows below.
	 */
	private static void drawWalkIn(GuiGraphicsExtractor g, Font font, long t) {
		if (!walkIn() || t < walkStart()) return;
		long wt = t - walkStart();
		float u = Math.min(1f, wt / (float) WALK_MS);
		int sw = g.guiWidth(), sh = g.guiHeight();
		g.fill(0, 0, sw, sh, Math.round(150 * Math.min(1f, wt / 300f)) << 24);
		float scale = sh * 0.55f / 44; // ~44 model units tall with the arms up
		float feetY = sh / 2f + 20 * scale;
		float ease = 1 - (1 - u) * (1 - u) * (1 - u);
		float startX = sw + 16 * scale, feetX = startX + (sw / 2f - startX) * ease;
		// walk facing left, then turn toward you, then raise the arms; a slow pump once he's posing
		BuffScatha.Pose pose = new BuffScatha.Pose(look(wt), 0.16f, (float) (u * Math.PI * WALK_STEPS), 1 - smooth((wt - WALK_MS) / (float) TURN_MS));
		ScathaRoulette.ellipse(g, feetX, feetY + 1, 15 * scale, 3 * scale, 0x70000000, 0x30000000); // shadow on the floor
		BuffScatha.draw(g, feetX, feetY, scale, pose);
		Matrix3x2fStack m = g.pose();
		// caption, typed out as he walks
		String caption = "Then I scathe in";
		int shown = (int) Math.min(caption.length(), Math.max(0, (wt - 150) / 55));
		if (shown > 0) {
			m.pushMatrix();
			m.translate(sw / 2f, feetY - 46 * scale - 30);
			m.scale(2.4f, 2.4f);
			g.text(font, caption.substring(0, shown), -font.width(caption) / 2, 0, 0xFFFFFFFF, true);
			m.popMatrix();
		}
		// the rarity, as he hits the pose
		if (t >= celebrate() && walkText != null) drawBanner(g, font, t, walkText, sw / 2f, Math.round(feetY + 12));
		else if (t >= celebrate()) drawMessage(g, font, t, sw / 2f, Math.round(feetY + 12), true, true, RARITIES[tier()].color());
	}

	/**
	 * His pose {@code wt} ms into the walk-in: walking, then turning into a double biceps (the rarity
	 * shows as it lands), then the routine, each pose held a moment and blended into the next.
	 */
	private static BuffScatha.Look look(long wt) {
		if (wt < WALK_MS) return BuffScatha.WALK;
		if (wt < WALK_MS + POSE_MS) return BuffScatha.WALK.lerp(BuffScatha.DOUBLE_BICEPS, smooth((wt - WALK_MS) / (float) POSE_MS));
		long rt = wt - WALK_MS - POSE_MS;
		int i = (int) (rt / HOLD_MS);
		BuffScatha.Look from = ROUTINE[i % ROUTINE.length], to = ROUTINE[(i + 1) % ROUTINE.length];
		return from.lerp(to, smooth((rt % HOLD_MS - (HOLD_MS - BLEND_MS)) / (float) BLEND_MS));
	}

	private static float smooth(float k) {
		k = Math.max(0f, Math.min(1f, k));
		return k * k * (3 - 2 * k);
	}

	/** Falling, fluttering paper bits in screen space; frame-rate independent. */
	private static void drawConfetti(GuiGraphicsExtractor g) {
		long nowMs = System.currentTimeMillis();
		float dt = Math.min(0.05f, (nowMs - lastFrame) / 1000f);
		lastFrame = nowMs;
		Matrix3x2fStack pose = g.pose();
		for (int i = confetti.size() - 1; i >= 0; i--) {
			Bit b = confetti.get(i);
			float age = (nowMs - b.born()) / 1000f;
			float drag = (float) Math.pow(0.25, dt); // air slows them, then they drift down
			float vx = b.vx() * drag + (float) Math.sin(age * 4 + b.rot()) * 12 * dt, vy = b.vy() * drag + 260 * dt;
			vy = Math.min(vy, 30 + 13 * b.size()); // paper falls slowly, each bit at its own speed (else a burst settles into a line)
			Bit n = new Bit(b.x() + vx * dt, b.y() + vy * dt, vx, vy, b.rot() + b.spin() * dt, b.spin(), b.color(), b.size(), b.born());
			if (n.y() > g.guiHeight() + 10 || age > 7) {
				confetti.remove(i);
				continue;
			}
			confetti.set(i, n);
			int alpha = age > 6 ? Math.round(255 * (7 - age)) : 255;
			pose.pushMatrix();
			pose.translate(n.x(), n.y());
			pose.rotate(n.rot());
			float flip = Math.abs((float) Math.cos(n.rot() * 1.7f)) * n.size() + 0.6f; // tumbling: width shrinks and grows
			pose.scale(flip, n.size() * 0.6f);
			g.fill(-1, -1, 1, 1, alpha << 24 | n.color());
			pose.popMatrix();
		}
	}

	private static void draw(GuiGraphicsExtractor g, Font font, long t, long e, boolean done, boolean win, int x, int y, int w, int h) {

		// gold cabinet; takes the pet's rarity colour once it's revealed
		boolean revealed = win && t >= celebrate();
		int accent = revealed ? RARITIES[tier()].color() : 0xFFC83D;
		int gold = revealed ? Theme.mix(0xFFC83D, accent, 0.6f) : 0xFFC83D;
		// 3D cabinet: front face, lit top and shaded right side drawn as an oblique box (depth goes up-right)
		int fx0 = x - 3, fy0 = y - 15, fx1 = x + w + 3, fy1 = y + h + 3;
		int light = 0xFF000000 | Theme.mix(gold, 0xFFFFFF, 0.35f), dark = 0xFF000000 | Theme.mix(gold, 0x2A1800, 0.55f);
		g.fillGradient(fx0 + 6, fy1 + 1, fx1 + DEPTH + 2, fy1 + 7, 0x50000000, 0x00000000); // shadow on the ground
		for (int i = 0; i < DEPTH; i++) {
			g.fillGradient(fx1 + i, fy0 - i - 1, fx1 + i + 1, fy1 - i, 0xFF000000 | Theme.mix(dark, 0x000000, i * 0.03f), 0xFF000000 | Theme.mix(dark, 0x000000, 0.35f));
			g.fill(fx0 + i + 1, fy0 - i - 1, fx1 + i + 1, fy0 - i, 0xFF000000 | Theme.mix(light, gold, i / (float) DEPTH));
		}
		g.fill(fx0 - 1, fy0 - 1, fx1 + 1, fy1 + 1, 0xFF4A2E00);
		g.fillGradient(fx0, fy0, fx1, fy1, gold | 0xFF000000, 0xFFB8860B);
		g.fill(fx0, fy0, fx1, fy0 + 1, 0xA0FFFFFF); // bevel: lit top and left edge, dark bottom
		g.fill(fx0, fy0, fx0 + 1, fy1, 0x70FFFFFF);
		g.fill(fx0, fy1 - 1, fx1, fy1, 0x60000000);
		// marquee plate, sunk into the front
		g.fill(x + 14, y - 13, x + w - 14, y - 2, 0xFF2A1800);
		g.fill(x + 14, y - 3, x + w - 14, y - 2, 0x60FFFFFF);
		g.text(font, "SCATHA SLOTS", x + (w - font.width("SCATHA SLOTS")) / 2, y - 11, revealed ? 0xFF000000 | Theme.mix(accent, 0xFFFFFF, 0.3f) : 0xFFFFE38A, true);
		// chase lights along the edge
		for (int i = 0; i < w + 6; i += 8) {
			boolean on = ((i / 8) + (t / (done ? 300 : 90))) % 2 == 0;
			int c = on ? (revealed ? 0xFF000000 | Theme.mix(accent, 0xFFFFFF, 0.4f) : 0xFFFFF4B0) : 0xFF8A5A00;
			g.fill(x - 3 + i, y + h, x - 1 + i, y + h + 2, c);
			g.fill(x - 3 + i, y - 15 + 1, x - 1 + i, y - 15 + 3, c);
		}
		// lever on the side: knocked down when the spin starts, springs back
		float pull = t < 400 ? (float) Math.sin(Math.PI * t / 400f) : 0f;
		int hubX = fx1 + DEPTH / 2, hubY = y + h / 2 - DEPTH / 2;
		int ballY = hubY - 30 + Math.round(pull * 46);
		g.fill(hubX - 3, hubY - 5, hubX + 4, hubY + 6, 0xFF6A4400);
		g.fill(hubX - 2, hubY - 4, hubX + 3, hubY + 5, 0xFFB8860B);
		g.fill(hubX + 6, Math.min(ballY, hubY), hubX + 8, Math.max(ballY, hubY), 0xFFCFCFCF);
		g.fill(hubX + 7, Math.min(ballY, hubY), hubX + 8, Math.max(ballY, hubY), 0xFF8A8A8A);
		g.fill(hubX + 3, hubY - 1, hubX + 8, hubY + 2, 0xFF9A9A9A);
		ball(g, hubX + 7, ballY, 6, 0xE02020);

		for (int r = 0; r < 3; r++) {
			int rx = x + 6 + r * (REEL_W + 6), ry = y + 6, rh = 3 * CELL;
			boolean landed = t >= stopAt(r);
			// recessed window: dark inner shadow top/left, light lip bottom/right
			g.fill(rx - 2, ry - 2, rx + REEL_W + 2, ry + rh + 2, 0xFF2A1800);
			g.fill(rx - 1, ry + rh, rx + REEL_W + 2, ry + rh + 2, 0xFFFFE9A0);
			g.fill(rx + REEL_W, ry - 1, rx + REEL_W + 2, ry + rh + 2, 0xFFFFE9A0);
			drawDrum(g, rx, ry, rh, position(r, t), r, landed, revealed ? Theme.mix(0xEFE6D2, accent, 0.3f) : 0xEFE6D2);
			// payline: the edges of the middle symbol's slot on the drum
			int half = Math.round(rh / 2f * (float) Math.sin(Math.toRadians(DRUM_DEG / 2)));
			int line = landed && result[r] == SCATHA ? 0xFFFFD700 : 0x90FF4040;
			g.fill(rx - 2, ry + rh / 2 - half, rx + REEL_W + 2, ry + rh / 2 - half + 1, line);
			g.fill(rx - 2, ry + rh / 2 + half - 1, rx + REEL_W + 2, ry + rh / 2 + half, line);
		}

		if (!done) return;
		int msgY = y + h + 14;
		if (walkIn()) return; // the walk-in shows the rarity
		if (win && rarity >= 0 && t >= rarityStart() - 250) {
			drawRarityReel(g, font, t, x + (w - RARITY_W) / 2, y + h + 10);
			msgY = y + h + 10 + CELL + 10;
		}
		drawMessage(g, font, t, x + w / 2f, msgY, win, revealed, accent);
	}

	/** The result line under either game: the pet and its rarity, or what you landed on. */
	static void drawMessage(GuiGraphicsExtractor g, Font font, long t, float cx, int msgY, boolean win, boolean revealed, int accent) {
		String msg;
		int color;
		float scale = 1f;
		if (revealed) {
			msg = (rarity >= 0 ? RARITIES[rarity].name() + " " : "") + "SCATHA PET!";
			// glints toward white now and then (no rainbow); punches in once when revealed
			float glint = (float) Math.max(0, Math.sin((t - celebrate()) / 260.0)) * 0.45f;
			color = 0xFF000000 | Theme.mix(accent, 0xFFFFFF, glint);
			float since = t - celebrate();
			scale = 1.5f + (tier() == 2 ? 0.5f : 0f) + Math.max(0f, 1 - since / 220f) * 1.5f;
		} else if (win) {
			msg = "SCATHA PET!";
			color = 0xFFFFE38A;
			scale = 1.3f;
		} else if (roulette) {
			msg = ScathaRoulette.pocketName();
			color = 0xFF000000 | ScathaRoulette.pocketTextColor();
			scale = 1.3f;
		} else {
			msg = tease() ? "So close..." : SYMS[result[0]].name() + " • " + SYMS[result[1]].name() + " • " + SYMS[result[2]].name();
			color = tease() ? 0xFFFFAA00 : 0xFFDDDDDD;
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, msgY);
		pose.scale(scale, scale);
		g.text(font, msg, -font.width(msg) / 2, 0, color, true);
		pose.popMatrix();
	}

	/** The single rarity reel: cards scroll past and land on the drop's real rarity. */
	private static void drawRarityReel(GuiGraphicsExtractor g, Font font, long t, int rx, int ry) {
		long rt = t - rarityStart();
		boolean landed = rt >= RARITY_SPIN;
		g.fill(rx - 2, ry - 2, rx + RARITY_W + 2, ry + CELL + 2, 0xFF5A3A00);
		g.fill(rx - 1, ry - 1, rx + RARITY_W + 1, ry + CELL + 1, 0xFFFFC83D);
		g.fill(rx, ry, rx + RARITY_W, ry + CELL, 0xFF140E30);
		g.enableScissor(rx, ry, rx + RARITY_W, ry + CELL);
		float p = pos(RARITY_SPIN, rt);
		for (int c = (int) Math.floor(p) - 1; c <= (int) Math.floor(p) + 1; c++) {
			Rarity r = RARITIES[rarityAt(c)];
			int cy = ry + Math.round((c - p) * CELL);
			g.fill(rx + 2, cy + 2, rx + RARITY_W - 2, cy + CELL - 2, 0xFF000000 | Theme.mix(0x140E30, r.color(), landed ? 0.55f : 0.35f));
			item(g, icon(SYMS[SCATHA]), rx + 14, cy + CELL / 2f, 1f);
			g.text(font, r.name(), rx + 26 + (RARITY_W - 28 - font.width(r.name())) / 2, cy + (CELL - 8) / 2, 0xFF000000 | Theme.mix(r.color(), 0xFFFFFF, 0.35f), true);
		}
		g.fillGradient(rx, ry, rx + RARITY_W, ry + 6, 0xC0000000, 0x00000000);
		g.fillGradient(rx, ry + CELL - 6, rx + RARITY_W, ry + CELL, 0x00000000, 0xC0000000);
		g.disableScissor();
	}

	private static void item(GuiGraphicsExtractor g, ItemStack stack, float cx, float cy, float scale) {
		item(g, stack, cx, cy, scale, scale);
	}

	static void item(GuiGraphicsExtractor g, ItemStack stack, float cx, float cy, float sx, float sy) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(sx, sy);
		g.item(stack, -8, -8);
		pose.popMatrix();
	}

	/**
	 * One reel seen from the front as a real drum: we see its front half, so a point at angle a
	 * sits at y = R·sin(a) and is lit by cos(a). Symbols and divider lines bunch up and darken as
	 * they roll over the top and bottom, which is what makes it read as turning.
	 */
	private static void drawDrum(GuiGraphicsExtractor g, int rx, int ry, int rh, float p, int reel, boolean landed, int surface) {
		float radius = rh / 2f, cy0 = ry + radius;
		double step = Math.toRadians(DRUM_DEG);
		int rim = 3;
		g.enableScissor(rx, ry, rx + REEL_W, ry + rh);
		// strip and metal rims, shaded band by band like a cylinder, with a soft highlight above the middle
		for (int yy = 0; yy < rh; yy += 2) {
			float dy = (yy + 1 - radius) / radius, lit = (float) Math.sqrt(Math.max(0, 1 - dy * dy));
			float shine = (float) Math.exp(-Math.pow((dy + 0.35f) / 0.18f, 2)) * 0.45f;
			int strip = Theme.mix(Theme.mix(0x000000, surface, 0.15f + 0.85f * lit), 0xFFFFFF, shine);
			int metal = Theme.mix(Theme.mix(0x000000, 0x9A9AA6, 0.15f + 0.85f * lit), 0xFFFFFF, shine * 1.4f);
			g.fill(rx + rim, ry + yy, rx + REEL_W - rim, ry + yy + 2, 0xFF000000 | strip);
			g.fill(rx, ry + yy, rx + rim, ry + yy + 2, 0xFF000000 | metal);
			g.fill(rx + REEL_W - rim, ry + yy, rx + REEL_W, ry + yy + 2, 0xFF000000 | metal);
		}
		g.fill(rx + rim, ry, rx + rim + 1, ry + rh, 0x50000000);
		g.fill(rx + REEL_W - rim - 1, ry, rx + REEL_W - rim, ry + rh, 0x50000000);
		// dividers between symbols, riding on the drum
		for (int c = (int) Math.floor(p) - 3; c <= (int) Math.floor(p) + 3; c++) {
			double a = (c + 0.5 - p) * step;
			if (Math.abs(a) >= Math.PI / 2) continue;
			int ly = Math.round(cy0 + (float) Math.sin(a) * radius);
			g.fill(rx + rim, ly, rx + REEL_W - rim, ly + 1, Math.round(110 * (float) Math.cos(a)) << 24);
		}
		// symbols on the surface: squashed by cos(a)
		for (int c = (int) Math.floor(p) - 3; c <= (int) Math.floor(p) + 3; c++) {
			double a = (c - p) * step;
			if (Math.abs(a) >= Math.PI / 2) continue;
			float s = landed && c == landCell(reel) ? 1.5f : 1.3f;
			item(g, icon(SYMS[symAt(reel, c)]), rx + REEL_W / 2f, cy0 + (float) Math.sin(a) * radius, s, s * (float) Math.cos(a));
		}
		// a new layer so the shading lands on top of the item icons, not under them
		g.nextStratum();
		for (int yy = 0; yy < rh; yy += 2) {
			float dy = (yy + 1 - radius) / radius, dark = 1 - (float) Math.sqrt(Math.max(0, 1 - dy * dy));
			if (dark > 0.02f) g.fill(rx, ry + yy, rx + REEL_W, ry + yy + 2, Math.round(235 * Math.min(1f, dark * 1.15f)) << 24);
		}
		g.fillGradient(rx, ry, rx + REEL_W, ry + rh / 3, 0x22FFFFFF, 0x00FFFFFF); // glass glare
		g.fill(rx + REEL_W - 7, ry + 3, rx + REEL_W - 5, ry + rh - 3, 0x16FFFFFF);
		g.disableScissor();
	}

	/** A shaded sphere (the lever knob): dark rim, lit upper-left, small shine. */
	static void ball(GuiGraphicsExtractor g, int cx, int cy, int r, int rgb) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
			float lit = 0.5f - dy / (2f * r); // brighter toward the top
			g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0xFF000000 | Theme.mix(Theme.mix(rgb, 0x000000, 0.45f), rgb, lit));
		}
		g.fill(cx - r / 2 - 1, cy - r / 2 - 1, cx - r / 2 + 2, cy - r / 2 + 1, 0xC0FFFFFF);
	}

	/** Dev preview only: a spin that started {@code msAgo} ms ago (rarity -2 = normal roll), to screenshot mid-animation. */
	public static void previewSpin(int rarityIndex, long msAgo) {
		if (rarityIndex == -2) spin(false);
		else spinWin(rarityIndex);
		start -= msAgo;
	}

	/** /scathaslots rare|epic|legendary: a jackpot spin with that rarity. */
	public static void spinWin(int rarityIndex) {
		spin(true);
		rarity = rarityIndex;
	}

	/**
	 * The buff Scatha walk-in on its own, with {@code text} instead of the pet's rarity
	 * (e.g. a nucleus run PB). Legendary confetti and music; does nothing while a spin is up.
	 */
	public static void walkInFor(String text) {
		if (start >= 0) return;
		start = System.currentTimeMillis();
		jackpot = decided = none = true;
		roulette = false;
		java.util.Arrays.fill(result, SCATHA);
		java.util.Arrays.fill(rang, false);
		rarity = 2;
		rarityRang = false;
		wave = 0;
		walkStep = -1;
		walkText = text;
		ScathaRoulette.reset();
		ScathaMusic.reset();
		wantOverlay = true;
	}

	/** A big gold line, punched in once, glinting now and then: the walk-in's custom text. */
	private static void drawBanner(GuiGraphicsExtractor g, Font font, long t, String text, float cx, int y) {
		float since = t - celebrate();
		float glint = (float) Math.max(0, Math.sin(since / 260.0)) * 0.45f;
		float scale = 2f + Math.max(0f, 1 - since / 220f) * 1.5f;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, y);
		pose.scale(scale, scale);
		g.text(font, text, -font.width(text) / 2, 0, 0xFF000000 | Theme.mix(0xFFAA00, 0xFFFFFF, glint), true);
		pose.popMatrix();
	}
}
