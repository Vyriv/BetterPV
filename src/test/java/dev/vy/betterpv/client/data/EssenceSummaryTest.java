package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class EssenceSummaryTest {
	@Test
	public void emptyShopIsNotLoaded() {
		EssenceSummary s = EssenceSummary.of(DungeonSnapshot.EssenceShop.empty("wither", "Wither"), id -> null);
		assertFalse(s.loaded());
		assertFalse(s.complete());
	}

	@Test
	public void sumsRemainingTiersAndCountsUnknownPerks() {
		DungeonSnapshot.EssenceShop shop = new DungeonSnapshot.EssenceShop("wither", "Wither", 500L, "ESSENCE_WITHER", List.of(
			new DungeonSnapshot.EssencePerk("a", "A", 1, 3),
			new DungeonSnapshot.EssencePerk("b", "B", 2, 2),
			new DungeonSnapshot.EssencePerk("c", "C", 0, 2),
			new DungeonSnapshot.EssencePerk("d", "D", 0, 4)
		));
		Map<String, int[]> costs = Map.of(
			"a", new int[] { 10, 20, 30 },
			"b", new int[] { 5, 5 },
			"d", new int[] { 1, 2 }
		);
		EssenceSummary s = EssenceSummary.of(shop, costs::get);
		assertTrue(s.loaded());
		assertEquals(1, s.perksMaxed());
		assertEquals(4, s.perkCount());
		assertEquals(3, s.levels());
		assertEquals(11, s.maxLevels());
		assertEquals(50L, s.toMax());
		// c has no costs, d's cost list is shorter than its max level.
		assertEquals(2, s.unknownCostPerks());
		assertFalse(s.complete());
	}

	@Test
	public void allMaxedIsComplete() {
		DungeonSnapshot.EssenceShop shop = new DungeonSnapshot.EssenceShop("ice", "Ice", 0L, "ESSENCE_ICE", List.of(
			new DungeonSnapshot.EssencePerk("x", "X", 5, 5)
		));
		EssenceSummary s = EssenceSummary.of(shop, id -> null);
		assertTrue(s.complete());
		assertEquals(0L, s.toMax());
		assertEquals(0, s.unknownCostPerks());
	}
}
