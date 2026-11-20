package cleanui.data;

import cleanui.CleanUi;
import cleanui.skyblock.StorageParser;
import cleanui.skyblock.StorageParser.PageId;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembers the contents of each ender chest page and backpack the player opens, so the
 * Storage page can show them later. Read-only: it copies what's on screen and never
 * opens or clicks anything. Saved per SkyBlock profile under config/cleanui/.
 */
public final class StorageCache {
	public record Page(List<ItemStack> items, long seenAt) {}

	private static final Logger LOGGER = LoggerFactory.getLogger("cleanui");
	private static final Pattern PROFILE_ID = Pattern.compile("Profile ID: ([0-9a-fA-F-]{32,36})");
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(CleanUi.ID);

	private static final Map<String, Page> pages = new HashMap<>();
	private static String profileId; // from Hypixel's "Profile ID: ..." join message
	private static String loadedKey;
	private static PageId openPage; // page container on screen right now, if any
	private static boolean dirty;

	private StorageCache() {}

	public static void init() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			Matcher m = PROFILE_ID.matcher(ChatFormatting.stripFormatting(message.getString()));
			if (m.find()) profileId = m.group(1);
		});
		ClientTickEvents.END_CLIENT_TICK.register(StorageCache::tick);
	}

	/** Cached page, or null if the player hasn't opened it yet (on this profile). */
	public static Page get(PageId id) {
		ensureLoaded(Minecraft.getInstance());
		return pages.get(id.key());
	}

	/** Dev preview only: fill the cache with fake pages. */
	public static void putForPreview(PageId id, List<ItemStack> items, long seenAt) {
		ensureLoaded(Minecraft.getInstance());
		pages.put(id.key(), new Page(items, seenAt));
	}

	/** Dev preview only: drop the in-memory cache and read it back from disk. */
	public static void reloadForPreview() {
		Minecraft mc = Minecraft.getInstance();
		if (dirty) save(mc); // what a page close would do
		loadedKey = null;
		ensureLoaded(mc);
	}

	private static void tick(Minecraft mc) {
		PageId now = null;
		if (mc.player != null && mc.gui.screen() instanceof AbstractContainerScreen<?> screen)
			now = StorageParser.pageTitle(ChatFormatting.stripFormatting(screen.getTitle().getString()));

		if (now != null) {
			ensureLoaded(mc);
			if (mc.player.tickCount % 5 == 0) capture(mc, (AbstractContainerScreen<?>) mc.gui.screen(), now);
		}
		if (openPage != null && !openPage.equals(now) && dirty) save(mc); // page just closed or changed
		openPage = now;
	}

	/** Copies the page's item slots. Drops the top row when it's only navigation. */
	private static void capture(Minecraft mc, AbstractContainerScreen<?> screen, PageId id) {
		List<ItemStack> items = new ArrayList<>();
		for (Slot slot : screen.getMenu().slots)
			if (slot.container != mc.player.getInventory()) items.add(slot.getItem().copy());
		if (items.size() > 9 && items.subList(0, 9).stream().allMatch(s -> s.isEmpty() || isDecoration(s)))
			items = new ArrayList<>(items.subList(9, items.size()));
		items.replaceAll(s -> isDecoration(s) ? ItemStack.EMPTY : s);
		pages.put(id.key(), new Page(items, System.currentTimeMillis()));
		dirty = true;
	}

	private static boolean isDecoration(ItemStack s) {
		return StorageParser.isDecoration(ChatFormatting.stripFormatting(s.getHoverName().getString()),
				BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
	}

	private static String key(Minecraft mc) {
		if (profileId != null) return profileId;
		return mc.player != null ? mc.player.getUUID().toString() : null;
	}

	private static void ensureLoaded(Minecraft mc) {
		String key = key(mc);
		if (key == null || key.equals(loadedKey) || mc.level == null) return;
		if (dirty) save(mc);
		pages.clear();
		loadedKey = key;
		Path file = file(key);
		if (!Files.exists(file)) return;
		try {
			RegistryOps<Tag> ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
			CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			for (String k : root.keySet()) {
				CompoundTag page = root.getCompoundOrEmpty(k);
				ListTag list = page.getListOrEmpty("items");
				List<ItemStack> items = new ArrayList<>();
				for (int i = 0; i < list.size(); i++)
					items.add(ItemStack.OPTIONAL_CODEC.parse(ops, list.get(i)).result().orElse(ItemStack.EMPTY));
				pages.put(k, new Page(items, page.getLongOr("seen", 0)));
			}
		} catch (Exception e) {
			LOGGER.warn("[Clean UI] Couldn't read storage cache {}; starting fresh", file, e); // never crash over a cache
		}
	}

	private static void save(Minecraft mc) {
		if (loadedKey == null || mc.level == null) return;
		try {
			RegistryOps<Tag> ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
			CompoundTag root = new CompoundTag();
			pages.forEach((k, page) -> {
				ListTag list = new ListTag();
				for (ItemStack s : page.items())
					list.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, s).result().orElse(new CompoundTag()));
				CompoundTag tag = new CompoundTag();
				tag.put("items", list);
				tag.putLong("seen", page.seenAt());
				root.put(k, tag);
			});
			Files.createDirectories(DIR);
			NbtIo.writeCompressed(root, file(loadedKey));
			dirty = false;
		} catch (Exception e) {
			LOGGER.warn("[Clean UI] Couldn't save storage cache", e);
		}
	}

	private static Path file(String key) {
		return DIR.resolve("storage-" + key.replaceAll("[^0-9a-zA-Z-]", "") + ".nbt");
	}
}
