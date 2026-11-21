package cleanui.feature;

import cleanui.data.Config;
import cleanui.data.SkyblockItems;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Crystal Nucleus run settings, opened from the Mining skill page: what shows during a run, where
 * the tracker sits and how big, the finale's blur, a test run, and your PB (with a reset).
 * Saved on every change; Back returns to the Mining page.
 */
public final class NucleusSettingsScreen extends Window {
	private static final int COL_W = 200, RIGHT_X = 214, ROW_H = SettingsScreen.ROW_H, ROW_STEP = SettingsScreen.ROW_STEP;
	private static final String[] POSITIONS = {"LEFT", "RIGHT", "TOP"};
	private static final int[] SIZES = {75, 100, 125, 150};

	/** A switch (on != null) or a value you click through (value != null). */
	private record Row(String label, String tip, Predicate<Config> on, Consumer<Config> flip, Function<Config, String> value) {
		static Row toggle(String label, String tip, Predicate<Config> on, Consumer<Config> flip) {
			return new Row(label, tip, on, flip, null);
		}

		static Row choice(String label, String tip, Function<Config, String> value, Consumer<Config> next) {
			return new Row(label, tip, null, next, value);
		}
	}

	private static final List<Row> ROWS = List.of(
			Row.toggle("Crystal tracker", "Five crystal slots on screen during a run: found crystals fill in, placed ones glow.",
					c -> c.nucleusTracker, c -> c.nucleusTracker = !c.nucleusTracker),
			Row.choice("Tracker position", "Where the tracker sits: left, right or top of the screen. Click to change.",
					c -> cap(c.nucleusTrackerPos), c -> c.nucleusTrackerPos = POSITIONS[(indexOf(POSITIONS, c.nucleusTrackerPos) + 1) % POSITIONS.length]),
			Row.choice("Tracker size", "How big the tracker is. Click to change.",
					c -> c.nucleusTrackerScale + "%", c -> c.nucleusTrackerScale = SIZES[(indexOf(SIZES, c.nucleusTrackerScale) + 1) % SIZES.length]),
			Row.toggle("Run timer", "Run time on the tracker: green ahead of your PB, yellow close, red behind. Your PB below it.",
					c -> c.nucleusTimer, c -> c.nucleusTimer = !c.nucleusTimer),
			Row.toggle("Crystal animations", "Fly-in when you find a crystal, a note when you place one, beams when the fifth goes in.",
					c -> c.nucleusAnimations, c -> c.nucleusAnimations = !c.nucleusAnimations),
			Row.toggle("Loot reveal + stats", "The loot bundle spun out on a reel, then a card with your time, splits, PB and profit.",
					c -> c.nucleusLoot, c -> c.nucleusLoot = !c.nucleusLoot),
			Row.toggle("Blur finale", "During the beams, loot and stats: blur the world and hide the rest of the HUD. You can still walk.",
					c -> c.nucleusFocus, c -> c.nucleusFocus = !c.nucleusFocus),
			Row.toggle("PB walk-in", "The buff Scatha walks in when you set a new personal best.",
					c -> c.nucleusPbWalkIn, c -> c.nucleusPbWalkIn = !c.nucleusPbWalkIn));

	private final Screen parent;
	private final List<Anim> knobs = new ArrayList<>();
	private long resetArmed; // Reset PB needs a second click within 3 s
	private Button reset;

	public NucleusSettingsScreen(Screen parent) {
		super("Nucleus", crystalIcon(), 3);
		this.parent = parent;
		for (Row r : ROWS) knobs.add(new Anim(r.on() != null && r.on().test(Config.get()) ? 1f : 0f, 16f));
	}

	/** The Amethyst Crystal (Hypixel's look when its pack is loaded). */
	static ItemStack crystalIcon() {
		ItemStack real = SkyblockItems.stack("Amethyst Crystal");
		return real != null ? real : new ItemStack(Items.AMETHYST_CLUSTER);
	}

	private static String cap(String s) {
		return s.charAt(0) + s.substring(1).toLowerCase(java.util.Locale.ROOT);
	}

