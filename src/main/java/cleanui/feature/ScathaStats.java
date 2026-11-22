package cleanui.feature;

import cleanui.data.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scatha Stats: a framed panel while you farm worms. Scatha pets dropped by rarity, worms and
 * Scathas killed (all time and this session) with the Scatha share, spawns since the last Scatha,
 * Scathas since the last pet, time since the last spawn, and where you are and which way you face.
 * Kills are read from nametags like the slots do; pets from "PET DROP! Scatha". All-time numbers
 * are saved in the config. Shows after a worm or Scatha near you, hides 10 minutes after the last.
 */
public final class ScathaStats {
	/** "[Lv119] Stoneworm 5/5❤", "[Lv181] Scatha 3/10❤" (older: "Worm"; not the Magma Fields' Flaming Worm): name and current health. */
	private static final Pattern MOB = Pattern.compile("(?<!Flaming )\\b(Stoneworm|Worm|Scatha)\\D*?([\\d.,]+)([kKmM]?)(?:/[\\d.,]+[kKmM]?)?❤");
	private static final float LOW = 3, NEAR = 20, KILL_RANGE = 16;
	private static final long SHOW_MS = 10 * 60_000;
	private static final int W = 222;
	private static final int[] RARITY = {0x5555FF, 0xAA00AA, 0xFFAA00};
	private static final int GOLD = 0xE8B84A, GOLD_DARK = 0x8A5A1A, VIOLET = 0x8A4AE0;

	private record Seen(boolean scatha, float hp, double dist) {}

	private static final Map<Integer, Seen> last = new HashMap<>();
	private static final Set<Integer> counted = new HashSet<>();
	private static int sessionWorms, sessionScathas, sinceScatha;
	private static long lastSpawn = -1, lastActivity = -1;

	private static boolean inHollows;
	private static final Set<String> logged = new HashSet<>();
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cleanui");

	private ScathaStats() {}

	/** Hypixel's tab list says "Area: Crystal Hollows" while you're there. */
	private static boolean inHollows(Minecraft mc) {
		if (mc.getConnection() == null) return false;
		for (var info : mc.getConnection().getListedOnlinePlayers()) {
			Component name = info.getTabListDisplayName();
			if (name == null) continue;
			String s = ChatFormatting.stripFormatting(name.getString());
			if (s.contains("Area:") && s.contains("Crystal Hollows")) return true;
		}
		return false;
	}

	/** A nametag that mentions a worm or Scatha but didn't read as one: logged once, so its format can be fixed. */
	private static void logUnread(String name) {
		String low = name.toLowerCase(java.util.Locale.ROOT);
		if ((low.contains("worm") || low.contains("scatha")) && !low.contains("flaming") && logged.size() < 30 && logged.add(name))
			LOGGER.info("[Scatha Stats] nametag not read as a worm/Scatha: '{}'", name);
	}

