package dev.vy.betterpv.client.gui.mining.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HotmTreeLayoutTest {
	private static final int COLS = 7;
	private static final int ROWS = 10;

	@Test
	public void roomyPanelKeepsIntegerZoom() {
		HotmPage.TreeLayout layout = HotmPage.treeLayout(COLS, ROWS, 400, 480);

		assertEquals(40, layout.cell());
		assertEquals(4, layout.gap());
		assertEquals(2f, layout.iconScale(), 0f);
		assertEquals(0, layout.maxScroll());
	}

	@Test
	public void everyNodeReachableAcrossPanelSizes() {
		for (int innerW = 120; innerW <= 500; innerW += 7) {
			for (int innerH = 80; innerH <= 500; innerH += 5) {
				HotmPage.TreeLayout layout = HotmPage.treeLayout(COLS, ROWS, innerW, innerH);
				String at = innerW + "x" + innerH;
				assertTrue("width fits at " + at, layout.gridW() <= innerW);
				assertTrue("last row reachable at " + at, layout.gridH() <= innerH + layout.maxScroll());
				assertEquals("scroll only when needed at " + at,
					Math.max(0, layout.gridH() - innerH), layout.maxScroll());
				assertTrue("icon fits its cell at " + at, Math.round(16 * layout.iconScale()) <= layout.cell());
			}
		}
	}

	@Test
	public void shortPanelTightensBeforeScrolling() {
		// 10 rows at 20px + 2px gaps = 218px; tight cells are 189px.
		HotmPage.TreeLayout layout = HotmPage.treeLayout(COLS, ROWS, 200, 195);

		assertEquals(18, layout.cell());
		assertEquals(0, layout.maxScroll());
	}

	@Test
	public void veryShortPanelScrollsInsteadOfClipping() {
		HotmPage.TreeLayout layout = HotmPage.treeLayout(COLS, ROWS, 200, 90);

		assertTrue(layout.maxScroll() > 0);
		assertTrue(layout.cell() >= 12);
	}
}
