package cleanui.feature;

import cleanui.data.Config;
import cleanui.ui.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Customize HUD: every FluxysGUI panel over the live game. Click a panel to pick it; drag to move,
 * drag its corner (or scroll over it) to resize, arrow keys nudge. With the grid on, moves snap to it
 * and to the screen's edges and centre; Shift places freely. The picked panel's own options sit in
 * the toolbar. Saved as you go; Esc / Done go back.
 */
public final class HudEditor extends Screen {
	private static final int[] GRIDS = {4, 8, 16, 32};
	private static final int MIN_SCALE = 50, MAX_SCALE = 200, STEP = 5, HANDLE = 7, BAR_H = 18, GAP = 4, READOUT = 44;
	private static final int GOLD = 0xE8B84A;

	/** A panel option shown as a toolbar button "Label: Value"; click to change. */
	private record Option(String label, String tip, Function<Config, String> value, Consumer<Config> next) {}

	/** One movable panel: its box size at 100%, where it is now, how to draw and place it, its options. */
	private interface Panel {
		String name();

		int baseW();

		int baseH();

		int scale();

		/** Top-left on screen right now (kept on screen). */
		int[] at(int screenW, int screenH);

		void set(int x, int y, int scale);

		void draw(GuiGraphicsExtractor g, int x, int y, float scale);

		/** Shown at all? Off panels are drawn dimmed in the editor. */
		boolean shown();

		List<Option> options();
	}

	private static Option toggle(String label, String tip, Predicate<Config> get, Consumer<Config> flip, String on, String off) {
		return new Option(label, tip, c -> get.test(c) ? on : off, flip);
	}

	private static final Panel SCATHA = new Panel() {
		public String name() { return "Scatha Stats"; }
		public int baseW() { return ScathaStats.BOX_W; }
		public int baseH() { return ScathaStats.boxH(); }
		public int scale() { return Config.get().scathaStatsScale; }
		public int[] at(int sw, int sh) {
			Config c = Config.get();
			return ScathaStats.clamp(c.scathaStatsX, c.scathaStatsY, c.scathaStatsScale / 100f, sw, sh);
		}
		public void set(int x, int y, int s) {
			Config c = Config.get();
			c.scathaStatsX = x;
			c.scathaStatsY = y;
			c.scathaStatsScale = s;
		}
		public void draw(GuiGraphicsExtractor g, int x, int y, float s) { ScathaStats.drawAt(g, x, y, s); }
		public boolean shown() { return Config.get().scathaStats; }
		public List<Option> options() {
			return List.of(
					toggle("Shown", "Show Scatha Stats while you farm worms.", c -> c.scathaStats, c -> c.scathaStats = !c.scathaStats, "Yes", "No"),
					toggle("Timers", "Tunnel Vision and the 30 s worm spawn cooldown as bars.", c -> c.scathaTimers, c -> c.scathaTimers = !c.scathaTimers, "On", "Off"));
		}
	};

	private static final Panel SCOREBOARD = new Panel() {
		public String name() { return "Scoreboard"; }
		public int baseW() { return ScoreboardHud.boxW(); }
		public int baseH() { return ScoreboardHud.boxH(); }
		public int scale() { return Config.get().scoreboardScale; }
		public int[] at(int sw, int sh) { return ScoreboardHud.place(sw, sh); }
		public void set(int x, int y, int s) {
			Config c = Config.get();
			c.scoreboardX = x;
			c.scoreboardY = y;
			c.scoreboardScale = s;
		}
		public void draw(GuiGraphicsExtractor g, int x, int y, float s) { ScoreboardHud.drawAt(g, x, y, s); }
		public boolean shown() { return Config.get().scoreboardFancy; }
		public List<Option> options() {
			return List.of(
					toggle("Style", "Fancy: the Scatha Stats frame. Minecraft: the normal scoreboard (place and size don't apply).",
							c -> c.scoreboardFancy, c -> c.scoreboardFancy = !c.scoreboardFancy, "Fancy", "Minecraft"),
					toggle("Title", "The title in the server's own colours, or a rainbow shimmer.",
							c -> c.scoreboardShimmer, c -> c.scoreboardShimmer = !c.scoreboardShimmer, "Shimmer", "Server"),
					toggle("Spacers", "Blank lines as gem dividers, or plain gaps.", c -> c.scoreboardGems, c -> c.scoreboardGems = !c.scoreboardGems, "Gems", "Gaps"),
					toggle("Date line", "Hypixel's date and server line at the top (11/22/26 m38AD).",
							c -> !c.scoreboardHideServer, c -> c.scoreboardHideServer = !c.scoreboardHideServer, "Show", "Hide"),
					toggle("Footer", "The www.hypixel.net line at the bottom.", c -> !c.scoreboardHideFooter, c -> c.scoreboardHideFooter = !c.scoreboardHideFooter, "Show", "Hide"));
		}
	};

