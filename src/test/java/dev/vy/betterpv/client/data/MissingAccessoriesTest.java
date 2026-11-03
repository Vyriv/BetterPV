package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.MissingAccessories.Entry;
import dev.vy.betterpv.client.data.MissingAccessories.OwnedAccessory;
import dev.vy.betterpv.client.data.MissingAccessories.PriceStatus;
import dev.vy.betterpv.client.data.MissingAccessories.Profile;
import dev.vy.betterpv.client.data.MissingAccessories.Result;
import dev.vy.betterpv.client.data.MissingAccessories.UpgradeType;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import dev.vy.betterpv.client.price.ItemPricer.PriceAvailability;
import dev.vy.betterpv.client.price.ItemPricer.PriceQuote;
import dev.vy.betterpv.client.price.ItemPricer.PriceSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.Test;

public class MissingAccessoriesTest {
	@Test
	public void ownedEligibleAccessoryGetsIncrementalRecombRecommendation() {
		Result result = compute(
			List.of(item("HUNTER_RING", "Hunter Ring", "RARE", true)),
			List.of(owned("HUNTER_RING", "RARE", false)),
			false,
			Map.of("RECOMBOBULATOR_3000", 9_000_000D)
		);

		Entry recomb = entry(result, UpgradeType.RECOMBOBULATE);
		assertEquals(4, recomb.mpGain());
		assertEquals(2_250_000D, recomb.coinsPerMp(), 0.01D);
		assertEquals(4, result.mpToMax());
	}

	@Test
	public void alreadyRecombobulatedAndIneligibleAccessoriesAreSkipped() {
		List<JsonObject> catalog = List.of(
			item("HUNTER_RING", "Hunter Ring", "RARE", true),
			item("BOOK_OF_PROGRESSION", "Book of Progression", "COMMON", false)
		);
		Result result = compute(
			catalog,
			List.of(owned("HUNTER_RING", "EPIC", true), owned("BOOK_OF_PROGRESSION", "COMMON", false)),
			false,
			Map.of("RECOMBOBULATOR_3000", 9_000_000D)
		);

		assertFalse(result.entries().stream().anyMatch(e -> e.type() == UpgradeType.RECOMBOBULATE));
	}

	@Test
	public void inactiveLowerFamilyDuplicateDoesNotGetRecombRecommended() {
		List<JsonObject> catalog = List.of(
			item("WOLF_TALISMAN", "Wolf Talisman", "COMMON", true),
			item("WOLF_RING", "Wolf Ring", "UNCOMMON", true)
		);
		Result result = compute(
			catalog,
			List.of(owned("WOLF_TALISMAN", "COMMON", false), owned("WOLF_RING", "UNCOMMON", false)),
			false,
			Map.of("RECOMBOBULATOR_3000", 9_000_000D)
		);

		List<Entry> recombs = result.entries().stream().filter(e -> e.type() == UpgradeType.RECOMBOBULATE).toList();
		assertEquals(1, recombs.size());
		assertTrue(recombs.get(0).name().contains("Wolf Ring"));
		assertEquals(3, recombs.get(0).mpGain());
	}

	@Test
	public void recombobulatedCompactorOutsideBagIsMovedInsteadOfRecombobulatingBagCopy() {
		List<JsonObject> catalog = List.of(item("PERSONAL_COMPACTOR_7000", "Personal Compactor 7000", "LEGENDARY", true));
		Result result = compute(
			catalog,
			List.of(
				new OwnedAccessory("PERSONAL_COMPACTOR_7000", "LEGENDARY", false, List.of(), "Accessory Bag"),
				new OwnedAccessory("PERSONAL_COMPACTOR_7000", "MYTHIC", true, List.of(), "Inventory")
			),
			false, Map.of("RECOMBOBULATOR_3000", 9_000_000D)
		);

		assertFalse(result.entries().stream().anyMatch(e -> e.type() == UpgradeType.RECOMBOBULATE));
		Entry move = entry(result, UpgradeType.MOVE_TO_BAG);
		assertEquals(6, move.mpGain());
		assertTrue(move.currentState().contains("Inventory"));
		assertEquals(6, result.mpToMax());
		assertEquals(0D, result.costToMax(), 0D);
		assertConsistentTotals(result);
	}

	@Test
	public void powderFamilyPlansPeopleThroughRecombobulation() {
		List<JsonObject> catalog = List.of(
			item("POWDER_OF_THE_FEW", "Powder of the Few", "UNCOMMON", true),
			item("POWDER_OF_THE_MANY", "Powder of the Many", "RARE", true),
			item("POWDER_OF_THE_PEOPLE", "Powder of the People", "EPIC", true)
		);
		Result result = compute(
			catalog, List.of(owned("POWDER_OF_THE_FEW", "UNCOMMON", false)), false,
			Map.of("POWDER_OF_THE_PEOPLE", 10_000_000D, "POWDER_OF_THE_FEW", 500_000D, "RECOMBOBULATOR_3000", 9_000_000D)
		);

		Entry upgrade = entry(result, UpgradeType.TIER_UPGRADE);
		assertEquals("POWDER_OF_THE_PEOPLE", upgrade.id());
		assertEquals(7, upgrade.mpGain());
		// No recorded upgrade method, so the cost is unknown rather than the market price of a new copy.
		assertEquals(MissingAccessories.CostBasis.UNKNOWN, upgrade.costBasis());
		assertEquals(PriceStatus.MISSING, upgrade.priceStatus());
		assertFalse(upgrade.priced());
		assertFalse(result.entries().stream().anyMatch(e -> e.id().equals("POWDER_OF_THE_MANY")));
		List<Entry> recombs = result.entries().stream().filter(e -> e.type() == UpgradeType.RECOMBOBULATE).toList();
		assertEquals(1, recombs.size());
		assertTrue(recombs.get(0).name().contains("Powder of the People"));
		assertEquals(4, recombs.get(0).mpGain());
		assertEquals(11, result.mpToMax());
		assertEquals(9_000_000D, result.costToMax(), 0.01D);
		assertEquals(1, result.unpricedTop());
		assertConsistentTotals(result);
	}

