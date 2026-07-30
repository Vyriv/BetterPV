package dev.vy.betterpv.client.networth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.price.HypixelItemsCache;
import dev.vy.betterpv.client.price.ItemPricer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * SkyHelper-style item valuation (base + modifiers) using {@link ItemPricer}.
 */
public final class ItemWorth {
	private static final Map<String, UpgradeEnchant> ENCHANT_UPGRADES = Map.of(
		"SCAVENGER", new UpgradeEnchant("GOLDEN_BOUNTY", 6),
		"PESTERMINATOR", new UpgradeEnchant("PESTHUNTING_GUIDE", 6),
		"LUCK_OF_THE_SEA", new UpgradeEnchant("GOLD_BOTTLE_CAP", 7),
		"PISCARY", new UpgradeEnchant("TROUBLED_BUBBLE", 7),
		"FRAIL", new UpgradeEnchant("SEVERED_PINCER", 7),
		"SPIKED_HOOK", new UpgradeEnchant("OCTOPUS_TENDRIL", 7),
		"CHARM", new UpgradeEnchant("CHAIN_END_TIMES", 6),
		"VENOMOUS", new UpgradeEnchant("FATEFUL_STINGER", 7)
	);

	private ItemWorth() {
	}

	public static double value(InventoryDecoder.Stack stack) {
		return value(stack, true);
	}

	public static double value(InventoryDecoder.Stack stack, boolean includeCosmetics) {
		if (stack == null || stack.id() == null) {
			return 0;
		}
		if (!includeCosmetics && isCosmeticItem(stack)) {
			return 0;
		}
		// Simple stacks (sacks / essence): id × count only
		CompoundTag ea = stack.extraAttributes();
		if (ea == null || ea.isEmpty()) {
			return ItemPricer.price(stack.id()) * stack.count();
		}

		JsonObject skyblockItem = HypixelItemsCache.get(stack.id());
		String itemId = resolveItemId(stack, skyblockItem, includeCosmetics);
		double base = ItemPricer.price(itemId) * stack.count();
		double mods = 0;

		mods += potatoBooks(ea);
		mods += recomb(stack, skyblockItem, ea);
		mods += enchantments(stack, ea);
		mods += enchantedBook(stack, ea);
		mods += masterStars(skyblockItem, ea);
		mods += essenceStars(skyblockItem, ea);
		mods += gemstones(stack, skyblockItem, ea);
		mods += scrolls(ea);
		mods += additive(ea, "art_of_war_count", "THE_ART_OF_WAR", NetworthData.worth("artOfWar", 0.6));
		mods += additive(ea, "artOfPeaceApplied", "THE_ART_OF_PEACE", NetworthData.worth("artOfPeace", 0.8));
		mods += flag(ea, "ethermerge", "ETHERWARP_CONDUIT", NetworthData.worth("etherwarp", 1));
		mods += additive(ea, "tuned_transmission", "TRANSMISSION_TUNER", NetworthData.worth("tunedTransmission", 0.7));
		mods += additive(ea, "wood_singularity_count", "WOOD_SINGULARITY", NetworthData.worth("woodSingularity", 0.5));
		mods += additive(ea, "divan_powder_coating", "DIVAN_POWDER_COATING", NetworthData.worth("divanPowderCoating", 0.8));
		mods += additive(ea, "farming_for_dummies_count", "FARMING_FOR_DUMMIES", NetworthData.worth("farmingForDummies", 0.5));
		mods += additive(ea, "jalapeno_count", "JALAPENO_BOOK", NetworthData.worth("jalapenoBook", 0.8));
		mods += additive(ea, "mana_disintegrator", "MANA_DISINTEGRATOR", NetworthData.worth("manaDisintegrator", 0.8));
		mods += additive(ea, "polarvoid", "POLARVOID_BOOK", NetworthData.worth("polarvoidBook", 1));
		mods += enrichment(ea);
		mods += reforge(stack, skyblockItem, ea);
		mods += drillParts(ea);
		mods += rodParts(ea);
		if (includeCosmetics) {
			mods += dye(ea);
			mods += runeOnItem(stack, ea);
			mods += soulboundSkin(stack, ea);
		}

		return Math.max(0, base + mods);
	}

