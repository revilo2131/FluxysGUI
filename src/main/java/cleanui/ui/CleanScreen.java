package cleanui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Base for every Clean UI screen: fade + scale on open and close, and a shared alpha
 * that widgets read via {@link #currentAlpha()}. Subclasses draw in {@link #extractContent}.
 * Closing returns to {@code parent} if there is one.
 */
public abstract class CleanScreen extends Screen {
	private final Anim open = new Anim(0f, 14f);
	private final Screen parent;
	private boolean closing;
	private float alpha;

	protected CleanScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	/** Draw this screen's panels and text. Widgets are drawn after, on top. */
	protected abstract void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float alpha);

	@Override
	public void added() {
		// replay the open animation whenever this screen is shown, including on return from a child
		closing = false;
		open.snap(0f);
		open.target(1f);
	}

	@Override
	public final void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		alpha = open.get();
		if (closing && open.done()) {
			if (parent != null) minecraft.setScreenAndShow(parent);
			else super.onClose();
			return;
		}
		float s = 0.94f + 0.06f * alpha; // subtle scale-in from 94%
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(width / 2f, height / 2f);
		pose.scale(s, s);
		pose.translate(-width / 2f, -height / 2f);

		extractContent(g, mouseX, mouseY, alpha);
		super.extractRenderState(g, mouseX, mouseY, partialTick); // widgets

		pose.popMatrix();
	}

	/** Fade alpha of the open Clean UI screen, or 1 if none. Lets widgets fade without plumbing. */
	public static float currentAlpha() {
		return Minecraft.getInstance().gui.screen() instanceof CleanScreen s ? s.alpha : 1f;
	}

	@Override
	public void onClose() {
		closing = true; // play the close animation; the actual close runs when it finishes
		open.target(0f);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
