package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.KuudraGearChecks.Check;
import dev.vy.betterpv.client.data.KuudraGearChecks.CheckState;
import dev.vy.betterpv.client.data.KuudraGearChecks.Entry;
import dev.vy.betterpv.client.data.KuudraGearChecks.Group;
import dev.vy.betterpv.client.data.KuudraGearChecks.Owned;
import dev.vy.betterpv.client.data.KuudraGearChecks.Result;
import dev.vy.betterpv.client.data.KuudraGearChecks.Status;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.Test;

public class KuudraGearChecksTest {
	@Test
	public void duplexTerminatorUsesReiterateIdAndFlagsPiercing() {
		Result r = compute(items(owned(item("TERMINATOR", null, null, 5,
			"ultimate_reiterate", 5, "cubism", 6, "smoldering", 5, "power", 7, "piercing", 1))));
		Group duplex = group(r.items(), "Duplex Terminator");
		assertEquals(Status.COMPLETE, duplex.status());
		Entry e = duplex.entries().get(0);
		assertEquals("Duplex Terminator (1)", e.name());
		assertEquals(CheckState.WARN, check(e, "Piercing").state());
		assertTrue(check(e, "Cubism").highlight());
		assertEquals(CheckState.OK, check(e, "Power").state());
		assertEquals(Status.MISSING, group(r.items(), "Rend Terminator").status());
	}

	@Test
	public void belowMaxEnchantsAreYellowUpgradesNotMissing() {
		Result r = compute(items(owned(item("TERMINATOR", null, null, 0, "ultimate_rend", 3, "power", 6))));
		Entry e = group(r.items(), "Rend Terminator").entries().get(0);
		assertEquals(CheckState.UPGRADE, check(e, "Power").state());
		assertEquals("VII", check(e, "Power").upgradeTo());
		assertEquals(CheckState.UPGRADE, check(e, "Rend").state());
		assertEquals(CheckState.UPGRADE, check(e, "Cubism").state());
		assertEquals("VI", check(e, "Cubism").upgradeTo());
		assertEquals(Status.INCOMPLETE, e.status());
		assertEquals(Status.INCOMPLETE, group(r.items(), "Rend Terminator").status());
	}

	@Test
	public void missingEssentialUltimateIsRedButItemIsStillListed() {
		Result r = compute(items(owned(item("RAGNAROCK_AXE", null, null, 0, "power", 5))));
		Group rag = group(r.items(), "Ragnarock");
		Entry e = rag.entries().get(0);
		assertEquals(CheckState.MISSING, check(e, "Chimera").state());
		assertEquals(Status.MISSING, e.status());
		assertEquals(Status.INCOMPLETE, rag.status());
	}

	@Test
	public void otherTerminatorsAreInformational() {
		Result r = compute(items(owned(item("TERMINATOR", null, null, 0, "ultimate_soul_eater", 5))));
		Group other = group(r.items(), "Other Terminators");
		assertTrue(other.informational());
		assertEquals("Soul Eater V", check(other.entries().get(0), "Ultimate").value());
		assertEquals(Status.MISSING, group(r.items(), "Duplex Terminator").status());
	}

	@Test
	public void witherBladesAreNumberedPerCopy() {
		Result r = compute(items(
			owned(item("HYPERION", "a", "heroic", 5, "ultimate_wise", 5)),
			owned(item("HYPERION", "b", null, 0))
		));
		Group blades = group(r.items(), "Wither Blades");
		assertEquals(2, blades.owned());
		assertEquals("Hyperion (1)", blades.entries().get(0).name());
		assertEquals("Hyperion (2)", blades.entries().get(1).name());
		assertEquals("Ultimate Wise V", check(blades.entries().get(0), "Ultimate").value());
		assertEquals(CheckState.UPGRADE, check(blades.entries().get(1), "Ultimate").state());
		assertEquals(Status.COMPLETE, blades.status());
	}

	@Test
	public void collectDedupesByUuidAcrossSources() {
		InventoryDecoder.Stack blade = item("HYPERION", "same", null, 0);
		Map<String, List<InventoryDecoder.Stack>> cats = new LinkedHashMap<>();
		cats.put("equipment", List.of(blade, blade));
		cats.put("inventory", List.of(blade, item("SOS_FLARE", null, null, 0)));
		cats.put("sacks", List.of(item("TERMINATOR", null, null, 0)));
		List<Owned> owned = KuudraGearChecks.collect(cats);
		assertEquals(2, owned.size());
		assertEquals("Equipment", owned.get(0).source());
		assertEquals("Inventory", owned.get(1).source());
	}

