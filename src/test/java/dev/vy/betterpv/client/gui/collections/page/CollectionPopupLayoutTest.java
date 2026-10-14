package dev.vy.betterpv.client.gui.collections.page;

import static dev.vy.betterpv.client.gui.collections.page.CollectionPopupLayout.COL_GAP;
import static dev.vy.betterpv.client.gui.collections.page.CollectionPopupLayout.MARGIN;
import static dev.vy.betterpv.client.gui.collections.page.CollectionPopupLayout.PAD;
import static dev.vy.betterpv.client.gui.collections.page.CollectionPopupLayout.ROW_H;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CollectionPopupLayoutTest {
	private static final int LINE_H = 9;
	private static final CollectionPopupLayout.Content ONE_REWARD =
		new CollectionPopupLayout.Content(9, 1, true, 150, 220, 130, 80);
	private static final CollectionPopupLayout.Content BUSY =
		new CollectionPopupLayout.Content(11, 6, true, 150, 220, 170, 100);

	// Board sizes the Collections content rect takes across GUI scales and window sizes.
	private static final int[][] BOARDS = {
		{8, 64, 504, 240}, {8, 64, 504, 184}, {40, 80, 700, 420}, {0, 30, 300, 184}, {20, 50, 1200, 600},
	};

	@Test
	public void popupStaysInsideTheBoardWithMargins() {
		for (int[] b : BOARDS) {
			for (CollectionPopupLayout.Content c : new CollectionPopupLayout.Content[] {ONE_REWARD, BUSY}) {
				CollectionPopupLayout l = CollectionPopupLayout.compute(b[0], b[1], b[2], b[3], LINE_H, c);
				String at = b[2] + "x" + b[3];
				assertTrue(at, l.x() >= b[0] + MARGIN);
				assertTrue(at, l.y() >= b[1] + MARGIN);
				assertTrue(at, l.x() + l.w() <= b[0] + b[2] - MARGIN);
				assertTrue(at, l.y() + l.h() <= b[1] + b[3] - MARGIN);
				assertEquals("centred", l.x() - b[0], b[0] + b[2] - (l.x() + l.w()), 1);
			}
		}
	}

	@Test
	public void sectionsStackInOrderInsideThePanel() {
		for (int[] b : BOARDS) {
			CollectionPopupLayout l = CollectionPopupLayout.compute(b[0], b[1], b[2], b[3], LINE_H, BUSY);
			assertTrue(l.headerY() >= l.y() + PAD);
			assertTrue(l.statsY() >= l.headerY() + CollectionPopupLayout.HEADER_H);
			assertTrue(l.barY() > l.statsY());
			assertTrue(l.trackY() > l.barY());
			if (l.hasTiersHeader()) {
				assertTrue(l.tiersY() > l.barY() && l.trackY() > l.tiersY());
			}
			assertTrue(l.selectedY() >= l.trackY() + 20 + 2 + LINE_H);
			assertTrue(l.selectedBarY() > l.selectedY());
			assertTrue(l.columnsY() > l.selectedBarY());
			assertTrue(l.columnsBodyY(LINE_H) + ROW_H <= l.bottom());
			assertEquals(l.y() + l.h() - PAD, l.bottom());
		}
	}

	@Test
	public void recipeColumnSharesTheRewardsRowAndRightEdge() {
		CollectionPopupLayout l = CollectionPopupLayout.compute(8, 64, 504, 240, LINE_H, ONE_REWARD);
		assertEquals(l.innerX() + l.innerW(), l.recipeX() + l.recipeW());
		assertEquals(l.innerW(), l.rewardsW() + COL_GAP + l.recipeW());
		assertTrue(l.recipeW() >= CollectionPopupLayout.RECIPE_GRID_W);
	}

	@Test
	public void singleRewardTierLeavesNoEmptyBand() {
		CollectionPopupLayout l = CollectionPopupLayout.compute(8, 64, 504, 240, LINE_H, ONE_REWARD);
		assertEquals("body is exactly the recipe grid", CollectionPopupLayout.recipeBodyH(LINE_H, true),
			l.bottom() - l.columnsBodyY(LINE_H));
		assertTrue(l.h() < 240 - MARGIN * 2);

		CollectionPopupLayout plain = CollectionPopupLayout.compute(8, 64, 504, 240, LINE_H,
			new CollectionPopupLayout.Content(5, 1, false, 120, 200, 140, 0));
		assertEquals("no recipe grid reserved when nothing has one", ROW_H, plain.bottom() - plain.columnsBodyY(LINE_H));
	}

	@Test
	public void busyCollectionsTightenThenScrollRatherThanOverflow() {
		CollectionPopupLayout roomy = CollectionPopupLayout.compute(40, 80, 700, 420, LINE_H, BUSY);
		assertTrue(roomy.hasTiersHeader());
		assertEquals(6 * ROW_H, roomy.bottom() - roomy.columnsBodyY(LINE_H));

		CollectionPopupLayout small = CollectionPopupLayout.compute(8, 64, 504, 184, LINE_H, BUSY);
		assertFalse("drops the Tiers header first", small.hasTiersHeader());
		assertEquals(184 - MARGIN * 2, small.h());
	}

	@Test
	public void widthFollowsContentWithinLimits() {
		CollectionPopupLayout narrow = CollectionPopupLayout.compute(0, 0, 1200, 600, LINE_H,
			new CollectionPopupLayout.Content(3, 1, true, 100, 150, 100, 60));
		assertEquals(CollectionPopupLayout.MIN_INNER_W + PAD * 2, narrow.w());

		CollectionPopupLayout wide = CollectionPopupLayout.compute(0, 0, 1200, 600, LINE_H,
			new CollectionPopupLayout.Content(11, 2, true, 300, 380, 400, 200));
		assertEquals(CollectionPopupLayout.MAX_W, wide.w());
		assertEquals(CollectionPopupLayout.RECIPE_MAX_W, wide.recipeW());

		CollectionPopupLayout fitted = CollectionPopupLayout.compute(0, 0, 1200, 600, LINE_H,
			new CollectionPopupLayout.Content(9, 2, true, 150, 220, 200, 120));
		assertEquals("recipe title gets its full width", 120, fitted.recipeW());
		assertEquals(200 + COL_GAP + 120 + PAD * 2, fitted.w());
	}

	@Test
	public void longNextTierTextMovesToItsOwnLine() {
		CollectionPopupLayout inline = CollectionPopupLayout.compute(8, 64, 504, 240, LINE_H, ONE_REWARD);
		assertTrue(inline.nextInline());
		CollectionPopupLayout wrapped = CollectionPopupLayout.compute(0, 0, 300, 240, LINE_H,
			new CollectionPopupLayout.Content(5, 1, true, 120, 400, 120, 60));
		assertFalse(wrapped.nextInline());
		assertTrue(wrapped.nextY() > wrapped.barY());
	}

	@Test
	public void singlePageTracksSpreadAcrossTheWidth() {
		assertEquals(25, CollectionDetailView.trackGap(9, 384));
		assertEquals("capped so short tracks stay together", 40, CollectionDetailView.trackGap(3, 384));
		assertEquals(6, CollectionDetailView.trackGap(1, 384));
		assertEquals(6, CollectionDetailView.trackGap(12, 200));
	}
}
