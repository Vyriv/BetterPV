package dev.vy.betterpv.client.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.api.SessionAuthPolicy.Action;
import dev.vy.betterpv.client.api.SessionAuthPolicy.CredentialBackoff;
import dev.vy.betterpv.client.api.SessionAuthPolicy.RenewOutcome;
import java.util.UUID;
import org.junit.Test;

public final class SessionAuthPolicyTest {
	private static final long SECOND = 1_000L;
	private static final long MINUTE = 60_000L;
	private static final long HOUR = 60L * MINUTE;
	private static final UUID ALICE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID BOB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

	@Test
	public void renewsOnlyLiveRenewableTokens() {
		assertEquals(Action.USE_CACHED, SessionAuthPolicy.nextAction(true, false, 0L));
		assertEquals(Action.RENEW, SessionAuthPolicy.nextAction(true, true, HOUR));
		assertEquals(Action.PROOF, SessionAuthPolicy.nextAction(true, true, 29 * SECOND));
		assertEquals(Action.PROOF, SessionAuthPolicy.nextAction(false, true, HOUR));
		assertEquals(Action.PROOF, SessionAuthPolicy.nextAction(false, false, HOUR));
	}

	@Test
	public void classifiesRefreshResponses() {
		assertEquals(RenewOutcome.RENEWED, SessionAuthPolicy.classifyRenewal(200, "unknown"));
		assertEquals(RenewOutcome.NEEDS_PROOF, SessionAuthPolicy.classifyRenewal(404, "unknown"));
		assertEquals(RenewOutcome.NEEDS_PROOF, SessionAuthPolicy.classifyRenewal(401, "session_reauth_required"));
		assertEquals(RenewOutcome.NEEDS_PROOF, SessionAuthPolicy.classifyRenewal(401, "session_refresh_rejected"));
		assertEquals(RenewOutcome.SESSION_DEAD, SessionAuthPolicy.classifyRenewal(401, "session_revoked_or_expired"));
		assertEquals(RenewOutcome.SESSION_DEAD, SessionAuthPolicy.classifyRenewal(403, "session_revoked_or_expired"));
		assertEquals(RenewOutcome.SESSION_DEAD, SessionAuthPolicy.classifyRenewal(401, "unauthorized"));
		assertEquals(RenewOutcome.BLOCKED, SessionAuthPolicy.classifyRenewal(403, "access_restricted"));
		for (String cause : new String[] {"invalid_request_proof", "request_replayed", "unknown"}) {
			assertEquals(cause, RenewOutcome.RETRY, SessionAuthPolicy.classifyRenewal(401, cause));
		}
		for (int status : new int[] {-1, 429, 500, 502, 503, 504}) {
			assertEquals(RenewOutcome.RETRY, SessionAuthPolicy.classifyRenewal(status, "unknown"));
		}
	}

	@Test
	public void refreshRetriesStayInsideTheTokenLifetime() {
		assertEquals(10 * SECOND, SessionAuthPolicy.renewRetryDelayMillis(1, 60 * SECOND));
		assertEquals(15 * SECOND, SessionAuthPolicy.renewRetryDelayMillis(2, 50 * SECOND));
		assertEquals(20 * SECOND, SessionAuthPolicy.renewRetryDelayMillis(3, 35 * SECOND));
		assertEquals(-1L, SessionAuthPolicy.renewRetryDelayMillis(4, HOUR));
		assertEquals(-1L, SessionAuthPolicy.renewRetryDelayMillis(1, 15 * SECOND));
		assertEquals(-1L, SessionAuthPolicy.renewRetryDelayMillis(0, HOUR));
	}

	@Test
	public void credentialKeysSeparateAccountsAndTokensWithoutExposingThem() {
		String key = SessionAuthPolicy.credentialKey(ALICE, "launcher-token-1");
		assertEquals(key, SessionAuthPolicy.credentialKey(ALICE, "launcher-token-1"));
		assertNotEquals(key, SessionAuthPolicy.credentialKey(ALICE, "launcher-token-2"));
		assertNotEquals(key, SessionAuthPolicy.credentialKey(BOB, "launcher-token-1"));
		assertFalse(key.contains("launcher-token-1"));
		assertEquals(64, key.length());
		assertNull(SessionAuthPolicy.credentialKey(ALICE, " "));
		assertNull(SessionAuthPolicy.credentialKey(null, "launcher-token-1"));
	}

	@Test
	public void rejectedLoginsBackOffAndResetForANewLogin() {
		CredentialBackoff backoff = new CredentialBackoff();
		String alice = SessionAuthPolicy.credentialKey(ALICE, "expired");
		assertEquals(MINUTE, backoff.recordInvalid(alice));
		assertEquals(5 * MINUTE, backoff.recordInvalid(alice));
		assertEquals(15 * MINUTE, backoff.recordInvalid(alice));
		assertEquals(30 * MINUTE, backoff.recordInvalid(alice));
		assertEquals(30 * MINUTE, backoff.recordInvalid(alice));
		assertTrue(backoff.appliesTo(alice));
		assertFalse(backoff.appliesTo(SessionAuthPolicy.credentialKey(ALICE, "relogged")));
		assertEquals(MINUTE, backoff.recordInvalid(SessionAuthPolicy.credentialKey(ALICE, "relogged")));
		backoff.reset();
		assertFalse(backoff.appliesTo(alice));
	}

	@Test
	public void launcherTokenExpiringMidGameDoesNotEndARenewableSession() {
		// Tiriaa: 15 proofs every 4 minutes, then InvalidCredentials about an hour after launch.
		SimulatedClient client = new SimulatedClient(ALICE, 64 * MINUTE, true);
		client.run(6 * HOUR);
		assertEquals(1, client.mojangCalls);
		assertEquals(0, client.mojangRejections);
		assertEquals(0, client.ticksWithoutToken);
		assertTrue(client.renewals >= 6 * 14);
	}

