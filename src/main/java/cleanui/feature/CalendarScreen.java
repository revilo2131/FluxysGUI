package cleanui.feature;

import cleanui.skyblock.Events;
import cleanui.skyblock.SkyblockTime;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.CleanScreen;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SkyBlock calendar: today's date and time, a browsable month grid with event markers,
 * and a live list of active and upcoming events. Everything is computed from the clock.
 */
public final class CalendarScreen extends CleanScreen {
	private static final int W = 400, H = 190;

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
	private static final int COLS = 8, CELL = 22, CELL_GAP = 3, STEP = CELL + CELL_GAP;
	private static final int GRID_W = COLS * STEP - CELL_GAP; // 197
	private static final int LIST_X = Theme.PAD + GRID_W + 14, ROW_H = 17, ROWS = 8;
	private static final int SLIDE = 16; // px a month page slides in from

	private final Anim slide = new Anim(0f, 16f);
	private final Anim monthBar = new Anim(0f, 6f);
	private int viewYear, viewMonth;

	public CalendarScreen(Screen parent) {
		super(Component.literal("Calendar"), parent);
		SkyblockTime.Date today = SkyblockTime.at(SkyblockTime.now());
		viewYear = today.year();
		viewMonth = today.month();
	}

	@Override
	protected void init() {
		int x = left(), y = top() + 60;
		addRenderableWidget(new Button(x, y, 16, 14, Component.literal("<"), () -> shiftMonth(-1))
				.tooltip(Component.literal("Previous month")));
		addRenderableWidget(new Button(x + GRID_W - 16, y, 16, 14, Component.literal(">"), () -> shiftMonth(1))
				.tooltip(Component.literal("Next month")));
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return (height - H) / 2;
	}

	private void shiftMonth(int dir) {
		int m = viewYear * 12 + viewMonth + dir;
		viewYear = Math.floorDiv(m, 12);
		viewMonth = Math.floorMod(m, 12);
		slide.snap(dir * SLIDE); // new page slides in from the side it came from
		slide.target(0f);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int px = left(), py = top(), x = px + Theme.PAD;
		long now = SkyblockTime.now();
		SkyblockTime.Date today = SkyblockTime.at(now);

		Theme.panel(g, px, py, W, H, a);

		// header
		Theme.title(g, font, title.getString(), x, py + Theme.PAD, a);
		String clock = today.clock();
		g.text(font, clock, px + W - Theme.PAD - font.width(clock), py + Theme.PAD, Theme.argb(Theme.TEXT, a), true);

		// today + month progress (animates in from 0 on open)
		g.text(font, today.format(), x, py + 28, Theme.argb(Theme.TEXT, a), true);
		g.text(font, today.season(), x + GRID_W - font.width(today.season()), py + 28, Theme.argb(Theme.ACCENT, a), true);
		long monthStart = SkyblockTime.start(today.year(), today.month(), 1);
		monthBar.target((now - monthStart) / (float) SkyblockTime.MONTH);
		int barY = py + 42;
		Theme.box(g, x, barY, GRID_W, 6, Theme.SLOT, a);
		int fill = Math.round((GRID_W - 2) * monthBar.get());
		g.fill(x + 1, barY + 1, x + 1 + fill, barY + 5, Theme.argb(Theme.ACCENT, a));

		// month title between the arrows
		String month = SkyblockTime.MONTHS[viewMonth] + " · Year " + viewYear;
		g.text(font, month, x + (GRID_W - font.width(month)) / 2, py + 63, Theme.argb(Theme.TEXT, a), true);

		extractGrid(g, x, py + 80, mouseX, mouseY, today, a);
		extractUpcoming(g, px + LIST_X, py + 28, now, a);
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
			Theme.box(g, cx, cy, CELL, CELL, bg, pageAlpha, isToday ? Theme.ACCENT : Theme.LINE, pageAlpha);
			String n = Integer.toString(day);
			g.text(font, n, cx + (CELL - font.width(n)) / 2, cy + 4, Theme.argb(Theme.TEXT, pageAlpha), true);

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
				g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY, Theme.TOOLTIP_STYLE);
			}
		}
	}

	private void extractUpcoming(GuiGraphicsExtractor g, int lx, int ly, long now, float a) {
		int w = W - LIST_X - Theme.PAD;
		g.text(font, "Upcoming", lx, ly, Theme.argb(Theme.TEXT_MUTED, a), true);
		List<Events.Occurrence> list = Events.upcoming(now);
		for (int i = 0; i < Math.min(ROWS, list.size()); i++) {
			Events.Occurrence o = list.get(i);
			int y = ly + 14 + i * ROW_H, color = Theme.color(o.event().slot());
			boolean active = o.active(now);
			// row: stripe in the event's color; active rows get a colored border
			Theme.box(g, lx - 3, y - 4, w + 6, ROW_H - 1, Theme.ROW, a, active ? color : Theme.LINE, a);
			g.fill(lx - 2, y - 3, lx, y + ROW_H - 6, Theme.argb(color, a));
			ItemStack item = EVENT_ITEMS.get(o.event().name());
			if (item != null) Theme.item(g, item, lx + 2, y - 2, 12, a);
			g.text(font, o.event().name(), lx + 17, y, Theme.argb(Theme.TEXT, a), true);
			String when = active
					? (o.end() > o.start() ? SkyblockTime.duration(o.end() - now) + " left" : "now")
					: "in " + SkyblockTime.duration(o.start() - now);
			g.text(font, when, lx + w - font.width(when), y, Theme.argb(active ? color : Theme.TEXT_MUTED, a), true);
		}
	}
}
