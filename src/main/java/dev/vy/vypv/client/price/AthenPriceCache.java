package dev.vy.vypv.client.price;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.vypv.VyPV;
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

/**
 * Live Athen AH/bazaar prices from {@code https://athen.aerii.xyz/prices}.
 */
public final class AthenPriceCache {
	private static final URI PRICES_URI = URI.create("https://athen.aerii.xyz/prices");
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final long REFRESH_MINUTES = 10L;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "VyPV-AthenPrices");
		t.setDaemon(true);
		return t;
	});

	private static volatile Map<String, Double> prices = Map.of();
	private static volatile boolean ready;

	private AthenPriceCache() {
	}

	public static void start() {
		EXECUTOR.execute(AthenPriceCache::refreshSafely);
		EXECUTOR.scheduleAtFixedRate(AthenPriceCache::refreshSafely, REFRESH_MINUTES, REFRESH_MINUTES, TimeUnit.MINUTES);
	}

	public static boolean isReady() {
		return ready && !prices.isEmpty();
	}

	public static Double get(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return null;
		}
		return prices.get(itemId);
	}

	private static void refreshSafely() {
		try {
			refresh();
		} catch (Exception exception) {
			VyPV.LOGGER.warn("Failed to refresh Athen prices", exception);
		}
	}

	private static void refresh() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(PRICES_URI).timeout(TIMEOUT).GET().build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Athen prices HTTP " + response.statusCode());
		}
		JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
		Map<String, Double> next = new ConcurrentHashMap<>();
		loadAuctionHouse(root.getAsJsonObject("auction_house"), next);
		loadBazaar(root.getAsJsonObject("bazaar"), next);
		if (!next.isEmpty()) {
			prices = Map.copyOf(next);
			ready = true;
			VyPV.LOGGER.info("Loaded {} Athen price entries", next.size());
		}
	}

	private static void loadAuctionHouse(JsonObject section, Map<String, Double> out) {
		if (section == null) {
			return;
		}
		for (var entry : section.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject obj = entry.getValue().getAsJsonObject();
			Double price = firstPositive(num(obj, "p3d"), num(obj, "lbin"), num(obj, "p7d"));
			if (price != null) {
				out.put(entry.getKey(), price);
			}
		}
	}

	private static void loadBazaar(JsonObject section, Map<String, Double> out) {
		if (section == null) {
			return;
		}
		for (var entry : section.entrySet()) {
			if (!entry.getValue().isJsonObject() || out.containsKey(entry.getKey())) {
				continue;
			}
			JsonObject obj = entry.getValue().getAsJsonObject();
			Double price = firstPositive(num(obj, "ib"), num(obj, "tb"), num(obj, "is"), num(obj, "ts"));
			if (price != null) {
				out.put(entry.getKey(), price);
			}
		}
	}

	private static Double num(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			double value = element.getAsDouble();
			return value > 0 ? value : null;
		}
		return null;
	}

	private static Double firstPositive(Double... values) {
		for (Double value : values) {
			if (value != null && value > 0) {
				return value;
			}
		}
		return null;
	}
}
