package cleanui.feature;

import cleanui.data.Config;
import cleanui.skyblock.CollectionParser;
import cleanui.skyblock.PerkParser;
import cleanui.skyblock.TrackParser;
import cleanui.skyblock.PerkParser.State;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heart of the Mountain / Forest, shown in place of Hypixel's tree menu. Every perk keeps its
 * spot in the real menu's grid; neighbours are joined by lines (gold along the unlocked path),
 * the "Tier N" items become row labels, and nodes show their level and state. Clicks (and
 * shift-clicks, which Hypixel uses for +10 levels) go to the real menu 1:1.
 */
public final class TreeScreen extends MenuPage {
	private static final Pattern TITLE = Pattern.compile("^Heart of the (Mountain|Forest)$");
	private static final int TIERS = 10;
	/** Maxed perks: yellow. */
	private static final int MAXED = 0xFFE04A;
	private static final int TOP = 24, LABEL_W = 46, COL_STEP = 38, ROW_STEP = 29, TILE_W = 32, TILE_H = 25;
	private static final List<String> UP = List.of("Scroll Up", "Previous Page"), DOWN = List.of("Scroll Down", "Next Page");

	private record Node(MenuItem item, PerkParser.Perk perk, int col, int row, int x, int y) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + TILE_W && my >= y && my < y + TILE_H;
		}
	}

	private final Map<Integer, Anim> hover = new HashMap<>();
	private Button up, down;
	private int upSlot = -1, downSlot = -1, tokens = -1;
	/** Powder amounts read from the menu this frame (name → amount), and counters that ease toward them. */
	private final Map<String, Long> powder = new HashMap<>();
	private final Map<String, Anim> powderShown = new HashMap<>();
	private final List<int[]> tierLabels = new ArrayList<>(); // {row, tier, fill 0..1000}

	private TreeScreen(String title, AbstractContainerScreen<?> original) {
		super(title, new ItemStack(Items.AMETHYST_CLUSTER), 1, original);
	}

	public static TreeScreen forTitle(String title, AbstractContainerScreen<?> original) {
		Matcher m = TITLE.matcher(title);
		return Config.get().hotm && m.matches() ? new TreeScreen(m.group(1).equals("Mountain") ? "Heart of the Mountain" : "Heart of the Forest", original) : null;
	}

	@Override
	protected String hint() {
		return "Click to level a perk; shift-click for +10.";
	}

	@Override
	protected void initBody() {
		int right = bx() + BW;
		addRenderableWidget(originalMenuButton(right - 86, by()));
		down = addRenderableWidget(new Button(right - 86 - 6 - 22, by(), 22, 18, Component.literal("▼"), () -> click(downSlot, 0))
				.tooltip(Component.literal("Scroll down the tree")).body());
		up = addRenderableWidget(new Button(right - 86 - 6 - 22 - 4 - 22, by(), 22, 18, Component.literal("▲"), () -> click(upSlot, 0))
				.tooltip(Component.literal("Scroll up the tree")).body());
	}

	private int cellX(int col) {
		return bx() + (col == 0 ? 0 : LABEL_W + (col - 1) * COL_STEP + (COL_STEP - TILE_W) / 2);
	}

	/** Perks at their menu positions; tier items become labels; scroll arrows become buttons. */
	private List<Node> nodes() {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		List<Node> nodes = new ArrayList<>();
		tierLabels.clear();
		upSlot = downSlot = tokens = -1;
		powder.clear();
		for (MenuItem item : items) {
			String name = item.name();
			if (tokens < 0) tokens = PerkParser.tokens(item.lore());
			PerkParser.powder(item.lore()).forEach(powder::putIfAbsent);
			if (UP.stream().anyMatch(name::startsWith)) { upSlot = item.slot(); continue; }
			if (DOWN.stream().anyMatch(name::startsWith)) { downSlot = item.slot(); continue; }
			if (name.isEmpty() || NAV.contains(name)) continue;
			if (item.slot() % 9 == 8) continue; // right column: info items; the powder panel sits there
			int col = item.slot() % 9, row = item.slot() / 9, tier = PerkParser.tier(name);
			if (col == 0 && tier >= 0) {
				String tid = BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString();
				tierLabels.add(new int[]{row, tier, tierFill(tid, item)});
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString();
			nodes.add(new Node(item, PerkParser.perk(id, item.lore()), col, row, cellX(col), by() + TOP + row * ROW_STEP));
		}
		return nodes;
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<Node> nodes = nodes();
		up.active = upSlot >= 0;
		down.active = downSlot >= 0;
		up.visible = down.visible = upSlot >= 0 || downSlot >= 0;
		if (giveUpIfUnreadable(nodes.isEmpty())) return;
		int x = bx(), y = by();

		// summary: unlocked / maxed counts and tokens
		int unlocked = 0, maxed = 0;
		for (Node n : nodes) if (n.perk().state() != State.LOCKED) { unlocked++; if (n.perk().state() == State.MAXED) maxed++; }
		String summary = unlocked + " perks unlocked";
		g.text(font, summary, x, y + 5, Theme.argb(Theme.TITLE, a), true);
		int bx = x + font.width(summary) + 6;
		if (maxed > 0) bx += Theme.badge(g, font, maxed + " maxed", bx + font.width(maxed + " maxed") + 5, y + 4, MAXED, a) + 4;
		if (tokens >= 0) Theme.badge(g, font, tokens + " tokens", bx + font.width(tokens + " tokens") + 5, y + 4, Theme.FRAME, a);

		// row labels
		extractXpBar(g, x, a);
		for (int[] t : tierLabels)
			g.text(font, "Tier " + t[1], x + 8, by() + TOP + t[0] * ROW_STEP + (TILE_H - 8) / 2,
					Theme.argb(t[2] >= 1000 ? Theme.TEXT : t[2] > 0 ? Theme.color(5) : Theme.TEXT_MUTED, a), true);

		// connectors between neighbouring perks, gold where both ends are unlocked
		Map<Integer, Node> bySlot = new HashMap<>();
		for (Node n : nodes) bySlot.put(n.item().slot(), n);
		for (Node n : nodes) {
			Node right = n.col() < 8 ? bySlot.get(n.item().slot() + 1) : null, below = bySlot.get(n.item().slot() + 9);
			if (right != null) link(g, n.x() + TILE_W, n.y() + TILE_H / 2, right.x(), n.y() + TILE_H / 2 + 2, n, right, a);
			if (below != null) link(g, n.x() + TILE_W / 2 - 1, n.y() + TILE_H, n.x() + TILE_W / 2 + 1, below.y(), n, below, a);
		}

		Node hovered = null;
		for (Node n : nodes) {
			boolean over = n.contains(mouseX, mouseY);
			if (over) hovered = n;
			extractNode(g, n, over, a);
		}
		extractPowder(g, a);
		if (hovered != null) Theme.itemTooltip(g, hovered.item().stack(), mouseX, mouseY); // Hypixel's perk text
	}

	/** Powder kinds in display order: name, color, item. */
	private static final Object[][] POWDERS = {
			{"Mithril", 0x6CD655, new ItemStack(Items.PRISMARINE_CRYSTALS)},
			{"Gemstone", 0xC57BFF, new ItemStack(Items.AMETHYST_SHARD)},
			{"Glacite", 0x8FDCFF, new ItemStack(Items.PACKED_ICE)}};
	private static final int PANEL_W = 104;

	/**
	 * Right-side powder panel. The numbers count toward what the menu shows, so spending
	 * powder visibly ticks the value down.
	 */
	private void extractPowder(GuiGraphicsExtractor g, float a) {
		int x = bx() + BW - PANEL_W, y = by() + TOP;
		Theme.divider(g, font, "POWDER", x, y, PANEL_W, a);
		for (int i = 0; i < POWDERS.length; i++) {
			String kind = (String) POWDERS[i][0];
			int color = (int) POWDERS[i][1], cy = y + 14 + i * 40;
			Long amount = powder.get(kind);
			Theme.box(g, x, cy, PANEL_W, 36, Theme.ROW, a);
			g.fill(x + 1, cy + 1, x + 3, cy + 35, Theme.argb(color, a));
			Theme.slot(g, (ItemStack) POWDERS[i][2], x + 7, cy + 9, 1f, a);
			g.text(font, kind, x + 29, cy + 7, Theme.argb(color, a), true); // "POWDER" heading above says what it is
			String shown;
			if (amount == null) shown = "—";
			else {
				Anim count = powderShown.computeIfAbsent(kind, k -> {
					Anim c = new Anim(amount, 6f);
					return c;
				});
				count.target(amount);
				shown = String.format("%,d", Math.round((double) count.get()));
			}
			g.text(font, shown, x + 29, cy + 20, Theme.argb(Theme.TEXT, a), true);
		}
	}

	/** How full a tier is (0..1000): reached = full, in progress = its percent, locked = empty. */
	private static int tierFill(String itemId, MenuItem item) {
		return switch (TrackParser.state(itemId)) {
			case DONE -> 1000;
			case LOCKED -> 0;
			case CURRENT -> {
				Float p = CollectionParser.parse(item.name(), item.lore()).percent();
				yield p == null ? 0 : Math.round(p * 1000);
			}
		};
	}

	/**
	 * Total progress through all {@link #TIERS} tiers (0..1), remembered across scrolling and
	 * reopening, so the bar stays put. Updated only when the visible tiers pin it down.
	 */
	private static float totalProgress = -1;

	private void updateTotalProgress() {
		float best = -1;
		int highestDone = 0;
		boolean anyLocked = false;
		for (int[] t : tierLabels) {
			if (t[2] >= 1000) highestDone = Math.max(highestDone, t[1]);
			else if (t[2] > 0) best = (t[1] - 1 + t[2] / 1000f) / TIERS; // the tier in progress says it exactly
			else anyLocked = true;
		}
		if (best >= 0) totalProgress = best;
		else if (highestDone > 0 && anyLocked) totalProgress = highestDone / (float) TIERS; // done right up to a locked tier
		else if (highestDone > 0) totalProgress = Math.max(totalProgress, highestDone / (float) TIERS); // at least this far
	}

	/** HOTM XP: one fixed green bar down the tier column for total progress; scrolling doesn't move it. */
	private void extractXpBar(GuiGraphicsExtractor g, int x, float a) {
		updateTotalProgress();
		int top = by() + TOP, bottom = by() + TOP + 5 * ROW_STEP + TILE_H;
		Theme.box(g, x, top, 5, bottom - top, Theme.SLOT, a);
		if (totalProgress <= 0) return;
		int fillTop = bottom - 1 - Math.round((bottom - top - 2) * Math.min(1f, totalProgress));
		g.fill(x + 1, fillTop, x + 4, bottom - 1, Theme.argb(Theme.color(5), a));
	}

	private static void link(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, Node a1, Node a2, float a) {
		boolean lit = a1.perk().state() != State.LOCKED && a2.perk().state() != State.LOCKED;
		g.fill(x1, y1, x2, y2, Theme.argb(lit ? Theme.ACCENT : Theme.LINE, a));
	}

	private void extractNode(GuiGraphicsExtractor g, Node n, boolean over, float a) {
		Anim hv = hover.computeIfAbsent(n.item().slot(), k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		float h = hv.get();
		int lift = Math.round(h);
		int fill, border;
		switch (n.perk().state()) {
			case MAXED -> { fill = Theme.mix(Theme.ROW, MAXED, 0.22f); border = MAXED; }
			case UNLOCKED -> { fill = Theme.mix(Theme.ROW, Theme.FRAME, 0.12f); border = Theme.FRAME; }
			default -> { fill = Theme.ROW; border = Theme.LINE; }
		}
		int x = n.x(), y = n.y() - lift;
		Theme.box(g, x, y, TILE_W, TILE_H, Theme.mix(fill, Theme.ROW_HOVER, 0.5f * h), a, Theme.mix(border, Theme.TEXT, 0.5f * h), a);
		int size = Math.round(16 + 2 * h);
		Theme.item(g, n.item().stack(), x + (TILE_W - size) / 2, y + (TILE_H - size) / 2 - 2, size, a);
		if (n.perk().state() == State.LOCKED) g.fill(x + 1, y + 1, x + TILE_W - 1, y + TILE_H - 1, Theme.argb(Theme.PANEL, a * 0.55f));
		if (n.perk().level() >= 0 && n.perk().state() != State.LOCKED) { // level in the corner, like a stack count
			String lvl = String.valueOf(n.perk().level());
			g.text(font, lvl, x + TILE_W - 2 - font.width(lvl), y + TILE_H - 9,
					Theme.argb(n.perk().state() == State.MAXED ? MAXED : Theme.TEXT, a), true);
		}
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		for (Node n : nodes())
			if (n.contains(event.x(), event.y())) {
				clickAs(n.item().slot(), event);
				return true;
			}
		return false;
	}
}
