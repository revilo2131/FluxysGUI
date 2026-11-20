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
 * Plain: centered label. With {@link #tile}: a color stripe on the left, an item in a slot,
 * title and colored subtitle. Hover widens the stripe and lifts and grows the item.
 */
public final class Button extends AbstractButton {
	private final Runnable action;
	private final Anim hover = new Anim(0f, 18f);
	private Component tooltip;
	private ItemStack item;
	private Component subtitle;
	private int color = Theme.ACCENT;

	public Button(int x, int y, int w, int h, Component label, Runnable action) {
		super(x, y, w, h, label);
		this.action = action;
	}

	public Button tooltip(Component tooltip) {
		this.tooltip = tooltip;
		return this;
	}

	/** Draw as a tile with an item and a subtitle. */
	public Button tile(ItemStack item, Component subtitle) {
		this.item = item;
		this.subtitle = subtitle;
		return this;
	}

	/** Identity color (RGB), usually {@link Theme#color(int)}. */
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
		float a = CleanScreen.currentAlpha();
		hover.target(isHoveredOrFocused() ? 1f : 0f);
		float h = hover.get() * (active ? 1f : 0.4f); // inactive still reacts, but less
		int x = getX(), y = getY();
		Font font = Minecraft.getInstance().font;

		Theme.box(g, x, y, width, height, Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a,
				Theme.mix(Theme.LINE, color, 0.5f * h), a);

		if (item == null) {
			int labelColor = Theme.mix(active ? Theme.TEXT : Theme.TEXT_MUTED, color, h);
			g.text(font, getMessage(), x + (width - font.width(getMessage())) / 2, y + (height - 8) / 2,
					Theme.argb(labelColor, a), false);
		} else {
			float on = active ? 1f : 0.45f;
			g.fill(x + 1, y + 1, x + 4 + Math.round(2 * h), y + height - 1, Theme.argb(color, a * on)); // stripe
			int lift = Math.round(2 * h);
			Theme.slot(g, item, x + (width - 18) / 2, y + 7 - lift, 1f + 0.15f * h, a);
			centered(g, font, getMessage(), x, y + height - 25, active ? Theme.TEXT : Theme.TEXT_MUTED, a);
			centered(g, font, subtitle, x, y + height - 14, active ? color : Theme.TEXT_MUTED, a);
		}

		if (tooltip != null && isHovered()) g.setTooltipForNextFrame(font, tooltip, mouseX, mouseY, Theme.TOOLTIP_STYLE);
	}

	private void centered(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int rgb, float a) {
		g.text(font, text, x + 2 + (width - font.width(text)) / 2, y, Theme.argb(rgb, a), true); // +2 offsets the stripe
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