	private static final List<Panel> PANELS = List.of(SCATHA, SCOREBOARD);

	private final Screen parent;
	private Panel selected;
	/** 0 = nothing, 1 = moving, 2 = resizing. */
	private int drag;
	private double grabX, grabY;
	private final List<Button> toolbar = new ArrayList<>();
	/** Top of the toolbar's two button rows: at the bottom, or at the top while the picked panel sits down there. */
	private int barY = -1;

	/** Opens with every panel; {@code scathaFirst} picks Scatha Stats (from its settings), else the scoreboard. */
	public HudEditor(Screen parent, boolean scathaFirst) {
		super(Component.literal("Customize HUD"));
		this.parent = parent;
		this.selected = scathaFirst ? SCATHA : SCOREBOARD;
	}

	private static Config c() {
		return Config.get();
	}

	private int[] box(Panel p) {
		int[] at = p.at(width, height);
		float s = p.scale() / 100f;
		return new int[]{at[0], at[1], Math.round(p.baseW() * s), Math.round(p.baseH() * s)};
	}

	private int toolbarH() {
		return BAR_H * 2 + GAP;
	}

	@Override
	protected void init() {
		if (barY < 0) barY = height - toolbarH() - 8;
		toolbar.clear();
		// row 1: the picked panel's options
		List<Option> opts = selected.options();
		int[] ow = new int[opts.size()];
		int total = -GAP;
		for (int i = 0; i < opts.size(); i++) {
			ow[i] = Math.max(56, font.width(optionLabel(opts.get(i))) + 14);
			total += ow[i] + GAP;
		}
		int x = (width - total) / 2;
		for (int i = 0; i < opts.size(); i++) {
			Option o = opts.get(i);
			Button[] self = new Button[1];
			self[0] = add(new Button(x, barY, ow[i], BAR_H, Component.literal(optionLabel(o)), () -> {
				o.next().accept(c());
				c().save();
				self[0].setMessage(Component.literal(optionLabel(o)));
			}).tooltip(Component.literal(o.tip())).color(GOLD));
			x += ow[i] + GAP;
		}
		// row 2: grid, size, reset, done
		int y = barY + BAR_H + GAP;
		int[] w = {74, 58, 18, 18, 52, 52};
		x = rowTwoX();
		Button[] grid = new Button[2];
		grid[0] = add(new Button(x, y, w[0], BAR_H, gridLabel(), () -> {
			c().scathaStatsGrid = !c().scathaStatsGrid;
			c().save();
			grid[0].setMessage(gridLabel());
		}).tooltip(Component.literal("Snap to the grid and the screen's edges and centre while moving. Hold Shift to place freely.")));
		x += w[0] + GAP;
		grid[1] = add(new Button(x, y, w[1], BAR_H, gridSizeLabel(), () -> {
			int i = 0;
			while (i < GRIDS.length && GRIDS[i] != c().scathaStatsGridSize) i++;
			c().scathaStatsGridSize = GRIDS[(i + 1) % GRIDS.length];
			c().save();
			grid[1].setMessage(gridSizeLabel());
		}).tooltip(Component.literal("Grid spacing in pixels. Click to change.")));
		x += w[1] + GAP;
		add(new Button(x, y, w[2], BAR_H, Component.literal("-"), () -> resizeBy(-STEP)).tooltip(Component.literal("Smaller")));
		x += w[2] + READOUT + GAP;
		add(new Button(x, y, w[3], BAR_H, Component.literal("+"), () -> resizeBy(STEP)).tooltip(Component.literal("Bigger")));
		x += w[3] + GAP;
		add(new Button(x, y, w[4], BAR_H, Component.literal("Reset"), () -> {
			if (selected == SCATHA) selected.set(6, 5, 100);
			else selected.set(-1, -1, 100); // back to vanilla's right-centre
			c().save();
		}).tooltip(Component.literal("Back to where it starts, normal size.")));
		x += w[4] + GAP;
		add(new Button(x, y, w[5], BAR_H, Component.literal("Done"), this::onClose).color(0x55FF55));
	}