	@Test
	public void lastBreathNeedsExactlyDuplexOne() {
		Result r = compute(items(
			owned(item("LAST_BREATH", "a", null, 0, "ultimate_reiterate", 2)),
			owned(item("LAST_BREATH", "b", null, 0, "ultimate_reiterate", 1))
		));
		Group lb = group(r.items(), "Last Breath (Duplex I)");
		assertEquals("II, needs I", check(lb.entries().get(0), "Duplex").value());
		assertEquals(CheckState.MISSING, check(lb.entries().get(0), "Duplex").state());
		assertEquals(CheckState.OK, check(lb.entries().get(1), "Duplex").state());
		assertEquals(Status.COMPLETE, lb.status());
	}

	@Test
	public void swarmAotsAndRendBonemerangCheckEveryEnchant() {
		Result r = compute(items(
			owned(item("AXE_OF_THE_SHREDDED", null, null, 0, "ultimate_swarm", 5, "cubism", 6,
				"giant_killer", 7, "prosecute", 6, "smoldering", 5, "tabasco", 3)),
			owned(item("BONE_BOOMERANG", null, null, 0, "ultimate_rend", 5, "cubism", 6, "power", 7,
				"smoldering", 5, "piercing", 1))
		));
		assertEquals(Status.COMPLETE, group(r.items(), "Swarm AOTS").status());
		Group bone = group(r.items(), "Rend Bonemerang");
		assertEquals(Status.INCOMPLETE, bone.status());
		assertEquals(CheckState.UPGRADE, check(bone.entries().get(0), "Tabasco").state());
		assertEquals("I", check(bone.entries().get(0), "Piercing").value());
	}

	@Test
	public void bonemerangWithoutRendIsListedWithRedReason() {
		Result r = compute(items(owned(item("BONE_BOOMERANG", null, null, 0, "power", 7))));
		Group bone = group(r.items(), "Rend Bonemerang");
		assertEquals(Status.INCOMPLETE, bone.status());
		assertEquals(CheckState.MISSING, check(bone.entries().get(0), "Rend").state());
	}

	@Test
	public void goldenDragonCopiesKeepTheirOwnMetadata() {
		PetSnapshot.Entry maxed = pet(200, "PET_ITEM_MINOS_RELIC", 10);
		PetSnapshot.Entry low = pet(102, "", 0);
		Result r = compute(new KuudraGearChecks.Inputs(List.of(), true, List.of(maxed, low), true, List.of(), "",
			id -> id, s -> "", id -> id));
		Group gd = group(r.items(), "Golden Dragon");
		assertEquals(2, gd.owned());
		assertNull(check(gd.entries().get(0), "Level").upgradeTo());
		assertEquals("10 / 10", check(gd.entries().get(0), "Candies").value());
		assertEquals(CheckState.UPGRADE, check(gd.entries().get(1), "Level").state());
		assertEquals("200", check(gd.entries().get(1), "Level").upgradeTo());
		assertEquals("None", check(gd.entries().get(1), "Pet item").value());
	}

	@Test
	public void inventoryApiOffIsUnknownNotMissing() {
		Result r = compute(new KuudraGearChecks.Inputs(List.of(), false, List.of(), false, List.of(), null,
			id -> id, s -> "", id -> id));
		for (Group g : r.items()) {
			assertEquals(g.title(), Status.UNKNOWN, g.status());
		}
		for (Group g : r.armour()) {
			assertEquals(g.title(), Status.UNKNOWN, g.status());
			for (Check c : g.checks()) {
				assertEquals(CheckState.UNKNOWN, c.state());
			}
		}
	}