	/** Standalone cosmetic items (skins, dyes, furniture, etc.) - zeroed when cosmetics off. */
	private static boolean isCosmeticItem(InventoryDecoder.Stack stack) {
		String id = stack.id().toUpperCase(Locale.ROOT);
		if (id.contains("_SKIN") || id.startsWith("PET_SKIN_") || id.endsWith("_DYE") || id.contains("DYE_")) {
			return true;
		}
		if ("RUNE".equals(id) || "UNIQUE_RUNE".equals(id) || id.startsWith("RUNE_")) {
			return true;
		}
		JsonObject hypixel = HypixelItemsCache.get(id);
		if (hypixel != null && hypixel.has("category") && hypixel.get("category").isJsonPrimitive()) {
			String cat = hypixel.get("category").getAsString();
			if ("COSMETIC".equalsIgnoreCase(cat)) {
				return true;
			}
		}
		return false;
	}

	private static String resolveItemId(InventoryDecoder.Stack stack, JsonObject skyblockItem, boolean includeCosmetics) {
		String itemId = stack.id();
		CompoundTag ea = stack.extraAttributes();
		String skin = NbtAttrs.string(ea, "skin");
		if (includeCosmetics && skin != null && !skin.isBlank()) {
			String skinned = itemId + "_SKINNED_" + skin;
			if (ItemPricer.price(skinned) > ItemPricer.price(itemId)) {
				return skinned;
			}
		}
		if ("RUNE".equals(itemId) || "UNIQUE_RUNE".equals(itemId)) {
			CompoundTag runes = NbtAttrs.compound(ea, "runes");
			if (runes != null && !runes.isEmpty()) {
				String type = runes.keySet().iterator().next();
				int tier = NbtAttrs.intValue(runes, type, 1);
				return ("RUNE_" + type + "_" + tier).toUpperCase(Locale.ROOT);
			}
		}
		if ("NEW_YEAR_CAKE".equals(itemId)) {
			return "NEW_YEAR_CAKE_" + NbtAttrs.intValue(ea, "new_years_cake", 0);
		}
		if (includeCosmetics && NbtAttrs.has(ea, "is_shiny") && ItemPricer.price(itemId + "_SHINY") > 0) {
			return itemId + "_SHINY";
		}
		if (itemId.startsWith("STARRED_") && ItemPricer.price(itemId) <= 0) {
			String unstarred = itemId.substring("STARRED_".length());
			if (ItemPricer.price(unstarred) > 0) {
				return unstarred;
			}
		}
		return itemId;
	}

	private static double potatoBooks(CompoundTag ea) {
		int count = NbtAttrs.intValue(ea, "hot_potato_count", 0);
		if (count <= 0) {
			return 0;
		}
		int hot = Math.min(count, 10);
		double total = ItemPricer.price("HOT_POTATO_BOOK") * hot * NetworthData.worth("hotPotatoBook", 1);
		if (count > 10) {
			int fuming = count - 10;
			total += ItemPricer.price("FUMING_POTATO_BOOK") * fuming * NetworthData.worth("fumingPotatoBook", 0.6);
		}
		return total;
	}

