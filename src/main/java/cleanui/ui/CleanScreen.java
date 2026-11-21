package cleanui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Base for every Clean UI screen: fade + scale on open and close, the user's UI scale, and the
 * alphas that widgets read via {@link #currentAlpha} so they fade with their screen.
 * Mouse input is mapped into the scaled space, so subclasses handle clicks in {@link #clicked}
 * and scrolling in {@link #scrolled} with coordinates that match what they drew.
 */
public abstract class CleanScreen extends Screen {
	/** Dev preview only: forces the mouse position so hover states can be screenshotted. */
	public static int debugMouseX = -1, debugMouseY = -1;

	private final Anim open = new Anim(0f, 14f);
	private boolean closing;
	private float alpha;
	private float scale = 1f; // last frame's total scale, for mapping mouse input
	/** Alpha for page content; a subclass lowers it while a page slides in. */
	protected float bodyAlpha = 1f;

	protected CleanScreen(Component title) {
		super(title);
	}

	/** Draw this screen's panels and text. Widgets are drawn after, on top. */
	protected abstract void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float alpha);

	/** Size scale for the whole screen (user setting, shrunk to fit). 1 = as designed. */
	protected float baseScale() {
		return 1f;
	}

	@Override
	public void added() {
		// replay the open animation whenever this screen is shown
		closing = false;
		open.snap(0f);
		open.target(1f);
	}

	/** Show at full size and opacity right away (e.g. moving between pages of an open window). */
	protected void skipOpenAnimation() {
		open.snap(1f);
	}

	protected boolean isClosing() {
		return closing;
	}

	@Override
	public final void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		alpha = open.get();
		if (closing && open.done()) {
			closed();
			return;
		}
		scale = (0.94f + 0.06f * alpha) * baseScale(); // subtle scale-in from 94%
		if (debugMouseX >= 0) {
			mouseX = debugMouseX;
			mouseY = debugMouseY;
		} else {
			mouseX = (int) mapX(mouseX);
			mouseY = (int) mapY(mouseY);
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(width / 2f, height / 2f);
		pose.scale(scale, scale);
		pose.translate(-width / 2f, -height / 2f);

		extractContent(g, mouseX, mouseY, alpha);
		super.extractRenderState(g, mouseX, mouseY, partialTick); // widgets

		pose.popMatrix();
	}

	private double mapX(double x) {
		return width / 2.0 + (x - width / 2.0) / scale;
	}

	private double mapY(double y) {
		return height / 2.0 + (y - height / 2.0) / scale;
	}

	private MouseButtonEvent map(MouseButtonEvent e) {
		return new MouseButtonEvent(mapX(e.x()), mapY(e.y()), e.buttonInfo());
	}

	@Override
	public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return clicked(map(event), doubleClick);
	}

	/** Click in this screen's own (unscaled) coordinates. Default: widgets. */
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public final boolean mouseReleased(MouseButtonEvent event) {
		return super.mouseReleased(map(event));
	}

	@Override
	public final boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return super.mouseDragged(map(event), dx / scale, dy / scale);
	}

	@Override
	public final boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return scrolled(mapX(x), mapY(y), scrollX, scrollY);
	}

	/** Scroll in this screen's own (unscaled) coordinates. Default: widgets. */
	protected boolean scrolled(double x, double y, double scrollX, double scrollY) {
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public final void mouseMoved(double x, double y) {
		super.mouseMoved(mapX(x), mapY(y));
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

	/** The inventory key (whatever it's bound to) closes the page like Esc, as it does in chest menus; not while typing in a box. */
	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (super.keyPressed(event)) return true;
		if (minecraft.options.keyInventory.matches(event) && !(getFocused() instanceof net.minecraft.client.gui.components.EditBox)) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