	@Test
	public void unownedHunterRingCostAndMpDescribeTheSameRecombobulatedEndState() {
		List<JsonObject> catalog = List.of(
			item("HUNTER_TALISMAN", "Hunter Talisman", "UNCOMMON", true),
			item("HUNTER_RING", "Hunter Ring", "RARE", true)
		);
		Result result = compute(
			catalog, List.of(), false,
			Map.of("HUNTER_TALISMAN", 1_000_000D, "HUNTER_RING", 5_000_000D, "RECOMBOBULATOR_3000", 9_000_000D)
		);

		assertFalse(result.entries().stream().anyMatch(e -> e.id().equals("HUNTER_TALISMAN")));
		Entry purchase = entry(result, UpgradeType.PURCHASE);
		assertEquals("HUNTER_RING", purchase.id());
		assertEquals(8, purchase.mpGain());
		Entry recomb = entry(result, UpgradeType.RECOMBOBULATE);
		assertEquals(4, recomb.mpGain());
		assertEquals(12, result.mpToMax());
		assertEquals(14_000_000D, result.costToMax(), 0.01D);
		assertConsistentTotals(result);
	}

	@Test
	public void hunterTalismanInBagIsReplacedByRingThenRecombobulated() {
		List<JsonObject> catalog = List.of(
			item("HUNTER_TALISMAN", "Hunter Talisman", "UNCOMMON", true),
			item("HUNTER_RING", "Hunter Ring", "RARE", true)
		);
		Result result = compute(
			catalog, List.of(owned("HUNTER_TALISMAN", "UNCOMMON", false)), false,
			Map.of("HUNTER_TALISMAN", 1_000_000D, "HUNTER_RING", 5_000_000D, "RECOMBOBULATOR_3000", 9_000_000D)
		);

		Entry upgrade = entry(result, UpgradeType.TIER_UPGRADE);
		assertEquals("HUNTER_TALISMAN", upgrade.soldId());
		assertEquals(3, upgrade.mpGain());
		assertFalse(upgrade.priced());
		assertEquals(0D, upgrade.soldPrice(), 0D);
		assertEquals(1, result.entries().stream().filter(e -> e.type() == UpgradeType.RECOMBOBULATE).count());
		Entry recomb = entry(result, UpgradeType.RECOMBOBULATE);
		assertEquals("Hunter Ring", recomb.prerequisite());
		assertEquals("Upgrade to Hunter Ring first", recomb.currentState());
		assertEquals(MissingAccessories.CostBasis.UNKNOWN, upgrade.costBasis());
		assertEquals("Owned: Uncommon Hunter Talisman", upgrade.currentState());
		assertEquals(7, result.mpToMax());
		assertEquals(9_000_000D, result.costToMax(), 0.01D);
		assertConsistentTotals(result);
	}

	@Test
	public void purchaseThatOnlyGainsWhenRecombobulatedIsOneCombinedStep() {
		List<JsonObject> catalog = List.of(
			item("WOLF_TALISMAN", "Wolf Talisman", "COMMON", true),
			item("WOLF_RING", "Wolf Ring", "UNCOMMON", true)
		);
		Map<String, Double> prices = Map.of(
			"WOLF_TALISMAN", 200_000D, "WOLF_RING", 1_000_000D, "ENCHANTED_BONE", 10_000D, "RECOMBOBULATOR_3000", 9_000_000D
		);
		Result result = MissingAccessories.compute(
			new Profile(List.of(owned("WOLF_TALISMAN", "UNCOMMON", true)), true, false), catalog, Map.of(), Set.of(),
			id -> prices.getOrDefault(id, 0D), Map.of("WOLF_RING", recipe("WOLF_TALISMAN:1", "ENCHANTED_BONE:8"))
		);

		assertEquals(1, result.entries().size());
		Entry combined = result.entries().get(0);
		assertEquals(UpgradeType.TIER_UPGRADE, combined.type());
		assertTrue(combined.name().contains("Recombobulator"));
		assertEquals(3, combined.mpGain());
		assertEquals(80_000D + 9_000_000D, combined.netCost(), 0.01D);
		assertEquals(3, result.mpToMax());
		assertConsistentTotals(result);
	}

