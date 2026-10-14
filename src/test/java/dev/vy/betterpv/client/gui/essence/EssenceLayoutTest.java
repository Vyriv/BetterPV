package dev.vy.betterpv.client.gui.essence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class EssenceLayoutTest {
	private static final int LINE = 9;
	private static final List<Integer> GROUPS = List.of(5, 3, 3);

	@Test
	public void selectorWidthStaysBetweenMinimumAndCap() {
		assertEquals(EssenceLayout.SELECTOR_MIN_W, EssenceLayout.selectorWidth(508, 40));
		assertEquals(100 + EssenceLayout.PAD * 2, EssenceLayout.selectorWidth(508, 100));
		assertEquals(508 * 34 / 100, EssenceLayout.selectorWidth(508, 400));
		assertEquals(EssenceLayout.SELECTOR_MIN_W, EssenceLayout.selectorWidth(200, 400));
	}

	@Test
	public void detailFillsTheRestOfTheBody() {
		EssenceLayout.Rect body = new EssenceLayout.Rect(10, 20, 508, 260);
		EssenceLayout.Rect detail = EssenceLayout.detail(body, 112);
		assertEquals(10 + 112 + EssenceLayout.GAP, detail.x());
		assertEquals(body.right(), detail.right());
		assertEquals(body.h(), detail.h());
	}

	@Test
	public void tallSelectorUsesHeadersWithoutScrolling() {
		EssenceLayout.Selector s = EssenceLayout.selector(new EssenceLayout.Rect(0, 0, 112, 262), LINE, GROUPS);
		assertTrue(s.headers());
		assertEquals(EssenceLayout.SELECTOR_ROW_MAX, s.rowH());
		assertEquals(0, s.maxScroll());
		assertRowsInOrder(s);
		for (int g = 0, row = 0; g < GROUPS.size(); row += GROUPS.get(g), g++) {
			assertTrue("header overlaps first row", s.headerY()[g] + LINE + 3 <= s.rowY()[row]);
		}
	}

	@Test
	public void minimumBodyHeightFitsEveryShopWithoutScrolling() {
		EssenceLayout.Selector s = EssenceLayout.selector(new EssenceLayout.Rect(0, 0, 112, 220), LINE, GROUPS);
		assertEquals(EssenceLayout.SELECTOR_ROW, s.rowH());
		assertEquals(0, s.maxScroll());
		assertRowsInOrder(s);
	}

	@Test
	public void shortSelectorSwitchesToDividersAndScrolls() {
		EssenceLayout.Selector s = EssenceLayout.selector(new EssenceLayout.Rect(0, 0, 112, 150), LINE, GROUPS);
		assertFalse(s.headers());
		assertEquals(0, s.headerY().length);
		assertEquals(2, s.dividerY().length);
		assertTrue(s.rowH() >= EssenceLayout.SELECTOR_ROW_MIN);
		assertTrue(s.maxScroll() > 0);
		assertEquals(s.contentH() - s.list().h(), s.maxScroll());
		assertRowsInOrder(s);
	}

	@Test
	public void selectorListStaysInsidePanel() {
		EssenceLayout.Rect panel = new EssenceLayout.Rect(4, 8, 112, 240);
		EssenceLayout.Selector s = EssenceLayout.selector(panel, LINE, GROUPS);
		assertTrue(s.list().x() >= panel.x() && s.list().right() <= panel.right());
		assertTrue(s.list().y() >= panel.y() && s.list().bottom() <= panel.bottom());
		assertEquals(11, s.rowY().length);
	}

	@Test
	public void wideDetailUsesTwoColumnsWithoutScrolling() {
		EssenceLayout.Rect detail = new EssenceLayout.Rect(124, 0, 384, 262);
		EssenceLayout.Perks p = EssenceLayout.perks(detail, LINE, 12);
		assertEquals(2, p.cols());
		assertEquals(6, p.rows());
		assertEquals(EssenceLayout.PERK_ROW, p.rowH());
		assertEquals(0, p.maxScroll());
		assertEquals(1, p.col(6));
		assertEquals(0, p.row(6));
		assertEquals(5, p.row(5));
		assertTrue(p.list().right() <= detail.right() - EssenceLayout.PAD);
		assertTrue(p.list().bottom() <= detail.bottom() - EssenceLayout.PAD);
		assertTrue(p.list().y() >= EssenceLayout.perkListTop(detail, LINE));
		assertTrue(p.colW() * 2 + EssenceLayout.PERK_COL_GAP <= p.list().w());
	}

	@Test
	public void narrowDetailFallsBackToOneScrollingColumn() {
		EssenceLayout.Rect detail = new EssenceLayout.Rect(0, 0, 250, 262);
		EssenceLayout.Perks p = EssenceLayout.perks(detail, LINE, 12);
		assertEquals(1, p.cols());
		assertEquals(12, p.rows());
		assertEquals(EssenceLayout.PERK_ROW_MIN, p.rowH());
		assertTrue(p.maxScroll() > 0);
		assertEquals(p.contentH() - p.list().h(), p.maxScroll());
	}

	@Test
	public void emptyShopHasNoPerkRows() {
		EssenceLayout.Perks p = EssenceLayout.perks(new EssenceLayout.Rect(0, 0, 384, 262), LINE, 0);
		assertEquals(0, p.rows());
		assertEquals(0, p.maxScroll());
	}

	@Test
	public void clampScrollStaysInRange() {
		assertEquals(0, EssenceLayout.clampScroll(-5, 40));
		assertEquals(40, EssenceLayout.clampScroll(99, 40));
		assertEquals(12, EssenceLayout.clampScroll(12, 40));
		assertEquals(0, EssenceLayout.clampScroll(12, -3));
	}

	private static void assertRowsInOrder(EssenceLayout.Selector s) {
		for (int i = 1; i < s.rowY().length; i++) {
			assertTrue("rows overlap at " + i, s.rowY()[i - 1] + s.rowH() <= s.rowY()[i]);
		}
		assertEquals(s.contentH(), s.rowY()[s.rowY().length - 1] + s.rowH());
	}
}
