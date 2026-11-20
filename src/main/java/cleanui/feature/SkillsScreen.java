package cleanui.feature;

import cleanui.skyblock.SkillParser;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.CleanScreen;
import cleanui.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drawn in place of Hypixel's "Your Skills" chest menu. The real menu stays open underneath:
 * this screen only reads its items, and each click on a skill is passed on as exactly one
 * normal slot click. If nothing parses (menu changed, still loading too long) it hands back
 * the original screen.
 */
public final class SkillsScreen extends CleanScreen {
	public static final String MENU_TITLE = "Your Skills";

	/** Display groups, in order. Skills not listed go into the second group. */
	private static final List<List<String>> GROUPS = List.of(
			List.of("Combat", "Farming", "Fishing", "Mining", "Foraging", "Hunting"),
			List.of("Enchanting", "Carpentry", "Taming", "Alchemy"),
			List.of("Social", "Runecrafting"));
	private static final List<String> DUNGEON = List.of("Dungeoneering", "Catacombs");
	// Skill average leaves these out, as Hypixel does.
	private static final List<String> NOT_IN_AVERAGE = List.of("Runecrafting", "Social");

	private static final int W = 420, COLS = 3, COL_GAP = 10, ROW_H = 28, ROW_GAP = 6, GROUP_GAP = 14;
	private static final int HEADER = 36, FOOTER = 30, DUNGEON_W = 170, DUNGEON_H = 22;
	// room for 2 + 2 + 1 rows (the three groups) at most
	private static final int H = HEADER + 5 * ROW_H + 2 * ROW_GAP + 2 * GROUP_GAP + Theme.PAD + FOOTER;
	private static final long FALLBACK_MS = 2500;
	private static final Logger LOGGER = LoggerFactory.getLogger("cleanui");

	/** The vanilla screen the user asked to see; the open hook leaves this one alone. */
	public static Screen showingOriginal;

	private record Entry(int menuSlot, ItemStack stack, SkillParser.Skill skill) {}

	/** Where one entry is drawn this frame; also used for clicks. */
	private record Cell(Entry entry, int x, int y, int w, int h, int color) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final AbstractContainerScreen<?> original;
	private final Map<Integer, Anim> hover = new HashMap<>(), bars = new HashMap<>();
	private final Set<String> logged = new HashSet<>();
	private final long openedAt = System.currentTimeMillis();
	private List<Entry> entries = List.of();

	public SkillsScreen(AbstractContainerScreen<?> original) {
		super(Component.literal("Skills"), null);
		this.original = original;
	}

	@Override
	protected void init() {
		int x = left() + W - Theme.PAD - 92, y = top() + H - FOOTER + (FOOTER - 16) / 2;
		addRenderableWidget(new Button(x, y, 92, 16, Component.literal("Original menu"), this::showOriginal)
				.tooltip(Component.literal("Show Hypixel's own menu instead.")));
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return (height - H) / 2;
	}

	private void showOriginal() {
		showingOriginal = original;
		minecraft.setScreenAndShow(original);
	}

