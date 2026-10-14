package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GardenOverflowTest {
	private static final long GARDEN_15_XP = 60_120L;

	@Test
	public void belowCapHasNoOverflow() {
		assertNull(GardenData.gardenOverflow(GARDEN_15_XP - 1));
		assertEquals(14, GardenData.gardenLevel(GARDEN_15_XP - 1).level());
	}

	@Test
	public void exactlyAtCapIsLevel15WithNoProgress() {
		GardenData.LevelProgress overflow = GardenData.gardenOverflow(GARDEN_15_XP);
		assertEquals(15, overflow.level());
		assertEquals(0L, overflow.intoLevel());
		assertEquals(GardenData.GARDEN_OVERFLOW_STEP, overflow.needForNext());
	}

	@Test
	public void overflowLevelsUseTenThousandXpSteps() {
		GardenData.LevelProgress overflow = GardenData.gardenOverflow(GARDEN_15_XP + 23_400);
		assertEquals(17, overflow.level());
		assertEquals(3_400L, overflow.intoLevel());
		assertEquals(0.34f, overflow.fill(), 0.0001f);
		assertFalse(overflow.maxed());
	}

	@Test
	public void hugeXpStaysExact() {
		// 2^31 + change, beyond float precision.
		long xp = GARDEN_15_XP + 2_147_490_000L;
		GardenData.LevelProgress overflow = GardenData.gardenOverflow(xp);
		assertEquals(15 + 214_749, overflow.level());
		assertEquals(0L, overflow.intoLevel());
	}

	@Test
	public void hoverKeepsOverflowXpVisible() {
		double xp = GARDEN_15_XP + 23_400;
		String hover = GardenSnapshot.gardenHover(GardenData.gardenLevel(xp), GardenData.gardenOverflow(xp), xp);
		assertTrue(hover, hover.startsWith("Garden 15 - MAX"));
		assertTrue(hover, hover.contains("Overflow Level\t17"));
		assertTrue(hover, hover.contains("Next Level\t3,400 / 10,000"));
		assertTrue(hover, hover.contains("Overflow XP\t23,400"));
		assertTrue(hover, hover.contains("Total XP\t83,520"));
	}

	@Test
	public void cropMilestoneOverflowPastIntRange() {
		long collected = 3_000_000_000L;
		GardenData.LevelProgress ms = GardenData.cropMilestone("WHEAT", collected);
		assertTrue(ms.maxed());
		assertTrue(ms.intoLevel() > Integer.MAX_VALUE);
		assertTrue(ms.intoLevel() < collected);
	}
}