	private static double recomb(InventoryDecoder.Stack stack, JsonObject skyblockItem, CompoundTag ea) {
		int upgrades = NbtAttrs.intValue(ea, "rarity_upgrades", 0);
		if (upgrades <= 0 || NbtAttrs.has(ea, "item_tier")) {
			return 0;
		}
		boolean hasEnchants = !NbtAttrs.intMap(ea, "enchantments").isEmpty();
		String category = skyblockItem != null && skyblockItem.has("category")
			? skyblockItem.get("category").getAsString()
			: "";
		boolean allows = NetworthData.allowedRecombCategories().contains(category)
			|| NetworthData.allowedRecombIds().contains(stack.id());
		String lastLore = stack.lore().isEmpty() ? "" : stack.lore().get(stack.lore().size() - 1);
		boolean accessory = lastLore.contains("ACCESSORY") || lastLore.contains("HATCESSORY");
		if (!(hasEnchants || allows || accessory)) {
			return 0;
		}
		double mult = NetworthData.worth("recombobulator", 0.8);
		if ("BONE_BOOMERANG".equals(stack.id())) {
			mult *= 0.5;
		}
		return ItemPricer.price("RECOMBOBULATOR_3000") * mult;
	}

	private static double enchantments(InventoryDecoder.Stack stack, CompoundTag ea) {
		if ("ENCHANTED_BOOK".equals(stack.id())) {
			return 0;
		}
		Map<String, Integer> enchants = NbtAttrs.intMap(ea, "enchantments");
		if (enchants.isEmpty()) {
			return 0;
		}
		double total = 0;
		Map<String, List<String>> blocked = NetworthData.blockedEnchants();
		Map<String, Integer> ignored = NetworthData.ignoredEnchants();
		List<String> stacking = NetworthData.stackingEnchants();
		List<String> ignoreSilex = NetworthData.ignoreSilex();
		for (var entry : enchants.entrySet()) {
			String name = entry.getKey().toUpperCase(Locale.ROOT);
			int value = entry.getValue();
			List<String> blockedForItem = blocked.get(stack.id());
			if (blockedForItem != null && blockedForItem.contains(name)) {
				continue;
			}
			if (ignored.getOrDefault(name, Integer.MIN_VALUE) == value) {
				continue;
			}
			if (stacking.contains(name)) {
				value = 1;
			}
			if ("EFFICIENCY".equals(name) && value >= 6 && !ignoreSilex.contains(stack.id())) {
				int efficiencyLevel = value - ("STONK_PICKAXE".equals(stack.id()) ? 6 : 5);
				if (efficiencyLevel > 0) {
					total += ItemPricer.price("SIL_EX") * efficiencyLevel * NetworthData.worth("silex", 0.75);
				}
			}
			UpgradeEnchant upgrade = ENCHANT_UPGRADES.get(name);
			if (upgrade != null && value >= upgrade.tier()) {
				total += ItemPricer.price(upgrade.item()) * NetworthData.worth("enchantmentUpgrades", 0.8);
			}
			double mult = NetworthData.enchantWorth(name, NetworthData.worth("enchantments", 0.85));
			total += ItemPricer.price("ENCHANTMENT_" + name + "_" + value) * mult;
		}
		return total;
	}

	private static double enchantedBook(InventoryDecoder.Stack stack, CompoundTag ea) {
		if (!"ENCHANTED_BOOK".equals(stack.id())) {
			return 0;
		}
		Map<String, Integer> enchants = NbtAttrs.intMap(ea, "enchantments");
		if (enchants.isEmpty()) {
			return 0;
		}
		boolean single = enchants.size() == 1;
		double total = 0;
		for (var entry : enchants.entrySet()) {
			String name = entry.getKey().toUpperCase(Locale.ROOT);
			int value = entry.getValue();
			double mult = single ? 1.0 : NetworthData.worth("enchantments", 0.85);
			mult = NetworthData.enchantWorth(name, mult);
			total += ItemPricer.price("ENCHANTMENT_" + name + "_" + value) * mult;
		}
		return total;
	}

	private static int upgradeLevel(CompoundTag ea) {
		String dungeon = String.valueOf(NbtAttrs.intValue(ea, "dungeon_item_level", 0));
		String upgrade = String.valueOf(NbtAttrs.intValue(ea, "upgrade_level", 0));
		int d = Integer.parseInt(dungeon.replaceAll("\\D", "").isEmpty() ? "0" : dungeon.replaceAll("\\D", ""));
		int u = Integer.parseInt(upgrade.replaceAll("\\D", "").isEmpty() ? "0" : upgrade.replaceAll("\\D", ""));
		return Math.max(d, u);
	}