	@Test
	public void unownedRecombobulationNeverRanksAboveItsPurchase() {
		List<JsonObject> catalog = List.of(
			item("WOLF_TALISMAN", "Wolf Talisman", "COMMON", true),
			item("WOLF_RING", "Wolf Ring", "UNCOMMON", true),
			item("HUNTER_RING", "Hunter Ring", "RARE", true)
		);
		// Hunter Ring has no price, so its purchase sorts last while its recomb alone would rank first.
		Result result = compute(
			catalog, List.of(owned("WOLF_RING", "UNCOMMON", false)), false,
			Map.of("RECOMBOBULATOR_3000", 9_000_000D)
		);

		Entry ownedRecomb = result.entries().get(0);
		assertEquals("Recombobulate Wolf Ring", ownedRecomb.name());
		assertNull(ownedRecomb.prerequisite());
		assertTrue(ownedRecomb.currentState().startsWith("Owned"));

		Entry purchase = entry(result, UpgradeType.PURCHASE);
		assertEquals("HUNTER_RING", purchase.id());
		assertEquals(PriceStatus.MISSING, purchase.priceStatus());
		Entry followUp = result.entries().stream()
			.filter(e -> e.type() == UpgradeType.RECOMBOBULATE && e.prerequisite() != null)
			.findFirst().orElseThrow();
		assertEquals("Then recombobulate Hunter Ring", followUp.name());
		assertEquals("Hunter Ring", followUp.prerequisite());
		assertEquals("Buy Hunter Ring first", followUp.currentState());
		assertEquals(result.entries().indexOf(purchase) + 1, result.entries().indexOf(followUp));

		List<Entry> byCost = MissingAccessories.ordered(result.entries(), MissingAccessories.byNetCost());
		assertEquals(byCost.indexOf(purchase) + 1, byCost.indexOf(followUp));
		assertEquals(result.entries().size(), byCost.size());
		assertConsistentTotals(result);
	}

	@Test
	public void recipeThatConsumesOwnedTalismanIsCostedAsCraftNotResale() {
		Result result = MissingAccessories.compute(
			new Profile(List.of(owned("HUNTER_TALISMAN", "UNCOMMON", false)), true, false),
			hunterCatalog(), Map.of(), Set.of(), hunterPrices(true)::get, Map.of("HUNTER_RING", hunterRingRecipe())
		);

		Entry upgrade = entry(result, UpgradeType.TIER_UPGRADE);
		assertEquals(MissingAccessories.CostBasis.CRAFT, upgrade.costBasis());
		assertEquals("HUNTER_TALISMAN", upgrade.soldId());
		assertEquals(0D, upgrade.soldPrice(), 0D);
		assertEquals(740_000D, upgrade.netCost(), 0.01D);
		assertEquals(3, upgrade.mpGain());
		assertEquals("Craft Hunter Ring first", entry(result, UpgradeType.RECOMBOBULATE).currentState());
		assertEquals(9_740_000D, result.costToMax(), 0.01D);
		assertEquals(7, result.mpToMax());
		assertConsistentTotals(result);
	}

	@Test
	public void craftRouteRecursesThroughUnownedMiddleTier() {
		List<JsonObject> catalog = List.of(
			item("SPEED_TALISMAN", "Speed Talisman", "COMMON", false),
			item("SPEED_RING", "Speed Ring", "UNCOMMON", false),
			item("SPEED_ARTIFACT", "Speed Artifact", "RARE", false)
		);
		Map<String, JsonObject> recipes = Map.of(
			"SPEED_RING", recipe("ENCHANTED_SUGAR:12", "SPEED_TALISMAN:1"),
			"SPEED_ARTIFACT", recipe("ENCHANTED_SUGAR_CANE:6", "SPEED_RING:1")
		);
		Map<String, Double> prices = Map.of(
			"ENCHANTED_SUGAR", 1_000D, "ENCHANTED_SUGAR_CANE", 100_000D,
			"SPEED_TALISMAN", 50_000D, "SPEED_ARTIFACT", 5_000_000D
		);
		Result result = MissingAccessories.compute(
			new Profile(List.of(owned("SPEED_TALISMAN", "COMMON", false)), true, false),
			catalog, Map.of(), Set.of(), id -> prices.getOrDefault(id, 0D), recipes
		);

		assertEquals(1, result.entries().size());
		Entry upgrade = result.entries().get(0);
		assertEquals("SPEED_ARTIFACT", upgrade.id());
		assertEquals(MissingAccessories.CostBasis.CRAFT, upgrade.costBasis());
		assertEquals("SPEED_TALISMAN", upgrade.soldId());
		assertEquals(612_000D, upgrade.netCost(), 0.01D);
		assertEquals(5, upgrade.mpGain());
		assertConsistentTotals(result);
	}

	@Test
	public void craftCanConsumeLowerTierHeldInStorage() {
		Result result = MissingAccessories.compute(
			new Profile(
				List.of(new OwnedAccessory("HUNTER_TALISMAN", "UNCOMMON", false, List.of(), "Ender Chest")), true, false
			),
			hunterCatalog(), Map.of(), Set.of(), hunterPrices(true)::get, Map.of("HUNTER_RING", hunterRingRecipe())
		);

		Entry upgrade = entry(result, UpgradeType.TIER_UPGRADE);
		assertEquals(MissingAccessories.CostBasis.CRAFT, upgrade.costBasis());
		assertEquals(740_000D, upgrade.netCost(), 0.01D);
		assertEquals(8, upgrade.mpGain());
		assertEquals("Owned: Uncommon Hunter Talisman (Ender Chest)", upgrade.currentState());
		assertFalse(result.entries().stream().anyMatch(e -> e.type() == UpgradeType.MOVE_TO_BAG));
		assertConsistentTotals(result);
	}