	/** Reads every container slot (not the player inventory) and keeps the ones that parse as skills. */
	private List<Entry> read() {
		AbstractContainerMenu menu = original.getMenu();
		List<Entry> list = new ArrayList<>();
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			if (slot.container == minecraft.player.getInventory()) continue;
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			ItemLore lore = stack.get(DataComponents.LORE);
			List<String> lines = new ArrayList<>();
			if (lore != null) for (Component c : lore.lines()) lines.add(ChatFormatting.stripFormatting(c.getString()));
			String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
			SkillParser.Skill skill = SkillParser.parse(name, lines);
			if (skill != null) list.add(new Entry(i, stack, skill));
			else if (SkillParser.NAME.matcher(name.strip()).matches() && logged.add(name))
				// looks like a skill but didn't parse: log its text once so the parser can be fixed
				LOGGER.warn("[Clean UI] Skipped skill-like item '{}' with lore {}", name, lines);
		}
		return list;
	}

	/** Groups → rows of {@link #COLS}; Dungeoneering goes to its own footer button. */
	private List<Cell> layout() {
		List<List<Entry>> groups = new ArrayList<>();
		for (int i = 0; i < GROUPS.size(); i++) groups.add(new ArrayList<>());
		List<Cell> cells = new ArrayList<>();
		int px = left(), py = top();

		for (Entry e : entries) {
			String name = e.skill().name();
			if (DUNGEON.contains(name)) {
				cells.add(new Cell(e, px + Theme.PAD, py + H - FOOTER + (FOOTER - DUNGEON_H) / 2, DUNGEON_W, DUNGEON_H, Theme.color(3)));
				continue;
			}
			int g = 1;
			for (int i = 0; i < GROUPS.size(); i++) if (GROUPS.get(i).contains(name)) g = i;
			groups.get(g).add(e);
		}

		int colW = (W - Theme.PAD * 2 - COL_GAP * (COLS - 1)) / COLS, y = py + HEADER, color = 0;
		for (int g = 0; g < groups.size(); g++) {
			List<Entry> group = groups.get(g);
			if (group.isEmpty()) continue;
			List<String> order = GROUPS.get(g);
			group.sort(Comparator.comparingInt(e -> {
				int i = order.indexOf(e.skill().name());
				return i < 0 ? Integer.MAX_VALUE : i; // unknown skills last
			}));
			for (int i = 0; i < group.size(); i++)
				cells.add(new Cell(group.get(i), px + Theme.PAD + (i % COLS) * (colW + COL_GAP),
						y + (i / COLS) * (ROW_H + ROW_GAP), colW, ROW_H, Theme.color(color++)));
			int rows = (group.size() + COLS - 1) / COLS;
			y += rows * ROW_H + (rows - 1) * ROW_GAP + GROUP_GAP;
		}
		return cells;
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		entries = read();
		if (entries.isEmpty() && System.currentTimeMillis() - openedAt > FALLBACK_MS) {
			showOriginal(); // couldn't read the menu: never leave the player stuck
			return;
		}

		int px = left(), py = top(), x = px + Theme.PAD;
		Theme.panel(g, px, py, W, H, a);
		Theme.title(g, font, title.getString(), x, py + Theme.PAD, a);
		g.text(font, "Click a skill for its rewards.", x, py + Theme.PAD + 17, Theme.argb(Theme.TEXT_MUTED, a), true);

		String avg = average();
		if (avg != null) {
			String label = "Skill Average ";
			int ax = px + W - Theme.PAD - font.width(label + avg);
			g.text(font, label, ax, py + Theme.PAD, Theme.argb(Theme.TEXT_MUTED, a), true);
			g.text(font, avg, ax + font.width(label), py + Theme.PAD, Theme.argb(Theme.ACCENT, a), true);
		}

		if (entries.isEmpty())
			g.text(font, "Loading skills…", x, py + HEADER + 4, Theme.argb(Theme.TEXT_MUTED, a), true);

		Cell previous = null;
		for (Cell c : layout()) {
			// faint line in the gap where a new group starts
			if (previous != null && c.h() == ROW_H && c.y() > previous.y() + ROW_H + ROW_GAP)
				g.fill(x, c.y() - GROUP_GAP / 2 - 1, px + W - Theme.PAD, c.y() - GROUP_GAP / 2, Theme.argb(Theme.LINE, a * 0.7f));
			extractCell(g, c, mouseX, mouseY, a);
			if (c.h() == ROW_H) previous = c;
		}

		g.fill(px + 1, py + H - FOOTER, px + W - 1, py + H - FOOTER + 1, Theme.argb(Theme.LINE, a)); // footer separator
	}

	private void extractCell(GuiGraphicsExtractor g, Cell c, int mouseX, int mouseY, float a) {
		Entry e = c.entry();
		int x = c.x(), y = c.y(), w = c.w(), h = c.h(), color = c.color();
		boolean hovered = c.contains(mouseX, mouseY);
		Anim hv = hover.computeIfAbsent(e.menuSlot(), k -> new Anim(0f, 18f));
		hv.target(hovered ? 1f : 0f);
		float t = hv.get();
		Anim bar = bars.computeIfAbsent(e.menuSlot(), k -> new Anim(0f, 5f)); // fills in from 0 on open
		bar.target(e.skill().progress());

		Theme.box(g, x, y, w, h, Theme.mix(Theme.ROW, Theme.ROW_HOVER, t), a, Theme.mix(Theme.LINE, color, 0.5f * t), a);
		g.fill(x + 1, y + 1, x + 4 + Math.round(2 * t), y + h - 1, Theme.argb(color, a)); // stripe
		Theme.slot(g, e.stack(), x + 8, y + (h - 18) / 2, 1f + 0.12f * t, a);

		SkillParser.Skill s = e.skill();
		int tx = x + 31, tw = w - 31 - 6, ty = y + (h - 18) / 2 + 1;
		String label = s.name() + " " + s.level();
		if (s.maxed()) {
			Theme.shimmer(g, font, label, tx, ty, a);
			Theme.shimmer(g, font, "MAX", tx + tw - font.width("MAX"), ty, a);
		} else {
			g.text(font, label, tx, ty, Theme.argb(Theme.TEXT, a), true);
			String pct = String.format("%.1f%%", s.progress() * 100);
			g.text(font, pct, tx + tw - font.width(pct), ty, Theme.argb(color, a), true);
		}

		int by = ty + 11;
		Theme.box(g, tx, by, tw, 5, Theme.SLOT, a);
		g.fill(tx + 1, by + 1, tx + 1 + Math.round((tw - 2) * bar.get()), by + 4, Theme.argb(s.maxed() ? Theme.ACCENT : color, a));

		if (hovered) g.setTooltipForNextFrame(font, e.stack(), mouseX, mouseY); // Hypixel's own lore
	}

	private String average() {
		int sum = 0, n = 0;
		for (Entry e : entries) {
			String name = e.skill().name();
			if (NOT_IN_AVERAGE.contains(name) || DUNGEON.contains(name)) continue;
			sum += e.skill().level();
			n++;
		}
		return n == 0 ? null : String.format("%.1f", sum / (float) n);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		for (Cell c : layout()) {
			if (c.contains(event.x(), event.y())) {
				// exactly one normal click on that slot, same as clicking it in the chest
				minecraft.gameMode.handleContainerInput(original.getMenu().containerId, c.entry().menuSlot(),
						event.button(), ContainerInput.PICKUP, minecraft.player);
				return true;
			}
		}
		return false;
	}

	@Override
	protected void closed() {
		minecraft.player.closeContainer(); // closes the real menu on the server too
	}
}
