package cleanui.feature;

import cleanui.skyblock.RngParser;
import cleanui.skyblock.SkillParser;
import cleanui.ui.Anim;
import cleanui.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Any "... RNG Meter" menu (Crystal Nucleus, Slayers, Catacombs): a card with the selected drop and
 * its meter, every drop in three columns with its rarity, chance and how full the meter is for it,
 * and the menu's other buttons (Search, Reset) along the bottom. Clicks go to the real menu.
 */
public final class RngMeterScreen extends MenuPage {
	private static final int CARD_H = 44, LIST_Y = CARD_H + 6, COLS = 3, COL_GAP = 4, ROW_H = 19, ROW_STEP = 21;
	private static final int BUTTON_W = 96, BUTTON_H = 18;

	private record Box(MenuItem item, RngParser.Drop drop, int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final String parent;
	private final Map<Integer, Anim> hover = new HashMap<>();
	private final Anim meter = new Anim(0f, 5f);

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
		return "Click a drop to select it.";
	}

	@Override
	protected void initBody() {
		addRenderableWidget(originalMenuButton(bx() + BW - 86, by() + BH - 18));
	}

	/** Header item (the meter), drops in three columns, then the other buttons along the bottom. */
	private List<Box> boxes(List<MenuItem> items) {
		List<Box> boxes = new ArrayList<>();
		int colW = (BW - (COLS - 1) * COL_GAP) / COLS, i = 0, b = 0;
		for (MenuItem item : items) {
			String name = item.name();
			if (name.isEmpty() || NAV.contains(name) || name.equals(getTitle().getString())) continue;
			RngParser.Drop d = RngParser.drop(item.lore());
			if (d != null) {
				boxes.add(new Box(item, d, bx() + (i % COLS) * (colW + COL_GAP), by() + LIST_Y + (i / COLS) * ROW_STEP, colW, ROW_H));
				i++;
			} else boxes.add(new Box(item, null, bx() + b++ * (BUTTON_W + COL_GAP), by() + BH - BUTTON_H, BUTTON_W, BUTTON_H));
		}
		return boxes;
	}

	private MenuItem header(List<MenuItem> items) {
		return items.stream().filter(i -> i.name().equals(getTitle().getString())).findFirst().orElse(null);
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		List<Box> boxes = boxes(items);
		if (giveUpIfUnreadable(boxes.isEmpty())) return;
		Box selected = boxes.stream().filter(b -> b.drop() != null && b.drop().selected()).findFirst().orElse(null);
		Box hoveredDrop = boxes.stream().filter(b -> b.drop() != null && b.contains(mouseX, mouseY)).findFirst().orElse(null);
		MenuItem header = header(items);
		// hovering a drop previews it in the card: what the meter would look like if you picked it
		extractCard(g, header, hoveredDrop != null ? hoveredDrop : selected, hoveredDrop != null && hoveredDrop != selected, mouseX, mouseY, a);
		Box hovered = null;
		for (Box b : boxes) {
			boolean over = b.contains(mouseX, mouseY);
			if (over) hovered = b;
			Anim hv = hover.computeIfAbsent(b.item().slot(), k -> new Anim(0f, 18f));
			hv.target(over ? 1f : 0f);
			if (b.drop() != null) extractDrop(g, b, hv.get(), a);
			else extractButton(g, b, hv.get(), a);
		}
		if (hovered != null) Theme.itemTooltip(g, hovered.item().stack(), mouseX, mouseY);
	}