	@Test
	public void unpricedRecipeMaterialMarksUpgradeCostUnavailable() {
		Result result = MissingAccessories.compute(
			new Profile(List.of(owned("HUNTER_TALISMAN", "UNCOMMON", false)), true, false),
			hunterCatalog(), Map.of(), Set.of(), hunterPrices(false)::get, Map.of("HUNTER_RING", hunterRingRecipe())
		);

		Entry upgrade = entry(result, UpgradeType.TIER_UPGRADE);
		assertEquals(MissingAccessories.CostBasis.CRAFT, upgrade.costBasis());
		assertEquals(PriceStatus.MISSING, upgrade.priceStatus());
		assertFalse(upgrade.priced());
		assertEquals("HUNTER_TALISMAN", upgrade.soldId());
		assertEquals(0D, upgrade.soldPrice(), 0D);
		assertEquals(Double.MAX_VALUE, upgrade.coinsPerMp(), 0D);
		assertEquals(9_000_000D, result.costToMax(), 0.01D);
		assertEquals(1, result.unpricedTop());
		assertConsistentTotals(result);
	}

	@Test
	public void multiCopyRecipesCraftFromOwnedTierAndCostExtraCopiesAtCheapestRoute() {
		List<JsonObject> catalog = List.of(
			item("EMPEROR_TALISMAN", "Emperor's Talisman", "UNCOMMON", false),
			item("EMPEROR_RING", "Emperor's Ring", "RARE", false),
			item("EMPEROR_ARTIFACT", "Emperor's Artifact", "EPIC", false)
		);
		// NEU: 4 talismans make a ring and 4 rings make an artifact.
		Map<String, JsonObject> recipes = Map.of(
			"EMPEROR_RING", recipe("EMPEROR_TALISMAN:1", "EMPEROR_TALISMAN:1", "EMPEROR_TALISMAN:1", "EMPEROR_TALISMAN:1"),
			"EMPEROR_ARTIFACT", recipe("EMPEROR_RING:1", "EMPEROR_RING:1", "EMPEROR_RING:1", "EMPEROR_RING:1")
		);
		Profile profile = new Profile(List.of(owned("EMPEROR_TALISMAN", "UNCOMMON", false)), true, false);

		// Rings cost more to buy (6M) than to craft (4 x 1M), so the extra rings are crafted.
		Map<String, Double> craftCheaper = Map.of(
			"EMPEROR_TALISMAN", 1_000_000D, "EMPEROR_RING", 6_000_000D, "EMPEROR_ARTIFACT", 200_000_000D
		);
		Entry crafted = MissingAccessories.compute(
			profile, catalog, Map.of(), Set.of(), craftCheaper::get, recipes
		).entries().get(0);
		assertEquals("EMPEROR_ARTIFACT", crafted.id());
		assertEquals(MissingAccessories.CostBasis.CRAFT, crafted.costBasis());
		assertEquals("EMPEROR_TALISMAN", crafted.soldId());
		assertEquals(3_000_000D + 3 * 4_000_000D, crafted.netCost(), 0.01D);
		assertEquals(7, crafted.mpGain());

		// Rings at 3M are cheaper to buy than to craft.
		Map<String, Double> buyCheaper = Map.of(
			"EMPEROR_TALISMAN", 1_000_000D, "EMPEROR_RING", 3_000_000D, "EMPEROR_ARTIFACT", 200_000_000D
		);
		Entry bought = MissingAccessories.compute(
			profile, catalog, Map.of(), Set.of(), buyCheaper::get, recipes
		).entries().get(0);
		assertEquals(3_000_000D + 3 * 3_000_000D, bought.netCost(), 0.01D);
	}

	@Test
	public void tierLabelsAreTitleCased() {
		assertEquals("Epic", MissingAccessories.tierLabel("EPIC"));
		assertEquals("Very Special", MissingAccessories.tierLabel("VERY_SPECIAL"));
		assertEquals("", MissingAccessories.tierLabel(""));
	}

	@Test
	public void hunterRingOwnedOnlyInStorageIsMovedNotBoughtAndNotCountedAsActive() {
		Result result = compute(
			List.of(item("HUNTER_RING", "Hunter Ring", "RARE", true)),
			List.of(new OwnedAccessory("HUNTER_RING", "RARE", false, List.of(), "Personal Vault")),
			false, Map.of("HUNTER_RING", 5_000_000D, "RECOMBOBULATOR_3000", 9_000_000D)
		);

		assertFalse(result.entries().stream().anyMatch(e -> e.type() == UpgradeType.PURCHASE));
		Entry move = entry(result, UpgradeType.MOVE_TO_BAG);
		assertEquals(8, move.mpGain());
		assertTrue(move.priced());
		assertEquals(0D, move.netCost(), 0D);
		assertTrue(move.currentState().contains("Personal Vault"));
		assertNull(move.prerequisite());
		Entry recomb = entry(result, UpgradeType.RECOMBOBULATE);
		assertEquals(4, recomb.mpGain());
		assertEquals("Move Hunter Ring to bag", recomb.prerequisite());
		assertTrue(recomb.name().startsWith("Then recombobulate"));
		assertEquals(result.entries().indexOf(move) + 1, result.entries().indexOf(recomb));
		assertEquals(12, result.mpToMax());
		assertEquals(9_000_000D, result.costToMax(), 0.01D);
		assertConsistentTotals(result);
	}

