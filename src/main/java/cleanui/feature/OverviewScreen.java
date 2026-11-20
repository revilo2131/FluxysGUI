package cleanui.feature;

import cleanui.skyblock.Events;
import cleanui.skyblock.SkyblockTime;
import cleanui.ui.Anim;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * Home page: a profile card (live player model with armor and held item in slots, like a
 * profile viewer) next to today's SkyBlock date and the next few events.
 */
public final class OverviewScreen extends Window {
	private static final int CARD_W = 150, GAP = 10;
	private static final EquipmentSlot[] GEAR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
	/** Vanilla's empty-slot outlines, so empty gear slots still read as armor slots. */
	private static final Identifier[] EMPTY = {
			Identifier.withDefaultNamespace("container/slot/helmet"),
			Identifier.withDefaultNamespace("container/slot/chestplate"),
			Identifier.withDefaultNamespace("container/slot/leggings"),
			Identifier.withDefaultNamespace("container/slot/boots"),
			Identifier.withDefaultNamespace("container/slot/sword")};
	private static final int NEXT_ROWS = 5;

	private final Anim monthBar = new Anim(0f, 6f);

	public OverviewScreen() {
		super(Tab.OVERVIEW);
	}

	@Override
	protected String hint() {
		return "Hover your gear for details.";
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractProfile(g, bx(), by(), mouseX, mouseY, a);
		extractToday(g, bx() + CARD_W + GAP, by(), BW - CARD_W - GAP, a);
	}

	private void extractProfile(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, float a) {
		LocalPlayer player = minecraft.player;
		Theme.box(g, x, y, CARD_W, BH, Theme.ROW, a);
		if (player == null) return;

		String name = player.getName().getString();
		g.text(font, name, x + (CARD_W - font.width(name)) / 2, y + 7, Theme.argb(Theme.TEXT, a), true);
		g.fill(x + 8, y + 19, x + CARD_W - 8, y + 20, Theme.argb(Theme.LINE, a));

		// gear column: helmet, chest, legs, boots, then the held item after a gap
		ItemStack hovered = null;
		for (int i = 0; i < GEAR.length; i++) {
			int sx = x + 8, sy = y + 28 + i * 21 + (i == 4 ? 8 : 0);
			ItemStack stack = player.getItemBySlot(GEAR[i]);
			Theme.box(g, sx, sy, 18, 18, Theme.SLOT, a);
			if (stack.isEmpty())
				g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY[i], sx + 1, sy + 1, 16, 16, Theme.argb(0xFFFFFF, a * 0.35f));
			else Theme.item(g, stack, sx + 1, sy + 1, 16, a);
			if (!stack.isEmpty() && mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) hovered = stack;
		}

		// model fills the rest of the card; it can't fade, so it appears once the frame is mostly in
		if (a > 0.5f)
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 34, y + 24, x + CARD_W - 6, y + BH - 6,
					56, 0.0625f, mouseX, mouseY, player);

		if (hovered != null) Theme.itemTooltip(g, hovered, mouseX, mouseY);
	}

	private void extractToday(GuiGraphicsExtractor g, int x, int y, int w, float a) {
		long now = SkyblockTime.now();
		SkyblockTime.Date today = SkyblockTime.at(now);

		// date block
		Theme.box(g, x, y, w, 62, Theme.ROW, a);
		Theme.label(g, font, "TODAY", x + 8, y + 7, a);
		String season = today.season();
		g.text(font, season, x + w - 8 - font.width(season), y + 7, Theme.argb(Theme.ACCENT, a), true);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + 8, y + 19);
		pose.scale(2f, 2f);
		g.text(font, today.monthName() + " " + SkyblockTime.ordinal(today.day()), 0, 0, Theme.argb(Theme.TEXT, a), true);
		pose.popMatrix();
		long monthStart = SkyblockTime.start(today.year(), today.month(), 1);
		monthBar.target((now - monthStart) / (float) SkyblockTime.MONTH);
		Theme.textPair(g, font, "Year " + today.year() + "  ·  " + today.clock(), Theme.TEXT_MUTED,
				Math.round(monthBar.get() * 100) + "% of month", Theme.TEXT_MUTED, x + 8, y + 39, w - 16, a);
		Theme.box(g, x + 8, y + 50, w - 16, 5, Theme.SLOT, a);
		g.fill(x + 9, y + 51, x + 9 + Math.round((w - 18) * monthBar.get()), y + 54, Theme.argb(Theme.ACCENT, a));

		// next events
		int ly = y + 72;
		Theme.label(g, font, "NEXT UP", x, ly, a);
		List<Events.Occurrence> list = Events.upcoming(now);
		for (int i = 0; i < Math.min(NEXT_ROWS, list.size()); i++)
			CalendarScreen.extractEventRow(g, font, list.get(i), now, x, ly + 13 + i * 20, w, a);
	}
}
