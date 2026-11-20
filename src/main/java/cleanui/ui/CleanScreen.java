package cleanui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Base for every Clean UI screen: fade + scale on open and close, and the alphas that
 * widgets read via {@link #currentAlpha} so they fade with their screen.
 * Subclasses draw in {@link #extractContent}.
 */
public abstract class CleanScreen extends Screen {
	/** Dev preview only: forces the mouse position so hover states can be screenshotted. */
	public static int debugMouseX = -1, debugMouseY = -1;

	private final Anim open = new Anim(0f, 14f);
	private boolean closing;
	private float alpha;
	/** Alpha for page content; a subclass lowers it while a page slides in. */
	protected float bodyAlpha = 1f;

	protected CleanScreen(Component title) {
		super(title);
	}

	/** Draw this screen's panels and text. Widgets are drawn after, on top. */
	protected abstract void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float alpha);

	@Override
	public void added() {
		// replay the open animation whenever this screen is shown
		closing = false;
		open.snap(0f);
		open.target(1f);
	}

	/** Show at full size and opacity right away (e.g. switching tabs inside an open window). */
	protected void skipOpenAnimation() {
		open.snap(1f);
	}

	protected boolean isClosing() {
		return closing;
	}

	@Override
	public final void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (debugMouseX >= 0) {
			mouseX = debugMouseX;
			mouseY = debugMouseY;
		}
		alpha = open.get();
		if (closing && open.done()) {
			closed();
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

	/** Fade alpha of the open Clean UI screen (times the page alpha for body widgets), or 1 if none. */
	public static float currentAlpha(boolean body) {
		return Minecraft.getInstance().gui.screen() instanceof CleanScreen s ? s.alpha * (body ? s.bodyAlpha : 1f) : 1f;
	}

	/** Runs when the close animation ends. */
	protected void closed() {
		super.onClose();
	}

	@Override
	public void onClose() {
		closing = true; // play the close animation; closed() runs when it finishes
		open.target(0f);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
