package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import dev.vy.betterpv.client.data.FarmingFortuneEstimate.Garden;
import dev.vy.betterpv.client.data.FarmingFortuneEstimate.Kind;
import dev.vy.betterpv.client.data.FarmingFortuneEstimate.Line;
import dev.vy.betterpv.client.data.FarmingFortuneSummary.Category;
import dev.vy.betterpv.client.data.FarmingFortuneSummary.Group;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.Test;

public class FarmingFortuneSummaryTest {
	private static final double EPS = 0.001D;
	private static final Garden GARDEN = new Garden(true, 24, 15, Map.of("Wheat", 9), Map.of("Wheat", 46), 0L);

	private static InventoryDecoder.Stack item(String id, String... lore) {
		return new InventoryDecoder.Stack(id, 1, null, List.of(lore), false);
	}

	private static FarmingFortuneEstimate eliteComparison() {
		var raw = FarmingFortuneEstimate.inputs(
			JsonParser.parseString(FarmingFortuneEstimateTest.ELITE_COMPARISON_MEMBER).getAsJsonObject());
		Map<String, Integer> shards = Map.of("fortunate_farmer", 10, "ultimate_dna", 10, "solar_power", 10,
			"lunar_power", 10, "infiltration", 10, "filter_upgrade", 10);
		var in = new FarmingFortuneEstimate.Inputs(true, true, true, List.of(), Set.of(), false, 60, raw.anitaLevel(),
			raw.pestTiers(), raw.gardenTiers(), raw.dnaMilestone(), raw.cropshotLevel(), shards, false, Map.of(), List.of());
		return FarmingFortuneEstimate.from(in, Map.of("accessories", List.of(
			item("HELIANTHUS_RELIC", "\u00a77Farming Fortune: \u00a7a+40"),
			item("MAGIC_8_BALL", "\u00a77+25 Farming Fortune"),
			item("ATMOSPHERIC_FILTER", "\u00a77Grants \u00a76+25 Farming Fortune\u00a77."),
			item("FARMING_TALISMAN", "\u00a77Farming Fortune: \u00a7a+3")
		)));
	}

	@Test
	public void permanentGroupsAddUpToThePermanentTotal() {
		FarmingFortuneEstimate est = eliteComparison();
		List<Group> groups = FarmingFortuneSummary.permanentGroups(est, GARDEN);
		assertEquals(List.of(Category.SKILLS, Category.GARDEN, Category.SHARDS, Category.ACCESSORIES),
			groups.stream().map(Group::category).toList());
		assertEquals("Farming 240 and Anita 60", 300D, groups.get(0).total(), EPS);
		assertEquals("plots 72, bestiary 96.8, DNA 30, Cropshot 100", 298.8D, groups.get(1).total(), EPS);
		assertEquals("Fly 25, Galaxy Fish 10, Firefly and Lunar Moth 50", 85D, groups.get(2).total(), EPS);
		assertEquals("Talisman 3 and Helianthus Relic 40", 43D, groups.get(3).total(), EPS);
		assertEquals(726.8D, groups.stream().mapToDouble(Group::total).sum(), EPS);
		assertEquals(est.permanentTotal(GARDEN), groups.stream().mapToDouble(Group::total).sum(), EPS);
		assertTrue("conditional bonuses stay out of the groups",
			groups.stream().flatMap(g -> g.lines().stream()).noneMatch(l -> l.kind() == Kind.CONDITIONAL));
		assertEquals("Farming, Anita", FarmingFortuneSummary.detail(groups.get(0)));
		assertEquals("1 accessory, Relic", FarmingFortuneSummary.detail(groups.get(3)));
	}

	@Test
	public void everyCountedPermanentSourceHasAKnownCategory() {
		Map<String, Category> expected = Map.ofEntries(
			Map.entry("Farming level", Category.SKILLS),
			Map.entry("Anita bonus", Category.SKILLS),
			Map.entry("Unlocked plots", Category.GARDEN),
			Map.entry("Garden bestiary", Category.GARDEN),
			Map.entry("DNA Analysis milestone", Category.GARDEN),
			Map.entry("Cropshot chip", Category.GARDEN),
			Map.entry("Fly shard", Category.SHARDS),
			Map.entry("Galaxy Fish shard", Category.SHARDS),
			Map.entry("Firefly and Lunar Moth shards", Category.SHARDS),
			Map.entry("Accessories", Category.ACCESSORIES),
			Map.entry("Helianthus Relic", Category.ACCESSORIES)
		);
		for (Line line : eliteComparison().permanentLines(GARDEN)) {
			if (line.kind() != Kind.CONDITIONAL) {
				assertEquals(line.label(), expected.get(line.label()), FarmingFortuneSummary.category(line.label()));
			}
		}
	}

	@Test
	public void temporarySourcesExplainWhenTheyApply() {
		var temporary = eliteComparison().temporary("Wheat", GARDEN, 0L);
		assertTrue(temporary.stream().map(FarmingFortuneTemporary.Source::label).toList()
			.containsAll(List.of("Earthworm shard", "Magic 8 Ball", "Atmospheric Filter")));
		for (var s : temporary) {
			assertFalse(s.label(), s.condition().isBlank());
			assertTrue(s.condition(), s.condition().endsWith("."));
			assertFalse(s.label(), s.iconId() == null || s.iconId().isBlank());
		}
	}

	@Test
	public void missingToolTextHasNoPlaceholders() {
		for (String note : List.of("Inventory API off", "Toolkit slot unreadable", "Toolkit not in API",
			"None in inventories, toolkit locked", "None in inventories or toolkit")) {
			Line line = new Line("Best tool", 0D, Kind.UNAVAILABLE, note);
			for (String text : List.of(FarmingFortuneSummary.missingToolDetail(line),
				FarmingFortuneSummary.missingToolExplanation("Wild Rose", line))) {
				assertFalse(text, text.contains("?"));
				assertFalse(text, text.toLowerCase().contains("unknown"));
				assertFalse(text, text.isBlank());
			}
		}
		Line none = new Line("Best tool", 0D, Kind.COUNTED, "None in inventories or toolkit");
		assertEquals("No tool", FarmingFortuneSummary.missingToolDetail(none));
		assertEquals("No Potato tool in inventories or the toolkit.", FarmingFortuneSummary.missingToolExplanation("Potato", none));
	}

	@Test
	public void shortToolNameDropsReforgeAndMark() {
		CompoundTag attrs = new CompoundTag();
		attrs.putString("modifier", "blessed");
		var named = new InventoryDecoder.Stack("THEORETICAL_HOE_WHEAT_3", 1, attrs, List.of(), false,
			"\u00a76Blessed Euclid's Wheat Hoe Mk. III", null, null, null);
		assertEquals("Euclid's Wheat Hoe III", FarmingFortuneSummary.shortToolName(named));

		var plain = new InventoryDecoder.Stack("CACTUS_KNIFE", 1, null, List.of(), false, "\u00a79Cactus Knife", null, null, null);
		assertEquals("Cactus Knife", FarmingFortuneSummary.shortToolName(plain));
		assertEquals("CACTUS_KNIFE", FarmingFortuneSummary.shortToolName(item("CACTUS_KNIFE")));
	}
}