	private static double masterStars(JsonObject skyblockItem, CompoundTag ea) {
		int level = upgradeLevel(ea);
		if (level <= 5) {
			return 0;
		}
		JsonArray costs = skyblockItem != null && skyblockItem.has("upgrade_costs")
			? skyblockItem.getAsJsonArray("upgrade_costs")
			: null;
		if (costs != null && costs.size() > 5) {
			return 0;
		}
		int starsUsed = Math.min(level - 5, 5);
		List<String> stars = NetworthData.masterStars();
		double total = 0;
		for (int i = 0; i < starsUsed && i < stars.size(); i++) {
			total += ItemPricer.price(stars.get(i)) * NetworthData.worth("masterStar", 1);
		}
		return total;
	}

	private static double essenceStars(JsonObject skyblockItem, CompoundTag ea) {
		if (skyblockItem == null || !skyblockItem.has("upgrade_costs")) {
			return 0;
		}
		int level = upgradeLevel(ea);
		if (level <= 0) {
			return 0;
		}
		JsonArray costs = skyblockItem.getAsJsonArray("upgrade_costs");
		double total = 0;
		for (int i = 0; i < Math.min(level, costs.size()); i++) {
			JsonElement step = costs.get(i);
			if (step.isJsonArray()) {
				for (JsonElement cost : step.getAsJsonArray()) {
					total += starCost(cost.getAsJsonObject());
				}
			} else if (step.isJsonObject()) {
				total += starCost(step.getAsJsonObject());
			}
		}
		return total;
	}

	private static double starCost(JsonObject upgrade) {
		if (upgrade == null) {
			return 0;
		}
		int amount = upgrade.has("amount") ? upgrade.get("amount").getAsInt() : 0;
		if (upgrade.has("essence_type")) {
			String type = upgrade.get("essence_type").getAsString().toUpperCase(Locale.ROOT);
			return ItemPricer.price("ESSENCE_" + type) * amount * NetworthData.worth("essence", 0.75);
		}
		if (upgrade.has("item_id")) {
			return ItemPricer.price(upgrade.get("item_id").getAsString()) * amount;
		}
		return 0;
	}

	private static double gemstones(InventoryDecoder.Stack stack, JsonObject skyblockItem, CompoundTag ea) {
		CompoundTag gems = NbtAttrs.compound(ea, "gems");
		if (gems == null || gems.isEmpty()) {
			return 0;
		}
		double total = 0;
		for (String key : gems.keySet()) {
			if ("unlocked_slots".equals(key) || key.endsWith("_gem")) {
				continue;
			}
			String tier;
			Tag value = gems.get(key);
			if (value instanceof CompoundTag compound) {
				tier = NbtAttrs.string(compound, "quality");
			} else {
				tier = NbtAttrs.string(gems, key);
			}
			if (tier == null || tier.isBlank()) {
				continue;
			}
			String type = NbtAttrs.string(gems, key + "_gem");
			if (type == null || type.isBlank()) {
				int underscore = key.indexOf('_');
				type = underscore > 0 ? key.substring(0, underscore) : key;
			}
			String id = (tier + "_" + type + "_GEM").toUpperCase(Locale.ROOT);
			total += ItemPricer.price(id) * NetworthData.worth("gemstone", 1);
		}
		return total;
	}

	private static double scrolls(CompoundTag ea) {
		double total = 0;
		for (String id : NbtAttrs.stringList(ea, "ability_scroll")) {
			total += ItemPricer.price(id.toUpperCase(Locale.ROOT)) * NetworthData.worth("necronBladeScroll", 1);
		}
		return total;
	}

