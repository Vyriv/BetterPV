package dev.vy.betterpv.client.networth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import org.junit.Test;

public final class PetWorthLevelTest {
	private static final double LEGENDARY_100 = 25_353_230;
	private static final double PER_LEVEL_AFTER_100 = 1_886_700;

	private static PetWorth.LevelInfo level(String type, String tier, double exp) {
		JsonObject pet = new JsonObject();
		pet.addProperty("type", type);
		pet.addProperty("tier", tier);
		pet.addProperty("exp", exp);
		return PetWorth.levelInfo(pet);
	}

	@Test
	public void normalLegendaryCapsAt100() {
		assertEquals(99, level("ENDER_DRAGON", "LEGENDARY", LEGENDARY_100 - 1).level());
		assertEquals(100, level("ENDER_DRAGON", "LEGENDARY", LEGENDARY_100).level());
		assertEquals(100, level("ENDER_DRAGON", "LEGENDARY", LEGENDARY_100 * 10).level());
		assertEquals(100, level("ENDER_DRAGON", "LEGENDARY", 0).maxLevel());
	}

	@Test
	public void goldenDragonCostsTheSameEveryLevelAfter100() {
		assertEquals(200, level("GOLDEN_DRAGON", "LEGENDARY", 0).maxLevel());
		assertEquals(100, level("GOLDEN_DRAGON", "LEGENDARY", LEGENDARY_100).level());
		assertEquals(100, level("GOLDEN_DRAGON", "LEGENDARY", LEGENDARY_100 + PER_LEVEL_AFTER_100 - 1).level());
		assertEquals(101, level("GOLDEN_DRAGON", "LEGENDARY", LEGENDARY_100 + PER_LEVEL_AFTER_100).level());
		assertEquals(129, level("GOLDEN_DRAGON", "LEGENDARY", LEGENDARY_100 + 29 * PER_LEVEL_AFTER_100).level());
		assertEquals(199, level("GOLDEN_DRAGON", "LEGENDARY", LEGENDARY_100 + 100 * PER_LEVEL_AFTER_100 - 1).level());
		assertEquals(200, level("GOLDEN_DRAGON", "LEGENDARY", LEGENDARY_100 + 100 * PER_LEVEL_AFTER_100).level());
		assertEquals(LEGENDARY_100 + 100 * PER_LEVEL_AFTER_100, level("GOLDEN_DRAGON", "LEGENDARY", 0).xpMax(), 0.5);
	}

	@Test
	public void roseAndJadeDragonsHaveNoGoldenDragonEggRows() {
		for (String type : new String[] { "ROSE_DRAGON", "JADE_DRAGON" }) {
			assertEquals(200, level(type, "LEGENDARY", 0).maxLevel());
			assertEquals(100, level(type, "LEGENDARY", LEGENDARY_100 + 5_555).level());
			assertEquals(101, level(type, "LEGENDARY", LEGENDARY_100 + PER_LEVEL_AFTER_100).level());
			assertEquals(200, level(type, "LEGENDARY", 214_023_230).level());
			assertEquals(199, level(type, "LEGENDARY", 214_023_229).level());
		}
	}

	@Test
	public void specialRarityUsesItsOwnOffset() {
		for (double exp : new double[] { 0, 10_000, 1_000_000, 18_000_000 }) {
			assertEquals(level("PHOENIX", "EPIC", exp).level(), level("PHOENIX", "SPECIAL", exp).level());
			assertEquals(level("PHOENIX", "LEGENDARY", exp).level(), level("PHOENIX", "VERY_SPECIAL", exp).level());
		}
		// Without a SPECIAL offset the COMMON table was used, which reads far higher.
		assertEquals(level("PHOENIX", "EPIC", 1_000_000).level(), level("PHOENIX", "SPECIAL", 1_000_000).level());
		assertTrue(level("PHOENIX", "COMMON", 1_000_000).level() > level("PHOENIX", "SPECIAL", 1_000_000).level());
	}
}
