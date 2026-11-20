package cleanui.ui;

import cleanui.CleanUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Themed button. Extends vanilla AbstractButton so keyboard focus, click sound and
 * narration keep working; only the drawing is ours. With an icon it draws as a tile:
 * icon on top, label below, and the icon lifts and brightens on hover.
 * With a color it tints background, border and icon in that color.
 */
public final class Button extends AbstractButton {
	private static final int ICON = 24;

	private final Runnable action;
	private final Anim hover = new Anim(0f, 18f);
	private Component tooltip;
	private Identifier icon;
	private int color = -1; // -1 = neutral

	public Button(int x, int y, int w, int h, Component label, Runnable action) {
		super(x, y, w, h, label);
		this.action = action;
	}

	public Button tooltip(Component tooltip) {
		this.tooltip = tooltip;
		return this;
	}

	/** Icon sprite name under assets/cleanui/textures/gui/sprites/icon/, e.g. "calendar". */
	public Button icon(String name) {
		this.icon = Identifier.fromNamespaceAndPath(CleanUi.ID, "icon/" + name);
		return this;
	}

	/** Identity color (RGB), usually from {@link Theme#PALETTE}. */
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
		float h = hover.get() * (active ? 1f : 0.6f); // inactive still reacts, but less

		boolean tinted = color != -1;
		int bg = tinted ? Theme.mix(Theme.RAISED, color, 0.16f + 0.16f * h) : Theme.mix(Theme.RAISED, Theme.HOVER, h);
		int border = tinted ? color : Theme.BORDER;
		float borderAlpha = tinted ? 0.30f + 0.45f * h : 0.10f + 0.12f * h;
		Theme.box(g, getX(), getY(), width, height, bg, a, a * borderAlpha);

		Font font = Minecraft.getInstance().font;
		int labelColor = Theme.argb(active || tinted ? Theme.TEXT : Theme.TEXT_MUTED, a);
		int labelX = getX() + (width - font.width(getMessage())) / 2;

		if (icon == null) {
			g.text(font, getMessage(), labelX, getY() + (height - 8) / 2, labelColor, false);
		} else {
			int base = tinted ? color : Theme.TEXT;
			int iconColor = Theme.mix(base, 0xFFFFFF, 0.10f + 0.30f * h); // brighten toward white on hover
			float scale = 1f + 0.08f * h;
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(getX() + width / 2f, getY() + 10 + ICON / 2f - 2f * h); // lift 2px on hover
			pose.scale(scale, scale);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, -ICON / 2, -ICON / 2, ICON, ICON,
					Theme.argb(iconColor, a * (active ? 1f : 0.8f)));
			pose.popMatrix();
			g.text(font, getMessage(), labelX, getY() + height - 16, labelColor, false);
		}

		if (tooltip != null && isHovered()) g.setTooltipForNextFrame(font, tooltip, mouseX, mouseY, Theme.TOOLTIP_STYLE);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
