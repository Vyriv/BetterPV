package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.client.data.MissingAccessories.CostBasis;
import dev.vy.betterpv.client.data.MissingAccessories.Entry;
import dev.vy.betterpv.client.data.MissingAccessories.OwnedAccessory;
import dev.vy.betterpv.client.data.MissingAccessories.PriceStatus;
import dev.vy.betterpv.client.data.MissingAccessories.PricingSource;
import dev.vy.betterpv.client.data.MissingAccessories.Profile;
import dev.vy.betterpv.client.data.MissingAccessories.Result;
import dev.vy.betterpv.client.data.MissingAccessories.UpgradeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** Upgrade costs from NEU-shaped item files: crafting, Forge, NPC shops, and missing data. */
public class AccessoryUpgradeRoutesTest {
	private static final List<JsonObject> EMPEROR_REPO = List.of(
		neu("EMPEROR_RING", "{\"recipes\":[{\"A1\":\"EMPEROR_TALISMAN:1\",\"A2\":\"EMPEROR_TALISMAN:1\",\"A3\":\"\","
			+ "\"B1\":\"EMPEROR_TALISMAN:1\",\"B2\":\"EMPEROR_TALISMAN:1\",\"type\":\"crafting\",\"count\":1,"
			+ "\"overrideOutputId\":\"EMPEROR_RING\"}]}"),
		neu("EMPEROR_ARTIFACT", "{\"recipes\":[{\"A1\":\"EMPEROR_RING:1\",\"A2\":\"EMPEROR_RING:1\",\"A3\":\"\","
			+ "\"B1\":\"EMPEROR_RING:1\",\"B2\":\"EMPEROR_RING:1\",\"type\":\"crafting\",\"count\":1,"
			+ "\"overrideOutputId\":\"EMPEROR_ARTIFACT\"}]}")
	);

	@Test
	public void emperorsArtifactIsCraftedFromOwnedTalismanWithoutAnySale() {
		List<JsonObject> catalog = List.of(
			item("EMPEROR_TALISMAN", "Emperor's Talisman", "RARE"),
			item("EMPEROR_RING", "Emperor's Ring", "EPIC"),
			item("EMPEROR_ARTIFACT", "Emperor's Artifact", "LEGENDARY")
		);
		Map<String, Double> prices = Map.of(
			"EMPEROR_TALISMAN", 1_000_000D, "EMPEROR_RING", 6_000_000D, "EMPEROR_ARTIFACT", 60_000_000D
		);
		Result result = compute(List.of(owned("EMPEROR_TALISMAN", "RARE")), catalog, prices, EMPEROR_REPO);

		Entry upgrade = only(result);
		assertEquals("EMPEROR_ARTIFACT", upgrade.id());
		assertEquals(UpgradeType.TIER_UPGRADE, upgrade.type());
		assertEquals(CostBasis.CRAFT, upgrade.costBasis());
		assertEquals("EMPEROR_TALISMAN", upgrade.soldId());
		assertEquals(0D, upgrade.soldPrice(), 0D);
		// One ring from the owned talisman plus 3 bought talismans, then 3 more rings crafted at 4 x 1M each.
		assertEquals(3_000_000D + 3 * 4_000_000D, upgrade.netCost(), 0.01D);
		assertEquals(8, upgrade.mpGain());
		assertEquals(15_000_000D, result.costToMax(), 0.01D);

		List<String> lines = render(upgrade);
		assertTrue(lines.contains("Craft cost: 15,000,000"));
		assertTrue(lines.contains("Uses your Emperor's Talisman"));
		assertNoSpeculation(lines);
	}

