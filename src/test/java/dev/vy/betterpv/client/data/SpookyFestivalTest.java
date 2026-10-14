package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

public class SpookyFestivalTest {
	@Test
	public void bestFestivalRankedByPointsNotCandy() {
		// Year 432 has more candy, year 443 has more points thanks to purple candy.
		JsonObject stats = JsonParser.parseString("""
			{"candy_collected": {
			  "total": 9000, "green_candy": 6000, "purple_candy": 3000,
			  "spooky_festival_432": {"total": 3499, "green_candy": 3200, "purple_candy": 299},
			  "spooky_festival_443": {"total": 2599, "green_candy": 1980, "purple_candy": 619}
			}}
			""").getAsJsonObject();

		MiscStatsSnapshot.SeasonalStats s = MiscStatsSnapshot.parseSeasonal(stats);

		assertEquals(443, s.bestFestivalYear());
		assertEquals(5075L, s.bestFestivalPoints());
		assertEquals(2599L, s.bestFestivalCandy());
		assertEquals(2, s.festivals());
		assertEquals(6098L, s.festivalHistoryCandy());
		assertTrue(s.festivalHistoryIncomplete());
	}

	@Test
	public void completeHistoryIsNotFlagged() {
		JsonObject stats = JsonParser.parseString("""
			{"candy_collected": {"total": 10, "green_candy": 10,
			  "spooky_festival_1": {"total": 10, "green_candy": 10}}}
			""").getAsJsonObject();
		MiscStatsSnapshot.SeasonalStats s = MiscStatsSnapshot.parseSeasonal(stats);
		assertFalse(s.festivalHistoryIncomplete());
		assertEquals(10L, s.bestFestivalPoints());
	}
}
