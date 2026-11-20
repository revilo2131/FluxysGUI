package cleanui.dev;

import cleanui.feature.CalendarScreen;
import cleanui.feature.OverviewScreen;
import cleanui.feature.SkillsScreen;
import cleanui.ui.CleanScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Dev only, off unless the game runs with -Dcleanui.preview=true (gradlew runPreview).
 * Opens each page in turn (Skills with a fake "Your Skills" menu), forces a mouse position
 * for hover states, saves a screenshot of each to run/screenshots, then quits.
 */
public final class Preview {
	private record Shot(String name, Function<Minecraft, Screen> screen, int mouseX, int mouseY) {}

	// mouse positions are GUI coords for a 960×540 GUI (1920×1080 at GUI scale 2)
	private static final List<Shot> SHOTS = List.of(
			new Shot("overview", mc -> new OverviewScreen(), -1, -1),
			new Shot("overview-gear", mc -> new OverviewScreen(), 285, 230),
			new Shot("calendar", mc -> new CalendarScreen(), 300, 250),
			new Shot("skills", Preview::fakeSkills, -1, -1),
			new Shot("skills-card", Preview::fakeSkills, 450, 290),
			new Shot("skills-row", Preview::fakeSkills, 600, 268));
	private static final int START = 80, EACH = 40;
	private static int ticks;

	private Preview() {}

	public static void init() {
		if (Boolean.getBoolean("cleanui.preview")) ClientTickEvents.END_CLIENT_TICK.register(Preview::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.player == null) return;
		if (++ticks == START - 10) {
			// some gear so the profile card has something to show (client-side only)
			mc.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
			mc.player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			mc.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		}
		int t = ticks - START;
		if (t < 0) return;
		int i = t / EACH, phase = t % EACH;
		if (i >= SHOTS.size()) {
			mc.stop();
			return;
		}
		Shot s = SHOTS.get(i);
		if (phase == 0) {
			CleanScreen.debugMouseX = s.mouseX();
			CleanScreen.debugMouseY = s.mouseY();
			mc.setScreenAndShow(s.screen().apply(mc));
		}
		if (phase == EACH - 5)
			Screenshot.grab(mc.gameDirectory, "preview-" + s.name() + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
	}

	/** Skills page over a fake menu with the same data as a real one seen on Hypixel. */
	private static Screen fakeSkills(Minecraft mc) {
		SimpleContainer c = new SimpleContainer(54);
		put(c, 19, Items.STONE_SWORD, "Combat XXVII", "Progress to Level XXVIII: 65.1%", "1,953,000/3M");
		put(c, 20, Items.GOLDEN_HOE, "Farming XI", "Progress to Level XII: 18.3%", "5,490/30k");
		put(c, 21, Items.FISHING_ROD, "Fishing XVI", "Progress to Level XVII: 34.3%", "68,600/200k");
		put(c, 22, Items.STONE_PICKAXE, "Mining LX", "Max Skill level reached!");
		put(c, 23, Items.JUNGLE_SAPLING, "Foraging XII", "Progress to Level XIII: 65.1%", "32,550/50k");
		put(c, 24, Items.LEAD, "Hunting XXII", "Progress to Level XXIII: 65.9%", "988,500/1.5M");
		put(c, 25, Items.ENCHANTING_TABLE, "Enchanting XXVIII", "Progress to Level XXIX: 9.6%", "345,600/3.6M");
		put(c, 29, Items.CRAFTING_TABLE, "Carpentry XXXIII", "Progress to Level XXXIV: 41.9%", "1,936,000/4.6M");
		put(c, 30, Items.BONE, "Taming XLVIII", "Level up pets to earn Taming EXP!", "", "Progress to Level XLIX: 56%", "2,070,294.5/3.7M");
		put(c, 31, Items.BREWING_STAND, "Alchemy IX", "Progress to Level X: 45%", "6,750/15k");
		put(c, 32, Items.EMERALD, "Social I", "Progress to Level II: 61.4%", "307/500");
		put(c, 33, Items.MAGMA_CREAM, "Runecrafting IX", "Progress to Level X: 93.6%", "9,360/10k");
		put(c, 48, Items.WITHER_SKELETON_SKULL, "Dungeoneering", "Complete Dungeons to level up your", "classes!", "", "Click to view!");
		put(c, 49, Items.BARRIER, "Close");
		ChestMenu menu = ChestMenu.sixRows(0, mc.player.getInventory(), c);
		return new SkillsScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal(SkillsScreen.MENU_TITLE)));
	}

	private static void put(SimpleContainer c, int slot, Item item, String name, String... lore) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		s.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore).map(l -> (Component) Component.literal(l)).toList()));
		c.setItem(slot, s);
	}
}
