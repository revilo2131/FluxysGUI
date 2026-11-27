package cleanui.feature;

import cleanui.data.Config;
import cleanui.data.Networth;
import cleanui.data.NetworthCalc;
import cleanui.skyblock.SkillParser;
import cleanui.ui.Anim;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Clean UI settings: theme preset, UI scale, and which Hypixel menus get a Clean UI page.
 * Opened from the gear in any page's header; Back returns to that page. Saved on every change.
 */
public final class SettingsScreen extends Window {
	private static final int SWATCH_W = 36, SWATCH_H = 22, SWATCH_STEP = 40, SWATCH_ROW = 26, PER_ROW = 5, COL_W = 200, RIGHT_X = 214;
	static final int ROW_H = 16, ROW_STEP = 18;
	/** Page toggles: 11 rows, a little tighter so they fit the body. */
	private static final int PAGE_Y = 12, PAGE_STEP = 17;
	private static final int STYLE_Y = 80, CELL_W = (COL_W - 4) / 2, NET_Y = 152;
	private static final int[] SCALES = {80, 90, 100, 110, 120};
	/** The Customize HUD button, right of the API key box. */
	private static final int HUD_W = 92;

	/** A style setting shown as "Label ···· Value"; click cycles it (right-click goes back). */
	private record Knob(String label, String tip, java.util.function.Function<Config, String> value, java.util.function.BiConsumer<Config, Integer> step) {}

	private static final List<Knob> STYLES = List.of(
			new Knob("Frame", "Window frame: Ornate, Clean, Bold or None.", c -> cap(c.frame), (c, d) -> {
				Theme.FrameStyle[] all = Theme.FrameStyle.values();
				c.frame = all[Math.floorMod(Theme.frameStyle.ordinal() + d, all.length)].name();
			}),
			new Knob("Corners", "Square or rounded boxes.", c -> c.rounded ? "Round" : "Square", (c, d) -> c.rounded = !c.rounded),
			new Knob("Bars", "Pointed or flat progress bars.", c -> c.flatBars ? "Flat" : "Angled", (c, d) -> c.flatBars = !c.flatBars),
			new Knob("Motion", "Animations on or off.", c -> c.motion ? "On" : "Off", (c, d) -> c.motion = !c.motion),
			new Knob("Size", "UI scale; still shrinks to fit small screens.", c -> c.uiScale + "%", (c, d) -> {
				int i = 0;
				while (i < SCALES.length - 1 && SCALES[i] < c.uiScale) i++;
				c.uiScale = SCALES[Math.floorMod(i + d, SCALES.length)];
			}));

	private static String cap(String s) {
		return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(java.util.Locale.ROOT);
	}

	record Toggle(String label, String tip, Predicate<Config> get, Consumer<Config> flip) {}

	private static final List<Toggle> TOGGLES = List.of(
			new Toggle("SkyBlock Menu", "The Nether Star menu.", c -> c.skyblockMenu, c -> c.skyblockMenu = !c.skyblockMenu),
			new Toggle("Skills", "Your Skills.", c -> c.skills, c -> c.skills = !c.skills),
			new Toggle("Collections", "Collections and their categories.", c -> c.collections, c -> c.collections = !c.collections),
			new Toggle("Storage", "Ender Chest and Backpacks.", c -> c.storage, c -> c.storage = !c.storage),
			new Toggle("Calendar", "Calendar and Events.", c -> c.calendar, c -> c.calendar = !c.calendar),
			new Toggle("Bestiary", "Bestiary and its pages.", c -> c.bestiary, c -> c.bestiary = !c.bestiary),
			new Toggle("Pets", "Your pets list.", c -> c.pets, c -> c.pets = !c.pets),
			new Toggle("Slayer", "Slayer hub and boss pages.", c -> c.slayer, c -> c.slayer = !c.slayer),
			new Toggle("Level tracks", "A skill's or collection's tiers and rewards.", c -> c.tracks, c -> c.tracks = !c.tracks),
			new Toggle("HOTM tree", "Heart of the Mountain and Heart of the Forest.", c -> c.hotm, c -> c.hotm = !c.hotm),
			new Toggle("Restyle other menus", "Every other Hypixel menu, with its own controls.", c -> c.restyleMenus, c -> c.restyleMenus = !c.restyleMenus));