	@Test
	public void storedHigherFamilyTierIsMovedInsteadOfBought() {
		List<JsonObject> catalog = List.of(
			item("WOLF_TALISMAN", "Wolf Talisman", "COMMON", true),
			item("WOLF_RING", "Wolf Ring", "UNCOMMON", true)
		);
		Result result = compute(
			catalog,
			List.of(
				owned("WOLF_TALISMAN", "COMMON", false),
				new OwnedAccessory("WOLF_RING", "UNCOMMON", false, List.of(), "Ender Chest")
			),
			false, Map.of("WOLF_RING", 1_000_000D, "RECOMBOBULATOR_3000", 9_000_000D)
		);

		assertFalse(result.entries().stream().anyMatch(
			e -> e.type() == UpgradeType.PURCHASE || e.type() == UpgradeType.TIER_UPGRADE
		));
		Entry move = entry(result, UpgradeType.MOVE_TO_BAG);
		assertEquals("WOLF_RING", move.id());
		assertEquals(2, move.mpGain());
		Entry recomb = entry(result, UpgradeType.RECOMBOBULATE);
		assertTrue(recomb.name().contains("Wolf Ring"));
		assertEquals(3, recomb.mpGain());
		assertEquals(5, result.mpToMax());
		assertConsistentTotals(result);
	}

	@Test
	public void neuOnlyChainIsPlannedOnceAtItsTopTier() {
		List<JsonObject> catalog = List.of(
			item("TEST_TALISMAN", "Test Talisman", "COMMON", false),
			item("TEST_RING", "Test Ring", "UNCOMMON", false),
			item("TEST_ARTIFACT", "Test Artifact", "RARE", false)
		);
		Map<String, List<String>> upgrades = Map.of(
			"TEST_TALISMAN", List.of("TEST_RING", "TEST_ARTIFACT"),
			"TEST_RING", List.of("TEST_ARTIFACT")
		);
		Result result = MissingAccessories.compute(
			new Profile(List.of(), true, false), catalog, upgrades, Set.of(), id -> 1_000_000D
		);

		assertEquals(1, result.entries().size());
		assertEquals("TEST_ARTIFACT", result.entries().get(0).id());
		assertEquals(8, result.mpToMax());
		assertConsistentTotals(result);
	}

	@Test
	public void recombsSortByActualIncrementalGain() {
		List<JsonObject> catalog = List.of(
			item("HEGEMONY_ARTIFACT", "Hegemony Artifact", "LEGENDARY", true),
			item("HUNTER_RING", "Hunter Ring", "RARE", true)
		);
		Result result = compute(
			catalog,
			List.of(owned("HEGEMONY_ARTIFACT", "LEGENDARY", false), owned("HUNTER_RING", "RARE", false)),
			false,
			Map.of("RECOMBOBULATOR_3000", 12_000_000D)
		);

		List<Entry> recombs = result.entries().stream().filter(e -> e.type() == UpgradeType.RECOMBOBULATE).toList();
		assertEquals(12, recombs.get(0).mpGain());
		assertEquals(4, recombs.get(1).mpGain());
		assertTrue(recombs.get(0).coinsPerMp() < recombs.get(1).coinsPerMp());
	}

	@Test
	public void missingRecombPriceIsNotFree() {
		Result result = compute(
			List.of(item("HUNTER_RING", "Hunter Ring", "RARE", true)),
			List.of(owned("HUNTER_RING", "RARE", false)), false, Map.of()
		);
		Entry recomb = entry(result, UpgradeType.RECOMBOBULATE);
		assertEquals(PriceStatus.MISSING, recomb.priceStatus());
		assertFalse(recomb.priced());
		assertEquals(Double.MAX_VALUE, recomb.coinsPerMp(), 0D);
	}

	@Test
	public void staleRecombPriceIsShownButNotRanked() {
		Result result = MissingAccessories.computeQuoted(
			new Profile(List.of(owned("HUNTER_RING", "RARE", false)), true, false),
			List.of(item("HUNTER_RING", "Hunter Ring", "RARE", true)), Map.of(), Set.of(),
			id -> new PriceQuote(9_000_000D, PriceSource.VYRIV, PriceAvailability.STALE)
		);

		Entry recomb = entry(result, UpgradeType.RECOMBOBULATE);
		assertEquals(PriceStatus.STALE, recomb.priceStatus());
		assertFalse(recomb.priced());
		assertEquals(0D, result.costToMax(), 0D);
	}

	@Test
	public void pandoraNotOwnedCountsPurchaseAndUnpricedProgressionOnce() {
		Result result = compute(
			List.of(item("PANDORAS_BOX", "Pandora's Box", "COMMON", false)),
			List.of(), false, Map.of("PANDORAS_BOX", 500_000_000D)
		);
		assertEquals(22, result.mpToMax());
		Entry purchase = entry(result, UpgradeType.PURCHASE);
		assertEquals(3, purchase.mpGain());
		Entry progress = entry(result, UpgradeType.PANDORA_PROGRESSION);
		assertEquals(19, progress.mpGain());
		assertEquals(PriceStatus.NOT_PRICED, progress.priceStatus());
		assertEquals("Pandora's Box", progress.prerequisite());
		assertEquals(result.entries().indexOf(purchase) + 1, result.entries().indexOf(progress));
	}