	@Test
	public void stunShowsOneRepresentativeToolAndTheSelectedAbility() {
		List<Owned> tools = items(
			owned(item("MITHRIL_PICKAXE", "1", null, 0), "UNCOMMON PICKAXE"),
			owned(item("TITANIUM_DRILL_4", "2", null, 0), "LEGENDARY DRILL"),
			owned(item("DUNGEONBREAKER", "3", null, 0), "MYTHIC PICKAXE"),
			owned(item("PICKONIMBUS", "4", null, 0), "EPIC PICKAXE")
		);
		Group stun = group(compute(new KuudraGearChecks.Inputs(tools, true, List.of(), true, List.of(),
			"sheer_force", id -> id, KuudraGearChecksTest::toolCategory, id -> "Sheer Force")).items(), "Stun");
		assertEquals(2, stun.entries().size());
		Entry tool = stun.entries().get(0);
		assertEquals("Owned", tool.value());
		assertEquals("TITANIUM_DRILL_4", tool.stack().id());
		Entry ability = stun.entries().get(1);
		assertEquals("Sheer Force", ability.value());
		assertEquals(Status.MISSING, ability.status());
		assertEquals("Pickobulus", ability.checks().get(0).value());
		assertEquals(Status.INCOMPLETE, stun.status());

		Group ready = group(compute(new KuudraGearChecks.Inputs(tools, true, List.of(), true, List.of(),
			"pickaxe_toss", id -> id, KuudraGearChecksTest::toolCategory, id -> "Pickobulus")).items(), "Stun");
		assertEquals(Status.COMPLETE, ready.status());
		Group unknownHotm = group(compute(new KuudraGearChecks.Inputs(tools, true, List.of(), true, List.of(),
			null, id -> id, KuudraGearChecksTest::toolCategory, id -> id)).items(), "Stun");
		assertEquals(Status.UNKNOWN, unknownHotm.status());
	}

	@Test
	public void crownWarnsOnlyForTerrorWardrobeSets() {
		InventoryDecoder.Stack crown = item("CROWN_OF_AVARICE", "c", null, 0);
		InventoryDecoder.Stack chest = item("INFERNAL_TERROR_CHESTPLATE", "t", "ancient", 0);
		List<InventoryDecoder.WardrobePreset> presets = List.of(
			new InventoryDecoder.WardrobePreset(1, false, Arrays.asList(crown, null, null, null)),
			new InventoryDecoder.WardrobePreset(3, true, Arrays.asList(crown, chest, null, null))
		);
		Result r = compute(new KuudraGearChecks.Inputs(items(owned(crown), owned(chest)), true, List.of(), true,
			presets, "", id -> id, s -> "", id -> id));
		Group g = group(r.armour(), "Crown of Avarice");
		assertEquals(Status.INCOMPLETE, g.status());
		assertEquals(1, g.entries().size());
		assertEquals("Wardrobe slot 3", g.entries().get(0).name());
		assertEquals("Crown of Avarice in Terror set", g.entries().get(0).value());
		assertEquals("Warden Helmet preferred", g.entries().get(0).checks().get(0).value());
		assertEquals(CheckState.WARN, g.entries().get(0).checks().get(0).state());

		Result noPresets = compute(new KuudraGearChecks.Inputs(items(owned(crown), owned(chest)), true, List.of(),
			true, List.of(), "", id -> id, s -> "", id -> id));
		assertEquals(Status.UNKNOWN, group(noPresets.armour(), "Crown of Avarice").status());
	}

	@Test
	public void dpsAndRcmEachFindTheirOwnTerrorAcrossWardrobeSlots() {
		Map<String, List<InventoryDecoder.Stack>> cats = new LinkedHashMap<>();
		cats.put("wardrobe", List.of(
			item("WARDEN_HELMET", "w", "ancient", 5),
			item("INFERNAL_TERROR_CHESTPLATE", "d1", "ancient", 10),
			item("INFERNAL_TERROR_LEGGINGS", "d2", "ancient", 10),
			item("INFERNAL_TERROR_BOOTS", "d3", "ancient", 10),
			item("WITHER_GOGGLES", "g", "necrotic", 5),
			item("INFERNAL_TERROR_CHESTPLATE", "r1", "necrotic", 8),
			item("INFERNAL_TERROR_LEGGINGS", "r2", "loving", 8),
			item("INFERNAL_TERROR_BOOTS", "r3", "necrotic", 8)
		));
		Result r = compute(KuudraGearChecks.collect(cats));

		Group dps = group(r.armour(), "DPS Terror");
		assertEquals(Status.COMPLETE, dps.status());
		assertEquals(4, dps.entries().size());
		assertEquals("Warden Helmet", row(dps, "Helmet").value());
		for (String slot : List.of("Chestplate", "Leggings", "Boots")) {
			Entry e = row(dps, slot);
			assertEquals("Infernal Terror", e.value());
			assertEquals("Ancient", check(e, "Reforge").value());
			assertTrue(uuid(e).startsWith("d"));
		}

		Group rcm = group(r.armour(), "RCM");
		assertEquals(Status.COMPLETE, rcm.status());
		assertEquals("Wither Goggles", row(rcm, "Helmet").value());
		for (String slot : List.of("Chestplate", "Leggings", "Boots")) {
			Entry e = row(rcm, slot);
			assertEquals("Infernal Terror", e.value());
			assertTrue(uuid(e).startsWith("r"));
		}
		assertEquals("Loving", check(row(rcm, "Leggings"), "Reforge").value());
	}

