package cleanui.feature;

import cleanui.skyblock.SkillParser;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skills page, shown in place of Hypixel's "Your Skills" chest menu. The real menu stays open
 * underneath: this page only reads its items, and each click on a skill is passed on as exactly
 * one normal slot click. If nothing parses (menu changed, or still loading after a while) it
 * hands back the original screen.
 * Layout: the main skills as large cards, the rest as a compact list beside them.
 */
public final class SkillsScreen extends MenuPage {
	public static final String MENU_TITLE = "Your Skills";

	/** Main skills (cards), in display order. */
	private static final List<String> MAIN = List.of("Combat", "Farming", "Fishing", "Mining", "Foraging", "Hunting");
	/** Compact list groups, in display order. Skills not listed anywhere join the first. */
	private static final List<List<String>> MINOR = List.of(
			List.of("Enchanting", "Carpentry", "Taming", "Alchemy"),
			List.of("Social", "Runecrafting"));
	/** Fixed identity color per skill (Theme color index). */
	private static final Map<String, Integer> COLOR = Map.ofEntries(
			Map.entry("Combat", 3), Map.entry("Farming", 5), Map.entry("Fishing", 1), Map.entry("Mining", 4),
			Map.entry("Foraging", 6), Map.entry("Hunting", 2), Map.entry("Enchanting", 0), Map.entry("Carpentry", 4),
			Map.entry("Taming", 2), Map.entry("Alchemy", 6), Map.entry("Social", 1), Map.entry("Runecrafting", 0));
	private static final String DUNGEON = "Dungeoneering";
	// Skill average leaves these out, as Hypixel does.
	private static final List<String> NOT_IN_AVERAGE = List.of("Runecrafting", "Social");

	private static final int TOP = 26; // below the summary bar
	private static final int MAIN_W = 280, CARD_GAP = 8, CARD_H = 50;
	private static final int LIST_X = MAIN_W + 10, LIST_W = BW - LIST_X, ROW_H = 21, ROW_STEP = 24, GROUP_GAP = 10;
	private static final Logger LOGGER = LoggerFactory.getLogger("cleanui");

	private record Entry(int menuSlot, ItemStack stack, SkillParser.Skill skill) {}

	/** Where one skill is drawn this frame; also used for clicks. */
	private record Cell(Entry entry, int x, int y, int w, int h, boolean card) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final Map<Integer, Anim> hover = new HashMap<>(), bars = new HashMap<>();
	private final Set<String> logged = new HashSet<>();
	private List<Entry> entries = List.of();
	private int dungeonSlot = -1;
	private Button dungeons;

	public SkillsScreen(AbstractContainerScreen<?> original) {
		super("Skills", new ItemStack(Items.DIAMOND_SWORD), 1, original);
	}

	@Override
	protected String hint() {
		return "Click a skill to see its rewards.";
	}

	@Override
	protected void initBody() {
		int right = bx() + BW;
		addRenderableWidget(originalMenuButton(right - 86, by()));
		dungeons = addRenderableWidget(new Button(right - 86 - 6 - 92, by(), 92, 18, Component.literal("Dungeons"), this::openDungeons)
				.item(new ItemStack(Items.SKELETON_SKULL)).color(Theme.color(3))
				.tooltip(Component.literal("Your Catacombs and class levels.")).body());
	}

	private void openDungeons() {
		if (dungeonSlot >= 0) click(dungeonSlot, 0);
	}

	/** Keeps the menu items that parse as skills; notes where the Dungeoneering link is. */
	private List<Entry> read() {
		List<Entry> list = new ArrayList<>();
		dungeonSlot = -1;
		for (MenuItem item : menuItems()) {
			String name = item.name();
			if (name.equals(DUNGEON)) {
				dungeonSlot = item.slot(); // no level in this menu's lore; it's just a link to the dungeon page
				continue;
			}
			List<String> lines = item.lore();
			SkillParser.Skill skill = SkillParser.parse(name, lines);
			if (skill != null) list.add(new Entry(item.slot(), item.stack(), skill));
			else if (COLOR.containsKey(name.split(" ")[0]) && logged.add(name))
				// a known skill that didn't parse: log its text once so the parser can be fixed
				LOGGER.warn("[Clean UI] Skipped skill item '{}' with lore {}", name, lines);
		}
		return list;
	}

