package dev.vy.betterpv.client.cosmetics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NameStylerCachePolicyTest {
	@Test
	public void admitsNormalTextAndRejectsOversizedText() {
		assertTrue(NameStyler.cacheable("x".repeat(4096)));
		assertFalse(NameStyler.cacheable("x".repeat(4097)));
	}
}
