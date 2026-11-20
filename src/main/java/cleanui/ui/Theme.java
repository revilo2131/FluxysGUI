package cleanui.ui;

import cleanui.CleanUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Design tokens and shared drawing. Crisp style: square corners, 1px lines, a gold frame
 * on top-level panels. Colors are RGB; alpha is applied per draw.
 */
public final class Theme {
	public static final int PANEL = 0x18191D;
	public static final int ROW = 0x222327;      // rows, cells, buttons
	public static final int ROW_HOVER = 0x2C2D33;
	public static final int SLOT = 0x141518;     // item slots
	public static final int LINE = 0x3B3C42;     // 1px borders and separators
	public static final int GOLD = 0xC8902E;     // top-level panel frame
	public static final int ACCENT = 0xF5A524;   // titles, highlights
	public static final int TEXT = 0xFFFFFF;
	public static final int TEXT_MUTED = 0x9C9CA3;

	/** Identity colors; each feature or event takes one by index. */
	// shortcut: fixed set; user-picked colors come with the customization phase.
	private static final int[] COLORS = {
			0xA35CE6, // purple
			0x4FA8E8, // blue
			0xE35AAE, // pink
			0xDB4A36, // red
			0xF0902A, // orange
			0x62C043, // green
			0x4CB8A0, // teal
	};

	public static final int PAD = 8;

	/** Vanilla tooltip style id; maps to sprites tooltip/clean_background and tooltip/clean_frame. */
	public static final Identifier TOOLTIP_STYLE = Identifier.fromNamespaceAndPath(CleanUi.ID, "clean");

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
		int b = argb(border, borderAlpha);
		g.fill(x, y, x + w, y + 1, b);
		g.fill(x, y + h - 1, x + w, y + h, b);
		g.fill(x, y + 1, x + 1, y + h - 1, b);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, b);
	}

	/** Box with the standard grey line border. */
	public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int bg, float alpha) {
		box(g, x, y, w, h, bg, alpha, LINE, alpha);
	}

	/** Top-level panel: dark, slightly see-through, gold frame. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float a) {
		box(g, x, y, w, h, PANEL, a * 0.94f, GOLD, a * 0.85f);
	}

	/** Screen title in accent color with a short underline, like an active tab. */
	public static void title(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String text, int x, int y, float a) {
		g.text(font, text, x, y, argb(ACCENT, a), true);
		g.fill(x, y + 11, x + font.width(text), y + 13, argb(ACCENT, a));
	}

	/**
	 * Gold text with a bright band sweeping left to right, for maxed things.
	 * Driven by the real clock, so it's the same speed at any FPS.
	 */
	public static void shimmer(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String text, int x, int y, float a) {
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

	/** Item in a dark square slot, like an inventory slot. */
	public static void slot(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float itemScale, float a) {
		box(g, x, y, 18, 18, SLOT, a);
		int size = Math.round(16 * itemScale);
		item(g, stack, x + 9 - size / 2, y + 9 - size / 2, size, a);
	}

	/** Small muted section label, e.g. "NEXT UP". */
	public static void label(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String text, int x, int y, float a) {
		g.text(font, text, x, y, argb(TEXT_MUTED, a), true);
	}

	/** An item's full tooltip (e.g. Hypixel's lore) in the Clean UI style. */
	public static void itemTooltip(GuiGraphicsExtractor g, ItemStack stack, int mouseX, int mouseY) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		g.setComponentTooltipForNextFrame(mc.font, net.minecraft.client.gui.screens.Screen.getTooltipFromItem(mc, stack),
				mouseX, mouseY, TOOLTIP_STYLE);
	}

	/** {@code text} shortened with "…" so it fits in {@code maxW} px. */
	public static String fit(net.minecraft.client.gui.Font font, String text, int maxW) {
		if (font.width(text) <= maxW) return text;
		String s = text;
		while (!s.isEmpty() && font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
		return s.strip() + "…";
	}

	/**
	 * Draws {@code left} at x and {@code right} right-aligned at x + w, dropping
	 * {@code left} if the two would touch. Keeps rows from ever overlapping.
	 */
	public static void textPair(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String left, int leftRgb,
			String right, int rightRgb, int x, int y, int w, float a) {
		int rw = font.width(right);
		g.text(font, right, x + w - rw, y, argb(rightRgb, a), true);
		if (left != null && font.width(left) + 6 <= w - rw) g.text(font, left, x, y, argb(leftRgb, a), true);
	}
}
