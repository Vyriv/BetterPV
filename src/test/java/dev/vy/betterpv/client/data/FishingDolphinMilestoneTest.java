package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonParser;
import org.junit.Test;

public class FishingDolphinMilestoneTest {
	@Test
	public void killsAndDolphinMilestoneComeFromSeparateFields() {
		FishingSnapshot snap = FishingSnapshot.fromMember(JsonParser.parseString("""
			{"player_stats": {"sea_creature_kills": 35043, "pets": {"milestone": {"sea_creatures_killed": 85499}}}}
			""").getAsJsonObject());

		assertEquals(35043L, snap.seaCreatureKills());
		assertEquals(85499L, snap.dolphinMilestone());
	}

	@Test
	public void missingMilestoneIsHiddenNotZero() {
		FishingSnapshot snap = FishingSnapshot.fromMember(JsonParser.parseString("""
			{"player_stats": {"sea_creature_kills": 12}}
			""").getAsJsonObject());

		assertEquals(12L, snap.seaCreatureKills());
		assertEquals(-1L, snap.dolphinMilestone());
	}
}
