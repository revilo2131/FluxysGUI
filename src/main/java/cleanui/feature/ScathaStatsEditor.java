package cleanui.feature;

import cleanui.data.Config;
import cleanui.ui.Button;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Move and resize Scatha Stats over the live game: drag the panel to move it, drag its corner
 * (or scroll over it) to resize, arrow keys nudge. With the grid on, moves snap to it and to the
 * screen's edges and centre; hold Shift to place freely. Saved when you let go; Esc / Done go back.
 */
public final class ScathaStatsEditor extends Screen {
	private static final int[] GRIDS = {4, 8, 16, 32};
	private static final int MIN_SCALE = 50, MAX_SCALE = 200, STEP = 5, HANDLE = 7, BAR_H = 18;
	private static final int GOLD = 0xE8B84A;

	private final Screen parent;
	/** 0 = nothing, 1 = moving, 2 = resizing. */
	private int drag;
	private double grabX, grabY;
	private Button gridButton, gridSizeButton;
	private final java.util.List<Button> toolbar = new java.util.ArrayList<>();
	/** Toolbar buttons' top edge: at the bottom, or at the top while the panel sits down there. */
	private int barY;

	public ScathaStatsEditor(Screen parent) {
		super(Component.literal("Move Scatha Stats"));
		this.parent = parent;
	}

	private static Config c() {
		return Config.get();
	}

	private float scale() {
		return c().scathaStatsScale / 100f;
	}

	/** The panel's box on screen right now: x, y, w, h. */
	private int[] box() {
		float s = scale();
		int[] at = ScathaStats.clamp(c().scathaStatsX, c().scathaStatsY, s, width, height);
		return new int[]{at[0], at[1], Math.round(ScathaStats.BOX_W * s), Math.round(ScathaStats.boxH() * s)};
	}

	@Override
	protected void init() {
		int y = height - BAR_H - 8;
		int[] w = {74, 58, 18, 18, 52, 52};
		int gap = 4, total = gap * (w.length - 1) + 44; // 44 = the size readout between - and +
		for (int v : w) total += v;
		int x = (width - total) / 2;
		gridButton = addRenderableWidget(new Button(x, y, w[0], BAR_H, gridLabel(), () -> {
			c().scathaStatsGrid = !c().scathaStatsGrid;
			c().save();
			gridButton.setMessage(gridLabel());
		}).tooltip(Component.literal("Snap to the grid and the screen's edges and centre while moving. Hold Shift to place freely.")));
		x += w[0] + gap;
		gridSizeButton = addRenderableWidget(new Button(x, y, w[1], BAR_H, gridSizeLabel(), () -> {
			int i = 0;
			while (i < GRIDS.length && GRIDS[i] != c().scathaStatsGridSize) i++;
			c().scathaStatsGridSize = GRIDS[(i + 1) % GRIDS.length];
			c().save();
			gridSizeButton.setMessage(gridSizeLabel());
		}).tooltip(Component.literal("Grid spacing in pixels. Click to change.")));
		x += w[1] + gap;
		addRenderableWidget(new Button(x, y, w[2], BAR_H, Component.literal("-"), () -> resizeBy(-STEP))
				.tooltip(Component.literal("Smaller")));
		x += w[2] + 44 + gap;
		addRenderableWidget(new Button(x, y, w[3], BAR_H, Component.literal("+"), () -> resizeBy(STEP))
				.tooltip(Component.literal("Bigger")));
		x += w[3] + gap;
		addRenderableWidget(new Button(x, y, w[4], BAR_H, Component.literal("Reset"), () -> {
			c().scathaStatsX = 6;
			c().scathaStatsY = 5;
			c().scathaStatsScale = 100;
			c().save();
		}).tooltip(Component.literal("Top left, normal size.")));
		x += w[4] + gap;
		addRenderableWidget(new Button(x, y, w[5], BAR_H, Component.literal("Done"), this::onClose).color(0x55FF55));
		toolbar.clear();
		for (var child : children()) if (child instanceof Button b) toolbar.add(b);
		barY = y;
	}

	/** Toolbar at the bottom, unless the panel reaches down there and has room above: then at the top. */
	private void placeToolbar(int[] b) {
		int bottom = height - BAR_H - 8;
		boolean top = b[1] + b[3] > bottom - 20 && b[1] > 44;
		int y = top ? 8 : bottom;
		if (y == barY) return;
		for (Button button : toolbar) button.setY(y);
		barY = y;
	}

