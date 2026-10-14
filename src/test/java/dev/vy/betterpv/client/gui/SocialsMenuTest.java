package dev.vy.betterpv.client.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import org.junit.Test;

public class SocialsMenuTest {
	private static final ToIntFunction<String> WIDTH = s -> s.length() * 6;
	private static final int LINE = 9;
	private static final int LABEL_X = 100;
	private static final int LABEL_Y = 300;

	private final List<String> opened = new ArrayList<>();
	private SocialLinks.Target viewed = SocialLinksTest.FULL;

	private SocialsMenu menu() {
		SocialsMenu menu = new SocialsMenu(() -> this.viewed, this.opened::add);
		menu.setLabel(LABEL_X, LABEL_Y, 60, LINE);
		menu.setViewport(0, 0, 640, 480);
		return menu;
	}

	private static SocialsMenu.Layout openAndLayout(SocialsMenu menu) {
		assertTrue(menu.mouseClicked(LABEL_X + 1, LABEL_Y + 1, 0));
		menu.layoutPopup(WIDTH, LINE);
		return menu.layout();
	}

	private static double rowCenterY(SocialsMenu.Layout layout, int row) {
		return layout.rowY(row) + layout.rowH() / 2.0;
	}

	@Test
	public void labelTogglesPopup() {
		SocialsMenu menu = menu();
		assertFalse(menu.isOpen());
		assertTrue(menu.mouseClicked(LABEL_X + 5, LABEL_Y + 4, 0));
		assertTrue(menu.isOpen());
		assertTrue(menu.mouseClicked(LABEL_X + 5, LABEL_Y + 4, 0));
		assertFalse(menu.isOpen());
	}

	@Test
	public void rightClickOnLabelDoesNotToggle() {
		SocialsMenu menu = menu();
		assertTrue(menu.mouseClicked(LABEL_X + 5, LABEL_Y + 4, 1));
		assertFalse(menu.isOpen());
	}

	@Test
	public void closedMenuLetsOtherClicksThrough() {
		SocialsMenu menu = menu();
		assertFalse(menu.mouseClicked(10, 10, 0));
		assertFalse(menu.blocksMouse(LABEL_X + 5, LABEL_Y + 4));
		assertFalse(menu.mouseScrolled(LABEL_X + 5, LABEL_Y + 4));
	}

	@Test
	public void outsideClickClosesWithoutClickThrough() {
		SocialsMenu menu = menu();
		openAndLayout(menu);
		assertTrue(menu.mouseClicked(5, 5, 0));
		assertFalse(menu.isOpen());
		assertTrue(this.opened.isEmpty());
	}

	@Test
	public void clickOnPopupPaddingKeepsItOpen() {
		SocialsMenu menu = menu();
		SocialsMenu.Layout layout = openAndLayout(menu);
		assertTrue(menu.mouseClicked(layout.x() + 3, layout.y() + 1, 0));
		assertTrue(menu.isOpen());
		assertTrue(this.opened.isEmpty());
	}

	@Test
	public void rowsOpenViewedPlayerLinksInOrderAndClose() {
		String[] expected = {
			"https://plancke.io/hypixel/player/stats/" + SocialLinksTest.UNDASHED,
			"https://sky.shiiyu.moe/stats/" + SocialLinksTest.UNDASHED + "/Watermelon",
			"https://eliteskyblock.com/@" + SocialLinksTest.UNDASHED + "/3564b910b00c40698b6cf652a344a41e",
			"https://sky.coflnet.com/player/" + SocialLinksTest.UNDASHED,
			"https://namemc.com/profile/b876ec32-e396-476b-a115-8438d83c67d4"
		};
		for (int row = 0; row < expected.length; row++) {
			SocialsMenu menu = menu();
			SocialsMenu.Layout layout = openAndLayout(menu);
			assertEquals(row, menu.rowAt(layout.x() + 20, rowCenterY(layout, row)));
			assertTrue(menu.mouseClicked(layout.x() + 20, rowCenterY(layout, row), 0));
			assertFalse(menu.isOpen());
			assertEquals(expected[row], this.opened.get(this.opened.size() - 1));
		}
		assertEquals(expected.length, this.opened.size());
	}

