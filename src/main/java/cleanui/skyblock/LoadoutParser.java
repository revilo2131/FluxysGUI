package cleanui.skyblock;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a loadout item from Hypixel's "Loadouts" menu: "Helmet: Necrotic Wise Dragon Helmet",
 * "Pet: [Lvl 96] Armadillo", and whether it is the one in use (it has no "Left-click to equip!").
 */
public final class LoadoutParser {
	/** Armor names in order helmet, chestplate, leggings, boots; null where "None". */
	public record Loadout(String[] armor, String pet, boolean active, boolean empty) {}

	private static final String[] PIECES = {"Helmet:", "Chestplate:", "Leggings:", "Boots:"};

	private LoadoutParser() {}

	/** Null unless the lore lists armor pieces (so the menu's other items aren't loadouts). */
	public static Loadout parse(List<String> lore) {
		String[] armor = new String[4];
		String pet = null;
		boolean listed = false, equip = false, edit = false, mustCustomize = false;
		for (String raw : lore) {
			String line = raw.strip();
			for (int p = 0; p < 4; p++)
				if (line.startsWith(PIECES[p])) {
					listed = true;
					String v = line.substring(PIECES[p].length()).strip();
					armor[p] = v.equals("None") ? null : v;
				}
			if (line.startsWith("Pet:")) {
				String v = line.substring(4).strip();
				pet = v.equals("None") ? null : v;
			}
			if (line.startsWith("Left-click to equip")) equip = true;
			if (line.startsWith("Right-click to edit")) edit = true;
			if (line.startsWith("You must customize")) mustCustomize = true;
		}
		if (!listed) return null;
		boolean empty = mustCustomize || armor[0] == null && armor[1] == null && armor[2] == null && armor[3] == null && pet == null;
		// the loadout in use is the only one you can edit but not equip
		return new Loadout(armor, pet, edit && !equip && !empty, empty);
	}

	/**
	 * Names to look an item up by, best first: the full name, then with leading reforge words
	 * dropped ("Necrotic Wise Dragon Helmet" → "Wise Dragon Helmet", "Jaded Helmet of Divan" →
	 * "Helmet of Divan"). Stars and symbols at the ends are removed.
	 */
	public static List<String> candidates(String display) {
		String s = display.replaceAll("[^A-Za-z0-9' ()-]", " ").replaceAll("\\s+", " ").strip();
		List<String> out = new ArrayList<>();
		String[] words = s.split(" ");
		for (int drop = 0; drop < Math.min(3, words.length); drop++)
			out.add(String.join(" ", java.util.Arrays.copyOfRange(words, drop, words.length)));
		return out;
	}
}
