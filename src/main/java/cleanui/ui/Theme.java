package cleanui.ui;

import cleanui.CleanUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Design tokens and shared drawing. Pixel-crisp "fancy tooltip" style: deep navy surfaces,
 * a cyan frame with corner ornaments, dotted leaders, labeled dividers, badges and angled
 * bars. Colors are RGB; alpha is applied per draw.
 */
public final class Theme {
	public static final int PANEL = 0x0C1524;
	public static final int ROW = 0x13203A;      // rows, cells, buttons
	public static final int ROW_HOVER = 0x1B2C4C;
	public static final int SLOT = 0x08101D;     // item slots, bar tracks
	public static final int LINE = 0x24395A;     // 1px borders and separators
	public static final int FRAME = 0x5ED6EA;    // cyan frame, hover/selection borders
	public static final int FRAME_DIM = 0x2C7488; // inner frame, ornaments on dividers
	public static final int TITLE = 0x7FE6F5;    // page titles, names
	public static final int ACCENT = 0xFFA62B;   // values, highlights
	public static final int TEXT = 0xF2F5FA;
	public static final int TEXT_MUTED = 0x8796B0;

	/** Identity colors; each skill or event takes one by index. */
	// shortcut: fixed set; user-picked colors come with the customization phase.
	private static final int[] COLORS = {
			0xB06CF2, // purple
			0x58A8F5, // blue
			0xF06BBE, // pink
			0xF0584A, // red
			0xFFA62B, // orange
			0x6CD655, // green
			0x4FD6C0, // teal
	};

	public static final int PAD = 8;

	/** Vanilla tooltip style id; maps to sprites tooltip/clean_background and tooltip/clean_frame. */
	public static final Identifier TOOLTIP_STYLE = Identifier.fromNamespaceAndPath(CleanUi.ID, "clean");

	/** Corner ornament (top-left orientation), mirrored into the other corners. */
	private static final String[] ORNAMENT = {
			"XXXX.",
			"X....",
			"X.XX.",
			"X.X..",
			"....."};

	private Theme() {}

	public static int color(int index) {
		return COLORS[Math.floorMod(index, COLORS.length)];
	}

	/** RGB + alpha (0..1) → ARGB. */
	public static int argb(int rgb, float alpha) {
		return Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24 | (rgb & 0xFFFFFF);
	}

	/** Linear blend of two RGB colors. */
	public static int mix(int from, int to, float t) {
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return r << 16 | g << 8 | b;
	}

