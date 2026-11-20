package cleanui;

import cleanui.feature.HubScreen;
import cleanui.feature.SkillsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class CleanUi implements ClientModInitializer {
	public static final String ID = "cleanui";

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
		KeyMapping open = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.cleanui.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick()) client.setScreenAndShow(new HubScreen());
		});

		// Show our Skills screen in place of Hypixel's "Your Skills" menu (only on Hypixel).
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?> menu && screen != SkillsScreen.showingOriginal && onHypixel()
					&& SkillsScreen.MENU_TITLE.equals(ChatFormatting.stripFormatting(screen.getTitle().getString())))
				client.execute(() -> client.setScreenAndShow(new SkillsScreen(menu))); // after init finishes
		});
	}

	public static boolean onHypixel() {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		return server != null && server.ip.toLowerCase().contains("hypixel.net");
	}
}
