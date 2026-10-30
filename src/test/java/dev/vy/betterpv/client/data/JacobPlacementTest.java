package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class JacobPlacementTest {
	private static GardenSnapshot.ContestEntry contest(String crop, int position, int participants) {
		return new GardenSnapshot.ContestEntry(crop, crop, crop, 100L, "none", position, participants, 0L);
	}

	@Test
	public void positionZeroIsFirstPlace() {
		assertEquals(1, contest("WHEAT", 0, 1290).placement());
		assertEquals(0, contest("WHEAT", -1, 0).placement());
	}

	@Test
	public void bestPlacementPerCropMergesAliasesAndCountsUnclaimed() {
		Map<String, GardenSnapshot.CropPlacement> byCrop = GardenSnapshot.placementsByCrop(List.of(
			contest("DOUBLE_PLANT", 4, 900),
			contest("SUNFLOWER", 0, 1290),
			contest("DOUBLE_PLANT", -1, 0),
			contest("NETHER_STALK", -1, 0)
		));
		GardenSnapshot.CropPlacement sunflower = byCrop.get("SUNFLOWER");
		assertEquals(new GardenSnapshot.CropPlacement(1, 1290, 3, 1), sunflower);
		GardenSnapshot.CropPlacement wart = byCrop.get("NETHER_WART");
		assertEquals(new GardenSnapshot.CropPlacement(0, 0, 1, 1), wart);
		assertNull(byCrop.get("WHEAT"));
	}

	@Test
	public void positionMissingNullOrNegativeIsUnknown() {
		JsonObject c = JsonParser.parseString("""
			{"a":-1,"b":null,"c":"x","d":0,"e":554,"f":-5}
			""").getAsJsonObject();
		assertEquals(-1, GardenSnapshot.positionOf(c, "a"));
		assertEquals(-1, GardenSnapshot.positionOf(c, "b"));
		assertEquals(-1, GardenSnapshot.positionOf(c, "c"));
		assertEquals(0, GardenSnapshot.positionOf(c, "d"));
		assertEquals(554, GardenSnapshot.positionOf(c, "e"));
		assertEquals(-1, GardenSnapshot.positionOf(c, "f"));
		assertEquals(-1, GardenSnapshot.positionOf(c, "missing"));
	}

	@Test
	public void hypixelUnclaimedContestHasNoPlacement() {
		JsonObject member = JsonParser.parseString("""
			{"jacobs_contest":{"contests":{
			  "467:4_2:WHEAT":{"collected":3200,"claimed_participants":2899,"claimed_position":2397,"claimed_rewards":true},
			  "467:4_2:SUGAR_CANE":{"collected":2880}
			}}}
			""").getAsJsonObject();
		GardenSnapshot snap = GardenSnapshot.fromMember(member);
		assertEquals(new GardenSnapshot.CropPlacement(0, 0, 1, 1), snap.placement("SUGAR_CANE"));
		assertEquals(2398, snap.placement("WHEAT").best());
	}

	@Test
	public void hypixelFirstPlaceStillCounts() {
		JsonObject member = JsonParser.parseString("""
			{"jacobs_contest":{"contests":{
			  "480:1_1:DOUBLE_PLANT":{
			    "collected":3115360,"claimed_position":0,"claimed_participants":1290,
			    "claimed_medal":"diamond","claimed_rewards":true
			  }
			}}}
			""").getAsJsonObject();
		assertEquals(1, GardenSnapshot.fromMember(member).placement("SUNFLOWER").best());
	}

	@Test
	public void hypixelContestTimestampFromKeyAndNewestFirst() {
		assertEquals(1_768_857_300L, GardenSnapshot.timestampFromContestKey("467:4_2:WHEAT"));
		assertEquals(0L, GardenSnapshot.timestampFromContestKey("WHEAT"));

		JsonObject member = JsonParser.parseString("""
			{"jacobs_contest":{"contests":{
			  "467:4_2:WHEAT":{"collected":100,"claimed_position":10,"claimed_participants":100,"claimed_rewards":true},
			  "467:4_5:MELON":{"collected":200,"claimed_position":5,"claimed_participants":100,"claimed_medal":"bronze","claimed_rewards":true},
			  "466:12_31:SUGAR_CANE":{"collected":150}
			}}}
			""").getAsJsonObject();
		List<GardenSnapshot.ContestEntry> contests = GardenSnapshot.fromMember(member).contests();
		assertEquals(3, contests.size());
		assertEquals("MELON", contests.get(0).crop());
		assertEquals("WHEAT", contests.get(1).crop());
		assertEquals("SUGAR_CANE", contests.get(2).crop());
		assertTrue(contests.get(0).timestampSeconds() > contests.get(1).timestampSeconds());
		assertTrue(contests.get(1).timestampSeconds() > contests.get(2).timestampSeconds());
	}

	@Test
	public void ignoresContestsWithFewerThan100Crops() {
		JsonObject member = JsonParser.parseString("""
			{"jacobs_contest":{"contests":{
			  "467:4_2:WHEAT":{"collected":99,"claimed_participants":100,"claimed_position":50,"claimed_rewards":true},
			  "467:4_3:WHEAT":{"collected":100,"claimed_participants":200,"claimed_position":10,"claimed_rewards":true},
			  "467:4_4:POTATO_ITEM":{"collected":0}
			}}}
			""").getAsJsonObject();
		GardenSnapshot snap = GardenSnapshot.fromMember(member);
		assertEquals(1, snap.contests().size());
		assertEquals(11, snap.placement("WHEAT").best());
		assertNull(snap.placement("POTATO_ITEM"));
	}

	@Test
	public void derivesMedalFromPositionWhenClaimedMedalMissing() {
		JsonObject member = JsonParser.parseString("""
			{"jacobs_contest":{"contests":{
			  "467:4_2:WHEAT":{"collected":100,"claimed_position":10,"claimed_participants":100,"claimed_rewards":true},
			  "467:4_3:MELON":{"collected":200,"claimed_position":1,"claimed_participants":100,"claimed_medal":"bronze","claimed_rewards":true}
			}}}
			""").getAsJsonObject();
		List<GardenSnapshot.ContestEntry> contests = GardenSnapshot.fromMember(member).contests();
		assertEquals("bronze", contests.stream().filter(c -> "MELON".equals(c.crop())).findFirst().orElseThrow().medal());
		assertEquals("gold", contests.stream().filter(c -> "WHEAT".equals(c.crop())).findFirst().orElseThrow().medal());
	}

	/**
	 * For N=100, Elite cuts are floor(2/5/10/30/60). Inclusive {@code <=} on 0-based position
	 * keeps Hypixel's extra slot (e.g. diamond positions 0..2 = 3 players, not 2).
	 */
	@Test
	public void medalBracketBoundariesAreInclusiveOnZeroBasedPosition() {
		assertEquals(2, GardenSnapshot.bracketCut(100, 0.02));
		assertEquals(5, GardenSnapshot.bracketCut(100, 0.05));
		assertEquals(10, GardenSnapshot.bracketCut(100, 0.1));
		assertEquals(30, GardenSnapshot.bracketCut(100, 0.3));
		assertEquals(60, GardenSnapshot.bracketCut(100, 0.6));

		assertEquals("diamond", GardenSnapshot.medalFromPlacement(0, 100));
		assertEquals("diamond", GardenSnapshot.medalFromPlacement(2, 100));
		assertEquals("platinum", GardenSnapshot.medalFromPlacement(3, 100));
		assertEquals("platinum", GardenSnapshot.medalFromPlacement(5, 100));
		assertEquals("gold", GardenSnapshot.medalFromPlacement(6, 100));
		assertEquals("gold", GardenSnapshot.medalFromPlacement(10, 100));
		assertEquals("silver", GardenSnapshot.medalFromPlacement(11, 100));
		assertEquals("silver", GardenSnapshot.medalFromPlacement(30, 100));
		assertEquals("bronze", GardenSnapshot.medalFromPlacement(31, 100));
		assertEquals("bronze", GardenSnapshot.medalFromPlacement(60, 100));
		assertEquals("none", GardenSnapshot.medalFromPlacement(61, 100));
		assertEquals("none", GardenSnapshot.medalFromPlacement(-1, 100));

		// Non-round N: floor(1290*0.02)=25 still includes position 25 as diamond (+1 slot).
		assertEquals(25, GardenSnapshot.bracketCut(1290, 0.02));
		assertEquals("diamond", GardenSnapshot.medalFromPlacement(25, 1290));
		assertEquals("platinum", GardenSnapshot.medalFromPlacement(26, 1290));
	}
}
