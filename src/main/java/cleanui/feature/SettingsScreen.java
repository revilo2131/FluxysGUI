package cleanui.feature;

import cleanui.data.Config;
import cleanui.data.Networth;
import cleanui.data.NetworthCalc;
import cleanui.skyblock.SkillParser;
import cleanui.ui.Anim;
import cleanui.ui.Button;
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
	private static final int SWATCH_W = 36, SWATCH_H = 22, SWATCH_STEP = 40, COL_W = 200, RIGHT_X = 214;
	private static final int ROW_H = 18, ROW_STEP = 20;
	private static final int[] SCALES = {80, 90, 100, 110, 120};

	private record Toggle(String label, String tip, Predicate<Config> get, Consumer<Config> flip) {}

	private static final List<Toggle> TOGGLES = List.of(
			new Toggle("SkyBlock Menu", "The Nether Star menu.", c -> c.skyblockMenu, c -> c.skyblockMenu = !c.skyblockMenu),
			new Toggle("Skills", "Your Skills.", c -> c.skills, c -> c.skills = !c.skills),
			new Toggle("Collections", "Collections and their categories.", c -> c.collections, c -> c.collections = !c.collections),
			new Toggle("Storage", "Ender Chest and Backpacks.", c -> c.storage, c -> c.storage = !c.storage),
			new Toggle("Calendar", "Calendar and Events.", c -> c.calendar, c -> c.calendar = !c.calendar),
			new Toggle("Bestiary", "Bestiary and its pages.", c -> c.bestiary, c -> c.bestiary = !c.bestiary),
			new Toggle("Pets", "Your pets list.", c -> c.pets, c -> c.pets = !c.pets),
			new Toggle("Level tracks", "A skill's or collection's tiers and rewards.", c -> c.tracks, c -> c.tracks = !c.tracks),
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
		return "Changes save right away.";
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
		int y = by() + 76;
		addRenderableWidget(new Button(bx(), y, 18, 16, Component.literal("−"), () -> stepScale(-1))
				.tooltip(Component.literal("Smaller")).body());
		addRenderableWidget(new Button(bx() + 82, y, 18, 16, Component.literal("+"), () -> stepScale(1))
				.tooltip(Component.literal("Bigger")).body());

		apiKey = addRenderableWidget(new EditBox(font, bx() + 5, by() + 120, COL_W - 10, 10, Component.literal("Hypixel API key")));
		apiKey.setBordered(false);
		apiKey.setMaxLength(64);
		apiKey.setTextColor(Theme.argb(Theme.TEXT, 1f));
		apiKey.setHint(Component.literal("Paste your Hypixel API key").withColor(Theme.TEXT_MUTED));
		String saved = Config.get().hypixelApiKey;
		apiKey.setValue(saved == null ? "" : saved);
		// masked, so the key doesn't show on streams or screenshots
		apiKey.addFormatter((text, start) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
		apiKey.setResponder(value -> {
			Config.get().hypixelApiKey = value.strip();
			Config.get().save();
			Networth.reset();
		});
	}

	private void stepScale(int dir) {
		Config c = Config.get();
		int i = 0;
		while (i < SCALES.length - 1 && SCALES[i] < c.uiScale) i++;
		c.uiScale = SCALES[Math.clamp(i + dir, 0, SCALES.length - 1)];
		c.save();
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = bx(), y = by();
		Config c = Config.get();

		// theme swatches: a mini frame in each preset's colors
		Theme.divider(g, font, "THEME", x, y, COL_W, a);
		for (int i = 0; i < Theme.PRESETS.size(); i++) {
			Theme.Preset p = Theme.PRESETS.get(i);
			int sx = x + i * SWATCH_STEP, sy = y + 14;
			boolean selected = p.id().equals(Theme.preset().id());
			boolean hovered = mouseX >= sx && mouseX < sx + SWATCH_W && mouseY >= sy && mouseY < sy + SWATCH_H;
			if (selected || hovered) Theme.box(g, sx - 2, sy - 2, SWATCH_W + 4, SWATCH_H + 4, Theme.PANEL, 0f,
					selected ? Theme.TEXT : Theme.TEXT_MUTED, a);
			Theme.box(g, sx, sy, SWATCH_W, SWATCH_H, Theme.PANEL, a, p.frame(), a);
			Theme.box(g, sx + 2, sy + 2, SWATCH_W - 4, SWATCH_H - 4, Theme.PANEL, 0f, p.frameDim(), a);
			g.fill(sx + 6, sy + 9, sx + SWATCH_W - 6, sy + 13, Theme.argb(p.title(), a));
		}
		Theme.textLeader(g, font, "Theme", Theme.TEXT_MUTED, Theme.preset().name(), Theme.TITLE, x, y + 42, COL_W, a);

		// UI scale
		Theme.divider(g, font, "SIZE", x, y + 60, COL_W, a);
		String scale = c.uiScale + "%";
		g.text(font, scale, x + 50 - font.width(scale) / 2, y + 80, Theme.argb(Theme.TEXT, a), true);
		Theme.label(g, font, "Fits any screen.", x + 108, y + 80, a);

		// networth: personal Hypixel API key (masked) and the current status
		Theme.divider(g, font, "NETWORTH", x, y + 104, COL_W, a);
		Theme.box(g, x, y + 116, COL_W, 16, Theme.SLOT, a, apiKey.isFocused() ? Theme.FRAME : Theme.LINE, a);
		Networth.refreshIfStale();
		String err = Networth.error();
		NetworthCalc.Result nw = Networth.result();
		if (Networth.loading() && nw == null) Theme.label(g, font, "Checking your profile…", x, y + 137, a);
		else if (err != null) g.text(font, Theme.fit(font, err, COL_W), x, y + 137, Theme.argb(Theme.color(3), a), true);
		else if (nw != null) Theme.textLeader(g, font, "Networth", Theme.TEXT_MUTED, SkillParser.compact(String.valueOf((long) nw.total())), Theme.ACCENT, x, y + 137, COL_W, a);
		Theme.label(g, font, "Get one at developer.hypixel.net", x, y + 150, a);

		// about: what this mod does and doesn't do
		Theme.label(g, font, "Visual only: every click is passed on 1:1.", x, y + 170, a);
		Theme.label(g, font, "Prices: SkyHelper. Not by Hypixel.", x, y + 181, a);

		// page toggles
		int rx = x + RIGHT_X, rw = BW - RIGHT_X;
		Theme.divider(g, font, "PAGES", rx, y, rw, a);
		Toggle hovered = null;
		for (int i = 0; i < TOGGLES.size(); i++) {
			Toggle t = TOGGLES.get(i);
			int ry = y + 14 + i * ROW_STEP;
			boolean over = mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + ROW_H;
			if (over) hovered = t;
			boolean on = t.get().test(c);
			Anim knob = knobs.get(i);
			knob.target(on ? 1f : 0f);
			float k = knob.get();
			Theme.box(g, rx, ry, rw, ROW_H, over ? Theme.ROW_HOVER : Theme.ROW, a);
			g.text(font, t.label(), rx + 6, ry + 5, Theme.argb(on ? Theme.TEXT : Theme.TEXT_MUTED, a), true);
			// switch: track fills with the frame color, knob slides across
			int sw = 20, sx = rx + rw - sw - 6, sy = ry + 4;
			Theme.box(g, sx, sy, sw, 10, Theme.mix(Theme.SLOT, Theme.FRAME_DIM, k), a, Theme.mix(Theme.LINE, Theme.FRAME, k), a);
			int kx = sx + 1 + Math.round(k * (sw - 10));
			g.fill(kx, sy + 1, kx + 8, sy + 9, Theme.argb(Theme.mix(Theme.TEXT_MUTED, Theme.TEXT, k), a));
		}
		if (hovered != null)
			g.setTooltipForNextFrame(font, Component.literal(hovered.tip()), mouseX, mouseY, Theme.tooltipStyle());
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		int x = bx(), y = by();
		Config c = Config.get();
		for (int i = 0; i < Theme.PRESETS.size(); i++) {
			int sx = x + i * SWATCH_STEP, sy = y + 14;
			if (event.x() >= sx && event.x() < sx + SWATCH_W && event.y() >= sy && event.y() < sy + SWATCH_H) {
				c.theme = Theme.PRESETS.get(i).id();
				Theme.applyPreset(c.theme);
				c.save();
				return true;
			}
		}
		int rx = x + RIGHT_X, rw = BW - RIGHT_X;
		for (int i = 0; i < TOGGLES.size(); i++) {
			int ry = y + 14 + i * ROW_STEP;
			if (event.x() >= rx && event.x() < rx + rw && event.y() >= ry && event.y() < ry + ROW_H) {
				TOGGLES.get(i).flip().accept(c);
				c.save();
				return true;
			}
		}
		return false;
	}
}
