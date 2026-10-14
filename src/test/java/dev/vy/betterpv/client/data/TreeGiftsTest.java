package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;

public class TreeGiftsTest {
	@Test
	public void readsFigMangroveAndHelix() {
		List<ForagingSnapshot.TreeGift> gifts = ForagingSnapshot.parseTreeGifts(JsonParser.parseString("""
			{"FIG": 120, "MANGROVE": 45, "HELIX": 7,
			 "milestone_tier_claimed": {"FIG": 3, "MANGROVE": 1, "HELIX": 1}}
			""").getAsJsonObject());

		assertEquals(List.of("FIG", "MANGROVE", "HELIX"), gifts.stream().map(ForagingSnapshot.TreeGift::id).toList());
		assertEquals(7L, gifts.get(2).count());
		assertEquals(1, gifts.get(2).milestoneTier());
		assertEquals(120L, gifts.get(0).count());
		assertEquals(3, gifts.get(0).milestoneTier());
	}

	@Test
	public void missingHelixIsOmitted() {
		List<ForagingSnapshot.TreeGift> gifts = ForagingSnapshot.parseTreeGifts(JsonParser.parseString("""
			{"FIG": 2, "MANGROVE": 1}
			""").getAsJsonObject());

		assertEquals(List.of("FIG", "MANGROVE"), gifts.stream().map(ForagingSnapshot.TreeGift::id).toList());
	}
}
