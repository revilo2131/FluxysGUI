package cleanui.feature;

import cleanui.data.Config;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Hand animation settings in the Scatha Stats frame, on the left so your hand stays in view on the
 * right. Choices cycle on click (right-click goes back); sliders take a click or drag, scroll for
 * one step, right-click to reset. Test buttons swing or pull out without hitting anything.
 */
public final class HandAnimScreen extends Screen {
	private static final int W = 230, ROW = 12, HEAD = 11, PAD = 10, LABEL_W = 78, GOLD = 0xE8B84A;

	private interface Row {}

	private record Header(String text) implements Row {}

	/** A choice: label, tip, its values, read and write by index. */
	private record Choice(String label, String tip, String[] names, String[] values, Function<Config, String> get, BiConsumer<Config, String> set) implements Row {}

	private record Slider(String label, String tip, int min, int max, int step, int def, ToIntFunction<Config> get, BiConsumer<Config, Integer> set,
	                      Function<Integer, String> fmt) implements Row {}

	private record Action(String label, String tip, Runnable run) implements Row {}

	private static final String[] ON_OFF = {"ON", "OFF"};
	private static final Function<Integer, String> PCT = v -> v + "%", DEG = v -> v + "°", BLOCK = v -> String.format("%+.2f", v / 100f);

	private static Choice toggle(String label, String tip, Function<Config, Boolean> get, BiConsumer<Config, Boolean> set) {
		return new Choice(label, tip, new String[]{"On", "Off"}, ON_OFF, c -> get.apply(c) ? "ON" : "OFF", (c, v) -> set.accept(c, v.equals("ON")));
	}

	private final List<Row> rows = List.of(
			toggle("Enabled", "Off: Minecraft's own hand animations.", c -> c.handAnim, (c, v) -> c.handAnim = v),
			new Header("SWING"),
			new Choice("Style", "How the item swings when you hit.", new String[]{"Vanilla", "Smooth", "Chop", "Spin", "Punch", "Slide"}, HandAnim.SWINGS,
					c -> c.swingStyle, (c, v) -> c.swingStyle = v),
			new Choice("Motion", "Full: turns and moves. Rotate: turns in place. None: the item stays still while you hit.",
					new String[]{"Full", "Rotate only", "None"}, HandAnim.MOTIONS, c -> c.swingMotion, (c, v) -> c.swingMotion = v),
			new Slider("Speed", "How fast the swing plays. Looks only: doesn't change how fast you attack.", 25, 300, 5, 100,
					c -> c.swingSpeed, (c, v) -> c.swingSpeed = v, PCT),
			new Slider("Strength", "How far the item swings.", 0, 200, 5, 100, c -> c.swingStrength, (c, v) -> c.swingStrength = v, PCT),
			new Header("POSITION"),
			new Slider("Left / right", "Move the item sideways (in blocks).", -60, 60, 1, 0, c -> c.handX, (c, v) -> c.handX = v, BLOCK),
			new Slider("Up / down", "Move the item up or down (in blocks).", -60, 60, 1, 0, c -> c.handY, (c, v) -> c.handY = v, BLOCK),
			new Slider("Near / far", "Move the item toward you or away (in blocks).", -60, 60, 1, 0, c -> c.handZ, (c, v) -> c.handZ = v, BLOCK),
			new Slider("Size", "How big the item is.", 30, 200, 5, 100, c -> c.handScale, (c, v) -> c.handScale = v, PCT),
			new Slider("Tilt", "Turn the item forward or back.", -180, 180, 5, 0, c -> c.handRotX, (c, v) -> c.handRotX = v, DEG),
			new Slider("Turn", "Turn the item left or right.", -180, 180, 5, 0, c -> c.handRotY, (c, v) -> c.handRotY = v, DEG),
			new Slider("Roll", "Roll the item sideways.", -180, 180, 5, 0, c -> c.handRotZ, (c, v) -> c.handRotZ = v, DEG),
			new Header("PULL-OUT"),
			new Choice("Style", "How an item comes up when you switch to it.", new String[]{"Vanilla", "Rise", "None", "Slide", "Spin", "Flip", "Zoom", "Drop"},
					HandAnim.EQUIPS, c -> c.equipStyle, (c, v) -> c.equipStyle = v),
			new Slider("Speed", "How fast the pull-out plays.", 25, 300, 5, 100, c -> c.equipSpeed, (c, v) -> c.equipSpeed = v, PCT),
			toggle("Cooldown dip", "Vanilla's item dipping down after each hit while the attack recharges. Only with the Vanilla pull-out.",
					c -> c.cooldownDip, (c, v) -> c.cooldownDip = v),
			new Header(""),
			new Action("Test swing", "Swing once (only on your screen, hits nothing).", () -> {
				var p = net.minecraft.client.Minecraft.getInstance().player;
				if (p != null) p.swing(InteractionHand.MAIN_HAND, false); // no packet: client-side only
			}),
			new Action("Test pull-out", "Play the pull-out again.", HandAnim::replayEquip),
			new Action("Reset all", "Everything back to Minecraft's own.", HandAnimScreen::resetAll),
			new Action("Done", "Save and go back.", null));