	/** Name and health from a nametag: {@code [scatha ? 1 : 0, hp]}, or null if it isn't a worm or Scatha. */
	static float[] parse(String plainName) {
		Matcher m = MOB.matcher(plainName);
		if (!m.find()) return null;
		try {
			float v = Float.parseFloat(m.group(2).replace(",", ""));
			v *= switch (m.group(3).toLowerCase()) {
				case "k" -> 1e3f;
				case "m" -> 1e6f;
				default -> 1f;
			};
			return new float[]{m.group(1).equals("Scatha") ? 1 : 0, v};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** A bestiary mob entry, "[Lv119] Stoneworm" / "[Lv181] Scatha", with "Kills: 1,234" in its lore (not the family item "Stoneworm XV": that's both added up). */
	private static final Pattern BESTIARY_NAME = Pattern.compile("^\\[Lv\\d+\\] (Stoneworm|Worm|Scatha)$"), KILLS = Pattern.compile("Kills: ([\\d,]+)");

	/** From a bestiary item: {@code [scatha ? 1 : 0, kills]}, or null if it isn't the Worm / Scatha entry. */
	static long[] fromBestiary(String name, java.util.List<String> lore) {
		if (!BESTIARY_NAME.matcher(name.strip()).matches()) return null;
		for (String line : lore) {
			Matcher m = KILLS.matcher(line);
			if (m.find()) return new long[]{name.endsWith("Scatha") ? 1 : 0, Long.parseLong(m.group(1).replace(",", ""))};
		}
		return null;
	}

	/** An open menu showing the Worm or Scatha bestiary entry: take its kill count as the all-time number. */
	private static void readBestiary(Minecraft mc) {
		if (mc.player.containerMenu == mc.player.inventoryMenu) return;
		Config c = Config.get();
		boolean changed = false;
		for (var slot : mc.player.containerMenu.slots) {
			var stack = slot.getItem();
			if (stack.isEmpty() || slot.container == mc.player.getInventory()) continue;
			var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
			if (lore == null) continue;
			java.util.List<String> lines = lore.lines().stream().map(l -> ChatFormatting.stripFormatting(l.getString())).toList();
			long[] b = fromBestiary(ChatFormatting.stripFormatting(stack.getHoverName().getString()), lines);
			if (b == null) continue;
			if (b[0] == 1 && c.statScathas != b[1]) {
				c.statScathas = b[1];
				changed = true;
			} else if (b[0] == 0 && c.statWorms != b[1]) {
				c.statWorms = b[1];
				changed = true;
			}
		}
		if (changed) c.save();
	}

	/** "PET DROP! Scatha": one more pet of that rarity, and the since-last-pet count starts again. */
	public static void onChat(Component message) {
		String s = ChatFormatting.stripFormatting(message.getString());
		if (!s.contains("PET DROP!") || !s.contains("Scatha")) return;
		Config c = Config.get();
		int r = ScathaSlots.dropRarity(message);
		if (r >= 0) c.statPets[r]++;
		c.statSincePet = 0;
		c.save();
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			last.clear();
			counted.clear();
			return;
		}
		if (mc.player.tickCount % 10 == 0) readBestiary(mc);
		if (mc.player.tickCount % 20 == 0) inHollows = inHollows(mc);
		Map<Integer, Seen> seen = new HashMap<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.getCustomName() == null) continue;
			String name = ChatFormatting.stripFormatting(e.getCustomName().getString());
			float[] p = parse(name);
			if (p == null) {
				logUnread(name);
				continue;
			}
			int id = e.getId();
			double dist = e.distanceTo(mc.player);
			boolean scatha = p[0] == 1;
			if (!last.containsKey(id) && dist < NEAR) spawned();
			if (dist < NEAR) lastActivity = System.currentTimeMillis();
			seen.put(id, new Seen(scatha, p[1], dist));
			LivingEntity mob = ScathaSlots.mobOf(mc, e);
			if (dist < KILL_RANGE && (p[1] <= 0 || (mob != null && mob.isDeadOrDying()))) killed(id, scatha);
		}
		// a low tag vanishing close by = it died (Hypixel sometimes removes it without a death frame)
		for (Map.Entry<Integer, Seen> was : last.entrySet())
			if (!seen.containsKey(was.getKey()) && was.getValue().hp() <= LOW && was.getValue().dist() < KILL_RANGE)
				killed(was.getKey(), was.getValue().scatha());
		counted.retainAll(seen.keySet());
		last.clear();
		last.putAll(seen);
	}

	private static void spawned() {
		lastSpawn = System.currentTimeMillis();
	}

	private static void killed(int id, boolean scatha) {
		if (!counted.add(id)) return;
		Config c = Config.get();
		if (scatha) {
			c.statScathas++;
			c.statSincePet++;
			sessionScathas++;
			sinceScatha = 0;
		} else {
			c.statWorms++;
			sessionWorms++;
			sinceScatha++;
		}
		c.save();
	}

