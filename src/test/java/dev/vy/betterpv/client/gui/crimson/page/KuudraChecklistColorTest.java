package dev.vy.betterpv.client.gui.crimson.page;

import static org.junit.Assert.assertEquals;

import dev.vy.betterpv.client.data.KuudraGearChecks.Check;
import dev.vy.betterpv.client.data.KuudraGearChecks.CheckState;
import dev.vy.betterpv.client.data.KuudraGearChecks.Status;
import dev.vy.betterpv.client.gui.PvDraw;
import org.junit.Test;

public class KuudraChecklistColorTest {
	@Test
	public void checkStatesMapToOneColourEach() {
		assertEquals(KuudraChecklistColumn.COLOR_OK, color(CheckState.OK, null, false));
		assertEquals(KuudraChecklistColumn.COLOR_UPGRADE, color(CheckState.UPGRADE, "VII", false));
		assertEquals(KuudraChecklistColumn.COLOR_MISSING, color(CheckState.MISSING, null, false));
		assertEquals(KuudraChecklistColumn.COLOR_WARN, color(CheckState.WARN, null, false));
		assertEquals(PvDraw.COLOR_MUTED, color(CheckState.UNKNOWN, null, false));
		assertEquals(KuudraChecklistColumn.COLOR_HIGHLIGHT,
			KuudraChecklistColumn.checkColor(new Check("Cubism", "VI", CheckState.OK, null, true), false));
	}

	@Test
	public void informationalRowsNeverShowRedOrYellow() {
		assertEquals(PvDraw.COLOR_MUTED, color(CheckState.MISSING, null, true));
		assertEquals(PvDraw.COLOR_MUTED, color(CheckState.UPGRADE, "VII", true));
		assertEquals(PvDraw.COLOR_MUTED, KuudraChecklistColumn.statusColor(Status.MISSING, true));
		assertEquals(KuudraChecklistColumn.COLOR_OK, KuudraChecklistColumn.statusColor(Status.COMPLETE, true));
	}

	@Test
	public void rowStatusesUseTheSamePalette() {
		assertEquals(KuudraChecklistColumn.COLOR_OK, KuudraChecklistColumn.statusColor(Status.COMPLETE, false));
		assertEquals(KuudraChecklistColumn.COLOR_UPGRADE, KuudraChecklistColumn.statusColor(Status.INCOMPLETE, false));
		assertEquals(KuudraChecklistColumn.COLOR_MISSING, KuudraChecklistColumn.statusColor(Status.MISSING, false));
		assertEquals(PvDraw.COLOR_MUTED, KuudraChecklistColumn.statusColor(Status.UNKNOWN, false));
	}

	private static int color(CheckState state, String upgradeTo, boolean info) {
		return KuudraChecklistColumn.checkColor(new Check("x", "y", state, upgradeTo, false), info);
	}
}
