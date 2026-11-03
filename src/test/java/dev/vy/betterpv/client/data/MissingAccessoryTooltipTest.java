package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.MissingAccessories.Entry;
import dev.vy.betterpv.client.data.MissingAccessories.OwnedAccessory;
import dev.vy.betterpv.client.data.MissingAccessories.Profile;
import dev.vy.betterpv.client.data.MissingAccessories.Result;
import dev.vy.betterpv.client.data.MissingAccessories.UpgradeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class MissingAccessoryTooltipTest {
	@Test
	public void purchaseUsesFriendlyLabelsAndPriceAttribution() {
		Result result = compute(List.of(), Map.of(), true);
		List<String> lines = render(find(result, UpgradeType.PURCHASE));

		assertEquals("Buy", lines.get(0));
		assertEquals("Not owned", lines.get(1));
		assertTrue(lines.contains("Cost: 5,000,000"));
		assertTrue(lines.contains("Magical Power: +8"));
		assertNoPriceSource(lines);
		assertNoRawEnumText(lines);
	}

	@Test
	public void followUpShowsItsRequirementOnce() {
		Result result = compute(List.of(), Map.of(), true);
		Entry recomb = find(result, UpgradeType.RECOMBOBULATE);
		List<String> lines = render(recomb);

		assertEquals("Buy Hunter Ring first", lines.get(0));
		assertEquals(1, lines.stream().filter(line -> line.contains("Hunter Ring first")).count());
		assertFalse(lines.stream().anyMatch(line -> line.startsWith("Requires")));
		assertFalse(lines.contains("Recombobulate"));
		assertNoRawEnumText(lines);
	}

	@Test
	public void ownedTierReadsNaturallyAndCraftShowsConsumedAccessory() {
		Result result = compute(
			List.of(new OwnedAccessory("HUNTER_TALISMAN", "UNCOMMON", false, List.of())),
			Map.of("HUNTER_RING", MissingAccessoriesTest.hunterRingRecipe()), true
		);
		List<String> lines = render(find(result, UpgradeType.TIER_UPGRADE));

		assertEquals("Craft upgrade", lines.get(0));
		assertEquals("Owned: Uncommon Hunter Talisman", lines.get(1));
		assertTrue(lines.contains("Craft cost: 740,000"));
		assertTrue(lines.contains("Uses your Hunter Talisman"));
		assertNoPriceSource(lines);
		assertFalse(lines.stream().anyMatch(line -> line.contains("sell")));
		assertNoRawEnumText(lines);
	}

	@Test
	public void upgradeWithoutKnownMethodShowsUnavailableCostInsteadOfResale() {
		Result result = compute(List.of(new OwnedAccessory("HUNTER_TALISMAN", "UNCOMMON", false, List.of())), Map.of(), true);
		List<String> lines = render(find(result, UpgradeType.TIER_UPGRADE));

		assertEquals("Upgrade", lines.get(0));
		assertTrue(lines.contains("Cost: Unknown upgrade method"));
		assertTrue(lines.contains("Replaces your Hunter Talisman"));
		assertFalse(lines.stream().anyMatch(line -> line.toLowerCase(java.util.Locale.ROOT).contains("sell")
			|| line.contains("~") || line.startsWith("Coins per MP")));
		assertNoPriceSource(lines);
	}

	@Test
	public void craftWithUnpricedMaterialSaysSoWithoutGuessing() {
		Map<String, Double> prices = new java.util.HashMap<>(MissingAccessoriesTest.hunterPrices(false));
		Result result = MissingAccessories.compute(
			new Profile(List.of(new OwnedAccessory("HUNTER_TALISMAN", "UNCOMMON", false, List.of())), true, true),
			MissingAccessoriesTest.hunterCatalog(), Map.of(), Set.of(), prices::get,
			Map.of("HUNTER_RING", MissingAccessoriesTest.hunterRingRecipe())
		);
		List<String> lines = render(find(result, UpgradeType.TIER_UPGRADE));

		assertEquals("Craft upgrade", lines.get(0));
		assertTrue(lines.contains("Craft cost: Material prices unavailable"));
		assertTrue(lines.contains("Uses your Hunter Talisman"));
		assertNoPriceSource(lines);
	}

	@Test
	public void moveToBagIsFreeWithoutSourceOrRate() {
		Result result = compute(
			List.of(new OwnedAccessory("HUNTER_RING", "RARE", false, List.of(), "Personal Vault")), Map.of(), true
		);
		List<String> lines = render(find(result, UpgradeType.MOVE_TO_BAG));

		assertEquals("Owned: Rare Hunter Ring (Personal Vault)", lines.get(0));
		assertTrue(lines.contains("Cost: Free, already owned"));
		assertFalse(lines.stream().anyMatch(line -> line.startsWith("Coins per MP")));
		assertNoPriceSource(lines);
	}

	@Test
	public void unpricedAndImbueLinesStayReadable() {
		Result result = compute(List.of(), Map.of(), false);
		assertTrue(render(find(result, UpgradeType.PURCHASE)).contains("Cost: No price available"));

		Result rift = MissingAccessories.compute(
			new Profile(List.of(), true, false), List.of(riftPrism()), Map.of(), Set.of(), id -> 1_000_000D
		);
		List<String> lines = render(find(rift, UpgradeType.RIFT_PRISM_IMBUEMENT));
		assertTrue(lines.contains("Includes 200,000 imbue fee"));
		assertNoPriceSource(lines);
		assertNoRawEnumText(lines);
	}

	private static void assertNoPriceSource(List<String> lines) {
		for (String line : lines) {
			assertFalse(line, line.contains("Vyriv") || line.contains("SkyHelper") || line.contains(" from "));
		}
	}

	private static Result compute(List<OwnedAccessory> owned, Map<String, JsonObject> recipes, boolean ringPriced) {
		Map<String, Double> prices = new java.util.HashMap<>(MissingAccessoriesTest.hunterPrices(true));
		if (!ringPriced) {
			prices.remove("HUNTER_RING");
		}
		return MissingAccessories.compute(
			new Profile(owned, true, true), MissingAccessoriesTest.hunterCatalog(), Map.of(), Set.of(),
			prices::get, recipes
		);
	}

	private static Entry find(Result result, UpgradeType type) {
		return result.entries().stream().filter(entry -> entry.type() == type).findFirst().orElseThrow();
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

	private static void assertNoRawEnumText(List<String> lines) {
		for (String line : lines) {
			assertFalse(line, line.matches(".*\\b[A-Z]{3,}\\b.*") || line.contains("_"));
		}
	}

	private static JsonObject riftPrism() {
		JsonObject item = new JsonObject();
		item.addProperty("id", "RIFT_PRISM");
		item.addProperty("name", "Rift Prism");
		item.addProperty("category", "ACCESSORY");
		item.addProperty("tier", "RARE");
		item.addProperty("origin", "RIFT");
		item.addProperty("rift_transferrable", true);
		item.addProperty("can_recombobulate", false);
		return item;
	}
}
