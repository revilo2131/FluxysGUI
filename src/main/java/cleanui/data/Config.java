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
