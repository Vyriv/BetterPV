package dev.vy.betterpv.client.gui.inventories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.networth.ItemWorth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.junit.Test;

public class SackValueTest {
	private static final Map<String, Double> PRICES = Map.of("COBBLESTONE", 2.0, "ICE", 5.0, "NETHERRACK", 1.0);
	private static final ToDoubleFunction<String> PRICE = id -> PRICES.getOrDefault(id, 0.0);
	private static final Function<String, String> NAME = id -> id.charAt(0) + id.substring(1).toLowerCase();

	private static InventorySnapshot.Slot slot(String id, int count) {
		return new InventorySnapshot.Slot(id, count, List.of(), null, null, null, null);
	}

	private static InventorySnapshot.Page page(String title, InventorySnapshot.Slot... slots) {
		return new InventorySnapshot.Page(title, List.of(slots), 9);
	}

	@Test
	public void sumsStoredQuantitiesTimesUnitPrice() {
		List<SackValue> values = SackValue.compute(List.of(
			page("Mining", slot("COBBLESTONE", 100), slot("ICE", 10))
		), PRICE, NAME);

		SackValue mining = values.get(0);
		assertTrue(mining.known());
		assertEquals(250.0, mining.total(), 0.001);
		assertEquals("highest subtotal first", "COBBLESTONE", mining.priced().get(0).id());
	}

	@Test
	public void sharedItemIsOnlyValuedInFirstSack() {
		List<SackValue> values = SackValue.compute(List.of(
			page("Mining", slot("ICE", 10)),
			page("Winter", slot("ICE", 10))
		), PRICE, NAME);

		assertEquals(50.0, values.get(0).total(), 0.001);
		assertFalse("ice already counted in Mining", values.get(1).known());
		assertEquals("Mining", values.get(1).shared().get(0).countedIn());
	}

	@Test
	public void missingPricesAreListedNotTreatedAsZero() {
		List<SackValue> values = SackValue.compute(List.of(
			page("Nether", slot("NETHERRACK", 30), slot("MYSTERY", 4))
		), PRICE, NAME);

		SackValue nether = values.get(0);
		assertEquals(30.0, nether.total(), 0.001);
		assertEquals(1, nether.unpriced().size());
		assertEquals("MYSTERY", nether.unpriced().get(0).id());
	}

	@Test
	public void sackWithNoPricedContentsIsUnknown() {
		List<SackValue> values = SackValue.compute(List.of(
			page("Rune", slot("RUNE_ZAP_1", 2)),
			page("Empty", slot("COBBLESTONE", 0))
		), PRICE, NAME);

		assertFalse(values.get(0).known());
		assertFalse(values.get(1).known());
		assertTrue(values.get(1).unpriced().isEmpty());
	}

	@Test
	public void breakdownHasQuantityPriceAndSubtotalRows() {
		SackValue value = SackValue.compute(List.of(
			page("Mining", slot("COBBLESTONE", 1200), slot("MYSTERY", 1))
		), PRICE, NAME).get(0);

		ItemWorth.Breakdown breakdown = value.breakdown();
		assertEquals(2400.0, breakdown.total(), 0.001);
		ItemWorth.Part row = breakdown.parts().get(0);
		assertEquals(ItemWorth.Section.CONTENTS, row.section());
		assertEquals("Cobblestone", row.label());
		assertEquals("1,200 x 2", row.detail());
		assertEquals(2400.0, row.amount(), 0.001);
		ItemWorth.Part note = breakdown.parts().get(1);
		assertEquals(ItemWorth.Role.NOTE, note.role());
		assertEquals("Mystery x1: no price", note.label());
	}
}
