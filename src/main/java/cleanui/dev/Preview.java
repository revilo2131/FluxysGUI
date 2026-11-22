package cleanui.dev;

import cleanui.data.StorageCache;
import cleanui.CleanUi;
import cleanui.feature.CalendarScreen;
import cleanui.data.Config;
import cleanui.feature.ScathaSettingsScreen;
import cleanui.feature.ScathaSlots;
import cleanui.feature.SettingsScreen;
import cleanui.feature.SubMenuScreen;
import cleanui.skyblock.CollectionParser;
import net.minecraft.world.item.DyeColor;
import cleanui.feature.ListPage;
import cleanui.feature.OverviewScreen;
import cleanui.feature.SkillsScreen;
import cleanui.feature.StorageScreen;
import cleanui.skyblock.StorageParser;
import cleanui.skyblock.StorageParser.Kind;
import cleanui.skyblock.StorageParser.PageId;
import cleanui.ui.CleanScreen;
import cleanui.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Dev only, off unless the game runs with -Dcleanui.preview=true (gradlew runPreview).
 * Opens each page in turn (Skills with a fake "Your Skills" menu), forces a mouse position
 * for hover states, saves a screenshot of each to run/screenshots, then quits.
 */
public final class Preview {
	private record Shot(String name, Function<Minecraft, Screen> screen, int mouseX, int mouseY) {}

	// mouse positions are GUI coords for a 960×540 GUI (1920×1080 at GUI scale 2)
	private static final List<Shot> SHOTS = List.of(
			new Shot("overview", Preview::fakeSkyBlockMenu, -1, -1),
			new Shot("overview-tile", Preview::fakeSkyBlockMenu, 339, 268),
			new Shot("overview-hero", Preview::fakeSkyBlockMenu, 520, 192),
			new Shot("calendar", Preview::fakeCalendar, 300, 232),
			new Shot("calendar-mayor", Preview::fakeCalendar, 600, 300),
			new Shot("skills", Preview::fakeSkills, -1, -1),
			new Shot("skills-card", Preview::fakeSkills, 450, 272),
			new Shot("skills-row", Preview::fakeSkills, 600, 250),
			new Shot("storage", Preview::fakeStorage, 380, 282),
			new Shot("storage-item", Preview::fakeStorage, 548, 229),
			new Shot("storage-search", mc -> { StorageScreen.searchForPreview("diamond"); return fakeStorage(mc); }, -1, -1),
			// capture → save → reload from disk: page 3 must show 45 items with the nav row dropped
			new Shot("capture", mc -> { StorageScreen.searchForPreview(""); return fakeEnderChestPage(mc); }, -1, -1),
			new Shot("storage-reloaded", Preview::storageAfterReload, -1, -1),
			new Shot("collections", Preview::fakeCollections, -1, -1),
			new Shot("collections-farming", Preview::fakeFarmingCollections, 330, 230),
			new Shot("track-skill", Preview::fakeSkillTrack, 452, 266),
			new Shot("track-combat", Preview::fakeCombatTrack, 290, 266),
			new Shot("track-collection", Preview::fakeCollectionTrack, -1, -1),
			new Shot("bestiary", Preview::fakeBestiary, -1, -1),
			new Shot("bestiary-hub", Preview::fakeBestiaryHub, -1, -1),
			new Shot("pets", Preview::fakePets, -1, -1),
			new Shot("settings", mc -> themed("cyan", m -> new SettingsScreen(fakeSkyBlockMenu(m)), mc), -1, -1),
			new Shot("scatha", mc -> themed("cyan", m -> new ScathaSettingsScreen(fakeSkyBlockMenu(m)), mc), -1, -1),
			new Shot("nucleus-settings", mc -> themed("cyan", m -> new cleanui.feature.NucleusSettingsScreen(fakeSkyBlockMenu(m)), mc), -1, -1),
			new Shot("scatha-settings", mc -> themed("cyan", m -> new cleanui.feature.ScathaSettingsScreen(fakeSkyBlockMenu(m)), mc), -1, -1),
			new Shot("scatha-stats", mc -> { cleanui.feature.ScathaStats.preview(); return null; }, -1, -1),
			// the move/resize editor, panel moved off the corner, grid on, mouse over the panel
			new Shot("scatha-stats-edit", mc -> {
				cleanui.feature.ScathaStats.preview();
				Config c = Config.get();
				c.scathaStatsX = 40;
				c.scathaStatsY = 64;
				c.scathaStatsScale = 80;
				c.scathaStatsGrid = true;
				return new cleanui.feature.ScathaStatsEditor(themed("cyan", m -> new ScathaSettingsScreen(fakeSkyBlockMenu(m)), mc));
			}, 120, 120),
			// panel low on the screen: the toolbar moves to the top
			new Shot("scatha-stats-edit-low", mc -> {
				cleanui.feature.ScathaStats.preview();
				Config c = Config.get();
				c.scathaStatsX = 300;
				c.scathaStatsY = 160;
				c.scathaStatsScale = 60;
				return new cleanui.feature.ScathaStatsEditor(themed("cyan", m -> new ScathaSettingsScreen(fakeSkyBlockMenu(m)), mc));
			}, 100, 100),
			new Shot("scatha-stats-small", mc -> {
				cleanui.feature.ScathaStats.preview();
				Config c = Config.get();
				c.scathaStatsX = 700;
				c.scathaStatsY = 300;
				c.scathaStatsScale = 70;
				return null;
			}, -1, -1),
			new Shot("vanguard-settings", mc -> themed("cyan", m -> new cleanui.feature.VanguardSettingsScreen(fakeSkyBlockMenu(m)), mc), -1, -1),
			// screenshots land 1.75s after the shot starts: mid-roll, and just after a legendary landing
			new Shot("roulette-roll", mc -> roulette(-2, 1500), -1, -1),
			new Shot("roulette-portal", mc -> roulette(1, 4650), -1, -1),
			new Shot("roulette-win", mc -> roulette(2, 5900), -1, -1),
			new Shot("roulette-lose", mc -> roulette(-2, 4400), -1, -1),
			new Shot("walkin-mid", mc -> game("ROULETTE", true, 1, 4400), -1, -1),
			new Shot("walkin-done", mc -> game("NONE", true, 2, -1750 + 500 + 1500 + 1200), -1, -1),
			new Shot("pose-abs", mc -> game("NONE", true, 2, -1750 + 2300 + 1800 + 900), -1, -1),
			new Shot("pose-lats", mc -> game("NONE", true, 2, -1750 + 2300 + 3600 + 900), -1, -1),
			new Shot("nucleus-found", mc -> nucleus(-1300), -1, -1),
			new Shot("nucleus-tracker", mc -> nucleus(7550), -1, -1),
			new Shot("fin-gauntlet-place", mc -> finale("GAUNTLET", 12050), -1, -1),
			new Shot("fin-gauntlet-build", mc -> finale("GAUNTLET", 16050), -1, -1),
			new Shot("fin-gauntlet-late", mc -> finale("GAUNTLET", 17150), -1, -1),
			new Shot("fin-gauntlet-climax", mc -> finale("GAUNTLET", 17700), -1, -1),
			new Shot("fin-gauntlet-best", mc -> finale("GAUNTLET", 18950), -1, -1),
			new Shot("fin-crown-best", mc -> finale("CROWN", 19400), -1, -1),
			new Shot("fin-altar-place", mc -> finale("ALTAR", 12050), -1, -1),
			new Shot("fin-altar-build", mc -> finale("ALTAR", 16050), -1, -1),
			new Shot("fin-altar-late", mc -> finale("ALTAR", 17150), -1, -1),
			new Shot("fin-altar-climax", mc -> finale("ALTAR", 17700), -1, -1),
			new Shot("fin-reactor-place", mc -> finale("REACTOR", 12050), -1, -1),
			new Shot("fin-reactor-build", mc -> finale("REACTOR", 16050), -1, -1),
			new Shot("fin-reactor-late", mc -> finale("REACTOR", 17150), -1, -1),
			new Shot("fin-reactor-climax", mc -> finale("REACTOR", 17700), -1, -1),
			new Shot("fin-constellation-place", mc -> finale("CONSTELLATION", 12050), -1, -1),
			new Shot("fin-constellation-build", mc -> finale("CONSTELLATION", 16050), -1, -1),
			new Shot("fin-constellation-late", mc -> finale("CONSTELLATION", 17150), -1, -1),
			new Shot("fin-constellation-climax", mc -> finale("CONSTELLATION", 17700), -1, -1),
			new Shot("fin-crown-place", mc -> finale("CROWN", 12050), -1, -1),
			new Shot("fin-crown-build", mc -> finale("CROWN", 16050), -1, -1),
			new Shot("fin-crown-late", mc -> finale("CROWN", 17150), -1, -1),
			new Shot("fin-crown-climax", mc -> finale("CROWN", 17700), -1, -1),
			new Shot("fin-golem-place", mc -> finale("GOLEM", 12050), -1, -1),
			new Shot("fin-golem-build", mc -> finale("GOLEM", 16050), -1, -1),
			new Shot("fin-golem-late", mc -> finale("GOLEM", 17150), -1, -1),
			new Shot("fin-golem-climax", mc -> finale("GOLEM", 17700), -1, -1),

			new Shot("lock-picking", mc -> lock(650, 0, 0), -1, -1),
			new Shot("lock-fail", mc -> lock(1350, 2), -1, -1),
			new Shot("lock-wisp-open", mc -> lock(3050, 0, 2), -1, -1),
			new Shot("lock-dye", mc -> lock(4650, 0, 0), -1, -1),
			new Shot("lock-locket", mc -> lock(4550, 0, 1), -1, -1),
			new Shot("lock-wisp", mc -> lock(3950, 0, 2), -1, -1),
			new Shot("lock-key", mc -> lock(3950, 0, 3), -1, -1),
			new Shot("snake-build", mc -> snake(-1480), -1, -1),
			new Shot("snake-spin", mc -> snake(-250), -1, -1),
			new Shot("snake-swing", mc -> snake(1440, 1), -1, -1),
			new Shot("snake-cut", mc -> snake(1640, 1), -1, -1),
			new Shot("snake-drops", mc -> snake(3450, 0, 2), -1, -1),
			new Shot("snake-glow", mc -> snake(3050), -1, -1),
			new Shot("snake-crumble", mc -> snake(3800), -1, -1),
			new Shot("theme-midnight", mc -> themed("midnight", Preview::fakeSlayer, mc), -1, -1),
			new Shot("theme-neon", mc -> themed("neon", Preview::fakeLoadouts, mc), -1, -1),
			new Shot("theme-glass", mc -> themed("glass", Preview::fakeSkyBlockMenu, mc), -1, -1),
			new Shot("theme-parchment", mc -> themed("parchment", Preview::fakeSvenMenu, mc), 410, 260),
			new Shot("theme-parchment-settings", mc -> themed("parchment", m -> new SettingsScreen(fakeSkyBlockMenu(m)), mc), -1, -1),
			new Shot("wardrobe", Preview::fakeWardrobe, 380, 260),
			new Shot("loadouts", Preview::fakeLoadouts, 700, 260),
			new Shot("hotm", Preview::fakeHotm, -1, -1),
			new Shot("hotm-hover", Preview::fakeHotm, 470, 355),
			new Shot("dungeons", Preview::fakeDungeons, -1, -1),
			new Shot("leveling", Preview::fakeLeveling, -1, -1),
			new Shot("leveling-tile", Preview::fakeLeveling, 340, 262),
			new Shot("hotm-reset", Preview::fakeHotmReset, -1, -1),
			new Shot("hotm-swap", Preview::fakeHotmSwap, -1, -1),
			new Shot("slayer", Preview::fakeSlayer, -1, -1),
			new Shot("slayer-hover", Preview::fakeSlayer, 420, 250),
			new Shot("sven", Preview::fakeSvenMenu, -1, -1),
			new Shot("stats", Preview::fakeStats, -1, -1),
			new Shot("rng", Preview::fakeRngMeter, -1, -1),
			new Shot("rng-hover", Preview::fakeRngMeter, 330, 232));
	private static final int START = 80, EACH = 40;
	/** -Dcleanui.preview.only=a,b runs just those shots. */
	private static final List<Shot> RUN = SHOTS.stream()
			.filter(s -> System.getProperty("cleanui.preview.only", "").isEmpty()
					|| List.of(System.getProperty("cleanui.preview.only").split(",")).contains(s.name())).toList();
	private static int ticks;

