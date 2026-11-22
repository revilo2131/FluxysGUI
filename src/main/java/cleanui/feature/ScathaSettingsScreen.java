package cleanui.feature;

import cleanui.data.Config;
import cleanui.ui.Anim;
import cleanui.ui.Button;
import cleanui.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Scatha gambling settings, opened from the Mining skill page. Saved on every change;
 * Back returns to the Mining page.
 */
public final class ScathaSettingsScreen extends Window {
	private static final int COL_W = 200, RIGHT_X = 214, ROW_H = SettingsScreen.ROW_H, ROW_STEP = SettingsScreen.ROW_STEP;

	private static final List<SettingsScreen.Toggle> TOGGLES = List.of(
			new SettingsScreen.Toggle("Gamble on kills", "Play the game below on every Scatha kill. The pet only wins if it really dropped.",
					c -> c.scathaSlots, c -> c.scathaSlots = !c.scathaSlots),
			new SettingsScreen.Toggle("Hide chat at low HP", "Chat disappears while a Scatha is on 3 HP or less, so a pet drop can't spoil the roll.",
					c -> c.scathaHideChat, c -> c.scathaHideChat = !c.scathaHideChat),
			new SettingsScreen.Toggle("Blur while rolling", "Blur the world and hide every other UI while the reels spin. Off: they draw over the HUD and you keep mining.",
					c -> c.scathaFocus, c -> c.scathaFocus = !c.scathaFocus),
			new SettingsScreen.Toggle("Then I walk in", "On a pet drop the buff Scatha stomps in from the right: \"Then I scathe in\", with the rarity. Replaces the rarity roll and the portal.",
					c -> c.scathaWalkIn, c -> c.scathaWalkIn = !c.scathaWalkIn),
			new SettingsScreen.Toggle("Sounds", "Reel clicks, landings and the pet celebration.",
					c -> c.scathaSounds, c -> c.scathaSounds = !c.scathaSounds),
			new SettingsScreen.Toggle("Auto coords", "When a Scatha spawns next to you, send your coordinates in party chat (/pc). At most once every 15 seconds.",
					c -> c.scathaAutoCoords, c -> c.scathaAutoCoords = !c.scathaAutoCoords),
			new SettingsScreen.Toggle("Music","Note block build-up when the pet is certain, and a drop as the rarity shows. Needs Sounds on.",
					c -> c.scathaMusic, c -> c.scathaMusic = !c.scathaMusic),
			new SettingsScreen.Toggle("Scatha Stats", "A panel while you farm (it shows after a worm or Scatha near you, hides 10 min after the last): pets by rarity, Stoneworms and Scathas killed all time and this session (all-time numbers update from your bestiary), spawns since the last Scatha, Scathas since your last pet.",
					c -> c.scathaStats, c -> c.scathaStats = !c.scathaStats),
					new SettingsScreen.Toggle("Timers", "Bars on Scatha Stats: Tunnel Vision (active, cooldown, ready; starts tracking the first time you use it) and the 30 s worm spawn cooldown after every worm or Scatha.",
							c -> c.scathaTimers, c -> c.scathaTimers = !c.scathaTimers));

	private static final int TEST_STEP = 20;

	private final Screen parent;
	private final List<Anim> knobs = new ArrayList<>();

	public ScathaSettingsScreen(Screen parent) {
		super("Scatha", ScathaSlots.scathaHead(), 3);
		this.parent = parent;
		for (SettingsScreen.Toggle t : TOGGLES) knobs.add(new Anim(t.get().test(Config.get()) ? 1f : 0f, 16f));
	}

	@Override
	protected String crumb() {
		return parent.getTitle().getString();
	}

	@Override
	protected String hint() {
		return "Saved right away. Test buttons close the menu and spin.";
	}

	@Override
	protected void goBack() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	protected void closed() {
		// the Mining page we came from still has its Hypixel menu open underneath
		if (parent instanceof MenuPage) minecraft.player.closeContainer();
		else super.closed();
	}

	@Override
	protected void initBody() {
		int x = bx() + RIGHT_X, w = BW - RIGHT_X, y = by() + 14;
		addRenderableWidget(new Button(x, y, w, 18, Component.literal("Test spin"), () -> test(-2))
				.item(new ItemStack(Items.GOLD_INGOT)).tooltip(Component.literal("A normal roll: usually no pet.")).body());
		String[] names = {"Rare pet", "Epic pet", "Legendary pet"};
		int[] colors = {0x5555FF, 0xAA00AA, 0xFFAA00};
		for (int i = 0; i < 3; i++) {
			int rarity = i;
			addRenderableWidget(new Button(x, y + (i + 1) * TEST_STEP, w, 18, Component.literal(names[i]), () -> test(rarity))
					.item(ScathaSlots.scathaHead()).outline(colors[i]).color(colors[i])
					.tooltip(Component.literal("A jackpot roll with this rarity and its celebration.")).body());
		}
		// pets dropped, by rarity: set your real totals (from before the mod) with - and +
		for (int i = 0; i < 3; i++) {
			int r = i, ry = petRowY(i);
			addRenderableWidget(new Button(x + w - 42, ry, 20, 18, Component.literal("-"), () -> addPet(r, -1))
					.tooltip(Component.literal("One less " + names[i].toLowerCase())).body());
			addRenderableWidget(new Button(x + w - 20, ry, 20, 18, Component.literal("+"), () -> addPet(r, 1))
					.tooltip(Component.literal("One more " + names[i].toLowerCase())).body());
		}
		// Scatha Stats on screen: drag it around and resize it over the game
		addRenderableWidget(new Button(x, petRowY(2) + 24, w, 18, Component.literal("Move / resize Scatha Stats"),
				() -> minecraft.setScreenAndShow(new HudEditor(this, true)))
				.item(new ItemStack(Items.COMPASS))
				.tooltip(Component.literal("Place the Scatha Stats panel anywhere and pick its size, with a grid to line it up.")).body());
	}

