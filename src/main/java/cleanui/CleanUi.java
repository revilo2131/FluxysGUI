package cleanui;

import cleanui.feature.HubScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
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
	}
}