	private static int indexOf(String[] a, String v) {
		for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
		return 0;
	}

	private static int indexOf(int[] a, int v) {
		for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
		return 1;
	}

	@Override
	protected String crumb() {
		return parent.getTitle().getString();
	}

	@Override
	protected String hint() {
		return "Saved right away. Test run closes the menu and plays a whole run.";
	}

	@Override
	protected void goBack() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	protected void closed() {
		// the Mining page we came from still has its Hypixel menu open underneath
		if (parent instanceof MenuPage) minecraft.player.closeContainer();
		else super.closed();
	}

	@Override
	protected void initBody() {
		int x = bx() + RIGHT_X, w = BW - RIGHT_X, y = by() + 14;
		addRenderableWidget(new Button(x, y, w, 18, Component.literal("Test nucleus run"), () -> {
			onClose();
			NucleusRun.test(0);
		}).item(crystalIcon()).color(0xFF55FF)
				.tooltip(Component.literal("A whole run from fake chat: five crystals found, placed, the finale, loot and stats.")).body());
		reset = addRenderableWidget(new Button(x, y + 92, w, 18, Component.literal("Reset PB"), this::resetPb)
				.item(new ItemStack(Items.CLOCK)).color(0xFF5555)
				.tooltip(Component.literal("Forget your fastest run. Click twice.")).body());
	}

	private void resetPb() {
		long now = System.currentTimeMillis();
		if (now - resetArmed > 3000) {
			resetArmed = now;
			return;
		}
		resetArmed = 0;
		Config.get().nucleusBestMs = 0;
		Config.get().save();
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = bx(), y = by();
		Config c = Config.get();
		Theme.divider(g, font, "NUCLEUS RUNS", x, y, COL_W, a);
		String tip = null;
		for (int i = 0; i < ROWS.size(); i++) {
			Row r = ROWS.get(i);
			int ry = y + 14 + i * ROW_STEP;
			boolean over = mouseX >= x && mouseX < x + COL_W && mouseY >= ry && mouseY < ry + ROW_H;
			if (over) tip = r.tip();
			if (r.on() != null) {
				boolean on = r.on().test(c);
				knobs.get(i).target(on ? 1f : 0f);
				SettingsScreen.toggleRow(g, font, r.label(), on, knobs.get(i).get(), x, ry, COL_W, over, a);
			} else {
				Theme.box(g, x, ry, COL_W, ROW_H, over ? Theme.ROW_HOVER : Theme.ROW, a, over ? Theme.FRAME : Theme.LINE, a);
				Theme.textPair(g, font, r.label(), Theme.TEXT_MUTED, r.value().apply(c), Theme.TITLE, x + 5, ry + 4, COL_W - 10, a);
			}
		}
		// right: test run, then your best
		int rx = x + RIGHT_X, rw = BW - RIGHT_X;
		Theme.divider(g, font, "TRY IT", rx, y, rw, a);
		Theme.divider(g, font, "PERSONAL BEST", rx, y + 44, rw, a);
		long best = c.nucleusBestMs;
		Theme.textLeader(g, font, "Fastest run", Theme.TEXT_MUTED, best > 0 ? NucleusRun.time(best) : "--", 0xFFAA00, rx, y + 60, rw, a);
		Theme.textLeader(g, font, "Runs this session", Theme.TEXT_MUTED, String.valueOf(NucleusRun.sessionRuns()), Theme.TEXT, rx, y + 74, rw, a);
		boolean armed = System.currentTimeMillis() - resetArmed <= 3000;
		reset.setMessage(Component.literal(armed ? "Click again to reset" : "Reset PB"));
		if (tip != null) g.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY, Theme.tooltipStyle());
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		int x = bx(), y = by();
		for (int i = 0; i < ROWS.size(); i++) {
			int ry = y + 14 + i * ROW_STEP;
			if (event.x() >= x && event.x() < x + COL_W && event.y() >= ry && event.y() < ry + ROW_H) {
				ROWS.get(i).flip().accept(Config.get());
				Config.get().save();
				return true;
			}
		}
		return false;
	}
}