	/** Preview: as if you'd been farming a while. */
	public static void preview() {
		Config c = Config.get();
		c.statWorms = 644;
		c.statScathas = 396;
		c.statPets[0] = 2;
		c.statSincePet = 127;
		sessionWorms = 12;
		sessionScathas = 8;
		sinceScatha = 1;
		lastSpawn = System.currentTimeMillis() - 12_000;
		lastActivity = System.currentTimeMillis();
	}

	// ---- drawing ----

	public static void render(GuiGraphicsExtractor g) {
		boolean active = lastActivity >= 0 && System.currentTimeMillis() - lastActivity < SHOW_MS;
		if (!Config.get().scathaStats || !(active || inHollows)) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Font font = mc.font;
		Config c = Config.get();
		long now = System.currentTimeMillis();
		int x = 6, y = 12, h = 142;
		frame(g, font, x, y, W, h, now);
		int cx = x + W / 2;
		// pets by rarity
		int py = y + 16;
		label(g, font, "Pets", x + 9, py + 2, 0x55FF55);
		for (int r = 0; r < 3; r++) {
			int px = x + 64 + r * 52;
			g.fill(px - 1, py - 1, px + 13, py + 13, NucleusFinale.argb(RARITY[r], 0.35f));
			outline(g, px - 1, py - 1, 14, 14, NucleusFinale.argb(RARITY[r], 0.9f));
			ScathaSlots.item(g, ScathaSlots.scathaHead(), px + 6, py + 6, 0.7f, 0.7f);
			g.text(font, String.valueOf(c.statPets[r]), px + 17, py + 3, 0xFF000000 | RARITY[r], true);
		}
		divider(g, x, py + 18, W, now);
		// kills table: all time, this session
		int ty = py + 25;
		int[] col = {x + 86, x + 146, x + 196};
		center(g, font, "Stoneworms", col[0], ty, 0xFFAA00);
		center(g, font, "Scathas", col[1], ty, 0xFF6AD5);
		center(g, font, "Total", col[2], ty, 0xFFFFFF);
		row(g, font, x, ty + 13, "All time", c.statWorms, c.statScathas, col, true);
		row(g, font, x, ty + 25, "Session", sessionWorms, sessionScathas, col, false);
		divider(g, x, ty + 39, W, now);
		// streaks and where you are
		int ly = ty + 46;
		String streak = sinceScatha == 0 && sessionScathas > 0 ? "Last spawn was a Scatha!" : "No Scatha for " + sinceScatha + " spawn" + (sinceScatha == 1 ? "" : "s");
		line(g, font, x, ly, "☠", 0xFF6AD5, streak, sinceScatha == 0 && sessionScathas > 0 ? 0x55FF55 : 0xDDDDDD);
		line(g, font, x, ly + 11, "⚔", 0xFFAA00, "Scathas since last pet: " + c.statSincePet, 0xDDDDDD);
		String spawn = lastSpawn < 0 ? "No spawn seen yet" : "Last spawn: " + ago(now - lastSpawn);
		line(g, font, x, ly + 22, "⏱", 0x55FFFF, spawn, 0xDDDDDD);
		var pos = mc.player.blockPosition();
		line(g, font, x, ly + 33, "⌖", 0xAAAAFF, pos.getX() + " " + pos.getY() + " " + pos.getZ() + "  facing " + facing(mc.player.getYRot()), 0xBBBBBB);
	}

	private static String ago(long ms) {
		long s = ms / 1000;
		return s < 60 ? s + "s ago" : s / 60 + "m " + s % 60 + "s ago";
	}

	/** Which way you look along the axes, like F3: -Z north, +X east, +Z south, -X west. */
	private static String facing(float yaw) {
		int q = Math.floorMod(Math.round(yaw / 90f), 4);
		return switch (q) {
			case 0 -> "+Z";
			case 1 -> "-X";
			case 2 -> "-Z";
			default -> "+X";
		};
	}

