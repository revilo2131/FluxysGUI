package cleanui.feature;

import cleanui.ui.Button;
import cleanui.ui.CleanScreen;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Entry screen: one icon tile per feature. Tiles turn active as features land. */
public final class HubScreen extends CleanScreen {
	private static final int W = 260, TILE_H = 64, GAP = 6, HEADER = 40;
	private static final int H = HEADER + TILE_H + Theme.PAD;
	private static final ItemStack SKYBLOCK_MENU = new ItemStack(Items.NETHER_STAR); // SkyBlock's own menu item
	private static final int CARD_W = 84, CARD_H = 120;
	private static final EquipmentSlot[] GEAR = {
			EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** {@code open} builds the feature's screen with the hub as parent; null = not built yet. */
	private record Feature(String name, String icon, String description, Function<Screen, Screen> open) {}

	private static final Feature[] FEATURES = {
			new Feature("Calendar", "calendar", "SkyBlock dates, seasons and events.", CalendarScreen::new),
			new Feature("Skills", "skills", "Skill levels and progress.", null),
			new Feature("Storage", "storage", "Backpacks and ender chest pages you've opened.", null),
	};

	public HubScreen() {
		super(Component.literal("Clean UI"), null);
	}

	@Override
	protected void init() {
		int left = (width - W) / 2 + Theme.PAD, y = (height - H) / 2 + HEADER;
		int tileW = (W - Theme.PAD * 2 - GAP * (FEATURES.length - 1)) / FEATURES.length;
		for (int i = 0; i < FEATURES.length; i++) {
			Feature f = FEATURES[i];
			boolean ready = f.open() != null;
			Button tile = new Button(left + i * (tileW + GAP), y, tileW, TILE_H, Component.literal(f.name()),
					() -> { if (ready) minecraft.setScreenAndShow(f.open().apply(this)); })
					.icon(f.icon())
					.color(Theme.PALETTE.get(i))
					.tooltip(Component.literal(ready ? f.description() : f.description() + " Coming soon."));
			tile.active = ready;
			addRenderableWidget(tile);
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = (width - W) / 2, y = (height - H) / 2;
		Theme.panel(g, x, y, W, H, a);
		Theme.item(g, SKYBLOCK_MENU, x + Theme.PAD, y + Theme.PAD - 2, 12, a);
		g.text(font, title, x + Theme.PAD + 16, y + Theme.PAD, Theme.argb(Theme.TEXT, a), false);
		Theme.paletteBar(g, x + Theme.PAD + 16, y + Theme.PAD + 11, 10, a);
		g.text(font, "Pick a feature.", x + Theme.PAD, y + Theme.PAD + 20, Theme.argb(Theme.TEXT_MUTED, a), false);
		extractProfileCard(g, mouseX, mouseY, a);
	}

	/** Bottom-left card: your live player model (skin, armor, held item); hover lists the gear. */
	private void extractProfileCard(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		LocalPlayer player = minecraft.player;
		if (player == null) return;
		int x = Theme.PAD * 2, y = height - CARD_H - Theme.PAD * 2;
		Theme.panel(g, x, y, CARD_W, CARD_H, a);

		String name = player.getName().getString();
		g.text(font, name, x + (CARD_W - font.width(name)) / 2, y + Theme.PAD, Theme.argb(Theme.TEXT, a), false);
		Theme.paletteBar(g, x + CARD_W / 2 - 15, y + Theme.PAD + 11, 10, a);

		// the model can't fade, so it appears once the panel is mostly in
		if (a > 0.5f)
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 6, y + 24, x + CARD_W - 6, y + CARD_H - 6,
					38, 0.0625f, mouseX, mouseY, player);

		if (mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(name));
			for (EquipmentSlot slot : GEAR) {
				ItemStack stack = player.getItemBySlot(slot);
				if (!stack.isEmpty()) lines.add(stack.getStyledHoverName());
			}
			if (lines.size() == 1) lines.add(Component.literal("No gear equipped").withColor(Theme.TEXT_MUTED));
			g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY, Theme.TOOLTIP_STYLE);
		}
	}
}