	private Preview() {}

	/** Opens {@code page} in the theme with this id, with that theme's own style. */
	private static Screen themed(String id, Function<Minecraft, Screen> page, Minecraft mc) {
		Theme.Preset p = Theme.byId(id);
		Theme.applyPreset(id);
		Theme.applyStyle(p.frameStyle().name(), p.rounded(), p.flatBars(), true);
		return page.apply(mc);
	}

	public static void init() {
		if (Boolean.getBoolean("cleanui.preview")) ClientTickEvents.END_CLIENT_TICK.register(Preview::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null) return;
		mc.options.pauseOnLostFocus = false; // a pause menu over the shots when the window loses focus
		if (++ticks == START - 10) {
			// some gear so the profile card has something to show (client-side only)
			mc.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
			mc.player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			mc.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		}
		int t = ticks - START;
		if (t < 0) return;
		int i = t / EACH, phase = t % EACH;
		if (i >= RUN.size()) {
			mc.stop();
			return;
		}
		Shot s = RUN.get(i);
		if (phase == 0) {
			CleanScreen.debugMouseX = s.mouseX();
			CleanScreen.debugMouseY = s.mouseY();
			themed("cyan", m -> null, mc); // every shot starts in the default theme; themed(...) shots switch it
			mc.setScreenAndShow(s.screen().apply(mc));
		}
		if (phase == EACH - 5)
			Screenshot.grab(mc.gameDirectory, "preview-" + s.name() + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
	}

	/** A roulette spin already {@code msAgo} in; no screen (the spin opens its own). */
	private static Screen roulette(int rarity, long msAgo) {
		return game("ROULETTE", false, rarity, msAgo);
	}

	/** The Vanguard lockpick {@code msAgo} in; {@code pins} = how far it gets with no drop. */
	private static Screen lock(long msAgo, int pins, int... drops) {
		cleanui.feature.VanguardSnake.previewLock(msAgo, pins, drops);
		return null;
	}

	/** The Vanguard snake {@code msAgo} in, with these drops (none = no drop). */
	private static Screen snake(long msAgo, int... drops) {
		cleanui.feature.VanguardSnake.preview(msAgo, drops);
		return null;
	}

	/** A test nucleus run with this finale, {@code msAgo} in (screenshots land 1.75 s later). */
	private static Screen finale(String style, long msAgo) {
		cleanui.feature.NucleusRun.test(msAgo, style);
		return null;
	}

	/** A test nucleus run already {@code msAgo} in; no screen. */
	private static Screen nucleus(long msAgo) {
		cleanui.feature.NucleusRun.test(msAgo);
		return null;
	}

	/** A Scatha game spin already {@code msAgo} in, with the walk-in on or off. */
	private static Screen game(String game, boolean walkIn, int rarity, long msAgo) {
		Config.get().scathaGame = game;
		Config.get().scathaWalkIn = walkIn;
		ScathaSlots.previewSpin(rarity, msAgo);
		return null;
	}

	/** Overview over a fake SkyBlock Menu: glass filler, the usual buttons, and Close. */
	private static Screen fakeSkyBlockMenu(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		for (int i = 0; i < 54; i++) put(c, i, Items.GLASS_PANE, " ");
		// same slots as the real menu (Nov 2026)
		put(c, 10, Items.AMETHYST_CLUSTER, "Heart of the Mountain", "View your Heart of the Mountain tree.");
		put(c, 13, Items.PLAYER_HEAD, "Your SkyBlock Profile", "Health 1,200", "Defense 450", "Strength 210", "", "Click to view your profile!");
		put(c, 19, Items.DIAMOND_SWORD, "Your Skills", "View your Skill progression and rewards.");
		put(c, 20, Items.PAINTING, "Collections", "View all of the items available in SkyBlock.");
		put(c, 21, Items.BOOK, "Recipe Book", "Through your adventure, you will unlock recipes.");
		put(c, 22, Items.EXPERIENCE_BOTTLE, "SkyBlock Leveling", "Your SkyBlock Level and rewards.");
		put(c, 23, Items.WRITABLE_BOOK, "Quest Log", "View your active quests.");
		put(c, 24, Items.CLOCK, "Calendar and Events", "View the SkyBlock Calendar.");
		put(c, 25, Items.CHEST, "Storage", "Store items in your Ender Chest and Backpacks.");
		put(c, 29, Items.LEATHER, "Your Bags", "Different bags allow you to store items.");
		put(c, 30, Items.BONE, "Pets", "View and manage all of your Pets.");
		put(c, 31, Items.CRAFTING_TABLE, "Crafting Table", "Opens the crafting grid.");
		put(c, 32, Items.LEATHER_CHESTPLATE, "Wardrobe", "Store armor sets and quickly swap.");
		put(c, 33, Items.GOLD_INGOT, "Personal Bank", "Contact your Banker from anywhere.");
		put(c, 47, Items.FILLED_MAP, "Fast Travel", "Teleport to islands you've already visited.");
		put(c, 48, Items.NAME_TAG, "Profile Management", "Create and manage your profiles.");
		put(c, 49, Items.BARRIER, "Close");
		put(c, 50, Items.REDSTONE_TORCH, "Settings", "View and edit your SkyBlock settings.");
		put(c, 51, Items.COOKIE, "Booster Cookie", "Obtain the Cookie Buff.");
		return new OverviewScreen(menu(mc, c, OverviewScreen.MENU_TITLE));
	}

	/** Main Collections menu; lore shapes vary on purpose to exercise the loose parser. */
	private static Screen fakeCollections(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 20, Items.GOLDEN_HOE, "Farming Collections", "View your Farming Collections!", "", "Collections Unlocked: 80%", "12/15");
		put(c, 21, Items.STONE_PICKAXE, "Mining Collections", "View your Mining Collections!", "", "Collections Unlocked: 61%", "14/23");
		put(c, 22, Items.STONE_SWORD, "Combat Collections", "View your Combat Collections!", "", "Collections Maxed Out: 4/11");
		put(c, 23, Items.JUNGLE_SAPLING, "Foraging Collections", "View your Foraging Collections!", "", "7/7");
		put(c, 24, Items.FISHING_ROD, "Fishing Collections", "View your Fishing Collections!", "", "Collections Unlocked: 44%");
		put(c, 30, Items.MYCELIUM, "Rift Collections", "View your Rift Collections!");
		put(c, 32, Items.WITHER_SKELETON_SKULL, "Boss Collections", "View your Boss Collections!", "", "3/8");
		put(c, 48, Items.ARROW, "Go Back");
		put(c, 49, Items.BARRIER, "Close");
		return ListPage.forTitle("Collections", menu(mc, c, "Collections"));
	}

