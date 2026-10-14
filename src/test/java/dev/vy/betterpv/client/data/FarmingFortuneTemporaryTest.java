package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.client.data.FarmingFortuneEstimate.Garden;
import dev.vy.betterpv.client.data.FarmingFortuneTemporary.Buffs;
import dev.vy.betterpv.client.data.FarmingFortuneTemporary.Source;
import dev.vy.betterpv.client.data.FarmingFortuneTemporary.Status;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.Test;

public class FarmingFortuneTemporaryTest {
	private static final double EPS = 0.001D;
	private static final long EPOCH = 1_560_275_700_000L;
	private static final long HOUR = 60L * 60L * 1000L;
	// A year is 124 hours; Spring is the first 31.
	private static final long SUMMER = EPOCH + 100L * 124L * HOUR + 40L * HOUR;
	private static final long SPRING = EPOCH + 100L * 124L * HOUR + 2L * HOUR;
	private static final Garden GARDEN = new Garden(true, 24, 15, Map.of(), Map.of(), 0L);

	private static InventoryDecoder.Stack item(String id, String... lore) {
		return new InventoryDecoder.Stack(id, 1, null, List.of(lore), false);
	}

	private static FarmingFortuneEstimate est(Map<String, Integer> shards, Buffs buffs, Map<String, List<InventoryDecoder.Stack>> all) {
		var in = new FarmingFortuneEstimate.Inputs(true, true, true, List.of(), Set.of(), false, 60, 15, 0, 0, 0, 0,
			shards, false, Map.of(), List.of(), buffs);
		return FarmingFortuneEstimate.from(in, all);
	}

	private static Source find(List<Source> sources, String label) {
		return sources.stream().filter(s -> s.label().equals(label)).findFirst().orElseThrow(() -> new AssertionError(label));
	}

	private static boolean has(List<Source> sources, String label) {
		return sources.stream().anyMatch(s -> s.label().equals(label));
	}

	@Test
	public void readsPotionCakeAndChipsFromPlayerData() {
		JsonObject data = JsonParser.parseString("""
			{
			  "active_effects": [
			    {"effect": "speed", "level": 8, "ticks_remaining": 99999},
			    {"effect": "harvest_harbinger", "level": 5, "ticks_remaining": 18000, "infinite": false}
			  ],
			  "temp_stat_buffs": [
			    {"stat": 21, "key": "chocolate_century_cake_farming_fortune", "amount": 5, "expire_at": 1900000000000}
			  ],
			  "garden_chips": {"hypercharge": 15, "overdrive": {"level": 20}, "cropshot": 20}
			}
			""").getAsJsonObject();
		Buffs buffs = FarmingFortuneTemporary.buffs(data);
		assertTrue(buffs.known());
		assertEquals(18000L, buffs.harvestHarbingerTicks());
		assertEquals(1_900_000_000_000L, buffs.cakeExpiresAt());
		assertEquals(15, buffs.hypercharge());
		assertEquals(20, buffs.overdrive());

		assertFalse(FarmingFortuneTemporary.buffs(null).known());
		Buffs none = FarmingFortuneTemporary.buffs(new JsonObject());
		assertTrue("player data without buffs proves they're off", none.known());
		assertEquals(0L, none.harvestHarbingerTicks());
	}

	@Test
	public void cakeExpiryInSecondsIsNotReadAs1970() {
		JsonObject data = JsonParser.parseString("""
			{"temp_stat_buffs": [{"key": "farming_fortune_cake", "expire_at": 1900000000}]}
			""").getAsJsonObject();
		assertEquals(1_900_000_000_000L, FarmingFortuneTemporary.buffs(data).cakeExpiresAt());
	}

	@Test
	public void onlyBuffsProvenBySavedDataAreActive() {
		long now = 1_800_000_000_000L;
		var active = est(Map.of(), new Buffs(true, 18000L, now + 3L * HOUR, 0, 0), Map.of()).temporary(null, GARDEN, now);
		Source potion = find(active, "Harvest Harbinger Potion");
		assertEquals(Status.ACTIVE, potion.status());
		assertEquals(50D, potion.value(), EPS);
		assertEquals("18000 ticks", "15m left", potion.state());
		Source cake = find(active, "Chocolate Century Cake");
		assertEquals(Status.ACTIVE, cake.status());
		assertEquals(5D, cake.value(), EPS);
		assertEquals("3h left", cake.state());
		assertEquals(55D, FarmingFortuneTemporary.activeTotal(active), EPS);
		assertEquals("active sources are sorted first", Status.ACTIVE, active.get(0).status());

		var expired = est(Map.of(), new Buffs(true, 0L, now - 1L, 0, 0), Map.of()).temporary(null, GARDEN, now);
		assertEquals(Status.INACTIVE, find(expired, "Harvest Harbinger Potion").status());
		assertEquals(Status.INACTIVE, find(expired, "Chocolate Century Cake").status());

		var unknown = est(Map.of(), Buffs.NONE, Map.of()).temporary(null, GARDEN, now);
		assertEquals(Status.UNKNOWN, find(unknown, "Harvest Harbinger Potion").status());
		assertEquals(Status.UNKNOWN, find(unknown, "Chocolate Century Cake").status());
		assertEquals(0D, FarmingFortuneTemporary.activeTotal(unknown), EPS);
	}

