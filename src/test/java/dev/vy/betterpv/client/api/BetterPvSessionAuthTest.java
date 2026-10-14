package dev.vy.betterpv.client.api;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Tests auth admission without issuing session proofs or HTTP requests. */
public final class BetterPvSessionAuthTest {
	@org.junit.Test
	public void authAdmissionRegression() throws Exception {
		for (String text : new String[] {"AuthenticationException: RateLimiter disallowed request",
			"RATE LIMIT exceeded", "Too Many Requests"}) {
			check(BetterPvSessionAuth.classifyJoinFailure(text) == BetterPvSessionAuth.Failure.MOJANG_RATE_LIMITED,
				"Rate limiting must be recognized: " + text);
		}
		check(BetterPvSessionAuth.classifyJoinFailure("InvalidCredentials: rate limit")
			== BetterPvSessionAuth.Failure.INVALID_CREDENTIALS, "Credentials take precedence");
		check(BetterPvSessionAuth.classifyJoinFailure("AuthenticationException: unavailable")
			== BetterPvSessionAuth.Failure.JOIN_SERVER_FAILED, "Other failures stay distinct");

		String detail = "AuthenticationException: RateLimiter disallowed request";
		Method pause = method("pauseAuth", BetterPvSessionAuth.Failure.class, String.class);
		pause.invoke(null, BetterPvSessionAuth.Failure.MOJANG_RATE_LIMITED, detail);
		long deadline = (long) get("nextMojangAuthAttemptAtMillis");
		for (int i = 0; i < 100; i++) {
			BetterPvSessionAuth.invalidate();
			BetterPvSessionAuth.prefetchAsync();
			check(BetterPvSessionAuth.bearerTokenAsync().join().isEmpty(), "Async cooldown");
			check(BetterPvSessionAuth.ensureBearerToken().isEmpty(), "Blocking cooldown");
			URI uri = URI.create("https://example.invalid/hypixel/test");
			check(!BetterPvSessionAuth.applyAuthHeaders(HttpRequest.newBuilder(uri), uri), "Cache caller cooldown");
		}
		check(get("inFlight") == null, "Cooldown must not even queue authentication");
		check((long) get("nextMojangAuthAttemptAtMillis") == deadline, "Spam must not extend or clear cooldown");
		check(get("lastFailureDetail").equals(detail), "Original diagnostic detail retained");
		check(BetterPvSessionAuth.lastFailure() == BetterPvSessionAuth.Failure.MOJANG_RATE_LIMITED,
			"Diagnostic reason retained");
		check(((Optional<?>) method("authenticateOnce").invoke(null)).isEmpty(), "Worker cooldown guard");
		check(((CompletableFuture<?>) method("requestToken", boolean.class).invoke(null, true)).join()
			.equals(Optional.empty()), "Scheduled refresh cooldown guard");

		pause.invoke(null, BetterPvSessionAuth.Failure.AUTH_COOLDOWN, "Proof spacing");
		method("setFailure", BetterPvSessionAuth.Failure.class, String.class)
			.invoke(null, BetterPvSessionAuth.Failure.AUTH_HTTP, "status=502");
		check((boolean) method("cooldownActive").invoke(null), "Proof cooldown remains enforced after backend failure");
		check(BetterPvSessionAuth.lastFailure() == BetterPvSessionAuth.Failure.AUTH_HTTP,
			"Backend failure must remain visible during proof cooldown");
		check(get("lastFailureDetail").equals("status=502"), "Backend status retained during proof cooldown");

		set("nextMojangAuthAttemptAtMillis", System.currentTimeMillis() - 1L);
		check(!(boolean) method("cooldownActive").invoke(null), "Expired cooldown allows admission");
		CompletableFuture<Optional<String>> active = new CompletableFuture<>();
		set("inFlight", active);
		try (var callers = Executors.newFixedThreadPool(16)) {
			var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
			for (int i = 0; i < 64; i++) {
				results.add(callers.submit(() -> {
					BetterPvSessionAuth.invalidate();
					return BetterPvSessionAuth.bearerTokenAsync() == active;
				}));
			}
			for (var result : results) check(result.get(5, TimeUnit.SECONDS), "Shared single-flight future");
		}
		check(get("inFlight") == active, "Invalidation must preserve active work");
		active.complete(Optional.empty());
		set("inFlight", null);

		long now = System.currentTimeMillis();
		installCachedToken("test-jwt", now + 300_000L, now - 1L);
		pause.invoke(null, BetterPvSessionAuth.Failure.MOJANG_RATE_LIMITED, detail);
		check(BetterPvSessionAuth.ensureBearerToken().orElseThrow().equals("test-jwt"),
			"Valid JWT remains usable after its refresh threshold and during proof cooldown");

		CompletableFuture<Optional<String>> refresh = new CompletableFuture<>();
		set("inFlight", refresh);
		check(((CompletableFuture<?>) method("requestToken", boolean.class).invoke(null, false)).join()
			.equals(Optional.of("test-jwt")), "Regular callers must not wait for a background refresh");
		check(method("requestToken", boolean.class).invoke(null, true) == refresh,
			"Forced refresh must reuse active authentication");
		set("inFlight", null);
		refresh.complete(Optional.empty());

		check(BetterPvSessionAuth.unauthorizedCause("{\"cause\":\"INVALID_REQUEST_PROOF\"}")
			.equals("invalid_request_proof"), "401 cause parsing");
		check(BetterPvSessionAuth.unauthorizedCause("<html>proxy response</html>").equals("unknown"),
			"Non-JSON 401 cause is unknown");
		check(!BetterPvSessionAuth.invalidatesSession("invalid_request_proof"),
			"Request proof failure is not a session rejection");
		check(BetterPvSessionAuth.invalidatesSession("session_revoked_or_expired"),
			"Revoked session invalidates its JWT");

		check(!BetterPvSessionAuth.handleUnauthorized(
			request("test-jwt"), "{\"success\":false,\"cause\":\"invalid_request_proof\"}"
		), "Request proof 401 must preserve the current JWT");
		check(get("cachedJwt").equals("test-jwt"), "Proof failure retains current JWT");
		check(BetterPvSessionAuth.ensureBearerToken().orElseThrow().equals("test-jwt"),
			"Proof failure cannot expose AUTH_COOLDOWN while the JWT remains valid");
		check(!BetterPvSessionAuth.handleUnauthorized(
			request("test-jwt"), "{\"success\":false,\"cause\":\"request_replayed\"}"
		), "Replay rejection must preserve the current JWT");
		check(!BetterPvSessionAuth.handleUnauthorized(request("test-jwt"), "gateway response"),
			"Unclassified proxy 401 must not destroy a valid session");

		BetterPvSessionAuth.handleUnauthorized(request("old-jwt"), "{\"cause\":\"unauthorized\"}");
		check(get("cachedJwt").equals("test-jwt"), "Late 401 must not invalidate a replacement JWT");

		set("lastUsedAtMillis", System.currentTimeMillis());
		set("nextMojangAuthAttemptAtMillis", System.currentTimeMillis() + 500L);
		method("scheduleRefreshLocked", long.class).invoke(null, 1L);
		Thread.sleep(50L);
		check(get("pendingRefresh") != null, "Scheduled refresh must defer during proof cooldown");
		check(get("inFlight") == null, "Deferred refresh must not start another Mojang proof");

		set("nextMojangAuthAttemptAtMillis", System.currentTimeMillis() + 60_000L);
		installCachedToken(
			"test-jwt",
			System.currentTimeMillis() + 300_000L,
			System.currentTimeMillis() + 240_000L
		);
		method("scheduleRefresh").invoke(null);
		ScheduledFuture<?> first = (ScheduledFuture<?>) get("pendingRefresh");
		check(first.getDelay(TimeUnit.MILLISECONDS) > 235_000L,
			"Five-minute JWT refresh is scheduled near the 60-second refresh threshold");
		method("scheduleRefresh").invoke(null);
		check(first.isCancelled(), "Replacement refresh cancels previous task");
		ScheduledFuture<?> second = (ScheduledFuture<?>) get("pendingRefresh");
		check(BetterPvSessionAuth.handleUnauthorized(
			request("test-jwt"), "{\"cause\":\"session_revoked_or_expired\"}"
		), "Explicit session rejection invalidates the current JWT");
		check(get("cachedJwt") == null && second.isCancelled(), "Session rejection clears JWT and refresh");
		check(BetterPvSessionAuth.ensureBearerToken().isEmpty(),
			"A revoked session cannot bypass Mojang proof cooldown");
		System.out.println("BetterPV session auth regression checks passed");
	}