	private Component gridLabel() {
		return Component.literal("Grid: " + (c().scathaStatsGrid ? "On" : "Off"));
	}

	private Component gridSizeLabel() {
		return Component.literal(c().scathaStatsGridSize + " px");
	}

	/** Bigger or smaller around the panel's top-left corner, kept on screen. */
	private void resizeBy(int by) {
		setScale(c().scathaStatsScale + by);
		c().save();
	}

	private void setScale(int percent) {
		int[] b = box();
		// the biggest size that still fits from where it is
		float room = Math.min((width - b[0]) / (float) ScathaStats.BOX_W, (height - b[1]) / (float) ScathaStats.boxH());
		int max = Math.min(MAX_SCALE, (int) (room * 100) / STEP * STEP);
		c().scathaStatsScale = Math.max(MIN_SCALE, Math.min(Math.max(MIN_SCALE, max), Math.round(percent / (float) STEP) * STEP));
		c().scathaStatsX = b[0];
		c().scathaStatsY = b[1];
	}

	private static boolean shift() {
		long w = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
		return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
	}

	/** Snap a corner position to the grid, then to the screen's edges and centre if close. */
	private int snap(int v, int size, int screen, boolean free) {
		if (free || !c().scathaStatsGrid) return v;
		int g = c().scathaStatsGridSize;
		int best = Math.round(v / (float) g) * g;
		int[] stick = {0, screen - size, (screen - size) / 2};
		for (int s : stick) if (Math.abs(v - s) <= g / 2 + 1) best = s;
		return best;
	}

	private boolean overHandle(double mx, double my, int[] b) {
		int hx = b[0] + b[2], hy = b[1] + b[3];
		return mx >= hx - HANDLE - 2 && mx <= hx + 2 && my >= hy - HANDLE - 2 && my <= hy + 2;
	}

