package dev.vy.betterpv.client.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
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

	@Test
	public void dropsFinishedTrialKeysOnly() {
		JsonObject doc = new JsonObject();
		doc.addProperty("profileViewerScalePercent", 110);
		assertFalse(BetterPVConfig.dropRemovedTrialKeys(doc));
		doc.addProperty("trialFishingTrophies", false);
		doc.addProperty("trialSkillOverviews", true);
		assertTrue(BetterPVConfig.dropRemovedTrialKeys(doc));
		assertFalse(doc.has("trialFishingTrophies"));
		assertFalse(doc.has("trialSkillOverviews"));
		assertTrue(doc.has("profileViewerScalePercent"));
	}
}