	@Test
	public void ancientTerrorNeverCountsAsRcm() {
		Result r = compute(items(
			owned(item("INFERNAL_TERROR_CHESTPLATE", "1", "ancient", 0)),
			owned(item("INFERNAL_TERROR_LEGGINGS", "2", "ancient", 0))
		));
		Group rcm = group(r.armour(), "RCM");
		assertEquals(Status.MISSING, rcm.status());
		assertEquals("Not found", row(rcm, "Chestplate").value());
		assertEquals(Status.MISSING, row(rcm, "Chestplate").status());
	}

	@Test
	public void lowerTierRcmPiecesAreRecognisedWithTierUpgrade() {
		Result r = compute(items(owned(item("FIERY_TERROR_CHESTPLATE", "1", "necrotic", 0))));
		Entry chest = row(group(r.armour(), "RCM"), "Chestplate");
		assertEquals("Fiery Terror", chest.value());
		assertEquals(Status.INCOMPLETE, chest.status());
		assertEquals("Infernal", check(chest, "Tier").upgradeTo());
	}

	@Test
	public void dpsPicksBestPieceAndMarksMissingSlotsRed() {
		Result r = compute(items(
			owned(item("FIERY_TERROR_CHESTPLATE", "1", "ancient", 0)),
			owned(item("INFERNAL_TERROR_CHESTPLATE", "2", "fierce", 0)),
			owned(item("INFERNAL_TERROR_CHESTPLATE", "3", "ancient", 4))
		));
		Group dps = group(r.armour(), "DPS Terror");
		Entry chest = row(dps, "Chestplate");
		assertEquals("3", uuid(chest));
		assertEquals(Status.COMPLETE, chest.status());
		assertEquals(Status.MISSING, row(dps, "Leggings").status());
		assertEquals(Status.MISSING, row(dps, "Helmet").status());
		assertEquals(Status.INCOMPLETE, dps.status());

		Entry wrongReforge = row(group(compute(items(owned(item("INFERNAL_TERROR_BOOTS", "9", "fierce", 0))))
			.armour(), "DPS Terror"), "Boots");
		assertEquals(Status.INCOMPLETE, wrongReforge.status());
		assertEquals("Ancient", check(wrongReforge, "Reforge").upgradeTo());
	}

	@Test
	public void reaperIsOptionalWithPlainTitle() {
		Group reaper = group(compute(items()).armour(), "Reaper");
		assertTrue(reaper.informational());
		assertFalse(reaper.entries().isEmpty());
		for (Group g : compute(items()).armour()) {
			assertFalse(g.title(), g.title().toLowerCase().contains("info"));
		}
	}

	@Test
	public void missingArmourSlotsSayNotFound() {
		Result r = compute(items());
		for (Group g : r.armour()) {
			for (Entry e : g.entries()) {
				if (e.stack() == null && e.status() == Status.MISSING) {
					assertEquals(g.title() + " " + e.name(), "Not found", e.value());
				}
			}
		}
		assertEquals("Not found", group(r.items(), "Stun").entries().get(0).value());
	}

	@Test
	public void hollowWandDoesNotAffectHollowCompletion() {
		List<Owned> armour = items(
			owned(item("INFERNAL_HOLLOW_HELMET", "1", null, 0)),
			owned(item("INFERNAL_HOLLOW_CHESTPLATE", "2", null, 0)),
			owned(item("INFERNAL_HOLLOW_LEGGINGS", "3", null, 0)),
			owned(item("INFERNAL_HOLLOW_BOOTS", "4", null, 0))
		);
		Group without = group(compute(armour).armour(), "Hollow");
		assertEquals(Status.COMPLETE, without.status());
		assertEquals(4, without.entries().size());
		armour.add(owned(item("HOLLOW_WAND", "5", null, 0)));
		Group with = group(compute(armour).armour(), "Hollow");
		assertEquals(Status.COMPLETE, with.status());
		assertTrue(with.entries().stream().noneMatch(e -> e.name().equals("Wand")));
	}