	private List<Cell> layout() {
		int x = bx(), y = by() + TOP;
		List<Cell> cells = new ArrayList<>();
		int cardW = (MAIN_W - CARD_GAP) / 2;
		List<List<Entry>> minor = new ArrayList<>();
		for (int i = 0; i < MINOR.size(); i++) minor.add(new ArrayList<>());

		int card = 0;
		for (String name : MAIN)
			for (Entry e : entries)
				if (e.skill().name().equals(name)) {
					cells.add(new Cell(e, x + (card % 2) * (cardW + CARD_GAP), y + (card / 2) * (CARD_H + CARD_GAP), cardW, CARD_H, true));
					card++;
				}
		for (Entry e : entries) {
			String name = e.skill().name();
			if (MAIN.contains(name)) continue;
			int g = 0;
			for (int i = 0; i < MINOR.size(); i++) if (MINOR.get(i).contains(name)) g = i;
			minor.get(g).add(e);
		}
		int ry = y;
		for (int g = 0; g < minor.size(); g++) {
			List<String> order = MINOR.get(g);
			List<Entry> group = minor.get(g);
			group.sort((p, q) -> Integer.compare(rank(order, p), rank(order, q)));
			for (Entry e : group) {
				cells.add(new Cell(e, x + LIST_X, ry, LIST_W, ROW_H, false));
				ry += ROW_STEP;
			}
			if (!group.isEmpty()) ry += GROUP_GAP;
		}
		return cells;
	}

	private static int rank(List<String> order, Entry e) {
		int i = order.indexOf(e.skill().name());
		return i < 0 ? Integer.MAX_VALUE : i; // unknown skills last
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		entries = read();
		dungeons.active = dungeonSlot >= 0;
		if (giveUpIfUnreadable(entries.isEmpty())) return;
		int x = bx(), y = by();

		// summary bar: label + value badge
		String avg = average();
		g.text(font, "Skill Average", x, y + 5, Theme.argb(Theme.TITLE, a), true);
		if (avg != null) Theme.badge(g, font, avg, x + font.width("Skill Average") + 6 + font.width(avg) + 5, y + 4, Theme.ACCENT, a);
		if (entries.isEmpty()) {
			String loading = "Loading skills…";
			g.text(font, loading, x + (BW - font.width(loading)) / 2, y + BH / 2, Theme.argb(Theme.TEXT_MUTED, a), true);
			return;
		}

		Cell hovered = null, previous = null;
		for (Cell c : layout()) {
			// link divider between the two compact-list groups
			if (!c.card() && previous != null && !previous.card() && c.y() - previous.y() > ROW_STEP)
				Theme.divider(g, c.x() + 4, c.y() - GROUP_GAP / 2 - 2, c.w() - 8, a);
			boolean over = c.contains(mouseX, mouseY);
			if (over) hovered = c;
			if (c.card()) extractCard(g, c, over, a);
			else extractRow(g, c, over, a);
			previous = c;
		}
		if (hovered != null) Theme.itemTooltip(g, hovered.entry().stack(), mouseX, mouseY);
	}

	/**
	 * Large card: framed slot, name ···· level badge, progress line, and an angled bar with
	 * the XP written inside (like the drill fuel bar on fancy tooltips).
	 */
	private void extractCard(GuiGraphicsExtractor g, Cell c, boolean over, float a) {
		Entry e = c.entry();
		SkillParser.Skill s = e.skill();
		int x = c.x(), y = c.y(), w = c.w(), color = colorOf(s);
		float h = hoverOf(e, over), p = barOf(e);

		Theme.box(g, x, y, w, c.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, color, 0.7f * h), a);
		g.fill(x + 1, y + 1, x + 3 + Math.round(2 * h), y + c.h() - 1, Theme.argb(color, a)); // stripe
		Theme.slot(g, e.stack(), x + 8, y + 6, 1f + 0.12f * h, a);