	/** Filled rect with a 1px border. */
	public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int bg, float alpha, int border, float borderAlpha) {
		g.fill(x, y, x + w, y + h, argb(bg, alpha));
		outline(g, x, y, w, h, argb(border, borderAlpha));
	}

	/** Box with the standard line border. */
	public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int bg, float alpha) {
		box(g, x, y, w, h, bg, alpha, LINE, alpha);
	}

	private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
		g.fill(x, y, x + w, y + 1, argb);
		g.fill(x, y + h - 1, x + w, y + h, argb);
		g.fill(x, y + 1, x + 1, y + h - 1, argb);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
	}

	/** Top-level panel: navy, cyan frame, a dim inner frame, and pixel ornaments in each corner. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float a) {
		box(g, x, y, w, h, PANEL, a * 0.96f, FRAME, a);
		outline(g, x + 2, y + 2, w - 4, h - 4, argb(FRAME_DIM, a * 0.55f));
		int c = argb(FRAME, a);
		for (int r = 0; r < ORNAMENT.length; r++)
			for (int col = 0; col < ORNAMENT[r].length(); col++)
				if (ORNAMENT[r].charAt(col) == 'X') {
					int ox = 3 + col, oy = 3 + r;
					g.fill(x + ox, y + oy, x + ox + 1, y + oy + 1, c);
					g.fill(x + w - 1 - ox, y + oy, x + w - ox, y + oy + 1, c);
					g.fill(x + ox, y + h - 1 - oy, x + ox + 1, y + h - oy, c);
					g.fill(x + w - 1 - ox, y + h - 1 - oy, x + w - ox, y + h - oy, c);
				}
	}

	/** Horizontal separator with a small link ornament in the middle (like "—⊂⊃—"). */
	public static void divider(GuiGraphicsExtractor g, int x, int y, int w, float a) {
		int mid = x + w / 2, line = argb(LINE, a), orn = argb(FRAME_DIM, a);
		g.fill(x, y, mid - 6, y + 1, line);
		g.fill(mid + 6, y, x + w, y + 1, line);
		outline(g, mid - 5, y - 1, 10, 3, orn);
	}

	/** Section heading: "——◆ LABEL ◆——" across {@code w}, text centered. */
	public static void divider(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w, float a) {
		int tw = font.width(label), tx = x + (w - tw) / 2, line = argb(LINE, a), orn = argb(FRAME_DIM, a);
		g.fill(x, y + 4, tx - 8, y + 5, line);
		g.fill(tx + tw + 8, y + 4, x + w, y + 5, line);
		diamond(g, tx - 6, y + 4, orn);
		diamond(g, tx + tw + 5, y + 4, orn);
		g.text(font, label, tx, y, argb(TEXT_MUTED, a), true);
	}

	/** 3×3 pixel diamond centered on (cx, cy). */
	private static void diamond(GuiGraphicsExtractor g, int cx, int cy, int argb) {
		g.fill(cx, cy - 1, cx + 1, cy + 2, argb);
		g.fill(cx - 1, cy, cx + 2, cy + 1, argb);
	}

	/** Dotted leader between two pieces of text on a row (like "Mining Speed ······ +3,260"). */
	public static void leader(GuiGraphicsExtractor g, int x1, int x2, int y, float a) {
		int c = argb(LINE, a * 1.4f);
		for (int x = x1; x < x2; x += 3) g.fill(x, y, x + 1, y + 1, c);
	}

	/**
	 * {@code left} at x, {@code right} right-aligned at x + w, dotted leader between.
	 * Drops {@code left} if the two would touch, so rows never overlap.
	 */
	public static void textLeader(GuiGraphicsExtractor g, Font font, String left, int leftRgb,
			String right, int rightRgb, int x, int y, int w, float a) {
		int rw = font.width(right), rx = x + w - rw;
		g.text(font, right, rx, y, argb(rightRgb, a), true);
		if (left == null || font.width(left) + 6 > w - rw) return;
		g.text(font, left, x, y, argb(leftRgb, a), true);
		leader(g, x + font.width(left) + 3, rx - 3, y + 6, a);
	}

	/** Like {@link #textLeader} without the dots. */
	public static void textPair(GuiGraphicsExtractor g, Font font, String left, int leftRgb,
			String right, int rightRgb, int x, int y, int w, float a) {
		int rw = font.width(right);
		g.text(font, right, x + w - rw, y, argb(rightRgb, a), true);
		if (left != null && font.width(left) + 6 <= w - rw) g.text(font, left, x, y, argb(leftRgb, a), true);
	}

	/** Filled level badge like the "IV" boxes: colored box, dark text. Drawn right-aligned at {@code right}; returns its width. */
	public static int badge(GuiGraphicsExtractor g, Font font, String text, int right, int y, int rgb, float a) {
		int w = font.width(text) + 5;
		g.fill(right - w, y, right, y + 10, argb(rgb, a));
		outline(g, right - w, y, w, 10, argb(mix(rgb, 0x000000, 0.35f), a));
		g.text(font, text, right - w + 3, y + 1, argb(PANEL, a), false);
		return w;
	}

	/** Outlined tag like "DRILL": border and text in {@code rgb}. Returns its width. */
	public static int tag(GuiGraphicsExtractor g, Font font, String text, int x, int y, int rgb, float a) {
		int w = font.width(text) + 7;
		box(g, x, y, w, 11, SLOT, a, rgb, a);
		g.text(font, text, x + 4, y + 2, argb(rgb, a), false);
		return w;
	}

	/**
	 * Angled progress bar (pointed ends, like the drill fuel bar), optional text centered in it.
	 * {@code h} should be odd so the points are symmetric.
	 */
	public static void bar(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, float progress, int rgb, String text, float a) {
		int mid = h / 2, fillTo = x + Math.round(w * Math.clamp(progress, 0f, 1f));
		for (int r = 0; r < h; r++) {
			int inset = Math.abs(r - mid);
			int x1 = x + inset, x2 = x + w - inset;
			boolean edge = r == 0 || r == h - 1;
			g.fill(x1, y + r, x2, y + r + 1, argb(edge ? LINE : SLOT, a));
			if (!edge && fillTo > x1 + 1) g.fill(x1 + 1, y + r, Math.min(fillTo, x2 - 1), y + r + 1, argb(rgb, a));
			if (!edge) { // pointed ends outlined
				g.fill(x1, y + r, x1 + 1, y + r + 1, argb(LINE, a));
				g.fill(x2 - 1, y + r, x2, y + r + 1, argb(LINE, a));
			}
		}
		if (text != null && font.width(text) <= w - h * 2)
			g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2 + 1, argb(TEXT, a), true);
	}

	/**
	 * Gold text with a bright band sweeping left to right, for maxed things.
	 * Driven by the real clock, so it's the same speed at any FPS.
	 */
	public static void shimmer(GuiGraphicsExtractor g, Font font, String text, int x, int y, float a) {
		double t = System.nanoTime() / 1e9;
		int cx = x;
		for (int i = 0; i < text.length(); i++) {
			String c = String.valueOf(text.charAt(i));
			double band = Math.sin(t * 4.0 - i * 0.45); // one wave crest travels along the text
			float glow = (float) Math.max(0, band) * 0.75f;
			g.text(font, c, cx, y, argb(mix(0xE8A317, 0xFFF4C2, glow), a), true);
			cx += font.width(c);
		}
	}

	/** Small right-pointing arrow (▸), 4×7 px. */
	public static void arrow(GuiGraphicsExtractor g, int x, int y, int rgb, float a) {
		for (int i = 0; i < 4; i++) g.fill(x + i, y + i, x + i + 1, y + 7 - i, argb(rgb, a));
	}

	/**
	 * Minecraft item drawn at {@code size} px. Items can't be alpha-faded, so they
	 * pop in with a scale as the screen fades in instead.
	 */
	public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size, float a) {
		if (a < 0.3f) return;
		float s = size / 16f * (0.6f + 0.4f * Math.min(1f, (a - 0.3f) / 0.7f));
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + size / 2f, y + size / 2f);
		pose.scale(s, s);
		g.item(stack, -8, -8);
		pose.popMatrix();
	}

	/** Item in a dark slot with a cyan-tinted frame, like the item frame on fancy tooltips. */
	public static void slot(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float itemScale, float a) {
		box(g, x, y, 18, 18, SLOT, a, mix(LINE, FRAME, 0.45f), a);
		int size = Math.round(16 * itemScale);
		item(g, stack, x + 9 - size / 2, y + 9 - size / 2, size, a);
	}

	/** Small muted label, e.g. "Seen 12m ago". */
	public static void label(GuiGraphicsExtractor g, Font font, String text, int x, int y, float a) {
		g.text(font, text, x, y, argb(TEXT_MUTED, a), true);
	}

	/** An item's full tooltip (e.g. Hypixel's lore) in the Clean UI style. */
	public static void itemTooltip(GuiGraphicsExtractor g, ItemStack stack, int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		g.setComponentTooltipForNextFrame(mc.font, Screen.getTooltipFromItem(mc, stack), mouseX, mouseY, TOOLTIP_STYLE);
	}

	/** {@code text} shortened with "…" so it fits in {@code maxW} px. */
	public static String fit(Font font, String text, int maxW) {
		if (font.width(text) <= maxW) return text;
		String s = text;
		while (!s.isEmpty() && font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
		return s.strip() + "…";
	}
}
