package dev.vy.betterpv.client.gui.foraging.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.ForagingSnapshot;
import java.util.Map;
import org.junit.Test;

public class HotfWhisperTest {
	@Test
	public void whisperValueUsesThreeSignificantFigures() {
		ForagingSnapshot.WhisperPool forest = new ForagingSnapshot.WhisperPool(
			"forest", "Forest", 15_211_000L, 4_818_000L, 1, Map.of(1, 4_818_000L));
		assertEquals("10.4M / 15.2M", HotfPage.whisperValue(forest));
		ForagingSnapshot.WhisperPool desert = new ForagingSnapshot.WhisperPool(
			"desert", "Desert", 15_246_000L, 14_541_413L, 1, Map.of(1, 14_541_413L));
		assertEquals("705K / 15.2M", HotfPage.whisperValue(desert));
	}

	@Test
	public void stacksWhenLabelAndValueDoNotFit() {
		assertTrue(HotfPage.fitsOnOneLine(32, 70, 116));
		assertFalse(HotfPage.fitsOnOneLine(32, 95, 116));
	}
}
