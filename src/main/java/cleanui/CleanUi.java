package cleanui;

import cleanui.data.StorageCache;
import cleanui.dev.Preview;
import cleanui.feature.CalendarScreen;
import cleanui.feature.DungeonScreen;
import cleanui.feature.LevelingScreen;
import cleanui.feature.ListPage;
import cleanui.feature.RngMeterScreen;
import cleanui.feature.TrackScreen;
import cleanui.feature.TreeScreen;
import cleanui.feature.SubMenuScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import cleanui.data.Config;
import cleanui.ui.Theme;
import cleanui.feature.MenuPage;
import cleanui.feature.OverviewScreen;
import cleanui.feature.SkillsScreen;
import cleanui.feature.StorageScreen;
import cleanui.skyblock.StorageParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class CleanUi implements ClientModInitializer {
	public static final String ID = "cleanui";
	private static final Logger LOGGER = LoggerFactory.getLogger(ID);

	/** Hypixel menus (exact title) that are shown as a Clean UI page, each with its on/off setting. */
	private record Page(Predicate<Config> enabled, Function<AbstractContainerScreen<?>, Screen> open) {}

	private static final Map<String, Page> MENU_PAGES = Map.of(
			OverviewScreen.MENU_TITLE, new Page(c -> c.skyblockMenu, OverviewScreen::new),
			SkillsScreen.MENU_TITLE, new Page(c -> c.skills, SkillsScreen::new),
			StorageParser.MENU_TITLE, new Page(c -> c.storage, StorageScreen::new),
			CalendarScreen.MENU_TITLE, new Page(c -> c.calendar, CalendarScreen::new));
	private static final Set<String> SEEN_TITLES = new HashSet<>();

	/** The Clean UI page for a Hypixel menu title (respecting settings), or null to leave it. */
	public static Screen pageFor(String title, AbstractContainerScreen<?> menu) {
		Page exact = MENU_PAGES.get(title);
		if (exact != null) return exact.enabled().test(Config.get()) ? exact.open().apply(menu) : null;
		Screen rng = RngMeterScreen.forTitle(title, menu);
		if (rng != null) return rng;
		Screen leveling = LevelingScreen.forTitle(title, menu);
		if (leveling != null) return leveling;
		Screen dungeon = DungeonScreen.forTitle(title, menu);
		if (dungeon != null) return dungeon;
		Screen tree = TreeScreen.forTitle(title, menu);
		if (tree != null) return tree;
		Screen track = TrackScreen.forTitle(title, menu);
		return track != null ? track : ListPage.forTitle(title, menu);
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
		KeyMapping open = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.cleanui.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick()) {
				// same as clicking the SkyBlock Menu star: one /sbmenu, shown as the Clean UI overview
				if (onHypixel()) client.getConnection().sendCommand("sbmenu");
				else if (client.player != null)
					client.player.sendSystemMessage(Component.literal("[FluxysGUI] Open the SkyBlock Menu on Hypixel SkyBlock."));
			}
		});
		Theme.applyPreset(Config.get().theme);
		StorageCache.init();
		Preview.init(); // dev only; does nothing unless -Dcleanui.preview=true

		// Hypixel menus are swapped for their Clean UI page in GuiMixin, before they are shown.
	}

	/** Called for every screen Minecraft is about to show: a Hypixel menu with a Clean UI page becomes that page. */
	public static Screen swap(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> menu) || screen == MenuPage.showingOriginal || !onHypixel()) return screen;
		String title = ChatFormatting.stripFormatting(screen.getTitle().getString());
		Screen page = pageFor(title, menu);
		if (page != null) return page;
		// a menu opened from the HOTM page (Reset, Swap, ...) becomes a HOTM sub-page, whatever its title
		Screen from = Minecraft.getInstance().gui.screen();
		if (Config.get().hotm && from instanceof TreeScreen tree)
			return new SubMenuScreen(tree.getTitle().getString(), new ItemStack(Items.AMETHYST_CLUSTER), menu);
		if (from instanceof SubMenuScreen sub)
			return new SubMenuScreen(sub.getTitle().getString(), new ItemStack(Items.AMETHYST_CLUSTER), menu);
		if (SEEN_TITLES.add(title)) LOGGER.info("[Clean UI] Menu without a Clean UI page: '{}'", title);
		return screen;
	}

	public static boolean onHypixel() {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		return server != null && server.ip.toLowerCase().contains("hypixel.net");
	}
}