	@Test
	public void activeBuffsNeverChangeTheMainTotals() {
		long now = 1_800_000_000_000L;
		var accessories = Map.of("accessories", List.of(
			item("ATMOSPHERIC_FILTER"), item("MAGIC_8_BALL", "\u00a77Farming Fortune: \u00a7a+25")));
		var plain = est(Map.of("infiltration", 10), Buffs.NONE, accessories);
		var buffed = est(Map.of("infiltration", 10), new Buffs(true, 18000L, now + HOUR, 20, 20), accessories);
		for (String crop : FarmingFortuneEstimate.CROPS) {
			assertEquals(crop, plain.forCrop(crop, GARDEN), buffed.forCrop(crop, GARDEN), EPS);
		}
		assertEquals(plain.permanentTotal(GARDEN), buffed.permanentTotal(GARDEN), EPS);
		assertEquals(plain.general(GARDEN), buffed.general(GARDEN), EPS);
		assertTrue(FarmingFortuneTemporary.activeTotal(buffed.temporary("Wheat", GARDEN, SPRING)) > 0D);
	}

	@Test
	public void hyperchargeStrengthensOnlyTheBuffsItAffects() {
		assertEquals(0.30D, FarmingFortuneTemporary.hyperchargeBonus(10), EPS);
		assertEquals(0.60D, FarmingFortuneTemporary.hyperchargeBonus(15), EPS);
		assertEquals(1.00D, FarmingFortuneTemporary.hyperchargeBonus(20), EPS);
		assertEquals(0D, FarmingFortuneTemporary.hyperchargeBonus(0), EPS);

		long now = 1_800_000_000_000L;
		var accessories = Map.of("accessories", List.of(item("ATMOSPHERIC_FILTER"), item("MAGIC_8_BALL")));
		var sources = est(Map.of("filter_upgrade", 10), new Buffs(true, 1L, now + HOUR, 20, 0), accessories)
			.temporary(null, Garden.NONE, now);
		assertEquals("Harvest Harbinger isn't affected", 50D, find(sources, "Harvest Harbinger Potion").value(), EPS);
		assertEquals(10D, find(sources, "Chocolate Century Cake").value(), EPS);
		assertEquals("40 pests x 5, doubled", 400D, find(sources, "Pest turn-in").value(), EPS);
		assertEquals("25 x 1.2 from Mite 10, doubled", 60D, find(sources, "Atmospheric Filter").value(), EPS);
		assertEquals(50D, find(sources, "Magic 8 Ball").value(), EPS);

		Source filter = find(sources, "Atmospheric Filter");
		double parts = filter.parts().stream().mapToDouble(FarmingFortuneEstimate.Line::value).sum();
		assertEquals("tooltip parts add up to the shown value", filter.value(), parts, EPS);
		Source pests = find(sources, "Pest turn-in");
		assertEquals(pests.value(), pests.parts().stream().mapToDouble(FarmingFortuneEstimate.Line::value).sum(), EPS);
	}

	@Test
	public void filterFollowsTheSkyBlockSeason() {
		assertTrue(FarmingFortuneTemporary.spring(EPOCH));
		assertTrue(FarmingFortuneTemporary.spring(SPRING));
		assertFalse(FarmingFortuneTemporary.spring(SUMMER));
		assertFalse(FarmingFortuneTemporary.spring(EPOCH + 31L * HOUR));
		assertTrue(FarmingFortuneTemporary.spring(EPOCH + 31L * HOUR - 1L));

		var est = est(Map.of(), Buffs.NONE, Map.of("accessories", List.of(item("ATMOSPHERIC_FILTER"))));
		Source spring = find(est.temporary(null, GARDEN, SPRING), "Atmospheric Filter");
		assertEquals(Status.ACTIVE, spring.status());
		assertEquals("Spring, 1d 5h left", spring.state());
		Source summer = find(est.temporary(null, GARDEN, SUMMER), "Atmospheric Filter");
		assertEquals(Status.INACTIVE, summer.status());
		assertEquals("Spring in 3d 12h", summer.state());
	}

