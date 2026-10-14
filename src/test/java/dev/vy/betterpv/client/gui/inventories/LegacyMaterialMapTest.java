package dev.vy.betterpv.client.gui.inventories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;

public final class LegacyMaterialMapTest {
	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	// 26.2 groups coloured blocks into ColorCollections, so compare registry ids instead of Items constants.
	private static String path(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	@Test
	public void neuPetKeyWithRarityDoesNotThrow() {
		// Pets page fallback before the NEU json arrives: no Hypixel def, so material is null.
		assertSame(Items.PAPER, LegacyMaterialMap.resolveMaterial(null, "BLACK_CAT;4", 0));
		assertSame(Items.PAPER, LegacyMaterialMap.resolveMaterial(null, "black_cat;4", 0));
		assertSame(Items.PAPER, LegacyMaterialMap.resolveMaterial(null, "BLUE_WHALE;5", 0));
		assertSame(Items.PAPER, LegacyMaterialMap.resolveMaterial(null, "PINK_PONY;0", 0));
	}

	@Test
	public void neuSuffixIsDroppedNotUsedAsDamage() {
		assertEquals("red_wool", path(LegacyMaterialMap.resolveMaterial(null, "RED_WOOL;4", 0)));
		assertEquals("white_wool", path(LegacyMaterialMap.resolveMaterial(null, "WHITE_WOOL-14", 0)));
		assertEquals("light_blue_stained_glass_pane", path(LegacyMaterialMap.resolveMaterial(null, "LIGHT_BLUE_STAINED_GLASS_PANE", 0)));
	}

	@Test
	public void legacyMaterialWithDurabilityField() {
		assertEquals("red_stained_glass_pane", path(LegacyMaterialMap.resolveMaterial("STAINED_GLASS_PANE", "X", 14)));
		assertEquals("blue_terracotta", path(LegacyMaterialMap.resolveMaterial("STAINED_CLAY", "X", 11)));
		assertSame(Items.LAPIS_LAZULI, LegacyMaterialMap.resolveMaterial("INK_SACK", "X", 4));
		assertSame(Items.INK_SAC, LegacyMaterialMap.resolveMaterial("INK_SACK", "X", 0));
		assertSame(Items.CLOCK, LegacyMaterialMap.resolveMaterial("WATCH", "X", 0));
		assertSame(Items.DIAMOND_SWORD, LegacyMaterialMap.resolveMaterial("DIAMOND_SWORD", "X", 0));
	}

	@Test
	public void bukkitColonDataAndNamespacedMaterials() {
		assertEquals("red_wool", path(LegacyMaterialMap.resolveMaterial("WOOL:14", "X", 0)));
		assertSame(Items.LAPIS_LAZULI, LegacyMaterialMap.resolveMaterial("INK_SACK:4", "X", 0));
		assertSame(Items.DIAMOND_SWORD, LegacyMaterialMap.resolveMaterial("minecraft:diamond_sword", "X", 0));
		assertEquals("red_wool", path(LegacyMaterialMap.resolveMaterial("minecraft:wool:14", "X", 0)));
		assertSame(Items.DIAMOND_SWORD, LegacyMaterialMap.resolveMaterial("Diamond Sword", "X", 0));

		LegacyMaterialMap.Material skull = LegacyMaterialMap.parseMaterial("SKULL_ITEM:3", 0);
		assertEquals("SKULL_ITEM", skull.name());
		assertEquals(3, skull.damage());
		assertEquals(14, LegacyMaterialMap.parseMaterial("WOOL:14", 3).damage());
		assertEquals(3, LegacyMaterialMap.parseMaterial("WOOL", 3).damage());
	}

	@Test
	public void malformedMaterialsAreRejected() {
		for (String raw : Arrays.asList(
			null, "", "   ", "black_cat;4", "BLACK_CAT;4", "WOOL:", "WOOL:abc", "WOOL:-1", "WOOL:123456",
			"foo:bar", "minecraft:", ":", "A:B:C", "\u00a7cSTONE", "stone?", "STONE#1", "st\u00f6ne"
		)) {
			assertNull(String.valueOf(raw), LegacyMaterialMap.parseMaterial(raw, 0));
			assertSame(String.valueOf(raw), Items.PAPER, LegacyMaterialMap.resolveMaterial(raw, "UNKNOWN_THING", 0));
		}
	}

	@Test
	public void malformedMaterialFallsBackToTheSkyblockId() {
		assertEquals("red_wool", path(LegacyMaterialMap.resolveMaterial("black_cat;4", "RED_WOOL", 0)));
		assertSame(Items.DIAMOND_SWORD, LegacyMaterialMap.resolveMaterial("WOOL:abc", "ASPECT_OF_THE_SWORD", 0));
	}

	@Test
	public void vanillaLookupNeverThrows() {
		assertFalse(LegacyMaterialMap.vanillaItem("black_cat;4").isPresent());
		assertFalse(LegacyMaterialMap.vanillaItem("minecraft:black_cat;4").isPresent());
		assertFalse(LegacyMaterialMap.vanillaItem("upper case").isPresent());
		assertFalse(LegacyMaterialMap.vanillaItem("foo:stone").isPresent());
		assertFalse(LegacyMaterialMap.vanillaItem(null).isPresent());
		assertTrue(LegacyMaterialMap.vanillaItem("minecraft:stone").isPresent());
		assertTrue(LegacyMaterialMap.vanillaItem("STONE").isPresent());
	}

	@Test
	public void neuItemIdPathStaysSafe() {
		assertSame(Items.PAPER, LegacyMaterialMap.resolveItemId("black_cat;4", 0));
		assertSame(Items.PAPER, LegacyMaterialMap.resolveItemId("minecraft:black_cat;4", 4));
		assertSame(Items.LAPIS_LAZULI, LegacyMaterialMap.resolveItemId("dye", 4));
	}
}