	private static void row(GuiGraphicsExtractor g, Font font, int x, int y, String name, long worms, long scathas, int[] col, boolean all) {
		if (all) g.fill(x + 6, y - 2, x + W - 6, y + 10, 0x22FFFFFF);
		label(g, font, name, x + 9, y, all ? 0xC8B8E8 : 0x9A8AB8);
		long total = worms + scathas;
		center(g, font, String.format("%,d", worms), col[0], y, 0xFFFFFF);
		String pct = total == 0 ? "--" : String.format("%,d", scathas) + " §7" + String.format("%.1f%%", 100.0 * scathas / total);
		center(g, font, pct, col[1], y, 0xFFFFFF);
		center(g, font, String.format("%,d", total), col[2], y, 0xFFE38A);
	}

	private static void line(GuiGraphicsExtractor g, Font font, int x, int y, String icon, int iconRgb, String text, int rgb) {
		g.text(font, icon, x + 9, y, 0xFF000000 | iconRgb, true);
		g.text(font, text, x + 21, y, 0xFF000000 | rgb, true);
	}

	private static void label(GuiGraphicsExtractor g, Font font, String text, int x, int y, int rgb) {
		g.text(font, text, x, y, 0xFF000000 | rgb, true);
	}

	private static void center(GuiGraphicsExtractor g, Font font, String text, int x, int y, int rgb) {
		g.text(font, text, x - font.width(text) / 2, y, 0xFF000000 | rgb, true);
	}

