package cleanui.feature;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A client-only mannequin wearing a mayor's skin, drawn in the Calendar like the player model.
 * It is never added to the world or ticked, so it looks its skin up itself.
 */
final class MayorModel extends ClientMannequin {
	final String skin;
	private final Supplier<PlayerSkin> lookup;

	MayorModel(Level level, String skin) {
		super(level, Minecraft.getInstance().playerSkinRenderCache());
		this.skin = skin;
		setId(-4242); // rendering reads the id
		PropertyMap props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", skin)));
		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(skin.getBytes(StandardCharsets.UTF_8)), "Mayor", props);
		var future = Minecraft.getInstance().getSkinManager().get(profile);
		lookup = () -> future.getNow(Optional.empty()).orElse(null);
	}

	@Override
	public PlayerSkin getSkin() {
		PlayerSkin s = lookup.get();
		return s != null ? s : super.getSkin(); // default skin until the download finishes
	}
}
