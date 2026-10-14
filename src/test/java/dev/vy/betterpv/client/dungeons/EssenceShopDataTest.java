package dev.vy.betterpv.client.dungeons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.client.data.DungeonSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class EssenceShopDataTest {
	private static final List<String> SAFARI_ORDER = List.of(
		"critter_catcher", "critter_master", "floortunate", "fresh_footprints", "head_start", "hunting_hotspot",
		"thawing", "deep_diver", "quickdraw", "amateur_hour", "sparkling_specialist", "eagles_advantage"
	);

	// Same layout as a profile member: perks under player_data, balances under currencies.essence.
	private static JsonObject member(String perks) {
		return JsonParser.parseString("""
			{"player_data":{"perks":%s},"currencies":{"essence":{"SAFARI":{"current":4321}}}}
			""".formatted(perks)).getAsJsonObject();
	}

	@Test
	public void safariKeepsAllTwelvePerksInOrder() {
		DungeonSnapshot.EssenceShop shop = EssenceShopData.safari(member("{}"));
		assertEquals(12, shop.perks().size());
		for (int i = 0; i < SAFARI_ORDER.size(); i++) {
			assertEquals(SAFARI_ORDER.get(i), shop.perks().get(i).id());
			assertEquals(0, shop.perks().get(i).level());
		}
		assertEquals(4321L, shop.balance());
		assertEquals("ESSENCE_SAFARI", shop.iconId());
	}

	@Test
	public void safariLevelsComeFromPlayerDataPerks() {
		DungeonSnapshot.EssenceShop shop = EssenceShopData.safari(member("""
			{"critter_catcher":8,"critter_master":1,"thawing":4,"amateur_hour":10,"sparkling_specialist":1,"eagles_advantage":3}
			"""));
		assertEquals(8, level(shop, "critter_catcher"));
		assertEquals(1, level(shop, "critter_master"));
		assertEquals(4, level(shop, "thawing"));
		assertEquals(10, level(shop, "amateur_hour"));
		assertEquals(1, level(shop, "sparkling_specialist"));
		assertEquals(3, level(shop, "eagles_advantage"));
		assertEquals(5, maxLevel(shop, "eagles_advantage"));
		assertEquals(0, level(shop, "quickdraw"));
	}

	// Real profile log: [BetterPV] Unmapped essence perk keys: {..., eagles_advantage=5, ...}
	@Test
	public void eaglesAdvantageReadsTheApiKey() {
		DungeonSnapshot.EssenceShop shop = EssenceShopData.safari(member("{\"eagles_advantage\":5}"));
		DungeonSnapshot.EssencePerk eagle = shop.perks().get(shop.perks().size() - 1);
		assertEquals("eagles_advantage", eagle.id());
		assertEquals("Eagle's Advantage", eagle.name());
		assertEquals(5, eagle.level());
		assertEquals(5, eagle.maxLevel());
		assertTrue(eagle.maxed());

		assertEquals(0, level(EssenceShopData.safari(member("{\"eagle_advantage\":5}")), "eagles_advantage"));
	}

	@Test
	public void levelsClampToMaxAndAcceptNumericStrings() {
		DungeonSnapshot.EssenceShop shop = EssenceShopData.safari(member("""
			{"critter_catcher":99,"floortunate":"2"}
			"""));
		assertEquals(8, level(shop, "critter_catcher"));
		assertEquals(2, level(shop, "floortunate"));
	}

	@Test
	public void legacyDungeonsPerksStillRead() {
		JsonObject legacy = JsonParser.parseString("{\"dungeons\":{\"perks\":{\"permanent_health\":4}}}").getAsJsonObject();
		assertEquals(4, level(EssenceShopData.wither(legacy), "permanent_health"));
	}

	@Test
	public void unmappedPerksListUnknownKeysWithRawValues() {
		Map<String, String> unmapped = EssenceShopData.unmappedPerks(member("""
			{"critter_catcher":8,"help_of_the_fairies":1,"mystery_perk":3,"other_perk":"LEGENDARY"}
			"""));
		assertEquals(Map.of("mystery_perk", "3", "other_perk", "\"LEGENDARY\""), unmapped);
	}

	@Test
	public void realProfileLogOnlyFlagsGenuinelyUnmappedKeys() {
		Map<String, String> unmapped = EssenceShopData.unmappedPerks(member("""
			{"agatha_fig_fortune":4,"agatha_fig_personal_best":1,"agatha_mangrove_personal_best":1,"agatha_power":2,
			 "blessing_of_time":1,"dungeonbreaker_secrets":5,"eagles_advantage":5,"miria_helix_personal_best":1,
			 "miria_power":3,"critter_catcher":8}
			"""));
		assertFalse(unmapped.containsKey("eagles_advantage"));
		assertFalse(unmapped.containsKey("dungeonbreaker_secrets"));
		assertFalse(unmapped.containsKey("critter_catcher"));
		assertEquals(Set.of("agatha_fig_fortune", "agatha_fig_personal_best", "agatha_mangrove_personal_best",
			"agatha_power", "blessing_of_time", "miria_helix_personal_best", "miria_power"), unmapped.keySet());
	}

	// Real profile log: dungeonbreaker_secrets=5 (Echoes of the Lost, Wither shop, 5 tiers).
	@Test
	public void witherShowsEchoesOfTheLostAfterExistingPerks() {
		JsonObject member = JsonParser.parseString("""
			{"player_data":{"perks":{"permanent_health":5,"forbidden_blessing":10,"dungeonbreaker_secrets":5}},
			 "currencies":{"essence":{"WITHER":{"current":1200}}}}
			""").getAsJsonObject();
		DungeonSnapshot.EssenceShop shop = EssenceShopData.wither(member);
		assertEquals(List.of("permanent_health", "permanent_defense", "permanent_speed", "permanent_intelligence",
			"permanent_strength", "forbidden_blessing", "dungeonbreaker_secrets"),
			shop.perks().stream().map(DungeonSnapshot.EssencePerk::id).toList());
		DungeonSnapshot.EssencePerk echoes = shop.perks().get(6);
		assertEquals("Echoes of the Lost", echoes.name());
		assertEquals(5, echoes.level());
		assertEquals(5, echoes.maxLevel());
		assertTrue(echoes.maxed());
		assertEquals(5, level(shop, "permanent_health"));
		assertEquals(10, level(shop, "forbidden_blessing"));
		assertEquals(1200L, shop.balance());

		assertEquals(0, level(EssenceShopData.wither(member("{}")), "dungeonbreaker_secrets"));
	}

	@Test
	public void everyCurrentlyMappedSafariKeyIsKnown() {
		StringBuilder perks = new StringBuilder("{");
		for (String id : SAFARI_ORDER) {
			perks.append(perks.length() > 1 ? "," : "").append('"').append(id).append("\":1");
		}
		assertTrue(EssenceShopData.unmappedPerks(member(perks.append('}').toString())).isEmpty());
		assertTrue(EssenceShopData.unmappedPerks(null).isEmpty());
		assertFalse(EssenceShopData.unmappedPerks(member("{\"x\":1}")).isEmpty());
	}

	private static int level(DungeonSnapshot.EssenceShop shop, String id) {
		return shop.perks().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().level();
	}

	private static int maxLevel(DungeonSnapshot.EssenceShop shop, String id) {
		return shop.perks().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().maxLevel();
	}
}