	@Test
	public void speedRelicCraftsThroughEveryUnownedTier() {
		List<JsonObject> catalog = List.of(
			item("SPEED_TALISMAN", "Speed Talisman", "COMMON"),
			item("SPEED_RING", "Speed Ring", "UNCOMMON"),
			item("SPEED_ARTIFACT", "Speed Artifact", "RARE"),
			item("SPEED_RELIC", "Speed Relic", "EPIC")
		);
		List<JsonObject> repo = List.of(
			neu("SPEED_RING", "{\"recipe\":" + ring("ENCHANTED_SUGAR:12", "SPEED_TALISMAN:1") + "}"),
			neu("SPEED_ARTIFACT", "{\"recipe\":" + ring("ENCHANTED_SUGAR_CANE:6", "SPEED_RING:1") + "}"),
			neu("SPEED_RELIC", "{\"recipes\":[{\"A1\":\"ENCHANTED_SUGAR_CANE:64\",\"A2\":\"CANE_KNOT:16\","
				+ "\"A3\":\"ENCHANTED_SUGAR_CANE:64\",\"B1\":\"CANE_KNOT:16\",\"B2\":\"SPEED_ARTIFACT:1\","
				+ "\"B3\":\"CANE_KNOT:16\",\"C1\":\"ENCHANTED_SUGAR_CANE:64\",\"C2\":\"CANE_KNOT:16\","
				+ "\"C3\":\"ENCHANTED_SUGAR_CANE:64\",\"type\":\"crafting\",\"count\":1,\"overrideOutputId\":\"SPEED_RELIC\"}]}")
		);
		Map<String, Double> prices = Map.of(
			"ENCHANTED_SUGAR", 1_000D, "ENCHANTED_SUGAR_CANE", 20_000D, "CANE_KNOT", 50_000D,
			"SPEED_RELIC", 30_000_000D
		);
		Result result = compute(List.of(owned("SPEED_TALISMAN", "COMMON")), catalog, prices, repo);

		Entry upgrade = only(result);
		assertEquals("SPEED_RELIC", upgrade.id());
		assertEquals(CostBasis.CRAFT, upgrade.costBasis());
		assertEquals("SPEED_TALISMAN", upgrade.soldId());
		// Ring 96 sugar, artifact 48 cane, relic 256 cane and 64 knots.
		assertEquals(96_000D + 960_000D + 5_120_000D + 3_200_000D, upgrade.netCost(), 0.01D);
		assertEquals(9, upgrade.mpGain());
	}

	@Test
	public void masterSkullTierSevenUsesFourCopiesOfEachLowerTier() {
		List<JsonObject> catalog = List.of(
			item("MASTER_SKULL_TIER_5", "Master Skull - Tier 5", "EPIC"),
			item("MASTER_SKULL_TIER_6", "Master Skull - Tier 6", "EPIC"),
			item("MASTER_SKULL_TIER_7", "Master Skull - Tier 7", "LEGENDARY")
		);
		List<JsonObject> repo = List.of(
			neu("MASTER_SKULL_TIER_6", "{\"recipe\":" + square("MASTER_SKULL_TIER_5:1") + "}"),
			neu("MASTER_SKULL_TIER_7", "{\"recipe\":" + square("MASTER_SKULL_TIER_6:1") + "}")
		);
		Map<String, Double> prices = Map.of(
			"MASTER_SKULL_TIER_5", 2_000_000D, "MASTER_SKULL_TIER_6", 10_000_000D, "MASTER_SKULL_TIER_7", 50_000_000D
		);
		Result result = compute(List.of(owned("MASTER_SKULL_TIER_5", "EPIC")), catalog, prices, repo);

		Entry upgrade = only(result);
		assertEquals("MASTER_SKULL_TIER_7", upgrade.id());
		assertEquals(CostBasis.CRAFT, upgrade.costBasis());
		assertEquals("MASTER_SKULL_TIER_5", upgrade.soldId());
		// One Tier 6 from the owned Tier 5 plus 3 more Tier 5s, then 3 Tier 6s crafted from 4 Tier 5s each.
		assertEquals(3 * 2_000_000D + 3 * 8_000_000D, upgrade.netCost(), 0.01D);
		assertEquals(4, upgrade.mpGain());
	}

	@Test
	public void forgeAndNpcShopUpgradesConsumeTheOwnedTier() {
		List<JsonObject> catalog = List.of(
			item("TITANIUM_RING", "Titanium Ring", "UNCOMMON"),
			item("TITANIUM_ARTIFACT", "Titanium Artifact", "RARE"),
			item("CANDY_TALISMAN", "Candy Talisman", "COMMON"),
			item("CANDY_RING", "Candy Ring", "UNCOMMON")
		);
		List<JsonObject> repo = List.of(
			neu("TITANIUM_ARTIFACT", "{\"recipes\":[{\"type\":\"forge\",\"inputs\":[\"REFINED_TITANIUM:12.0\","
				+ "\"TITANIUM_RING:1.0\"],\"count\":1,\"overrideOutputId\":\"TITANIUM_ARTIFACT\",\"duration\":129600}]}"),
			neu("FEAR_MONGERER_NPC", "{\"recipes\":[{\"type\":\"npc_shop\",\"cost\":[\"CANDY_TALISMAN:1\","
				+ "\"GREEN_CANDY:64\"],\"result\":\"CANDY_RING\"}]}")
		);
		Map<String, Double> prices = Map.of(
			"REFINED_TITANIUM", 1_000_000D, "GREEN_CANDY", 5_000D,
			"TITANIUM_ARTIFACT", 40_000_000D, "CANDY_RING", 9_000_000D
		);
		Result result = compute(
			List.of(owned("TITANIUM_RING", "UNCOMMON"), owned("CANDY_TALISMAN", "COMMON")), catalog, prices, repo
		);

		Entry titanium = byId(result, "TITANIUM_ARTIFACT");
		assertEquals(CostBasis.CRAFT, titanium.costBasis());
		assertEquals("TITANIUM_RING", titanium.soldId());
		assertEquals(12_000_000D, titanium.netCost(), 0.01D);

		Entry candy = byId(result, "CANDY_RING");
		assertEquals(CostBasis.CRAFT, candy.costBasis());
		assertEquals("CANDY_TALISMAN", candy.soldId());
		assertEquals(320_000D, candy.netCost(), 0.01D);
	}

