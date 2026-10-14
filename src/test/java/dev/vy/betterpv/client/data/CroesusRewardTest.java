package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;

public class CroesusRewardTest {
	// Trimmed copies of the NEU repo entries.
	private static final Map<String, JsonObject> NEU = Map.of(
		"ULTIMATE_WISE;1", book("ULTIMATE_WISE;1", "\u00a7d\u00a7lUltimate Wise I", "\u00a7f\u00a7lCOMMON"),
		"ULTIMATE_BANK;1", book("ULTIMATE_BANK;1", "\u00a7d\u00a7lBank I", "\u00a7f\u00a7lCOMMON"),
		"FEATHER_FALLING;6", book("FEATHER_FALLING;6", "\u00a79Feather Falling VI", "\u00a79\u00a7lRARE"),
		"DYE_NECRON", JsonParser.parseString("""
			{"internalname": "DYE_NECRON", "displayname": "\u00a76Necron Dye",
			 "lore": ["\u00a78Combinable in Anvil", "", "\u00a77Changes the color of an armor piece", "\u00a75\u00a7lEPIC DYE"]}
			""").getAsJsonObject()
	);
	private static final Function<String, JsonObject> HYPIXEL = id -> id.equals("RECOMBOBULATOR_3000")
		? JsonParser.parseString("{\"name\": \"Recombobulator 3000\", \"tier\": \"LEGENDARY\"}").getAsJsonObject()
		: null;
	private static final Function<String, JsonObject> NEU_LOOKUP = NEU::get;

	private static JsonObject book(String id, String enchantLine, String rarity) {
		JsonObject o = new JsonObject();
		o.addProperty("internalname", id);
		o.addProperty("displayname", "\u00a7fEnchanted Book");
		var lore = new com.google.gson.JsonArray();
		lore.add("\u00a78Combinable in Anvil");
		lore.add("");
		lore.add(enchantLine);
		lore.add(rarity);
		o.add("lore", lore);
		return o;
	}

	private static String name(String raw) {
		return DungeonChestHistory.prettyReward(raw, HYPIXEL, NEU_LOOKUP);
	}

	private static String tier(String raw) {
		return DungeonChestHistory.rewardTier(raw, HYPIXEL, NEU_LOOKUP);
	}

	@Test
	public void ultimateWiseKeepsUltimateWithOrWithoutPrefix() {
		assertEquals("Ultimate Wise I", name("ultimate_wise_1"));
		assertEquals("Ultimate Wise I", name("wise_1"));
		assertEquals("ULTIMATE", tier("ultimate_wise_1"));
	}

	@Test
	public void otherUltimatesUseTheirRealName() {
		assertEquals("Bank I", name("ultimate_bank_1"));
		assertEquals("ULTIMATE", tier("bank_1"));
	}

	@Test
	public void ultimateFallbackWithoutNeuData() {
		Function<String, JsonObject> none = id -> null;
		assertEquals("Ultimate Jerry I", DungeonChestHistory.prettyReward("ultimate_jerry_1", none, none));
		assertEquals("Ultimate Jerry I", DungeonChestHistory.prettyReward("master_jerry_1", none, none));
		assertEquals("ULTIMATE", DungeonChestHistory.rewardTier("ultimate_rend_1", none, none));
	}

	@Test
	public void normalEnchantsStayNormal() {
		assertEquals("Feather Falling VI", name("feather_falling_6"));
		assertEquals("BOOK", tier("feather_falling_6"));
		assertEquals("Infinite Quiver VI", name("infinite_quiver_6"));
		assertEquals("BOOK", tier("infinite_quiver_6"));
	}

	@Test
	public void dyesResolveToTheirSkyBlockItem() {
		assertEquals("DYE_NECRON", DungeonChestHistory.itemIdFor("necron_dye"));
		assertEquals("Necron Dye", name("necron_dye"));
		assertEquals("EPIC", tier("necron_dye"));
	}

	@Test
	public void itemsAndEssenceUnchanged() {
		assertEquals("Recombobulator 3000", name("recombobulator_3000"));
		assertEquals("LEGENDARY", tier("recombobulator_3000"));
		assertEquals("22x Undead Essence", name("ESSENCE:UNDEAD:22"));
		assertEquals("ESSENCE", tier("ESSENCE:UNDEAD:22"));
	}
}
