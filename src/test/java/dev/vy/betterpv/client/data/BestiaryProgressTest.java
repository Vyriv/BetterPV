package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class BestiaryProgressTest {
	private static final List<Integer> LADDER = List.of(10, 25, 50, 100, 250, 500);

	private static BestiaryData.Family family(int cap) {
		return new BestiaryData.Family("zombie", "Zombie", "hub", 1, "", cap, List.of("zombie_1"), "", "");
	}

	@Test
	public void killsPastCapStayVisible() {
		BestiarySnapshot.FamilyProgress p = BestiarySnapshot.progressFor(family(100), 4321L, 3L, LADDER);
		assertTrue(p.maxed());
		assertEquals(4, p.tier());
		assertEquals(4, p.tiersMax());
		assertEquals(4321L, p.kills());
	}

	@Test
	public void progressBelowCapUnchanged() {
		BestiarySnapshot.FamilyProgress p = BestiarySnapshot.progressFor(family(100), 30L, 0L, LADDER);
		assertFalse(p.maxed());
		assertEquals(2, p.tier());
		assertEquals(5L, p.intoTier());
		assertEquals(25L, p.nextNeed());
		assertEquals(30L, p.kills());
	}
}
