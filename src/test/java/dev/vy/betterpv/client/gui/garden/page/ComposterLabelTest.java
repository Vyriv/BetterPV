package dev.vy.betterpv.client.gui.garden.page;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.GardenCosts;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComposterLabelTest {
	private static final ToIntFunction<String> WIDTH = String::length;
	private static final Map<String, Double> PRICES = Map.of("ENCHANTED_WHEAT", 1_000D, "CONDENSED_FERMENTO", 500_000D);
	private static final ToDoubleFunction<String> PRICE = id -> PRICES.getOrDefault(id, 0D);

	private static GardenCosts.ComposterRemaining remaining(long copper, Map<String, Long> crops, Map<String, Long> rare) {
		return new GardenCosts.ComposterRemaining(copper, crops, rare);
	}

	@Test
	public void coinCostSumsMaterialValueAndIgnoresCopper() {
		var total = remaining(1_250_000L, Map.of("ENCHANTED_WHEAT", 1_000L), Map.of("CONDENSED_FERMENTO", 4L));
		assertEquals(3_000_000D, total.coins(PRICE), 0.001D);
		assertEquals(0, total.unpriced(PRICE));
		assertEquals("To max: " + FormatUtil.shortCoins(3_000_000D) + " coins", CropsPage.costToMaxLabel(total, PRICE, WIDTH, 100));
	}

	@Test
	public void neverLabelsCopperAsCoins() {
		var total = remaining(1_250_000L, Map.of("ENCHANTED_WHEAT", 10L), Map.of());
		String label = CropsPage.costToMaxLabel(total, PRICE, WIDTH, 100);
		assertEquals("To max: " + FormatUtil.shortCoins(10_000D) + " coins", label);
		assertFalse(label.contains(FormatUtil.shortCoins(1_250_000L)));
	}

	@Test
	public void partialPricesShowALowerBound() {
		var total = remaining(0L, Map.of("ENCHANTED_WHEAT", 10L, "MYSTERY_CROP", 5L), Map.of());
		assertEquals(1, total.unpriced(PRICE));
		assertEquals("To max: " + FormatUtil.shortCoins(10_000D) + "+ coins", CropsPage.costToMaxLabel(total, PRICE, WIDTH, 100));
	}

	@Test
	public void unknownPricesAreNotShownAsZero() {
		var total = remaining(500L, Map.of("MYSTERY_CROP", 5L), Map.of());
		assertEquals("To max: ? coins", CropsPage.costToMaxLabel(total, id -> 0D, WIDTH, 100));
	}

	@Test
	public void copperOnlyRemainderSaysSo() {
		var total = remaining(500L, Map.of(), Map.of());
		assertEquals("To max: Copper only", CropsPage.costToMaxLabel(total, PRICE, WIDTH, 100));
	}

	@Test
	public void shortensToFitPanel() {
		var total = remaining(0L, Map.of("ENCHANTED_WHEAT", 1_000L), Map.of());
		String amount = FormatUtil.shortCoins(1_000_000D);
		assertEquals(amount + " coins", CropsPage.costToMaxLabel(total, PRICE, WIDTH, (amount + " coins").length()));
		assertEquals(amount, CropsPage.costToMaxLabel(total, PRICE, WIDTH, 2));
	}

	@Test
	public void maxedWhenNothingLeft() {
		assertEquals("Maxed", CropsPage.costToMaxLabel(remaining(0L, Map.of(), Map.of()), PRICE, WIDTH, 100));
	}

	@Test
	public void maxedUpgradesAddNothingToTheRemainder() {
		assertTrue(GardenCosts.hasComposterCosts("speed"));
		assertTrue(GardenCosts.composterRemaining(Map.of("speed", 25, "multi_drop", 25)).none());

		var oneLeft = GardenCosts.composterRemaining("speed", 24);
		assertEquals(4_000L, oneLeft.copper());
		assertEquals(Map.of("ENCHANTED_HAY_BALE", 128L), oneLeft.crops());
		assertEquals(Map.of("CONDENSED_FERMENTO", 8L), oneLeft.rare());

		var mixed = GardenCosts.composterRemaining(Map.of("speed", 24, "multi_drop", 25));
		assertEquals(oneLeft.copper(), mixed.copper());
		assertEquals(oneLeft.crops(), mixed.crops());
	}
}