	private Button add(Button b) {
		toolbar.add(b);
		return addRenderableWidget(b);
	}

	private int rowTwoX() {
		int total = 74 + 58 + 18 + 18 + 52 + 52 + READOUT + GAP * 5;
		return (width - total) / 2;
	}

	private static String optionLabel(Option o) {
		return o.label() + ": " + o.value().apply(Config.get());
	}

	private Component gridLabel() {
		return Component.literal("Grid: " + (c().scathaStatsGrid ? "On" : "Off"));
	}

	private Component gridSizeLabel() {
		return Component.literal(c().scathaStatsGridSize + " px");
	}

	private void select(Panel p) {
		if (p == selected) return;
		selected = p;
		clearWidgets();
		init();
	}

	private void resizeBy(int by) {
		setScale(selected.scale() + by);
		c().save();
	}

	/** New size around the panel's top-left corner, the biggest that still fits from there at most. */
	private void setScale(int percent) {
		int[] b = box(selected);
		float room = Math.min((width - b[0]) / (float) selected.baseW(), (height - b[1]) / (float) selected.baseH());
		int max = Math.min(MAX_SCALE, (int) (room * 100) / STEP * STEP);
		selected.set(b[0], b[1], Math.max(MIN_SCALE, Math.min(Math.max(MIN_SCALE, max), Math.round(percent / (float) STEP) * STEP)));
	}

	private void moveTo(int x, int y) {
		Panel p = selected;
		float s = p.scale() / 100f;
		int w = Math.round(p.baseW() * s), h = Math.round(p.baseH() * s);
		p.set(Math.max(0, Math.min(x, width - w)), Math.max(0, Math.min(y, height - h)), p.scale());
	}

	private static boolean shift() {
		long w = GLFW.glfwGetCurrentContext();
		return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
	}

	/** Snap a corner position to the grid, then to the screen's edges and centre if close. */
	private int snap(int v, int size, int screen, boolean free) {
		if (free || !c().scathaStatsGrid) return v;
		int g = c().scathaStatsGridSize;
		int best = Math.round(v / (float) g) * g;
		for (int s : new int[]{0, screen - size, (screen - size) / 2}) if (Math.abs(v - s) <= g / 2 + 1) best = s;
		return best;
	}

	private static boolean overHandle(double mx, double my, int[] b) {
		int hx = b[0] + b[2], hy = b[1] + b[3];
		return mx >= hx - HANDLE - 2 && mx <= hx + 2 && my >= hy - HANDLE - 2 && my <= hy + 2;
	}

	private static boolean inside(double mx, double my, int[] b) {
		return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
	}

