package dev.vy.betterpv.client.gui.dungeons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.gui.PvTooltip;
import org.junit.Test;

public class CroesusRewardLineTest {
	@Test
	public void ultimateEnchantsAreBoldWithFullName() {
		PvTooltip.Line line = DungeonChestsPage.rewardLine("Ultimate Wise I", "ULTIMATE");
		PvTooltip.Span name = line.spans().get(1);
		assertEquals("Ultimate Wise I", name.text());
		assertTrue(name.bold());
		assertEquals(0xFFFF55FF, name.color());
		assertFalse(line.spans().get(0).bold());
	}

	@Test
	public void regularBooksAndItemsStayNormal() {
		PvTooltip.Line book = DungeonChestsPage.rewardLine("Feather Falling VI", "BOOK");
		assertFalse(book.spans().get(1).bold());
		assertEquals(0xFF5555FF, book.spans().get(1).color());
		PvTooltip.Line dye = DungeonChestsPage.rewardLine("Necron Dye", "EPIC");
		assertFalse(dye.spans().get(1).bold());
		assertEquals(0xFFAA00AA, dye.spans().get(1).color());
	}
}
