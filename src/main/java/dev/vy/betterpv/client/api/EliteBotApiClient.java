package dev.vy.betterpv.client.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.BetterPV;
import dev.vy.betterpv.client.data.SoftDataFailure;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Elite Farmers public API ({@code https://api.eliteskyblock.com}, formerly api.elitebot.dev).
 * Used for farming weight (credited in Garden overview) and leaderboard ranks (Rankings tab).
 */
public final class EliteBotApiClient {
	private static final String BASE = "https://api.eliteskyblock.com";
	private static final Duration TIMEOUT = Duration.ofSeconds(15);
	private static final long SPACING_MS = 400L;
	private static final String USER_AGENT = "BetterPV/" + FabricLoader.getInstance()
		.getModContainer(BetterPV.MOD_ID)
		.map(container -> container.getMetadata().getVersion().getFriendlyString())
		.orElse("unknown");

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "BetterPV-EliteBot");
		t.setDaemon(true);
		return t;
	});
	private static long nextRequestAtMillis;
	private static final long RANKS_TTL_MS = 5L * 60L * 1000L;
	private static final ConcurrentHashMap<String, CachedBody> RANKS_CACHE = new ConcurrentHashMap<>();
	private static final InFlightRequestRegistry<String, Optional<JsonObject>> RANKS_IN_FLIGHT = new InFlightRequestRegistry<>();

	private record CachedBody(JsonObject body, long fetchedAtMs) {
	}

	private EliteBotApiClient() {
	}

	/** Farming weight for a profile. */
	public static CompletableFuture<Optional<JsonObject>> weight(UUID playerUuid, String profileId) {
		if (playerUuid == null || profileId == null || profileId.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String player = HypixelApiClient.undashed(playerUuid);
		String profile = profileId.trim().replace("-", "").toLowerCase();
		String url = BASE + "/weight/" + player + "/" + profile;
		return CompletableFuture.supplyAsync(() -> getObject(url), EXECUTOR);
	}

	/**
	 * Every leaderboard placement for a player on one profile ({@code GET /leaderboards/{player}/{profile}},
	 * one row per board the profile appears on, at most a few hundred). Unknown players/profiles come
	 * back as an empty {@code ranks} object, so an empty Optional always means the request itself failed.
	 * Successful bodies are cached briefly and concurrent callers share one request.
	 */
	public static CompletableFuture<Optional<JsonObject>> leaderboardRanks(UUID playerUuid, String profileId) {
		if (playerUuid == null || profileId == null || profileId.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String player = HypixelApiClient.undashed(playerUuid);
		String profile = profileId.trim().replace("-", "").toLowerCase();
		String key = player + "/" + profile;
		long now = System.currentTimeMillis();
		CachedBody cached = RANKS_CACHE.get(key);
		if (cached != null && now - cached.fetchedAtMs() < RANKS_TTL_MS) {
			return CompletableFuture.completedFuture(Optional.of(cached.body()));
		}
		String url = BASE + "/leaderboards/" + player + "/" + profile;
		return RANKS_IN_FLIGHT.share(key, "", () -> CompletableFuture.supplyAsync(() -> {
			Optional<JsonObject> body = getObject(url);
			if (body.isPresent()) {
				long fetchedAt = System.currentTimeMillis();
				RANKS_CACHE.values().removeIf(entry -> fetchedAt - entry.fetchedAtMs() >= RANKS_TTL_MS);
				RANKS_CACHE.put(key, new CachedBody(body.get(), fetchedAt));
			}
			return body;
		}, EXECUTOR)).future();
	}

	private static Optional<JsonObject> getObject(String url) {
		waitForSlot();
		try {
			HttpRequest request = eliteGet(url);
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
				BetterPV.LOGGER.warn("Elite GET {} status={}", url, response.statusCode());
				return Optional.empty();
			}
			JsonElement el = JsonParser.parseString(response.body());
			if (el.isJsonObject()) {
				return Optional.of(el.getAsJsonObject());
			}
			return Optional.empty();
		} catch (IOException | InterruptedException exception) {
			BetterPV.LOGGER.warn("Elite GET {} failed", url, exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			BetterPV.LOGGER.warn("Elite GET {} parse failed", url, exception);
			return Optional.empty();
		}
	}

	private static HttpRequest eliteGet(String url) {
		return HttpRequest.newBuilder(URI.create(url))
			.timeout(TIMEOUT)
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();
	}

	private static synchronized void waitForSlot() {
		long now = System.currentTimeMillis();
		long wait = nextRequestAtMillis - now;
		if (wait > 0L) {
			try {
				Thread.sleep(wait);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}
		nextRequestAtMillis = System.currentTimeMillis() + SPACING_MS;
	}
}
