package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.FarmingFortuneEstimate.Garden;
import dev.vy.betterpv.client.data.FarmingFortuneEstimate.Kind;
import dev.vy.betterpv.client.data.FarmingFortuneEstimate.Line;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.Test;

public class FarmingFortuneEstimateTest {
	private static final double EPS = 0.001D;
	private static final Garden GARDEN = new Garden(true, 24, 15, Map.of("Wheat", 9), Map.of("Cactus", 46, "Wheat", 46), 0L);

	private static InventoryDecoder.Stack item(String id, String... lore) {
		return new InventoryDecoder.Stack(id, 1, null, List.of(lore), false);
	}

	private static InventoryDecoder.Stack tool(String id, String uuid, CompoundTag attrs) {
		attrs.putString("id", id);
		attrs.putString("uuid", uuid);
		return new InventoryDecoder.Stack(id, 1, attrs, List.of(), false);
	}

	private static CompoundTag attrs(int level, String reforge, Map<String, Integer> enchants) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("levelable_lvl", level);
		if (reforge != null) {
			tag.putString("modifier", reforge);
		}
		CompoundTag ench = new CompoundTag();
		enchants.forEach(ench::putInt);
		tag.put("enchantments", ench);
		return tag;
	}

	private static PetSnapshot.Entry pet(String type, String tier, int level, String heldItem) {
		return new PetSnapshot.Entry(type, type, tier, 0, type, level, 100, 0, 0, 0, 0, 0f, false, heldItem, 0, "", "", 0);
	}

	private static FarmingFortuneEstimate.Inputs inputs(
		boolean inventoryKnown, boolean toolkitKnown, List<InventoryDecoder.Stack> toolkit, List<PetSnapshot.Entry> pets
	) {
		return new FarmingFortuneEstimate.Inputs(
			inventoryKnown, toolkitKnown, true, toolkit, Set.of(), false, 60, 15, 210, 210, 0, 0, Map.of(), true,
			Map.of("Wheat", 1_234_567L), pets
		);
	}

	private static FarmingFortuneEstimate.Inputs empty() {
		return new FarmingFortuneEstimate.Inputs(
			true, true, true, List.of(), Set.of(), false, 0, 0, 0, 0, 0, 0, Map.of(), false, Map.of(), List.of()
		);
	}

	private static double sum(List<Line> lines) {
		return lines.stream().filter(l -> l.kind() == Kind.COUNTED).mapToDouble(Line::value).sum();
	}

	@Test
	public void breakdownReconcilesWithTotalIncludingSetAndPieceBonuses() {
		var all = Map.of(
			"wardrobe", List.of(
				item("HELIANTHUS_HELMET", "\u00a77Farming Fortune: \u00a76+98", "\u00a7d\u00a7lMYTHIC HELMET"),
				item("HELIANTHUS_CHESTPLATE", "\u00a77Farming Fortune: \u00a76+98", "\u00a7d\u00a7lMYTHIC CHESTPLATE"),
				item("HELIANTHUS_LEGGINGS", "\u00a77Farming Fortune: \u00a76+80", "\u00a7d\u00a7lMYTHIC LEGGINGS"),
				item("HELIANTHUS_BOOTS", "\u00a77Farming Fortune: \u00a76+98", "\u00a7d\u00a7lMYTHIC BOOTS")
			),
			"equipment", List.of(
				item("BLOSSOM_NECKLACE", "\u00a77Farming Fortune: \u00a76+46.2", "\u00a77Piece Bonus: \u00a76+15\u2618", "\u00a76\u00a7lLEGENDARY NECKLACE"),
				item("BLOSSOM_BELT", "\u00a77Farming Fortune: \u00a76+46.2", "\u00a77Piece Bonus: \u00a76+15\u2618", "\u00a76\u00a7lLEGENDARY BELT"),
				item("BLOSSOM_BRACELET", "\u00a77Farming Fortune: \u00a76+46.2", "\u00a77Piece Bonus: \u00a76+15\u2618", "\u00a76\u00a7lLEGENDARY BRACELET"),
				item("ZORROS_CAPE", "\u00a77Farming Fortune: \u00a76+51.2", "\u00a76\u00a7lLEGENDARY CLOAK")
			)
		);
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(), List.of(pet("ELEPHANT", "LEGENDARY", 100, "YELLOW_BANDANA"))), all);

		assertEquals(240D + 60D + 72D + 84D, est.permanentTotal(GARDEN), EPS);
		assertEquals(est.permanentTotal(GARDEN), sum(est.permanentLines(GARDEN)), EPS);
		assertEquals("4 Helianthus pieces give the 4-piece set bonus", 75D, est.setBonuses().get(0).fortune(), EPS);
		assertEquals(98D * 3 + 80D + 75D, est.armourTotal(), EPS);
		assertEquals(46.2D * 3 + 15D * 3 + 51.2D, est.equipmentTotal(), EPS);
		assertEquals(180D, est.petTotal(null, GARDEN), EPS);
		assertEquals(456D + 449D + 234.8D + 180D, est.general(GARDEN), EPS);
		assertEquals(est.general(GARDEN) + sum(est.cropLines("Wheat", GARDEN)), est.forCrop("Wheat", GARDEN), EPS);
	}

	private static InventoryDecoder.Stack named(String id, String name, String... lore) {
		return new InventoryDecoder.Stack(id, 1, null, List.of(lore), false, name, null, null, null);
	}

	@Test
	public void reforgedBlossomEquipmentKeepsItsItemIds() {
		var all = Map.of("equipment", List.of(
			named("BLOSSOM_NECKLACE", "\u00a76Thorny Blossom Necklace", "\u00a77Farming Fortune: \u00a76+46.2", "\u00a76\u00a7lLEGENDARY NECKLACE"),
			named("BLOSSOM_CLOAK", "\u00a76Rooted Blossom Cloak", "\u00a77Farming Fortune: \u00a76+46.2", "\u00a76\u00a7lLEGENDARY CLOAK"),
			named("BLOSSOM_BELT", "\u00a76Thorny Blossom Belt", "\u00a77Farming Fortune: \u00a76+46.2", "\u00a76\u00a7lLEGENDARY BELT"),
			named("BLOSSOM_BRACELET", "\u00a76Thorny Blossom Bracelet", "\u00a77Farming Fortune: \u00a76+46.2", "\u00a76\u00a7lLEGENDARY BRACELET")
		));
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(), List.of()), all);

		List<String> ids = est.equipment().stream().map(p -> p.stack().id()).sorted().toList();
		assertEquals(List.of("BLOSSOM_BELT", "BLOSSOM_BRACELET", "BLOSSOM_CLOAK", "BLOSSOM_NECKLACE"), ids);
		assertEquals(46.2D * 4, est.equipmentTotal(), EPS);
	}

	@Test
	public void toolkitToolsWithoutLoreAreValuedFromAttributes() {
		// Same attributes as the Cactus Knife Mk. III that EliteBot found in the toolkit.
		var knife = tool("CACTUS_KNIFE_3", "k", attrs(17, "blessed",
			Map.of("harvesting", 6, "cultivating", 6, "turbo_cactus", 5, "dedication", 3)));
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(knife), List.of()), Map.of());

		FarmingFortuneEstimate.Tool cactus = est.tool("Cactus", GARDEN);
		assertTrue(cactus.fromToolkit());
		assertEquals("EPIC", cactus.rarity());
		assertEquals(68D + 13D + 75D + 12D + 46D + 25D, cactus.total(), EPS);
		assertEquals(cactus.total(), sum(cactus.parts()), EPS);
		assertEquals(cactus.total(), est.cropLines("Cactus", GARDEN).get(0).value(), EPS);
		assertEquals("Farming Toolkit", est.cropLines("Cactus", GARDEN).get(0).note());
		assertNull("a cactus knife does nothing for wheat", est.tool("Wheat", GARDEN));
	}

	@Test
	public void dedicationIsUnknownUntilTheGardenLoads() {
		var knife = tool("CACTUS_KNIFE_3", "k", attrs(17, "blessed", Map.of("dedication", 3)));
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(knife), List.of()), Map.of());
		FarmingFortuneEstimate.Tool tool = est.tool("Cactus", Garden.NONE);
		Line dedication = tool.parts().stream().filter(l -> l.label().startsWith("Dedication")).findFirst().orElseThrow();
		assertEquals(Kind.UNAVAILABLE, dedication.kind());
		assertEquals(68D + 13D, tool.total(), EPS);
	}

	@Test
	public void upgradedToolsMapToTheirCropsAndTypes() {
		var dicer = tool("MELON_DICER_3", "d", attrs(10, "blessed", Map.of("harvesting", 6, "sunder", 6, "turbo_melon", 5)));
		var eclipse = tool("THEORETICAL_HOE_SUNFLOWER_3", "e", attrs(1, null, Map.of("turbo_moonflower", 5)));
		var newton = tool("THEORETICAL_HOE_WARTS_3", "n", attrs(1, null, Map.of("harvesting", 6)));
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(dicer, eclipse, newton), List.of()), Map.of());

		assertEquals("dicers are axes: Sunder counts, Harvesting doesn't", 40D + 13D + 75D + 25D,
			est.tool("Melon", GARDEN).total(), EPS);
		assertEquals("THEORETICAL_HOE_SUNFLOWER_3", est.tool("Sunflower", GARDEN).stack().id());
		assertEquals("Turbo-Moonflower only counts on Moonflower", 4D, est.tool("Sunflower", GARDEN).total(), EPS);
		assertEquals(29D, est.tool("Moonflower", GARDEN).total(), EPS);
		assertEquals(4D + 75D, est.tool("Nether Wart", GARDEN).total(), EPS);
	}

	@Test
	public void recombAndGemsUseTheUpgradedRarity() {
		CompoundTag a = attrs(1, "blessed", Map.of());
		a.putInt("rarity_upgrades", 1);
		a.putInt("farming_for_dummies_count", 5);
		CompoundTag gems = new CompoundTag();
		gems.putString("PERIDOT_0", "PERFECT");
		CompoundTag fine = new CompoundTag();
		fine.putString("quality", "FINE");
		gems.put("PERIDOT_1", fine);
		a.put("gems", gems);
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(tool("CACTUS_KNIFE_3", "r", a)), List.of()), Map.of());
		FarmingFortuneEstimate.Tool tool = est.tool("Cactus", GARDEN);
		assertEquals("LEGENDARY", tool.rarity());
		assertEquals(4D + 16D + 5D + 8D + 5D, tool.total(), EPS);
	}

	@Test
	public void axedPerkAddsTwoPercentOnAxesOnly() {
		var chopper = tool("COCO_CHOPPER_3", "c", attrs(10, null, Map.of("sunder", 6)));
		var knife = tool("CACTUS_KNIFE_3", "k", attrs(10, null, Map.of("harvesting", 6)));
		var in = new FarmingFortuneEstimate.Inputs(true, true, true, List.of(chopper, knife), Set.of(), true,
			0, 0, 0, 0, 0, 0, Map.of(), false, Map.of(), List.of());
		var est = FarmingFortuneEstimate.from(in, Map.of());
		assertEquals((40D + 75D) * 1.02D, est.tool("Cocoa Beans", GARDEN).total(), EPS);
		assertEquals(40D + 75D, est.tool("Cactus", GARDEN).total(), EPS);
	}

	@Test
	public void inventoryCopyWinsOverTheSameToolInTheToolkit() {
		var held = tool("THEORETICAL_HOE_WHEAT_3", "same", attrs(20, null, Map.of()));
		var stored = tool("THEORETICAL_HOE_WHEAT_3", "same", attrs(5, null, Map.of()));
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(stored), List.of()), Map.of("inventory", List.of(held)));
		assertFalse(est.tool("Wheat", GARDEN).fromToolkit());
		assertEquals(80D, est.tool("Wheat", GARDEN).total(), EPS);
	}

	@Test
	public void missingToolExplainsWhyWhenNothingIsFound() {
		Line noToolkit = FarmingFortuneEstimate.from(inputs(true, false, List.of(), List.of()), Map.of())
			.cropLines("Cactus", GARDEN).get(0);
		assertEquals(Kind.UNAVAILABLE, noToolkit.kind());
		assertEquals("Toolkit not in API", noToolkit.note());

		Line noInventory = FarmingFortuneEstimate.from(inputs(false, true, List.of(), List.of()), Map.of())
			.cropLines("Cactus", GARDEN).get(0);
		assertEquals("Inventory API off", noInventory.note());

		var unreadable = new FarmingFortuneEstimate.Inputs(true, true, true, List.of(), Set.of("Cactus"), false,
			0, 0, 0, 0, 0, 0, Map.of(), false, Map.of(), List.of());
		Line bad = FarmingFortuneEstimate.from(unreadable, Map.of()).cropLines("Cactus", GARDEN).get(0);
		assertEquals(Kind.UNAVAILABLE, bad.kind());
		assertEquals("Toolkit slot unreadable", bad.note());

		var locked = new FarmingFortuneEstimate.Inputs(true, true, false, List.of(), Set.of(), false,
			0, 0, 0, 0, 0, 0, Map.of(), false, Map.of(), List.of());
		assertEquals("None in inventories, toolkit locked",
			FarmingFortuneEstimate.from(locked, Map.of()).cropLines("Cactus", GARDEN).get(0).note());

		Line none = FarmingFortuneEstimate.from(inputs(true, true, List.of(), List.of()), Map.of())
			.cropLines("Cactus", GARDEN).get(0);
		assertEquals(Kind.COUNTED, none.kind());
		assertEquals("None in inventories or toolkit", none.note());
	}

	@Test
	public void readsToolkitSlotsFromTheMemberJson() throws Exception {
		CompoundTag knife = attrs(17, "blessed", Map.of("harvesting", 6));
		knife.putString("id", "CACTUS_KNIFE_3");
		knife.putString("uuid", "k");
		CompoundTag dicer = attrs(10, null, Map.of());
		dicer.putString("id", "MELON_DICER_3");
		JsonObject member = new JsonObject();
		JsonObject inv = new JsonObject();
		inv.add("inv_contents", new JsonObject());
		member.add("inventory", inv);
		JsonObject gpd = new JsonObject();
		gpd.add("farming_toolkit", ToolkitFixture.toolkit(Map.of("CACTUS", knife, "MELON", dicer), Set.of("MELON")));
		member.add("garden_player_data", gpd);

		var est = FarmingFortuneEstimate.from(FarmingFortuneEstimate.inputs(member), Map.of());
		assertEquals("CACTUS_KNIFE_3", est.tool("Cactus", GARDEN).stack().id());
		assertEquals("Blessed reforge and rarity colour make the name", "\u00a75Blessed",
			est.tool("Cactus", GARDEN).stack().displayName().substring(0, 9));
		assertNull("in-use slots are skipped, the live copy is in the inventory", est.tool("Melon", GARDEN));
		assertEquals(1, est.toolkitTools());
	}

	@Test
	public void gardenSourcesAreUnavailableUntilTheIslandLoads() {
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(), List.of(pet("ELEPHANT", "LEGENDARY", 100, "GREEN_BANDANA"))), Map.of());
		List<Line> crop = est.cropLines("Wheat", Garden.NONE);
		assertEquals(Kind.UNAVAILABLE, crop.get(1).kind());
		assertEquals(45D, est.cropLines("Wheat", GARDEN).get(1).value(), EPS);
		assertEquals(150D, est.petTotal(null, Garden.NONE), EPS);
		assertEquals("Green Bandana is 4 per garden level", 210D, est.petTotal(null, GARDEN), EPS);
		assertEquals(Kind.UNAVAILABLE, est.permanentLines(Garden.NONE).get(2).kind());
	}

	@Test
	public void personalBestFortuneMatchesTheTierFormula() {
		assertEquals(100D, FarmingFortuneEstimate.personalBestFortune("Wheat", 1_234_567L), EPS);
		assertEquals(33.33D, FarmingFortuneEstimate.personalBestFortune("Carrot", 1_000_000L), EPS);
		assertEquals(0D, FarmingFortuneEstimate.personalBestFortune("Melon", 499L), EPS);

		var locked = FarmingFortuneEstimate.from(empty(), Map.of());
		assertEquals(0D, locked.cropLines("Wheat", GARDEN).get(2).value(), EPS);
		assertEquals("Perk not unlocked", locked.cropLines("Wheat", GARDEN).get(2).note());
	}

	private static double petTotal(String type, String tier, int level, String crop) {
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(), List.of(pet(type, tier, level, ""))), Map.of());
		var pick = est.bestPet(crop, GARDEN);
		return pick == null ? -1D : pick.total();
	}

	@Test
	public void petFormulasFollowCurrentPetData() {
		assertEquals(150D, petTotal("ELEPHANT", "LEGENDARY", 100, null), EPS);
		assertEquals("Mythic adds a fortune stat", 200D, petTotal("ELEPHANT", "MYTHIC", 100, null), EPS);
		assertEquals("Trunk Efficiency is Legendary and up", -1D, petTotal("ELEPHANT", "RARE", 100, null), EPS);
		assertEquals(100D, petTotal("MOOSHROOM_COW", "LEGENDARY", 100, null), EPS);
		assertEquals(20D, petTotal("BEE", "RARE", 100, null), EPS);
		assertEquals(30D, petTotal("BEE", "LEGENDARY", 100, null), EPS);
		assertEquals(40D, petTotal("BEE", "MYTHIC", 100, null), EPS);
		assertEquals(50D, petTotal("CHICKEN", "COMMON", 100, null), EPS);
		assertEquals("Hunter's Insight is 0.7 per pest bestiary tier", 0.7D * 210, petTotal("HEDGEHOG", "LEGENDARY", 100, null), EPS);
		assertEquals(-1D, petTotal("GOLDEN_DRAGON", "LEGENDARY", 200, null), EPS);
	}

	@Test
	public void roseDragonCountsGardenPowerRosyScalesAndSymbiosis() {
		Map<String, Integer> maxedMilestones = new java.util.HashMap<>();
		for (String crop : FarmingFortuneEstimate.CROPS) {
			maxedMilestones.put(crop, 46);
		}
		Garden garden = new Garden(true, 24, 15, Map.of(), maxedMilestones, 0L);
		List<PetSnapshot.Entry> pets = new ArrayList<>(List.of(
			new PetSnapshot.Entry("ROSE_DRAGON", "Rose Dragon", "LEGENDARY", 0, "ROSE_DRAGON", 200, 200, 0, 0, 0, 0, 0f,
				false, "", 0, "", "", 0),
			pet("HEDGEHOG", "LEGENDARY", 100, ""),
			pet("ELEPHANT", "LEGENDARY", 100, ""),
			pet("ELEPHANT", "MYTHIC", 100, ""),
			pet("MOOSHROOM_COW", "LEGENDARY", 100, ""),
			pet("BEE", "LEGENDARY", 100, ""),
			pet("CHICKEN", "LEGENDARY", 100, ""),
			pet("RABBIT", "LEGENDARY", 100, ""),
			pet("SLUG", "LEGENDARY", 99, ""),
			pet("GOLDEN_DRAGON", "LEGENDARY", 100, "")
		));
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(), pets), Map.of());
		var best = est.bestPet("Cactus", garden);

		assertEquals("ROSE_DRAGON", best.pet().type());
		double expected = 40D + 3D * 60 + 0.15D * 46 * 13 + 3D * 6;
		assertEquals("6 other unique maxed farming pets; level 99 Slug and Golden Dragon don't count", expected, best.total(), EPS);
		assertEquals(Kind.UNAVAILABLE, est.bestPet("Cactus", Garden.NONE).lines().stream()
			.filter(l -> l.label().equals("Rosy Scales")).findFirst().orElseThrow().kind());
	}

	@Test
	public void conditionalPetAbilitiesAreShownButNotCounted() {
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(), List.of(pet("HEDGEHOG", "LEGENDARY", 100, ""))), Map.of());
		var pick = est.bestPet(null, GARDEN);
		Line fearsome = pick.lines().stream().filter(l -> l.label().equals("Fearsome Farmer")).findFirst().orElseThrow();
		assertEquals(Kind.CONDITIONAL, fearsome.kind());
		assertEquals(100D, fearsome.value(), EPS);
		assertEquals(147D, pick.total(), EPS);
	}

	@Test
	public void cropSpecificPetsOnlyCountForTheirCrop() {
		Garden visitors = new Garden(true, 24, 15, Map.of(), Map.of(), 50L);
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(), List.of(
			pet("MOSQUITO", "LEGENDARY", 100, ""),
			pet("PIG", "LEGENDARY", 100, ""),
			pet("CHICKEN", "LEGENDARY", 100, "")
		)), Map.of());
		assertEquals("2 per visitor at level 100", 100D, est.bestPet("Sugar Cane", visitors).total(), EPS);
		assertEquals("MOSQUITO", est.bestPet("Sugar Cane", visitors).pet().type());
		assertEquals("CHICKEN", est.bestPet("Wheat", visitors).pet().type());
		assertEquals("CHICKEN", est.bestPet(null, visitors).pet().type());
		assertEquals(20D, est.pets("Potato", visitors).stream()
			.filter(p -> p.pet().type().equals("PIG")).findFirst().orElseThrow().total(), EPS);

		Garden many = new Garden(true, 24, 15, Map.of(), Map.of(), 140L);
		assertEquals("Buzzin' Barterer caps at 175", 175D, est.bestPet("Sugar Cane", many).total(), EPS);
		assertEquals("the Sugar Cane pet feeds the Sugar Cane total",
			est.permanentTotal(many) + 175D + est.cropBonus("Sugar Cane", many), est.forCrop("Sugar Cane", many), EPS);
	}

	@Test
	public void heldItemsCountWhenComparingPets() {
		var est = FarmingFortuneEstimate.from(inputs(true, true, List.of(), List.of(
			pet("MOOSHROOM_COW", "LEGENDARY", 100, ""),
			pet("ELEPHANT", "LEGENDARY", 100, ""),
			pet("ELEPHANT", "LEGENDARY", 80, "YELLOW_BANDANA")
		)), Map.of());
		assertEquals(List.of("ELEPHANT", "MOOSHROOM_COW"), est.pets(null, GARDEN).stream().map(p -> p.pet().type()).toList());
		assertEquals(150D, est.petTotal(null, GARDEN), EPS);
	}

	@Test
	public void pestBestiaryCountsReachedBrackets() {
		JsonObject kills = new JsonObject();
		kills.addProperty("pest_beetle_1", 250);
		kills.addProperty("pest_mouse_1", 100);
		kills.addProperty("pest_fly_1", 4);
		kills.addProperty("pest_praying_mantis_1", 1);
		assertEquals(15 + 15 + 3 + 1, FarmingFortuneEstimate.pestTiers(kills));
		assertEquals(0, FarmingFortuneEstimate.pestTiers(null));

		kills.addProperty("pest_lunar_moth_1", 100);
		assertEquals("Lunar Moth uses the 100-kill bracket like Field Mouse", 15 + 15 + 3 + 1 + 15,
			FarmingFortuneEstimate.pestTiers(kills));
	}

	@Test
	public void temporaryBonusesAreListedButLeftOutOfTotals() {
		var in = new FarmingFortuneEstimate.Inputs(true, true, true, List.of(), Set.of(), false, 0, 0, 0, 0, 0, 0,
			Map.of("lunar_power", 9, "infiltration", 5), false, Map.of(), List.of(pet("HEDGEHOG", "LEGENDARY", 100, "")));
		var est = FarmingFortuneEstimate.from(in, Map.of("accessories", List.of(item("MAGIC_8_BALL"))));
		assertEquals(est.permanentTotal(GARDEN), sum(est.permanentLines(GARDEN)), EPS);
		assertTrue(est.permanentLines(GARDEN).stream().noneMatch(l -> l.kind() == Kind.CONDITIONAL));
		assertEquals(72D, est.permanentTotal(GARDEN), EPS);

		Map<String, Double> temporary = new java.util.HashMap<>();
		for (var s : est.temporary("Cactus", GARDEN, 0L)) {
			temporary.put(s.label(), s.value());
		}
		assertEquals(45D, temporary.get("Lunar Moth shard"), EPS);
		assertEquals(15D, temporary.get("Earthworm shard"), EPS);
		assertEquals(25D, temporary.get("Magic 8 Ball"), EPS);
		assertEquals(100D, temporary.get("Fearsome Farmer"), EPS);
		assertEquals("nothing can be confirmed without player data", 0D,
			FarmingFortuneTemporary.activeTotal(est.temporary("Cactus", GARDEN, 0L)), EPS);
	}

	@Test
	public void cactusTotalIsTheSumOfEveryShownSourceOnce() {
		var knife = tool("CACTUS_KNIFE_3", "k", attrs(17, "blessed",
			Map.of("harvesting", 6, "cultivating", 6, "turbo_cactus", 5, "dedication", 3)));
		var all = Map.of(
			"wardrobe", List.of(
				item("HELIANTHUS_HELMET", "\u00a77Farming Fortune: \u00a76+98", "\u00a7d\u00a7lMYTHIC HELMET"),
				item("HELIANTHUS_CHESTPLATE", "\u00a77Farming Fortune: \u00a76+98", "\u00a7d\u00a7lMYTHIC CHESTPLATE")
			),
			"equipment", List.of(
				item("ZORROS_CAPE", "\u00a77Farming Fortune: \u00a76+51.2", "\u00a76\u00a7lLEGENDARY CLOAK")
			),
			"accessories", List.of(item("FERMENTO_ARTIFACT", "\u00a79\u00a7lRARE ACCESSORY"), item("MAGIC_8_BALL")),
			"inventory", List.<InventoryDecoder.Stack>of()
		);
		Map<String, Integer> milestones = new java.util.HashMap<>();
		for (String crop : FarmingFortuneEstimate.CROPS) {
			milestones.put(crop, 46);
		}
		Garden garden = new Garden(true, 24, 15, Map.of("Cactus", 9), milestones, 80L);
		var in = new FarmingFortuneEstimate.Inputs(true, true, true, List.of(knife), Set.of(), false, 60, 15, 210, 210, 0, 20,
			Map.of("ultimate_dna", 10, "lunar_power", 10), true, Map.of("Cactus", 2_000_000L), List.of(
				new PetSnapshot.Entry("ROSE_DRAGON", "Rose Dragon", "LEGENDARY", 0, "ROSE_DRAGON", 200, 200, 0, 0, 0, 0, 0f,
					false, "YELLOW_BANDANA", 0, "", "", 0),
				pet("HEDGEHOG", "LEGENDARY", 100, "")
			));
		var est = FarmingFortuneEstimate.from(in, all);

		double shown = sum(est.permanentLines(garden)) + est.armourTotal() + est.equipmentTotal()
			+ est.bestPet("Cactus", garden).total() + sum(est.cropLines("Cactus", garden));
		assertEquals(shown, est.forCrop("Cactus", garden), EPS);
		assertEquals("the toolkit knife is the Cactus tool line", 239D, est.cropLines("Cactus", garden).get(0).value(), EPS);
		assertEquals("ROSE_DRAGON", est.bestPet("Cactus", garden).pet().type());
		var temporary = est.temporary("Cactus", garden, 0L);
		assertTrue("Lunar Moth shard sits outside the total",
			temporary.stream().anyMatch(s -> s.label().equals("Lunar Moth shard") && s.value() == 50D));
		assertTrue(temporary.stream().anyMatch(s -> s.label().equals("Magic 8 Ball") && s.value() == 25D));
		assertFalse("no tool or pet line inside the permanent list",
			est.permanentLines(garden).stream().anyMatch(l -> l.label().contains("tool") || l.label().contains("Pet")));
	}

	@Test
	public void cropshotChipScalesWithRarity() {
		assertEquals(30D, FarmingFortuneEstimate.cropshotFortune(10), EPS);
		assertEquals(60D, FarmingFortuneEstimate.cropshotFortune(15), EPS);
		assertEquals(100D, FarmingFortuneEstimate.cropshotFortune(20), EPS);
	}

	@Test
	public void onlyTheHighestFermentoAccessoryCounts() {
		var squash = item("SQUASH_RING", "\u00a77Wheat Fortune: \u00a76+20", "\u00a7a\u00a7lUNCOMMON ACCESSORY");
		var fermento = item("FERMENTO_ARTIFACT", "\u00a77Wheat Fortune: \u00a76+30", "\u00a79\u00a7lRARE ACCESSORY");
		var est = FarmingFortuneEstimate.from(empty(), Map.of("accessories", List.of(squash, fermento)));
		assertTrue(est.cropLines("Wheat", GARDEN).stream().anyMatch(l -> l.label().equals("Fermento Artifact") && l.value() == 30D));
		assertFalse(est.cropLines("Sunflower", GARDEN).stream().anyMatch(l -> l.label().equals("Fermento Artifact")));
		assertTrue("family lines are not double counted as accessories", est.accessories().isEmpty());

		var relic = FarmingFortuneEstimate.from(empty(), Map.of("accessories", List.of(fermento, item("HELIANTHUS_RELIC", "\u00a79\u00a7lRARE ACCESSORY"))));
		assertTrue(relic.permanentLines(GARDEN).stream().anyMatch(l -> l.label().equals("Helianthus Relic") && l.value() == 40D));
	}

	@Test
	public void setPiecesWinWhenTheirBonusOutweighsABetterStatLine() {
		var all = Map.of("wardrobe", List.of(
			item("FERMENTO_HELMET", "\u00a77Farming Fortune: \u00a76+40", "\u00a76\u00a7lLEGENDARY HELMET"),
			item("RANCHERS_HELMET", "\u00a77Farming Fortune: \u00a76+50", "\u00a76\u00a7lLEGENDARY HELMET"),
			item("FERMENTO_CHESTPLATE", "\u00a77Farming Fortune: \u00a76+40", "\u00a76\u00a7lLEGENDARY CHESTPLATE")
		));
		var est = FarmingFortuneEstimate.from(empty(), all);
		assertEquals("FERMENTO_HELMET", est.armour().get(0).stack().id());
		assertEquals(40D + 40D + 25D, est.armourTotal(), EPS);
	}

	@Test
	public void dayAndNightShardsCountTheLowerLevelAndListTheRest() {
		var in = new FarmingFortuneEstimate.Inputs(true, true, true, List.of(), Set.of(), false, 0, 0, 0, 0, 0, 0,
			Map.of("ultimate_dna", 10, "solar_power", 4, "lunar_power", 9), false, Map.of(), List.of());
		List<Line> lines = new ArrayList<>(FarmingFortuneEstimate.from(in, Map.of()).permanentLines(GARDEN));
		assertTrue(lines.stream().anyMatch(l -> l.label().equals("Galaxy Fish shard") && l.value() == 10D));
		Line both = lines.stream().filter(l -> l.label().equals("Firefly and Lunar Moth shards")).findFirst().orElseThrow();
		assertEquals("Firefly 4 by day, Lunar Moth 9 by night: level 4 is always active", 20D, both.value(), EPS);
		assertEquals(Kind.COUNTED, both.kind());
		assertFalse(lines.stream().anyMatch(l -> l.label().equals("Lunar Moth shard")));
		var moth = FarmingFortuneEstimate.from(in, Map.of()).temporary(null, GARDEN, 0L).stream()
			.filter(s -> s.label().equals("Lunar Moth shard")).findFirst().orElseThrow();
		assertEquals(25D, moth.value(), EPS);
		assertEquals(FarmingFortuneTemporary.Status.UNKNOWN, moth.status());
	}

	// Raw member fields from the profile compared against EliteBot's General Fortune.
	static final String ELITE_COMPARISON_MEMBER = """
		{
		  "bestiary": {"kills": {
		    "pest_beetle_1": 1726, "pest_cricket_1": 1793, "pest_worm_1": 1632, "pest_dragonfly_1": 2001,
		    "pest_mouse_1": 356, "pest_fly_1": 1702, "pest_locust_1": 7690, "pest_lunar_moth_1": 100,
		    "pest_mite_1": 2858, "pest_mosquito_1": 1611, "pest_moth_1": 1740, "pest_praying_mantis_1": 3095,
		    "pest_firefly_1": 1876, "pest_rat_1": 1983, "pest_slug_1": 18364,
		    "timestalk_clone_100": 2, "zombuddy_1": 186, "zombie_1": 5000
		  }},
		  "objectives": {"tutorial": [
		    "dna_analysis_rewardfarming_fortune_1", "dna_analysis_rewardfarming_fortune_2",
		    "dna_analysis_rewardfarming_fortune_3", "dna_analysis_rewardfarming_fortune_4",
		    "dna_analysis_rewardfarming_fortune_5", "dna_analysis_rewardfarming_fortune_6", "barn_visitor"
		  ]},
		  "jacobs_contest": {"perks": {"double_drops": 15}},
		  "player_data": {"garden_chips": {"cropshot": 20, "overdrive": 20, "sowledge": 20}}
		}
		""";

	@Test
	public void readsGardenBestiaryAndDnaMilestoneFromTheRawProfile() {
		JsonObject member = com.google.gson.JsonParser.parseString(ELITE_COMPARISON_MEMBER).getAsJsonObject();
		var in = FarmingFortuneEstimate.inputs(member);
		assertEquals("15 maxed pests", 225, in.pestTiers());
		assertEquals("pests plus Zombuddy 15 and Timestalk Clone 2", 242, in.gardenTiers());
		assertEquals(6, in.dnaMilestone());
		assertEquals(15, in.anitaLevel());
		assertEquals(20, in.cropshotLevel());
		assertEquals(0, FarmingFortuneEstimate.dnaMilestone(new JsonObject()));
	}

	@Test
	public void generalFortuneReconcilesWithEliteBotSourceBySource() {
		JsonObject member = com.google.gson.JsonParser.parseString(ELITE_COMPARISON_MEMBER).getAsJsonObject();
		var raw = FarmingFortuneEstimate.inputs(member);
		// Shard counts 96 / 24 / 32 / 32 / 64 / 64 are all level 10 in NEU's attribute_levelling table.
		Map<String, Integer> shards = Map.of("fortunate_farmer", 10, "ultimate_dna", 10, "solar_power", 10,
			"lunar_power", 10, "infiltration", 10, "filter_upgrade", 10);
		var in = new FarmingFortuneEstimate.Inputs(true, true, true, List.of(), Set.of(), false, 60, raw.anitaLevel(),
			raw.pestTiers(), raw.gardenTiers(), raw.dnaMilestone(), raw.cropshotLevel(), shards, false, Map.of(), List.of());
		var est = FarmingFortuneEstimate.from(in, Map.of("accessories", List.of(
			item("HELIANTHUS_RELIC", "\u00a77Farming Fortune: \u00a7a+40"),
			item("MAGIC_8_BALL", "\u00a77+25 Farming Fortune"),
			item("ATMOSPHERIC_FILTER", "\u00a77Grants \u00a76+25 Farming Fortune\u00a77."),
			item("FARMING_TALISMAN", "\u00a77Farming Fortune: \u00a7a+3")
		)));
		List<Line> lines = est.permanentLines(GARDEN);
		Map<String, Double> counted = new java.util.LinkedHashMap<>();
		for (Line l : lines) {
			assertEquals(l.label(), Kind.COUNTED, l.kind());
			counted.put(l.label(), l.value());
		}

		assertEquals(240D, counted.get("Farming level"), EPS);
		assertEquals(60D, counted.get("Anita bonus"), EPS);
		assertEquals(72D, counted.get("Unlocked plots"), EPS);
		assertEquals(96.8D, counted.get("Garden bestiary"), EPS);
		assertEquals(30D, counted.get("DNA Analysis milestone"), EPS);
		assertEquals(100D, counted.get("Cropshot chip"), EPS);
		assertEquals(25D, counted.get("Fly shard"), EPS);
		assertEquals(10D, counted.get("Galaxy Fish shard"), EPS);
		assertEquals(50D, counted.get("Firefly and Lunar Moth shards"), EPS);
		assertEquals("Farming Talisman, which EliteBot doesn't count", 3D, counted.get("Accessories"), EPS);
		assertEquals(40D, counted.get("Helianthus Relic"), EPS);
		assertEquals(726.8D, est.permanentTotal(GARDEN), EPS);

		Map<String, Double> temporary = new java.util.LinkedHashMap<>();
		for (var s : est.temporary(null, GARDEN, 0L)) {
			temporary.put(s.label(), s.value());
		}
		assertEquals(30D, temporary.get("Earthworm shard"), EPS);
		assertEquals(25D, temporary.get("Magic 8 Ball"), EPS);
		assertEquals("25 with +20% from Mite shard 10, Spring only", 30D, temporary.get("Atmospheric Filter"), EPS);
		double elite = temporary.get("Earthworm shard") + temporary.get("Magic 8 Ball") + temporary.get("Atmospheric Filter");

		assertEquals("EliteBot's 808.8 list is these plus its three situational sources, without the talisman",
			808.8D, est.permanentTotal(GARDEN) - 3D + elite, EPS);
	}

	@Test
	public void classifiesSlotsToolsAndFamilies() {
		assertEquals("BOOTS", FarmingFortuneEstimate.slot(List.of("\u00a7d\u00a7l\u00a7ka\u00a7r \u00a7d\u00a7lMYTHIC BOOTS \u00a7d\u00a7l\u00a7ka")));
		assertEquals("", FarmingFortuneEstimate.slot(List.of("\u00a76\u00a7lLEGENDARY ACCESSORY")));
		assertEquals(List.of("Sugar Cane"), FarmingFortuneEstimate.toolCrops("THEORETICAL_HOE_CANE_3"));
		assertEquals(List.of("Cocoa Beans"), FarmingFortuneEstimate.toolCrops("COCO_CHOPPER_2"));
		assertTrue(FarmingFortuneEstimate.toolCrops("BASIC_GARDENING_HOE").isEmpty());
		assertEquals("HELIANTHUS", FarmingFortuneEstimate.family("HELIANTHUS_LEGGINGS"));
		assertNull(FarmingFortuneEstimate.family("RANCHERS_BOOTS"));
		assertEquals(15D, FarmingFortuneEstimate.pieceBonus(List.of("\u00a77Piece Bonus: \u00a76+15\u2618", "\u00a7a\u00a7lMAXED OUT! NICE!")), EPS);
	}
}