	@org.junit.Test
	public void sessionRenewalRegression() throws Exception {
		var previousTransport = BetterPvSessionAuth.renewTransport;
		var previousSigner = BetterPvSessionAuth.requestSigner;
		var sent = new java.util.concurrent.CopyOnWriteArrayList<HttpRequest>();
		var reply = new java.util.concurrent.atomic.AtomicReference<BetterPvSessionAuth.RenewReply>();
		var beforeReply = new java.util.concurrent.atomic.AtomicReference<Runnable>(() -> {});
		resetRenewalState();
		try {
			BetterPvSessionAuth.requestSigner = transcript -> {
				check(transcript.startsWith("betterpv-request-v2\nPOST\n/hypixel/auth/v2/refresh\n"),
					"Refresh proof signs the POST refresh path");
				return "signature";
			};
			BetterPvSessionAuth.renewTransport = request -> {
				sent.add(request);
				beforeReply.get().run();
				if (reply.get() == null) throw new java.io.IOException("offline");
				return reply.get();
			};
			// Holds the Mojang path closed: renewal must never depend on it.
			method("pauseAuth", BetterPvSessionAuth.Failure.class, String.class)
				.invoke(null, BetterPvSessionAuth.Failure.AUTH_COOLDOWN, "test cooldown");
			long mojangDeadline = (long) get("nextMojangAuthAttemptAtMillis");

			installRenewable("jwt-1");
			reply.set(renewed("jwt-2", 300, 86_000));
			check(refresh().equals(Optional.of("jwt-2")), "Renewal returns the rotated JWT");
			HttpRequest request = sent.get(0);
			check(request.method().equals("POST") && request.uri().getPath().equals("/hypixel/auth/v2/refresh"),
				"Renewal posts to the refresh route");
			check(request.headers().firstValue("Authorization").orElse("").equals("Bearer jwt-1"),
				"Renewal presents the current JWT");
			check(request.headers().firstValue("X-BetterPV-Signature").isPresent()
				&& request.headers().firstValue("X-BetterPV-Nonce").isPresent(), "Renewal carries a request proof");
			check(get("cachedJwt").equals("jwt-2"), "Rotated JWT installed");
			long deadline = (long) get("sessionDeadlineMillis");
			check(deadline > System.currentTimeMillis() + 85_000_000L, "Session deadline follows the API");
			check(((ScheduledFuture<?>) get("pendingRefresh")).getDelay(TimeUnit.MILLISECONDS) > 235_000L,
				"Next renewal scheduled before the new JWT expires");
			check((long) get("nextMojangAuthAttemptAtMillis") == mojangDeadline, "Renewal never touches Mojang");

			for (BetterPvSessionAuth.RenewReply transient_ : new BetterPvSessionAuth.RenewReply[] {
				new BetterPvSessionAuth.RenewReply(503, "{\"cause\":\"unavailable\"}", "application"), null}) {
				installRenewable("jwt-3");
				reply.set(transient_);
				check(refresh().equals(Optional.of("jwt-3")), "Transient refresh failure keeps the valid JWT");
				check(get("cachedJwt").equals("jwt-3"), "Transient failure does not invalidate");
				check(((ScheduledFuture<?>) get("pendingRefresh")).getDelay(TimeUnit.MILLISECONDS) <= 10_000L,
					"Transient failure retries soon");
			}

			installRenewable("jwt-4");
			reply.set(new BetterPvSessionAuth.RenewReply(401, "{\"cause\":\"session_reauth_required\"}", "application"));
			check(refresh().isEmpty(), "Proof fallback stays behind the Mojang cooldown");
			check(get("cachedJwt").equals("jwt-4") && (long) get("sessionDeadlineMillis") == 0L,
				"Unrenewable session keeps its JWT until expiry");
			check(BetterPvSessionAuth.ensureBearerToken().equals(Optional.of("jwt-4")),
				"Callers still get the unexpired JWT");

			installRenewable("jwt-5");
			reply.set(new BetterPvSessionAuth.RenewReply(401, "{\"cause\":\"session_revoked_or_expired\"}", "application"));
			check(refresh().isEmpty() && get("cachedJwt") == null, "Revoked session drops its JWT");

			installRenewable("jwt-6");
			reply.set(new BetterPvSessionAuth.RenewReply(403, "{\"cause\":\"access_restricted\"}", "application"));
			refresh();
			check(BetterPvSessionAuth.lastFailure() == BetterPvSessionAuth.Failure.AUTH_REJECTED,
				"Restricted account is reported");
			check((long) get("sessionDeadlineMillis") == 0L, "Restricted account stops renewing");

			installRenewable("jwt-7");
			reply.set(renewed("jwt-lost", 300, 86_000));
			beforeReply.set(() -> {
				try {
					installCachedToken("jwt-newer", System.currentTimeMillis() + 300_000L, System.currentTimeMillis() + 240_000L);
				} catch (Exception exception) {
					throw new IllegalStateException(exception);
				}
			});
			check(refresh().equals(Optional.of("jwt-newer")), "A newer JWT wins over a late renewal");
			check(get("cachedJwt").equals("jwt-newer"), "Late renewal does not overwrite a newer JWT");
			beforeReply.set(() -> {});

			installRenewable("jwt-8");
			set("lastUsedAtMillis", 0L);
			var renewedOnSchedule = new java.util.concurrent.CountDownLatch(1);
			reply.set(renewed("jwt-9", 300, 86_000));
			beforeReply.set(renewedOnSchedule::countDown);
			method("scheduleRefreshLocked", long.class).invoke(null, 1L);
			check(renewedOnSchedule.await(5, TimeUnit.SECONDS), "Idle sessions keep renewing without /pv use");
			for (int i = 0; i < 100 && !"jwt-9".equals(get("cachedJwt")); i++) Thread.sleep(10L);
			check(get("cachedJwt").equals("jwt-9"), "Scheduled renewal installs the rotated JWT");
		} finally {
			BetterPvSessionAuth.renewTransport = previousTransport;
			BetterPvSessionAuth.requestSigner = previousSigner;
			resetRenewalState();
		}
	}

