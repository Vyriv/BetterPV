package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class MinionCostsTest {
	private static JsonObject item(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	private static final JsonObject WHEAT_2 = item("""
		{"recipe": {"A1": "WHEAT:20", "A2": "WHEAT:20", "A3": "WHEAT:20", "B1": "WHEAT:20",
		"B2": "WHEAT_GENERATOR_1:1", "B3": "WHEAT:20", "C1": "WHEAT:20", "C2": "WHEAT:20", "C3": "WHEAT:20"}}
		""");

	@Test
	public void previousTierMinionIsNotPriced() {
		Map<String, Integer> ing = MinionCosts.ingredients(MinionCosts.recipe(WHEAT_2), "WHEAT_GENERATOR");
		assertEquals(Map.of("WHEAT", 160), ing);

		MinionCosts.TierCost cost = MinionCosts.tierCost("WHEAT_GENERATOR", 2, WHEAT_2, id -> "WHEAT".equals(id) ? 2.5 : null);
		assertEquals(400.0, cost.coins(), 1e-9);
		assertTrue(cost.complete());
	}

	@Test
	public void unpricedIngredientIsFlaggedNotZero() {
		JsonObject wheat1 = item("""
			{"recipe": {"A1": "WHEAT:10", "A2": "WHEAT:10", "A3": "WHEAT:10", "B1": "WHEAT:10",
			"B2": "WOOD_HOE:1", "B3": "WHEAT:10", "C1": "WHEAT:10", "C2": "WHEAT:10", "C3": "WHEAT:10"}}
			""");
		MinionCosts.TierCost cost = MinionCosts.tierCost("WHEAT_GENERATOR", 1, wheat1, id -> "WHEAT".equals(id) ? 1.0 : null);
		assertEquals(80.0, cost.coins(), 1e-9);
		assertEquals(List.of("WOOD_HOE"), cost.unpriced());
		assertFalse(cost.complete());
	}

	@Test
	public void missingRecipeIsUnknown() {
		MinionCosts.TierCost cost = MinionCosts.tierCost("WHEAT_GENERATOR", 12, item("{\"recipe\": null}"), id -> 1.0);
		assertTrue(cost.noRecipe());
		assertFalse(cost.complete());
	}

	@Test
	public void neuDashIdsBecomeHypixelColonIds() {
		JsonObject birch = item("""
			{"recipe": {"A1": "LOG-2:10", "B2": "WOOD_AXE:1", "C3": "LOG-2:70"}}
			""");
		assertEquals(Map.of("LOG:2", 80, "WOOD_AXE", 1), MinionCosts.ingredients(MinionCosts.recipe(birch), "BIRCH_GENERATOR"));
	}

	@Test
	public void genericRecipeKeepsEveryIngredientAndSkipsCount() {
		JsonObject recipe = item("""
			{"A1": "", "A2": "ENCHANTED_EYE_OF_ENDER:16", "B2": "ENCHANTED_EYE_OF_ENDER:16",
			 "C2": "ENCHANTED_DIAMOND:1", "B3": "WHEAT_MINION_1:1", "count": 1}
			""");
		Map<String, Integer> out = MinionCosts.ingredients(recipe, null);
		assertEquals(Map.of("ENCHANTED_EYE_OF_ENDER", 32, "ENCHANTED_DIAMOND", 1, "WHEAT_MINION_1", 1), out);
	}

	@Test
	public void recipesArrayFallback() {
		JsonObject lily = item("""
			{"recipe": null, "recipes": [{"type": "crafting", "count": 1, "overrideOutputId": "X",
			"A1": "WATER_LILY:8", "B2": "FISHING_ROD:1"}]}
			""");
		assertEquals(Map.of("WATER_LILY", 8, "FISHING_ROD", 1), MinionCosts.ingredients(MinionCosts.recipe(lily), "LILY_PAD_GENERATOR"));
	}

	@Test
	public void categoryFromMainIngredientCollection() {
		JsonObject wheat1 = item("""
			{"recipe": {"A1": "WHEAT:10", "B2": "WOOD_HOE:1", "C3": "WHEAT:70"}}
			""");
		Map<String, String> coll = Map.of("WHEAT", "FARMING", "LOG:2", "FORAGING");
		assertEquals(MinionCosts.Category.FARMING, MinionCosts.category("WHEAT_GENERATOR", wheat1, coll::get));
		assertEquals(MinionCosts.Category.OTHER, MinionCosts.category("MYSTERY_GENERATOR", wheat1, id -> null));
		assertEquals(MinionCosts.Category.COMBAT, MinionCosts.category("REVENANT_GENERATOR", null, id -> null));
	}

	@Test
	public void cheapestSortsCompleteThenPartialThenUnknown() {
		List<MinionCosts.MissingTier> list = new ArrayList<>(List.of(
			new MinionCosts.MissingTier("A", new MinionCosts.TierCost(12, 0, List.of(), true)),
			new MinionCosts.MissingTier("B", new MinionCosts.TierCost(1, 10, List.of("WOOD_HOE"), false)),
			new MinionCosts.MissingTier("C", new MinionCosts.TierCost(3, 500, List.of(), false)),
			new MinionCosts.MissingTier("D", new MinionCosts.TierCost(2, 50, List.of(), false))
		));
		list.sort(MinionCosts.cheapestFirst());
		assertEquals(List.of("D", "C", "B", "A"), list.stream().map(MinionCosts.MissingTier::minionId).toList());
	}
}
