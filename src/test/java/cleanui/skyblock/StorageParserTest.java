package cleanui.skyblock;

import cleanui.skyblock.StorageParser.Kind;
import cleanui.skyblock.StorageParser.PageId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageParserTest {
	@Test
	void enderChestMenuItem() {
		var e = StorageParser.menuItem("Ender Chest Page 3", List.of("Click to open!"));
		assertEquals(new PageId(Kind.ENDER_CHEST, 3), e.id());
		assertFalse(e.empty());
	}

	@Test
	void backpackMenuItems() {
		assertEquals(new PageId(Kind.BACKPACK, 2), StorageParser.menuItem("Backpack Slot 2", List.of()).id());
		assertTrue(StorageParser.menuItem("Empty Backpack Slot 5", List.of()).empty());
		// named after the backpack, slot only in the lore
		assertEquals(new PageId(Kind.BACKPACK, 7), StorageParser.menuItem("Jumbo Backpack", List.of("Slot #7", "Click to open!")).id());
	}

	@Test
	void otherMenuItemsAreIgnored() {
		assertNull(StorageParser.menuItem("Locked Page", List.of("Unlock more pages...")));
		assertNull(StorageParser.menuItem("Go Back", List.of()));
		assertNull(StorageParser.menuItem("Ender Chest", List.of("Store items in your Ender Chest!")));
	}

	@Test
	void pageTitles() {
		assertEquals(new PageId(Kind.ENDER_CHEST, 2), StorageParser.pageTitle("Ender Chest (2/9)"));
		assertEquals(new PageId(Kind.BACKPACK, 4), StorageParser.pageTitle("Jumbo Backpack (Slot #4)"));
		assertNull(StorageParser.pageTitle("Storage"));
		assertNull(StorageParser.pageTitle("Your Skills"));
	}

	@Test
	void decorations() {
		assertTrue(StorageParser.isDecoration(" ", "minecraft:black_stained_glass_pane"));
		assertTrue(StorageParser.isDecoration("Next Page", "minecraft:arrow"));
		assertFalse(StorageParser.isDecoration("Hyperion", "minecraft:iron_sword"));
		assertFalse(StorageParser.isDecoration("Fine Jade Gemstone", "minecraft:lime_stained_glass_pane"));
	}

	@Test
	void keysAndLabels() {
		assertEquals("ec3", new PageId(Kind.ENDER_CHEST, 3).key());
		assertEquals("Backpack 12", new PageId(Kind.BACKPACK, 12).label());
	}
}
