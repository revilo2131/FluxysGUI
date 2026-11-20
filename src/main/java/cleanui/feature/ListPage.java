package cleanui.feature;

import cleanui.data.Config;
import cleanui.skyblock.CollectionItems;
import cleanui.skyblock.CollectionParser;
import cleanui.skyblock.SkillParser;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Hypixel menu that is a list of things with progress (Collections, a collection category,
 * Bestiary, a Bestiary island, Pets), shown as tiles with tier/level badge, progress and a bar.
 * Everything comes from the menu's own items via the loose {@link CollectionParser}; clicks,
 * paging and Back go to the real menu.
 */
public final class ListPage extends MenuPage {
	public enum Mode {
		COLLECTIONS(Items.PAINTING, 1, null, 3, "categories", "Pick a category.", false),
		COLLECTION_CATEGORY(Items.PAINTING, 2, "Collections", 4, "collections", "Hover a collection for its rewards.", true),
		BESTIARY(Items.WRITABLE_BOOK, 1, null, 3, "islands", "Pick an island.", false),
		BESTIARY_PAGE(Items.WRITABLE_BOOK, 2, "Bestiary", 4, "mobs", "Hover a mob for its milestones.", false),
		PETS(Items.BONE, 1, null, 4, "pets", "Click a pet to summon it.", false),
		/** Fallback for a level menu that turned out not to have tiers. */
		GENERIC(Items.BOOK, 2, null, 4, "items", "Hover for details.", false);

		final Item icon;
		final int depth, cols;
		final String crumb, noun, hint;
		/** Show the real item for entries not found yet (collections only). */
		final boolean realItems;

		Mode(Item icon, int depth, String crumb, int cols, String noun, String hint, boolean realItems) {
			this.icon = icon;
			this.depth = depth;
			this.crumb = crumb;
			this.cols = cols;
			this.noun = noun;
			this.hint = hint;
			this.realItems = realItems;
		}
	}

	private static final Pattern COLLECTIONS_TITLE = Pattern.compile("^Collections?$");
	private static final Pattern CATEGORY_TITLE = Pattern.compile("^(.+) Collections$");
	private static final Pattern BESTIARY_TITLE = Pattern.compile("^Bestiary(?:\\s*[➜>\\-:]\\s*(.+))?$");
	private static final Pattern PETS_TITLE = Pattern.compile("^Pets(?: \\(\\d+/\\d+\\))?$");
	private static final int TOP = 24, GAP = 5, ROW_GAP = 4;
	private static final Map<String, ItemStack> ITEM_CACHE = new HashMap<>();

	private record Tile(MenuItem item, CollectionParser.Entry entry, int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final Mode mode;
	private final Map<Integer, Anim> hover = new HashMap<>(), bars = new HashMap<>();
	private Button prev, next;
	private int prevSlot = -1, nextSlot = -1;

	public ListPage(String title, Mode mode, AbstractContainerScreen<?> original) {
		super(title, new ItemStack(mode.icon), mode.depth, original);
		this.mode = mode;
	}

	/** The right list page for a menu title (respecting settings), or null. */
	public static ListPage forTitle(String title, AbstractContainerScreen<?> original) {
		Config c = Config.get();
		Matcher m;
		if (c.collections && COLLECTIONS_TITLE.matcher(title).matches()) return new ListPage("Collections", Mode.COLLECTIONS, original);
		if (c.collections && (m = CATEGORY_TITLE.matcher(title)).matches()) return new ListPage(m.group(1), Mode.COLLECTION_CATEGORY, original);
		if (c.bestiary && (m = BESTIARY_TITLE.matcher(title)).matches())
			return m.group(1) == null ? new ListPage("Bestiary", Mode.BESTIARY, original) : new ListPage(m.group(1).strip(), Mode.BESTIARY_PAGE, original);
		if (c.pets && PETS_TITLE.matcher(title).matches()) return new ListPage("Pets", Mode.PETS, original);
		return null;
	}

	@Override
	protected String crumb() {
		return mode.crumb != null ? mode.crumb : super.crumb();
	}

	@Override
	protected String hint() {
		return mode.hint;
	}

	@Override
	protected void initBody() {
		int right = bx() + BW;
		addRenderableWidget(originalMenuButton(right - 86, by()));
		next = addRenderableWidget(new Button(right - 86 - 6 - 22, by(), 22, 18, Component.literal("›"), () -> click(nextSlot, 0))
				.tooltip(Component.literal("Next page")).body());
		prev = addRenderableWidget(new Button(right - 86 - 6 - 22 - 4 - 22, by(), 22, 18, Component.literal("‹"), () -> click(prevSlot, 0))
				.tooltip(Component.literal("Previous page")).body());
	}

	private List<Tile> tiles() {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		List<MenuItem> content = new ArrayList<>();
		prevSlot = nextSlot = -1;
		for (MenuItem item : items) {
			String name = item.name();
			if (name.equals("Previous Page")) prevSlot = item.slot();
			else if (name.equals("Next Page")) nextSlot = item.slot();
			else if (!name.isEmpty() && !NAV.contains(name)) content.add(item);
		}
		int n = content.size(), cols = mode.cols, rows = Math.max(1, (n + cols - 1) / cols);
		int w = (BW - (cols - 1) * GAP) / cols;
		int h = Math.min(cols == 3 ? 44 : 40, (BH - TOP - (rows - 1) * ROW_GAP) / rows);
		List<Tile> tiles = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			MenuItem item = content.get(i);
			tiles.add(new Tile(item, CollectionParser.parse(item.name(), item.lore()),
					bx() + (i % cols) * (w + GAP), by() + TOP + (i / cols) * (h + ROW_GAP), w, h));
		}
		return tiles;
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<Tile> tiles = tiles();
		prev.active = prevSlot >= 0;
		next.active = nextSlot >= 0;
		prev.visible = next.visible = prevSlot >= 0 || nextSlot >= 0;
		if (giveUpIfUnreadable(tiles.isEmpty())) return;

		// summary: how many, how many maxed
		int maxed = 0;
		for (Tile t : tiles) if (t.entry().maxed()) maxed++;
		String summary = tiles.size() + " " + mode.noun;
		g.text(font, summary, bx(), by() + 5, Theme.argb(Theme.TITLE, a), true);
		if (mode.depth == 2 && maxed > 0)
			Theme.badge(g, font, maxed + " maxed", bx() + font.width(summary) + 6 + font.width(maxed + " maxed") + 5, by() + 4, Theme.ACCENT, a);

		Tile hovered = null;
		for (Tile t : tiles) {
			boolean over = t.contains(mouseX, mouseY);
			if (over) hovered = t;
			extractTile(g, t, over, a);
		}
		if (hovered != null) Theme.itemTooltip(g, hovered.item().stack(), mouseX, mouseY);
	}

