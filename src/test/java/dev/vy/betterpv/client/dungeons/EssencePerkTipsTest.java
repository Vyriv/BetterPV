package dev.vy.betterpv.client.dungeons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public final class EssencePerkTipsTest {
	@Test
	public void echoesOfTheLostTipShowsNameLevelAndCharges() {
		List<String> text = text(EssencePerkTips.tip(
			new DungeonSnapshot.EssencePerk("dungeonbreaker_secrets", "Echoes of the Lost", 5, 5)));
		assertEquals("Echoes of the Lost", text.get(0));
		assertTrue(text.contains("Level5/5"));
		assertTrue(text.contains("Restore 5 charges on your Dungeonbreaker after collecting a secret."));
	}

	@Test
	public void echoesOfTheLostFirstTierUsesSingularCharge() {
		List<String> text = text(EssencePerkTips.tip(
			new DungeonSnapshot.EssencePerk("dungeonbreaker_secrets", "Echoes of the Lost", 1, 5)));
		assertTrue(text.contains("Restore 1 charge on your Dungeonbreaker after collecting a secret."));
	}

	@Test
	public void everyShopPerkHasAnEffectDescription() {
		JsonObject member = new JsonObject();
		List<DungeonSnapshot.EssenceShop> shops = List.of(
			EssenceShopData.wither(member), EssenceShopData.undead(member), EssenceShopData.gold(member),
			EssenceShopData.crimson(member), EssenceShopData.forest(member), EssenceShopData.diamond(member),
			EssenceShopData.spider(member), EssenceShopData.dragon(member), EssenceShopData.ice(member),
			EssenceShopData.fossil(member), EssenceShopData.safari(member));
		for (DungeonSnapshot.EssenceShop shop : shops) {
			for (DungeonSnapshot.EssencePerk perk : shop.perks()) {
				for (int level = 0; level <= perk.maxLevel(); level++) {
					List<String> text = text(EssencePerkTips.tip(
						new DungeonSnapshot.EssencePerk(perk.id(), perk.name(), level, perk.maxLevel())));
					assertFalse(perk.id(), text.contains("No effect description available."));
				}
			}
		}
	}

	@Test
	public void decimalTiersAndStatLabelsRenderCleanly() {
		List<String> thawing = text(EssencePerkTips.tip(new DungeonSnapshot.EssencePerk("thawing", "Thawing", 3, 10)));
		assertTrue(thawing.contains("Grants +7.5 ❄ Cold Resistance while in the Critter Safari."));
		List<String> tools = text(EssencePerkTips.tip(
			new DungeonSnapshot.EssencePerk("fresh_tools_kuudra", "Fresh Tools", 2, 5)));
		assertTrue(tools.get(3).startsWith("7.5% chance"));
		List<String> eager = text(EssencePerkTips.tip(
			new DungeonSnapshot.EssencePerk("eager_miner", "Eager Miner", 10, 10)));
		assertTrue(eager.contains("Increases your ⸕ Mining Speed by 100 while on Mining Islands."));
	}

	private static List<String> text(List<PvTooltip.Line> lines) {
		return lines.stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).collect(Collectors.joining()))
			.toList();
	}
}
