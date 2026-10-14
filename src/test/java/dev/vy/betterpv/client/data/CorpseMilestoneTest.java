package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CorpseMilestoneTest {
	private static MiningSnapshot.CorpseMilestone tier(int n) {
		return MiningSnapshot.CORPSE_MILESTONES.get(n - 1);
	}

	@Test
	public void tungstenMatchesUmberThresholds() {
		for (MiningSnapshot.CorpseMilestone m : MiningSnapshot.CORPSE_MILESTONES) {
			assertEquals("tier " + m.tier(), m.needUmber(), m.needTungsten());
		}
		assertEquals(100L, tier(7).needTungsten());
	}

	@Test
	public void missingTungstenBlocksMilestone() {
		MiningSnapshot.CorpseCounts noTungsten = new MiningSnapshot.CorpseCounts(1000, 100, 0, 20);
		assertFalse(tier(2).met(noTungsten));
		assertEquals(0f, tier(2).fill(noTungsten, tier(1)), 0.0001f);

		MiningSnapshot.CorpseCounts full = new MiningSnapshot.CorpseCounts(1000, 100, 100, 20);
		assertTrue(tier(7).met(full));
	}
}
