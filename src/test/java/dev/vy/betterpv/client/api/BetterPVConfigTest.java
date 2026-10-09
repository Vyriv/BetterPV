package dev.vy.betterpv.client.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BetterPVConfigTest {
	@Test
	public void clampsAndRoundsProfileViewerScale() {
		assertEquals(75, BetterPVConfig.normalizeProfileScale(20));
		assertEquals(75, BetterPVConfig.normalizeProfileScale(75));
		assertEquals(100, BetterPVConfig.normalizeProfileScale(100));
		assertEquals(125, BetterPVConfig.normalizeProfileScale(123));
		assertEquals(150, BetterPVConfig.normalizeProfileScale(150));
		assertEquals(150, BetterPVConfig.normalizeProfileScale(250));
	}
}
