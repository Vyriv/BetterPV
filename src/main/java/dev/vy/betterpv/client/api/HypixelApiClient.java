package dev.vy.betterpv.client.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.BetterPV;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hypixel SkyBlock API access via the shared Cloudflare worker
 * ({@code /hypixel/skyblock/...}), with optional local API-key override.
 */
public final class HypixelApiClient {
	private static final String WORKER_BASE = "https://plain-dawn-a5d2.ryaneagers2015.workers.dev";
	private static final String WORKER_HYPIXEL_HEADER = "X-VyPV-Key";
	private static final URI DIRECT_HYPIXEL = URI.create("https://api.hypixel.net/v2/");
	private static final URI MOJANG_PROFILE = URI.create("https://api.mojang.com/users/profiles/minecraft/");
	private static final URI MOJANG_SESSION = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/");
	private static final Duration TIMEOUT = Duration.ofSeconds(12);
	private static final long SPACING_MS = 80L;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final ConcurrentHashMap<String, JsonObject> PLAYER_CACHE = new ConcurrentHashMap<>();
	/** Mojang names do not change during this client session often enough to justify repeat lookups. */
	private static final ConcurrentHashMap<String, UuidName> UUID_NAME_CACHE = new ConcurrentHashMap<>();
	/** Small pool so museum + election (and other GETs) can overlap; rate limit still serializes spacing. */
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "BetterPV-HypixelApi");
		t.setDaemon(true);
		return t;
	});
	/** Heavy profile parse (NBT / networth) stays off the HTTP pool. */
	private static final ExecutorService PARSE_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "BetterPV-ProfileParse");
		t.setDaemon(true);
		return t;
	});

	public static ExecutorService parseExecutor() {
		return PARSE_EXECUTOR;
	}

	private static volatile String apiKey = "";
	private static long nextRequestAtMillis;

	private HypixelApiClient() {
	}

	public static void setApiKey(String key) {
		apiKey = key == null ? "" : key.trim();
	}

	public static boolean hasLocalApiKey() {
		return !apiKey.isBlank();
	}

	public static boolean canFetch() {
		return true;
	}

	public static CompletableFuture<Optional<UuidName>> resolveUuid(String name) {
		String cleaned = name == null ? "" : name.trim();
		if (cleaned.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		UuidName cached = UUID_NAME_CACHE.get(cleaned.toLowerCase(Locale.ROOT));
		if (cached != null) {
			return CompletableFuture.completedFuture(Optional.of(cached));
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(
					MOJANG_PROFILE.resolve(URLEncoder.encode(cleaned, StandardCharsets.UTF_8))
				).timeout(TIMEOUT).GET().build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
					return Optional.<UuidName>empty();
				}
				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
				UUID uuid = parseUndashedUuid(root.has("id") ? root.get("id").getAsString() : null);
				String resolved = root.has("name") ? root.get("name").getAsString() : cleaned;
				if (uuid == null) {
					return Optional.empty();
				}
				UuidName uuidName = new UuidName(uuid, resolved);
				UUID_NAME_CACHE.put(cleaned.toLowerCase(Locale.ROOT), uuidName);
				UUID_NAME_CACHE.put(resolved.toLowerCase(Locale.ROOT), uuidName);
				return Optional.of(uuidName);
			} catch (Exception exception) {
				BetterPV.LOGGER.warn("Mojang UUID lookup failed for {}", cleaned, exception);
				return Optional.empty();
			}
		}, EXECUTOR);
	}

	/** UUID → current Minecraft username (keyless Mojang session server). */
	public static CompletableFuture<Optional<UuidName>> resolveName(UUID uuid) {
		if (uuid == null) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(MOJANG_SESSION.resolve(id))
					.timeout(TIMEOUT)
					.GET()
					.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
					return Optional.<UuidName>empty();
				}
				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
				String resolved = root.has("name") ? root.get("name").getAsString() : null;
				if (resolved == null || resolved.isBlank()) {
					return Optional.empty();
				}
				UuidName uuidName = new UuidName(uuid, resolved);
				UUID_NAME_CACHE.put(resolved.toLowerCase(Locale.ROOT), uuidName);
				return Optional.of(uuidName);
			} catch (Exception exception) {
				BetterPV.LOGGER.debug("Mojang name lookup failed for {}", id, exception);
				return Optional.empty();
			}
		}, EXECUTOR);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockProfiles(UUID uuid) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(
			() -> fetchPreferWorker(
				WORKER_BASE + "/hypixel/skyblock/profiles/" + id,
				"skyblock/profiles",
				"uuid=" + id
			),
			EXECUTOR
		);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockMuseum(UUID uuid) {
		return skyblockMuseum(uuid, null);
	}

	public static CompletableFuture<Optional<JsonObject>> skyblockMuseum(UUID uuid, String profileId) {
		String id = undashed(uuid);
		String workerUrl = WORKER_BASE + "/hypixel/skyblock/museum/" + id;
		String directQuery = "uuid=" + id;
		if (profileId != null && !profileId.isBlank()) {
			String encoded = URLEncoder.encode(profileId.trim(), StandardCharsets.UTF_8);
			workerUrl += "?profile=" + encoded;
			directQuery += "&profile=" + profileId.trim();
		}
		String finalWorkerUrl = workerUrl;
		String finalDirectQuery = directQuery;
		return CompletableFuture.supplyAsync(
			() -> fetchPreferWorker(finalWorkerUrl, "skyblock/museum", finalDirectQuery),
			EXECUTOR
		);
	}

	/** Player auctions (Active). Requires API key / worker. */
	public static CompletableFuture<Optional<JsonObject>> skyblockAuction(UUID uuid) {
		String id = undashed(uuid);
		return CompletableFuture.supplyAsync(
			() -> fetchPreferWorker(
				WORKER_BASE + "/hypixel/skyblock/auction/" + id,
				"skyblock/auction",
				"player=" + id
			),
			EXECUTOR
		);
	}

	/** SkyBlock garden island by profile id (lazy; not on initial PV load). */
	public static CompletableFuture<Optional<JsonObject>> skyblockGarden(String profileId) {
		if (profileId == null || profileId.isBlank()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		String id = profileId.trim();
		String encoded = URLEncoder.encode(id, StandardCharsets.UTF_8);
		return CompletableFuture.supplyAsync(
			() -> {
				Optional<JsonObject> root = fetchPreferWorker(
					WORKER_BASE + "/hypixel/skyblock/garden/" + encoded,
					"skyblock/garden",
					"profile=" + id
				);
				if (root.isEmpty()) {
					return Optional.empty();
				}
				JsonObject body = root.get();
				if (body.has("garden") && body.get("garden").isJsonObject()) {
					return Optional.of(body.getAsJsonObject("garden"));
				}
				return Optional.of(body);
			},
			EXECUTOR
		);
	}

	/** Hypixel player object (ranks, displayname, …). */
	public static CompletableFuture<Optional<JsonObject>> player(UUID uuid) {
		String id = undashed(uuid);
		JsonObject cached = PLAYER_CACHE.get(id);
		if (cached != null) {
			return CompletableFuture.completedFuture(Optional.of(cached));
		}
		return CompletableFuture.supplyAsync(
			() -> {
				Optional<JsonObject> root = fetchPreferWorker(
					WORKER_BASE + "/hypixel/player/" + id,
					"player",
					"uuid=" + id
				);
				if (root.isEmpty()) {
					return Optional.empty();
				}
				JsonObject body = root.get();
				JsonObject player = body.has("player") && body.get("player").isJsonObject()
					? body.getAsJsonObject("player")
					: body;
				PLAYER_CACHE.put(id, player);
				return Optional.of(player);
			},
			EXECUTOR
		);
	}

	/** SkyBlock election / current mayor. Resources endpoint; no API key required. */
	public static CompletableFuture<Optional<JsonObject>> skyblockElection() {
		return CompletableFuture.supplyAsync(() -> {
			waitForSlot();
			Optional<JsonObject> viaWorker = getJson(WORKER_BASE + "/hypixel/resources/skyblock/election", false);
			if (viaWorker.isPresent()) {
				return viaWorker;
			}
			return getJson(DIRECT_HYPIXEL.resolve("resources/skyblock/election").toString(), false);
		}, EXECUTOR);
	}

	private static Optional<JsonObject> fetchPreferWorker(String workerUrl, String directPath, String directQuery) {
		waitForSlot();
		Optional<JsonObject> viaWorker = getJson(workerUrl, false);
		if (viaWorker.isPresent()) {
			return viaWorker;
		}
		if (!hasLocalApiKey()) {
			return Optional.empty();
		}
		BetterPV.LOGGER.warn("Hypixel worker miss for {}; falling back to direct API key", directPath);
		String url = DIRECT_HYPIXEL.resolve(directPath).toString();
		if (directQuery != null && !directQuery.isBlank()) {
			url = url + (url.contains("?") ? "&" : "?") + directQuery;
		}
		return getJson(url, true);
	}

	private static Optional<JsonObject> getJson(String url, boolean sendApiKey) {
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET();
			if (sendApiKey) {
				builder.header("API-Key", apiKey);
			}
			if (url != null && url.startsWith(WORKER_BASE) && url.contains("/hypixel/")) {
				String secret = WorkerSecrets.HYPIXEL_WORKER_SECRET;
				if (secret != null && !secret.isBlank()) {
					builder.header(WORKER_HYPIXEL_HEADER, secret);
				}
			}
			HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
				BetterPV.LOGGER.warn("Hypixel GET {} failed status={}", url, response.statusCode());
				return Optional.empty();
			}
			JsonElement element = JsonParser.parseString(response.body());
			if (!element.isJsonObject()) {
				return Optional.empty();
			}
			JsonObject root = element.getAsJsonObject();
			if (root.has("success") && root.get("success").isJsonPrimitive() && !root.get("success").getAsBoolean()) {
				return Optional.empty();
			}
			return Optional.of(root);
		} catch (IOException | InterruptedException exception) {
			BetterPV.LOGGER.warn("Hypixel GET {} failed", url, exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return Optional.empty();
		}
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

	public static String undashed(UUID uuid) {
		return uuid == null ? "" : uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
	}

	public static UUID parseUndashedUuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String hex = raw.trim().replace("-", "").toLowerCase(Locale.ROOT);
		if (hex.length() != 32) {
			return null;
		}
		try {
			String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
				+ "-" + hex.substring(16, 20) + "-" + hex.substring(20);
			return UUID.fromString(dashed);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	public record UuidName(UUID uuid, String name) {
	}
}