	@Test
	public void pandoraOwnedProgressUsesActualRarityAndChallengeLore() {
		List<JsonObject> catalog = List.of(item("PANDORAS_BOX", "Pandora's Box", "COMMON", false));
		Result noProgress = compute(
			catalog,
			List.of(new OwnedAccessory("PANDORAS_BOX", "COMMON", false, List.of("Challenges completed: 0/20"))),
			false, Map.of()
		);
		assertEquals(19, entry(noProgress, UpgradeType.PANDORA_PROGRESSION).mpGain());

		Result partial = compute(
			catalog,
			List.of(new OwnedAccessory("PANDORAS_BOX", "LEGENDARY", false, List.of("§7Challenges completed: §e9/20"))),
			false, Map.of()
		);
		Entry partialProgress = entry(partial, UpgradeType.PANDORA_PROGRESSION);
		assertEquals(6, partialProgress.mpGain());
		assertTrue(partialProgress.currentState().contains("9/20"));
		assertEquals(6, partial.mpToMax());
	}

	@Test
	public void fullyProgressedPandoraHasNoRemainingUpgrade() {
		Result result = compute(
			List.of(item("PANDORAS_BOX", "Pandora's Box", "COMMON", false)),
			List.of(new OwnedAccessory("PANDORAS_BOX", "MYTHIC", false, List.of("Challenges completed: 20/20"))),
			false, Map.of()
		);
		assertEquals(0, result.mpToMax());
		assertFalse(result.entries().stream().anyMatch(e -> e.type() == UpgradeType.PANDORA_PROGRESSION));
	}

	@Test
	public void missingInventoryMakesSpecialProgressUnknown() {
		Result result = MissingAccessories.compute(
			new Profile(List.of(), false, null),
			List.of(
				item("PANDORAS_BOX", "Pandora's Box", "COMMON", false),
				riftPrism()
			),
			Map.of(), Set.of(), id -> 1_000_000D
		);
		assertEquals(0, result.mpToMax());
		assertEquals(2, result.unknownStates());
		assertTrue(result.entries().stream().allMatch(Entry::unknownState));
	}

	@Test
	public void riftPrismNotOwnedUsesElevenMpPathWithoutInflatingTotal() {
		Result result = compute(
			List.of(riftPrism()), List.of(), false, Map.of("RIFT_PRISM", 1_399_800_000D)
		);
		Entry imbue = entry(result, UpgradeType.RIFT_PRISM_IMBUEMENT);
		assertEquals(11, imbue.mpGain());
		assertEquals(1_400_000_000D, imbue.netCost(), 0.01D);
		assertEquals(11, result.mpToMax());
		assertEquals(1, result.entries().stream().filter(e -> e.id().equals("RIFT_PRISM")).count());
		assertFalse(result.entries().stream().anyMatch(e -> e.type() == UpgradeType.PURCHASE));
		assertEquals(1_400_000_000D, result.costToMax(), 0.01D);
		assertConsistentTotals(result);
	}

	@Test
	public void storedRiftPrismIsMovedThenImbuedWithoutDoubleCounting() {
		Result result = compute(
			List.of(riftPrism()),
			List.of(new OwnedAccessory("RIFT_PRISM", "RARE", false, List.of(), "Ender Chest")),
			false, Map.of("RIFT_PRISM", 1_399_800_000D)
		);
		assertFalse(result.entries().stream().anyMatch(e -> e.type() == UpgradeType.PURCHASE));
		assertEquals(8, entry(result, UpgradeType.MOVE_TO_BAG).mpGain());
		Entry imbue = entry(result, UpgradeType.RIFT_PRISM_IMBUEMENT);
		assertEquals(3, imbue.mpGain());
		assertEquals(11, result.mpToMax());
		assertEquals(1_400_000_000D, result.costToMax(), 0.01D);
		assertConsistentTotals(result);
	}

	@Test
	public void ownedRiftPrismImbuementIsOnlyThreeIncrementalMp() {
		Result result = compute(
			List.of(riftPrism()), List.of(owned("RIFT_PRISM", "RARE", false)), false,
			Map.of("RIFT_PRISM", 1_399_800_000D)
		);
		Entry imbue = entry(result, UpgradeType.RIFT_PRISM_IMBUEMENT);
		assertEquals(3, imbue.mpGain());
		assertEquals(1_400_000_000D / 3D, imbue.coinsPerMp(), 0.01D);
		assertEquals(3, result.mpToMax());
	}

	@Test
	public void consumedRiftPrismIsNotRecommendedAgain() {
		Result result = compute(List.of(riftPrism()), List.of(), true, Map.of("RIFT_PRISM", 1_000_000_000D));
		assertEquals(0, result.mpToMax());
		assertFalse(result.entries().stream().anyMatch(e -> e.id().equals("RIFT_PRISM")));
	}