		int tx = x + 32, right = x + w - 8;
		int bw = Theme.badge(g, font, String.valueOf(s.level()), right, y + 6, s.maxed() ? Theme.ACCENT : color, a);
		if (s.maxed()) {
			Theme.shimmer(g, font, s.name(), tx, y + 7, a);
			Theme.shimmer(g, font, "Max level", tx, y + 19, a);
		} else {
			g.text(font, s.name(), tx, y + 7, Theme.argb(color, a), true);
			Theme.leader(g, tx + font.width(s.name()) + 3, right - bw - 3, y + 13, a);
			Theme.textLeader(g, font, "Progress", Theme.TEXT_MUTED, String.format("%.1f%%", s.progress() * 100), Theme.TEXT,
					tx, y + 19, right - tx, a);
		}
		String xp = s.maxed() ? "MAXED" : s.current() == null ? null : SkillParser.compact(s.current()) + " / " + SkillParser.compact(s.needed());
		Theme.bar(g, font, x + 8, y + c.h() - 14, w - 16, 11, p, s.maxed() ? Theme.mix(Theme.ACCENT, 0x000000, 0.2f) : Theme.mix(color, 0x000000, 0.25f), xp, a);
	}

	/** Compact row: item, name ···· level badge, thin bar along the bottom. */
	private void extractRow(GuiGraphicsExtractor g, Cell c, boolean over, float a) {
		Entry e = c.entry();
		SkillParser.Skill s = e.skill();
		int x = c.x(), y = c.y(), w = c.w(), color = colorOf(s);
		float h = hoverOf(e, over), p = barOf(e);

		Theme.box(g, x, y, w, c.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, color, 0.7f * h), a);
		g.fill(x + 1, y + 1, x + 3 + Math.round(2 * h), y + c.h() - 1, Theme.argb(color, a)); // stripe
		Theme.item(g, e.stack(), x + 7, y + 3, Math.round(13 + 2 * h), a);

		int tx = x + 25, right = x + w - 6;
		int bw = Theme.badge(g, font, String.valueOf(s.level()), right, y + 4, s.maxed() ? Theme.ACCENT : color, a);
		if (s.maxed()) Theme.shimmer(g, font, s.name(), tx, y + 5, a);
		else {
			g.text(font, s.name(), tx, y + 5, Theme.argb(Theme.TEXT, a), true);
			Theme.leader(g, tx + font.width(s.name()) + 3, right - bw - 3, y + 11, a);
		}
		int tw = right - tx;
		g.fill(tx, y + c.h() - 4, tx + tw, y + c.h() - 2, Theme.argb(Theme.SLOT, a));
		g.fill(tx, y + c.h() - 4, tx + Math.round(tw * p), y + c.h() - 2, Theme.argb(s.maxed() ? Theme.ACCENT : color, a));
	}

	private static int colorOf(SkillParser.Skill s) {
		return Theme.color(COLOR.getOrDefault(s.name(), 6));
	}

	private float hoverOf(Entry e, boolean over) {
		Anim hv = hover.computeIfAbsent(e.menuSlot(), k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		return hv.get();
	}

	/** Bar fill; animates in from 0 when the page opens. */
	private float barOf(Entry e) {
		Anim bar = bars.computeIfAbsent(e.menuSlot(), k -> new Anim(0f, 5f));
		bar.target(e.skill().progress());
		return bar.get();
	}

	private String average() {
		int sum = 0, n = 0;
		for (Entry e : entries) {
			if (NOT_IN_AVERAGE.contains(e.skill().name())) continue;
			sum += e.skill().level();
			n++;
		}
		return n == 0 ? null : String.format("%.1f", sum / (float) n);
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		for (Cell c : layout())
			if (c.contains(event.x(), event.y())) {
				click(c.entry().menuSlot(), event.button());
				return true;
			}
		return false;
	}
}
