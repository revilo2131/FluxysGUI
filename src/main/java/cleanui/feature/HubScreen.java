package cleanui.feature;

import cleanui.ui.Button;
import cleanui.ui.CleanScreen;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Function;

/** Entry screen: one icon tile per feature. Tiles turn active as features land. */
public final class HubScreen extends CleanScreen {
	private static final int W = 260, TILE_H = 64, GAP = 6, HEADER = 40;
	private static final int H = HEADER + TILE_H + Theme.PAD;
	private static final ItemStack SKYBLOCK_MENU = new ItemStack(Items.NETHER_STAR); // SkyBlock's own menu item

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
	}
}