	@Test
	public void magic8BallUsesTheRolledStatLine() {
		var rolled = est(Map.of(), Buffs.NONE, Map.of("accessories", List.of(
			item("MAGIC_8_BALL", "\u00a77Farming Fortune: \u00a7a+50")))).temporary(null, GARDEN, 0L);
		Source ball = find(rolled, "Magic 8 Ball");
		assertEquals(Status.ACTIVE, ball.status());
		assertEquals("lore value already includes Hypercharge", 50D, ball.value(), EPS);

		var other = est(Map.of(), Buffs.NONE, Map.of("accessories", List.of(
			item("MAGIC_8_BALL", "\u00a77Mining Fortune: \u00a7a+25")))).temporary(null, GARDEN, 0L);
		assertEquals(Status.INACTIVE, find(other, "Magic 8 Ball").status());

		var blank = est(Map.of(), Buffs.NONE, Map.of("accessories", List.of(item("MAGIC_8_BALL")))).temporary(null, GARDEN, 0L);
		assertEquals(Status.UNKNOWN, find(blank, "Magic 8 Ball").status());
		assertEquals(25D, find(blank, "Magic 8 Ball").value(), EPS);
	}

	@Test
	public void pestTurnInNeedsPhillip() {
		var est = est(Map.of(), Buffs.NONE, Map.of());
		assertTrue("unknown garden level still lists it", has(est.temporary(null, Garden.NONE, 0L), "Pest turn-in"));
		assertTrue(has(est.temporary(null, GARDEN, 0L), "Pest turn-in"));
		Garden early = new Garden(true, 3, 4, Map.of(), Map.of(), 0L);
		assertFalse("Phillip arrives at Garden V", has(est.temporary(null, early, 0L), "Pest turn-in"));
		Source pests = find(est.temporary(null, GARDEN, 0L), "Pest turn-in");
		assertEquals(200D, pests.value(), EPS);
		assertEquals(Status.UNKNOWN, pests.status());
	}

	@Test
	public void cropSourcesOnlyShowForACrop() {
		var all = Map.of(
			"accessories", List.of(item("ANITA_RING"), item("ANITA_ARTIFACT")),
			"inventory", List.of(item("REFINED_DARK_CACAO_TRUFFLE"))
		);
		var est = est(Map.of(), new Buffs(true, 0L, 0L, 0, 20), all);
		assertFalse(has(est.temporary(null, GARDEN, 0L), "Anita's Artifact"));
		var wheat = est.temporary("Wheat", GARDEN, 0L);
		Source anita = find(wheat, "Anita's Artifact");
		assertTrue(anita.crop());
		assertEquals("only the best Anita accessory", 25D, anita.value(), EPS);
		assertFalse(has(wheat, "Anita's Ring"));
		assertEquals(140D, find(wheat, "Overdrive Chip").value(), EPS);
		assertFalse(has(wheat, "Refined Dark Cacao Truffle"));
		Source truffle = find(est.temporary("Cocoa Beans", GARDEN, 0L), "Refined Dark Cacao Truffle");
		assertEquals(30D, truffle.value(), EPS);
		assertTrue(est.accessories().isEmpty());
	}

	@Test
	public void cropFeverComesFromTheToolEnchant() {
		CompoundTag attrs = new CompoundTag();
		attrs.putString("id", "THEORETICAL_HOE_WHEAT_3");
		attrs.putString("uuid", "w");
		CompoundTag ench = new CompoundTag();
		ench.putInt("ultimate_crop_fever", 3);
		attrs.put("enchantments", ench);
		var hoe = new InventoryDecoder.Stack("THEORETICAL_HOE_WHEAT_3", 1, attrs, List.of(), false);
		var est = est(Map.of(), Buffs.NONE, Map.of("inventory", List.of(hoe)));
		Source fever = find(est.temporary("Wheat", GARDEN, 0L), "Crop Fever");
		assertEquals(100D, fever.value(), EPS);
		assertEquals(Status.UNKNOWN, fever.status());
		assertFalse(has(est.temporary("Carrot", GARDEN, 0L), "Crop Fever"));
	}

	@Test
	public void shardSourcesUseTheirRealNames() {
		var sources = est(Map.of("infiltration", 10, "pest_fortune", 4), Buffs.NONE, Map.of()).temporary(null, GARDEN, 0L);
		assertEquals(30D, find(sources, "Earthworm shard").value(), EPS);
		assertEquals("ATTRIBUTE_SHARD_INFILTRATION;1", find(sources, "Earthworm shard").iconId());
		assertEquals(20D, find(sources, "Cricket shard").value(), EPS);
		assertFalse(has(sources, "Termite shard"));
	}
}