	/** Meter card: the selected (or previewed) drop, its rarity and chance, runs left, and the meter toward it. */
	private void extractCard(GuiGraphicsExtractor g, MenuItem header, Box selected, boolean preview, int mouseX, int mouseY, float a) {
		int x = bx(), y = by(), w = BW;
		boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CARD_H;
		int color = selected != null ? RngParser.color(selected.drop().rarity()) : Theme.FRAME;
		Theme.box(g, x, y, w, CARD_H, over ? Theme.ROW_HOVER : Theme.ROW, a, over ? color : Theme.LINE, a);
		g.fill(x + 1, y + 1, x + 3, y + CARD_H - 1, Theme.argb(color, a));
		ItemStack icon = selected != null ? selected.item().stack() : header != null ? header.stack() : new ItemStack(Items.ENDER_EYE);
		Theme.slot(g, icon, x + 8, y + (CARD_H - 18) / 2, 1f, a);
		int tx = x + 32, right = x + w - 8;
		if (selected == null) {
			g.text(font, "No drop selected", tx, y + 7, Theme.argb(Theme.TITLE, a), true);
			g.text(font, "Click a drop below to aim the meter at it.", tx, y + 20, Theme.argb(Theme.TEXT_MUTED, a), true);
			return;
		}
		RngParser.Drop d = selected.drop();
		String chance = d.rarity() + "  " + d.chance();
		g.text(font, chance, right - font.width(chance), y + 5, Theme.argb(bright(color), a), true);
		int tagW = preview ? Theme.tag(g, font, "PREVIEW", right - font.width(chance) - 8 - font.width("PREVIEW") - 6, y + 4, Theme.FRAME, a) + 6 : 0;
		g.text(font, Theme.fit(font, selected.item().name(), right - tx - font.width(chance) - 8 - tagW), tx, y + 5, Theme.argb(Theme.TITLE, a), true);
		long runs = header != null ? RngParser.runsLeft(d, RngParser.perRun(header.lore())) : -1;
		String status = runs == 0 ? (d.selected() ? "Meter full: guaranteed on your next run!" : "Meter already full: select it to guarantee it next run")
				: runs > 0 ? String.format("%,d runs left", runs) : null;
		if (status != null) g.text(font, status, tx, y + 16, Theme.argb(runs == 0 ? Theme.ACCENT : Theme.TEXT_MUTED, a), true);
		meter.target(d.fill());
		String xp = d.done() != null ? SkillParser.compact(d.done()) + " / " + SkillParser.compact(d.total()) + " XP" : null;
		Theme.bar(g, font, tx, y + CARD_H - 14, right - tx, 10, meter.get(), d.fill() >= 1f ? Theme.ACCENT : Theme.mix(color, 0x000000, 0.2f), xp, a);
		if (over && header != null) Theme.itemTooltip(g, header.stack(), mouseX, mouseY);
	}

	/** Drop row: rarity stripe, item, name ···· chance, meter fill along the bottom. */
	private void extractDrop(GuiGraphicsExtractor g, Box b, float h, float a) {
		RngParser.Drop d = b.drop();
		int color = RngParser.color(d.rarity()), x = b.x(), y = b.y(), w = b.w();
		Theme.box(g, x, y, w, b.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a,
				d.selected() ? Theme.FRAME : Theme.mix(Theme.LINE, color, 0.7f * h), a);
		g.fill(x + 1, y + 1, x + 3 + Math.round(2 * h), y + b.h() - 1, Theme.argb(color, a));
		Theme.item(g, b.item().stack(), x + 6, y + 2, Math.round(13 + 2 * h), a);
		int tx = x + 23, right = x + w - 5;
		g.text(font, d.chance(), right - font.width(d.chance()), y + 4, Theme.argb(bright(color), a), true);
		String name = Theme.fit(font, b.item().name(), right - tx - font.width(d.chance()) - 5);
		g.text(font, name, tx, y + 4, Theme.argb(d.selected() ? Theme.TITLE : Theme.TEXT, a), true);
		g.fill(tx, y + b.h() - 4, right, y + b.h() - 2, Theme.argb(Theme.SLOT, a));
		g.fill(tx, y + b.h() - 4, tx + Math.round((right - tx) * d.fill()), y + b.h() - 2, Theme.argb(d.fill() >= 1f ? Theme.ACCENT : color, a));
	}

	/** Rarity colour lifted a bit so dark ones (Extraordinary purple) read on the navy rows. */
	private static int bright(int color) {
		return Theme.mix(color, 0xFFFFFF, 0.25f);
	}

	/** Search / Reset: item and name, like a footer button. */
	private void extractButton(GuiGraphicsExtractor g, Box b, float h, float a) {
		Theme.box(g, b.x(), b.y(), b.w(), b.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, Theme.FRAME, 0.8f * h), a);
		Theme.item(g, b.item().stack(), b.x() + 4, b.y() + 3, 12, a);
		g.text(font, Theme.fit(font, b.item().name(), b.w() - 24), b.x() + 20, b.y() + 5, Theme.argb(Theme.TEXT, a), true);
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		for (Box b : boxes(menuItems()))
			if (b.contains(event.x(), event.y())) {
				clickAs(b.item().slot(), event);
				return true;
			}
		return false;
	}
}