	private final Screen parent;
	private final List<Anim> knobs = new ArrayList<>();
	private EditBox apiKey;

	public SettingsScreen(Screen parent) {
		super("Settings", new ItemStack(Items.COMPARATOR), 1);
		this.parent = parent;
		for (Toggle t : TOGGLES) {
			Anim knob = new Anim(t.get().test(Config.get()) ? 1f : 0f, 16f);
			knobs.add(knob);
		}
	}

	@Override
	protected String crumb() {
		return parent.getTitle().getString();
	}

	@Override
	protected boolean showSettingsButton() {
		return false;
	}

	@Override
	protected String hint() {
		return "Saved right away. Visual only: clicks pass 1:1. Prices: SkyHelper.";
	}

	@Override
	protected void goBack() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	protected void closed() {
		// the page we came from may still have a Hypixel menu open underneath
		if (parent instanceof MenuPage) minecraft.player.closeContainer();
		else super.closed();
	}

	@Override
	protected void initBody() {
		apiKey = addRenderableWidget(new EditBox(font, bx() + 5, by() + NET_Y + 16, COL_W - HUD_W - 14, 10, Component.literal("Hypixel API key")));
		apiKey.setBordered(false);
		apiKey.setMaxLength(64);
		apiKey.setTextColor(Theme.argb(Theme.TEXT, 1f));
		apiKey.setHint(Component.literal("Hypixel API key").withColor(Theme.TEXT_MUTED));
		String saved = Config.get().hypixelApiKey;
		apiKey.setValue(saved == null ? "" : saved);
		// masked, so the key doesn't show on streams or screenshots
		apiKey.addFormatter((text, start) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
		// the empty cell next to Size: hand animations
		int[] hand = cellPos(STYLES.size());
		addRenderableWidget(new cleanui.ui.Button(hand[0], hand[1], CELL_W, ROW_H, Component.literal("Hand anims"),
				() -> minecraft.setScreenAndShow(new HandAnimScreen(this)))
				.tooltip(Component.literal("How your held item swings, where it sits, and how it comes up when you switch items.")).body());
		// every HUD panel (Scatha Stats, the scoreboard): place, size and options over the game
		addRenderableWidget(new cleanui.ui.Button(bx() + COL_W - HUD_W, by() + NET_Y + 12, HUD_W, 16, Component.literal("Customize HUD"),
				() -> minecraft.setScreenAndShow(new HudEditor(this, false)))
				.item(new ItemStack(Items.PAINTING))
				.tooltip(Component.literal("Move and resize the scoreboard and Scatha Stats, with a snap grid, and pick their looks.")).body());
		apiKey.setResponder(value -> {
			Config.get().hypixelApiKey = value.strip();
			Config.get().save();
			Networth.reset();
		});
	}

	private int[] swatchPos(int i) {
		return new int[]{bx() + (i % PER_ROW) * SWATCH_STEP, by() + 14 + (i / PER_ROW) * SWATCH_ROW};
	}

	private int[] cellPos(int i) {
		return new int[]{bx() + (i % 2) * (CELL_W + 4), by() + STYLE_Y + 12 + (i / 2) * ROW_STEP};
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = bx(), y = by();
		Config c = Config.get();
		String tip = null;

		// theme swatches: a mini window in each theme's own colors and frame
		Theme.divider(g, font, "THEME", x, y, COL_W, a);
		for (int i = 0; i < Theme.PRESETS.size(); i++) {
			Theme.Preset p = Theme.PRESETS.get(i);
			int[] s = swatchPos(i);
			int sx = s[0], sy = s[1];
			boolean selected = p.id().equals(Theme.preset().id());
			boolean hovered = mouseX >= sx && mouseX < sx + SWATCH_W && mouseY >= sy && mouseY < sy + SWATCH_H;
			if (hovered) tip = p.name();
			if (selected || hovered) Theme.box(g, sx - 2, sy - 2, SWATCH_W + 4, SWATCH_H + 4, Theme.PANEL, 0f,
					selected ? Theme.TEXT : Theme.TEXT_MUTED, a);
			Theme.box(g, sx, sy, SWATCH_W, SWATCH_H, p.panel(), a, p.frame(), a);
			if (p.frameStyle() == Theme.FrameStyle.ORNATE || p.frameStyle() == Theme.FrameStyle.BOLD)
				Theme.box(g, sx + (p.frameStyle() == Theme.FrameStyle.BOLD ? 1 : 2), sy + (p.frameStyle() == Theme.FrameStyle.BOLD ? 1 : 2),
						SWATCH_W - (p.frameStyle() == Theme.FrameStyle.BOLD ? 2 : 4), SWATCH_H - (p.frameStyle() == Theme.FrameStyle.BOLD ? 2 : 4),
						p.panel(), 0f, p.frameStyle() == Theme.FrameStyle.BOLD ? p.frame() : p.frameDim(), a);
			g.fill(sx + 6, sy + 6, sx + SWATCH_W - 12, sy + 9, Theme.argb(p.title(), a)); // a title line
			g.fill(sx + 6, sy + 12, sx + SWATCH_W - 6, sy + 16, Theme.argb(p.row(), a)); // a row with an accent value
			g.fill(sx + SWATCH_W - 12, sy + 13, sx + SWATCH_W - 8, sy + 15, Theme.argb(p.accent(), a));
		}
		int nameY = y + 14 + 2 * SWATCH_ROW;
		Theme.textLeader(g, font, "Theme", Theme.TEXT_MUTED, Theme.preset().name(), Theme.TITLE, x, nameY, COL_W, a);

		// style knobs: set by the theme, changeable one by one
		Theme.divider(g, font, "STYLE", x, y + STYLE_Y, COL_W, a);
		for (int i = 0; i < STYLES.size(); i++) {
			Knob st = STYLES.get(i);
			int[] p = cellPos(i);
			boolean over = mouseX >= p[0] && mouseX < p[0] + CELL_W && mouseY >= p[1] && mouseY < p[1] + ROW_H;
			if (over) tip = st.tip() + " Click to change.";
			Theme.box(g, p[0], p[1], CELL_W, ROW_H, over ? Theme.ROW_HOVER : Theme.ROW, a, over ? Theme.FRAME : Theme.LINE, a);
			Theme.textPair(g, font, st.label(), Theme.TEXT_MUTED, st.value().apply(c), Theme.TITLE, p[0] + 5, p[1] + 4, CELL_W - 10, a);
		}

		// networth: personal Hypixel API key (masked) and the current status
		Theme.divider(g, font, "NETWORTH & HUD", x, y + NET_Y, COL_W, a);
		Theme.box(g, x, y + NET_Y + 12, COL_W - HUD_W - 4, 16, Theme.SLOT, a, apiKey.isFocused() ? Theme.FRAME : Theme.LINE, a);
		Networth.refreshIfStale();
		String err = Networth.error();
		NetworthCalc.Result nw = Networth.result();
		int ny = y + NET_Y + 33;
		if (Networth.loading() && nw == null) Theme.label(g, font, "Checking your profile…", x, ny, a);
		else if (err != null) g.text(font, Theme.fit(font, err, COL_W), x, ny, Theme.argb(Theme.color(3), a), true);
		else if (nw != null) Theme.textLeader(g, font, "Networth", Theme.TEXT_MUTED, SkillParser.compact(String.valueOf((long) nw.total())), Theme.ACCENT, x, ny, COL_W, a);
		else Theme.label(g, font, "Get a key at developer.hypixel.net", x, ny, a);
		if (tip != null) g.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY, Theme.tooltipStyle());

		// page toggles
		int rx = x + RIGHT_X, rw = BW - RIGHT_X;
		Theme.divider(g, font, "PAGES", rx, y, rw, a);
		Toggle hovered = null;
		for (int i = 0; i < TOGGLES.size(); i++) {
			Toggle t = TOGGLES.get(i);
			int ry = y + PAGE_Y + i * PAGE_STEP;
			boolean over = mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + ROW_H;
			if (over) hovered = t;
			boolean on = t.get().test(c);
			Anim knob = knobs.get(i);
			knob.target(on ? 1f : 0f);
			toggleRow(g, font, t.label(), on, knob.get(), rx, ry, rw, over, a);
		}
		if (hovered != null)
			g.setTooltipForNextFrame(font, Component.literal(hovered.tip()), mouseX, mouseY, Theme.tooltipStyle());
	}

