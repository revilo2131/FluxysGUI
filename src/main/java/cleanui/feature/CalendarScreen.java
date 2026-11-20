package cleanui.feature;

import cleanui.data.Mayor;
import cleanui.skyblock.Events;
import cleanui.skyblock.SkyblockTime;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SkyBlock calendar, shown in place of Hypixel's "Calendar and Events" menu: a browsable
 * month grid with event markers, this month's events, and a live list of active and
 * upcoming events, plus the current mayor (from Hypixel's public election data). Events are
 * computed from the clock; the menu is only kept open
 * so Back and "Original menu" work.
 */
public final class CalendarScreen extends MenuPage {
	public static final String MENU_TITLE = "Calendar and Events";

	/** Item shown next to each event; vanilla items close to what SkyBlock itself uses. */
	private static final Map<String, ItemStack> EVENT_ITEMS = Map.of(
			"Hoppity's Hunt", new ItemStack(Items.RABBIT_FOOT),
			"Traveling Zoo", new ItemStack(Items.LEAD),
			"Mayor Election", new ItemStack(Items.PAPER),
			"Spooky Festival", new ItemStack(Items.JACK_O_LANTERN),
			"Season of Jerry", new ItemStack(Items.SNOWBALL),
			"New Year Celebration", new ItemStack(Items.CAKE),
			"Dark Auction", new ItemStack(Items.ENDER_EYE),
			"Jacob's Contest", new ItemStack(Items.WHEAT));
	private static final int COLS = 8, CELL = 24, STEP = CELL + 3;
	private static final int GRID_W = COLS * STEP - 3; // 213
	private static final int LIST_X = GRID_W + 14, ROW_STEP = 20, ROWS = 4;
	private static final int MAYOR_Y = 13 + ROWS * ROW_STEP + 3, MAYOR_H = 64, MODEL_W = 42;
	private static final int NAV_Y = 0, GRID_Y = 20, MONTH_LINES = 3;
	private static final int SLIDE = 16; // px a month page slides in from

	private final Anim slide = new Anim(0f, 16f);
	private int viewYear, viewMonth;
	private MayorModel model;

	public CalendarScreen(AbstractContainerScreen<?> original) {
		super("Calendar", new ItemStack(Items.CLOCK), 1, original);
		SkyblockTime.Date today = SkyblockTime.at(SkyblockTime.now());
		viewYear = today.year();
		viewMonth = today.month();
	}

	@Override
	protected String hint() {
		return "Hover a day to see its events.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(new Button(bx(), by() + NAV_Y, 18, 14, Component.literal("<"), () -> shiftMonth(-1))
				.tooltip(Component.literal("Previous month")).body());
		addRenderableWidget(new Button(bx() + GRID_W - 18, by() + NAV_Y, 18, 14, Component.literal(">"), () -> shiftMonth(1))
				.tooltip(Component.literal("Next month")).body());
		addRenderableWidget(originalMenuButton(bx() + BW - 86, by() + BH - 18));
	}

	private void shiftMonth(int dir) {
		int m = viewYear * 12 + viewMonth + dir;
		viewYear = Math.floorDiv(m, 12);
		viewMonth = Math.floorMod(m, 12);
		slide.snap(dir * SLIDE); // new month slides in from the side it came from
		slide.target(0f);
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = bx(), y = by();
		long now = SkyblockTime.now();
		SkyblockTime.Date today = SkyblockTime.at(now);

		// month title between the arrows
		String month = SkyblockTime.MONTHS[viewMonth] + " · Year " + viewYear;
		g.text(font, month, x + (GRID_W - font.width(month)) / 2, y + NAV_Y + 3, Theme.argb(Theme.TEXT, a), true);

		extractGrid(g, x, y + GRID_Y, mouseX, mouseY, today, a);
		extractMonthEvents(g, x, y + GRID_Y + 4 * STEP + 7, a);

		int lx = x + LIST_X, lw = BW - LIST_X;
		Theme.divider(g, font, "UPCOMING", lx, y + 1, lw, a);
		List<Events.Occurrence> list = Events.upcoming(now);
		for (int i = 0; i < Math.min(ROWS, list.size()); i++)
			extractEventRow(g, font, list.get(i), now, lx, y + 13 + i * ROW_STEP, lw, a);
		extractMayor(g, lx, y + MAYOR_Y, lw, mouseX, mouseY, a);
	}

	/** Current mayor: their 3D model, name and perk names; hover for the full perk text. */
	private void extractMayor(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float a) {
		Theme.divider(g, font, "MAYOR", x, y, w, a);
		int cy = y + 12;
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + MAYOR_H;
		Theme.box(g, x, cy, w, MAYOR_H, over ? Theme.ROW_HOVER : Theme.ROW, a, over ? Theme.FRAME : Theme.LINE, a);
		Mayor.Info m = Mayor.get();
		if (m == null) {
			g.text(font, Mayor.failed() ? "Couldn't load the mayor." : "Loading mayor...", x + 8, cy + 8, Theme.argb(Theme.TEXT_MUTED, a), true);
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (m.skin() != null && mc.level != null && (model == null || !model.skin.equals(m.skin()))) model = new MayorModel(mc.level, m.skin());
		// models can't fade, so it appears once the frame is mostly in
		if (model != null && a > 0.5f)
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 2, cy + 2, x + MODEL_W, cy + MAYOR_H - 2, 28, 0.0625f, mouseX, mouseY, model);

		int tx = x + MODEL_W + 4, tw = w - MODEL_W - 10;
		g.text(font, m.name(), tx, cy + 6, Theme.argb(Theme.TITLE, a), true);
		int line = 0;
		for (Mayor.Perk p : m.perks()) {
			if (line == 3) break;
			g.text(font, Theme.fit(font, "• " + p.name(), tw), tx, cy + 18 + line++ * 10, Theme.argb(Theme.ACCENT, a), true);
		}
		if (m.minister() != null)
			Theme.textLeader(g, font, "Minister", Theme.TEXT_MUTED, m.minister(), Theme.TEXT, tx, cy + MAYOR_H - 12, tw, a);
		if (over) g.setTooltipForNextFrame(font, perkTooltip(m), mouseX, mouseY, Theme.tooltipStyle());
	}

	/** "Mayor X", each perk in gold with its description wrapped, then the minister and their perk. */
	private List<FormattedCharSequence> perkTooltip(Mayor.Info m) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.add(Component.literal("Mayor " + m.name()).withColor(Theme.TITLE).getVisualOrderText());
		for (Mayor.Perk p : m.perks()) addPerk(lines, p);
		if (m.ministerPerk() != null) {
			lines.add(FormattedCharSequence.EMPTY);
			lines.add(Component.literal("Minister " + m.minister()).withColor(Theme.TITLE).getVisualOrderText());
			addPerk(lines, m.ministerPerk());
		}
		return lines;
	}

	private void addPerk(List<FormattedCharSequence> lines, Mayor.Perk p) {
		lines.add(FormattedCharSequence.EMPTY);
		lines.add(Component.literal(p.name()).withColor(0xFFAA00).getVisualOrderText());
		// Hypixel's text carries § colors, which the font draws; §7 (gray) is its base color
		lines.addAll(font.split(Component.literal("§7" + p.description()), 200));
	}

	private void extractGrid(GuiGraphicsExtractor g, int gx, int gy, int mouseX, int mouseY, SkyblockTime.Date today, float a) {
		float s = slide.get();
		float pageAlpha = a * (1f - Math.abs(s) / SLIDE);
		int ox = Math.round(s);
		boolean thisMonth = viewYear == today.year() && viewMonth == today.month();

		for (int day = 1; day <= 31; day++) {
			int cx = gx + ((day - 1) % COLS) * STEP + ox, cy = gy + ((day - 1) / COLS) * STEP;
			boolean isToday = thisMonth && day == today.day();
			boolean hovered = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
			List<Events.Event> events = Events.on(viewYear, viewMonth, day);

			int bg = isToday ? Theme.mix(Theme.ROW, Theme.ACCENT, 0.35f) : hovered ? Theme.ROW_HOVER : Theme.ROW;
			Theme.box(g, cx, cy, CELL, CELL, bg, pageAlpha, isToday ? Theme.ACCENT : hovered ? Theme.FRAME : Theme.LINE, pageAlpha);
			String n = Integer.toString(day);
			g.text(font, n, cx + (CELL - font.width(n)) / 2, cy + 5, Theme.argb(Theme.TEXT, pageAlpha), true);

			// one dot per event, centered along the bottom
			int dots = Math.min(events.size(), 3), dx = cx + (CELL - (dots * 4 - 1)) / 2;
			for (int i = 0; i < dots; i++)
				g.fill(dx + i * 4, cy + CELL - 6, dx + i * 4 + 3, cy + CELL - 3,
						Theme.argb(Theme.color(events.get(i).slot()), pageAlpha));

			if (hovered) {
				List<Component> lines = new ArrayList<>();
				lines.add(Component.literal(SkyblockTime.MONTHS[viewMonth] + " " + SkyblockTime.ordinal(day)));
				if (events.isEmpty()) lines.add(Component.literal("No events").withColor(Theme.TEXT_MUTED));
				for (Events.Event e : events) lines.add(Component.literal("● " + e.name()).withColor(Theme.color(e.slot())));
				g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY, Theme.tooltipStyle());
			}
		}
	}

	/** "THIS MONTH": each yearly event in the viewed month with its day range, e.g. "1–3". */
	private void extractMonthEvents(GuiGraphicsExtractor g, int x, int y, float a) {
		Theme.divider(g, font, "THIS MONTH", x, y, GRID_W, a);
		Map<String, int[]> ranges = new LinkedHashMap<>(); // name → {first, last, slot}
		for (int day = 1; day <= 31; day++) {
			int d = day;
			for (Events.Event e : Events.on(viewYear, viewMonth, day))
				ranges.computeIfAbsent(e.name(), k -> new int[]{d, d, e.slot()})[1] = d;
		}
		if (ranges.isEmpty()) {
			g.text(font, "No events this month.", x, y + 13, Theme.argb(Theme.TEXT_MUTED, a), true);
			return;
		}
		int line = 0;
		for (Map.Entry<String, int[]> r : ranges.entrySet()) {
			if (line == MONTH_LINES) break;
			int[] v = r.getValue();
			String days = v[0] == 1 && v[1] == 31 ? "all month" : v[0] == v[1] ? SkyblockTime.ordinal(v[0]) : v[0] + "–" + v[1];
			int ly = y + 13 + line++ * 12;
			g.fill(x, ly + 2, x + 4, ly + 6, Theme.argb(Theme.color(v[2]), a));
			Theme.textLeader(g, font, r.getKey(), Theme.TEXT, days, Theme.ACCENT, x + 8, ly, GRID_W - 8, a);
		}
	}

	/** One event row: color stripe, item, name, countdown. Shared with the Overview page. */
	static void extractEventRow(GuiGraphicsExtractor g, Font font, Events.Occurrence o, long now, int x, int y, int w, float a) {
		int color = Theme.color(o.event().slot());
		boolean active = o.active(now);
		Theme.box(g, x, y, w, 18, active ? Theme.mix(Theme.ROW, color, 0.15f) : Theme.ROW, a, active ? color : Theme.LINE, a);
		g.fill(x + 1, y + 1, x + 3, y + 17, Theme.argb(color, a));
		ItemStack item = EVENT_ITEMS.get(o.event().name());
		if (item != null) Theme.item(g, item, x + 7, y + 3, 12, a);
		String when = active
				? (o.end() > o.start() ? SkyblockTime.duration(o.end() - now) + " left" : "now")
				: "in " + SkyblockTime.duration(o.start() - now);
		Theme.textLeader(g, font, o.event().name(), Theme.TEXT, when, active ? color : Theme.ACCENT, x + 23, y + 5, w - 29, a);
	}
}