	@Test
	public void linksFollowTheCurrentlyViewedPlayer() {
		SocialsMenu menu = menu();
		UUID other = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
		this.viewed = new SocialLinks.Target(other, "Notch", null, "Banana");
		SocialsMenu.Layout layout = openAndLayout(menu);
		menu.mouseClicked(layout.x() + 20, rowCenterY(layout, 3), 0);
		assertEquals("https://sky.coflnet.com/player/069a79f444e94726a5befca90e38aaf5", this.opened.get(0));
	}

	@Test
	public void unavailableRowDoesNothing() {
		SocialsMenu menu = menu();
		this.viewed = new SocialLinks.Target(null, "Technoblade", null, "");
		SocialsMenu.Layout layout = openAndLayout(menu);
		assertTrue(menu.mouseClicked(layout.x() + 20, rowCenterY(layout, 3), 0));
		assertTrue(menu.isOpen());
		assertTrue(this.opened.isEmpty());
	}

	@Test
	public void rightClickOnRowDoesNotOpen() {
		SocialsMenu menu = menu();
		SocialsMenu.Layout layout = openAndLayout(menu);
		assertTrue(menu.mouseClicked(layout.x() + 20, rowCenterY(layout, 0), 1));
		assertTrue(menu.isOpen());
		assertTrue(this.opened.isEmpty());
	}

	@Test
	public void escapeClosesOnlyWhenOpen() {
		SocialsMenu menu = menu();
		assertFalse(menu.keyPressed(256));
		openAndLayout(menu);
		assertFalse(menu.keyPressed(65));
		assertTrue(menu.isOpen());
		assertTrue(menu.keyPressed(256));
		assertFalse(menu.isOpen());
	}

	@Test
	public void openPopupBlocksMouseAndScroll() {
		SocialsMenu menu = menu();
		SocialsMenu.Layout layout = openAndLayout(menu);
		assertTrue(menu.blocksMouse(layout.x() + 5, layout.y() + 5));
		assertTrue(menu.mouseScrolled(layout.x() + 5, layout.y() + 5));
		assertFalse(menu.blocksMouse(5, 5));
	}

	@Test
	public void popupOpensBelowLabel() {
		SocialsMenu.Layout layout = SocialsMenu.popupLayout(LABEL_X, LABEL_Y, LINE, WIDTH, LINE, 0, 0, 640, 480);
		assertEquals(LABEL_X, layout.x());
		assertEquals(LABEL_Y + LINE + 2, layout.y());
	}

	@Test
	public void compactRowsWithoutDescriptions() {
		SocialsMenu.Layout layout = SocialsMenu.popupLayout(LABEL_X, LABEL_Y, LINE, WIDTH, LINE, 0, 0, 640, 480);
		assertEquals(LINE + 4, layout.rowH());
		assertEquals(5 * (LINE + 4) + 4, layout.h());
		assertEquals(layout.y() + 2, layout.rowY(0));
		assertEquals(layout.rowY(0) + layout.rowH(), layout.rowY(1));
	}

	@Test
	public void popupFlipsAboveWhenNoRoomBelow() {
		SocialsMenu.Layout layout = SocialsMenu.popupLayout(LABEL_X, 440, LINE, WIDTH, LINE, 0, 0, 640, 480);
		assertEquals(440 - 2 - layout.h(), layout.y());
		assertTrue(layout.y() + layout.h() <= 480);
	}

	@Test
	public void popupStaysInsideVisibleArea() {
		SocialsMenu.Layout right = SocialsMenu.popupLayout(600, 100, LINE, WIDTH, LINE, 0, 0, 640, 480);
		assertTrue(right.x() + right.w() <= 640 - SocialsMenu.EDGE);
		SocialsMenu.Layout offset = SocialsMenu.popupLayout(-40, 100, LINE, WIDTH, LINE, -20, -10, 700, 480);
		assertEquals(-20 + SocialsMenu.EDGE, offset.x());
		SocialsMenu.Layout tiny = SocialsMenu.popupLayout(10, 20, LINE, WIDTH, LINE, 0, 0, 640, 60);
		assertEquals(SocialsMenu.EDGE, tiny.y());
	}

	@Test
	public void popupFitsIconAndWidestNameOnly() {
		SocialsMenu.Layout layout = SocialsMenu.popupLayout(0, 0, LINE, WIDTH, LINE, 0, 0, 640, 480);
		int nameW = WIDTH.applyAsInt("SkyCrypt");
		assertEquals(nameW, layout.nameW());
		assertEquals(SocialsMenu.PAD + SocialsMenu.ICON + SocialsMenu.ICON_GAP + nameW + SocialsMenu.PAD, layout.w());
	}
}
