package cleanui.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches the player's own SkyBlock profile with their personal Hypixel API key and estimates
 * networth from public prices (SkyHelper's price file). Runs off the render thread; one profile
 * request per {@link #REFRESH}, prices at most every {@link #PRICE_REFRESH}. The key stays in
 * the local config and is only sent to api.hypixel.net.
 */
public final class Networth {
	private static final Duration REFRESH = Duration.ofMinutes(5), PRICE_REFRESH = Duration.ofMinutes(30);
	private static final String PROFILES = "https://api.hypixel.net/v2/skyblock/profiles?uuid=";
	private static final String PRICES = "https://raw.githubusercontent.com/SkyHelperBot/Prices/main/pricesV2.json";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private static volatile NetworthCalc.Result result;
	private static volatile String error;
	private static volatile long fetchedAt;
	private static volatile boolean loading;
	private static Map<String, Double> prices;
	private static long pricesAt;

	private Networth() {}

	public static NetworthCalc.Result result() {
		return result;
	}

	/** Why there's no result (no key, bad key, ...), or null. */
	public static String error() {
		if (key().isEmpty()) return "No API key yet.";
		return error;
	}

	public static boolean loading() {
		return loading;
	}

	public static long fetchedAt() {
		return fetchedAt;
	}

	/** Starts a background refresh if there's a key and the last one is older than 5 minutes. */
	public static void refreshIfStale() {
		String key = key();
		Minecraft mc = Minecraft.getInstance();
		if (key.isEmpty() || loading || mc.player == null) return;
		if (System.currentTimeMillis() - fetchedAt < REFRESH.toMillis()) return;
		String uuid = mc.player.getUUID().toString().replace("-", "");
		String profileId = StorageCache.profileId();
		loading = true;
		CompletableFuture.runAsync(() -> {
			try {
				result = NetworthCalc.compute(profile(key, uuid, profileId), uuid, prices());
				error = null;
			} catch (Exception e) {
				error = e.getMessage() != null ? e.getMessage() : "Couldn't load networth.";
			} finally {
				fetchedAt = System.currentTimeMillis();
				loading = false;
			}
		});
	}

	private static String key() {
		String k = Config.get().hypixelApiKey;
		return k == null ? "" : k.strip();
	}

	/** Forget the last result (e.g. after the key changes). */
	public static void reset() {
		result = null;
		error = null;
		fetchedAt = 0;
	}

	private static JsonObject profile(String key, String uuid, String profileId) throws Exception {
		HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(PROFILES + uuid))
				.header("API-Key", key).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
		switch (res.statusCode()) {
			case 200 -> {}
			case 403 -> throw new Exception("Hypixel says the API key is invalid.");
			case 429 -> throw new Exception("Too many requests; trying again later.");
			default -> throw new Exception("Hypixel API error " + res.statusCode() + ".");
		}
		JsonElement profiles = JsonParser.parseString(res.body()).getAsJsonObject().get("profiles");
		if (profiles == null || !profiles.isJsonArray() || profiles.getAsJsonArray().isEmpty())
			throw new Exception("No SkyBlock profiles found.");
		JsonObject chosen = null;
		for (JsonElement p : profiles.getAsJsonArray()) {
			JsonObject o = p.getAsJsonObject();
			boolean match = profileId != null && profileId.replace("-", "").equalsIgnoreCase(o.get("profile_id").getAsString().replace("-", ""));
			boolean selected = o.has("selected") && o.get("selected").getAsBoolean();
			if (match) return o; // the profile you're on right now
			if (selected && chosen == null) chosen = o;
		}
		return chosen != null ? chosen : profiles.getAsJsonArray().get(0).getAsJsonObject();
	}

	private static synchronized Map<String, Double> prices() throws Exception {
		if (prices != null && System.currentTimeMillis() - pricesAt < PRICE_REFRESH.toMillis()) return prices;
		HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(PRICES)).timeout(Duration.ofSeconds(20)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) throw new Exception("Couldn't load prices (" + res.statusCode() + ").");
		Map<String, Double> map = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : JsonParser.parseString(res.body()).getAsJsonObject().entrySet())
			if (e.getValue().isJsonPrimitive()) map.put(e.getKey(), e.getValue().getAsDouble());
		prices = map;
		pricesAt = System.currentTimeMillis();
		return prices;
	}
}
