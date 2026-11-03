package dev.vy.betterpv.client.networth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class NetworthBreakdownTooltipTest {
	private static List<String> text(List<PvTooltip.Line> lines) {
		return lines.stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).reduce("", String::concat))
			.toList();
	}

	private static NetworthBreakdown sample(int inventoryItems) {
		List<NetworthBreakdown.ItemLine> items = new ArrayList<>();
		double inventory = 0;
		for (int i = 0; i < inventoryItems; i++) {
			double v = 1_000_000 - i * 10_000;
			items.add(new NetworthBreakdown.ItemLine("ITEM_" + i, "Item " + i, 1, v));
			inventory += v;
		}
		return new NetworthBreakdown(inventory + 1_000_000, List.of(
			new NetworthBreakdown.Line("inventory", inventory, items),
			new NetworthBreakdown.Line("purse", 1_000_000)
		), "");
	}

	@Test
	public void totalsAreUnchangedAndSharesShown() {
		NetworthBreakdown nw = sample(3);
		List<String> lines = text(nw.tooltipStyledLines(NetworthMode.NORMAL));
		assertEquals("Networth: 3,970,000", lines.get(0));
		assertEquals("Mode: Normal (cosmetics included, soulbound included)", lines.get(1));
		assertTrue(lines.contains("Inventory: 2,970,000 (74.8%)"));
		assertTrue(lines.contains("Purse: 1,000,000 (25.2%)"));
	}

	@Test
	public void noShiftItemRowsOrFooterHints() {
		List<String> lines = text(sample(3).tooltipStyledLines(NetworthMode.NORMAL));
		assertFalse(lines.stream().anyMatch(l -> l.startsWith("  Item")));
		assertFalse(lines.stream().anyMatch(l -> l.contains("Hold L-shift")));
		assertFalse(lines.stream().anyMatch(l -> l.startsWith("Prices:")));
	}

	@Test
	public void modeLineDescribesFilters() {
		assertEquals(
			"Mode: Unsoulbound Non-Cosmetic (cosmetics excluded, soulbound excluded)",
			NetworthBreakdown.modeLine(NetworthMode.UNSOULBOUND_NON_COSMETIC)
		);
	}
}
