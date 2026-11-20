package cleanui.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The current mayor and minister with their perks, from Hypixel's public election endpoint (no
 * key needed), plus the mayor's NPC skin from the NotEnoughUpdates item repo. Fetched in the
 * background at most every {@link #REFRESH}; nothing about the player is sent.
 */
public final class Mayor {
	private static final Duration REFRESH = Duration.ofMinutes(10);
	private static final String ELECTION = "https://api.hypixel.net/v2/resources/skyblock/election";
	private static final String NEU_ITEM = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/";
	private static final Pattern TEXTURE = Pattern.compile("Value:\\\\\"([A-Za-z0-9+/=]+)\\\\\"");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public record Perk(String name, String description) {}

	/** {@code skin} is the base64 textures value, or null if the repo has none for this mayor. */
	public record Info(String name, List<Perk> perks, String minister, Perk ministerPerk, String skin) {}

	private static volatile Info info;
	private static volatile boolean loading, failed;
	private static volatile long fetchedAt;

	private Mayor() {}

	/** The latest known mayor (null until loaded); starts a background refresh when stale. */
	public static Info get() {
		if (!loading && System.currentTimeMillis() - fetchedAt > REFRESH.toMillis()) {
			loading = true;
			CompletableFuture.runAsync(() -> {
				try {
					Info parsed = parse(fetch(ELECTION));
					Info old = info;
					String skin = old != null && old.name().equals(parsed.name()) ? old.skin() : skin(parsed.name());
					info = new Info(parsed.name(), parsed.perks(), parsed.minister(), parsed.ministerPerk(), skin);
					failed = false;
				} catch (Exception e) {
					failed = info == null;
				} finally {
					fetchedAt = System.currentTimeMillis();
					loading = false;
				}
			});
		}
		return info;
	}

	public static boolean failed() {
		return failed;
	}

	/** Election JSON → mayor, perks and minister (skin left null). */
	static Info parse(String json) {
		JsonObject mayor = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("mayor");
		List<Perk> perks = new ArrayList<>();
		for (JsonElement p : mayor.getAsJsonArray("perks")) perks.add(perk(p.getAsJsonObject()));
		JsonObject minister = mayor.getAsJsonObject("minister");
		return new Info(mayor.get("name").getAsString(), perks,
				minister != null ? minister.get("name").getAsString() : null,
				minister != null && minister.has("perk") ? perk(minister.getAsJsonObject("perk")) : null, null);
	}

	private static Perk perk(JsonObject o) {
		return new Perk(o.get("name").getAsString(), o.has("description") ? o.get("description").getAsString() : "");
	}

	/** The first skin texture in an NEU item file, or null. */
	static String texture(String itemJson) {
		Matcher m = TEXTURE.matcher(itemJson);
		return m.find() ? m.group(1) : null;
	}

	private static String skin(String name) {
		String id = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
		for (String suffix : List.of("_MAYOR_MONSTER", "_SPECIAL_MAYOR_MONSTER"))
			try {
				String t = texture(fetch(NEU_ITEM + id + suffix + ".json"));
				if (t != null) return t;
			} catch (Exception ignored) {} // 404: try the other name
		return null;
	}

	private static String fetch(String url) throws Exception {
		HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) throw new Exception("HTTP " + res.statusCode());
		return res.body();
	}
}
