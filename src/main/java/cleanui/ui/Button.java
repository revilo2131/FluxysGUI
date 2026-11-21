package cleanui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Themed button. Extends vanilla AbstractButton so keyboard focus, click sound and
 * narration keep working; only the drawing is ours.
 * A box with an optional item and a label; border and label take {@link #color} on hover.
 */
public final class Button extends AbstractButton {
	private final Runnable action;
	private final Anim hover = new Anim(0f, 18f);
	private Component tooltip;
	private ItemStack item;
	private boolean body;
	private int color = Theme.FRAME;
	private int outline = -1;

	public Button(int x, int y, int w, int h, Component label, Runnable action) {
		super(x, y, w, h, label);
		this.action = action;
	}

	public Button tooltip(Component tooltip) {
		this.tooltip = tooltip;
		return this;
	}

	/** Small item drawn left of the label. */
	public Button item(ItemStack item) {
		this.item = item;
		return this;
	}

	/** A coloured square outline around the item (RGB), e.g. a pet's rarity. */
	public Button outline(int rgb) {
		this.outline = rgb;
		return this;
	}

	/** Part of the page body: fades with page transitions. */
	public Button body() {
		this.body = true;
		return this;
	}

	/** Hover color (RGB). */
	public Button color(int rgb) {
		this.color = rgb;
		return this;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float a = CleanScreen.currentAlpha(body);
		hover.target(isHoveredOrFocused() && active ? 1f : 0f);
		float h = hover.get();
		int x = getX(), y = getY();
		Font font = Minecraft.getInstance().font;

		Theme.box(g, x, y, width, height, Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, color, 0.7f * h), a);
		int labelColor = active ? Theme.mix(Theme.TEXT, color, h) : Theme.TEXT_MUTED;

		int gap = outline >= 0 ? 19 : 15; // an outline needs room before the label
		int textW = font.width(getMessage()) + (item != null ? gap : 0);
		int tx = x + (width - textW) / 2, ty = y + (height - 8) / 2;
		if (item != null) {
			if (outline >= 0 && a > 0.3f) headOutline(g, tx, ty - 2, 12, Theme.argb(outline, a));
			Theme.item(g, item, tx, ty - 2, 12, active ? a : a * 0.5f);
			tx += gap;
		}
		g.text(font, getMessage(), tx, ty, Theme.argb(labelColor, a), true);

		if (tooltip != null && isHovered()) g.setTooltipForNextFrame(font, tooltip, mouseX, mouseY, Theme.tooltipStyle());
	}

	/**
	 * A rim that hugs a player-head icon drawn at (x, y, size): a solid block-shaped hexagon a little
	 * bigger than the head, drawn behind it, so only a thin coloured edge shows around the head.
	 * Head bounds measured from the rendered icon: about 17-88% across, 21-96% down.
	 */
	private static void headOutline(GuiGraphicsExtractor g, int x, int y, int size, int argb) {
		float left = x + 0.17f * size - 1, right = x + 0.88f * size + 1, top = y + 0.21f * size - 1, bottom = y + 0.96f * size + 1;
		float cx = (left + right) / 2, half = (right - left) / 2;
		var pose = g.pose();
		pose.pushMatrix();
		pose.scale(0.5f, 0.5f); // half-pixel rows: smoother slopes on the hexagon
		for (int row = Math.round(top * 2); row < Math.round(bottom * 2); row++) {
			float v = (row / 2f - top) / (bottom - top);
			// narrow flat top, straight sides, slightly narrower bottom: a head seen at an angle
			float w = v < 0.25f ? 0.45f + 0.55f * v / 0.25f : v > 0.75f ? 0.3f + 0.7f * (1 - v) / 0.25f : 1f;
			int x0 = Math.round((cx - half * w) * 2), x1 = Math.round((cx + half * w) * 2);
			g.fill(x0, row, x1, row + 1, argb);
		}
		pose.popMatrix();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
