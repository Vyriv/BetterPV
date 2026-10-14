package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import org.junit.Test;

public class PlayerStatusTest {
	private static final long NOW = 1_790_000_000_000L;
	private static final long MINUTE = 60_000L;
	private static final long HOUR = 60 * MINUTE;
	private static final long DAY = 24 * HOUR;

	@Test
	public void onlineShowsLocationOnly() {
		assertEquals("Private Island", PlayerStatus.online("SKYBLOCK", "dynamic", "").buttonLabel(NOW));
		assertEquals("Hub", PlayerStatus.online("SKYBLOCK", "hub", "").buttonLabel(NOW));
		assertEquals("The Garden", PlayerStatus.online("SKYBLOCK", "garden", "").buttonLabel(NOW));
		assertEquals("Crystal Hollows", PlayerStatus.online("SKYBLOCK", "crystal_hollows", "").buttonLabel(NOW));
		assertEquals("Bed Wars", PlayerStatus.online("BEDWARS", "", "").buttonLabel(NOW));
	}

	@Test
	public void onlineMapBeatsMode() {
		assertEquals("Ruins", PlayerStatus.online("SKYWARS", "solo_normal", "Ruins").buttonLabel(NOW));
	}

	@Test
	public void onlineWithoutLocationIsHidden() {
		assertEquals(PlayerStatus.LOCATION_HIDDEN, PlayerStatus.online("", "", "").buttonLabel(NOW));
		assertEquals(PlayerStatus.LOCATION_HIDDEN, PlayerStatus.online(null, null, null).buttonLabel(NOW));
	}

	@Test
	public void offlineWithoutTimestampIsPlainOffline() {
		assertEquals("Offline", PlayerStatus.offline().buttonLabel(NOW));
		assertEquals("Offline", PlayerStatus.offline(0L).buttonLabel(NOW));
		assertEquals("Offline", PlayerStatus.offline(-5L).buttonLabel(NOW));
	}

	@Test
	public void lastOnlineUnitsAndPlurals() {
		assertEquals("Last online just now", PlayerStatus.lastOnlineLabel(NOW - 59_999L, NOW));
		assertEquals("Last online 1 minute ago", PlayerStatus.lastOnlineLabel(NOW - MINUTE, NOW));
		assertEquals("Last online 2 minutes ago", PlayerStatus.lastOnlineLabel(NOW - 2 * MINUTE - 30_000L, NOW));
		assertEquals("Last online 59 minutes ago", PlayerStatus.lastOnlineLabel(NOW - HOUR + 1, NOW));
		assertEquals("Last online 1 hour ago", PlayerStatus.lastOnlineLabel(NOW - HOUR, NOW));
		assertEquals("Last online 23 hours ago", PlayerStatus.lastOnlineLabel(NOW - DAY + 1, NOW));
		assertEquals("Last online 1 day ago", PlayerStatus.lastOnlineLabel(NOW - DAY, NOW));
		assertEquals("Last online 400 days ago", PlayerStatus.lastOnlineLabel(NOW - 400 * DAY, NOW));
	}

	@Test
	public void futureTimestampNeverGoesNegative() {
		assertEquals("Last online just now", PlayerStatus.lastOnlineLabel(NOW + 3 * MINUTE, NOW));
	}

	@Test
	public void offlineLabelUsesTimestamp() {
		assertEquals("Last online 3 hours ago", PlayerStatus.offline(NOW - 3 * HOUR).buttonLabel(NOW));
	}

	@Test
	public void normalizeAcceptsSecondsAndMillis() {
		long ms = 1_700_000_000_000L;
		assertEquals(ms, PlayerStatus.normalizeTimestamp(ms));
		assertEquals(ms, PlayerStatus.normalizeTimestamp(ms / 1000L));
		assertEquals(0L, PlayerStatus.normalizeTimestamp(0L));
		assertEquals(0L, PlayerStatus.normalizeTimestamp(-1L));
		assertEquals(0L, PlayerStatus.normalizeTimestamp(1_000L));
	}

	@Test
	public void lastOnlineFromPlayerUsesLogout() {
		JsonObject player = new JsonObject();
		player.addProperty("lastLogin", NOW - 5 * HOUR);
		player.addProperty("lastLogout", NOW - 2 * HOUR);
		assertEquals(NOW - 2 * HOUR, PlayerStatus.lastOnlineFromPlayer(player, NOW));
	}

	@Test
	public void lastOnlineFromPlayerAcceptsSeconds() {
		JsonObject player = new JsonObject();
		player.addProperty("lastLogin", (NOW - 5 * HOUR) / 1000L);
		player.addProperty("lastLogout", (NOW - 2 * HOUR) / 1000L);
		assertEquals(NOW - 2 * HOUR, PlayerStatus.lastOnlineFromPlayer(player, NOW));
	}