	@Test
	public void crownAloneOrOutsideTerrorSetsIsNotAWarning() {
		InventoryDecoder.Stack crown = item("CROWN_OF_AVARICE", "c", null, 0);
		InventoryDecoder.Stack chest = item("INFERNAL_TERROR_CHESTPLATE", "t", "ancient", 0);
		InventoryDecoder.Stack aurora = item("INFERNAL_AURORA_CHESTPLATE", "a", null, 0);
		List<InventoryDecoder.WardrobePreset> presets = List.of(
			new InventoryDecoder.WardrobePreset(1, false, Arrays.asList(crown, aurora, null, null)),
			new InventoryDecoder.WardrobePreset(2, false, Arrays.asList(null, chest, null, null))
		);
		Group g = group(compute(new KuudraGearChecks.Inputs(items(owned(crown), owned(chest), owned(aurora)), true,
			List.of(), true, presets, "", id -> id, s -> "", id -> id)).armour(), "Crown of Avarice");
		assertEquals(Status.COMPLETE, g.status());
		assertTrue(g.entries().isEmpty());
		assertTrue(g.checks().stream().noneMatch(c -> c.state() == CheckState.WARN));
	}

	private static Result compute(List<Owned> items) {
		return KuudraGearChecks.compute(new KuudraGearChecks.Inputs(items, true, List.of(), true, List.of(), "",
			KuudraGearChecksTest::name, s -> "", id -> id));
	}

	private static Result compute(KuudraGearChecks.Inputs in) {
		return KuudraGearChecks.compute(in);
	}

	private static String toolCategory(InventoryDecoder.Stack s) {
		return s.id().contains("DRILL") ? "DRILL" : s.id().contains("PICKAXE") || s.id().equals("DUNGEONBREAKER") || s.id().equals("PICKONIMBUS") ? "PICKAXE" : "";
	}

	private static String name(String id) {
		return switch (id) {
			case "HYPERION" -> "Hyperion";
			default -> id;
		};
	}

	private static Group group(List<Group> groups, String title) {
		for (Group g : groups) {
			if (g.title().equals(title)) {
				return g;
			}
		}
		throw new AssertionError("no group " + title);
	}

	private static Entry row(Group group, String name) {
		for (Entry e : group.entries()) {
			if (e.name().equals(name)) {
				return e;
			}
		}
		throw new AssertionError("no row " + name + " in " + group.title());
	}

	private static Check check(Entry entry, String label) {
		for (Check c : entry.checks()) {
			if (c.label().equals(label)) {
				return c;
			}
		}
		throw new AssertionError("no check " + label + " on " + entry.name());
	}

	private static String uuid(Entry entry) {
		assertNotNull(entry.stack());
		return entry.stack().extraAttributes().getStringOr("uuid", "");
	}

	private static List<Owned> items(Owned... owned) {
		return new ArrayList<>(List.of(owned));
	}

	private static Owned owned(InventoryDecoder.Stack stack) {
		assertNotNull(stack);
		return new Owned(stack, "Inventory");
	}

	private static Owned owned(InventoryDecoder.Stack stack, String rarityLine) {
		return new Owned(new InventoryDecoder.Stack(stack.id(), 1, stack.extraAttributes(), List.of("\u00a77Ability", "", "\u00a76\u00a7l" + rarityLine), false), "Inventory");
	}

	private static InventoryDecoder.Stack item(String id, String uuid, String reforge, int stars, Object... enchants) {
		CompoundTag extra = new CompoundTag();
		extra.putString("id", id);
		if (uuid != null) {
			extra.putString("uuid", uuid);
		}
		if (reforge != null) {
			extra.putString("modifier", reforge);
		}
		if (stars > 0) {
			extra.putInt("upgrade_level", stars);
		}
		if (enchants.length > 0) {
			CompoundTag ench = new CompoundTag();
			for (int i = 0; i + 1 < enchants.length; i += 2) {
				ench.putInt((String) enchants[i], (Integer) enchants[i + 1]);
			}
			extra.put("enchantments", ench);
		}
		return new InventoryDecoder.Stack(id, 1, extra, List.of(), false);
	}

	private static PetSnapshot.Entry pet(int level, String heldItem, int candy) {
		return new PetSnapshot.Entry("GOLDEN_DRAGON", "Golden Dragon", "LEGENDARY", 4, "GOLDEN_DRAGON;4",
			level, 200, 0, 0, 0, 0, 0f, false, heldItem, candy, "", "", 0);
	}
}
