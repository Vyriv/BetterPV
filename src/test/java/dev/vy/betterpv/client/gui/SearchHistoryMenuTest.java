package dev.vy.betterpv.client.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.api.SearchHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class SearchHistoryMenuTest {
	private static final int LINE = 9;
	private static final int BOX_X = 400;
	private static final int BOX_Y = 200;
	private static final int BOX_W = 110;
	private static final int BOX_H = 12;

	private final List<String> opened = new ArrayList<>();
	private SearchHistory history = SearchHistory.empty()
		.record(UUID.randomUUID(), "Notch")
		.record(UUID.randomUUID(), "jeb_")
		.record(UUID.randomUUID(), "Dinnerbone");

	private SearchHistoryMenu menu() {
		SearchHistoryMenu menu = new SearchHistoryMenu(() -> this.history, this.opened::add);
		menu.setAnchor(BOX_X, BOX_Y, BOX_W, BOX_H);
		menu.setViewport(0, 0, 640, 480);
		return menu;
	}

	private static SearchHistoryMenu.Layout focusAndLayout(SearchHistoryMenu menu, String query) {
		menu.syncSearch(true, query);
		menu.layoutDropdown(LINE);
		return menu.layout();
	}

	private static double rowCenterY(SearchHistoryMenu.Layout layout, int row) {
		return layout.rowY(row) + layout.rowH() / 2.0;
	}

	@Test
	public void focusOpensUnderTheSearchBoxAtTheSameWidth() {
		SearchHistoryMenu menu = menu();
		assertFalse(menu.isVisible());
		assertTrue(menu.syncSearch(true, ""));
		menu.layoutDropdown(LINE);
		SearchHistoryMenu.Layout layout = menu.layout();
		assertTrue(menu.isVisible());
		assertEquals(BOX_X, layout.x());
		assertEquals(BOX_W, layout.w());
		assertEquals(BOX_Y + BOX_H + 1, layout.y());
		assertEquals(3, layout.rows());
	}

	@Test
	public void showsNewestFirstAndAtMostThreeRows() {
		this.history = this.history.record(UUID.randomUUID(), "Grumm");
		SearchHistoryMenu menu = menu();
		focusAndLayout(menu, "");
		assertEquals(List.of("Grumm", "Dinnerbone", "jeb_"),
			menu.entries().stream().map(SearchHistory.Entry::name).toList());
		assertEquals(3, menu.layout().rows());
	}

	@Test
	public void emptyHistoryNeverShowsOrBlocks() {
		this.history = SearchHistory.empty();
		SearchHistoryMenu menu = menu();
		assertFalse(menu.syncSearch(true, ""));
		menu.layoutDropdown(LINE);
		assertTrue(menu.isOpen());
		assertFalse(menu.isVisible());
		assertNull(menu.layout());
		assertFalse(menu.blocksMouse(BOX_X + 5, BOX_Y + BOX_H + 5));
		assertFalse(menu.mouseClicked(BOX_X + 5, BOX_Y + BOX_H + 5, 0));
		assertFalse(menu.keyPressed(256));
	}

	@Test
	public void typingFiltersRowsAndHidesWhenNothingMatches() {
		SearchHistoryMenu menu = menu();
		focusAndLayout(menu, "");
		focusAndLayout(menu, "NOT");
		assertEquals(List.of("Notch"), menu.entries().stream().map(SearchHistory.Entry::name).toList());
		assertEquals(1, menu.layout().rows());
		focusAndLayout(menu, "zzz");
		assertFalse(menu.isVisible());
		assertTrue(focusAndLayout(menu, "e") != null);
		assertEquals(2, menu.entries().size());
	}

	@Test
	public void typingReopensAfterEscape() {
		SearchHistoryMenu menu = menu();
		focusAndLayout(menu, "");
		assertTrue(menu.keyPressed(256));
		assertFalse(menu.isVisible());
		menu.syncSearch(true, "");
		assertFalse(menu.isVisible());
		menu.syncSearch(true, "j");
		assertTrue(menu.isVisible());
	}

	@Test
	public void losingFocusCloses() {
		SearchHistoryMenu menu = menu();
		focusAndLayout(menu, "");
		menu.syncSearch(false, "");
		assertFalse(menu.isVisible());
	}

	@Test
	public void clickingARowOpensThatPlayerAndCloses() {
		SearchHistoryMenu menu = menu();
		SearchHistoryMenu.Layout layout = focusAndLayout(menu, "");
		assertTrue(menu.mouseClicked(layout.x() + 20, rowCenterY(layout, 1), 0));
		assertEquals(List.of("jeb_"), this.opened);
		assertFalse(menu.isOpen());
	}

	@Test
	public void clickingAFilteredRowOpensTheFilteredPlayer() {
		SearchHistoryMenu menu = menu();
		SearchHistoryMenu.Layout layout = focusAndLayout(menu, "dinner");
		assertTrue(menu.mouseClicked(layout.x() + 20, rowCenterY(layout, 0), 0));
		assertEquals(List.of("Dinnerbone"), this.opened);
	}

	@Test
	public void rightClickOnRowIsConsumedButOpensNothing() {
		SearchHistoryMenu menu = menu();
		SearchHistoryMenu.Layout layout = focusAndLayout(menu, "");
		assertTrue(menu.mouseClicked(layout.x() + 20, rowCenterY(layout, 0), 1));
		assertTrue(this.opened.isEmpty());
		assertTrue(menu.isVisible());
	}

	@Test
	public void outsideClickClosesAndPassesThrough() {
		SearchHistoryMenu menu = menu();
		focusAndLayout(menu, "");
		assertFalse(menu.mouseClicked(10, 10, 0));
		assertFalse(menu.isOpen());
		assertTrue(this.opened.isEmpty());
	}

	@Test
	public void clickInSearchBoxKeepsItOpenForTyping() {
		SearchHistoryMenu menu = menu();
		focusAndLayout(menu, "");
		assertFalse(menu.mouseClicked(BOX_X + 10, BOX_Y + 4, 0));
		assertTrue(menu.isVisible());
		assertTrue(menu.anchorContains(BOX_X + 10, BOX_Y + 4));
	}

	@Test
	public void dropdownOwnsMouseAndScroll() {
		SearchHistoryMenu menu = menu();
		SearchHistoryMenu.Layout layout = focusAndLayout(menu, "");
		double inX = layout.x() + 2;
		double inY = layout.y() + 1;
		assertTrue(menu.blocksMouse(inX, inY));
		assertTrue(menu.mouseScrolled(inX, inY));
		assertTrue(menu.mouseClicked(inX, inY, 0));
		assertTrue(this.opened.isEmpty());
		assertFalse(menu.blocksMouse(10, 10));
		assertFalse(menu.mouseScrolled(10, 10));
	}

	@Test
	public void flipsAboveAndStaysOnScreen() {
		SearchHistoryMenu.Layout below = SearchHistoryMenu.dropdownLayout(400, 460, 110, 12, 3, LINE, 0, 0, 640, 480);
		assertEquals(460 - 1 - below.h(), below.y());

		SearchHistoryMenu.Layout right = SearchHistoryMenu.dropdownLayout(600, 100, 110, 12, 3, LINE, 0, 0, 640, 480);
		assertEquals(640 - SearchHistoryMenu.EDGE - 110, right.x());

		SearchHistoryMenu.Layout cramped = SearchHistoryMenu.dropdownLayout(10, 5, 110, 12, 3, LINE, 0, 0, 640, 30);
		assertTrue(cramped.y() >= SearchHistoryMenu.EDGE);
	}
}
