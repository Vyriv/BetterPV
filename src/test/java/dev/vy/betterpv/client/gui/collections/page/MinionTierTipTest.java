package dev.vy.betterpv.client.gui.collections.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.MinionCosts;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public final class MinionTierTipTest {
	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void upgradeStoneTierShowsMaterialAndCraftCost() {
		Map<String, Integer> ingredients = Map.of("JUNGLE_MINION_XII_UPGRADE_STONE", 1);
		MinionCosts.TierCost cost = new MinionCosts.TierCost(12, 6_128_000D, List.of(), false);
		List<String> text = text(MinionsPage.tierTipLines("Jungle Minion XII", cost, ingredients,
			id -> "Jungle Minion XII Upgrade Stone", id -> null, id -> null, id -> 6_128_000D, "Unpriced", "No recipe data"));
		String price = FormatUtil.shortCoins(6_128_000D);
		assertEquals(List.of(
			"Jungle Minion XII",
			"",
			"1x Jungle Minion XII Upgrade Stone" + price,
			"",
			"Craft cost:" + price
		), text);
	}

	@Test
	public void ordinaryTierListsEveryMaterial() {
		Map<String, Integer> ingredients = new LinkedHashMap<>();
		ingredients.put("ENCHANTED_COBBLESTONE", 64);
		ingredients.put("ENCHANTED_COAL", 8);
		MinionCosts.TierCost cost = new MinionCosts.TierCost(7, 64 * 160D + 8 * 300D, List.of(), false);
		List<String> text = text(MinionsPage.tierTipLines("Cobblestone Minion VII", cost, ingredients,
			id -> id.equals("ENCHANTED_COAL") ? "Enchanted Coal" : "Enchanted Cobblestone", id -> null, id -> null,
			id -> id.equals("ENCHANTED_COAL") ? 300D : 160D, "Unpriced", "No recipe data"));
		assertEquals("64x Enchanted Cobblestone" + FormatUtil.shortCoins(64 * 160D), text.get(2));
		assertEquals("8x Enchanted Coal" + FormatUtil.shortCoins(8 * 300D), text.get(3));
		assertEquals("Craft cost:" + FormatUtil.shortCoins(64 * 160D + 8 * 300D), text.get(5));
		assertFalse(text.stream().anyMatch(line -> line.startsWith("Needs") || line.startsWith("Total")
			|| line.contains("Material cost only")));
	}

	@Test
	public void unknownPricesAreNotZero() {
		Map<String, Integer> ingredients = new LinkedHashMap<>();
		ingredients.put("PRICED", 2);
		ingredients.put("MYSTERY", 1);
		MinionCosts.TierCost partial = new MinionCosts.TierCost(3, 200D, List.of("MYSTERY"), false);
		List<String> text = text(MinionsPage.tierTipLines("Test Minion III", partial, ingredients,
			id -> id, id -> null, id -> null, id -> id.equals("PRICED") ? 100D : null, "Unpriced", "No recipe data"));
		assertEquals("1x MYSTERYUnpriced", text.get(3));
		assertEquals("Craft cost:" + FormatUtil.shortCoins(200D) + "+", text.get(5));
		assertEquals("Excludes materials with no price", text.get(6));

		MinionCosts.TierCost none = new MinionCosts.TierCost(3, 0D, List.of("MYSTERY"), false);
		List<String> unknown = text(MinionsPage.tierTipLines("Test Minion III", none, Map.of("MYSTERY", 1),
			id -> id, id -> null, id -> null, id -> null, "Unpriced", "No recipe data"));
		assertEquals("Craft cost:Unknown", unknown.get(unknown.size() - 1));
		assertTrue(unknown.stream().noneMatch(line -> line.endsWith(":0")));
	}

	@Test
	public void quantityNameAndPriceAreStyledApart() {
		List<PvTooltip.Line> lines = MinionsPage.tierTipLines("Coal Minion II", new MinionCosts.TierCost(2, 4_800D, List.of(), false),
			Map.of("ENCHANTED_COAL", 16), id -> "Enchanted Coal", id -> 0xFF55FF55, id -> null, id -> 300D, "Unpriced", "No recipe data");
		PvTooltip.Line row = lines.get(2);
		assertEquals("16x ", row.left().get(0).text());
		assertEquals(PvDraw.COLOR_MUTED, row.left().get(0).color());
		assertEquals(0xFF55FF55, row.left().get(1).color());
		assertEquals(PvDraw.COLOR_GOLD, row.value().get(0).color());
		assertNull("no icon when none is ready", row.icon());
		PvTooltip.Line total = lines.get(lines.size() - 1);
		assertTrue("craft cost total is highlighted", total.value().get(0).bold());
		assertEquals(PvDraw.COLOR_GOLD, total.value().get(0).color());
	}

	@Test
	public void missingRecipeSaysSo() {
		MinionCosts.TierCost cost = new MinionCosts.TierCost(1, 0D, List.of(), true);
		assertEquals(List.of("Odd Minion I", "No recipe data"), text(MinionsPage.tierTipLines(
			"Odd Minion I", cost, Map.of(), id -> id, id -> null, id -> null, id -> null, "Unpriced", "No recipe data")));
	}

	private static List<String> text(List<PvTooltip.Line> lines) {
		return lines.stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).collect(Collectors.joining()))
			.toList();
	}
}