	@Test
	public void missingRiftStateIsUnknownAndNotAssumedUnimbued() {
		Result result = computeWithRiftState(List.of(riftPrism()), List.of(), null, Map.of("RIFT_PRISM", 1D));
		assertEquals(0, result.mpToMax());
		Entry unknown = entry(result, UpgradeType.UNKNOWN_STATE);
		assertEquals("RIFT_PRISM", unknown.id());
	}

	@Test
	public void ordinaryAndConsumedRiftPowerAreNotDoubleCounted() {
		InventoryDecoder.Stack prism = new InventoryDecoder.Stack(
			"RIFT_PRISM", 1, new CompoundTag(), List.of("§9§lRARE ACCESSORY"), false
		);
		assertEquals(8, MagicalPowerCalculator.fromStacks(List.of(prism), 0, false));
		assertEquals(11, MagicalPowerCalculator.fromStacks(List.of(prism), 0, true));
		assertEquals(11, MagicalPowerCalculator.fromStacks(List.of(), 0, true));
	}

	@Test
	public void profileRiftFieldIsTriState() {
		JsonObject member = new JsonObject();
		assertNull(MagicalPowerCalculator.riftPrismConsumedState(member));
		JsonObject access = new JsonObject();
		JsonObject rift = new JsonObject();
		rift.add("access", access);
		member.add("rift", rift);
		assertEquals(Boolean.FALSE, MagicalPowerCalculator.riftPrismConsumedState(member));
		access.addProperty("consumed_prism", false);
		assertEquals(Boolean.FALSE, MagicalPowerCalculator.riftPrismConsumedState(member));
		access.addProperty("consumed_prism", true);
		assertEquals(Boolean.TRUE, MagicalPowerCalculator.riftPrismConsumedState(member));
		access.addProperty("consumed_prism", "not-a-boolean");
		assertNull(MagicalPowerCalculator.riftPrismConsumedState(member));
	}

	@Test
	public void familyAlternativesDoNotInflateMissingMpAndPricesSortSafely() {
		List<JsonObject> catalog = List.of(
			item("WOLF_TALISMAN", "Wolf Talisman", "COMMON", true),
			item("WOLF_RING", "Wolf Ring", "UNCOMMON", true),
			item("HUNTER_RING", "Hunter Ring", "RARE", true)
		);
		Map<String, List<String>> upgrades = Map.of("WOLF_TALISMAN", List.of("WOLF_RING"));
		Map<String, Double> prices = Map.of(
			"WOLF_TALISMAN", 100_000D, "WOLF_RING", 1_000_000D, "ENCHANTED_BONE", 10_000D,
			"RECOMBOBULATOR_3000", 10_000_000D
		);
		Result result = MissingAccessories.compute(
			new Profile(List.of(owned("WOLF_TALISMAN", "COMMON", false)), true, null),
			catalog, upgrades, Set.of(), id -> prices.getOrDefault(id, 0D),
			Map.of("WOLF_RING", recipe("WOLF_TALISMAN:1", "ENCHANTED_BONE:8"))
		);

		assertEquals(17, result.mpToMax());
		List<Entry> wolf = result.entries().stream()
			.filter(e -> e.conflictGroup().equals("family:WOLF_TALISMAN"))
			.toList();
		assertEquals(2, wolf.size());
		assertEquals(UpgradeType.TIER_UPGRADE, result.entries().get(0).type());
		assertTrue(result.entries().get(result.entries().size() - 1).unknownState());
	}

	@Test
	public void invertedNeuPowderChainDefersToBetterPvFamilyOrder() {
		List<JsonObject> catalog = List.of(
			item("POWDER_OF_ONE", "Powder of One", "COMMON", true),
			item("POWDER_OF_THE_FEW", "Powder of the Few", "UNCOMMON", true),
			item("POWDER_OF_THE_MANY", "Powder of the Many", "RARE", true),
			item("POWDER_OF_THE_PEOPLE", "Powder of the People", "EPIC", true)
		);
		Map<String, List<String>> invertedNeu = Map.of(
			"POWDER_OF_THE_PEOPLE", List.of("POWDER_OF_THE_MANY", "POWDER_OF_THE_FEW", "POWDER_OF_ONE"),
			"POWDER_OF_THE_MANY", List.of("POWDER_OF_THE_FEW", "POWDER_OF_ONE"),
			"POWDER_OF_THE_FEW", List.of("POWDER_OF_ONE")
		);
		Result result = MissingAccessories.compute(
			new Profile(List.of(owned("POWDER_OF_THE_FEW", "UNCOMMON", false)), true, false),
			catalog, invertedNeu, Set.of(), id -> 1_000_000D
		);

		List<Entry> powder = result.entries().stream()
			.filter(e -> e.type() == UpgradeType.PURCHASE || e.type() == UpgradeType.TIER_UPGRADE)
			.toList();
		assertEquals(1, powder.size());
		assertEquals("POWDER_OF_THE_PEOPLE", powder.get(0).id());
		assertEquals(7, powder.get(0).mpGain());
	}