	/** The panel under the mouse, top-most (drawn last) first. */
	private Panel panelAt(double mx, double my) {
		if (overHandle(mx, my, box(selected)) || inside(mx, my, box(selected))) return selected;
		for (int i = PANELS.size() - 1; i >= 0; i--) if (inside(mx, my, box(PANELS.get(i)))) return PANELS.get(i);
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		Panel p = panelAt(event.x(), event.y());
		if (p == null) return false;
		select(p);
		int[] b = box(p);
		if (overHandle(event.x(), event.y(), b)) drag = 2;
		else {
			drag = 1;
			grabX = event.x() - b[0];
			grabY = event.y() - b[1];
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (drag == 0) return super.mouseDragged(event, dx, dy);
		int[] b = box(selected);
		if (drag == 1) {
			boolean free = shift();
			moveTo(snap((int) Math.round(event.x() - grabX), b[2], width, free), snap((int) Math.round(event.y() - grabY), b[3], height, free));
		} else setScale((int) Math.round(Math.max(10, event.x() - b[0]) / selected.baseW() * 100)); // the corner follows the mouse
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
		Panel p = panelAt(x, y);
		if (p != null && scrollY != 0) {
			select(p);
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
			int[] b = box(selected);
			moveTo(b[0] + dx, b[1] + dy);
			c().save();
			return true;
		}
		return super.keyPressed(event);
	}

	/** No blur: the game stays visible behind, so panels can be lined up with it. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, 0x50000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (c().scathaStatsGrid) grid(g);
		long now = System.currentTimeMillis();
		for (Panel p : PANELS) {
			int[] b = box(p);
			p.draw(g, b[0], b[1], p.scale() / 100f);
			if (!p.shown()) { // off: dimmed, with a note
				g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0xA0000000);
				String off = p == SCOREBOARD ? "Minecraft's own scoreboard" : "Hidden";
				g.text(font, off, b[0] + (b[2] - font.width(off)) / 2, b[1] + b[3] / 2 - 4, 0xFFBBBBBB, true);
			}
			boolean sel = p == selected;
			boolean hot = sel || inside(mouseX, mouseY, b);
			int off = sel ? (int) (now / 80 % 8) : 0; // the picked one's dashes march
			dashes(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, off, sel ? 0xFF000000 | GOLD : hot ? 0xC0FFFFFF : 0x60FFFFFF);
			if (!sel) continue;
			// resize handle on the bottom-right corner
			boolean overH = drag == 2 || overHandle(mouseX, mouseY, b);
			int hx = b[0] + b[2], hy = b[1] + b[3];
			g.fill(hx - HANDLE - 1, hy - HANDLE - 1, hx + 2, hy + 2, 0xFF120A06);
			g.fill(hx - HANDLE, hy - HANDLE, hx + 1, hy + 1, 0xFF000000 | (overH ? 0xFFF1B0 : GOLD));
			for (int i = 1; i < HANDLE - 1; i += 2) g.fill(hx - i - 1, hy - 1, hx - i, hy, 0xFF8A5A1A); // grip
			// name, place and size above (or below, at the top edge) the panel
			String info = p.name() + "   " + b[0] + ", " + b[1] + "   " + p.scale() + "%";
			int iy = b[1] >= 14 ? b[1] - 12 : b[1] + b[3] + 5;
			int ix = Math.max(0, Math.min(b[0], width - font.width(info) - 4));
			g.fill(ix - 2, iy - 2, ix + font.width(info) + 4, iy + 10, 0xC0000000);
			g.text(font, info, ix + 1, iy, 0xFF000000 | GOLD, true);
		}
		if (drag == 0) placeToolbar(box(selected)); // never while dragging: the bar would jump under the mouse
		toolbarBackground(g);
		super.extractRenderState(g, mouseX, mouseY, delta); // buttons
	}

	/** Toolbar at the bottom, unless the picked panel reaches down there and has room above: then at the top. */
	private void placeToolbar(int[] b) {
		int bottom = height - toolbarH() - 8;
		boolean top = b[1] + b[3] > bottom - 20 && b[1] > toolbarH() + 30;
		int y = top ? 8 : bottom;
		if (y == barY) return;
		int dy = y - barY;
		for (Button button : toolbar) button.setY(button.getY() + dy);
		barY = y;
	}

	private void toolbarBackground(GuiGraphicsExtractor g) {
		boolean atTop = barY < height / 2;
		String hint = "Click a panel to pick it  ·  drag to move  ·  drag the corner or scroll to resize  ·  arrows nudge  ·  Shift = no snap";
		if (font.width(hint) > width - 8) hint = "Drag to move  ·  corner / scroll to resize  ·  Shift = no snap";
		int top = atTop ? 0 : barY - 18, bottom = atTop ? barY + toolbarH() + 18 : height;
		g.fill(0, top, width, bottom, 0x90000000);
		int line = atTop ? bottom - 1 : top;
		g.fill(0, line, width, line + 1, 0x60000000 | GOLD);
		int hy = atTop ? barY + toolbarH() + 5 : barY - 12;
		g.text(font, hint, (width - font.width(hint)) / 2, hy, 0xFFBBBBBB, true);
		// size readout between - and +
		String size = selected.scale() + "%";
		int sx = rowTwoX() + 74 + 58 + 18 + GAP * 3;
		g.text(font, size, sx + (READOUT - font.width(size)) / 2 - GAP / 2, barY + BAR_H + GAP + 5, 0xFFFFFFFF, true);
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
