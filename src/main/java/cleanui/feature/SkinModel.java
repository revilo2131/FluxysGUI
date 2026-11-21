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
 * A client-only mannequin with a given skin (the mayor in the Calendar, you in the Wardrobe),
 * drawn like the inventory player model. It is never added to the world or ticked, so it looks its
 * skin up itself.
 */
final class SkinModel extends ClientMannequin {
	/** The textures value it was made from (null when made from a profile). */
	final String skin;
	private final Supplier<PlayerSkin> lookup;

	/** From a base64 "textures" value (e.g. an NPC head's). */
	SkinModel(Level level, String skin) {
		this(level, new GameProfile(UUID.nameUUIDFromBytes(skin.getBytes(StandardCharsets.UTF_8)), "Model",
				new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", skin)))), skin);
	}

	/** From a real profile, e.g. your own (so it wears your skin). */
	SkinModel(Level level, GameProfile profile) {
		this(level, profile, null);
	}

	private SkinModel(Level level, GameProfile profile, String skin) {
		super(level, Minecraft.getInstance().playerSkinRenderCache());
		this.skin = skin;
		setId(-4242); // rendering reads the id
		var future = Minecraft.getInstance().getSkinManager().get(profile);
		lookup = () -> future.getNow(Optional.empty()).orElse(null);
	}

	@Override
	public PlayerSkin getSkin() {
		PlayerSkin s = lookup.get();
		return s != null ? s : super.getSkin(); // default skin until the download finishes
	}
}
