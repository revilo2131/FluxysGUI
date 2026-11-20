package cleanui.skyblock;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes Hypixel's storage: entries in the "Storage" menu, and the titles of the
 * ender chest pages and backpacks it opens. Names are formatting-stripped plain text.
 * Lenient: anything unrecognized returns null and the caller logs it.
 */
public final class StorageParser {
	public static final String MENU_TITLE = "Storage";

	public enum Kind { ENDER_CHEST, BACKPACK }

	/** One storage page: ender chest page N or backpack slot N. */
	public record PageId(Kind kind, int number) {
		public String key() {
			return (kind == Kind.ENDER_CHEST ? "ec" : "bp") + number;
		}

		public String label() {
			return (kind == Kind.ENDER_CHEST ? "Ender Chest " : "Backpack ") + number;
		}
	}

	/** A menu item that opens a page; {@code empty} = a backpack slot with no backpack in it. */
	public record MenuEntry(PageId id, boolean empty) {}

	private static final Pattern EC_ITEM = Pattern.compile("^Ender Chest Page (\\d+)$");
	private static final Pattern BP_ITEM = Pattern.compile("^(Empty )?Backpack Slot (\\d+)$");
	private static final Pattern SLOT_LORE = Pattern.compile("Slot #?(\\d+)");
	private static final Pattern EC_TITLE = Pattern.compile("^Ender Chest \\((\\d+)/\\d+\\)$");
	private static final Pattern BP_TITLE = Pattern.compile("Backpack.*\\(Slot #?(\\d+)\\)$");
	private static final Set<String> NAV = Set.of("Next Page", "Previous Page", "Go Back", "Close", "Back");

	private StorageParser() {}

	public static MenuEntry menuItem(String name, List<String> lore) {
		String n = name.strip();
		Matcher m = EC_ITEM.matcher(n);
		if (m.matches()) return new MenuEntry(new PageId(Kind.ENDER_CHEST, Integer.parseInt(m.group(1))), false);
		m = BP_ITEM.matcher(n);
		if (m.matches()) return new MenuEntry(new PageId(Kind.BACKPACK, Integer.parseInt(m.group(2))), m.group(1) != null);
		if (n.contains("Backpack") && !n.startsWith("Empty"))
			for (String line : lore) {
				Matcher s = SLOT_LORE.matcher(line);
				if (s.find()) return new MenuEntry(new PageId(Kind.BACKPACK, Integer.parseInt(s.group(1))), false);
			}
		return null;
	}

	/** Which page a container title belongs to, e.g. "Ender Chest (2/9)", or null. */
	public static PageId pageTitle(String title) {
		String t = title.strip();
		Matcher m = EC_TITLE.matcher(t);
		if (m.matches()) return new PageId(Kind.ENDER_CHEST, Integer.parseInt(m.group(1)));
		m = BP_TITLE.matcher(t);
		if (m.find()) return new PageId(Kind.BACKPACK, Integer.parseInt(m.group(1)));
		return null;
	}

	/** Decoration, not a stored item: blank-named glass panes and page navigation buttons. */
	public static boolean isDecoration(String name, String itemId) {
		String n = name.strip();
		return (itemId.endsWith("glass_pane") && n.isEmpty()) || NAV.contains(n);
	}
}
