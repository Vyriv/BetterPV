package dev.vy.betterpv.client.gui.crimson.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.Test;

public class KuudraPageLayoutTest {
	@Test
	public void tierGapShrinksSoAllTiersFitAboveButton() {
		assertEquals(6, KuudraPage.tierGap(400, 5));
		int tight = 5 * 24 + 5 * 2;
		int gap = KuudraPage.tierGap(tight, 5);
		assertEquals(2, gap);
		assertTrue(5 * (24 + gap) <= tight);
		assertEquals(0, KuudraPage.tierGap(100, 5));
	}

	@Test
	public void headerLegendShortensInsteadOfClipping() {
		ToIntFunction<String> width = s -> s.length() * 6;
		List<KuudraPage.LegendPart> full = KuudraPage.headerLegend(400, width);
		assertEquals("\u2714 Good   ! Upgrade   \u2718 Missing", text(full));
		assertEquals(KuudraChecklistColumn.COLOR_OK, full.get(0).color());
		assertEquals(KuudraChecklistColumn.COLOR_UPGRADE, full.get(2).color());
		assertEquals(KuudraChecklistColumn.COLOR_MISSING, full.get(4).color());

		for (int available : new int[] { 400, 170, 150, 90, 40, 20 }) {
			List<KuudraPage.LegendPart> parts = KuudraPage.headerLegend(available, width);
			int used = parts.stream().mapToInt(p -> width.applyAsInt(p.text())).sum();
			assertTrue(available + " -> " + used, used <= available);
		}
		assertEquals("\u2714 OK ! Upg \u2718 Miss", text(KuudraPage.headerLegend(130, width)));
		assertEquals("\u2714 ! \u2718", text(KuudraPage.headerLegend(40, width)));
		assertTrue(KuudraPage.headerLegend(20, width).isEmpty());
	}

	private static String text(List<KuudraPage.LegendPart> parts) {
		StringBuilder sb = new StringBuilder();
		parts.forEach(p -> sb.append(p.text()));
		return sb.toString();
	}
}