	@Test
	public void hotspotFamilyOnlyRecommendsTopMissingTier() {
		List<JsonObject> catalog = List.of(
			item("HOTSPOT_TALISMAN", "Hotspot Talisman", "COMMON", true),
			item("HOTSPOT_RING", "Hotspot Ring", "UNCOMMON", true),
			item("HOTSPOT_ARTIFACT", "Hotspot Artifact", "RARE", true)
		);
		Result result = compute(catalog, List.of(), false, Map.of("HOTSPOT_ARTIFACT", 2_000_000D));

		List<Entry> purchases = result.entries().stream().filter(e -> e.type() == UpgradeType.PURCHASE).toList();
		assertEquals(1, purchases.size());
		assertEquals("HOTSPOT_ARTIFACT", purchases.get(0).id());
	}

	@Test
	public void netCostSortKeepsUnpricedAndUnknownEntriesLast() {
		List<JsonObject> catalog = List.of(
			item("HUNTER_RING", "Hunter Ring", "RARE", true),
			item("HEGEMONY_ARTIFACT", "Hegemony Artifact", "LEGENDARY", true),
			item("NO_PRICE_RING", "No Price Ring", "RARE", true),
			riftPrism()
		);
		Map<String, Double> prices = Map.of("HUNTER_RING", 5_000_000D, "HEGEMONY_ARTIFACT", 50_000_000D);
		Result result = computeWithRiftState(catalog, List.of(), null, prices);

		List<Entry> byCost = result.entries().stream().sorted(MissingAccessories.byNetCost()).toList();
		assertEquals("HUNTER_RING", byCost.get(0).id());
		assertEquals("HEGEMONY_ARTIFACT", byCost.get(1).id());
		assertTrue(byCost.get(byCost.size() - 1).unknownState());

		List<Entry> byMp = result.entries().stream().sorted(MissingAccessories.byCoinsPerMp()).toList();
		assertTrue(byMp.get(0).priced() && byMp.get(1).priced());
		assertTrue(byMp.get(0).coinsPerMp() <= byMp.get(1).coinsPerMp());
	}

	private static Result compute(
		Collection<JsonObject> catalog, Collection<OwnedAccessory> owned,
		boolean riftConsumed, Map<String, Double> prices
	) {
		return computeWithRiftState(catalog, owned, riftConsumed, prices);
	}

	private static Result computeWithRiftState(
		Collection<JsonObject> catalog, Collection<OwnedAccessory> owned,
		Boolean riftConsumed, Map<String, Double> prices
	) {
		return MissingAccessories.compute(
			new Profile(owned, true, riftConsumed), catalog, Map.of(), Set.of(),
			id -> prices.getOrDefault(id, 0D)
		);
	}

	static List<JsonObject> hunterCatalog() {
		return List.of(
			item("HUNTER_TALISMAN", "Hunter Talisman", "UNCOMMON", true),
			item("HUNTER_RING", "Hunter Ring", "RARE", true)
		);
	}

	static Map<String, Double> hunterPrices(boolean baitPriced) {
		Map<String, Double> prices = new java.util.HashMap<>(Map.of(
			"GOLDEN_TOOTH", 10_000D, "HUNTER_TALISMAN", 1_000_000D, "HUNTER_RING", 5_000_000D,
			"RECOMBOBULATOR_3000", 9_000_000D
		));
		if (baitPriced) {
			prices.put("GRIZZLY_BAIT", 100_000D);
		}
		return Map.copyOf(prices);
	}

	/** Trimmed NEU HUNTER_RING grid: 64 Golden Teeth, a Grizzly Bait and the consumed Hunter Talisman. */
	static JsonObject hunterRingRecipe() {
		JsonObject recipe = recipe("GOLDEN_TOOTH:32", "HUNTER_TALISMAN:1", "GRIZZLY_BAIT:1");
		recipe.addProperty("C1", "GOLDEN_TOOTH:32");
		return recipe;
	}

	static JsonObject recipe(String... cells) {
		String[] slots = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};
		JsonObject recipe = new JsonObject();
		for (int i = 0; i < cells.length; i++) {
			recipe.addProperty(slots[i], cells[i]);
		}
		return recipe;
	}

	private static void assertConsistentTotals(Result result) {
		int mp = result.entries().stream().filter(e -> !e.unknownState()).mapToInt(Entry::mpGain).sum();
		double cost = result.entries().stream().filter(Entry::priced).mapToDouble(Entry::netCost).sum();
		assertEquals(mp, result.mpToMax());
		assertEquals(cost, result.costToMax(), 0.01D);
	}

	private static Entry entry(Result result, UpgradeType type) {
		Entry entry = result.entries().stream().filter(candidate -> candidate.type() == type).findFirst().orElse(null);
		assertNotNull("Missing entry type " + type, entry);
		return entry;
	}

	private static OwnedAccessory owned(String id, String tier, boolean recombobulated) {
		return new OwnedAccessory(id, tier, recombobulated, List.of());
	}

	private static JsonObject riftPrism() {
		JsonObject item = item("RIFT_PRISM", "Rift Prism", "RARE", false);
		item.addProperty("origin", "RIFT");
		item.addProperty("rift_transferrable", true);
		return item;
	}

	private static JsonObject item(String id, String name, String tier, boolean canRecombobulate) {
		JsonObject item = new JsonObject();
		item.addProperty("id", id);
		item.addProperty("name", name);
		item.addProperty("category", "ACCESSORY");
		item.addProperty("tier", tier);
		item.addProperty("can_recombobulate", canRecombobulate);
		return item;
	}
}
