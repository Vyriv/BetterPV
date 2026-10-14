package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonArray;
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
	public void eliteUnclaimedSentinelIsNotFirstPlace() {
		JsonArray elite = JsonParser.parseString("""
			[
			  {"crop":"Wheat","timestamp":1768857300,"collected":3200,"position":2397,"participants":2899,"medal":"none"},
			  {"crop":"Sugar Cane","timestamp":1768857300,"collected":2880,"position":-1,"participants":2400,"medal":"ghost"},
			  {"crop":"Melon","timestamp":1768864500,"collected":314485,"position":792,"participants":2580,"medal":"bronze"},
			  {"crop":"Melon","timestamp":1768870000,"collected":12,"position":-1,"participants":2600,"medal":"ghost"}
			]
			""").getAsJsonArray();
		GardenSnapshot snap = GardenSnapshot.empty().withEliteContests(elite);
		for (GardenSnapshot.ContestEntry c : snap.contests()) {
			if ("ghost".equals(c.medal())) {
				assertEquals(0, c.placement());
			}
		}
		assertEquals(new GardenSnapshot.CropPlacement(0, 0, 1, 1), snap.placement("SUGAR_CANE"));
		assertEquals(new GardenSnapshot.CropPlacement(793, 2580, 2, 1), snap.placement("MELON"));
		assertEquals(new GardenSnapshot.CropPlacement(2398, 2899, 1, 0), snap.placement("WHEAT"));
	}

	@Test
	public void eliteRealFirstPlaceStillCounts() {
		JsonArray elite = JsonParser.parseString("""
			[{"crop":"Sunflower","timestamp":1785262500,"collected":3115360,"position":0,"participants":1290,"medal":"diamond"}]
			""").getAsJsonArray();
		assertEquals(1, GardenSnapshot.empty().withEliteContests(elite).placement("SUNFLOWER").best());
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
}
