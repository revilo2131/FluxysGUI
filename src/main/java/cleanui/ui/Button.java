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

		int textW = font.width(getMessage()) + (item != null ? 15 : 0);
		int tx = x + (width - textW) / 2, ty = y + (height - 8) / 2;
		if (item != null) {
			Theme.item(g, item, tx, ty - 2, 12, active ? a : a * 0.5f);
			tx += 15;
		}
		g.text(font, getMessage(), tx, ty, Theme.argb(labelColor, a), true);

		if (tooltip != null && isHovered()) g.setTooltipForNextFrame(font, tooltip, mouseX, mouseY, Theme.TOOLTIP_STYLE);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
