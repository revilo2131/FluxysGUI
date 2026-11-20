package cleanui.feature;

import cleanui.data.Config;
import cleanui.skyblock.CollectionParser;
import cleanui.skyblock.SkillParser;
import cleanui.skyblock.TrackParser;
import cleanui.skyblock.TrackParser.State;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shown in place of a skill's or a collection's own menu ("Farming Skill", "Wheat Collection"):
 * a header card, every tier as a node on a track (done / in progress / locked, read from the
 * glass pane colors), and the next tier's rewards in Hypixel's own text. Clicks and paging go
 * to the real menu. If the menu turns out not to have tiers, it switches to the list view.
 */
public final class TrackScreen extends MenuPage {
	private static final Pattern SKILL = Pattern.compile("^(.+) Skill$");
	private static final Pattern COLLECTION = Pattern.compile("^(.+) Collection$");
	private static final int NODE_W = 22, NODE_H = 18, NODE_STEP = 26, ROW_STEP = 26;
	private static final int CARD_Y = 24, CARD_H = 42, TRACK_Y = 84, NOT_A_TRACK_MS = 800;

	private record Node(MenuItem item, TrackParser.Tier tier, State state, int x, int y) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + NODE_W && my >= y && my < y + NODE_H;
		}
	}

	private final String crumbText;
	private final boolean collection;
	private final long openedAt = System.currentTimeMillis();
	private final Map<Integer, Anim> hover = new HashMap<>();
	private final Anim cardBar = new Anim(0f, 5f), maxBar = new Anim(0f, 5f);
	private Button prev, next;
	private int prevSlot = -1, nextSlot = -1;
	private MenuItem info;

	private TrackScreen(String title, String crumb, Item icon, boolean collection, AbstractContainerScreen<?> original) {
		super(title, new ItemStack(icon), 2, original);
		this.crumbText = crumb;
		this.collection = collection;
	}

	/** Track page for "<Skill> Skill" / "<Item> Collection" menus (respecting settings), or null. */
	public static TrackScreen forTitle(String title, AbstractContainerScreen<?> original) {
		if (!Config.get().tracks) return null;
		Matcher m;
		if ((m = SKILL.matcher(title)).matches()) return new TrackScreen(m.group(1), "Skills", Items.DIAMOND_SWORD, false, original);
		if ((m = COLLECTION.matcher(title)).matches()) return new TrackScreen(m.group(1), "Collections", Items.PAINTING, true, original);
		return null;
	}

	@Override
	protected String crumb() {
		return crumbText;
	}

	@Override
	protected String hint() {
		return "Hover a tier for its rewards.";
	}

	@Override
	protected void initBody() {
		int right = bx() + BW;
		addRenderableWidget(originalMenuButton(right - 86, by()));
		next = addRenderableWidget(new Button(right - 86 - 6 - 22, by(), 22, 18, Component.literal("›"), () -> click(nextSlot, 0))
				.tooltip(Component.literal("Next tiers")).body());
		prev = addRenderableWidget(new Button(right - 86 - 6 - 22 - 4 - 22, by(), 22, 18, Component.literal("‹"), () -> click(prevSlot, 0))
				.tooltip(Component.literal("Previous tiers")).body());
	}

	/** Tier panes as nodes, in tier order, wrapped into rows; also finds the info item and paging. */
	private List<Node> nodes() {
		List<MenuItem> items = menuItems();
		logMenuOnce(items);
		List<MenuItem> tiers = new ArrayList<>();
		info = null;
		prevSlot = nextSlot = -1;
		for (MenuItem item : items) {
			String name = item.name();
			String id = BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString();
			// special reward levels (e.g. Combat XXV with Power Stones) are real items, not panes
			boolean rewardItem = item.lore().stream().anyMatch(l -> l.startsWith("Reward")) || TrackParser.marksUnlocked(item.lore());
			if (name.equals("Previous Page")) prevSlot = item.slot();
			else if (name.equals("Next Page")) nextSlot = item.slot();
			else if ((TrackParser.isPane(id) || rewardItem) && TrackParser.tier(name) != null) tiers.add(item);
			else if (info == null && !name.isEmpty() && !NAV.contains(name) && !TrackParser.isPane(id)) info = item;
		}
		tiers.sort(Comparator.comparingInt(i -> TrackParser.tier(i.name()).tier()));
		// skill menus say "UNLOCKED" / "Progress:" in the lore; otherwise the pane colour tells
		boolean loreStates = tiers.stream().anyMatch(i -> TrackParser.marksUnlocked(i.lore()));
		int perRow = BW / NODE_STEP;
		List<Node> nodes = new ArrayList<>();
		for (int i = 0; i < tiers.size(); i++) {
			MenuItem item = tiers.get(i);
			String id = BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString();
			nodes.add(new Node(item, TrackParser.tier(item.name()), loreStates ? TrackParser.stateFromLore(item.lore()) : TrackParser.state(id),
					bx() + (i % perRow) * NODE_STEP, by() + TRACK_Y + (i / perRow) * ROW_STEP));
		}
		return nodes;
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		List<Node> nodes = nodes();
		prev.active = prevSlot >= 0;
		next.active = nextSlot >= 0;
		prev.visible = next.visible = prevSlot >= 0 || nextSlot >= 0;
		boolean loaded = info != null || !nodes.isEmpty();
		if (giveUpIfUnreadable(!loaded)) return;
		if (loaded && nodes.size() < 2 && System.currentTimeMillis() - openedAt > NOT_A_TRACK_MS) {
			// not a tier menu after all (e.g. a category): show it as a list instead
			minecraft.setScreenAndShow(new ListPage(title.getString(), collection ? ListPage.Mode.COLLECTION_CATEGORY : ListPage.Mode.GENERIC, original));
			return;
		}
		int x = bx(), y = by();

		// summary: highest tier reached on this page
		Node reached = null;
		for (Node n : nodes) if (n.state() == State.DONE) reached = n;
		String summary = reached == null ? title.getString() : title.getString() + "  ";
		g.text(font, summary, x, y + 5, Theme.argb(Theme.TITLE, a), true);
		if (reached != null) Theme.badge(g, font, label(reached.tier()), x + font.width(summary) + font.width(label(reached.tier())) + 5, y + 4, Theme.ACCENT, a);

		extractCard(g, nodes, x, y + CARD_Y, a);

		Theme.divider(g, font, collection ? "TIERS" : "LEVELS", x, y + TRACK_Y - 14, BW, a);
		Node hovered = null;
		double t = System.nanoTime() / 1e9;
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			boolean over = n.contains(mouseX, mouseY);
			if (over) hovered = n;
			Anim hv = hover.computeIfAbsent(n.item().slot(), k -> new Anim(0f, 18f));
			hv.target(over ? 1f : 0f);
			float h = hv.get();
			// connector to the next node in the same row
			if (i + 1 < nodes.size() && nodes.get(i + 1).y() == n.y()) {
				boolean done = n.state() == State.DONE && nodes.get(i + 1).state() == State.DONE;
				g.fill(n.x() + NODE_W, n.y() + NODE_H / 2, n.x() + NODE_STEP, n.y() + NODE_H / 2 + 1, Theme.argb(done ? Theme.ACCENT : Theme.LINE, a));
			}
			int fill, border, text;
			switch (n.state()) {
				case DONE -> { fill = Theme.mix(Theme.ROW, Theme.ACCENT, 0.28f); border = Theme.ACCENT; text = Theme.TEXT; }
				case CURRENT -> {
					float pulse = 0.55f + 0.45f * (float) Math.sin(t * 3.0); // gentle pulse on the tier in progress
					fill = Theme.mix(Theme.ROW, Theme.FRAME, 0.18f);
					border = Theme.mix(Theme.FRAME_DIM, Theme.FRAME, pulse);
					text = Theme.TITLE;
				}
				default -> { fill = Theme.ROW; border = Theme.LINE; text = Theme.TEXT_MUTED; }
			}
			Theme.box(g, n.x(), n.y() - Math.round(h), NODE_W, NODE_H, Theme.mix(fill, Theme.ROW_HOVER, 0.5f * h), a,
					Theme.mix(border, Theme.TEXT, 0.6f * h), a);
			String lbl = String.valueOf(n.tier().tier());
			g.text(font, lbl, n.x() + (NODE_W - font.width(lbl)) / 2, n.y() + 5 - Math.round(h), Theme.argb(text, a), true);
		}

		extractNextRewards(g, nodes, x, y + TRACK_Y + rows(nodes) * ROW_STEP + 2, a);
		if (hovered != null) Theme.itemTooltip(g, hovered.item().stack(), mouseX, mouseY);
		else if (info != null && mouseX >= x && mouseX < x + BW && mouseY >= y + CARD_Y && mouseY < y + CARD_Y + CARD_H)
			Theme.itemTooltip(g, info.stack(), mouseX, mouseY);
	}

	private int rows(List<Node> nodes) {
		return nodes.isEmpty() ? 0 : (nodes.size() + BW / NODE_STEP - 1) / (BW / NODE_STEP);
	}

	private static String label(TrackParser.Tier tier) {
		return tier.arabic() ? String.valueOf(tier.tier()) : CollectionParser.roman(tier.tier());
	}

	/**
	 * Header card from the tiers: the level you're at, a bar to the next level (from the tier in
	 * progress) and, for skills, a second bar for total XP to max level. Falls back to the info item.
	 */
	private void extractCard(GuiGraphicsExtractor g, List<Node> nodes, int x, int y, float a) {
		Node reached = null, current = null;
		for (Node n : nodes) {
			if (n.state() == State.DONE) reached = n;
			if (current == null && n.state() == State.CURRENT) current = n;
		}
		TrackParser.Progress p = current != null ? TrackParser.progress(current.item().lore()) : null;
		if (p == null || p.percent() == null) {
			extractInfoCard(g, x, y, a);
			return;
		}
		Theme.box(g, x, y, BW, CARD_H, Theme.ROW, a);
		Theme.slot(g, info != null ? info.stack() : new ItemStack(minecraftIcon()), x + 8, y + (CARD_H - 18) / 2, 1f, a);
		int tx = x + 32, right = x + BW - 8, level = reached != null ? reached.tier().tier() : 0;
		String name = title.getString() + " " + (reached != null ? label(reached.tier()) : "0");
		g.text(font, name, tx, y + 5, Theme.argb(Theme.TITLE, a), true);
		String nextText = "Next: " + label(current.tier()) + "  " + String.format("%.1f%%", p.percent() * 100);
		g.text(font, nextText, right - font.width(nextText), y + 5, Theme.argb(Theme.ACCENT, a), true);
		cardBar.target(p.percent());
		String amount = p.done() != null ? SkillParser.compact(p.done()) + " / " + SkillParser.compact(p.total()) + " XP" : null;
		Theme.bar(g, font, tx, y + 16, right - tx, 10, cardBar.get(), Theme.FRAME_DIM, amount, a);

		int max = collection ? -1 : TrackParser.maxLevel(title.getString());
		if (max > 0 && p.done() != null) {
			long have = level >= max ? TrackParser.xpFor(max) : TrackParser.totalXp(level, p.done()), need = TrackParser.xpFor(max);
			String lbl = "To " + max;
			g.text(font, lbl, tx, y + 30, Theme.argb(Theme.TEXT_MUTED, a), true);
			int bx = tx + font.width(lbl) + 6;
			maxBar.target(Math.clamp((float) have / need, 0f, 1f));
			Theme.bar(g, font, bx, y + 29, right - bx, 10, maxBar.get(), Theme.ACCENT,
					SkillParser.compact(String.valueOf(have)) + " / " + SkillParser.compact(String.valueOf(need)) + " XP", a);
		}
	}

	private net.minecraft.world.item.Item minecraftIcon() {
		return collection ? Items.PAINTING : Items.DIAMOND_SWORD;
	}

	/** Card from the menu's own info item: its name, and its progress line and bar. */
	private void extractInfoCard(GuiGraphicsExtractor g, int x, int y, float a) {
		Theme.box(g, x, y, BW, CARD_H, Theme.ROW, a);
		ItemStack icon = info != null ? info.stack() : new ItemStack(Items.BOOK);
		Theme.slot(g, icon, x + 8, y + (CARD_H - 18) / 2, 1f, a);
		String name = info != null ? info.name() : title.getString();
		g.text(font, name, x + 32, y + 7, Theme.argb(Theme.TITLE, a), true);
		if (info == null) return;
		CollectionParser.Entry e = CollectionParser.parse(info.name(), info.lore());
		String value = e.collected() != null ? SkillParser.compact(e.collected())
				: e.done() != null ? SkillParser.compact(e.done()) + " / " + SkillParser.compact(e.total())
				: e.percent() != null ? Math.round(e.percent() * 100) + "%" : null;
		if (value != null) Theme.textLeader(g, font, e.collected() != null ? "Total collected" : "Progress", Theme.TEXT_MUTED,
				value, Theme.ACCENT, x + 32, y + 18, BW - 40, a);
		cardBar.target(e.maxed() ? 1f : e.percent() == null ? 0f : e.percent());
		Theme.bar(g, font, x + 32, y + CARD_H - 11, BW - 40, 7, cardBar.get(), e.maxed() ? Theme.ACCENT : Theme.FRAME_DIM, null, a);
	}

	/** The next tier to reach (in progress, else first locked) with its reward text. */
	private void extractNextRewards(GuiGraphicsExtractor g, List<Node> nodes, int x, int y, float a) {
		Node target = null;
		for (Node n : nodes) if (n.state() == State.CURRENT) { target = n; break; }
		if (target == null) for (Node n : nodes) if (n.state() == State.LOCKED) { target = n; break; }
		if (target == null) return;
		Theme.divider(g, font, "NEXT: " + target.item().name().toUpperCase(), x, y, BW, a);
		ItemLore lore = target.item().stack().get(DataComponents.LORE);
		if (lore == null) return;
		int line = 0, maxLines = (by() + BH - (y + 14)) / 10;
		for (Component c : lore.lines()) {
			if (c.getString().isBlank()) continue;
			if (line >= maxLines) break;
			List<FormattedCharSequence> parts = font.split(c, BW - 8);
			if (!parts.isEmpty()) g.text(font, parts.getFirst(), x + 4, y + 14 + line++ * 10, Theme.argb(Theme.TEXT, a), true);
		}
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		// tiers are hover-only: clicking one does nothing (paging and Back still work)
		return super.clicked(event, doubleClick);
	}
}