	private static Screen fakeFarmingCollections(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		Object[][] rows = {
				{Items.WHEAT, "Wheat XI", "Progress to Wheat XII: 45.2%", "55,000/100k", "Total Collected: 1,234,567"},
				{Items.CARROT, "Carrot IX", "Progress to Carrot X: 12%", "Total Collected: 402,113"},
				{Items.POTATO, "Potato VIII", "Progress to Potato IX: 78.5%", "Total Collected: 221,980"},
				{Items.PUMPKIN, "Pumpkin X", "Progress to Pumpkin XI: 3.1%", "Total Collected: 2,550,000"},
				{Items.MELON_SLICE, "Melon IX", "Progress to Melon X: 60%", "Total Collected: 980,400"},
				{Items.WHEAT_SEEDS, "Seeds VI", "Progress to Seeds VII: 22%", "Total Collected: 41,000"},
				{Items.RED_MUSHROOM, "Mushroom VII", "Progress to Mushroom VIII: 91%", "Total Collected: 88,210"},
				{Items.COCOA_BEANS, "Cocoa Beans X", "Progress to Cocoa Beans XI: 54%", "Total Collected: 1,800,000"},
				{Items.CACTUS, "Cactus IX", "Collection Maxed Out!", "Total Collected: 10,500,000"},
				{Items.SUGAR_CANE, "Sugar Cane VIII", "Progress to Sugar Cane IX: 35%", "Total Collected: 305,000"},
				{Items.FEATHER, "Feather V", "Progress to Feather VI: 66%", "Total Collected: 12,400"},
				{Items.LEATHER, "Leather IV", "Progress to Leather V: 8%", "Total Collected: 3,100"},
				{Items.PORKCHOP, "Raw Porkchop VI", "Progress to Raw Porkchop VII: 40%", "Total Collected: 25,000"},
				{Items.NETHER_WART, "Nether Wart IX", "Progress to Nether Wart X: 71%", "Total Collected: 2,500,000"},
				{Items.FLINT, "Mutton", "Find this item to add it to your", "collection and unlock rewards!"},
		};
		int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28};
		for (int i = 0; i < rows.length; i++) {
			String[] lore = new String[rows[i].length - 2];
			for (int j = 2; j < rows[i].length; j++) lore[j - 2] = (String) rows[i][j];
			put(c, slots[i], (Item) rows[i][0], (String) rows[i][1], lore);
		}
		put(c, 48, Items.ARROW, "Go Back");
		put(c, 53, Items.ARROW, "Next Page");
		return ListPage.forTitle("Farming Collections", menu(mc, c, "Farming Collections"));
	}

	private static Item pane(DyeColor color) {
		return Items.STAINED_GLASS_PANE.pick(color);
	}

	/** "Farming Skill", second page (levels 26-50): reached up to 31, 32 in progress. */
	private static Screen fakeSkillTrack(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 4, Items.GOLDEN_HOE, "Farming XXXI", "Progress to Level XXXII: 45%", "1,350,000/3M");
		int[] path = {9, 18, 27, 36, 37, 38, 29, 20, 11, 2, 3, 12, 21, 30, 39, 40, 41, 32, 23, 14, 5, 6, 15, 24, 33};
		for (int i = 0; i < path.length; i++) {
			int level = 26 + i;
			DyeColor color = level <= 31 ? DyeColor.LIME : level == 32 ? DyeColor.YELLOW : DyeColor.RED;
			put(c, path[i], pane(color), "Farming " + CollectionParser.roman(level), "Rewards:", "  Farmhand " + CollectionParser.roman(level + 1),
					"    Grants +4☘ Farming Fortune.", "  +5 ❤ Health", "  +" + (level * 10_000) + " Coins", "  +20 SkyBlock XP");
		}
		put(c, 45, Items.ARROW, "Previous Page");
		put(c, 48, Items.ARROW, "Go Back");
		put(c, 53, Items.ARROW, "Next Page");
		return CleanUi.pageFor("Farming Skill", menu(mc, c, "Farming Skill"));
	}

	/** Real "Combat Skill" layout (game log, 2026-11-21): lore says UNLOCKED / Progress; XXV and XXXV are items. */
	private static Screen fakeCombatTrack(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		int[] path = {0, 9, 18, 27, 28, 29, 20, 11, 2, 3, 4, 13, 22, 31, 32, 33, 24, 15, 6, 7, 8, 17};
		for (int i = 0; i < path.length; i++) {
			int level = 17 + i;
			String r = CollectionParser.roman(level);
			List<String> lore = new ArrayList<>(List.of("Rewards:", "  Warrior " + r, "    Deal " + (level * 4 - 4) + "➜" + level * 4 + "% more damage to mobs.",
					"  +0.5% Crit Chance", "  +20 SkyBlock XP"));
			if (level <= 27) lore.addAll(List.of("", "UNLOCKED"));
			if (level == 28) lore.addAll(List.of("", "Progress: 66.8%", "                          667,615.3/1M"));
			Item item = level == 25 || level == 35 ? Items.NETHER_STAR : pane(level <= 27 ? DyeColor.LIME : level == 28 ? DyeColor.YELLOW : DyeColor.RED);
			put(c, path[i], item, "Combat Level " + r, lore.toArray(String[]::new));
		}
		put(c, 48, Items.ARROW, "Go Back");
		put(c, 39, Items.WRITABLE_BOOK, "Bestiary", "The Bestiary is a compendium of", "mobs in SkyBlock.", "", "Families Found: 26.2%");
		put(c, 41, Items.GOLDEN_SWORD, "Slayer", "Slayer Quests involve killing a large", "number of a certain mob.");
		put(c, 45, Items.ARROW, "Scroll Left", "Left-click to scroll!");
		put(c, 53, Items.ARROW, "Scroll Right", "Left-click to scroll!");
		return CleanUi.pageFor("Combat Skill", menu(mc, c, "Combat Skill"));
	}

	private static Screen fakeCollectionTrack(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 4, Items.WHEAT, "Wheat XI", "Total Collected: 1,234,567", "Progress to Wheat XII: 45.2%", "55,000/100k");
		for (int tier = 1; tier <= 12; tier++)
			put(c, 17 + tier, pane(tier <= 11 ? DyeColor.LIME : DyeColor.YELLOW), "Wheat " + CollectionParser.roman(tier),
					"Wheat " + CollectionParser.roman(tier) + " Rewards:", "  Enchanted Hay Bale Recipe", "  +4 SkyBlock XP");
		put(c, 48, Items.ARROW, "Go Back");
		return CleanUi.pageFor("Wheat Collection", menu(mc, c, "Wheat Collection"));
	}

	private static Screen fakeBestiary(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		String[][] islands = {{"Private Island", "8/12"}, {"The Hub", "14/20"}, {"The Spider's Den", "9/11"}, {"The End", "6/10"},
				{"Crimson Isle", "11/25"}, {"Deep Caverns", "7/7"}, {"Dwarven Mines", "10/14"}, {"Crystal Hollows", "5/12"},
				{"The Park", "4/6"}, {"Galatea", "2/9"}, {"Dungeons", "12/18"}, {"Fishing", "20/41"}};
		for (int i = 0; i < islands.length; i++)
			put(c, 10 + i + (i / 7) * 2, Items.PLAYER_HEAD, islands[i][0], "View your " + islands[i][0] + " Bestiary!", "", "Families Found: " + islands[i][1]);
		put(c, 48, Items.ARROW, "Go Back");
		return CleanUi.pageFor("Bestiary", menu(mc, c, "Bestiary"));
	}

	private static Screen fakeBestiaryHub(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		Object[][] mobs = {{Items.ROTTEN_FLESH, "Zombie VII", "Kills: 4,102", "Progress to Tier VIII: 61%"},
				{Items.BONE, "Skeleton V", "Kills: 1,230", "Progress to Tier VI: 22%"},
				{Items.STRING, "Spider IV", "Kills: 640", "Progress to Tier V: 88%"},
				{Items.GUNPOWDER, "Creeper III", "Kills: 210", "Progress to Tier IV: 40%"},
				{Items.SLIME_BALL, "Slime II", "Kills: 75", "Progress to Tier III: 15%"},
				{Items.FEATHER, "Chicken X", "Kills: 25,000", "Family maxed!"},
				{Items.LEATHER, "Cow VI", "Kills: 2,000", "Progress to Tier VII: 70%"},
				{Items.PORKCHOP, "Pig VI", "Kills: 1,800", "Progress to Tier VII: 55%"},
				{Items.BARRIER, "Crypt Ghoul", "Kill this mob to add it", "to your Bestiary!"}};
		for (int i = 0; i < mobs.length; i++) {
			String[] lore = new String[mobs[i].length - 2];
			for (int j = 2; j < mobs[i].length; j++) lore[j - 2] = (String) mobs[i][j];
			put(c, 10 + i + (i / 7) * 2, (Item) mobs[i][0], (String) mobs[i][1], lore);
		}
		put(c, 48, Items.ARROW, "Go Back");
		return CleanUi.pageFor("Bestiary ➜ The Hub", menu(mc, c, "Bestiary ➜ The Hub"));
	}

	private static Screen fakePets(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		Object[][] pets = {{Items.PLAYER_HEAD, "§7[Lvl 100] §6Golden Dragon", "MAX LEVEL"},
				{Items.PLAYER_HEAD, "§7[Lvl 87] §6Elephant", "Progress to Level 88: 41.2%"},
				{Items.PLAYER_HEAD, "§7[Lvl 100] §5Ender Dragon", "MAX LEVEL"},
				{Items.PLAYER_HEAD, "§7[Lvl 64] §9Bee", "Progress to Level 65: 12%"},
				{Items.PLAYER_HEAD, "§7[Lvl 99] §6Blue Whale", "Progress to Level 100: 93%"},
				{Items.PLAYER_HEAD, "§7[Lvl 45] §aRabbit", "Progress to Level 46: 30%"},
				{Items.PLAYER_HEAD, "§7[Lvl 100] §dGriffin", "MAX LEVEL"},
				{Items.PLAYER_HEAD, "§7[Lvl 72] §6Mithril Golem", "Progress to Level 73: 66%"},
				{Items.PLAYER_HEAD, "§7[Lvl 30] §fSheep", "Progress to Level 31: 8%"},
				{Items.PLAYER_HEAD, "§7[Lvl 100] §5Scatha", "MAX LEVEL"},
				{Items.BONE, "Pet Score Rewards", "Pet Score: 182", "Magic Find: +3"}};
		for (int i = 0; i < pets.length; i++)
			put(c, 10 + i + (i / 7) * 2, (Item) pets[i][0], (String) pets[i][1], (String) pets[i][2]);
		put(c, 48, Items.ARROW, "Go Back");
		put(c, 53, Items.ARROW, "Next Page");
		return CleanUi.pageFor("Pets (1/2)", menu(mc, c, "Pets (1/2)"));
	}

	/** A menu with its own controls stays a chest screen, only restyled. */
	private static Screen fakeWardrobe(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		Item[][] sets = {{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
				{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS},
				{Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS}};
		for (int s = 0; s < sets.length; s++)
			for (int piece = 0; piece < 4; piece++) c.setItem(piece * 9 + s, new ItemStack(sets[s][piece]));
		for (int s = 0; s < 9; s++) {
			String st = s == 1 ? "Equipped" : s < 3 ? "Ready" : s < 7 ? "Empty" : "Locked";
			put(c, 36 + s, pane(s == 1 ? DyeColor.LIME : s < 3 ? DyeColor.PINK : s < 7 ? DyeColor.GRAY : DyeColor.RED), "Slot " + (s + 1) + ": " + st,
					s < 3 ? "Click to equip!" : st);
		}
		put(c, 53, Items.ARROW, "Next Page");
		put(c, 48, Items.ARROW, "Go Back");
		put(c, 49, Items.BARRIER, "Close");
		return CleanUi.pageFor("Wardrobe (1/2)", menu(mc, c, "Wardrobe (1/2)"));
	}

	/** Heart of the Mountain, tiers 7 (top) to 2: tier labels in column 0, perks in a tree, scroll arrows. */
	private static Screen fakeHotm(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		for (int row = 0; row < 6; row++) { // tiers 2-5 reached, 6 at 45%, 7 locked
			int tier = 7 - row;
			DyeColor color = tier <= 5 ? DyeColor.LIME : tier == 6 ? DyeColor.YELLOW : DyeColor.RED;
			put(c, row * 9, pane(color), "Tier " + tier, tier == 6 ? "Progress: 45%" : "Tier " + tier + " perks");
		}
		Object[][] perks = { // slot, item, name, level line
				{2, Items.COAL, "Mining Madness", "Level 0/1"}, {3, Items.COAL, "Skymall", "Level 0/1"},
				{4, Items.COAL, "Mining Master", "Level 0/10"}, {5, Items.COAL, "Front Loaded", "Level 0/1"},
				{6, Items.COAL, "Seasoned Mineman", "Level 0/100"},
				{12, Items.EMERALD, "Orbiter", "Level 40/80"}, {13, Items.REDSTONE_BLOCK, "Peak of the Mountain", "Level 5/10"},
				{14, Items.EMERALD, "Lonesome Miner", "Level 22/45"},
				{20, Items.EMERALD, "Professional", "Level 140/140"}, {21, Items.EMERALD, "Mole", "Level 190/190"},
				{22, Items.EMERALD, "Fortunate", "Level 20/20"}, {23, Items.EMERALD, "Great Explorer", "Level 12/20"},
				{24, Items.EMERALD, "Maniac Miner", "Level 1/1"},
				{31, Items.EMERALD, "Goblin Killer", "Level 1/1"},
				{38, Items.EMERALD, "Luck of the Cave", "Level 45/45"}, {39, Items.EMERALD, "Daily Powder", "Level 60/100"},
				{40, Items.EMERALD, "Crystallized", "Level 30/30"}, {41, Items.EMERALD, "Efficient Miner", "Level 100/100"},
				{42, Items.EMERALD, "Orbiter", "Level 80/80"},
				{47, Items.EMERALD_BLOCK, "Mining Speed Boost", "Level 1/1"}, {48, Items.EMERALD, "Precision Mining", "Level 1/1"},
				{49, Items.EMERALD, "Mining Fortune", "Level 34/50"}, {50, Items.EMERALD, "Titanium Insanium", "Level 12/50"},
				{51, Items.EMERALD_BLOCK, "Pickobulus", "Level 1/1"}};
		for (Object[] p : perks)
			put(c, (int) p[0], (Item) p[1], (String) p[2], (String) p[3], "", "Grants a bonus while mining.");
		put(c, 8, Items.ARROW, "Scroll Up");
		put(c, 53, Items.ARROW, "Scroll Down");
		put(c, 17, Items.AMETHYST_CLUSTER, "Heart of the Mountain", "Token of the Mountain: 3",
				"Mithril Powder: 1,234,567", "Gemstone Powder: 3,402,110", "Glacite Powder: 812,345");
		return CleanUi.pageFor("Heart of the Mountain", menu(mc, c, "Heart of the Mountain"));
	}

	/** Real Dungeoneering layout (game log, 2026-11-21). */
	private static Screen fakeDungeons(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 12, Items.WITHER_SKELETON_SKULL, "The Catacombs 21", "Complete this dungeon to", "demonstrate your mastery.", "",
				"Progress to Mastery 22: 21.8%", "                          15,589.1/71.5k", "", "Click to view!");
		put(c, 13, pane(DyeColor.GRAY), "Not Coming Soon");
		put(c, 14, pane(DyeColor.GRAY), "Not Coming Soon");
		Object[][] classes = {{Items.POTION, "Healer 14", "15: 82.4%", "6,077.7/7.4k"}, {Items.BLAZE_ROD, "Mage 16", "17: 86%", "12,385.5/14.4k"},
				{Items.IRON_SWORD, "Berserk 17", "18: 49%", "9,806.4/20k"}, {Items.BOW, "Archer 20", "21: 40.3%", "21,155.2/52.5k"},
				{Items.LEATHER_CHESTPLATE, "Tank 10", "11: 25.8%", "487.3/1.9k"}};
		for (int i = 0; i < classes.length; i++) {
			Object[] k = classes[i];
			put(c, 29 + i, (Item) k[0], (String) k[1], "Progress to Level " + k[2], "                          " + k[3], "",
					i == 1 ? "SELECTED" : "Click to view!");
		}
		put(c, 48, Items.ARROW, "Go Back", "To Your Skills");
		put(c, 50, Items.AMETHYST_SHARD, "Your Essence", "Collect Essence in Dungeons which", "can be used to upgrade gear.", "", "Click to view!");
		return CleanUi.pageFor("Dungeoneering", menu(mc, c, "Dungeoneering"));
	}

	/** Real SkyBlock Leveling layout (game log, 2026-11-21). */
	private static Screen fakeLeveling(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 4, Items.PAINTING, "Your SkyBlock Level Ranking", "Classic Mode", "", "Your level: 132", "You have: 13,233 XP", "",
				"You have completed 22.8% of the", "total SkyBlock XP Tasks.");
		put(c, 16, Items.REDSTONE_TORCH, "Ways to Level Up", "Learn more about the different ways", "to earn SkyBlock XP.", "", "Click to view!");
		put(c, 19, pane(DyeColor.LIME), "Level 132", "Your Level", "", "Reward:", " +5 Health", "", "UNLOCKED", "", "Click to view rewards!");
		put(c, 20, pane(DyeColor.YELLOW), "Level 133", "Next Level", "", "Reward:", " +5 Health", "", "Progress to Level Up:",
				"                          33/100 XP", "", "Click to view rewards!");
		put(c, 21, pane(DyeColor.RED), "Level 134", "Reward:", " +5 Health", "", "Click to view rewards!");
		put(c, 22, pane(DyeColor.RED), "Level 135", "Rewards:", " +1 Strength", " +5 Health", "", "Click to view rewards!");
		put(c, 23, pane(DyeColor.RED), "Level 136", "Reward:", " +5 Health", "", "Click to view rewards!");
		put(c, 25, Items.MAP, "⚑ SkyBlock XP Guide", "Your SkyBlock XP Guide tracks the", "progress you have made.", "", "Click to view!");
		put(c, 30, pane(DyeColor.ORANGE), "Level 140", "Next Milestone Level", "", "Rewards:", " Soulflow Emblem ⸎", " +1 Strength", " +5 Health", "",
				"XP Left to Gain: 767 XP (8 Levels)", "", "Click to view rewards!");
		put(c, 34, Items.CHEST, "Leveling Rewards", "View all the rewards you can unlock.", "", "Progress to Max: 52.9%", "                          27/51", "",
				"Click to view rewards!");
		put(c, 43, Items.NAME_TAG, "Prefix Emblems", "Add some spice by having an emblem", "next to your name.", "", "Click to view!");
		put(c, 48, Items.ARROW, "Go Back", "To SkyBlock Menu");
		put(c, 50, Items.OAK_SIGN, "SkyBlock Levels in Chat", "View other players' SkyBlock Level.", "", "ENABLED", "", "Click to disable!");
		return CleanUi.pageFor("SkyBlock Leveling", menu(mc, c, "SkyBlock Leveling"));
	}

	/** "(1/3) Loadouts" laid out like the user's screenshot (item text guessed): actions, equipment, armor, pet, loadouts. */
	private static Screen fakeLoadouts(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 18, Items.ENDER_EYE, "Edit Loadout", "Click to edit!");
		put(c, 27, Items.OBSIDIAN, "Rename Loadout", "Click to rename!");
		put(c, 36, Items.LEVER, "Loadout Settings", "Click to open!");
		String[] equip = {"Dwarven Handwarmers", "Flowstate Cloak", "Dwarven Belt", "Divan Gloves"};
		for (int i = 0; i < 4; i++) put(c, 10 + i * 9, Items.PLAYER_HEAD, equip[i]);
		Item[] armor = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
		for (int i = 0; i < 4; i++) put(c, 11 + i * 9, armor[i], "Divan's " + new String[]{"Helmet", "Chestplate", "Leggings", "Boots"}[i]);
		put(c, 21, Items.PLAYER_HEAD, "[Lvl 96] Armadillo", "Mining Mount");
		int[] slots = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41};
		Item[] icons = {Items.DIAMOND_HELMET, Items.GOLDEN_HELMET, Items.NETHERITE_HELMET, Items.GOLDEN_HELMET, Items.GOLDEN_HELMET,
				Items.GOLDEN_HELMET, Items.DIAMOND_HELMET, Items.GOLDEN_HELMET, Items.NETHERITE_HELMET, Items.LEATHER_HELMET};
		// real armor names from the user's menu (game log, 2026-11-21); 9 is in use, 11-12 blank
		String[][] sets = {{"Necrotic Wise Dragon Helmet", "Loving Wise Dragon Chestplate", "Necrotic Wise Dragon Leggings", "Necrotic Wise Dragon Boots"},
				{"Jaded Helmet of Divan", "Jaded Chestplate of Divan", "Jaded Leggings of Divan", "Jaded Boots of Divan"},
				{"Jaded Mineral Helmet", "Jaded Glossy Mineral Chestplate", "Jaded Glossy Mineral Leggings", "Jaded Glossy Mineral Boots"},
				{"Ancient Strong Dragon Helmet", "Ancient Strong Dragon Chestplate", "Ancient Strong Dragon Leggings", "Ancient Strong Dragon Boots"},
				{"Jaded Helmet of Divan", "Jaded Chestplate of Divan", "Jaded Leggings of Divan", "Jaded Boots of Divan"},
				{"Jaded Helmet of Divan", "Jaded Chestplate of Divan", "Jaded Leggings of Divan", "Jaded Boots of Divan"},
				{" Ancient Shadow Assassin Helmet", " Ancient Shadow Assassin Chestplate", " Ancient Shadow Assassin Leggings", " Ancient Shadow Assassin Boots"},
				{"Ancient Mastiff Crown", "Ancient Mastiff Chestplate", "Ancient Mastiff Leggings", "Ancient Mastiff Boots"},
				{"Jaded Mineral Helmet", "Jaded Glossy Mineral Chestplate", "Jaded Glossy Mineral Leggings", "Jaded Glossy Mineral Boots"},
				{"Werewolf Helmet", "Werewolf Chestplate", "Werewolf Leggings", "Werewolf Boots"}};
		for (int i = 0; i < slots.length; i++)
			put(c, slots[i], icons[i], "Loadout " + (i + 1), "Helmet: " + sets[i][0], "Chestplate: " + sets[i][1], "Leggings: " + sets[i][2],
					"Boots: " + sets[i][3], "", "Pet: None", "", i == 8 ? "Right-click to edit" : "Left-click to equip!", i == 8 ? "" : "Right-click to edit");
		for (int s : new int[]{42, 43})
			put(c, s, pane(DyeColor.GRAY), "Loadout " + (s - 31), "Helmet: None", "Chestplate: None", "Leggings: None", "Boots: None", "", "Pet: None", "",
					"Right-click to edit", "", "You must customize this loadout", "before you can equip it!");
		put(c, 44, Items.ARROW, "Next Page");
		put(c, 48, Items.ARROW, "Go Back");
		put(c, 49, Items.BARRIER, "Close");
		return CleanUi.pageFor("(1/3) Loadouts", menu(mc, c, "(1/3) Loadouts"));
	}

	/** Real Maddox "Slayer" hub (game log, 2026-11-21), lore shortened. */
	private static Screen fakeSlayer(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 10, Items.ROTTEN_FLESH, "☠ Revenant Horror", " Undead", "", "Abhorrent Zombie stuck between", "", "Zombie Slayer: LVL 6", "", "Click to view boss!");
		put(c, 11, Items.COBWEB, "☠ Tarantula Broodfather", " Arthropod", "", "Monstrous Spider.", "", "Spider Slayer: LVL 6", "", "Click to view boss!");
		put(c, 12, Items.MUTTON, "☠ Sven Packmaster", " Animal", "", "Rabid Wolf.", "", "Wolf Slayer: LVL 7", "", "Click to view boss!");
		put(c, 13, Items.ENDER_PEARL, "☠ Voidgloom Seraph", " Ender", "", "A dark demigod.", "", "Enderman Slayer: LVL 2", "", "Click to view boss!");
		put(c, 14, Items.REDSTONE, "☠ Riftstalker Bloodfiend", "A half-vampire, half-thrall.", "", "Only inside The Rift!");
		put(c, 15, Items.BLAZE_POWDER, "☠ Inferno Demonlord", " Infernal", "", "Pure evil.", "", "Slay Voidgloom Seraph III!");
		put(c, 16, Items.COAL_BLOCK, "Not released yet!", "This boss is still in development!");
		put(c, 27, Items.ARROW, "Go Back", "To Combat Skill");
		put(c, 28, Items.COMPARATOR, "Auto-Slayer", "Upon defeating a boss, automatically", "", "Currently: Enabled", "", "Click to disable!");
		put(c, 29, Items.GOLD_INGOT, "Slayer Leaderboards", "Ultimate bragging rights", "", "Revenant Horror: 25,730 (Top 15.3%)",
				"Tarantula Broodfather: 20,130 (Top 19.84%)", "Sven Packmaster: 100,130 (Top 11.11%)", "Voidgloom Seraph: N/A");
		put(c, 31, Items.BARRIER, "Close");
		put(c, 32, Items.EXPERIENCE_BOTTLE, "Global Combat Wisdom Buff", "Slayer Bonus", "", "Highest slain tiers", "Revenant Horror: Tier IV",
				"Tarantula Broodfather: Tier IV", "Sven Packmaster: Tier IV", "Voidgloom Seraph: Tier II");
		put(c, 33, Items.EMERALD, "Slayer Bonus Rewards", "Unlock bonuses by reaching a LVL", "on the first 3 bosses.", "", "✔ LVL 6", "Earn +3 of any boss's main token.");
		put(c, 34, Items.CLOCK, "Ongoing Slayer Quest", "You have an active Slayer quest.", "", "Boss: Sven Packmaster IV", "Kill Wolves to spawn the boss!",
				"", "Click to cancel the quest!");
		put(c, 35, Items.ENDER_EYE, "Sven Packmaster RNG Meter", "Stored Slayer XP: 100,100", "", "Click to view!");
		return CleanUi.pageFor("Slayer", menu(mc, c, "Slayer"));
	}

	/** Real "Revenant Horror" boss menu (game log, 2026-11-21), lore shortened. */
	private static Screen fakeSvenMenu(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		String[][] tiers = {{"Beginner", "500❤", "15", "5"}, {"Strong", "20,000❤", "25", "25"}, {"Challenging", "400,000❤", "120", "100"},
				{"Deadly", "1,500,000❤", "400", "500"}};
		for (int i = 0; i < tiers.length; i++)
			put(c, 11 + i, Items.ROTTEN_FLESH, "Revenant Horror " + CollectionParser.roman(i + 1), tiers[i][0], "", "Health: " + tiers[i][1],
					"Damage: " + tiers[i][2] + " per second", "", "Life Drain", "Drains health every few seconds.", "",
					"Reward: " + tiers[i][3] + " Zombie Slayer XP", "  + Boss drops", "", "You already have a Slayer Quest active!", "Cancel it first!");
		put(c, 15, Items.ROTTEN_FLESH, "Revenant Horror V", "Excruciating", "", "Requires Revenant Horror Slayer 7");
		put(c, 28, Items.GOLD_INGOT, "Boss Leveling Rewards", "Zombie Slayer LVL", "", "Current LVL: 6", "", "Zombie Slayer XP to LVL 7:",
				"                     25,730/100k", "", "Click to view levels!");
		put(c, 30, Items.GOLD_NUGGET, "Boss Drops", "Revenant Horror", "", "Click to view drops!");
		put(c, 32, Items.BOOK, "Slayer Recipes", "Unlocked: 17 recipes", "", "Click to view recipes!");
		put(c, 34, Items.ENDER_EYE, "Revenant Horror RNG Meter", "Stored Slayer XP: 25,700", "", "Click to view!");
		put(c, 48, Items.ARROW, "Go Back", "To Slayer");
		return CleanUi.pageFor("Revenant Horror", menu(mc, c, "Revenant Horror"));
	}

	/** Guessed "Stats & Equipment" layout (not logged yet): armor + equipment columns, pet, stat items with § colours. */
	private static Screen fakeStats(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 10, Items.DIAMOND_HELMET, "Divan's Helmet", "Mining Speed: +80");
		put(c, 19, Items.DIAMOND_CHESTPLATE, "Divan's Chestplate");
		put(c, 28, Items.DIAMOND_LEGGINGS, "Divan's Leggings");
		put(c, 37, Items.DIAMOND_BOOTS, "Divan's Boots");
		put(c, 11, Items.NAUTILUS_SHELL, "Dwarven Handwarmers");
		put(c, 20, Items.LEATHER_CHESTPLATE, "Empty Cloak Slot");
		put(c, 29, Items.LEAD, "Dwarven Belt");
		put(c, 38, Items.STRING, "Empty Gloves Slot");
		put(c, 47, Items.PLAYER_HEAD, "[Lvl 100] Mithril Golem", "Mining Pet");
		put(c, 15, Items.IRON_SWORD, "Combat Stats", "§c❤ Health §f1,245", "§a❈ Defense §f612", "§c❁ Strength §f287", "§9☣ Crit Chance §f48%",
				"§9☠ Crit Damage §f212%", "§e⚔ Bonus Attack Speed §f35%", "§b✎ Intelligence §f540", "§f✦ Speed §f156", "§c⫽ Ferocity §f12",
				"§d♣ Pet Luck §f20", "§b✯ Magic Find §f145", "§c❣ Health Regen §f100", "", "§eClick to view!");
		put(c, 16, Items.GOLDEN_PICKAXE, "Gathering Stats", "§6⸕ Mining Speed §f1,830", "§6☘ Mining Fortune §f310", "§6☘ Farming Fortune §f120",
				"§6☘ Foraging Fortune §f40", "§3☯ Mining Wisdom §f12", "§9▚ Pristine §f4.5");
		put(c, 24, Items.BOOK, "Wisdom Stats", "§3☯ Combat Wisdom §f30", "§3☯ Mining Wisdom §f12", "§3☯ Farming Wisdom §f5");
		put(c, 25, Items.CLOCK, "Misc Stats", "§a≈ Swing Range §f3", "§c♨ Heat Resistance §f150", "§b❄ Cold Resistance §f20");
		put(c, 48, Items.ARROW, "Go Back", "To SkyBlock Menu");
		return CleanUi.pageFor("Stats & Equipment", menu(mc, c, "Stats & Equipment"));
	}

	/** Real "Crystal Nucleus RNG Meter" layout (game log, 2026-11-21). */
	private static Screen fakeRngMeter(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 4, Items.ENDER_EYE, "Crystal Nucleus RNG Meter", "Your Crystal Nucleus RNG Meter fills", "with 1,000 Nucleus XP every time.", "",
				"Selected Drop", "Divan's Alloy", "", "Progress: 5%", "                          50,000/1M");
		Object[][] drops = {{10, Items.DIAMOND_PICKAXE, "Pickonimbus 2000", "Rare (3.2551%)", "4,000"},
				{11, Items.EGG, "Prehistoric Egg", "Extraordinary (1.6275%)", "8,000"}, {12, Items.GOLD_NUGGET, "Divan Fragment", "Extraordinary (1.953%)", "8,000"},
				{13, Items.AMETHYST_SHARD, "Jasper Crystal", "Extraordinary (0.8138%)", "10,000"},
				{14, Items.ENCHANTED_BOOK, "Enchanted Book (Lapidary I)", "Extraordinary (1.302%)", "10,000"},
				{15, Items.BONE, "Helix Fossil", "Extraordinary (1.302%)", "10,000"}, {16, Items.REDSTONE, "Ruby Crystal", "Extraordinary (0.8138%)", "10,000"},
				{19, Items.COOKIE, "Dwarven O's Gemstone Grahams", "Pray RNGesus (0.472%)", "20,000"}, {20, Items.EMERALD, "Jaderald", "Pray RNGesus (0.472%)", "20,000"},
				{21, Items.ENCHANTED_BOOK, "Enchanted Book (Fortune IV)", "Pray RNGesus (0.2279%)", "35,000"},
				{22, Items.GUNPOWDER, "Gemstone Mixture", "Pray RNGesus (0.2279%)", "35,000"}, {23, Items.SNOWBALL, "Precious Pearl", "Pray RNGesus (0.1628%)", "50,000"},
				{24, Items.BONE_MEAL, "Claw Fossil", "Pray RNGesus (0.1628%)", "50,000"}, {25, Items.POTION, "Recall Potion", "Pray RNGesus (0.0814%)", "100,000"},
				{28, Items.FEATHER, "Quick Claw", "RNGesus Incarnate (0.0163%)", "450,000"}, {30, Items.SLIME_BALL, "Jade Dye", "RNGesus Incarnate (0.001%)", "2,500,000"}};
		for (Object[] d : drops)
			put(c, (Integer) d[0], (Item) d[1], (String) d[2], "Odds: " + d[3], "", "Nucleus XP: 50,000/" + d[4], "", "Click to select!");
		put(c, 29, Items.GOLD_BLOCK, "Divan's Alloy", "Odds: RNGesus Incarnate (0.0055% 0.0061%)", "", "Progress: 5%",
				"                          50,000/1M", "", "SELECTED");
		put(c, 48, Items.ARROW, "Go Back", "To Heart of the Mountain");
		put(c, 50, Items.OAK_SIGN, "Search", "Find drops by name!", "", "Click to search!");
		put(c, 51, Items.LEVER, "Reset RNG Drop", "Resets the selected drop.", "", "Selected Drop", "Divan's Alloy", "", "Click to reset!");
		put(c, 31, pane(DyeColor.GRAY), "???", "Requires HOTM 7!");
		put(c, 32, pane(DyeColor.GRAY), "???", "Requires HOTM 7!");
		return CleanUi.pageFor("Crystal Nucleus RNG Meter", menu(mc, c, "Crystal Nucleus RNG Meter"));
	}

	/** A confirmation opened from HOTM: becomes a dialog card. */
	private static Screen fakeHotmReset(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(27);
		put(c, 13, Items.TNT, "Reset Heart of the Mountain", "Resets every perk and refunds", "your Mithril, Gemstone and Glacite", "Powder.", "",
				"Your tokens are refunded too.");
		put(c, 11, pane(DyeColor.LIME), "Confirm", "Click to reset your tree!");
		put(c, 15, pane(DyeColor.RED), "Cancel", "Go back without resetting.");
		ChestMenu menu = ChestMenu.threeRows(4, mc.player.getInventory(), c);
		return new SubMenuScreen("Heart of the Mountain", new ItemStack(Items.AMETHYST_CLUSTER),
				new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Reset Heart of the Mountain")));
	}

	/** Any other menu opened from HOTM: buttons at their vanilla spots. */
	private static Screen fakeHotmSwap(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 20, Items.EMERALD_BLOCK, "Tree 1", "Your main mining tree.", "", "SELECTED");
		put(c, 22, Items.GOLD_BLOCK, "Tree 2", "A gemstone mining setup.", "", "Click to swap!");
		put(c, 24, Items.COAL_BLOCK, "Tree 3", "Unlocks at HOTM Tier 9.");
		put(c, 40, Items.BOOK, "How tree swapping works", "Swap between saved perk setups.");
		put(c, 48, Items.ARROW, "Go Back");
		return new SubMenuScreen("Heart of the Mountain", new ItemStack(Items.AMETHYST_CLUSTER), menu(mc, c, "Swap Tree"));
	}

	private static Screen fakeCalendar(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 48, Items.ARROW, "Go Back");
		return new CalendarScreen(menu(mc, c, CalendarScreen.MENU_TITLE));
	}

	private static ContainerScreen menu(Minecraft mc, SimpleContainer c, String title) {
		return new ContainerScreen(ChestMenu.sixRows(3, mc.player.getInventory(), c), mc.player.getInventory(), Component.literal(title));
	}

	/** Skills page over a fake menu with the same data as a real one seen on Hypixel. */
	private static Screen fakeSkills(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 19, Items.STONE_SWORD, "Combat XXVII", "Progress to Level XXVIII: 65.1%", "1,953,000/3M");
		put(c, 20, Items.GOLDEN_HOE, "Farming XI", "Progress to Level XII: 18.3%", "5,490/30k");
		put(c, 21, Items.FISHING_ROD, "Fishing XVI", "Progress to Level XVII: 34.3%", "68,600/200k");
		put(c, 22, Items.STONE_PICKAXE, "Mining LX", "Max Skill level reached!");
		put(c, 23, Items.JUNGLE_SAPLING, "Foraging XII", "Progress to Level XIII: 65.1%", "32,550/50k");
		put(c, 24, Items.LEAD, "Hunting XXII", "Progress to Level XXIII: 65.9%", "988,500/1.5M");
		put(c, 25, Items.ENCHANTING_TABLE, "Enchanting XXVIII", "Progress to Level XXIX: 9.6%", "345,600/3.6M");
		put(c, 29, Items.CRAFTING_TABLE, "Carpentry XXXIII", "Progress to Level XXXIV: 41.9%", "1,936,000/4.6M");
		put(c, 30, Items.BONE, "Taming XLVIII", "Level up pets to earn Taming EXP!", "", "Progress to Level XLIX: 56%", "2,070,294.5/3.7M");
		put(c, 31, Items.BREWING_STAND, "Alchemy IX", "Progress to Level X: 45%", "6,750/15k");
		put(c, 32, Items.EMERALD, "Social I", "Progress to Level II: 61.4%", "307/500");
		put(c, 33, Items.MAGMA_CREAM, "Runecrafting IX", "Progress to Level X: 93.6%", "9,360/10k");
		put(c, 48, Items.WITHER_SKELETON_SKULL, "Dungeoneering", "Complete Dungeons to level up your", "classes!", "", "Click to view!");
		put(c, 49, Items.BARRIER, "Close");
		ChestMenu menu = ChestMenu.sixRows(0, mc.player.getInventory(), c);
		return new SkillsScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal(SkillsScreen.MENU_TITLE)));
	}

	/** Storage page over a fake "Storage" menu, with a few pages already remembered. */
	private static Screen fakeStorage(Minecraft mc) {
		long now = System.currentTimeMillis();
		Item[] loot = {Items.DIAMOND, Items.IRON_SWORD, Items.ENCHANTED_BOOK, Items.GOLD_INGOT, Items.EMERALD_BLOCK,
				Items.BOW, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.PRISMARINE_SHARD, Items.NETHER_STAR, Items.COAL_BLOCK};
		StorageCache.putForPreview(new PageId(Kind.ENDER_CHEST, 1), fakePage(45, 38, loot, 0), now - 12 * 60_000);
		StorageCache.putForPreview(new PageId(Kind.ENDER_CHEST, 2), fakePage(45, 12, loot, 3), now - 3 * 3_600_000);
		StorageCache.putForPreview(new PageId(Kind.BACKPACK, 1), fakePage(45, 27, loot, 5), now - 2 * 86_400_000);
		return storageScreen(mc);
	}

	private static Screen storageScreen(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		for (int i = 1; i <= 9; i++)
			if (i <= 5) put(c, 8 + i, Items.ENDER_CHEST, "Ender Chest Page " + i, "Click to open!");
			else put(c, 8 + i, Items.GLASS_PANE, "Locked Page", "Unlock more pages!");
		for (int i = 1; i <= 9; i++)
			if (i <= 7) put(c, 26 + i, Items.PLAYER_HEAD, "Backpack Slot " + i, "Jumbo Backpack", "Click to open!");
			else put(c, 26 + i, Items.GLASS_PANE, "Empty Backpack Slot " + i);
		put(c, 49, Items.BARRIER, "Close");
		ChestMenu menu = ChestMenu.sixRows(1, mc.player.getInventory(), c);
		return new StorageScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal(StorageParser.MENU_TITLE)));
	}

	/** A Hypixel-style "Ender Chest (3/9)" page: navigation row on top, then 45 item slots. */
	private static Screen fakeEnderChestPage(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		for (int i = 0; i < 9; i++) put(c, i, Items.GLASS_PANE, " ");
		put(c, 0, Items.ARROW, "Previous Page");
		put(c, 8, Items.ARROW, "Next Page");
		Item[] loot = {Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ, Items.SLIME_BALL};
		for (int i = 9; i < 54; i++) c.setItem(i, new ItemStack(loot[i % loot.length], i));
		ChestMenu menu = ChestMenu.sixRows(2, mc.player.getInventory(), c);
		return new ContainerScreen(menu, mc.player.getInventory(), Component.literal("Ender Chest (3/9)"));
	}

	private static Screen storageAfterReload(Minecraft mc) {
		StorageCache.reloadForPreview();
		StorageScreen.selectForPreview(new PageId(Kind.ENDER_CHEST, 3));
		return storageScreen(mc);
	}

	private static List<ItemStack> fakePage(int size, int filled, Item[] loot, int seed) {
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			boolean has = (i * 7 + seed) % size < filled;
			items.add(has ? new ItemStack(loot[(i + seed) % loot.length], 1 + (i * 13 + seed) % 64 % (i % 3 == 0 ? 1 : 64)) : ItemStack.EMPTY);
		}
		return items;
	}

	private static void put(SimpleContainer c, int slot, Item item, String name, String... lore) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		s.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore).map(l -> (Component) Component.literal(l)).toList()));
		c.setItem(slot, s);
	}
}
