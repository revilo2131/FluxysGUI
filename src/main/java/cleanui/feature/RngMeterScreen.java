package cleanui.feature;

import cleanui.skyblock.RngParser;
import cleanui.skyblock.SkillParser;
import cleanui.ui.Anim;
import cleanui.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Any "... RNG Meter" menu (Crystal Nucleus, Slayers, Catacombs). Left: the selected drop with its
 * meter, XP, runs left, and the menu's buttons. Right: every drop grouped by rarity (Rare →
 * RNGesus Incarnate) in a scrolling list. Clicks go to the real menu.
 */
public final class RngMeterScreen extends MenuPage {
	private static final int LEFT_W = 150, LIST_X = LEFT_W + 10, LIST_W = BW - LIST_X;
	private static final int COLS = 2, COL_GAP = 6, ROW_H = 29, ROW_STEP = 34, HEADER_H = 16, SCROLLBAR = 6;
	private static final int BUTTON_H = 18;

	/** A drop row, or a group header when drop is null; y is unscrolled, relative to the list top. */
	private record Row(MenuItem item, RngParser.Drop drop, String rarity, int count, int x, int y, int w) {}

	private final String parent;
	private final Map<Integer, Anim> hover = new HashMap<>();
	private final Anim meter = new Anim(0f, 5f), scroll = new Anim(0f, 18f);
	private float scrollTarget;

	private RngMeterScreen(String title, String parent, AbstractContainerScreen<?> original) {
		super(title, new ItemStack(Items.ENDER_EYE), 2, original);
		this.parent = parent;
	}

	public static RngMeterScreen forTitle(String title, AbstractContainerScreen<?> original) {
		if (!title.endsWith("RNG Meter")) return null;
		// opened from a Clean UI page (e.g. Heart of the Mountain): keep it in the breadcrumb
		String parent = Minecraft.getInstance().gui.screen() instanceof MenuPage p ? p.getTitle().getString() : "SkyBlock Menu";
		return new RngMeterScreen(title, parent, original);
	}

	@Override
	protected String crumb() {
		return parent;
	}

	@Override
	protected String hint() {
		return "Click a drop to select it. Scroll for more.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(originalMenuButton(bx(), by() + BH - BUTTON_H));
	}

	/** Drops grouped by rarity (rarest last), two per line, each group under its own header. */
	private List<Row> rows(List<MenuItem> items) {
		List<MenuItem> drops = new ArrayList<>();
		for (MenuItem i : items) if (!i.name().isEmpty() && RngParser.drop(i.lore()) != null) drops.add(i);
		drops.sort(Comparator.comparingInt(i -> RngParser.rank(RngParser.drop(i.lore()).rarity()))); // stable: keeps menu order inside a group
		List<Row> rows = new ArrayList<>();
		int colW = (LIST_W - SCROLLBAR - (COLS - 1) * COL_GAP) / COLS, y = 0, col = 0;
		String group = null;
		for (MenuItem item : drops) {
			RngParser.Drop d = RngParser.drop(item.lore());
			if (!d.rarity().equals(group)) {
				if (col > 0) y += ROW_STEP;
				group = d.rarity();
				String g = group;
				rows.add(new Row(null, null, group, (int) drops.stream().filter(i -> RngParser.drop(i.lore()).rarity().equals(g)).count(), 0, y, LIST_W - SCROLLBAR));
				y += HEADER_H;
				col = 0;
			}
			rows.add(new Row(item, d, d.rarity(), 0, col * (colW + COL_GAP), y, colW));
			if (++col == COLS) {
				col = 0;
				y += ROW_STEP;
			}
		}
		return rows;
	}

	private static int contentHeight(List<Row> rows) {
		return rows.isEmpty() ? 0 : rows.getLast().y() + ROW_H + 2;
	}

	/** Search / Reset (anything that isn't a drop, the meter itself or navigation), stacked above Original menu. */
	private List<MenuItem> buttons(List<MenuItem> items) {
		return items.stream().filter(i -> !i.name().isEmpty() && !NAV.contains(i.name()) && !i.name().equals(getTitle().getString())
				&& RngParser.drop(i.lore()) == null).toList();
	}

	private int buttonY(int index) {
		return by() + BH - BUTTON_H - (index + 1) * (BUTTON_H + 4);
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		List<Row> rows = rows(items);
		if (giveUpIfUnreadable(rows.isEmpty())) return;
		MenuItem header = items.stream().filter(i -> i.name().equals(getTitle().getString())).findFirst().orElse(null);
		Row selected = rows.stream().filter(r -> r.drop() != null && r.drop().selected()).findFirst().orElse(null);
		MenuItem tooltip = extractSelected(g, header, selected, mouseX, mouseY, a);

		List<MenuItem> buttons = buttons(items);
		for (int i = 0; i < buttons.size(); i++) {
			MenuItem b = buttons.get(i);
			int y = buttonY(buttons.size() - 1 - i);
			boolean over = mouseX >= bx() && mouseX < bx() + LEFT_W && mouseY >= y && mouseY < y + BUTTON_H;
			if (over) tooltip = b;
			float h = hoverOf(b.slot(), over);
			Theme.box(g, bx(), y, LEFT_W, BUTTON_H, Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, Theme.FRAME, 0.8f * h), a);
			Theme.item(g, b.stack(), bx() + 5, y + 3, 12, a);
			g.text(font, Theme.fit(font, b.name(), LEFT_W - 26), bx() + 22, y + 5, Theme.argb(Theme.TEXT, a), true);
		}

