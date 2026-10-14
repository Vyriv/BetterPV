package dev.vy.betterpv.client.gui.crimson.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.KuudraGearChecks.Check;
import dev.vy.betterpv.client.data.KuudraGearChecks.CheckState;
import dev.vy.betterpv.client.data.KuudraGearChecks.Entry;
import dev.vy.betterpv.client.data.KuudraGearChecks.Status;
import dev.vy.betterpv.client.gui.crimson.page.KuudraChecklistColumn.Hover;
import java.util.List;
import org.junit.Test;

public class KuudraChecklistHoverTest {
	private static final int LX = 100;
	private static final int LW = 180;
	private static final int H = 20;
	private static final int CLIP_TOP = 50;
	private static final int CLIP_BOTTOM = 250;

	@Test
	public void onlyTheOwnedIconSlotOpensTheItemTooltip() {
		int ly = 80;
		int sx = KuudraChecklistColumn.slotX(LX);
		int sy = KuudraChecklistColumn.slotY(ly, H);
		assertEquals(Hover.ICON, hover(sx + 1, sy + 1, ly, true));
		assertEquals(Hover.ICON, hover(sx + KuudraChecklistColumn.SLOT_SIZE - 1, sy + KuudraChecklistColumn.SLOT_SIZE - 1, ly, true));
		assertEquals(Hover.ROW, hover(sx + KuudraChecklistColumn.SLOT_SIZE, sy + 5, ly, true));
		assertEquals(Hover.ROW, hover(LX + 60, ly + 10, ly, true));
		assertEquals(Hover.ROW, hover(LX + LW - 1, ly + 10, ly, true));
	}

	@Test
	public void missingItemsNeverOpenAnItemPreview() {
		int ly = 80;
		int sx = KuudraChecklistColumn.slotX(LX);
		int sy = KuudraChecklistColumn.slotY(ly, H);
		assertEquals(Hover.ROW, hover(sx + 4, sy + 4, ly, false));
		Entry missing = new Entry("Chestplate", "Not found", "INFERNAL_TERROR_CHESTPLATE", Status.MISSING, List.of(), null, null, null);
		assertTrue(!KuudraChecklistColumn.hasPreview(missing));
	}

	@Test
	public void hoverFollowsScrollAndClipping() {
		int offset = 40;
		int scrolled = KuudraChecklistColumn.lineTop(CLIP_TOP, 30, offset);
		assertEquals(CLIP_TOP + 12, scrolled);
		int sy = KuudraChecklistColumn.slotY(scrolled, H);
		int sx = KuudraChecklistColumn.slotX(LX);
		assertEquals(Hover.ICON, hover(sx + 2, sy + 2, scrolled, true));
		int unscrolled = KuudraChecklistColumn.lineTop(CLIP_TOP, 0, offset);
		assertEquals(Hover.NONE, hover(sx + 2, unscrolled + H + 2, scrolled, true));

		int partlyHidden = CLIP_TOP - 8;
		int hiddenSlotY = KuudraChecklistColumn.slotY(partlyHidden, H);
		assertEquals(Hover.NONE, hover(sx + 2, hiddenSlotY + 2, partlyHidden, true));
		assertEquals(Hover.ICON, hover(sx + 2, CLIP_TOP + 2, partlyHidden, true));

		int belowClip = CLIP_BOTTOM - 4;
		assertEquals(Hover.NONE, hover(sx + 2, CLIP_BOTTOM + 1, belowClip, true));
		int offScreen = KuudraChecklistColumn.lineTop(CLIP_TOP, 200, offset);
		assertEquals(Hover.NONE, hover(sx + 2, offScreen + 5, offScreen, true));
	}

	@Test
	public void rowNotesListOnlySpecificReasons() {
		Entry terminator = new Entry("Rend Terminator (1)", null, "TERMINATOR", Status.MISSING, List.of(
			new Check("Rend", "None", CheckState.MISSING, null, false),
			new Check("Power", "VI", CheckState.UPGRADE, "VII", false),
			new Check("Piercing", "I", CheckState.WARN, null, false),
			new Check("Cubism", "VI", CheckState.OK, null, false)
		), "Inventory", null, null);
		assertEquals(List.of("\u00a7cMissing: Rend None", "\u00a7eUpgrade: Power VI \u2192 VII", "\u00a76Warning: Piercing I"),
			KuudraChecklistColumn.rowTipLines(terminator));

		Entry crown = new Entry("Wardrobe slot 3", "Crown of Avarice in Terror set", "CROWN_OF_AVARICE", Status.INCOMPLETE,
			List.of(new Check("", "Warden Helmet preferred", CheckState.WARN, null, false)), "Wardrobe slot 3", null, null);
		assertEquals(List.of("\u00a76Warning: Warden Helmet preferred"), KuudraChecklistColumn.rowTipLines(crown));

		Entry fine = new Entry("Helmet", "Warden Helmet", "WARDEN_HELMET", Status.COMPLETE,
			List.of(new Check("Reforge", "Ancient", CheckState.INFO, null, false)), "Wardrobe", null, null);
		assertTrue(KuudraChecklistColumn.rowTipLines(fine).isEmpty());
	}

	private static Hover hover(int mx, int my, int ly, boolean preview) {
		return KuudraChecklistColumn.hoverAt(mx, my, LX, ly, LW, H, CLIP_TOP, CLIP_BOTTOM, preview);
	}
}