	private final Screen parent;
	private Slider dragging;
	private float scale = 1;

	public HandAnimScreen(Screen parent) {
		super(Component.literal("Hand Animations"));
		this.parent = parent;
	}

	private static void resetAll() {
		Config c = Config.get(), d = new Config();
		c.handAnim = d.handAnim;
		c.swingStyle = d.swingStyle;
		c.swingMotion = d.swingMotion;
		c.swingSpeed = d.swingSpeed;
		c.swingStrength = d.swingStrength;
		c.handX = d.handX;
		c.handY = d.handY;
		c.handZ = d.handZ;
		c.handScale = d.handScale;
		c.handRotX = d.handRotX;
		c.handRotY = d.handRotY;
		c.handRotZ = d.handRotZ;
		c.equipStyle = d.equipStyle;
		c.equipSpeed = d.equipSpeed;
		c.cooldownDip = d.cooldownDip;
	}

	// ---- layout: panel at (8, 8) in its own units, scaled down to fit short screens ----

	private int rowH(Row r) {
		return r instanceof Header ? HEAD : ROW;
	}

	private int bodyH() {
		int h = 18 + 8;
		for (Row r : rows) h += r instanceof Action ? 0 : rowH(r);
		return h + 2 * (ROW + 4); // two rows of buttons
	}