		MenuItem listTip = extractList(g, rows, mouseX, mouseY, a);
		if (listTip != null) tooltip = listTip;
		if (tooltip != null) Theme.itemTooltip(g, tooltip.stack(), mouseX, mouseY);
	}

	private float hoverOf(int slot, boolean over) {
		Anim hv = hover.computeIfAbsent(slot, k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		return hv.get();
	}

	/** Left card: the selected drop with every detail. Returns the meter item when hovered (for its tooltip). */
	private MenuItem extractSelected(GuiGraphicsExtractor g, MenuItem header, Row selected, int mouseX, int mouseY, float a) {
		int x = bx(), y = by(), w = LEFT_W, h = buttonY(buttons(menuItems()).size() - 1) - y - 8;
		Theme.divider(g, font, "SELECTED DROP", x, y, w, a);
		int cy = y + 12, ch = h - 12;
		int color = selected != null ? RngParser.color(selected.drop().rarity()) : Theme.FRAME;
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + ch;
		Theme.box(g, x, cy, w, ch, Theme.ROW, a, Theme.mix(Theme.LINE, color, 0.6f), a);
		g.fill(x + 1, cy + 1, x + w - 1, cy + 3, Theme.argb(color, a));
		if (selected == null) {
			g.text(font, "No drop selected", x + 8, cy + 12, Theme.argb(Theme.TITLE, a), true);
			List<FormattedCharSequence> help = font.split(Component.literal("Click a drop on the right to aim the meter at it."), w - 16);
			for (int i = 0; i < help.size(); i++) g.text(font, help.get(i), x + 8, cy + 26 + i * 10, Theme.argb(Theme.TEXT_MUTED, a), true);
			return over ? header : null;
		}
		RngParser.Drop d = selected.drop();
		Theme.slot(g, selected.item().stack(), x + 8, cy + 10, 1.3f, a);
		int tx = x + 36;
		List<FormattedCharSequence> name = font.split(Component.literal(selected.item().name()), w - 44);
		for (int i = 0; i < Math.min(2, name.size()); i++) g.text(font, name.get(i), tx, cy + 9 + i * 10, Theme.argb(Theme.TITLE, a), true);
		g.text(font, Theme.fit(font, d.rarity(), w - 16), x + 8, cy + 34, Theme.argb(bright(color), a), true);

		long perRun = header != null ? RngParser.perRun(header.lore()) : -1, runs = RngParser.runsLeft(d, perRun);
		int ly = cy + 48;
		Theme.textLeader(g, font, "Drop chance", Theme.TEXT_MUTED, d.chance(), bright(color), x + 8, ly, w - 16, a);
		if (d.done() != null) Theme.textLeader(g, font, "Meter XP", Theme.TEXT_MUTED, SkillParser.compact(d.done()) + " / " + SkillParser.compact(d.total()),
				Theme.TEXT, x + 8, ly + 12, w - 16, a);
		if (runs >= 0) Theme.textLeader(g, font, "Runs left", Theme.TEXT_MUTED, runs == 0 ? "none!" : String.format("%,d", runs),
				runs == 0 ? Theme.ACCENT : Theme.TEXT, x + 8, ly + 24, w - 16, a);
		if (perRun > 0) Theme.textLeader(g, font, "XP per run", Theme.TEXT_MUTED, String.format("%,d", perRun), Theme.TEXT, x + 8, ly + 36, w - 16, a);
		meter.target(d.fill());
		Theme.bar(g, font, x + 8, cy + ch - 18, w - 16, 11, meter.get(), d.fill() >= 1f ? Theme.ACCENT : Theme.mix(color, 0x000000, 0.2f),
				Math.round(d.fill() * 1000) / 10f + "%", a);
		return over ? header : null;
	}

	/** Right side: scrolling, clipped list of rarity groups. Returns the hovered drop, if any. */
	private MenuItem extractList(GuiGraphicsExtractor g, List<Row> rows, int mouseX, int mouseY, float a) {
		int x = bx() + LIST_X, y = by(), listH = BH;
		int max = Math.max(0, contentHeight(rows) - listH);
		scrollTarget = Math.clamp(scrollTarget, 0, max);
		scroll.target(scrollTarget);
		int off = Math.round(scroll.get());
		boolean inList = mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + listH;
		MenuItem hovered = null;
		g.enableScissor(x, y, x + LIST_W, y + listH);
		for (Row r : rows) {
			int rx = x + r.x(), ry = y + r.y() - off;
			if (ry + ROW_H < y || ry > y + listH) continue; // off screen
			int color = RngParser.color(r.rarity());
			if (r.drop() == null) { // group header: "Pray RNGesus  7" and a line in its colour
				g.text(font, r.rarity(), rx, ry + 3, Theme.argb(bright(color), a), true);
				String n = String.valueOf(r.count());
				int nx = rx + font.width(r.rarity()) + 5;
				g.text(font, n, nx, ry + 3, Theme.argb(Theme.TEXT_MUTED, a), true);
				g.fill(nx + font.width(n) + 5, ry + 7, rx + r.w(), ry + 8, Theme.argb(Theme.mix(color, Theme.PANEL, 0.6f), a));
				continue;
			}
			boolean over = inList && mouseX >= rx && mouseX < rx + r.w() && mouseY >= ry && mouseY < ry + ROW_H;
			if (over) hovered = r.item();
			extractDrop(g, r, rx, ry, hoverOf(r.item().slot(), over), a);
		}
		g.disableScissor();
		if (max > 0) { // scrollbar
			int thumbH = Math.max(16, listH * listH / (listH + max)), thumbY = y + Math.round((listH - thumbH) * (off / (float) max));
			g.fill(x + LIST_W - 2, y, x + LIST_W, y + listH, Theme.argb(Theme.SLOT, a));
			g.fill(x + LIST_W - 2, thumbY, x + LIST_W, thumbY + thumbH, Theme.argb(Theme.TEXT_MUTED, a));
		}
		return hovered;
	}

	/** Drop row: rarity stripe, item, name; chance and meter status under it; meter fill along the bottom. */
	private void extractDrop(GuiGraphicsExtractor g, Row r, int x, int y, float h, float a) {
		RngParser.Drop d = r.drop();
		int color = RngParser.color(d.rarity()), w = r.w();
		Theme.box(g, x, y, w, ROW_H, d.selected() ? Theme.mix(Theme.ROW, Theme.FRAME, 0.14f) : Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a,
				d.selected() ? Theme.FRAME : Theme.mix(Theme.LINE, color, 0.7f * h), a);
		g.fill(x + 1, y + 1, x + 3 + Math.round(2 * h), y + ROW_H - 1, Theme.argb(color, a));
		Theme.item(g, r.item().stack(), x + 7, y + 4, Math.round(16 + 2 * h), a);
		int tx = x + 28, right = x + w - 6;
		g.text(font, Theme.fit(font, r.item().name(), right - tx), tx, y + 4, Theme.argb(d.selected() ? Theme.TITLE : Theme.TEXT, a), true);
		if (d.rarity().equals(RngParser.LOCKED)) { // "???": just say what unlocks it
			g.text(font, Theme.fit(font, d.chance(), right - tx), tx, y + 14, Theme.argb(Theme.TEXT_MUTED, a), true);
			return;
		}
		g.text(font, d.chance(), tx, y + 14, Theme.argb(bright(color), a), true);
		String status = d.selected() ? "SELECTED" : d.fill() >= 1f ? "FULL" : Math.round(d.fill() * 100) + "%";
		int statusColor = d.selected() ? Theme.FRAME : d.fill() >= 1f ? Theme.ACCENT : Theme.TEXT_MUTED;
		g.text(font, status, right - font.width(status), y + 14, Theme.argb(statusColor, a), true);
		g.fill(tx, y + ROW_H - 3, right, y + ROW_H - 2, Theme.argb(Theme.SLOT, a));
		g.fill(tx, y + ROW_H - 3, tx + Math.round((right - tx) * d.fill()), y + ROW_H - 2, Theme.argb(d.fill() >= 1f ? Theme.ACCENT : color, a));
	}

	/** Rarity colour lifted a bit so dark ones (Extraordinary purple) read on the navy rows. */
	private static int bright(int color) {
		return Theme.mix(color, 0xFFFFFF, 0.25f);
	}

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= bx() + LIST_X && mouseX < bx() + BW && mouseY >= by() && mouseY < by() + BH) {
			scrollTarget -= (float) scrollY * ROW_STEP;
			return true;
		}
		return super.scrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		List<MenuItem> items = menuItems();
		List<MenuItem> buttons = buttons(items);
		for (int i = 0; i < buttons.size(); i++) {
			int y = buttonY(buttons.size() - 1 - i);
			if (event.x() >= bx() && event.x() < bx() + LEFT_W && event.y() >= y && event.y() < y + BUTTON_H) {
				clickAs(buttons.get(i).slot(), event);
				return true;
			}
		}
		int x = bx() + LIST_X, off = Math.round(scroll.get());
		if (event.x() < x || event.x() >= x + LIST_W || event.y() < by() || event.y() >= by() + BH) return false;
		for (Row r : rows(items)) {
			int rx = x + r.x(), ry = by() + r.y() - off;
			if (r.drop() != null && event.x() >= rx && event.x() < rx + r.w() && event.y() >= ry && event.y() < ry + ROW_H) {
				clickAs(r.item().slot(), event);
				return true;
			}
		}
		return false;
	}
}
