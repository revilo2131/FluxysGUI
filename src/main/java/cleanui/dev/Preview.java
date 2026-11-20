package cleanui.dev;

import cleanui.data.StorageCache;
import cleanui.CleanUi;
import cleanui.feature.CalendarScreen;
import cleanui.feature.SettingsScreen;
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
			new Shot("track-collection", Preview::fakeCollectionTrack, -1, -1),
			new Shot("bestiary", Preview::fakeBestiary, -1, -1),
			new Shot("bestiary-hub", Preview::fakeBestiaryHub, -1, -1),
			new Shot("pets", Preview::fakePets, -1, -1),
			new Shot("settings", mc -> new SettingsScreen(fakeSkyBlockMenu(mc)), -1, -1),
			new Shot("restyled", Preview::fakeWardrobe, -1, -1),
			new Shot("hotm", Preview::fakeHotm, -1, -1),
			new Shot("hotm-hover", Preview::fakeHotm, 470, 355));
	private static final int START = 80, EACH = 40;
	/** -Dcleanui.preview.only=a,b runs just those shots. */
	private static final List<Shot> RUN = SHOTS.stream()
			.filter(s -> System.getProperty("cleanui.preview.only", "").isEmpty()
					|| List.of(System.getProperty("cleanui.preview.only").split(",")).contains(s.name())).toList();
	private static int ticks;

	private Preview() {}

	public static void init() {
		if (Boolean.getBoolean("cleanui.preview")) ClientTickEvents.END_CLIENT_TICK.register(Preview::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null) return;
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
			mc.setScreenAndShow(s.screen().apply(mc));
		}
		if (phase == EACH - 5)
			Screenshot.grab(mc.gameDirectory, "preview-" + s.name() + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
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
		Object[][] pets = {{Items.PLAYER_HEAD, "[Lvl 100] Golden Dragon", "MAX LEVEL"},
				{Items.PLAYER_HEAD, "[Lvl 87] Elephant", "Progress to Level 88: 41.2%"},
				{Items.PLAYER_HEAD, "[Lvl 100] Ender Dragon", "MAX LEVEL"},
				{Items.PLAYER_HEAD, "[Lvl 64] Bee", "Progress to Level 65: 12%"},
				{Items.PLAYER_HEAD, "[Lvl 99] Blue Whale", "Progress to Level 100: 93%"},
				{Items.PLAYER_HEAD, "[Lvl 45] Rabbit", "Progress to Level 46: 30%"},
				{Items.PLAYER_HEAD, "[Lvl 100] Griffin", "MAX LEVEL"},
				{Items.PLAYER_HEAD, "[Lvl 72] Mithril Golem", "Progress to Level 73: 66%"},
				{Items.PLAYER_HEAD, "[Lvl 30] Sheep", "Progress to Level 31: 8%"},
				{Items.PLAYER_HEAD, "[Lvl 100] Scatha", "MAX LEVEL"},
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
		for (int s = 0; s < 9; s++) put(c, 36 + s, pane(s < 3 ? DyeColor.LIME : DyeColor.GRAY), "Slot " + (s + 1), s < 3 ? "Click to equip!" : "Empty");
		put(c, 48, Items.ARROW, "Go Back");
		put(c, 49, Items.BARRIER, "Close");
		return menu(mc, c, "Wardrobe (1/2)");
	}

	/** Heart of the Mountain, tiers 7 (top) to 2: tier labels in column 0, perks in a tree, scroll arrows. */
	private static Screen fakeHotm(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		for (int row = 0; row < 6; row++) put(c, row * 9, Items.GLASS_PANE, "Tier " + (7 - row), "Tier " + (7 - row) + " perks");
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
		put(c, 17, Items.AMETHYST_CLUSTER, "Heart of the Mountain", "Token of the Mountain: 3", "Mithril Powder: 1.2M");
		return CleanUi.pageFor("Heart of the Mountain", menu(mc, c, "Heart of the Mountain"));
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
