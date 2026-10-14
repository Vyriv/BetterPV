package dev.vy.betterpv.client.gui.collections.page;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class CollectionRewardsTest {
	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final Map<String, String> NAMES = Map.of(
		"Cactus Minion I", "CACTUS_GENERATOR_1",
		"Enchanted Cactus Green", "ENCHANTED_CACTUS_GREEN",
		"Cactus Helmet", "CACTUS_HELMET"
	);

	private static CollectionRewards.Reward parse(String unlock, List<String> asked) {
		return CollectionRewards.parse(unlock, name -> {
			asked.add(name);
			return NAMES.get(name);
		});
	}

	@Test
	public void minionRecipesLookUpTheTierOneMinion() {
		List<String> asked = new ArrayList<>();
		CollectionRewards.Reward r = parse("Cactus Minion Recipes", asked);

		assertEquals(List.of("Cactus Minion I"), asked);
		assertEquals(CollectionRewards.Kind.MINION, r.kind());
		assertEquals("CACTUS_GENERATOR_1", r.itemId());
		assertEquals("Cactus Minion", r.label());
		assertEquals("Recipes", r.suffix());
	}

	@Test
	public void itemRecipesUseTheNameBeforeRecipe() {
		CollectionRewards.Reward r = parse("Enchanted Cactus Green Recipe", new ArrayList<>());

		assertEquals(CollectionRewards.Kind.ITEM, r.kind());
		assertEquals("ENCHANTED_CACTUS_GREEN", r.itemId());
		assertEquals("Enchanted Cactus Green", r.label());
		assertEquals("Recipe", r.suffix());
	}

	@Test
	public void unknownRecipeNamesStayPlainText() {
		CollectionRewards.Reward r = parse("[Lvl 1] Silverfish Recipe", new ArrayList<>());

		assertEquals(CollectionRewards.Kind.ITEM, r.kind());
		assertNull(r.itemId());
		assertFalse(r.isItem());
		assertEquals("[Lvl 1] Silverfish", r.label());
	}

	@Test
	public void xpAndPerksNeverLookUpItems() {
		List<String> asked = new ArrayList<>();
		CollectionRewards.Reward xp = parse("+4 SkyBlock XP", asked);
		CollectionRewards.Reward perk = parse("Thorns Exp Discount (-25%)", asked);
		CollectionRewards.Reward exp = parse("+50,000 Farming Experience", asked);

		assertTrue(asked.isEmpty());
		assertEquals(CollectionRewards.Kind.XP, xp.kind());
		assertEquals(CollectionRewards.XP_COLOR, CollectionRewards.color(xp));
		assertEquals(CollectionRewards.Kind.OTHER, perk.kind());
		assertEquals("Thorns Exp Discount (-25%)", perk.label());
		assertEquals(CollectionRewards.Kind.OTHER, exp.kind());
	}

	@Test
	public void recipeGridKeepsCellPositionsAndCounts() {
		JsonObject json = JsonParser.parseString("""
			{"A1":"","A2":"ENCHANTED_CACTUS:32","A3":"",
			 "B1":"ENCHANTED_CACTUS:32","B2":"","B3":"ENCHANTED_CACTUS:32",
			 "C1":"","C2":"ENCHANTED_CACTUS:32","C3":"","count":2}
			""").getAsJsonObject();

		CollectionRewards.Recipe recipe = CollectionRewards.recipe(json);

		assertArrayEquals(new String[] {
			null, "ENCHANTED_CACTUS", null,
			"ENCHANTED_CACTUS", null, "ENCHANTED_CACTUS",
			null, "ENCHANTED_CACTUS", null
		}, recipe.ids());
		assertEquals(32, recipe.counts()[1]);
		assertEquals(0, recipe.counts()[0]);
		assertEquals(2, recipe.outputCount());
	}

	private static final java.util.Set<String> NEU_IDS = java.util.Set.of(
		"CROW;0", "REPLENISH;1", "ULTIMATE_CROP_FEVER;1", "POTION_STUN;1", "POTION_ADRENALINE;1");

	private static CollectionRewards.Reward parseWithIds(String unlock, List<String> asked) {
		return CollectionRewards.parse(unlock, name -> {
			asked.add(name);
			return Map.of("Stun Potion", "TANK_DUNGEON_ABILITY_1", "\ue010 Perfect Ruby Gemstone", "PERFECT_RUBY_GEM",
				"Refined Umber", "REFINED_UMBER").get(name);
		}, NEU_IDS::contains);
	}

	@Test
	public void collectionPetsUseTheCommonNeuPet() {
		CollectionRewards.Reward crow = parseWithIds("[Lvl 1] Crow Recipe", new ArrayList<>());
		assertEquals("CROW;0", crow.itemId());
		assertEquals("CROW;0", crow.iconId());
		assertEquals("[Lvl 1] Crow", crow.label());
		assertNull("unknown pets stay plain", parseWithIds("[Lvl 1] Glacite Golem Recipe", new ArrayList<>()).itemId());
	}

	@Test
	public void enchantedBooksMapToNeuEnchantIds() {
		assertEquals("REPLENISH;1", parseWithIds("Enchanted Book (Replenish I) Recipe", new ArrayList<>()).itemId());
		CollectionRewards.Reward fever = parseWithIds("Enchanted Book (Crop Fever I) Recipe", new ArrayList<>());
		assertEquals("ULTIMATE_CROP_FEVER;1", fever.itemId());
		assertEquals("Crop Fever I", fever.label());
		assertEquals("Enchanted Book (Crop Fever I) Recipe", fever.text());
		CollectionRewards.Reward missing = parseWithIds("Enchanted Book (Prismatic I) Recipe", new ArrayList<>());
		assertNull("no rarity or tooltip for a book NEU doesn't have", missing.itemId());
		assertEquals("ENCHANTED_BOOK", missing.iconId());
		assertEquals("Prismatic I", missing.label());
		assertEquals("Enchanted Book (Prismatic I) Recipe", missing.text());
	}

	@Test
	public void potionsBorrowOnlyTheLevelOneIcon() {
		List<String> asked = new ArrayList<>();
		CollectionRewards.Reward stun = parseWithIds("Stun Potion Recipe", asked);
		assertTrue("the dungeon Stun Potion ability item is never looked up", asked.isEmpty());
		assertNull(stun.itemId());
		assertEquals("POTION_STUN;1", stun.iconId());
		assertTrue(stun.hasIcon());
		assertFalse(stun.isItem());
	}

	@Test
	public void dwarvenForgeRewardsDropTheSuffixAndGlyphs() {
		List<String> asked = new ArrayList<>();
		CollectionRewards.Reward umber = parseWithIds("Refined Umber Dwarven Forge Recipe", asked);
		assertEquals("REFINED_UMBER", umber.itemId());
		assertEquals("Refined Umber", umber.label());
		assertEquals("Forge Recipe", umber.suffix());

		CollectionRewards.Reward ruby = parseWithIds("\ue010 Perfect Ruby Gemstone Dwarven Forge Recipe", asked);
		assertEquals("PERFECT_RUBY_GEM", ruby.itemId());
		assertEquals("\u2764 Perfect Ruby Gemstone", ruby.label());
		assertFalse(ruby.text().chars().anyMatch(c -> c >= 0xE000 && c <= 0xF8FF));
	}

	@Test
	public void statRewardsUseTheStatColourAndVanillaSymbol() {
		CollectionRewards.Reward fortune = parse("+1\ue053 Mining Fortune", new ArrayList<>());
		assertEquals(CollectionRewards.Kind.STAT, fortune.kind());
		assertEquals("+1\u2618 Mining Fortune", fortune.text());
		assertEquals(0xFFFFAA00, CollectionRewards.color(fortune));
		assertEquals(CollectionRewards.Kind.OTHER, parse("+6 Accessory Bag Slots", new ArrayList<>()).kind());
	}

	@Test
	public void sharedNamesPickTheOnlyCraftableItem() {
		assertEquals("SMALL_BACKPACK", CollectionRewards.pickId(List.of("PURPLE_SMALL_BACKPACK", "SMALL_BACKPACK"),
			"SMALL_BACKPACK"::equals, () -> "FALLBACK"));
		assertNull("two craftable items stay unresolved", CollectionRewards.pickId(List.of("A", "B"), id -> true, () -> "FALLBACK"));
		assertEquals("FALLBACK", CollectionRewards.pickId(List.of(), id -> true, () -> "FALLBACK"));
		assertEquals("ONLY", CollectionRewards.pickId(List.of("ONLY"), id -> false, () -> "FALLBACK"));
	}

	@Test
	public void forgeInputsFillTheGridInOrder() {
		JsonObject item = JsonParser.parseString("""
			{"recipes":[{"type":"forge","inputs":["ENCHANTED_UMBER:160","GLACITE:3.0"],"count":1.0,"duration":3600}]}
			""").getAsJsonObject();
		CollectionRewards.Recipe recipe = CollectionRewards.forgeRecipe(item);
		assertTrue(recipe.forge());
		assertEquals("ENCHANTED_UMBER", recipe.ids()[0]);
		assertEquals(160, recipe.counts()[0]);
		assertEquals("GLACITE", recipe.ids()[1]);
		assertEquals(3, recipe.counts()[1]);
		assertNull(recipe.ids()[2]);
		assertEquals(1, recipe.outputCount());
		assertNull(CollectionRewards.forgeRecipe(JsonParser.parseString("{\"recipes\":[{\"type\":\"katgrade\"}]}").getAsJsonObject()));
	}

	@Test
	public void romanNumeralsParse() {
		assertEquals(1, CollectionRewards.roman("I"));
		assertEquals(4, CollectionRewards.roman("IV"));
		assertEquals(9, CollectionRewards.roman("IX"));
		assertEquals(14, CollectionRewards.roman("XIV"));
	}

	@Test
	public void emptyOrMissingRecipeIsNull() {
		assertNull(CollectionRewards.recipe((JsonObject) null));
		assertNull(CollectionRewards.recipe(JsonParser.parseString("{\"A1\":\"\",\"B2\":\"\"}").getAsJsonObject()));
	}
}
