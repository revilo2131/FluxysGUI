package cleanui.skyblock;

import java.util.Locale;
import java.util.Map;

/**
 * Vanilla item id for a SkyBlock collection name, so collections not found yet can still
 * show their real item instead of Hypixel's placeholder. Most names map directly
 * ("Nether Wart" → nether_wart); the aliases cover the ones that don't.
 */
public final class CollectionItems {
	private static final Map<String, String> ALIASES = Map.ofEntries(
			Map.entry("Melon", "melon_slice"), Map.entry("Seeds", "wheat_seeds"), Map.entry("Mushroom", "red_mushroom"),
			Map.entry("Raw Porkchop", "porkchop"), Map.entry("Raw Chicken", "chicken"), Map.entry("Raw Rabbit", "rabbit"),
			Map.entry("Raw Beef", "beef"), Map.entry("Nether Quartz", "quartz"), Map.entry("Mithril", "prismarine_crystals"),
			Map.entry("Hard Stone", "stone"), Map.entry("Slimeball", "slime_ball"), Map.entry("Gemstone", "amethyst_shard"),
			Map.entry("Glacite", "packed_ice"), Map.entry("Umber", "terracotta"), Map.entry("Tungsten", "clay"),
			Map.entry("Oak Wood", "oak_log"), Map.entry("Spruce Wood", "spruce_log"), Map.entry("Birch Wood", "birch_log"),
			Map.entry("Dark Oak Wood", "dark_oak_log"), Map.entry("Acacia Wood", "acacia_log"), Map.entry("Jungle Wood", "jungle_log"),
			Map.entry("Mangrove Wood", "mangrove_log"), Map.entry("Raw Fish", "cod"), Map.entry("Raw Salmon", "salmon"),
			Map.entry("Clownfish", "tropical_fish"), Map.entry("Wild Rose", "rose_bush"), Map.entry("Moonflower", "blue_orchid"),
			Map.entry("Wilted Berberis", "dead_bush"), Map.entry("Half-Eaten Carrot", "carrot"), Map.entry("Agaricus Cap", "red_mushroom"));

	private CollectionItems() {}

	/** Item id path to try, e.g. "Nether Wart" → "nether_wart"; aliases first. */
	public static String idFor(String name) {
		String alias = ALIASES.get(name);
		if (alias != null) return alias;
		return name.toLowerCase(Locale.ROOT).replace("'", "").replace('-', '_').replace(' ', '_');
	}
}
