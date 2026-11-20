package cleanui.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Estimates a SkyBlock profile's networth from Hypixel's profile data and a price map
 * (item id → coins, pets as "LVL_1_LEGENDARY_GOLDEN_DRAGON"). Items count at their base
 * price only: reforges, enchantments, gems and the like aren't added, so this is a floor.
 */
public final class NetworthCalc {
	/** Total plus a breakdown in display order (category → coins). */
	public record Result(double total, Map<String, Double> parts) {}

	/** Pet level 100 needs this much xp at legendary; past it, the pet counts at its LVL_100 price. */
	private static final double PET_MAX_XP = 25_353_230;

	private NetworthCalc() {}

	public static Result compute(JsonObject profile, String memberUuid, Map<String, Double> prices) {
		JsonObject member = obj(obj(profile, "members"), memberUuid.replace("-", ""));
		JsonObject inv = obj(member, "inventory");
		Map<String, Double> parts = new LinkedHashMap<>();
		parts.put("Purse", num(obj(member, "currencies"), "coin_purse"));
		parts.put("Bank", num(obj(profile, "banking"), "balance"));
		parts.put("Inventory", items(data(inv, "inv_contents"), prices));
		parts.put("Armor", items(data(inv, "inv_armor"), prices));
		parts.put("Equipment", items(data(inv, "equipment_contents"), prices));
		parts.put("Wardrobe", items(data(inv, "wardrobe_contents"), prices));
		parts.put("Ender Chest", items(data(inv, "ender_chest_contents"), prices));
		double backpacks = 0;
		for (Map.Entry<String, JsonElement> e : obj(inv, "backpack_contents").entrySet())
			if (e.getValue().isJsonObject()) backpacks += items(str(e.getValue().getAsJsonObject(), "data"), prices);
		parts.put("Backpacks", backpacks);
		parts.put("Accessories", items(data(obj(inv, "bag_contents"), "talisman_bag"), prices));
		parts.put("Personal Vault", items(data(inv, "personal_vault_contents"), prices));
		double sacks = 0;
		for (Map.Entry<String, JsonElement> e : obj(inv, "sacks_counts").entrySet())
			sacks += e.getValue().getAsDouble() * prices.getOrDefault(e.getKey(), 0.0);
		parts.put("Sacks", sacks);
		parts.put("Pets", pets(member, prices));
		parts.values().removeIf(v -> v <= 0);
		double total = 0;
		for (double v : parts.values()) total += v;
		return new Result(total, parts);
	}

	/** Base price of every item in a Hypixel base64 + gzip NBT inventory blob. */
	static double items(String base64, Map<String, Double> prices) {
		if (base64 == null || base64.isEmpty()) return 0;
		try {
			CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(base64)), NbtAccounter.unlimitedHeap());
			ListTag list = root.getListOrEmpty("i");
			double sum = 0;
			for (int i = 0; i < list.size(); i++) {
				CompoundTag item = list.getCompoundOrEmpty(i);
				String id = item.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes").getStringOr("id", "");
				if (!id.isEmpty()) sum += prices.getOrDefault(id, 0.0) * Math.max(1, item.getByteOr("Count", (byte) 1));
			}
			return sum;
		} catch (Exception e) {
			return 0; // one unreadable inventory shouldn't sink the whole estimate
		}
	}

	private static double pets(JsonObject member, Map<String, Double> prices) {
		JsonElement list = obj(member, "pets_data").get("pets");
		if (list == null || !list.isJsonArray()) return 0;
		double sum = 0;
		for (JsonElement el : list.getAsJsonArray()) {
			JsonObject pet = el.getAsJsonObject();
			String key = pet.get("tier").getAsString() + "_" + pet.get("type").getAsString();
			boolean max = num(pet, "exp") >= PET_MAX_XP;
			Double price = prices.get((max ? "LVL_100_" : "LVL_1_") + key);
			if (price == null) price = prices.getOrDefault("LVL_1_" + key, 0.0);
			sum += price;
		}
		return sum;
	}

	private static JsonObject obj(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	private static String data(JsonObject o, String key) {
		return str(obj(o, key), "data");
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static double num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0;
	}
}
