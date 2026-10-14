package dev.vy.betterpv.client.gui.overview;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SummaryCardsTest {
	@Test
	public void columnsScaleWithWidth() {
		assertEquals(1, SummaryCards.columnsFor(100));
		assertEquals(2, SummaryCards.columnsFor(306));
		assertEquals(3, SummaryCards.columnsFor(462));
		assertEquals(3, SummaryCards.columnsFor(2000));
	}

	@Test
	public void cardsGoToShortestColumn() {
		assertArrayEquals(new int[] { 0, 1, 2, 1, 2 }, SummaryCards.assignColumns(new int[] { 100, 40, 60, 30, 20 }, 3));
		assertArrayEquals(new int[] { 0, 0, 0 }, SummaryCards.assignColumns(new int[] { 10, 10, 10 }, 1));
	}
}
