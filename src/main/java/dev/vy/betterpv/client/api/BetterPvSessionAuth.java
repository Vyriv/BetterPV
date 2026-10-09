package dev.vy.betterpv.client.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import dev.vy.betterpv.BetterPV;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * BetterPV → api.vyriv.dev session proof (JWT only).
 *
 * <p>Minecraft access tokens are used only for the official Mojang/authlib
 * {@link MinecraftSessionService#joinServer} call. They are never sent to
 * api.vyriv.dev / Vyriv / Cloudflare / logging.
 */
public final class BetterPvSessionAuth {
	private static final URI AUTH_CHALLENGE_URI = URI.create("https://api.vyriv.dev/hypixel/auth/v2/challenge");
	private static final URI AUTH_URI = URI.create("https://api.vyriv.dev/hypixel/auth/v2");
	private static final Duration TIMEOUT = Duration.ofSeconds(12);
	private static final long REFRESH_SKEW_MILLIS = 60_000L;
	private static final long TOKEN_EXPIRY_SAFETY_MILLIS = 5_000L;
	private static final long MOJANG_COOLDOWN_MILLIS = 60_000L;
	private static final SessionAuthConnectionGate CONNECTION_GATE = new SessionAuthConnectionGate(System::nanoTime);
	private static final HttpClient HTTP = HypixelApiClient.http();
	private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
	private static final Object LOCK = new Object();
	private static final ExecutorService AUTH_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BetterPV-SessionAuth");
		t.setDaemon(true);
		return t;
	});

	private static final long REFRESH_WHILE_USED_MILLIS = 30L * 60L * 1000L;
	private static final ScheduledExecutorService REFRESH_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "BetterPV-SessionAuthRefresh");
		t.setDaemon(true);
		return t;
	});

	private static volatile String cachedJwt;
	private static volatile long expiresAtMillis;
	private static volatile long usableUntilMillis;
	private static volatile long nextMojangAuthAttemptAtMillis;
	private static Failure cooldownFailure = Failure.NONE;
	private static String cooldownDetail = "";
	private static ScheduledFuture<?> pendingRefresh;
	private static long refreshGeneration;
	private static volatile long lastUsedAtMillis;
	private static CompletableFuture<Optional<String>> inFlight;
	private static volatile Failure lastFailure = Failure.NONE;
	private static volatile String lastFailureDetail = "";
	private static volatile long lastChatNoticeAtMillis;
	private static volatile String lastChatNotice;

	public enum Failure {
		NONE(""),
		MISSING_SESSION("BetterPV could not authenticate /pv (missing Minecraft session)"),
		OFFLINE_SESSION("BetterPV could not authenticate /pv (offline / Fabric session)"),
		JOIN_SERVER_FAILED("BetterPV could not authenticate /pv (Minecraft session service failed). Wait a minute before retrying."),
		INVALID_CREDENTIALS("BetterPV could not authenticate /pv (Minecraft session expired). Re-login to Microsoft in your launcher."),
		MOJANG_RATE_LIMITED("BetterPV authentication is temporarily rate-limited by Minecraft. Wait a minute before retrying /pv. BetterPV will not retry authentication until the cooldown expires."),
		SERVER_CONNECTING("BetterPV authentication deferred until your Minecraft server connection is ready."),
		SERVER_AUTH_UNAVAILABLE("BetterPV could not authenticate /pv (auth service unavailable)"),
		AUTH_COOLDOWN("BetterPV authentication is temporarily paused. Wait a minute before retrying /pv."),
		AUTH_REJECTED("BetterPV could not authenticate /pv (session proof rejected)"),
		AUTH_HTTP("BetterPV could not authenticate /pv (auth HTTP error)"),
		MISSING_JWT("BetterPV could not authenticate /pv (missing JWT)");

		private final String userMessage;

		Failure(String userMessage) {
			this.userMessage = userMessage;
		}

		public String userMessage() {
			return userMessage;
		}
	}

	private BetterPvSessionAuth() {
	}

	public static void invalidate() {
		synchronized (LOCK) {
			invalidateLocked();
		}
	}

	private static void invalidateLocked() {
		cachedJwt = null;
		expiresAtMillis = 0L;
		usableUntilMillis = 0L;
		// Never detach an active attempt or clear its cooldown.
		cancelRefreshLocked();
	}

	/** Ignore late 401s for an older JWT; preserve the shared authentication attempt. */
	public static void invalidate(HttpRequest rejectedRequest) {
		handleUnauthorized(rejectedRequest, "{\"cause\":\"unauthorized\"}");
	}

	/**
	 * Handles a resource 401 without assuming every request-proof failure invalidates the JWT.
	 * Returns true only when the rejected request used and invalidated the current token.
	 */
	public static boolean handleUnauthorized(HttpRequest rejectedRequest, String responseBody) {
		return handleUnauthorized(rejectedRequest, responseBody, -1L);
	}

	public static boolean handleUnauthorized(
		HttpRequest rejectedRequest,
		String responseBody,
		long elapsedMillis
	) {
		String cause = unauthorizedCause(responseBody);
		boolean invalidatingCause = invalidatesSession(cause);
		boolean currentToken;
		boolean invalidated = false;
		long validForMillis;
		long cooldownMillis;
		synchronized (LOCK) {
			String authorization = rejectedRequest == null
				? ""
				: rejectedRequest.headers().firstValue("Authorization").orElse("");
			currentToken = cachedJwt != null && authorization.equals("Bearer " + cachedJwt);
			validForMillis = Math.max(0L, expiresAtMillis - System.currentTimeMillis());
			cooldownMillis = remainingAuthCooldownMillis();
			if (currentToken && invalidatingCause) {
				invalidateLocked();
				invalidated = true;
			} else if (currentToken) {
				setFailure(Failure.AUTH_REJECTED, "resource 401 cause=" + cause);
			}
		}
		String correlationId = rejectedRequest == null
			? "none"
			: rejectedRequest.headers().firstValue("X-BetterPV-Correlation-ID").orElse("none");
		BetterPV.LOGGER.warn(
			"BetterPV auth correlationId={} stage=resource_unauthorized elapsedMs={} httpStatus=401 cause={} currentToken={} action={} tokenValidForMs={} proofCooldownMs={}",
			correlationId,
			elapsedMillis < 0L ? "unknown" : elapsedMillis,
			cause,
			currentToken,
			invalidated ? "invalidate" : currentToken ? "preserve" : "ignore_stale",
			validForMillis,
			cooldownMillis
		);
		return invalidated;
	}

	static String unauthorizedCause(String responseBody) {
		if (responseBody == null || responseBody.isBlank()) {
			return "unknown";
		}
		try {
			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			if (root.has("cause") && root.get("cause").isJsonPrimitive()) {
				String cause = root.get("cause").getAsString();
				if (cause != null && cause.matches("[A-Za-z0-9_]{1,80}")) {
					return cause.toLowerCase(Locale.ROOT);
				}
			}
		} catch (RuntimeException ignored) {
		}
		return "unknown";
	}

	static boolean invalidatesSession(String cause) {
		return "unauthorized".equals(cause)
			|| "session_revoked_or_expired".equals(cause)
			|| "auth_v1_disabled".equals(cause);
	}

	public static long remainingAuthCooldownMillis() {
		return Math.max(0L, nextMojangAuthAttemptAtMillis - System.currentTimeMillis());
	}

	private static boolean cooldownActive() {
		synchronized (LOCK) {
			long remaining = remainingAuthCooldownMillis();
			if (remaining > 0L) {
				// Keep a more useful exchange failure visible while still blocking another
				// Mojang proof. A successful joinServer followed by a backend 502 used to
				// be replaced with AUTH_COOLDOWN on the next caller.
				if (lastFailure == Failure.NONE || lastFailure == Failure.AUTH_COOLDOWN
					|| lastFailure == cooldownFailure) {
					setFailure(cooldownFailure, cooldownDetail);
				}
				BetterPV.LOGGER.debug("BetterPV session auth skipped; cooldown active for {}ms", remaining);
				return true;
			}
			if (nextMojangAuthAttemptAtMillis != 0L) {
				BetterPV.LOGGER.info("BetterPV session auth cooldown expired; authentication may resume");
				nextMojangAuthAttemptAtMillis = 0L;
			}
			return false;
		}
	}

	private static void pauseAuth(Failure failure, String detail) {
		synchronized (LOCK) {
			nextMojangAuthAttemptAtMillis = System.currentTimeMillis() + MOJANG_COOLDOWN_MILLIS;
			cooldownFailure = failure;
			cooldownDetail = detail;
			setFailure(failure, detail);
		}
		if (failure == Failure.MOJANG_RATE_LIMITED) {
			BetterPV.LOGGER.warn("BetterPV Minecraft session auth rate-limited; pausing Mojang auth for 60s");
		}
	}

	private static void setFailure(Failure failure, String detail) {
		lastFailure = failure == null ? Failure.NONE : failure;
		lastFailureDetail = detail == null ? "" : detail.trim();
	}

	public static Failure lastFailure() {
		return lastFailure == null ? Failure.NONE : lastFailure;
	}

	/** User-facing reason when JWT auth is unavailable; empty when OK. */
	public static Optional<String> userFacingFailure() {
		Failure failure = lastFailure();
		if (failure == Failure.NONE || failure.userMessage().isBlank()) {
			return Optional.empty();
		}
		return Optional.of(failure.userMessage());
	}

	/** Applies the v2 bearer token and proof of possession. */
	public static boolean applyAuthHeaders(HttpRequest.Builder builder, URI uri) {
		Optional<String> bearer = ensureBearerToken();
		if (bearer.isPresent()) {
			try {
				String token = bearer.get();
				String timestamp = Long.toString(Instant.now().getEpochSecond());
				byte[] nonceBytes = new byte[18];
				RANDOM.nextBytes(nonceBytes);
				String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
				String path = uri.getRawPath();
				if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) path += "?" + uri.getRawQuery();
				String transcript = requestTranscript("GET", path, timestamp, nonce, token);
				String signature = BetterPvInstallKey.load().sign(transcript);
				builder.header("Authorization", "Bearer " + token)
					.header("X-BetterPV-Timestamp", timestamp)
					.header("X-BetterPV-Nonce", nonce)
					.header("X-BetterPV-Signature", signature)
					.header("X-BetterPV-Correlation-ID", UUID.randomUUID().toString());
				return true;
			} catch (Exception exception) {
				BetterPV.LOGGER.warn("BetterPV request proof failed", exception);
				setFailure(Failure.AUTH_HTTP, "request proof failed");
				return false;
			}
		}
		if (lastFailure == Failure.NONE) {
			lastFailure = Failure.MISSING_JWT;
		}
		return false;
	}

	/** Posts a throttled in-game chat line for the current auth failure. */
	public static void notifyPlayerIfNeeded() {
		Failure failure = lastFailure();
		if (failure == Failure.NONE) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.gui == null) {
			return;
		}
		long now = System.currentTimeMillis();
		String noticeKey = failure.name();
		if (noticeKey.equals(lastChatNotice) && now - lastChatNoticeAtMillis < 15_000L) {
			return;
		}
		lastChatNotice = noticeKey;
		lastChatNoticeAtMillis = now;
		String report = copyableReport(mc, failure);
		mc.execute(() -> {
			if (mc.gui == null) {
				return;
			}
			mc.gui.getChat().addClientSystemMessage(authFailChat(report, failure));
		});
	}

	private static Component authFailChat(String report, Failure failure) {
		Component copyHint = Component.literal("Click to copy a report to DM Vyriv")
			.setStyle(Style.EMPTY
				.withColor(0xFFD36A)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.CopyToClipboard(report))
				.withHoverEvent(new HoverEvent.ShowText(
					Component.literal("Click to copy, then paste it to Vyriv on Discord")
				)));
		return Component.literal(failure.userMessage() + " ")
			.setStyle(Style.EMPTY.withColor(0xFFAAAAAA))
			.append(copyHint);
	}

	private static String copyableReport(Minecraft mc, Failure failure) {
		User user = mc.getUser();
		String name = user != null && user.getName() != null ? user.getName() : "?";
		String uuid = user != null && user.getProfileId() != null ? user.getProfileId().toString() : "?";
		String detail = lastFailureDetail == null || lastFailureDetail.isBlank() ? "" : "\ndetail: " + lastFailureDetail;
		return "BetterPV /pv auth failed"
			+ "\nname: " + name
			+ "\nuuid: " + uuid
			+ "\nreason: " + failure.name()
			+ detail
			+ "\nmod: " + modVersion();
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(BetterPV.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}

	/**
	 * Non-blocking warm of the session JWT once a real Minecraft login is present.
	 * Reuses in-flight auth; no-ops when a usable JWT is already cached.
	 */
	public static void prefetchAsync() {
		requestToken(false);
	}

	/** Non-blocking; concurrent consumers share one attempt. */
	public static CompletableFuture<Optional<String>> bearerTokenAsync() {
		lastUsedAtMillis = System.currentTimeMillis();
		return requestToken(false);
	}

	private static CompletableFuture<Optional<String>> requestToken(boolean refresh) {
		synchronized (LOCK) {
			if (!refresh && isTokenValid()) {
				setFailure(Failure.NONE, "");
				return CompletableFuture.completedFuture(Optional.of(cachedJwt));
			}
			if (inFlight != null && !inFlight.isDone()) return inFlight;
			if (cooldownActive()) return CompletableFuture.completedFuture(Optional.empty());
			inFlight = CompletableFuture.supplyAsync(BetterPvSessionAuth::authenticateOnce, AUTH_EXECUTOR)
				.exceptionally(error -> {
					BetterPV.LOGGER.warn("BetterPV session auth failed", error);
					setFailure(Failure.AUTH_HTTP, "Unexpected session authentication failure");
					return Optional.empty();
				});
			return inFlight;
		}
	}

	/** Blocking; call only from worker threads, never the render thread. */
	public static Optional<String> ensureBearerToken() {
		return bearerTokenAsync().join();
	}

	private static void cancelRefreshLocked() {
		refreshGeneration++;
		if (pendingRefresh != null) pendingRefresh.cancel(false);
		pendingRefresh = null;
	}

	// Only one pending refresh, and only while BetterPV is in use.
	private static void scheduleRefresh() {
		synchronized (LOCK) {
			long delayMillis = Math.max(30_000L, usableUntilMillis - System.currentTimeMillis());
			scheduleRefreshLocked(delayMillis);
		}
	}

	private static void scheduleRefreshLocked(long delayMillis) {
		cancelRefreshLocked();
		long generation = refreshGeneration;
		pendingRefresh = REFRESH_SCHEDULER.schedule(() -> {
			synchronized (LOCK) {
				if (generation != refreshGeneration) return;
				pendingRefresh = null;
				if (System.currentTimeMillis() - lastUsedAtMillis > REFRESH_WHILE_USED_MILLIS) return;
				long cooldownMillis = remainingAuthCooldownMillis();
				if (cooldownMillis > 0L) {
					BetterPV.LOGGER.info(
						"BetterPV session refresh deferred proofCooldownMs={} tokenValidForMs={}",
						cooldownMillis,
						Math.max(0L, expiresAtMillis - System.currentTimeMillis())
					);
					scheduleRefreshLocked(cooldownMillis + 250L);
					return;
				}
				requestToken(true);
			}
		}, Math.max(1L, delayMillis), TimeUnit.MILLISECONDS);
	}

	private static boolean isTokenValid() {
		return cachedJwt != null && !cachedJwt.isBlank()
			&& System.currentTimeMillis() < expiresAtMillis - TOKEN_EXPIRY_SAFETY_MILLIS;
	}

	private static Optional<String> authenticateOnce() {
		if (cooldownActive()) return Optional.empty();
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			setFailure(Failure.MISSING_SESSION, "minecraft instance null");
			return Optional.empty();
		}

		User user = mc.getUser();
		if (user == null) {
			setFailure(Failure.MISSING_SESSION, "user null");
			return Optional.empty();
		}

		String username = user.getName();
		UUID profileId = user.getProfileId();
		String accessToken = user.getAccessToken();
		if (username == null || username.isBlank() || profileId == null || accessToken == null || accessToken.isBlank()) {
			BetterPV.LOGGER.warn("BetterPV session auth skipped: missing Minecraft user session");
			setFailure(Failure.MISSING_SESSION, "blank username/uuid/token");
			return Optional.empty();
		}

		if (looksLikeOfflineDevSession(accessToken)) {
			BetterPV.LOGGER.warn(
				"BetterPV session JWT skipped: Fabric offline token user={}",
				username
			);
			setFailure(Failure.OFFLINE_SESSION, "FabricMC offline token");
			return Optional.empty();
		}

		long connectionGeneration = waitWhileConnecting(mc);
		if (connectionGeneration < 0L) return Optional.empty();
		String correlationId = UUID.randomUUID().toString();
		long authStartedAtMillis = System.currentTimeMillis();
		try {
			BetterPvInstallKey installKey = BetterPvInstallKey.load();
			String publicKey = installKey.publicKey();
			String challengeBody = "{\"username\":\"" + escapeJson(username)
				+ "\",\"publicKey\":\"" + publicKey + "\",\"protocol\":2}";
			HttpRequest challengeRequest = HttpRequest.newBuilder(AUTH_CHALLENGE_URI)
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("X-BetterPV-Correlation-ID", correlationId)
				.POST(HttpRequest.BodyPublishers.ofString(challengeBody, StandardCharsets.UTF_8))
				.build();
			logAuthStage(correlationId, "challenge_request_started", authStartedAtMillis, null);
			HttpResponse<String> challengeResponse = HTTP.send(challengeRequest, HttpResponse.BodyHandlers.ofString());
			logAuthStage(
				correlationId,
				"challenge_response",
				authStartedAtMillis,
				challengeResponse.statusCode(),
				responseOrigin(challengeResponse)
			);
			if (challengeResponse.statusCode() < 200 || challengeResponse.statusCode() >= 300
				|| challengeResponse.body() == null || challengeResponse.body().isBlank()) {
				setFailure(
					Failure.AUTH_HTTP,
					"challenge status=" + challengeResponse.statusCode()
						+ " origin=" + responseOrigin(challengeResponse)
						+ " correlationId=" + correlationId
				);
				return Optional.empty();
			}
			JsonObject challenge = JsonParser.parseString(challengeResponse.body()).getAsJsonObject();
			String challengeId = requiredString(challenge, "challengeId");
			String serverId = requiredString(challenge, "serverId");

			// Check on the client thread again after the challenge round trip. No network I/O
			// holds the lifecycle monitor, and vanilla never waits for our proof.
			if (cooldownActive()) return Optional.empty();
			if (waitWhileConnecting(mc) != connectionGeneration || !CONNECTION_GATE.beginProof(connectionGeneration)) {
				setFailure(Failure.SERVER_CONNECTING, "Minecraft connection changed before session proof");
				return Optional.empty();
			}
			Optional<String> joinError;
			boolean sameConnection;
			try {
				// A reconnect after admission can still overlap an already-issued Mojang request.
				// Authlib offers no reliable cancellation; do not stall vanilla to serialize it.
				logAuthStage(correlationId, "mojang_join_started", authStartedAtMillis, null);
				joinError = joinMinecraftSession(mc, profileId, accessToken, serverId, username);
			} finally {
				sameConnection = CONNECTION_GATE.finishProof(connectionGeneration);
			}

			if (joinError.isPresent()) {
				logAuthStage(correlationId, "mojang_join_failed", authStartedAtMillis, null);
				pauseAuth(classifyJoinFailure(joinError.get()), joinError.get());
				return Optional.empty();
			}
			logAuthStage(correlationId, "mojang_join_succeeded", authStartedAtMillis, null);
			// Also space successful proofs, including repeated backend 401 re-authentication.
			pauseAuth(Failure.AUTH_COOLDOWN, "Waiting before another Minecraft session proof");
			if (!sameConnection) {
				setFailure(Failure.SERVER_CONNECTING, "Minecraft connection changed during session proof");
				return Optional.empty();
			}

			String challengeTranscript = String.join("\n",
				"betterpv-auth-v2", challengeId, username, serverId, publicKey);
			String signature = installKey.sign(challengeTranscript);
			String body = "{\"challengeId\":\"" + challengeId
				+ "\",\"username\":\"" + escapeJson(username)
				+ "\",\"serverId\":\"" + serverId
				+ "\",\"publicKey\":\"" + publicKey
				+ "\",\"signature\":\"" + signature
				+ "\",\"protocol\":2}";
			HttpRequest request = HttpRequest.newBuilder(AUTH_URI)
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("X-BetterPV-Correlation-ID", correlationId)
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
			logAuthStage(correlationId, "exchange_request_started", authStartedAtMillis, null);
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			logAuthStage(correlationId, "exchange_response", authStartedAtMillis, status, responseOrigin(response));
			if (status == 502 || status == 504) {
				// The API keeps the signed challenge available after transient Mojang
				// failures. Retry this exact exchange once without another joinServer call.
				logAuthStage(correlationId, "exchange_retry_started", authStartedAtMillis, status);
				response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				status = response.statusCode();
				logAuthStage(
					correlationId,
					"exchange_retry_response",
					authStartedAtMillis,
					status,
					responseOrigin(response)
				);
			}
			String responseBody = response.body() == null ? "" : response.body();
			if (status == 503 || causeEquals(responseBody, "session_auth_unavailable")) {
				BetterPV.LOGGER.warn("BetterPV v2 auth unavailable status={}", status);
				setFailure(Failure.SERVER_AUTH_UNAVAILABLE, "status=" + status);
				return Optional.empty();
			}
			if (status < 200 || status >= 300 || responseBody.isBlank()) {
				BetterPV.LOGGER.warn("BetterPV v2 auth failed status={}", status);
				setFailure(
					status == 401 || status == 403 ? Failure.AUTH_REJECTED : Failure.AUTH_HTTP,
					"status=" + status + " origin=" + responseOrigin(response)
						+ " correlationId=" + correlationId
				);
				return Optional.empty();
			}

			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			if (root.has("success") && root.get("success").isJsonPrimitive() && !root.get("success").getAsBoolean()) {
				setFailure(Failure.AUTH_REJECTED, "success=false");
				return Optional.empty();
			}
			if (!root.has("token") || !root.get("token").isJsonPrimitive()) {
				setFailure(Failure.AUTH_REJECTED, "missing token field");
				return Optional.empty();
			}

			String token = root.get("token").getAsString();
			long expiresInSeconds = root.has("expiresIn") && root.get("expiresIn").isJsonPrimitive()
				? Math.max(1L, root.get("expiresIn").getAsLong())
				: 300L;
			synchronized (LOCK) {
				cachedJwt = token;
				expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L);
				usableUntilMillis = expiresAtMillis - Math.min(REFRESH_SKEW_MILLIS, expiresInSeconds * 1000L / 2L);
				scheduleRefresh();
			}
			setFailure(Failure.NONE, "");
			BetterPV.LOGGER.info(
				"BetterPV session token installed ttlSeconds={} refreshInMs={} proofCooldownMs={}",
				expiresInSeconds,
				Math.max(0L, usableUntilMillis - System.currentTimeMillis()),
				remainingAuthCooldownMillis()
			);
			logAuthStage(correlationId, "authentication_succeeded", authStartedAtMillis, status);
			return Optional.of(token);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("BetterPV v2 auth request failed", exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			setFailure(Failure.AUTH_HTTP, exception.toString());
			return Optional.empty();
		}
	}

	private static void logAuthStage(
		String correlationId,
		String stage,
		long startedAtMillis,
		Integer httpStatus
	) {
		logAuthStage(correlationId, stage, startedAtMillis, httpStatus, "none");
	}

	private static void logAuthStage(
		String correlationId,
		String stage,
		long startedAtMillis,
		Integer httpStatus,
		String origin
	) {
		BetterPV.LOGGER.info(
			"BetterPV auth correlationId={} stage={} elapsedMs={} httpStatus={} origin={}",
			correlationId,
			stage,
			Math.max(0L, System.currentTimeMillis() - startedAtMillis),
			httpStatus == null ? "none" : httpStatus,
			origin
		);
	}

	private static String responseOrigin(HttpResponse<?> response) {
		return response.headers().firstValue("X-BetterPV-Auth-Origin")
			.filter("application"::equalsIgnoreCase)
			.map(ignored -> "application")
			.orElse("proxy_or_edge");
	}

	/**
	 * Registers this client session with Mojang for {@code serverId}.
	 * Returns empty on success, or a short error tag on failure.
	 */
	private static Optional<String> joinMinecraftSession(
		Minecraft mc,
		UUID profileId,
		String accessToken,
		String serverId,
		String username
	) {
		try {
			// Access token is ONLY for official Minecraft session-service join; never sent to Vyriv.
			MinecraftSessionService sessionService = mc.services().sessionService();
			sessionService.joinServer(profileId, accessToken, serverId);
			return Optional.empty();
		} catch (InvalidCredentialsException exception) {
			BetterPV.LOGGER.warn(
				"Minecraft joinServer rejected credentials for BetterPV auth user={}",
				username
			);
			return Optional.of("InvalidCredentials: " + exception.toString());
		} catch (AuthenticationException exception) {
			BetterPV.LOGGER.warn("Minecraft joinServer failed for BetterPV auth: {}", exception.toString());
			return Optional.of(joinFailureDetail(exception));
		} catch (RuntimeException exception) {
			BetterPV.LOGGER.warn("Minecraft joinServer failed for BetterPV auth: {}", exception.toString());
			return Optional.of(joinFailureDetail(exception));
		}
	}

	private static String joinFailureDetail(Throwable failure) {
		StringBuilder detail = new StringBuilder();
		for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
			if (!detail.isEmpty()) detail.append("; caused by: ");
			detail.append(failure);
		}
		return detail.toString();
	}

	static Failure classifyJoinFailure(String detail) {
		String lower = detail.toLowerCase(Locale.ROOT);
		if (lower.contains("invalidcredentials")) return Failure.INVALID_CREDENTIALS;
		if (lower.contains("ratelimiter disallowed request") || lower.contains("rate limit")
			|| lower.contains("too many requests")) return Failure.MOJANG_RATE_LIMITED;
		return Failure.JOIN_SERVER_FAILED;
	}

	/** Called at connection start, before vanilla schedules authentication. Never waits for I/O. */
	public static void onConnectionStarting() {
		if (CONNECTION_GATE.connecting()) {
			BetterPV.LOGGER.debug("Minecraft connection started with a BetterPV proof already in flight; abandoning its result");
		}
	}

	public static void onPlayInit(Object listener) {
		CONNECTION_GATE.expectPlay(listener);
	}

	public static void onPlayReady(Object listener) {
		CONNECTION_GATE.playReady(listener);
	}

	public static void onPlayDisconnect(Object listener) {
		CONNECTION_GATE.disconnected(listener);
	}

	/** Client-thread snapshot, also used by prefetch so it does not consume its one shot too early. */
	public static boolean isReadyForSessionAuth(Minecraft client) {
		return connectionGeneration(client) >= 0L;
	}

	private static long connectionGeneration(Minecraft client) {
		if (client.screen instanceof ConnectScreen || client.player == null || client.level == null
			|| client.getConnection() == null || !client.getConnection().isAcceptingMessages()) return -1L;
		return CONNECTION_GATE.permit(client.getConnection());
	}

	// Read client-owned state on the client thread. Only BetterPV's worker waits, at most 2s.
	private static long waitWhileConnecting(Minecraft client) {
		try {
			long generation = client.submit(() -> connectionGeneration(client)).get(2L, TimeUnit.SECONDS);
			if (generation >= 0L) return generation;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
			BetterPV.LOGGER.debug("BetterPV session proof deferred: client connection state unavailable");
		}
		setFailure(Failure.SERVER_CONNECTING, "Minecraft play connection is not ready or is settling");
		return -1L;
	}

	/** Loom {@code runClient} default token. Real Microsoft sessions must still attempt joinServer. */
	static boolean looksLikeOfflineDevSession(String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			return true;
		}
		return "FabricMC".equalsIgnoreCase(accessToken.trim());
	}

	private static boolean causeEquals(String body, String cause) {
		if (body == null || body.isBlank() || cause == null) {
			return false;
		}
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			return root.has("cause")
				&& root.get("cause").isJsonPrimitive()
				&& cause.equalsIgnoreCase(root.get("cause").getAsString());
		} catch (RuntimeException ignored) {
			return body.contains(cause);
		}
	}

	private static String requiredString(JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
			throw new IllegalArgumentException("Missing auth response field: " + key);
		}
		String value = object.get(key).getAsString();
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Blank auth response field: " + key);
		}
		return value;
	}

	private static String requestTranscript(String method, String path, String timestamp, String nonce, String token)
		throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] tokenHash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
		String tokenHashHex = java.util.HexFormat.of().formatHex(tokenHash);
		return String.join("\n", "betterpv-request-v2", method, path, timestamp, nonce, tokenHashHex);
	}

	private static String escapeJson(String value) {
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
