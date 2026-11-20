package cleanui.ui;

import cleanui.CleanUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/** Design tokens and the shared panel look. Colors are RGB; alpha is applied per draw. */
public final class Theme {
	public static final int BG = 0x0F1115;
	public static final int PANEL = 0x171A21;
	public static final int RAISED = 0x272C38; // neutral buttons, cells
	public static final int HOVER = 0x323848;
	public static final int BORDER = 0xFFFFFF; // drawn at low alpha
	public static final int TEXT = 0xF0F2F8;
	public static final int TEXT_MUTED = 0x9AA1B2;

	/** Three-color palette. Each feature takes one as its identity color. */
	public record Palette(int primary, int secondary, int tertiary) {
		public int get(int i) {
			return switch (Math.floorMod(i, 3)) {
				case 0 -> primary;
				case 1 -> secondary;
				default -> tertiary;
			};
		}
	}

	// shortcut: one fixed palette; a palette picker comes with the customization phase.
	public static final Palette PALETTE = new Palette(0x7B7BFF, 0x38D6F0, 0xFF6FAE); // indigo, cyan, rose
	public static final int ACCENT = PALETTE.primary();

	public static final int PAD = 8; // 8px grid

	/** Vanilla tooltip style id; maps to sprites tooltip/clean_background and tooltip/clean_frame. */
	public static final Identifier TOOLTIP_STYLE = id("clean");

	static final Identifier PANEL_SPRITE = id("panel");
	static final Identifier OUTLINE_SPRITE = id("outline");
	private static final Identifier SHADOW_SPRITE = id("shadow");

	private Theme() {}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(CleanUi.ID, path);
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

	/** Rounded rect with a 1px border, tinted. Used by panels, buttons and cells. */
	public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int rgb, float alpha, float borderAlpha) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL_SPRITE, x, y, w, h, argb(rgb, alpha));
		g.blitSprite(RenderPipelines.GUI_TEXTURED, OUTLINE_SPRITE, x, y, w, h, argb(BORDER, borderAlpha));
	}

	/**
	 * Minecraft item drawn at {@code size} px. Items can't be alpha-faded, so they
	 * pop in with a scale as the screen fades in instead.
	 */
	public static void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int size, float a) {
		if (a < 0.3f) return;
		float s = size / 16f * (0.6f + 0.4f * (a - 0.3f) / 0.7f);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + size / 2f, y + size / 2f);
		pose.scale(s, s);
		g.item(stack, -8, -8);
		pose.popMatrix();
	}

	/** Thin bar split into the three palette colors; the brand mark under titles. */
	public static void paletteBar(GuiGraphicsExtractor g, int x, int y, int segment, float a) {
		for (int i = 0; i < 3; i++) g.fill(x + i * segment, y, x + (i + 1) * segment, y + 2, argb(PALETTE.get(i), a));
	}

	/** Raised panel with a soft drop shadow. {@code a} is the fade alpha. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float a) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, SHADOW_SPRITE, x - 8, y - 6, w + 16, h + 16, argb(0xFFFFFF, a));
		box(g, x, y, w, h, PANEL, a * 0.97f, a * 0.08f);
	}
}