	@Test
	public void npcCoinPriceIsUsedWhenTheTierIsBoughtSeparately() {
		List<JsonObject> catalog = List.of(
			item("ODGERS_BRONZE_TOOTH", "Odger's Bronze Tooth", "COMMON"),
			item("ODGERS_SILVER_TOOTH", "Odger's Silver Tooth", "UNCOMMON")
		);
		List<JsonObject> repo = List.of(neu(
			"ODGER_NPC",
			"{\"recipes\":[{\"type\":\"npc_shop\",\"result\":\"ODGERS_SILVER_TOOTH:1.0\",\"cost\":[\"SKYBLOCK_COIN:500000.0\"]}]}"
		));
		Result result = compute(
			List.of(owned("ODGERS_BRONZE_TOOTH", "COMMON")), catalog, Map.of("ODGERS_SILVER_TOOTH", 3_000_000D), repo
		);

		Entry upgrade = only(result);
		assertEquals(CostBasis.MARKET, upgrade.costBasis());
		assertEquals(PricingSource.NPC, upgrade.pricingSource());
		assertEquals(500_000D, upgrade.netCost(), 0.01D);
		assertEquals(0D, upgrade.soldPrice(), 0D);
		assertNoSpeculation(render(upgrade));
	}

	@Test
	public void missingRecipeDataLeavesUpgradeCostUnknownAndSortsItLast() {
		List<JsonObject> catalog = List.of(
			item("EMPEROR_TALISMAN", "Emperor's Talisman", "RARE"),
			item("EMPEROR_RING", "Emperor's Ring", "EPIC"),
			item("EMPEROR_ARTIFACT", "Emperor's Artifact", "LEGENDARY"),
			item("WOLF_TALISMAN", "Wolf Talisman", "COMMON")
		);
		Map<String, Double> prices = Map.of(
			"EMPEROR_TALISMAN", 1_000_000D, "EMPEROR_RING", 6_000_000D, "EMPEROR_ARTIFACT", 60_000_000D,
			"WOLF_TALISMAN", 100_000D
		);
		Result result = compute(List.of(owned("EMPEROR_TALISMAN", "RARE")), catalog, prices, List.of());

		Entry upgrade = byId(result, "EMPEROR_ARTIFACT");
		assertEquals(UpgradeType.TIER_UPGRADE, upgrade.type());
		assertEquals(CostBasis.UNKNOWN, upgrade.costBasis());
		assertEquals(PriceStatus.MISSING, upgrade.priceStatus());
		assertFalse(upgrade.priced());
		assertEquals(0D, upgrade.netCost(), 0D);
		assertEquals(Double.MAX_VALUE, upgrade.coinsPerMp(), 0D);
		assertEquals("WOLF_TALISMAN", result.entries().get(0).id());
		assertEquals(result.entries().size() - 1, result.entries().indexOf(upgrade));
		assertEquals(100_000D, result.costToMax(), 0.01D);
		assertEquals(3 + 8, result.mpToMax());
		assertEquals(1, result.unpricedTop());

		List<String> lines = render(upgrade);
		assertEquals("Upgrade", lines.get(0));
		assertTrue(lines.contains("Cost: Unknown upgrade method"));
		assertTrue(lines.contains("Replaces your Emperor's Talisman"));
		assertFalse(lines.stream().anyMatch(line -> line.startsWith("Coins per MP")));
		assertNoSpeculation(lines);
	}

