package cleanui.data;

import cleanui.skyblock.LoadoutParser;
import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkyBlock items by name, from Hypixel's public item list (no key, nothing about the player sent),
 * so a name like "Necrotic Wise Dragon Helmet" can be shown as the real thing: its head texture
 * or leather colour. Loaded once in the background on first use.
 */
public final class SkyblockItems {
	private static final String ITEMS = "https://api.hypixel.net/v2/resources/skyblock/items";

	/** Just what's needed to build the item: 1.8 material name, leather "r,g,b", head texture value. */
	private record Spec(String material, String color, String skin) {}

	private static volatile Map<String, Spec> specs;
	private static volatile boolean loading;
	private static final Map<String, ItemStack> BUILT = new ConcurrentHashMap<>();

	private SkyblockItems() {}

	/**
	 * The item for a display name (reforge words are skipped), or null while the list is loading
	 * or when the name isn't known. Safe to call every frame.
	 */
	public static ItemStack stack(String displayName) {
		Map<String, Spec> s = specs;
		if (s == null) {
			load();
			return null;
		}
		ItemStack cached = BUILT.get(displayName);
		if (cached != null) return cached.isEmpty() ? null : cached;
		ItemStack built = ItemStack.EMPTY;
		for (String name : LoadoutParser.candidates(displayName)) {
			Spec spec = s.get(name.toLowerCase(Locale.ROOT));
			if (spec != null) {
				built = build(spec);
				break;
			}
		}
		BUILT.put(displayName, built);
		return built.isEmpty() ? null : built;
	}

	private static synchronized void load() {
		if (loading || specs != null) return;
		loading = true;
		CompletableFuture.runAsync(() -> {
			try {
				HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
				HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(ITEMS)).timeout(Duration.ofSeconds(30)).GET().build(),
						HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() != 200) return;
				Map<String, Spec> map = new HashMap<>();
				for (JsonElement e : JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("items")) {
					JsonObject o = e.getAsJsonObject();
					if (!o.has("name") || !o.has("material")) continue;
					String skin = null;
					if (o.has("skin")) { // a JSON string: {"value": "...", "signature": "..."}
						JsonElement sk = o.get("skin");
						try {
							JsonObject so = sk.isJsonObject() ? sk.getAsJsonObject() : JsonParser.parseString(sk.getAsString()).getAsJsonObject();
							skin = so.get("value").getAsString();
						} catch (Exception ignored) {}
					}
					map.putIfAbsent(o.get("name").getAsString().toLowerCase(Locale.ROOT),
							new Spec(o.get("material").getAsString(), o.has("color") ? o.get("color").getAsString() : null, skin));
				}
				specs = map;
			} catch (Exception ignored) {
				// offline or Hypixel down: loadouts fall back to their icons
			} finally {
				loading = false;
			}
		});
	}

	private static ItemStack build(Spec spec) {
		String m = spec.material().toLowerCase(Locale.ROOT);
		if (m.equals("skull_item")) {
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			if (spec.skin() != null) {
				PropertyMap props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", spec.skin())));
				GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(spec.skin().getBytes(StandardCharsets.UTF_8)), "Item", props);
				head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
			}
			return head;
		}
		if (m.startsWith("gold_")) m = "golden_" + m.substring(5); // 1.8 names
		ItemStack stack = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(m)).map(ItemStack::new).orElse(ItemStack.EMPTY);
		if (!stack.isEmpty() && spec.color() != null && m.startsWith("leather_")) {
			String[] c = spec.color().split(",");
			try {
				stack.set(DataComponents.DYED_COLOR, new DyedItemColor(Integer.parseInt(c[0].strip()) << 16 | Integer.parseInt(c[1].strip()) << 8 | Integer.parseInt(c[2].strip())));
			} catch (Exception ignored) {}
		}
		return stack;
	}
}
