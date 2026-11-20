package cleanui.data;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NetworthCalcTest {
	/** An inventory blob in Hypixel's format: base64(gzip(NBT {i: [{Count, tag: {ExtraAttributes: {id}}}]})). */
	static String blob(Object... idCount) throws Exception {
		ListTag list = new ListTag();
		for (int i = 0; i < idCount.length; i += 2) {
			CompoundTag extra = new CompoundTag();
			extra.putString("id", (String) idCount[i]);
			CompoundTag tag = new CompoundTag();
			tag.put("ExtraAttributes", extra);
			CompoundTag item = new CompoundTag();
			item.putByte("Count", (byte) (int) idCount[i + 1]);
			item.put("tag", tag);
			list.add(item);
		}
		list.add(new CompoundTag()); // empty slot
		CompoundTag root = new CompoundTag();
		root.put("i", list);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		return Base64.getEncoder().encodeToString(out.toByteArray());
	}

	@Test
	void sumsEverySource() throws Exception {
		String uuid = "0123456789abcdef0123456789abcdef";
		String json = """
				{"banking": {"balance": 1000000},
				 "members": {"%s": {
				   "currencies": {"coin_purse": 250000},
				   "inventory": {
				     "inv_contents": {"data": "%s"},
				     "inv_armor": {"data": "%s"},
				     "backpack_contents": {"0": {"data": "%s"}},
				     "sacks_counts": {"ENCHANTED_DIAMOND": 10}
				   },
				   "pets_data": {"pets": [
				     {"type": "GOLDEN_DRAGON", "tier": "LEGENDARY", "exp": 30000000},
				     {"type": "BEE", "tier": "RARE", "exp": 100}
				   ]}
				 }}}
				""".formatted(uuid, blob("HYPERION", 1), blob("NECRON_CHESTPLATE", 1), blob("ENCHANTED_DIAMOND", 64));
		Map<String, Double> prices = Map.of("HYPERION", 500e6, "NECRON_CHESTPLATE", 100e6, "ENCHANTED_DIAMOND", 1000.0,
				"LVL_100_LEGENDARY_GOLDEN_DRAGON", 650e6, "LVL_1_RARE_BEE", 5000.0);

		var r = NetworthCalc.compute(JsonParser.parseString(json).getAsJsonObject(), uuid, prices);
		assertEquals(250_000, r.parts().get("Purse"));
		assertEquals(1_000_000, r.parts().get("Bank"));
		assertEquals(500e6, r.parts().get("Inventory"));
		assertEquals(100e6, r.parts().get("Armor"));
		assertEquals(64_000, r.parts().get("Backpacks"));
		assertEquals(10_000, r.parts().get("Sacks"));
		assertEquals(650e6 + 5000, r.parts().get("Pets"));
		assertFalse(r.parts().containsKey("Wardrobe")); // empty parts are left out
		assertEquals(250_000 + 1_000_000 + 500e6 + 100e6 + 64_000 + 10_000 + 650e6 + 5000, r.total(), 1e-3);
	}

	@Test
	void badDataCountsAsZero() {
		assertEquals(0, NetworthCalc.items("not base64!", Map.of()));
		assertEquals(0, NetworthCalc.items(null, Map.of()));
	}
}