	private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
		g.fill(x, y, x + w, y + 1, argb);
		g.fill(x, y + h - 1, x + w, y + h, argb);
		g.fill(x, y, x + 1, y + h, argb);
		g.fill(x + w - 1, y, x + w, y + h, argb);
	}

	/**
	 * The frame: drop shadow, dark violet body, a bevelled gold border with an inner violet line,
	 * rivets along the edges, gold corner brackets with a glowing gem each, a light glint running
	 * round the top edge, and a raised title plate with the Scatha head and a shimmering title.
	 */
	private static void frame(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, long now) {
		g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x66000000);                                  // shadow
		g.fill(x, y, x + w, y + h, 0xFF120A06);                                                  // outer edge
		g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF000000 | GOLD);                               // gold bevel: lit top/left
		g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF000000 | GOLD);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF000000 | GOLD_DARK);                   // shaded bottom/right
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF000000 | GOLD_DARK);
		g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, 0xF01A0F2E, 0xF00A0614);              // body
		outline(g, x + 4, y + 4, w - 8, h - 8, NucleusFinale.argb(VIOLET, 0.45f));               // inner line
		NucleusFinale.glowOval(g, x + w / 2f, y + h / 2f, w * 0.45f, h * 0.45f, 0x3A1A6A, 0.35f); // faint inner light
		// rivets along top and bottom
		for (int rx = x + 18; rx < x + w - 14; rx += 16) {
			g.fill(rx, y + 1, rx + 2, y + 2, 0xFFFFF1B0);
			g.fill(rx, y + h - 2, rx + 2, y + h - 1, 0xFFC08A30);
		}
		// glint sweeping along the top edge every 4 s
		float sweep = (now % 4000) / 1400f;
		if (sweep < 1) {
			int gx = x + Math.round(sweep * w);
			g.fillGradient(Math.max(x + 1, gx - 10), y + 1, Math.min(x + w - 1, gx + 10), y + 2, 0xFFFFFFFF, 0x80FFF1B0);
		}
		// corner brackets with gems
		int[][] corners = {{x, y, 1, 1}, {x + w, y, -1, 1}, {x, y + h, 1, -1}, {x + w, y + h, -1, -1}};
		for (int k = 0; k < 4; k++) {
			int cx = corners[k][0], cy = corners[k][1], dx = corners[k][2], dy = corners[k][3];
			g.fill(Math.min(cx, cx + dx * 9), Math.min(cy, cy + dy * 2), Math.max(cx, cx + dx * 9), Math.max(cy, cy + dy * 2), 0xFF000000 | GOLD);
			g.fill(Math.min(cx, cx + dx * 2), Math.min(cy, cy + dy * 9), Math.max(cx, cx + dx * 2), Math.max(cy, cy + dy * 9), 0xFF000000 | GOLD);
			float gx = cx + dx * 4.5f, gy = cy + dy * 4.5f;
			int gem = k < 2 ? 0xB070FF : RARITY[k == 2 ? 0 : 2];
			NucleusFinale.glow(g, gx, gy, 7, gem, 0.5f + 0.25f * (float) Math.sin(now / 500.0 + k));
			diamond(g, gx, gy, 2.6f, gem);
		}
		// title plate straddling the top edge
		String title = "Scatha Stats";
		int tw = Math.round(font.width(title) * 1.25f);
		int pw = tw + 34, px = x + (w - pw) / 2, py = y - 7;
		for (int s = 0; s < 4; s++) // little stepped wings
			g.fill(px - 4 + s, py + 4 + s, px + pw + 4 - s, py + 5 + s, 0xFF000000 | (s == 0 ? GOLD : GOLD_DARK));
		g.fill(px, py, px + pw, py + 15, 0xFF120A06);
		g.fillGradient(px + 1, py + 1, px + pw - 1, py + 14, 0xFF2E1A4A, 0xFF140A24);
		outline(g, px, py, pw, 15, 0xFF000000 | GOLD);
		g.fill(px + 1, py + 1, px + pw - 1, py + 2, 0x60FFFFFF);
		diamond(g, px + 4, py + 7.5f, 1.8f, GOLD);
		diamond(g, px + pw - 4, py + 7.5f, 1.8f, GOLD);
		ScathaSlots.item(g, ScathaSlots.scathaHead(), px + 13, py + 7.5f, 0.6f, 0.6f);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(px + 22, py + 3);
		pose.scale(1.25f, 1.25f);
		int tx = 0;
		for (int i = 0; i < title.length(); i++) { // a slow rainbow shimmer, letter by letter
			String ch = String.valueOf(title.charAt(i));
			int rgb = java.awt.Color.HSBtoRGB(((now / 2600f) + i * 0.05f) % 1f, 0.5f, 1f);
			g.text(font, ch, tx, 0, 0xFF000000 | rgb, true);
			tx += font.width(ch);
		}
		pose.popMatrix();
	}

	/** A section line fading out at both ends, with a small gem in the middle. */
	private static void divider(GuiGraphicsExtractor g, int x, int y, int w, long now) {
		int mid = x + w / 2;
		horizontalFade(g, x + 10, mid - 6, y, true);
		horizontalFade(g, mid + 6, x + w - 10, y, false);
		diamond(g, mid, y + 0.5f, 2.2f, VIOLET);
	}

	/** A 1px gold line fading toward its outer end (left half fades in, right half fades out). */
	private static void horizontalFade(GuiGraphicsExtractor g, int x0, int x1, int y, boolean fadeIn) {
		int n = Math.max(1, x1 - x0);
		for (int i = 0; i < n; i += 2) {
			float t = i / (float) n;
			float a = fadeIn ? t : 1 - t;
			g.fill(x0 + i, y, x0 + i + 2, y + 1, NucleusFinale.argb(GOLD, 0.85f * a));
		}
	}

	/** A small faceted gem: a square turned 45°, lit on top. */
	private static void diamond(GuiGraphicsExtractor g, float x, float y, float r, int rgb) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(r / 4f, r / 4f);
		pose.rotate((float) (Math.PI / 4));
		g.fill(-3, -3, 3, 3, 0xFF000000 | NucleusFinale.mix(rgb, 0x000000, 0.5f));
		g.fill(-2, -2, 2, 2, 0xFF000000 | rgb);
		g.fill(-2, -2, 0, 0, 0xFF000000 | NucleusFinale.mix(rgb, 0xFFFFFF, 0.5f));
		pose.popMatrix();
	}
}
