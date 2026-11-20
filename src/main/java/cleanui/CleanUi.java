package cleanui;

import cleanui.data.StorageCache;
import cleanui.dev.Preview;
import cleanui.feature.MenuPage;
import cleanui.feature.OverviewScreen;
import cleanui.feature.SkillsScreen;
import cleanui.feature.StorageScreen;
import cleanui.skyblock.StorageParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.function.Function;

public final class CleanUi implements ClientModInitializer {
	public static final String ID = "cleanui";

	/** Hypixel menus (by title) that are shown as a Clean UI page instead. */
	private static final Map<String, Function<AbstractContainerScreen<?>, Screen>> MENU_PAGES = Map.of(
			SkillsScreen.MENU_TITLE, SkillsScreen::new,
			StorageParser.MENU_TITLE, StorageScreen::new);

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
		KeyMapping open = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.cleanui.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick()) client.setScreenAndShow(new OverviewScreen());
		});
		StorageCache.init();
		Preview.init(); // dev only; does nothing unless -Dcleanui.preview=true

		// Show a Clean UI page in place of certain Hypixel menus (only on Hypixel).
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> menu) || screen == MenuPage.showingOriginal || !onHypixel()) return;
			var page = MENU_PAGES.get(ChatFormatting.stripFormatting(screen.getTitle().getString()));
			if (page != null) client.execute(() -> client.setScreenAndShow(page.apply(menu))); // after init finishes
		});
	}

	public static boolean onHypixel() {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		return server != null && server.ip.toLowerCase().contains("hypixel.net");
	}
}
