package dev.vy.betterpv.client.dungeons;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.Test;

public class EssenceShopCostsTest {
	@Test
	public void parsesNeuShopsAndSumsRemainingTiers() {
		Map<String, int[]> costs = EssenceShopCosts.parse(JsonParser.parseString("""
			{"ESSENCE_CRIMSON": {"strongarm_kuudra": {"costs": [1000, 2000], "name": "Strongarm"}},
			 "ESSENCE_SAFARI": {"Head_Start": {"costs": [5000], "name": "Head Start"}, "broken": 3}}
			""").getAsJsonObject());
		int[] strongarm = costs.get("strongarm_kuudra");
		assertArrayEquals(new int[] {1000, 2000}, strongarm);
		assertArrayEquals(new int[] {5000}, costs.get("head_start"));

		assertEquals(1000L, EssenceShopCosts.nextTier(strongarm, 0));
		assertEquals(3000L, EssenceShopCosts.toMax(strongarm, 0));
		assertEquals(2000L, EssenceShopCosts.nextTier(strongarm, 1));
		assertEquals(2000L, EssenceShopCosts.toMax(strongarm, 1));
		assertEquals(0L, EssenceShopCosts.nextTier(strongarm, 2));
		assertEquals(0L, EssenceShopCosts.toMax(strongarm, 2));
	}

	// NEU constants/essenceshops.json ESSENCE_WITHER entry; matches the wiki's 8,750 cumulative cost.
	@Test
	public void echoesOfTheLostCostsComeFromNeu() {
		Map<String, int[]> costs = EssenceShopCosts.parse(JsonParser.parseString("""
			{"ESSENCE_WITHER": {"dungeonbreaker_secrets": {"costs": [250, 500, 1000, 2000, 5000], "name": "Echos of the Lost"}}}
			""").getAsJsonObject());
		int[] echoes = EssenceShopCosts.lookup(costs, "dungeonbreaker_secrets");
		assertArrayEquals(new int[] {250, 500, 1000, 2000, 5000}, echoes);
		assertEquals(8750L, EssenceShopCosts.toMax(echoes, 0));
		assertEquals(0L, EssenceShopCosts.toMax(echoes, 5));
		assertEquals(0L, EssenceShopCosts.nextTier(echoes, 5));
	}

	@Test
	public void eaglesAdvantageFindsNeuCostsUnderNeuName() {
		Map<String, int[]> costs = EssenceShopCosts.parse(JsonParser.parseString("""
			{"ESSENCE_SAFARI": {"eagle_advantage": {"costs": [100, 200, 300, 400, 500]},
			 "head_start": {"costs": [5000]}}}
			""").getAsJsonObject());
		assertArrayEquals(new int[] {100, 200, 300, 400, 500}, EssenceShopCosts.lookup(costs, "eagles_advantage"));
		assertArrayEquals(new int[] {5000}, EssenceShopCosts.lookup(costs, "HEAD_START"));
		assertNull(EssenceShopCosts.lookup(costs, "unknown_perk"));
		assertNull(EssenceShopCosts.lookup(Map.of(), "eagles_advantage"));
	}
}
