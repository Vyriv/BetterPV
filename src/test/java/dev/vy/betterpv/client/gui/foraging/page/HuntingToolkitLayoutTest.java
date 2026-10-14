package dev.vy.betterpv.client.gui.foraging.page;

import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SLOT;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SLOT_GAP;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.STAT_ROW;
import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class HuntingToolkitLayoutTest {
	private static final int LH = 9;
	private static final int HEADER = LH + 3 + STAT_ROW + 3;

	@Test
	public void emptyToolkitReservesNoSlotsLine() {
		assertEquals(HEADER + LH, HuntingPage.toolkitHeight(LH, List.of(), 5));
	}

	@Test
	public void singleGroupWrapsByColumns() {
		List<String> six = List.of("trap", "trap", "trap", "trap", "trap", "trap");
		assertEquals(HEADER + LH + 2 + (SLOT + SLOT_GAP) + SLOT, HuntingPage.toolkitHeight(LH, six, 5));
		assertEquals(HEADER + LH + 2 + SLOT, HuntingPage.toolkitHeight(LH, six, 6));
	}

	@Test
	public void eachGroupStartsANewRowWithItsLabel() {
		List<String> groups = List.of("trap", "trap", "lure");
		int expected = HEADER + (LH + 2) + (SLOT + SLOT_GAP + 2) + (LH + 2) + SLOT;
		assertEquals(expected, HuntingPage.toolkitHeight(LH, groups, 5));
	}

	@Test
	public void columnsStayBetweenThreeAndFive() {
		assertEquals(3, HuntingPage.toolkitCols(10));
		assertEquals(5, HuntingPage.toolkitCols(400));
	}
}
