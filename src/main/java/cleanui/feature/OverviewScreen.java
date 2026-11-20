package cleanui.feature;

import cleanui.ui.Anim;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Shown in place of Hypixel's SkyBlock Menu (the Nether Star). Every button sits in the same
 * column and row order as in the real menu, as a Clean UI box; the profile button above the
 * middle becomes a Profile & Stats card with the live player model and gear. Each box passes
 * its click to the real button, so everything opens exactly as in the normal menu.
 */
public final class OverviewScreen extends MenuPage {
	public static final String MENU_TITLE = "SkyBlock Menu";

	private static final String PROFILE = "Your SkyBlock Profile";
	private static final int PROFILE_SLOT = 13;
	private static final int COLS = 9, COL_STEP = 47, TILE_W = 44, TILE_H = 36, ROW_STEP = 39, EMPTY_ROW = 6;
	private static final int GRID_W = COLS * COL_STEP - 3;
	private static final int HERO_COL = 2, HERO_COLS = 5, HERO_H = 70;
	private static final EquipmentSlot[] GEAR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
	/** Vanilla's empty-slot outlines, so empty gear slots still read as armor slots. */
	private static final Identifier[] EMPTY = {
			Identifier.withDefaultNamespace("container/slot/helmet"),
			Identifier.withDefaultNamespace("container/slot/chestplate"),
			Identifier.withDefaultNamespace("container/slot/leggings"),
			Identifier.withDefaultNamespace("container/slot/boots"),
			Identifier.withDefaultNamespace("container/slot/sword")};