	@Test
	public void indexReadsNpcShopsForgeDecimalsAndTrades() {
		Map<String, List<AccessoryUpgradeMethods.Method>> index = AccessoryUpgradeMethods.index(List.of(
			neu("FEAR_MONGERER_NPC", "{\"recipes\":[{\"type\":\"npc_shop\",\"cost\":[\"CANDY_TALISMAN:1\","
				+ "\"GREEN_CANDY:64\"],\"result\":\"CANDY_RING\"}]}"),
			neu("POWDER_OF_THE_MANY", "{\"recipes\":[{\"type\":\"forge\",\"inputs\":[\"POWDER_OF_THE_FEW:1.0\","
				+ "\"GLACITE_AMALGAMATION:4.0\"],\"count\":1.0,\"overrideOutputId\":\"POWDER_OF_THE_MANY\"}]}"),
			neu("LYNX_TALISMAN", "{\"recipes\":[{\"type\":\"trade\",\"cost\":\"ENCHANTED_EMERALD:6\",\"result\":\"LYNX_TALISMAN\"}]}"),
			neu("SOME_PET", "{\"recipes\":[{\"type\":\"drops\",\"drops\":[]}]}")
		));

		AccessoryUpgradeMethods.Method candy = index.get("CANDY_RING").get(0);
		assertEquals(AccessoryUpgradeMethods.Kind.NPC_SHOP, candy.kind());
		assertEquals(Map.of("CANDY_TALISMAN", 1, "GREEN_CANDY", 64), candy.inputs());
		assertEquals(Map.of("POWDER_OF_THE_FEW", 1, "GLACITE_AMALGAMATION", 4), index.get("POWDER_OF_THE_MANY").get(0).inputs());
		assertEquals(Map.of("ENCHANTED_EMERALD", 6), index.get("LYNX_TALISMAN").get(0).inputs());
		assertFalse(index.containsKey("SOME_PET"));
		assertFalse(index.containsKey("FEAR_MONGERER_NPC"));
	}

	private static Result compute(
		List<OwnedAccessory> owned, List<JsonObject> catalog, Map<String, Double> prices, List<JsonObject> repo
	) {
		Map<String, List<AccessoryUpgradeMethods.Method>> methods = AccessoryUpgradeMethods.index(repo);
		return MissingAccessories.computeQuoted(
			new Profile(owned, true, false), catalog, Map.of(), Set.of(),
			MissingAccessories.quotes(id -> prices.getOrDefault(id, 0D)), id -> methods.getOrDefault(id, List.of())
		);
	}

	private static Entry only(Result result) {
		assertEquals(1, result.entries().size());
		return result.entries().get(0);
	}

	private static Entry byId(Result result, String id) {
		return result.entries().stream().filter(entry -> entry.id().equals(id)).findFirst().orElseThrow();
	}

	private static List<String> render(Entry entry) {
		List<String> out = new ArrayList<>();
		for (MissingAccessoryTooltip.Line line : MissingAccessoryTooltip.lines(entry)) {
			if (!line.spacer()) {
				out.add(line.label() == null ? line.value() : line.label() + ": " + line.value());
			}
		}
		return out;
	}

	private static void assertNoSpeculation(List<String> lines) {
		for (String line : lines) {
			String lower = line.toLowerCase(java.util.Locale.ROOT);
			assertFalse(line, lower.contains("sell") || lower.contains("estimate") || line.contains("~")
				|| line.contains("Vyriv") || line.contains("SkyHelper") || lower.contains("price from"));
		}
	}

	/** The NEU talisman-to-ring layout: eight material cells around the lower tier. */
	private static String ring(String material, String center) {
		return "{\"A1\":\"" + material + "\",\"A2\":\"" + material + "\",\"A3\":\"" + material + "\",\"B1\":\""
			+ material + "\",\"B2\":\"" + center + "\",\"B3\":\"" + material + "\",\"C1\":\"" + material
			+ "\",\"C2\":\"" + material + "\",\"C3\":\"" + material + "\"}";
	}

	private static String square(String stack) {
		return "{\"A1\":\"" + stack + "\",\"A2\":\"" + stack + "\",\"A3\":\"\",\"B1\":\"" + stack + "\",\"B2\":\""
			+ stack + "\",\"B3\":\"\",\"C1\":\"\",\"C2\":\"\",\"C3\":\"\"}";
	}

	private static JsonObject neu(String id, String json) {
		JsonObject item = JsonParser.parseString(json).getAsJsonObject();
		item.addProperty("internalname", id);
		return item;
	}

	private static OwnedAccessory owned(String id, String tier) {
		return new OwnedAccessory(id, tier, false, List.of());
	}

	private static JsonObject item(String id, String name, String tier) {
		JsonObject item = new JsonObject();
		item.addProperty("id", id);
		item.addProperty("name", name);
		item.addProperty("category", "ACCESSORY");
		item.addProperty("tier", tier);
		item.addProperty("can_recombobulate", false);
		return item;
	}
}