	private static final int[] PET_COLORS = {0x5555FF, 0xAA00AA, 0xFFAA00};
	private static final String[] PET_NAMES = {"Rare", "Epic", "Legendary"};

	private int petRowY(int i) {
		return by() + 114 + i * 20;
	}

	private static void addPet(int rarity, int by) {
		Config c = Config.get();
		c.statPets[rarity] = Math.max(0, c.statPets[rarity] + by);
		c.save();
	}

	/** Close the menu, then spin: -2 = normal roll, 0..2 = a pet of that rarity. */
	private void test(int rarity) {
		onClose();
		if (rarity == -2) ScathaSlots.spin(false);
		else ScathaSlots.spinWin(rarity);
	}

	@Override
	protected void extractBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = bx(), y = by();
		Config c = Config.get();
		Theme.divider(g, font, "SCATHA GAMBLING", x, y, COL_W, a);
		// game choice: click to switch
		int gy = y + 14;
		boolean overGame = mouseX >= x && mouseX < x + COL_W && mouseY >= gy && mouseY < gy + ROW_H;
		Theme.box(g, x, gy, COL_W, ROW_H, overGame ? Theme.ROW_HOVER : Theme.ROW, a, overGame ? Theme.FRAME : Theme.LINE, a);
		Theme.textPair(g, font, "Game", Theme.TEXT_MUTED, gameName(c.scathaGame), Theme.TITLE, x + 5, gy + 4, COL_W - 10, a);
		String tip = overGame ? "Slots: 3 Scathas = pet. Roulette: green = pet. None: no game, only the walk-in on a pet drop. Click to switch." : null;
		SettingsScreen.Toggle hovered = null;
		for (int i = 0; i < TOGGLES.size(); i++) {
			SettingsScreen.Toggle t = TOGGLES.get(i);
			int ry = y + 14 + (i + 1) * ROW_STEP;
			boolean over = mouseX >= x && mouseX < x + COL_W && mouseY >= ry && mouseY < ry + ROW_H;
			if (over) hovered = t;
			boolean on = t.get().test(c);
			knobs.get(i).target(on ? 1f : 0f);
			SettingsScreen.toggleRow(g, font, t.label(), on, knobs.get(i).get(), x, ry, COL_W, over, a);
		}
		Theme.divider(g, font, "TRY IT", x + RIGHT_X, y, BW - RIGHT_X, a);
		// pets dropped: the counts Scatha Stats shows, editable
		int rx = x + RIGHT_X, rw = BW - RIGHT_X;
		Theme.divider(g, font, "PETS DROPPED", rx, y + 100, rw, a);
		for (int i = 0; i < 3; i++) {
			int ry = petRowY(i);
			Theme.box(g, rx, ry, rw - 46, 18, Theme.ROW, a, PET_COLORS[i], a);
			ScathaSlots.item(g, ScathaSlots.scathaHead(), rx + 10, ry + 9, 0.7f, 0.7f);
			Theme.textPair(g, font, PET_NAMES[i], PET_COLORS[i], String.valueOf(c.statPets[i]), Theme.TITLE, rx + 20, ry + 5, rw - 46 - 26, a);
		}
		if (mouseX >= rx && mouseX < rx + rw - 46 && mouseY >= petRowY(0) && mouseY < petRowY(2) + 18)
			tip = "Scatha pets you've dropped, shown in Scatha Stats. New drops count themselves; set your earlier ones here.";
		if (hovered != null) tip = hovered.tip();
		if (tip != null) g.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY, Theme.tooltipStyle());
	}

	private static String gameName(String game) {
		return switch (game) {
			case "ROULETTE" -> "Roulette";
			case "NONE" -> "None";
			default -> "Slots";
		};
	}

	@Override
	protected boolean clicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.clicked(event, doubleClick)) return true;
		int x = bx(), y = by();
		if (event.x() >= x && event.x() < x + COL_W && event.y() >= y + 14 && event.y() < y + 14 + ROW_H) {
			Config c = Config.get();
			c.scathaGame = switch (c.scathaGame) { case "SLOTS" -> "ROULETTE"; case "ROULETTE" -> "NONE"; default -> "SLOTS"; };
			c.save();
			return true;
		}
		for (int i = 0; i < TOGGLES.size(); i++) {
			int ry = y + 14 + (i + 1) * ROW_STEP;
			if (event.x() >= x && event.x() < x + COL_W && event.y() >= ry && event.y() < ry + ROW_H) {
				TOGGLES.get(i).flip().accept(Config.get());
				Config.get().save();
				return true;
			}
		}		return false;
	}
}
