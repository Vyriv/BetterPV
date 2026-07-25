package dev.vy.betterpv.client.price;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.BetterPV;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Hypixel SkyBlock item definitions (upgrade costs, gem slots, category). */
public final class HypixelItemsCache {
	private static final URI ITEMS_URI = URI.create("https://api.hypixel.net/v2/resources/skyblock/items");
	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final long REFRESH_HOURS = 12L;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "BetterPV-HypixelItems");
		t.setDaemon(true);
		return t;
	});

	private static volatile Map<String, JsonObject> items = Map.of();

	private HypixelItemsCache() {
	}

	public static void start() {
		EXECUTOR.execute(HypixelItemsCache::refreshSafely);
		EXECUTOR.scheduleAtFixedRate(HypixelItemsCache::refreshSafely, REFRESH_HOURS, REFRESH_HOURS, TimeUnit.HOURS);
	}

	public static JsonObject get(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		JsonObject direct = items.get(itemId);
		if (direct != null) {
			return direct;
		}
		return items.get(itemId.toUpperCase(java.util.Locale.ROOT));
	}

	private static void refreshSafely() {
		try {
			refresh();
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to refresh Hypixel items", exception);
		}
	}

	private static void refresh() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(ITEMS_URI).timeout(TIMEOUT).GET().build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Hypixel items HTTP " + response.statusCode());
		}
		JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
		JsonArray list = root.has("items") && root.get("items").isJsonArray() ? root.getAsJsonArray("items") : null;
		if (list == null) {
			throw new IOException("Hypixel items missing array");
		}
		Map<String, JsonObject> next = new ConcurrentHashMap<>();
		for (JsonElement element : list) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject item = element.getAsJsonObject();
			if (item.has("id") && item.get("id").isJsonPrimitive()) {
				next.put(item.get("id").getAsString(), item);
			}
		}
		if (!next.isEmpty()) {
			items = Map.copyOf(next);
			BetterPV.LOGGER.info("Loaded {} Hypixel item definitions", next.size());
		}
	}
}