	private record Tile(MenuItem item, int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	/** The profile card (may be null until the menu loads) and the other buttons. */
	private record Layout(Tile hero, List<Tile> tiles) {}

	private final Map<Integer, Anim> hover = new HashMap<>();

	public OverviewScreen(AbstractContainerScreen<?> original) {
		super(MENU_TITLE, new ItemStack(Items.NETHER_STAR), 0, original);
	}

	@Override
	protected String hint() {
		return "Hover a button to see what it does.";
	}

	private int gx() {
		return bx() + (BW - GRID_W) / 2;
	}

	/**
	 * Same columns as the real menu. Rows below the profile keep their order; a row that's
	 * empty in the menu becomes a small gap, like the gap above the bottom row in vanilla.
	 */
	private Layout layout() {
		MenuItem hero = null;
		Map<Integer, List<MenuItem>> rows = new TreeMap<>();
		for (MenuItem item : menuItems()) {
			String name = item.name();
			// blank names are glass filler; Heart of the Mountain is left out for now
			if (name.isEmpty() || name.startsWith("Heart of the")) continue;
			if (name.equals(PROFILE) || item.slot() == PROFILE_SLOT) { // by name, or by its spot above the middle
				hero = item;
				continue;
			}
			int row = item.slot() / COLS;
			if (row >= 2) rows.computeIfAbsent(row, k -> new ArrayList<>()).add(item);
		}
		Tile heroTile = hero == null ? null
				: new Tile(hero, gx() + HERO_COL * COL_STEP, by(), HERO_COLS * COL_STEP - 3, HERO_H);
		List<Tile> tiles = new ArrayList<>();
		int y = by() + HERO_H + 6;
		for (int r = 2; r < 6; r++) {
			List<MenuItem> row = rows.get(r);
			if (row == null) {
				y += EMPTY_ROW;
				continue;
			}
			for (MenuItem item : row) tiles.add(new Tile(item, gx() + (item.slot() % COLS) * COL_STEP, y, TILE_W, TILE_H));
			y += ROW_STEP;
		}
		return new Layout(heroTile, tiles);
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Layout layout = layout();
		if (giveUpIfUnreadable(layout.hero() == null && layout.tiles().isEmpty())) return;

		ItemStack tooltip = null;
		if (layout.hero() != null) tooltip = extractHero(g, layout.hero(), mouseX, mouseY, a);
		for (Tile t : layout.tiles()) {
			boolean over = t.contains(mouseX, mouseY);
			if (over) tooltip = t.item().stack();
			extractTile(g, t, over, a);
		}
		if (tooltip != null) Theme.itemTooltip(g, tooltip, mouseX, mouseY); // Hypixel's own description
	}

	private float hoverOf(int key, boolean over) {
		Anim hv = hover.computeIfAbsent(key, k -> new Anim(0f, 18f));
		hv.target(over ? 1f : 0f);
		return hv.get();
	}

	/** A button: its real item, larger, lifting and growing on hover. Name and description are in the tooltip. */
	private void extractTile(GuiGraphicsExtractor g, Tile t, boolean over, float a) {
		float h = hoverOf(t.item().slot(), over);
		Theme.box(g, t.x(), t.y(), t.w(), t.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a,
				Theme.mix(Theme.LINE, Theme.FRAME, 0.8f * h), a);
		int size = Math.round(20 + 4 * h);
		Theme.item(g, t.item().stack(), t.x() + (t.w() - size) / 2, t.y() + (t.h() - size) / 2 - Math.round(h), size, a);
	}

	/**
	 * Profile & Stats card in the profile button's spot: live model, name, gear in slots.
	 * Returns what to show a tooltip for: a gear piece, else the profile button (your stats).
	 */
	private ItemStack extractHero(GuiGraphicsExtractor g, Tile t, int mouseX, int mouseY, float a) {
		LocalPlayer player = minecraft.player;
		boolean over = t.contains(mouseX, mouseY);
		float h = hoverOf(-1, over);
		int x = t.x(), y = t.y();
		Theme.box(g, x, y, t.w(), t.h(), Theme.mix(Theme.ROW, Theme.ROW_HOVER, h), a,
				Theme.mix(Theme.LINE, Theme.FRAME, 0.8f * h), a);

		// model on the left; it can't fade, so it appears once the frame is mostly in
		if (a > 0.5f)
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 4, y + 3, x + 58, y + t.h() - 3,
					30, 0.0625f, mouseX, mouseY, player);

		int tx = x + 64;
		g.text(font, player.getName().getString(), tx, y + 7, Theme.argb(Theme.TITLE, a), true);
		int tagX = tx + Theme.tag(g, font, "PROFILE", tx, y + 19, Theme.FRAME, a) + 3;
		Theme.tag(g, font, "STATS", tagX, y + 19, Theme.TEXT_MUTED, a);
		Theme.arrow(g, x + t.w() - 12 + Math.round(2 * h), y + 9, Theme.mix(Theme.TEXT_MUTED, Theme.FRAME, h), a);

		ItemStack gearHover = null;
		for (int i = 0; i < GEAR.length; i++) {
			int sx = tx + i * 20 + (i == 4 ? 6 : 0), sy = y + t.h() - 25; // held item after a small gap
			ItemStack stack = player.getItemBySlot(GEAR[i]);
			Theme.box(g, sx, sy, 18, 18, Theme.SLOT, a, Theme.mix(Theme.LINE, Theme.FRAME, 0.45f), a);
			if (stack.isEmpty())
				g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY[i], sx + 1, sy + 1, 16, 16, Theme.argb(0xFFFFFF, a * 0.35f));
			else Theme.item(g, stack, sx + 1, sy + 1, 16, a);
			if (!stack.isEmpty() && mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) gearHover = stack;
		}
		return gearHover != null ? gearHover : over ? t.item().stack() : null;
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		Layout layout = layout();
		if (layout.hero() != null && layout.hero().contains(event.x(), event.y())) {
			click(layout.hero().item().slot(), event.button());
			return true;
		}
		for (Tile t : layout.tiles())
			if (t.contains(event.x(), event.y())) {
				click(t.item().slot(), event.button());
				return true;
			}
		return false;
	}
}
