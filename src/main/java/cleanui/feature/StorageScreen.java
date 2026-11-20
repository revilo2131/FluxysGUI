package cleanui.feature;

import cleanui.data.StorageCache;
import cleanui.skyblock.SkyblockTime;
import cleanui.skyblock.StorageParser;
import cleanui.skyblock.StorageParser.Kind;
import cleanui.skyblock.StorageParser.PageId;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storage page, shown in place of Hypixel's "Storage" menu: every ender chest page and
 * backpack in a scrollable list, and the selected one's contents from the last time the
 * player opened it. "Open" opens the real page with one normal click; moving items
 * always happens in Hypixel's own page.
 */
public final class StorageScreen extends MenuPage {
	private static final int LIST_W = 196, ROW_H = 22, ROW_STEP = 24, LABEL_H = 14;
	private static final int PANEL_X = LIST_W + 12, PANEL_W = BW - PANEL_X, SLOT = 18, COLS = 9;
	private static final Logger LOGGER = LoggerFactory.getLogger("cleanui");
	private static final Set<String> LOGGED = new HashSet<>();
	/** Last selected page, kept between opens. */
	private static PageId lastSelected;

	private record Entry(PageId id, int menuSlot, ItemStack icon) {}

	/** A list row (or group label when entry is null) at its unscrolled y. */
	private record Row(Entry entry, String label, int y) {}

	private final Anim scroll = new Anim(0f, 18f);
	private float scrollTarget;
	private List<Entry> entries = List.of();
	private Button open;

	public StorageScreen(AbstractContainerScreen<?> original) {
		super("Storage", new ItemStack(Items.ENDER_CHEST), 1, original);
	}

	@Override
	protected String hint() {
		return "Pages update each time you open them.";
	}

	@Override
	protected void initBody() {
		int px = bx() + PANEL_X;
		open = addRenderableWidget(new Button(px + PANEL_W - 52, by(), 52, 18, Component.literal("Open"), this::openSelected)
				.color(Theme.ACCENT).tooltip(Component.literal("Open this page in Hypixel's menu.")).body());
		addRenderableWidget(originalMenuButton(px + PANEL_W - 86, by() + BH - 18));
	}

	/** Dev preview only. */
	public static void selectForPreview(PageId id) {
		lastSelected = id;
	}

	private Entry selected() {
		for (Entry e : entries) if (e.id().equals(lastSelected)) return e;
		return entries.isEmpty() ? null : entries.getFirst();
	}

	private void openSelected() {
		Entry e = selected();
		if (e != null) click(e.menuSlot(), 0);
	}

	/** Menu items that open a page, ender chest first; unrecognized names are logged once. */
	private List<Entry> read() {
		Map<PageId, Entry> found = new LinkedHashMap<>();
		for (MenuItem item : menuItems()) {
			StorageParser.MenuEntry m = StorageParser.menuItem(item.name(), item.lore());
			if (m != null && !m.empty()) found.putIfAbsent(m.id(), new Entry(m.id(), item.slot(), item.stack()));
			else if (m == null && !item.name().isEmpty() && LOGGED.add(item.name()))
				LOGGER.info("[Clean UI] Storage menu item not used: '{}' (slot {})", item.name(), item.slot());
		}
		List<Entry> list = new ArrayList<>(found.values());
		list.sort(Comparator.comparing((Entry e) -> e.id().kind()).thenComparingInt(e -> e.id().number()));
		return list;
	}