	private static Optional<?> refresh() throws Exception {
		return (Optional<?>) ((CompletableFuture<?>) method("requestToken", boolean.class).invoke(null, true))
			.get(5, TimeUnit.SECONDS);
	}

	private static void installRenewable(String token) throws Exception {
		long now = System.currentTimeMillis();
		BetterPvSessionAuth.invalidate();
		installCachedToken(token, now + 60_000L, now - 1L);
		set("sessionDeadlineMillis", now + 3_600_000L);
		set("renewFailures", 0);
	}

	private static BetterPvSessionAuth.RenewReply renewed(String token, int expiresIn, int sessionExpiresIn) {
		return new BetterPvSessionAuth.RenewReply(200, "{\"success\":true,\"token\":\"" + token + "\",\"expiresIn\":"
			+ expiresIn + ",\"sessionExpiresIn\":" + sessionExpiresIn + ",\"protocol\":2}", "application");
	}

	private static void resetRenewalState() throws Exception {
		BetterPvSessionAuth.invalidate();
		CompletableFuture<?> active = (CompletableFuture<?>) get("inFlight");
		if (active != null) active.get(5, TimeUnit.SECONDS);
		set("inFlight", null);
		set("nextMojangAuthAttemptAtMillis", 0L);
		set("renewFailures", 0);
		method("setFailure", BetterPvSessionAuth.Failure.class, String.class)
			.invoke(null, BetterPvSessionAuth.Failure.NONE, "");
	}

	private static void installCachedToken(String token, long expiresAt, long refreshAt) throws Exception {
		set("cachedJwt", token);
		set("expiresAtMillis", expiresAt);
		set("usableUntilMillis", refreshAt);
	}

	private static HttpRequest request(String token) {
		return HttpRequest.newBuilder(URI.create("https://example.invalid/test"))
			.header("Authorization", "Bearer " + token).build();
	}

	private static Method method(String name, Class<?>... types) throws Exception {
		Method method = BetterPvSessionAuth.class.getDeclaredMethod(name, types);
		method.setAccessible(true);
		return method;
	}

	private static Field field(String name) throws Exception {
		Field field = BetterPvSessionAuth.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static Object get(String name) throws Exception { return field(name).get(null); }
	private static void set(String name, Object value) throws Exception { field(name).set(null, value); }
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