	/** Each row's top, in panel units; actions sit two to a row at the bottom. */
	private int[] rowY() {
		int[] ys = new int[rows.size()];
		int y = 18, actions = 0, actionsTop = -1;
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			if (r instanceof Action) {
				if (actionsTop < 0) actionsTop = y + 2;
				ys[i] = actionsTop + (actions / 2) * (ROW + 4);
				actions++;
			} else {
				ys[i] = y;
				y += rowH(r);
			}
		}
		return ys;
	}

	private int actionX(int i) {
		int n = 0;
		for (int k = 0; k < i; k++) if (rows.get(k) instanceof Action) n++;
		return PAD + (n % 2) * ((W - PAD * 2) / 2 + 2);
	}

	private double lx(double mx) {
		return (mx - 8) / scale;
	}

	private double ly(double my) {
		return (my - 8 - 7) / scale; // 7 = room for the title plate
	}

	private int barX() {
		return PAD + LABEL_W;
	}

	private int barW() {
		return W - PAD * 2 - LABEL_W - 40;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// nothing: your hand and the world stay clear to see the changes
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		scale = Math.min(1f, (height - 16f) / (bodyH() + 10));
		Config c = Config.get();
		long now = System.currentTimeMillis();
		double mx = lx(mouseX), my = ly(mouseY);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(8, 15);
		pose.scale(scale, scale);
		ScathaStats.frame(g, font, 0, 0, W, bodyH(), now, Component.literal("Hand Animations"), true, false);
		int[] ys = rowY();
		String tip = null;
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			int y = ys[i];
			boolean over;
			switch (r) {
				case Header h -> {
					if (!h.text().isEmpty()) {
						ScathaStats.divider(g, 0, y + 5, W, now);
						int tw = font.width(h.text());
						g.fill((W - tw) / 2 - 4, y + 1, (W + tw) / 2 + 4, y + 10, 0xFF140A24);
						g.text(font, h.text(), (W - tw) / 2, y + 2, 0xFF000000 | GOLD, true);
					}
					over = false;
				}
				case Choice ch -> {
					over = my >= y && my < y + ROW && mx >= PAD && mx < W - PAD;
					label(g, ch.label(), y, over);
					String v = ch.get().apply(c);
					int idx = indexOf(ch.values(), v);
					String shown = "‹ " + ch.names()[idx] + " ›";
					g.text(font, shown, W - PAD - font.width(shown), y + 2, over ? 0xFFFFF1B0 : 0xFF55FFFF, true);
					if (over) tip = ch.tip() + " Click to change.";
				}
				case Slider s -> {
					over = my >= y && my < y + ROW && mx >= PAD && mx < W - PAD;
					label(g, s.label(), y, over);
					int bx = barX(), bw = barW(), v = s.get().applyAsInt(c);
					float t = (v - s.min()) / (float) (s.max() - s.min());
					g.fill(bx, y + 4, bx + bw, y + 7, 0xC0000000);
					g.fill(bx, y + 4, bx + Math.round(bw * t), y + 7, 0xFF000000 | (over || dragging == s ? 0xFFF1B0 : GOLD));
					if (s.min() < 0) g.fill(bx + bw / 2, y + 3, bx + bw / 2 + 1, y + 8, 0x80FFFFFF); // the zero mark
					int kx = bx + Math.round(bw * t);
					g.fill(kx - 2, y + 2, kx + 2, y + 9, 0xFF120A06);
					g.fill(kx - 1, y + 3, kx + 1, y + 8, 0xFF000000 | (v == s.def() ? 0xAAAAAA : 0xFFFFFF));
					String val = s.fmt().apply(v);
					g.text(font, val, W - PAD - font.width(val), y + 2, v == s.def() ? 0xFFAAAAAA : 0xFFFFFFFF, true);
					if (over) tip = s.tip() + " Right-click: back to " + s.fmt().apply(s.def()) + ".";
				}
				case Action a -> {
					int ax = actionX(i), aw = (W - PAD * 2) / 2 - 2;
					over = my >= y && my < y + ROW && mx >= ax && mx < ax + aw;
					boolean done = a.run() == null;
					g.fill(ax, y, ax + aw, y + ROW, over ? 0xFF2E1A4A : 0xFF1A0F2E);
					int edge = 0xFF000000 | (done ? 0x55FF55 : GOLD);
					g.fill(ax, y, ax + aw, y + 1, edge);
					g.fill(ax, y + ROW - 1, ax + aw, y + ROW, edge);
					g.fill(ax, y, ax + 1, y + ROW, edge);
					g.fill(ax + aw - 1, y, ax + aw, y + ROW, edge);
					g.text(font, a.label(), ax + (aw - font.width(a.label())) / 2, y + 2, over ? 0xFFFFF1B0 : 0xFFFFFFFF, true);
					if (over) tip = a.tip();
				}
				default -> over = false;
			}
		}
		pose.popMatrix();
		if (tip != null) g.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	private void label(GuiGraphicsExtractor g, String text, int y, boolean over) {
		g.text(font, text, PAD, y + 2, over ? 0xFFFFF1B0 : 0xFFDDDDDD, true);
	}

	private static int indexOf(String[] values, String v) {
		for (int i = 0; i < values.length; i++) if (values[i].equals(v)) return i;
		return 0;
	}

	/** The row under the mouse (panel units), or -1. */
	private int rowAt(double mx, double my) {
		int[] ys = rowY();
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			if (r instanceof Header || my < ys[i] || my >= ys[i] + ROW) continue;
			if (r instanceof Action) {
				int ax = actionX(i), aw = (W - PAD * 2) / 2 - 2;
				if (mx >= ax && mx < ax + aw) return i;
			} else if (mx >= PAD && mx < W - PAD) return i;
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = lx(event.x()), my = ly(event.y());
		int i = rowAt(mx, my);
		if (i < 0) return super.mouseClicked(event, doubleClick);
		Config c = Config.get();
		boolean right = event.button() == 1;
		switch (rows.get(i)) {
			case Choice ch -> {
				int idx = indexOf(ch.values(), ch.get().apply(c)), n = ch.values().length;
				ch.set().accept(c, ch.values()[Math.floorMod(idx + (right ? -1 : 1), n)]);
			}
			case Slider s -> {
				if (right) s.set().accept(c, s.def());
				else {
					dragging = s;
					slide(s, mx);
				}
			}
			case Action a -> {
				if (a.run() == null) {
					onClose();
					return true;
				}
				a.run().run();
			}
			default -> {}
		}
		c.save();
		return true;
	}

	private void slide(Slider s, double mx) {
		double t = Math.max(0, Math.min(1, (mx - barX()) / barW()));
		int v = (int) Math.round((s.min() + t * (s.max() - s.min())) / s.step()) * s.step();
		s.set().accept(Config.get(), Math.max(s.min(), Math.min(s.max(), v)));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(event, dx, dy);
		slide(dragging, lx(event.x()));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			Config.get().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int i = rowAt(lx(x), ly(y));
		if (i >= 0 && rows.get(i) instanceof Slider s && scrollY != 0) {
			Config c = Config.get();
			int v = s.get().applyAsInt(c) + (scrollY > 0 ? s.step() : -s.step());
			s.set().accept(c, Math.max(s.min(), Math.min(s.max(), v)));
			c.save();
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		Config.get().save();
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