	private List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		int y = 0;
		Kind kind = null;
		for (Entry e : entries) {
			if (e.id().kind() != kind) {
				kind = e.id().kind();
				if (y > 0) y += 4;
				rows.add(new Row(null, kind == Kind.ENDER_CHEST ? "ENDER CHEST" : "BACKPACKS", y));
				y += LABEL_H;
			}
			rows.add(new Row(e, null, y));
			y += ROW_STEP;
		}
		return rows;
	}

	private int contentHeight(List<Row> rows) {
		return rows.isEmpty() ? 0 : rows.getLast().y() + ROW_H;
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		entries = read();
		Entry sel = selected();
		open.active = sel != null;
		if (giveUpIfUnreadable(entries.isEmpty())) return;
		if (entries.isEmpty()) {
			String loading = "Loading storage…";
			g.text(font, loading, bx() + (BW - font.width(loading)) / 2, by() + BH / 2, Theme.argb(Theme.TEXT_MUTED, a), true);
			return;
		}
		extractList(g, mouseX, mouseY, sel, a);
		extractPage(g, sel, mouseX, mouseY, a);
	}

	private void extractList(GuiGraphicsExtractor g, int mouseX, int mouseY, Entry sel, float a) {
		int x = bx(), y = by();
		List<Row> rows = rows();
		int max = Math.max(0, contentHeight(rows) - BH);
		scrollTarget = Math.clamp(scrollTarget, 0, max);
		scroll.target(scrollTarget);
		int off = Math.round(scroll.get());
		boolean inList = mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + BH;

		g.enableScissor(x, y, x + LIST_W, y + BH);
		for (Row r : rows) {
			int ry = y + r.y() - off;
			if (ry + ROW_H < y || ry > y + BH) continue; // off screen
			if (r.entry() == null) {
				Theme.divider(g, font, r.label(), x, ry + 2, LIST_W - 6, a);
				continue;
			}
			Entry e = r.entry();
			boolean isSel = e == sel || e.id().equals(sel.id());
			boolean hovered = inList && mouseY >= ry && mouseY < ry + ROW_H;
			int color = Theme.color(e.id().kind() == Kind.ENDER_CHEST ? 0 : 4);
			int bg = isSel ? Theme.mix(Theme.ROW, Theme.FRAME, 0.14f) : hovered ? Theme.ROW_HOVER : Theme.ROW;
			Theme.box(g, x, ry, LIST_W - 6, ROW_H, bg, a, isSel ? Theme.FRAME : Theme.LINE, a);
			g.fill(x + 1, ry + 1, x + (isSel ? 4 : 3), ry + ROW_H - 1, Theme.argb(color, a));
			Theme.item(g, e.icon(), x + 7, ry + 3, 16, a);
			StorageCache.Page page = StorageCache.get(e.id());
			String info = page == null ? "not opened" : count(page) + " items";
			Theme.textLeader(g, font, e.id().label(), Theme.TEXT, info, page == null ? Theme.mix(Theme.TEXT_MUTED, Theme.PANEL, 0.3f) : Theme.ACCENT,
					x + 28, ry + 7, LIST_W - 6 - 28 - 7, a);
		}
		g.disableScissor();

		if (max > 0) { // scrollbar
			int trackH = BH, thumbH = Math.max(16, trackH * BH / (BH + max)), thumbY = y + Math.round((trackH - thumbH) * (off / (float) max));
			g.fill(x + LIST_W - 2, y, x + LIST_W, y + trackH, Theme.argb(Theme.SLOT, a));
			g.fill(x + LIST_W - 2, thumbY, x + LIST_W, thumbY + thumbH, Theme.argb(Theme.TEXT_MUTED, a));
		}
	}

	private void extractPage(GuiGraphicsExtractor g, Entry sel, int mouseX, int mouseY, float a) {
		int px = bx() + PANEL_X, y = by();
		StorageCache.Page page = StorageCache.get(sel.id());
		g.text(font, sel.id().label(), px, y + 1, Theme.argb(Theme.TITLE, a), true);
		String seen = page == null ? "Not opened yet" : "Seen " + SkyblockTime.duration((System.currentTimeMillis() - page.seenAt()) / 1000) + " ago";
		Theme.label(g, font, seen, px, y + 11, a);

		int gx = px + (PANEL_W - COLS * SLOT) / 2, gy = y + 28;
		if (page == null) {
			String[] lines = {"Open this page once and", "Clean UI will remember it."};
			for (int i = 0; i < lines.length; i++)
				g.text(font, lines[i], px + (PANEL_W - font.width(lines[i])) / 2, gy + 30 + i * 11, Theme.argb(Theme.TEXT_MUTED, a), true);
			return;
		}
		List<ItemStack> items = page.items();
		int rows = Math.min(6, (items.size() + COLS - 1) / COLS);
		ItemStack hovered = null;
		for (int i = 0; i < Math.min(items.size(), rows * COLS); i++) {
			int sx = gx + (i % COLS) * SLOT, sy = gy + (i / COLS) * SLOT;
			Theme.box(g, sx, sy, SLOT, SLOT, Theme.SLOT, a, Theme.mix(Theme.LINE, Theme.FRAME, 0.25f), a);
			ItemStack s = items.get(i);
			if (s.isEmpty()) continue;
			Theme.item(g, s, sx + 1, sy + 1, 16, a);
			if (s.getCount() > 1 && a > 0.5f) g.itemDecorations(font, s, sx + 1, sy + 1); // stack count
			if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) hovered = s;
		}
		// how full the page is, as an angled bar with the count inside
		int n = count(page);
		Theme.bar(g, font, gx, gy + rows * SLOT + 6, COLS * SLOT, 11, n / (float) Math.max(1, items.size()),
				Theme.mix(Theme.FRAME_DIM, 0x000000, 0.1f), n + " / " + items.size() + " slots", a);
		if (hovered != null) Theme.itemTooltip(g, hovered, mouseX, mouseY);
	}

	private static int count(StorageCache.Page page) {
		int n = 0;
		for (ItemStack s : page.items()) if (!s.isEmpty()) n++;
		return n;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= bx() && mouseX < bx() + LIST_W && mouseY >= by() && mouseY < by() + BH) {
			scrollTarget -= (float) scrollY * ROW_STEP * 2;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		int x = bx(), y = by(), off = Math.round(scroll.get());
		if (event.x() < x || event.x() >= x + LIST_W - 6 || event.y() < y || event.y() >= y + BH) return false;
		for (Row r : rows()) {
			int ry = y + r.y() - off;
			if (r.entry() != null && event.y() >= ry && event.y() < ry + ROW_H) {
				lastSelected = r.entry().id();
				if (doubleClick) openSelected(); // double-click opens the page right away
				return true;
			}
		}
		return false;
	}
}