	@Test
	public void proofOnlyApiBacksOffInsteadOfHammeringMojang() {
		SimulatedClient client = new SimulatedClient(ALICE, 64 * MINUTE, false);
		client.run(6 * HOUR);
		// The old client retried every minute: about 300 rejected proofs over the same five hours.
		assertTrue("rejections=" + client.mojangRejections, client.mojangRejections <= 14);
		assertTrue(client.mojangRejections >= 4);
		assertTrue(client.ticksWithoutToken > 0);
	}

	@Test
	public void sessionDeadlineRequiresOneFreshProof() {
		SimulatedClient client = new SimulatedClient(ALICE, Long.MAX_VALUE, true);
		client.run(25 * HOUR);
		assertEquals(2, client.mojangCalls);
		assertEquals(0, client.ticksWithoutToken);

		SimulatedClient expired = new SimulatedClient(ALICE, 2 * HOUR, true);
		expired.run(26 * HOUR);
		assertEquals(0, expired.ticksWithoutTokenBefore(24 * HOUR));
		assertTrue(expired.mojangRejections > 0);
	}

	@Test
	public void twoPlayersFailingIndependently() {
		// OrangeJosh's launcher token dies early; Tiriaa's later. Neither affects the other's session.
		SimulatedClient josh = new SimulatedClient(ALICE, 10 * MINUTE, true);
		SimulatedClient tiriaa = new SimulatedClient(BOB, 64 * MINUTE, true);
		josh.run(3 * HOUR);
		tiriaa.run(3 * HOUR);
		assertEquals(0, josh.ticksWithoutToken);
		assertEquals(0, tiriaa.ticksWithoutToken);
		assertEquals(1, josh.mojangCalls);
		assertEquals(1, tiriaa.mojangCalls);
	}

	@Test
	public void switchingAccountsLiftsTheOtherAccountsBackoff() {
		CredentialBackoff backoff = new CredentialBackoff();
		String aliceExpired = SessionAuthPolicy.credentialKey(ALICE, "expired");
		backoff.recordInvalid(aliceExpired);
		backoff.recordInvalid(aliceExpired);
		backoff.recordInvalid(aliceExpired);
		String bob = SessionAuthPolicy.credentialKey(BOB, "fresh");
		assertFalse("Bob must not wait for Alice's expired login", backoff.appliesTo(bob));
		backoff.reset();
		assertFalse(backoff.appliesTo(aliceExpired));
	}

	/** Mirrors BetterPvSessionAuth's scheduler with a player using /pv throughout. */
	private static final class SimulatedClient {
		private static final long TICK = 10 * SECOND;
		private static final long JWT_TTL = 5 * MINUTE;
		private static final long SESSION_MAX_AGE = 24 * HOUR;

		private final String credential;
		private final long launcherTokenDiesAt;
		private final boolean apiRefresh;
		private final CredentialBackoff backoff = new CredentialBackoff();
		private long expiresAt;
		private long refreshAt;
		private long sessionDeadline;
		private long serverDeadline;
		private long proofCooldownUntil;
		private long firstGapAt = -1L;
		int mojangCalls;
		int mojangRejections;
		int renewals;
		int ticksWithoutToken;

		SimulatedClient(UUID profile, long launcherTokenDiesAt, boolean apiRefresh) {
			this.credential = SessionAuthPolicy.credentialKey(profile, "launcher-token");
			this.launcherTokenDiesAt = launcherTokenDiesAt;
			this.apiRefresh = apiRefresh;
		}

		void run(long duration) {
			for (long now = 0L; now <= duration; now += TICK) tick(now);
		}

		int ticksWithoutTokenBefore(long time) {
			return firstGapAt >= 0L && firstGapAt < time ? 1 : 0;
		}

		private void tick(long now) {
			boolean valid = now < expiresAt - 5 * SECOND;
			Action action = SessionAuthPolicy.nextAction(valid, !valid || now >= refreshAt, sessionDeadline - now);
			if (action == Action.RENEW) renew(now);
			else if (action == Action.PROOF && now >= proofCooldownUntil) proof(now);
			if (now >= expiresAt - 5 * SECOND) {
				ticksWithoutToken++;
				if (firstGapAt < 0L) firstGapAt = now;
			}
		}

		private void renew(long now) {
			int status = !apiRefresh ? 404 : serverDeadline - now < 30 * SECOND ? 401 : 200;
			RenewOutcome outcome = SessionAuthPolicy.classifyRenewal(status, status == 401 ? "session_reauth_required" : "unknown");
			if (outcome == RenewOutcome.RENEWED) {
				renewals++;
				install(now, Math.min(JWT_TTL, serverDeadline - now));
				return;
			}
			assertEquals(RenewOutcome.NEEDS_PROOF, outcome);
			sessionDeadline = 0L;
			if (now >= proofCooldownUntil) proof(now);
		}

		private void proof(long now) {
			mojangCalls++;
			if (now >= launcherTokenDiesAt) {
				mojangRejections++;
				proofCooldownUntil = now + backoff.recordInvalid(credential);
				return;
			}
			backoff.reset();
			proofCooldownUntil = now + MINUTE;
			serverDeadline = now + SESSION_MAX_AGE;
			install(now, JWT_TTL);
		}

		private void install(long now, long ttl) {
			expiresAt = now + ttl;
			refreshAt = expiresAt - Math.min(MINUTE, ttl / 2L);
			sessionDeadline = apiRefresh ? serverDeadline : 0L;
		}
	}
}