	/** Tall tiles get a framed slot and a detail line; short ones fit everything on one line. */
	private void extractTile(GuiGraphicsExtractor g, Tile t, boolean over, float a) {
		CollectionParser.Entry e = t.entry();
		Anim hv = hover.computeIfAbsent(t.item().slot(), k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		float h = hv.get();
		Anim bar = bars.computeIfAbsent(t.item().slot(), k -> new Anim(0f, 5f)); // fills in from 0 on open
		bar.target(e.maxed() ? 1f : e.percent() == null ? 0f : e.percent());
		float p = bar.get();
		int x = t.x(), y = t.y(), w = t.w(), th = t.h();
		boolean locked = e.locked() && mode.depth == 2;
		int barColor = e.maxed() ? Theme.ACCENT : Theme.mix(Theme.FRAME_DIM, Theme.FRAME, 0.3f);

		Theme.box(g, x, y, w, th, Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a, Theme.mix(Theme.LINE, Theme.FRAME, 0.8f * h), a);
		boolean tall = th >= 34;
		int iconX = x + 6, iconY = tall ? y + 6 : y + (th - 14) / 2;
		// not found yet: Hypixel shows a placeholder; show the collection's real item instead
		ItemStack icon = locked && mode.realItems
				? ITEM_CACHE.computeIfAbsent(e.name(), n -> realItem(n, t.item().stack())) : t.item().stack();
		if (tall) Theme.slot(g, icon, iconX, iconY, 1f + 0.12f * h, a);
		else Theme.item(g, icon, iconX, iconY, Math.round(14 + 2 * h), a);

		int tx = x + (tall ? 30 : 24), right = x + w - 6;
		String name = e.name().replaceFirst(" Collections?$", "");
		String tier = e.tier() < 0 ? null : e.arabic() ? String.valueOf(e.tier()) : CollectionParser.roman(e.tier());
		int tierColor = e.maxed() ? Theme.ACCENT : Theme.FRAME;
		int nameY = tall ? y + 7 : y + (th - 8) / 2 - 1;
		// tall tiles: the name gets the whole first line, the tier badge sits on the second
		int bw = !tall && tier != null ? Theme.badge(g, font, tier, right, nameY - 1, tierColor, a) : 0;
		String shown = Theme.fit(font, name, right - tx - (bw > 0 ? bw + 4 : 0));
		if (e.maxed()) Theme.shimmer(g, font, shown, tx, nameY, a);
		else g.text(font, shown, tx, nameY, Theme.argb(locked ? Theme.TEXT_MUTED : tall ? Theme.TITLE : Theme.TEXT, a), true);

		if (tall) {
			int y2 = y + 19;
			String value = e.collected() != null ? SkillParser.compact(e.collected())
					: e.done() != null ? e.done() + "/" + e.total()
					: e.percent() != null ? Math.round(e.percent() * 100) + "%" : null;
			if (locked) g.text(font, "Not found", tx, y2, Theme.argb(Theme.TEXT_MUTED, a), true);
			else if (tier != null) { // "2.5M ······ [IX]"
				int bw2 = Theme.badge(g, font, tier, right, y2 - 1, tierColor, a);
				if (value != null) {
					g.text(font, value, tx, y2, Theme.argb(Theme.ACCENT, a), true);
					Theme.leader(g, tx + font.width(value) + 3, right - bw2 - 3, y2 + 6, a);
				}
			} else if (value != null) { // "Unlocked ······ 12/15"
				String label = e.amountLabel() != null ? e.amountLabel() : "Progress";
				Theme.textLeader(g, font, label, Theme.TEXT_MUTED, value, Theme.ACCENT, tx, y2, right - tx, a);
			}
			if (!locked) Theme.bar(g, font, x + 6, y + th - 9, w - 12, 5, p, barColor, null, a);
		} else if (!locked) {
			g.fill(tx, y + th - 4, right, y + th - 2, Theme.argb(Theme.SLOT, a));
			g.fill(tx, y + th - 4, tx + Math.round((right - tx) * p), y + th - 2, Theme.argb(barColor, a));
		}
	}

	/** The vanilla item a collection is named after, or {@code fallback} if there isn't one. */
	private static ItemStack realItem(String name, ItemStack fallback) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(CollectionItems.idFor(name)))
				.map(ItemStack::new).orElse(fallback);
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		for (Tile t : tiles())
			if (t.contains(event.x(), event.y())) {
				click(t.item().slot(), event.button());
				return true;
			}
		return false;
	}
}
