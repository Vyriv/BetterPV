package dev.vy.betterpv.client.gui.garden.page;

import static org.junit.Assert.assertArrayEquals;

import dev.vy.betterpv.client.data.GardenSnapshot;
import dev.vy.betterpv.client.data.InventorySnapshot;
import java.util.List;
import org.junit.Test;

public final class FarmingToolkitCountTest {
	private static InventorySnapshot.Slot tool(String id) {
		return new InventorySnapshot.Slot(id, 1, List.of(), id, null, null, null);
	}

	@Test
	public void countsOnlyCropsHoldingTools() {
		List<GardenSnapshot.FarmingToolkitSlot> slots = List.of(
			new GardenSnapshot.FarmingToolkitSlot("WHEAT", "Wheat", 0, tool("THEORETICAL_HOE_WHEAT_3")),
			new GardenSnapshot.FarmingToolkitSlot("WHEAT", "Wheat", 1, tool("BASIC_GARDENING_HOE")),
			new GardenSnapshot.FarmingToolkitSlot("CARROT_ITEM", "Carrot", 0, null),
			new GardenSnapshot.FarmingToolkitSlot("POTATO_ITEM", "Potato", 0, tool("THEORETICAL_HOE_POTATO_2")),
			new GardenSnapshot.FarmingToolkitSlot("MELON", "Melon", 0, null)
		);

		assertArrayEquals(new int[] {2, 4, 3}, GardenOverviewPage.stockedCrops(slots));
	}

	@Test
	public void emptyToolkitIsZero() {
		assertArrayEquals(new int[] {0, 0, 0}, GardenOverviewPage.stockedCrops(List.of()));
	}
}
