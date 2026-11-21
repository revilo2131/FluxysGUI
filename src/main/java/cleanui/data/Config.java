package cleanui.data;

import cleanui.CleanUi;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** User settings, saved as config/cleanui.json. Fields are the JSON keys. */
public final class Config {
	private static final Logger LOGGER = LoggerFactory.getLogger(CleanUi.ID);
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(CleanUi.ID + ".json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance;

	public String theme = "cyan";
	/** Style knobs; picking a theme resets them to that theme's own (defaults = the original look). */
	public String frame = "ORNATE";
	public boolean rounded = false;
	public boolean flatBars = false;
	/** Off: no easing or sweeps, for anyone who prefers a still UI. */
	public boolean motion = true;
	/** Percent; the window still shrinks to fit small screens. */
	public int uiScale = 100;

	// which Hypixel menus get a Clean UI page
	public boolean skyblockMenu = true;
	public boolean skills = true;
	public boolean collections = true;
	public boolean storage = true;
	public boolean calendar = true;
	public boolean bestiary = true;
	public boolean pets = true;
	/** Skill and collection level tracks. */
	public boolean tracks = true;
	/** Restyle every other Hypixel menu (keeps its own controls). */
	public boolean restyleMenus = true;
	/** Heart of the Mountain / Forest tree. */
	public boolean hotm = true;
	// Scatha gambling (settings page on the Mining skill page)
	/** A slot machine spins on every Scatha kill. */
	public boolean scathaSlots = true;
	/** Which game a kill plays: "SLOTS", "ROULETTE" or "NONE" (no game; a pet drop still gets the walk-in). */
	public String scathaGame = "SLOTS";
	/** "Then I scathe in": on a pet, the buff Scatha walks in (instead of the rarity reel / portal). */
	public boolean scathaWalkIn = false;
	/** Chat hidden while a Scatha is on 3 HP or less, so a pet drop can't be seen early. */
	public boolean scathaHideChat = true;
	/** While rolling: world blurred, HUD hidden. Off: the machine draws over the HUD and you keep control. */
	public boolean scathaFocus = true;
	public boolean scathaSounds = true;
	/** When a Scatha spawns next to you, send your coordinates to party chat (/pc). */
	public boolean scathaAutoCoords = false;
	/** Note block build-up and drop on a pet win. */
	public boolean scathaMusic = true;
	// Crystal Nucleus runs (also on the Scatha page)
	/** Five crystal slots and a run timer on the HUD during a run. */
	public boolean nucleusTracker = true;
	/** Crystal found fly-in, placed toast, beams when the fifth goes in. */
	public boolean nucleusAnimations = true;
	/** Loot bundle on a reel, then the stats card. */
	public boolean nucleusLoot = true;
	/** Tracker place: "LEFT", "RIGHT" or "TOP"; size in percent. */
	public String nucleusTrackerPos = "LEFT";
	public int nucleusTrackerScale = 100;
	/** Run time (coloured against your PB) on the tracker. */
	public boolean nucleusTimer = true;
	/** The finale (beams, loot, stats) over a blurred world with the rest of the HUD hidden. */
	public boolean nucleusFocus = true;
	/** The buff Scatha walks in on a new personal best. */
	public boolean nucleusPbWalkIn = true;
	/** What the five crystals go into: RANDOM (a different one each run), GAUNTLET, ALTAR, REACTOR, CONSTELLATION, CROWN, GOLEM. */
	public String nucleusFinale = "RANDOM";
	/** Vanguard corpse loot: the spinning ouroboros, cut by a katana on a rare drop. */
	public boolean vanguardSnake = true;
	/** Vanguard corpses play the lock being picked (6/6 dye ... 4/6 wisp or key) instead of the snake. */
	public boolean vanguardLockpick = false;
	/** Vanguard animations over a blurred world with the rest of the HUD hidden (you can still walk). */
	public boolean vanguardFocus = true;
	/** Vanguard sounds: Wither spawn, the cut, the picks and each drop's own sound. */
	public boolean vanguardSounds = true;
	/** Confetti when a rare drop bursts out. */
	public boolean vanguardConfetti = true;
	/** The drop's coin value under its name. */
	public boolean vanguardValues = true;
	/** Fastest run, first crystal found to the fifth placed (ms; 0 = none yet). */
	public long nucleusBestMs = 0;
	/** Personal Hypixel API key for networth; stays on this PC, only sent to api.hypixel.net. */
	public String hypixelApiKey = "";

	public static Config get() {
		if (instance == null) {
			instance = new Config();
			try {
				if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), Config.class);
			} catch (Exception e) {
				LOGGER.warn("[Clean UI] Couldn't read {}; using defaults", FILE, e); // a broken file shouldn't stop the game
			}
			if (instance == null) instance = new Config();
		}
		return instance;
	}

	public void save() {
		try {
			Files.writeString(FILE, GSON.toJson(this));
		} catch (Exception e) {
			LOGGER.warn("[Clean UI] Couldn't save {}", FILE, e);
		}
	}
}