	/** A settings row: label left, on/off switch right ({@code k} = knob position 0..1, animated). */
	static void toggleRow(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String label, boolean on, float k, int rx, int ry, int rw, boolean over, float a) {
		Theme.box(g, rx, ry, rw, ROW_H, over ? Theme.ROW_HOVER : Theme.ROW, a);
		g.text(font, label, rx + 6, ry + 4, Theme.argb(on ? Theme.TEXT : Theme.TEXT_MUTED, a), true);
		// switch: track fills with the frame color, knob slides across
		int sw = 20, sx = rx + rw - sw - 6, sy = ry + 3;
		Theme.box(g, sx, sy, sw, 10, Theme.mix(Theme.SLOT, Theme.FRAME_DIM, k), a, Theme.mix(Theme.LINE, Theme.FRAME, k), a);
		int kx = sx + 1 + Math.round(k * (sw - 10));
		g.fill(kx, sy + 1, kx + 8, sy + 9, Theme.argb(Theme.mix(Theme.TEXT_MUTED, Theme.TEXT, k), a));
	}

	private void applyAndSave(Config c) {
		Theme.applyPreset(c.theme);
		Theme.applyStyle(c.frame, c.rounded, c.flatBars, c.motion);
		apiKey.setTextColor(Theme.argb(Theme.TEXT, 1f)); // the key box follows the theme's text color
		c.save();
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		int x = bx(), y = by();
		Config c = Config.get();
		for (int i = 0; i < Theme.PRESETS.size(); i++) {
			int[] s = swatchPos(i);
			if (event.x() >= s[0] && event.x() < s[0] + SWATCH_W && event.y() >= s[1] && event.y() < s[1] + SWATCH_H) {
				// a theme brings its own style too; the knobs below can change it afterwards
				Theme.Preset p = Theme.PRESETS.get(i);
				c.theme = p.id();
				c.frame = p.frameStyle().name();
				c.rounded = p.rounded();
				c.flatBars = p.flatBars();
				applyAndSave(c);
				return true;
			}
		}
		for (int i = 0; i < STYLES.size(); i++) {
			int[] p = cellPos(i);
			if (event.x() >= p[0] && event.x() < p[0] + CELL_W && event.y() >= p[1] && event.y() < p[1] + ROW_H) {
				STYLES.get(i).step().accept(c, event.button() == 1 ? -1 : 1);
				applyAndSave(c);
				return true;
			}
		}
		int rx = x + RIGHT_X, rw = BW - RIGHT_X;
		for (int i = 0; i < TOGGLES.size(); i++) {
			int ry = y + PAGE_Y + i * PAGE_STEP;
			if (event.x() >= rx && event.x() < rx + rw && event.y() >= ry && event.y() < ry + ROW_H) {
				TOGGLES.get(i).flip().accept(c);
				c.save();
				return true;
			}
		}
		return false;
	}
}