	@Test
	public void lastOnlineFromPlayerRejectsUnreliableData() {
		assertEquals(0L, PlayerStatus.lastOnlineFromPlayer(null, NOW));
		assertEquals(0L, PlayerStatus.lastOnlineFromPlayer(new JsonObject(), NOW));

		JsonObject future = new JsonObject();
		future.addProperty("lastLogout", NOW + DAY);
		assertEquals(0L, PlayerStatus.lastOnlineFromPlayer(future, NOW));

		JsonObject text = new JsonObject();
		text.addProperty("lastLogout", "yesterday");
		assertEquals(0L, PlayerStatus.lastOnlineFromPlayer(text, NOW));

		JsonObject ancient = new JsonObject();
		ancient.addProperty("lastLogin", 1_000L);
		ancient.addProperty("lastLogout", 0L);
		assertEquals(0L, PlayerStatus.lastOnlineFromPlayer(ancient, NOW));
	}

	@Test
	public void staleLogoutFallsBackToNewerLogin() {
		// Hypixel sometimes never writes lastLogout, so an offline player can have lastLogin > lastLogout.
		JsonObject player = new JsonObject();
		player.addProperty("lastLogin", NOW - 3 * HOUR);
		player.addProperty("lastLogout", NOW - 40 * DAY);
		assertEquals(NOW - 3 * HOUR, PlayerStatus.lastOnlineFromPlayer(player, NOW));
		assertEquals("Last online 3 hours ago", PlayerStatus.offlineFromPlayer(player, NOW).buttonLabel(NOW));
	}

	@Test
	public void loginOnlyStillGivesATime() {
		JsonObject player = new JsonObject();
		player.addProperty("lastLogin", NOW - 2 * DAY);
		assertEquals("Last online 2 days ago", PlayerStatus.offlineFromPlayer(player, NOW).buttonLabel(NOW));
	}

	@Test
	public void futureLoginIgnoredButLogoutKept() {
		JsonObject player = new JsonObject();
		player.addProperty("lastLogin", NOW + DAY);
		player.addProperty("lastLogout", NOW - 12 * MINUTE);
		assertEquals("Last online 12 minutes ago", PlayerStatus.offlineFromPlayer(player, NOW).buttonLabel(NOW));
	}

	@Test
	public void hiddenOnlineStatusStaysPlainOffline() {
		JsonObject player = new JsonObject();
		player.addProperty("lastLogin", NOW - HOUR);
		player.addProperty("lastLogout", NOW - 30 * MINUTE);
		JsonObject settings = new JsonObject();
		settings.addProperty("apiSession", false);
		player.add("settings", settings);
		assertTrue(PlayerStatus.sessionHidden(player));
		PlayerStatus status = PlayerStatus.offlineFromPlayer(player, NOW);
		assertTrue(status.sessionHidden());
		assertEquals(0L, status.lastOnlineMs());
		assertEquals("Offline", status.buttonLabel(NOW));
	}

	@Test
	public void apiSessionTrueOrUnsetIsNotHidden() {
		JsonObject unset = new JsonObject();
		assertFalse(PlayerStatus.sessionHidden(unset));
		JsonObject visible = new JsonObject();
		JsonObject settings = new JsonObject();
		settings.addProperty("apiSession", true);
		visible.add("settings", settings);
		assertFalse(PlayerStatus.sessionHidden(visible));
		assertFalse(PlayerStatus.offlineFromPlayer(null, NOW).sessionHidden());
		assertEquals("Offline", PlayerStatus.offlineFromPlayer(null, NOW).buttonLabel(NOW));
	}

	@Test
	public void diagnosticsShowRawFields() {
		JsonObject player = new JsonObject();
		player.addProperty("lastLogin", 1_700_000_000_000L);
		assertEquals(
			"lastLogin=1700000000000 lastLogout=missing apiSession=missing",
			PlayerStatus.timestampDiagnostics(player)
		);
		assertEquals("player=missing", PlayerStatus.timestampDiagnostics(null));
	}

	@Test
	public void slightClockSkewStillCounts() {
		JsonObject player = new JsonObject();
		player.addProperty("lastLogout", NOW + MINUTE);
		assertEquals(NOW + MINUTE, PlayerStatus.lastOnlineFromPlayer(player, NOW));
		assertEquals("Last online just now", PlayerStatus.offline(NOW + MINUTE).buttonLabel(NOW));
	}

	@Test
	public void loadingAndIdleUnchanged() {
		assertEquals("Status", PlayerStatus.idle().buttonLabel(NOW));
		assertEquals("...", PlayerStatus.loading().buttonLabel(NOW));
		assertEquals("Status", PlayerStatus.error("x").buttonLabel(NOW));
	}
}
