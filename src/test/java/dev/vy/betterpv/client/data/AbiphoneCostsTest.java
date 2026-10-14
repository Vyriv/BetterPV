package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;

public class AbiphoneCostsTest {
	private static final Map<String, String> NAMES = Map.of(
		"mutant nether wart", "MUTANT_NETHER_STALK",
		"silent pearl", "SILENT_PEARL",
		"ultimate carrot candy", "ULTIMATE_CARROT_CANDY",
		"enchanted sulphur cube", "ENCHANTED_SULPHUR_CUBE",
		"kat flower", "KAT_FLOWER"
	);
	private static final Map<String, Double> PRICES = Map.of(
		"MUTANT_NETHER_STALK", 10_000D,
		"SILENT_PEARL", 500D,
		"ULTIMATE_CARROT_CANDY", 2_000_000D,
		"ENCHANTED_SULPHUR_CUBE", 300_000D
	);
	private static final Function<String, String> ID = name -> NAMES.get(name.toLowerCase());
	private static final Function<String, Double> PRICE = PRICES::get;

	private static AbiphoneCosts.Estimate est(String... lines) {
		return AbiphoneCosts.estimate(List.of(lines), ID, PRICE);
	}

	@Test
	public void pricesItemLinesAndPlurals() {
		assertEquals(640_000D, est("- 64x Mutant Nether Wart.").coins(), 0.01);
		assertEquals(32_000D, est("- 64x Silent Pearls").coins(), 0.01);
		assertEquals(4_000_000D, est("- 2x Ultimate Carrot Candies").coins(), 0.01);
	}

	@Test
	public void stripsTrailingParenthetical() {
		assertEquals(300_000D,
			est("- 1x Enchanted Sulphur Cube. (Must be Sulphur VII Collection or higher.)").coins(), 0.01);
	}

	@Test
	public void coinAmountsInEitherSeparatorStyle() {
		assertEquals(32_000_000D, est("- 32.000.000 Coins").coins(), 0.01);
		assertEquals(1_000_000D, est("- 1,000,000 Coins").coins(), 0.01);
	}

	@Test
	public void noRequirementIsFree() {
		AbiphoneCosts.Estimate free = est("- No Requirement.");
		assertTrue(free.known());
		assertEquals(0D, free.coins(), 0.0);
	}

	@Test
	public void unpricedOrNonItemRequirementsAreUnknown() {
		assertFalse(est("- 1x Kat Flower").known());
		assertFalse(est("- Kill Arachne once.").known());
		assertFalse(est("- 10,000,000 Coins or", "- Clear Target Practice IV").known());
		assertFalse(est("- 24x Pelts", "- 1,000,000 Coins").known());
		assertFalse(est("- 1x [Lvl 1] Tarantula").known());
		assertFalse(AbiphoneCosts.estimate(List.of(), ID, PRICE).known());
		assertFalse(est("").known());
	}
}