	private static double additive(CompoundTag ea, String key, String itemId, double mult) {
		int count = NbtAttrs.intValue(ea, key, 0);
		if (count <= 0) {
			return 0;
		}
		return ItemPricer.price(itemId) * count * mult;
	}

	private static double flag(CompoundTag ea, String key, String itemId, double mult) {
		if (!NbtAttrs.has(ea, key)) {
			return 0;
		}
		return ItemPricer.price(itemId) * mult;
	}

	private static double enrichment(CompoundTag ea) {
		String enrichment = NbtAttrs.string(ea, "talisman_enrichment");
		if (enrichment == null || enrichment.isBlank()) {
			return 0;
		}
		double min = Double.POSITIVE_INFINITY;
		for (String id : NetworthData.enrichments()) {
			double price = ItemPricer.price(id);
			if (price > 0) {
				min = Math.min(min, price);
			}
		}
		if (!Double.isFinite(min)) {
			return 0;
		}
		return min * NetworthData.worth("enrichment", 0.5);
	}

	private static double reforge(InventoryDecoder.Stack stack, JsonObject skyblockItem, CompoundTag ea) {
		String modifier = NbtAttrs.string(ea, "modifier");
		if (modifier == null || modifier.isBlank()) {
			return 0;
		}
		String category = skyblockItem != null && skyblockItem.has("category")
			? skyblockItem.get("category").getAsString()
			: "";
		if ("ACCESSORY".equals(category)) {
			return 0;
		}
		String item = NetworthData.reforgeItem(modifier);
		if (item == null) {
			return 0;
		}
		return ItemPricer.price(item) * NetworthData.worth("reforge", 1);
	}

	private static double drillParts(CompoundTag ea) {
		double total = 0;
		for (String key : List.of("drill_part_upgrade_module", "drill_part_fuel_tank", "drill_part_engine")) {
			String part = NbtAttrs.string(ea, key);
			if (part != null && !part.isBlank()) {
				total += ItemPricer.price(part.toUpperCase(Locale.ROOT)) * NetworthData.worth("drillPart", 1);
			}
		}
		return total;
	}

	private static double rodParts(CompoundTag ea) {
		double total = 0;
		for (String key : List.of("line", "hook", "sinker")) {
			CompoundTag part = NbtAttrs.compound(ea, key);
			if (part == null) {
				continue;
			}
			String id = NbtAttrs.string(part, "part");
			if (id != null && !id.isBlank()) {
				total += ItemPricer.price(id.toUpperCase(Locale.ROOT)) * NetworthData.worth("rodPart", 1);
			}
		}
		return total;
	}

	private static double dye(CompoundTag ea) {
		String dye = NbtAttrs.string(ea, "dye_item");
		if (dye == null || dye.isBlank()) {
			return 0;
		}
		return ItemPricer.price(dye) * NetworthData.worth("dye", 0.9);
	}

	private static double runeOnItem(InventoryDecoder.Stack stack, CompoundTag ea) {
		if (stack.id().startsWith("RUNE")) {
			return 0;
		}
		CompoundTag runes = NbtAttrs.compound(ea, "runes");
		if (runes == null || runes.isEmpty()) {
			return 0;
		}
		String type = runes.keySet().iterator().next();
		int tier = NbtAttrs.intValue(runes, type, 1);
		String id = ("RUNE_" + type + "_" + tier).toUpperCase(Locale.ROOT);
		return ItemPricer.price(id) * NetworthData.worth("runes", 0.6);
	}

	private static double soulboundSkin(InventoryDecoder.Stack stack, CompoundTag ea) {
		String skin = NbtAttrs.string(ea, "skin");
		if (skin == null || skin.isBlank() || !stack.soulbound()) {
			return 0;
		}
		if (stack.id().contains(skin)) {
			return 0;
		}
		return ItemPricer.price(skin) * NetworthData.worth("soulboundSkins", 0.8);
	}

	private record UpgradeEnchant(String item, int tier) {
	}
}