	private static boolean inside(double mx, double my, int[] b) {
		return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		int[] b = box();
		if (overHandle(event.x(), event.y(), b)) drag = 2;
		else if (inside(event.x(), event.y(), b)) {
			drag = 1;
			grabX = event.x() - b[0];
			grabY = event.y() - b[1];
		} else return false;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (drag == 0) return super.mouseDragged(event, dx, dy);
		int[] b = box();
		if (drag == 1) {
			boolean free = shift();
			c().scathaStatsX = snap((int) Math.round(event.x() - grabX), b[2], width, free);
			c().scathaStatsY = snap((int) Math.round(event.y() - grabY), b[3], height, free);
			int[] at = ScathaStats.clamp(c().scathaStatsX, c().scathaStatsY, scale(), width, height);
			c().scathaStatsX = at[0];
			c().scathaStatsY = at[1];
		} else {
			// the corner follows the mouse: size from the new width
			double w = Math.max(10, event.x() - b[0]);
			setScale((int) Math.round(w / ScathaStats.BOX_W * 100));
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (drag != 0) {
			drag = 0;
			c().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (inside(x, y, box()) && scrollY != 0) {
			resizeBy(scrollY > 0 ? STEP : -STEP);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int step = c().scathaStatsGrid && !shift() ? c().scathaStatsGridSize : 1;
		int dx = switch (event.key()) { case GLFW.GLFW_KEY_LEFT -> -step; case GLFW.GLFW_KEY_RIGHT -> step; default -> 0; };
		int dy = switch (event.key()) { case GLFW.GLFW_KEY_UP -> -step; case GLFW.GLFW_KEY_DOWN -> step; default -> 0; };
		if (dx != 0 || dy != 0) {
			int[] b = box();
			int[] at = ScathaStats.clamp(b[0] + dx, b[1] + dy, scale(), width, height);
			c().scathaStatsX = at[0];
			c().scathaStatsY = at[1];
			c().save();
			return true;
		}
		return super.keyPressed(event);
	}

	/** No blur: the game stays visible behind, so the panel can be lined up with it. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, 0x50000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (c().scathaStatsGrid) grid(g);
		int[] b = box();
		if (drag == 0) placeToolbar(b); // not mid-drag: the bar jumping under the mouse would be confusing
		ScathaStats.drawAt(g, b[0], b[1], scale());
		boolean hot = drag != 0 || inside(mouseX, mouseY, b);
		// selection outline: marching gold dashes
		int off = (int) (System.currentTimeMillis() / 80 % 8);
		dashes(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, off, hot ? 0xFF000000 | GOLD : 0xA0FFFFFF);
		// resize handle on the bottom-right corner
		boolean overH = drag == 2 || overHandle(mouseX, mouseY, b);
		int hx = b[0] + b[2], hy = b[1] + b[3];
		g.fill(hx - HANDLE - 1, hy - HANDLE - 1, hx + 2, hy + 2, 0xFF120A06);
		g.fill(hx - HANDLE, hy - HANDLE, hx + 1, hy + 1, 0xFF000000 | (overH ? 0xFFF1B0 : GOLD));
		for (int i = 1; i < HANDLE - 1; i += 2) // grip lines
			g.fill(hx - i - 1, hy - 1, hx - i, hy, 0xFF8A5A1A);
		// readout above (or below, at the top edge) the panel
		String info = b[0] + ", " + b[1] + "   " + c().scathaStatsScale + "%";
		int iy = b[1] >= 14 ? b[1] - 12 : b[1] + b[3] + 5;
		g.fill(b[0] - 2, iy - 2, b[0] + font.width(info) + 4, iy + 10, 0xC0000000);
		g.text(font, info, b[0] + 1, iy, 0xFF000000 | GOLD, true);
		// toolbar: background strip with the hint, size readout
		int ty = barY;
		boolean atTop = ty < height / 2;
		String hint = "Drag to move  ·  drag the corner or scroll to resize  ·  arrows nudge  ·  Shift = no snap";
		if (atTop) {
			g.fill(0, 0, width, ty + BAR_H + 18, 0x90000000);
			g.fill(0, ty + BAR_H + 17, width, ty + BAR_H + 18, 0x60000000 | GOLD);
			g.text(font, hint, (width - font.width(hint)) / 2, ty + BAR_H + 5, 0xFFBBBBBB, true);
		} else {
			g.fill(0, ty - 18, width, height, 0x90000000);
			g.fill(0, ty - 18, width, ty - 17, 0x60000000 | GOLD);
			g.text(font, hint, (width - font.width(hint)) / 2, ty - 12, 0xFFBBBBBB, true);
		}
		String size = c().scathaStatsScale + "%";
		int sx = sizeReadoutX();
		g.text(font, size, sx + (44 - font.width(size)) / 2, ty + 5, 0xFFFFFFFF, true);
		super.extractRenderState(g, mouseX, mouseY, delta); // buttons
	}

	/** Left edge of the space between the - and + buttons. */
	private int sizeReadoutX() {
		int[] w = {74, 58, 18, 18, 52, 52};
		int gap = 4, total = gap * (w.length - 1) + 44;
		for (int v : w) total += v;
		return (width - total) / 2 + w[0] + w[1] + w[2] + gap * 2;
	}

	private void grid(GuiGraphicsExtractor g) {
		int s = c().scathaStatsGridSize;
		for (int x = 0; x < width; x += s) g.fill(x, 0, x + 1, height, x % (s * 4) == 0 ? 0x2CFFFFFF : 0x12FFFFFF);
		for (int y = 0; y < height; y += s) g.fill(0, y, width, y + 1, y % (s * 4) == 0 ? 0x2CFFFFFF : 0x12FFFFFF);
		g.fill(width / 2, 0, width / 2 + 1, height, 0x60000000 | GOLD); // centre lines
		g.fill(0, height / 2, width, height / 2 + 1, 0x60000000 | GOLD);
	}

	private static void dashes(GuiGraphicsExtractor g, int x, int y, int w, int h, int off, int argb) {
		for (int i = -off; i < w; i += 8) {
			int a = Math.max(0, i), e = Math.min(w, i + 4);
			if (e > a) {
				g.fill(x + a, y, x + e, y + 1, argb);
				g.fill(x + w - e, y + h - 1, x + w - a, y + h, argb);
			}
		}
		for (int i = -off; i < h; i += 8) {
			int a = Math.max(0, i), e = Math.min(h, i + 4);
			if (e > a) {
				g.fill(x + w - 1, y + a, x + w, y + e, argb);
				g.fill(x, y + h - e, x + 1, y + h - a, argb);
			}
		}
	}

	@Override
	public void onClose() {
		c().save();
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
