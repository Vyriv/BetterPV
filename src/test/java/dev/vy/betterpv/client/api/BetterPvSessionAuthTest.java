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

		set("cachedJwt", "test-jwt");
		set("usableUntilMillis", System.currentTimeMillis() + 300_000L);
		pause.invoke(null, BetterPvSessionAuth.Failure.MOJANG_RATE_LIMITED, detail);
		check(BetterPvSessionAuth.ensureBearerToken().orElseThrow().equals("test-jwt"), "Cached JWT bypasses proof");
		BetterPvSessionAuth.invalidate(request("old-jwt"));
		check(get("cachedJwt").equals("test-jwt"), "Late 401 must not invalidate a replacement JWT");
		method("scheduleRefresh").invoke(null);
		ScheduledFuture<?> first = (ScheduledFuture<?>) get("pendingRefresh");
		method("scheduleRefresh").invoke(null);
		check(first.isCancelled(), "Replacement refresh cancels previous task");
		ScheduledFuture<?> second = (ScheduledFuture<?>) get("pendingRefresh");
		BetterPvSessionAuth.invalidate(request("test-jwt"));
		check(get("cachedJwt") == null && second.isCancelled(), "Current 401 clears JWT and refresh");
		check(BetterPvSessionAuth.ensureBearerToken().isEmpty(), "401 cannot bypass Mojang cooldown");
		System.out.println("BetterPV session auth regression checks passed");
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
