package dev.vy.betterpv.client.networth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.gui.SkyBlockSymbols;
import dev.vy.betterpv.client.neu.NeuRepoCache;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Decodes Hypixel inventory base64 → SkyBlock item stacks for networth. */
public final class InventoryDecoder {
	/** Rank max (18) + Community Center wardrobe/loadout upgrades (9). */
	private static final int WARDROBE_SLOT_COUNT = 27;
	/** Hypixel wardrobe UI shows 9 set columns per page. */
	private static final int WARDROBE_SETS_PER_PAGE = 9;
	private static final int LOADOUT_SLOT_COUNT = 27;

	public record Stack(
		String id,
		int count,
		CompoundTag extraAttributes,
		List<String> lore,
		boolean soulbound,
		String displayName,
		Integer dyeColor,
		String skullValue,
		String skullSignature
	) {
		public Stack(String id, int count, CompoundTag extraAttributes, List<String> lore, boolean soulbound) {
			this(id, count, extraAttributes, lore, soulbound, null, null, null, null);
		}
	}

	private InventoryDecoder() {
	}

	public static Map<String, List<Stack>> parseCategories(JsonObject member, JsonObject museumMember) {
		Map<String, List<Stack>> categories = new LinkedHashMap<>();
		JsonObject inventory = obj(member.get("inventory"));
		if (inventory == null) {
			inventory = obj(member.get("inventories"));
		}
		JsonObject shared = obj(member.get("shared_inventory"));
		JsonObject bags = inventory == null ? null : obj(inventory.get("bag_contents"));

		put(categories, "armor", decodeField(inventory, "inv_armor"));
		put(categories, "equipment", decodeField(inventory, "equipment_contents"));
		put(categories, "inventory", decodeField(inventory, "inv_contents"));
		put(categories, "enderchest", decodeField(inventory, "ender_chest_contents"));
		put(categories, "accessories", bags == null ? List.of() : decodeField(bags, "talisman_bag"));
		put(categories, "personal_vault", decodeField(inventory, "personal_vault_contents"));
		put(categories, "fishing_bag", bags == null ? List.of() : decodeField(bags, "fishing_bag"));
		put(categories, "potion_bag", bags == null ? List.of() : decodeField(bags, "potion_bag"));
		put(categories, "sacks_bag", bags == null ? List.of() : decodeField(bags, "sacks_bag"));
		put(categories, "quiver", bags == null ? List.of() : decodeField(bags, "quiver"));
		put(categories, "candy_inventory", shared == null ? List.of() : decodeField(shared, "candy_inventory_contents"));
		put(categories, "carnival_mask_inventory", shared == null ? List.of() : decodeField(shared, "carnival_mask_inventory_contents"));

		List<Stack> storage = new ArrayList<>();
		JsonObject backpacks = inventory == null ? null : obj(inventory.get("backpack_contents"));
		if (backpacks != null) {
			for (var entry : backpacks.entrySet()) {
				storage.addAll(decodeDataElement(entry.getValue()));
			}
		}
		JsonObject icons = inventory == null ? null : obj(inventory.get("backpack_icons"));
		if (icons != null) {
			for (var entry : icons.entrySet()) {
				storage.addAll(decodeDataElement(entry.getValue()));
			}
		}
		categories.put("storage", storage);

		List<Stack> wardrobe = new ArrayList<>();
		JsonObject loadout = obj(member.get("loadout"));
		JsonObject armorLayouts = loadout == null ? null : obj(loadout.get("armor"));
		if (armorLayouts != null) {
			for (var layout : armorLayouts.entrySet()) {
				JsonObject page = obj(layout.getValue());
				if (page == null) {
					continue;
				}
				for (String slot : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
					wardrobe.addAll(decodeDataElement(page.get(slot)));
				}
			}
		}
		// Legacy wardrobe blob
		if (wardrobe.isEmpty()) {
			wardrobe.addAll(decodeField(inventory, "wardrobe_contents"));
		}
		categories.put("wardrobe", wardrobe);

		List<Stack> equipmentLayouts = new ArrayList<>(categories.getOrDefault("equipment", List.of()));
		JsonObject equipLayouts = loadout == null ? null : obj(loadout.get("equipment"));
		if (equipLayouts != null) {
			for (var layout : equipLayouts.entrySet()) {
				JsonObject page = obj(layout.getValue());
				if (page == null) {
					continue;
				}
				for (String slot : List.of("EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4")) {
					equipmentLayouts.addAll(decodeDataElement(page.get(slot)));
				}
			}
		}
		categories.put("equipment", equipmentLayouts);

		categories.put("museum", parseMuseum(museumMember));
		categories.put("sacks", parseSacks(member, inventory));
		categories.put("essence", parseEssence(member));
		categories.put("pets", List.of()); // pets valued separately from JSON
		return categories;
	}

	/** Structured inventories for the Inventories tab (keeps empty slots / pages). */
	/** Decode a single auction {@code item_bytes} blob into a UI slot (or null). */
	public static InventorySnapshot.Slot slotFromItemBytes(JsonElement itemBytes) {
		List<Stack> decoded = decodeDataElement(itemBytes);
		if (decoded.isEmpty()) {
			return null;
		}
		return toUiSlot(decoded.get(0));
	}

	/** Minimal slot from a SkyBlock item tag (Coflnet history rows). */
	public static InventorySnapshot.Slot slotFromTag(String tag, String displayName) {
		if (tag == null || tag.isBlank()) {
			return null;
		}
		return new InventorySnapshot.Slot(
			tag.toUpperCase(Locale.ROOT),
			1,
			List.of(),
			displayName == null ? "" : displayName,
			null,
			null,
			null
		);
	}

	public static InventorySnapshot parseUi(JsonObject member) {
		if (member == null) {
			return InventorySnapshot.empty();
		}
		JsonObject inventory = obj(member.get("inventory"));
		if (inventory == null) {
			inventory = obj(member.get("inventories"));
		}
		JsonObject bags = inventory == null ? null : obj(inventory.get("bag_contents"));
		JsonObject loadout = obj(member.get("loadout"));

		List<InventorySnapshot.Slot> invSlots = toUiSlots(decodeFieldKeepingEmpty(inventory, "inv_contents", 36));
		List<InventorySnapshot.Slot> armorSlots = toUiSlots(readArmorSlots(member));
		List<InventorySnapshot.Slot> equipSlots = toUiSlots(decodeFieldKeepingEmpty(inventory, "equipment_contents", 4));
		// Player layout: equipment + armor columns left, main 3×9, hotbar bottom.
		// Store as [equip×4][helmet..boots][inv_contents] (Hypixel inv: 0-8 hotbar, 9-35 main).
		List<InventorySnapshot.Slot> combinedInv = new ArrayList<>(44);
		while (equipSlots.size() < 4) {
			equipSlots.add(null);
		}
		combinedInv.addAll(equipSlots.subList(0, 4));
		if (armorSlots.size() >= 4) {
			combinedInv.add(armorSlots.get(3)); // helmet
			combinedInv.add(armorSlots.get(2));
			combinedInv.add(armorSlots.get(1));
			combinedInv.add(armorSlots.get(0)); // boots
		} else {
			for (int i = 0; i < 4; i++) {
				combinedInv.add(i < armorSlots.size() ? armorSlots.get(i) : null);
			}
		}
		while (invSlots.size() < 36) {
			invSlots.add(null);
		}
		combinedInv.addAll(invSlots.subList(0, 36));

		List<InventorySnapshot.Page> enderPages = chunkPages(
			toUiSlots(decodeFieldKeepingEmpty(inventory, "ender_chest_contents", 45)),
			45,
			9,
			"Ender Chest"
		);

		List<InventorySnapshot.Page> backpackPages = new ArrayList<>();
		JsonObject backpacks = inventory == null ? null : obj(inventory.get("backpack_contents"));
		if (backpacks != null) {
			List<String> keys = new ArrayList<>(backpacks.keySet());
			keys.sort(InventoryDecoder::compareKeys);
			for (String key : keys) {
				List<InventorySnapshot.Slot> slots = toUiSlots(decodeDataElementKeepingEmpty(backpacks.get(key), 27));
				Integer num = tryParseInt(key);
				String title = num != null ? "Backpack " + (num + 1) : "Backpack " + key;
				backpackPages.add(new InventorySnapshot.Page(title, slots, 9));
			}
		}
		if (backpackPages.isEmpty()) {
			backpackPages = List.of(InventorySnapshot.emptyPage("Backpacks", 9));
		}

		List<InventorySnapshot.Page> wardrobePages = parseWardrobePages(inventory, loadout);
		List<InventorySnapshot.Page> equipmentPages = parseEquipmentWardrobePages(inventory, loadout);
		InventorySnapshot.AccessoryInfo accessoryInfo = parseAccessoryInfo(member);
		List<InventorySnapshot.Loadout> loadouts = parseNamedLoadouts(member, loadout, accessoryInfo);

		List<InventorySnapshot.Page> sackPages = parseSackPages(member, inventory);

		return new InventorySnapshot(
			new InventorySnapshot.Page("Inventory", combinedInv, 9),
			enderPages.isEmpty() ? List.of(InventorySnapshot.emptyPage("Ender Chest", 9)) : enderPages,
			backpackPages,
			wardrobePages,
			equipmentPages,
			loadouts,
			sackPages,
			bagPage(bags, "fishing_bag", "Fishing Bag"),
			bagPage(bags, "potion_bag", "Potion Bag"),
			bagPage(bags, "quiver", "Quiver"),
			accessoryBagPages(bags),
			accessoryInfo,
			timePocketPage(bags),
			new InventorySnapshot.Page(
				"Personal Vault",
				toUiSlots(decodeFieldKeepingEmpty(inventory, "personal_vault_contents", 27)),
				9
			)
		);
	}

	private static InventorySnapshot.Page bagPage(JsonObject bags, String field, String title) {
		return new InventorySnapshot.Page(title, toUiSlots(decodeFieldKeepingEmpty(bags, field, 27)), 9);
	}

	/** Accessory bag is one flat NBT list; in-game UI pages every 45 slots (9×5). */
	private static List<InventorySnapshot.Page> accessoryBagPages(JsonObject bags) {
		List<InventorySnapshot.Slot> all = toUiSlots(decodeFieldKeepingEmpty(bags, "talisman_bag", 45));
		List<InventorySnapshot.Page> pages = chunkPages(all, 45, 9, "Accessory Bag");
		return pages.isEmpty() ? List.of(InventorySnapshot.emptyPage("Accessory Bag", 9)) : pages;
	}

	private static InventorySnapshot.Page timePocketPage(JsonObject bags) {
		if (bags == null) {
			return InventorySnapshot.emptyPage("Time Pocket", 9);
		}
		for (String key : List.of("time_pocket", "time_bag", "timed_items", "timepocket", "time_pocket_contents")) {
			if (bags.has(key)) {
				return new InventorySnapshot.Page("Time Pocket", toUiSlots(decodeFieldKeepingEmpty(bags, key, 27)), 9);
			}
		}
		// Fallback: any bag key containing "time"
		for (var entry : bags.entrySet()) {
			if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).contains("time")) {
				return new InventorySnapshot.Page("Time Pocket", toUiSlots(decodeDataElementKeepingEmpty(entry.getValue(), 27)), 9);
			}
		}
		return InventorySnapshot.emptyPage("Time Pocket", 9);
	}

	private static List<InventorySnapshot.Page> parseWardrobePages(JsonObject inventory, JsonObject loadout) {
		Map<Integer, WardrobeSet> byId = new LinkedHashMap<>();
		JsonObject armorLayouts = loadout == null ? null : obj(loadout.get("armor"));
		Integer equippedId = armorLayouts == null ? null : jsonInt(armorLayouts, "equipped_set");
		if (armorLayouts != null && !armorLayouts.entrySet().isEmpty()) {
			for (String key : armorLayouts.keySet()) {
				if ("equipped_set".equals(key)) {
					continue;
				}
				Integer setId = layoutSetId(armorLayouts.get(key), key);
				if (setId == null) {
					continue;
				}
				List<InventorySnapshot.Slot> slots = readArmorSet(inventory, armorLayouts, setId, key);
				boolean equipped = equippedId != null && equippedId.equals(setId);
				byId.put(setId, new WardrobeSet(slots, equipped));
			}
		}
		if (byId.isEmpty()) {
			List<InventorySnapshot.Slot> flat = toUiSlots(decodeFieldKeepingEmpty(inventory, "wardrobe_contents", 72));
			for (int i = 0, setId = 0; i + 3 < flat.size(); i += 4, setId++) {
				List<InventorySnapshot.Slot> set = new ArrayList<>(4);
				set.add(flat.get(i));
				set.add(flat.get(i + 1));
				set.add(flat.get(i + 2));
				set.add(flat.get(i + 3));
				byId.put(setId, new WardrobeSet(set, false));
			}
		}
		return packWardrobeSetPages(padWardrobeSets(byId), "Wardrobe");
	}

	/** Armor/equipment sets per page, each set a vertical column. */
	private static List<InventorySnapshot.Page> packWardrobeSetPages(List<WardrobeSet> sets, String titlePrefix) {
		if (sets.isEmpty()) {
			sets = padWardrobeSets(Map.of());
		}
		List<InventorySnapshot.Page> pages = new ArrayList<>();
		final int perPage = WARDROBE_SETS_PER_PAGE;
		for (int start = 0; start < sets.size(); start += perPage) {
			int end = Math.min(sets.size(), start + perPage);
			int count = end - start;
			List<InventorySnapshot.Slot> slots = new ArrayList<>(4 * count);
			int equippedColumn = -1;
			// Row-major by piece so columns=count draws piece rows with sets as columns.
			for (int piece = 0; piece < 4; piece++) {
				for (int col = 0; col < count; col++) {
					WardrobeSet set = sets.get(start + col);
					slots.add(piece < set.slots().size() ? set.slots().get(piece) : null);
					if (piece == 0 && set.equipped()) {
						equippedColumn = col;
					}
				}
			}
			int pageIndex = start / perPage + 1;
			int totalPages = (sets.size() + perPage - 1) / perPage;
			String title = totalPages <= 1 ? titlePrefix : titlePrefix + " " + pageIndex;
			pages.add(new InventorySnapshot.Page(title, slots, count, equippedColumn));
		}
		return pages;
	}

	private static List<InventorySnapshot.Page> parseEquipmentWardrobePages(JsonObject inventory, JsonObject loadout) {
		Map<Integer, WardrobeSet> byId = new LinkedHashMap<>();
		JsonObject equipLayouts = loadout == null ? null : obj(loadout.get("equipment"));
		Integer equippedId = equipLayouts == null ? null : jsonInt(equipLayouts, "equipped_set");
		if (equipLayouts != null) {
			for (String key : equipLayouts.keySet()) {
				if ("equipped_set".equals(key)) {
					continue;
				}
				Integer setId = layoutSetId(equipLayouts.get(key), key);
				if (setId == null) {
					continue;
				}
				List<InventorySnapshot.Slot> slots = readEquipSet(inventory, equipLayouts, setId, key);
				boolean equipped = equippedId != null && equippedId.equals(setId);
				byId.put(setId, new WardrobeSet(slots, equipped));
			}
		}
		if (byId.isEmpty()) {
			List<InventorySnapshot.Slot> equipped = toUiSlots(decodeFieldKeepingEmpty(inventory, "equipment_contents", 4));
			while (equipped.size() < 4) {
				equipped.add(null);
			}
			int idx = equippedId != null ? Math.max(0, Math.min(WARDROBE_SLOT_COUNT - 1, equippedId)) : 0;
			byId.put(idx, new WardrobeSet(new ArrayList<>(equipped.subList(0, 4)), true));
		}
		return packWardrobeSetPages(padWardrobeSets(byId), "Equipment");
	}

	/** Fill every wardrobe slot index (including empty / locked) so page totals stay fixed. */
	private static List<WardrobeSet> padWardrobeSets(Map<Integer, WardrobeSet> byId) {
		List<WardrobeSet> out = new ArrayList<>(WARDROBE_SLOT_COUNT);
		for (int i = 0; i < WARDROBE_SLOT_COUNT; i++) {
			WardrobeSet set = byId == null ? null : byId.get(i);
			out.add(set != null ? set : emptyWardrobeSet());
		}
		return out;
	}

	private static WardrobeSet emptyWardrobeSet() {
		List<InventorySnapshot.Slot> empty = new ArrayList<>(4);
		empty.add(null);
		empty.add(null);
		empty.add(null);
		empty.add(null);
		return new WardrobeSet(empty, false);
	}

	private record WardrobeSet(List<InventorySnapshot.Slot> slots, boolean equipped) {
	}

	private static InventorySnapshot.AccessoryInfo parseAccessoryInfo(JsonObject member) {
		JsonObject storage = obj(member.get("accessory_bag_storage"));
		if (storage == null) {
			return InventorySnapshot.AccessoryInfo.empty();
		}
		int mp = storage.has("highest_magical_power") && storage.get("highest_magical_power").isJsonPrimitive()
			? storage.get("highest_magical_power").getAsInt()
			: 0;
		String power = storage.has("selected_power") && storage.get("selected_power").isJsonPrimitive()
			? storage.get("selected_power").getAsString()
			: "";
		List<InventorySnapshot.TuningTemplate> tunings = new ArrayList<>();
		JsonObject tuning = obj(storage.get("tuning"));
		if (tuning != null) {
			List<String> keys = new ArrayList<>();
			for (String key : tuning.keySet()) {
				if (key != null && key.startsWith("slot_")) {
					keys.add(key);
				}
			}
			keys.sort(InventoryDecoder::compareKeys);
			for (String key : keys) {
				Integer index = tryParseInt(key.substring("slot_".length()));
				if (index == null) {
					continue;
				}
				JsonObject slot = obj(tuning.get(key));
				List<InventorySnapshot.StatPoint> stats = readTuningStats(slot);
				if (!stats.isEmpty() || slot != null) {
					tunings.add(new InventorySnapshot.TuningTemplate(index, stats));
				}
			}
		}
		return new InventorySnapshot.AccessoryInfo(mp, power, tunings);
	}

	private static List<InventorySnapshot.StatPoint> readTuningStats(JsonObject slot) {
		if (slot == null) {
			return List.of();
		}
		List<InventorySnapshot.StatPoint> stats = new ArrayList<>();
		for (var entry : List.of(
			Map.entry("health", "HP"),
			Map.entry("defense", "Def"),
			Map.entry("walk_speed", "Spd"),
			Map.entry("strength", "Str"),
			Map.entry("critical_damage", "CD"),
			Map.entry("critical_chance", "CC"),
			Map.entry("attack_speed", "AS"),
			Map.entry("intelligence", "Int")
		)) {
			if (!slot.has(entry.getKey()) || !slot.get(entry.getKey()).isJsonPrimitive()) {
				continue;
			}
			int value = slot.get(entry.getKey()).getAsInt();
			if (value != 0) {
				stats.add(new InventorySnapshot.StatPoint(entry.getKey(), entry.getValue(), value));
			}
		}
		return stats;
	}

	/**
	 * Named presets under {@code loadout.loadouts}, resolving armor/equipment set ids and pet uniqueId.
	 * Equipped wardrobe sets store pieces in inv_armor / equipment_contents (layout entry is empty).
	 */
	private static List<InventorySnapshot.Loadout> parseNamedLoadouts(
		JsonObject member,
		JsonObject loadout,
		InventorySnapshot.AccessoryInfo accessoryInfo
	) {
		Map<Integer, InventorySnapshot.Loadout> byIndex = new LinkedHashMap<>();
		if (loadout == null) {
			return padLoadouts(byIndex);
		}
		JsonObject inventory = obj(member.get("inventory"));
		if (inventory == null) {
			inventory = obj(member.get("inventories"));
		}
		JsonObject named = obj(loadout.get("loadouts"));
		JsonObject armorLayouts = obj(loadout.get("armor"));
		JsonObject equipLayouts = obj(loadout.get("equipment"));
		Map<String, JsonObject> petsByUuid = indexPets(member);

		if (named != null && !named.entrySet().isEmpty()) {
			for (String key : named.keySet()) {
				JsonObject entry = obj(named.get(key));
				if (entry == null) {
					continue;
				}
				Integer index = tryParseInt(key);
				if (index == null) {
					index = byIndex.size();
				}
				String name = entry.has("name") && entry.get("name").isJsonPrimitive()
					? entry.get("name").getAsString()
					: "Loadout " + (index + 1);
				Integer armorId = jsonInt(entry, "armor_set_id");
				Integer equipId = jsonInt(entry, "equipment_set_id");
				List<InventorySnapshot.Slot> armor = readArmorSet(inventory, armorLayouts, armorId, null);
				List<InventorySnapshot.Slot> equip = readEquipSet(inventory, equipLayouts, equipId, null);
				String power = entry.has("power_stone") && entry.get("power_stone").isJsonPrimitive()
					? entry.get("power_stone").getAsString()
					: "";
				Integer tuneSlot = jsonInt(entry, "tuning_points_slot");
				List<InventorySnapshot.StatPoint> tuning = resolveTuning(accessoryInfo, tuneSlot);
				String petUuid = entry.has("pet") && entry.get("pet").isJsonPrimitive()
					? entry.get("pet").getAsString()
					: "";
				JsonObject petJson = petUuid.isBlank() ? null : petsByUuid.get(normalizeUuid(petUuid));
				InventorySnapshot.Slot petSlot = petSlot(petJson);
				String petLabel = petLabel(petJson);
				byIndex.put(index, new InventorySnapshot.Loadout(
					name, equip, armor, power, tuneSlot, tuning, petSlot, petLabel
				));
			}
		}

		// Fallback: wardrobe set keys if named presets are empty.
		if (byIndex.isEmpty()) {
			LinkedHashMap<String, Boolean> keys = new LinkedHashMap<>();
			if (armorLayouts != null) {
				for (String key : armorLayouts.keySet()) {
					if (!"equipped_set".equals(key)) {
						keys.put(key, true);
					}
				}
			}
			if (equipLayouts != null) {
				for (String key : equipLayouts.keySet()) {
					if (!"equipped_set".equals(key)) {
						keys.put(key, true);
					}
				}
			}
			for (String key : keys.keySet()) {
				Integer id = tryParseInt(key);
				if (id == null) {
					continue;
				}
				byIndex.put(id, new InventorySnapshot.Loadout(
					"Loadout " + (id + 1),
					readEquipSet(inventory, equipLayouts, id, key),
					readArmorSet(inventory, armorLayouts, id, key),
					"",
					null,
					List.of(),
					null,
					""
				));
			}
		}
		return padLoadouts(byIndex);
	}

	/** Keep empty / locked loadout slots so the pager shows the full page total. */
	private static List<InventorySnapshot.Loadout> padLoadouts(Map<Integer, InventorySnapshot.Loadout> byIndex) {
		List<InventorySnapshot.Loadout> out = new ArrayList<>(LOADOUT_SLOT_COUNT);
		for (int i = 0; i < LOADOUT_SLOT_COUNT; i++) {
			InventorySnapshot.Loadout loadout = byIndex == null ? null : byIndex.get(i);
			out.add(loadout != null ? loadout : emptyLoadout(i));
		}
		return out;
	}

	private static InventorySnapshot.Loadout emptyLoadout(int index) {
		List<InventorySnapshot.Slot> emptyEquip = new ArrayList<>(4);
		List<InventorySnapshot.Slot> emptyArmor = new ArrayList<>(4);
		for (int i = 0; i < 4; i++) {
			emptyEquip.add(null);
			emptyArmor.add(null);
		}
		return new InventorySnapshot.Loadout(
			"Loadout " + (index + 1),
			emptyEquip,
			emptyArmor,
			"",
			null,
			List.of(),
			null,
			""
		);
	}

	private static List<InventorySnapshot.StatPoint> resolveTuning(InventorySnapshot.AccessoryInfo info, Integer slot) {
		if (info == null || slot == null) {
			return List.of();
		}
		for (InventorySnapshot.TuningTemplate template : info.tunings()) {
			if (template.slot() == slot) {
				return template.stats();
			}
		}
		return List.of();
	}

	private static Integer layoutSetId(JsonElement element, String key) {
		JsonObject page = obj(element);
		if (page != null && page.has("id") && page.get("id").isJsonPrimitive()) {
			try {
				return page.get("id").getAsInt();
			} catch (Exception ignored) {
			}
		}
		return tryParseInt(key);
	}

	private static List<InventorySnapshot.Slot> readArmorSet(
		JsonObject inventory,
		JsonObject layouts,
		Integer id,
		String keyHint
	) {
		JsonObject page = findLayout(layouts, id, keyHint);
		List<InventorySnapshot.Slot> slots = new ArrayList<>(4);
		for (String slot : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
			List<Stack> decoded = page == null ? List.of() : decodeDataElement(page.get(slot));
			slots.add(decoded.isEmpty() ? null : toUiSlot(decoded.get(0)));
		}
		Integer equippedId = layouts == null ? null : jsonInt(layouts, "equipped_set");
		boolean equipped = id != null && equippedId != null && id.equals(equippedId);
		if (equipped && slots.stream().allMatch(s -> s == null || s.isEmpty())) {
			return liveArmorHelmetToBoots(inventory);
		}
		return slots;
	}

	private static List<InventorySnapshot.Slot> readEquipSet(
		JsonObject inventory,
		JsonObject layouts,
		Integer id,
		String keyHint
	) {
		JsonObject page = findLayout(layouts, id, keyHint);
		List<InventorySnapshot.Slot> slots = new ArrayList<>(4);
		for (String slot : List.of("EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4")) {
			List<Stack> decoded = page == null ? List.of() : decodeDataElement(page.get(slot));
			slots.add(decoded.isEmpty() ? null : toUiSlot(decoded.get(0)));
		}
		Integer equippedId = layouts == null ? null : jsonInt(layouts, "equipped_set");
		boolean equipped = id != null && equippedId != null && id.equals(equippedId);
		if (equipped && slots.stream().allMatch(s -> s == null || s.isEmpty())) {
			List<InventorySnapshot.Slot> live = toUiSlots(decodeFieldKeepingEmpty(inventory, "equipment_contents", 4));
			while (live.size() < 4) {
				live.add(null);
			}
			return new ArrayList<>(live.subList(0, 4));
		}
		return slots;
	}

	/** inv_armor is boots→helmet; loadout/wardrobe columns are helmet→boots. */
	private static List<InventorySnapshot.Slot> liveArmorHelmetToBoots(JsonObject inventory) {
		List<InventorySnapshot.Slot> raw = toUiSlots(decodeFieldKeepingEmpty(inventory, "inv_armor", 4));
		while (raw.size() < 4) {
			raw.add(null);
		}
		List<InventorySnapshot.Slot> out = new ArrayList<>(4);
		out.add(raw.get(3));
		out.add(raw.get(2));
		out.add(raw.get(1));
		out.add(raw.get(0));
		return out;
	}

	private static JsonObject findLayout(JsonObject layouts, Integer id, String keyHint) {
		if (layouts == null) {
			return null;
		}
		if (keyHint != null && layouts.has(keyHint)) {
			return obj(layouts.get(keyHint));
		}
		if (id != null) {
			if (layouts.has(String.valueOf(id))) {
				return obj(layouts.get(String.valueOf(id)));
			}
			for (var entry : layouts.entrySet()) {
				JsonObject page = obj(entry.getValue());
				if (page != null && page.has("id") && page.get("id").isJsonPrimitive() && page.get("id").getAsInt() == id) {
					return page;
				}
			}
		}
		return null;
	}

	private static Map<String, JsonObject> indexPets(JsonObject member) {
		Map<String, JsonObject> map = new LinkedHashMap<>();
		JsonObject petsData = obj(member.get("pets_data"));
		JsonArray pets = null;
		if (petsData != null && petsData.has("pets") && petsData.get("pets").isJsonArray()) {
			pets = petsData.getAsJsonArray("pets");
		} else if (member.has("pets") && member.get("pets").isJsonArray()) {
			pets = member.getAsJsonArray("pets");
		}
		if (pets == null) {
			return map;
		}
		for (JsonElement element : pets) {
			JsonObject pet = obj(element);
			if (pet == null) {
				continue;
			}
			// Loadouts reference uniqueId; some payloads also expose uuid.
			if (pet.has("uniqueId") && pet.get("uniqueId").isJsonPrimitive()) {
				map.put(normalizeUuid(pet.get("uniqueId").getAsString()), pet);
			}
			if (pet.has("uuid") && pet.get("uuid").isJsonPrimitive()) {
				map.put(normalizeUuid(pet.get("uuid").getAsString()), pet);
			}
		}
		return map;
	}

	private static InventorySnapshot.Slot petSlot(JsonObject pet) {
		if (pet == null || !pet.has("type")) {
			return null;
		}
		String type = pet.get("type").getAsString();
		String tier = pet.has("tier") && pet.get("tier").isJsonPrimitive() ? pet.get("tier").getAsString() : "COMMON";
		String label = petLabel(pet);
		List<String> lore = new ArrayList<>();
		if (!tier.isBlank()) {
			lore.add(prettyWords(tier));
		}
		if (pet.has("heldItem") && pet.get("heldItem").isJsonPrimitive()) {
			lore.add(pet.get("heldItem").getAsString());
		}
		// NEU pet items are TYPE;tierIndex (COMMON=0 … MYTHIC=5).
		String neuId = type.toUpperCase(Locale.ROOT) + ";" + petTierIndex(tier);
		return new InventorySnapshot.Slot(neuId, 1, lore, label, null, null, null);
	}

	private static String petLabel(JsonObject pet) {
		if (pet == null || !pet.has("type")) {
			return "";
		}
		return prettyWords(pet.get("type").getAsString());
	}

	private static int petTierIndex(String tier) {
		if (tier == null) {
			return 0;
		}
		return switch (tier.toUpperCase(Locale.ROOT)) {
			case "COMMON" -> 0;
			case "UNCOMMON" -> 1;
			case "RARE" -> 2;
			case "EPIC" -> 3;
			case "LEGENDARY" -> 4;
			case "MYTHIC" -> 5;
			default -> 4;
		};
	}

	private static Integer jsonInt(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) {
			return null;
		}
		try {
			return obj.get(key).getAsInt();
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String normalizeUuid(String uuid) {
		return uuid == null ? "" : uuid.replace("-", "").toLowerCase(Locale.ROOT);
	}

	public static String prettyWords(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String[] parts = raw.toLowerCase(Locale.ROOT).replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1));
			}
		}
		return sb.toString();
	}

	private static List<InventorySnapshot.Page> chunkPages(List<InventorySnapshot.Slot> slots, int pageSize, int columns, String titlePrefix) {
		if (slots.isEmpty()) {
			return List.of(InventorySnapshot.emptyPage(titlePrefix, columns));
		}
		int totalPages = (slots.size() + pageSize - 1) / pageSize;
		List<InventorySnapshot.Page> pages = new ArrayList<>();
		for (int start = 0; start < slots.size(); start += pageSize) {
			int end = Math.min(slots.size(), start + pageSize);
			int pageIndex = start / pageSize + 1;
			String title = totalPages <= 1 ? titlePrefix : titlePrefix + " " + pageIndex;
			pages.add(new InventorySnapshot.Page(title, slots.subList(start, end), columns));
		}
		return pages;
	}

	private static List<InventorySnapshot.Slot> toUiSlots(List<Stack> stacks) {
		List<InventorySnapshot.Slot> out = new ArrayList<>(stacks.size());
		for (Stack stack : stacks) {
			out.add(toUiSlot(stack));
		}
		return out;
	}

	private static InventorySnapshot.Slot toUiSlot(Stack stack) {
		if (stack == null) {
			return null;
		}
		return new InventorySnapshot.Slot(
			stack.id(),
			stack.count(),
			stack.lore(),
			stack.displayName(),
			stack.dyeColor(),
			stack.skullValue(),
			stack.skullSignature()
		);
	}

	private static List<Stack> decodeFieldKeepingEmpty(JsonObject container, String field, int minSlots) {
		if (container == null || field == null) {
			return emptySlots(minSlots);
		}
		return decodeDataElementKeepingEmpty(container.get(field), minSlots);
	}

	private static List<Stack> decodeDataElementKeepingEmpty(JsonElement element, int minSlots) {
		String data = extractData(element);
		if (data.isBlank()) {
			return emptySlots(minSlots);
		}
		try {
			return decodeKeepingEmpty(data, minSlots);
		} catch (Exception ignored) {
			return emptySlots(minSlots);
		}
	}

	private static List<Stack> emptySlots(int count) {
		List<Stack> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			out.add(null);
		}
		return out;
	}

	private static int compareKeys(String a, String b) {
		Integer ai = tryParseInt(a);
		Integer bi = tryParseInt(b);
		if (ai != null && bi != null) {
			return Integer.compare(ai, bi);
		}
		return a.compareToIgnoreCase(b);
	}

	private static Integer tryParseInt(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException ignored) {
			String digits = value.replaceAll("\\D+", "");
			if (digits.isEmpty()) {
				return null;
			}
			try {
				return Integer.parseInt(digits);
			} catch (NumberFormatException ignored2) {
				return null;
			}
		}
	}

	private static List<Stack> parseMuseum(JsonObject museumMember) {
		if (museumMember == null) {
			return List.of();
		}
		List<Stack> out = new ArrayList<>();
		JsonObject items = obj(museumMember.get("items"));
		if (items != null) {
			for (var entry : items.entrySet()) {
				JsonObject value = obj(entry.getValue());
				if (value == null) {
					continue;
				}
				if (value.has("borrowing") && value.get("borrowing").getAsBoolean()) {
					continue;
				}
				JsonObject nested = obj(value.get("items"));
				if (nested != null && nested.has("data")) {
					out.addAll(decodeDataElement(nested));
				} else if (value.has("items") && value.get("items").isJsonArray()) {
					// already-decoded style - skip
				}
			}
		}
		JsonArray special = museumMember.has("special") && museumMember.get("special").isJsonArray()
			? museumMember.getAsJsonArray("special")
			: null;
		if (special != null) {
			for (JsonElement element : special) {
				JsonObject value = obj(element);
				if (value == null) {
					continue;
				}
				JsonObject nested = obj(value.get("items"));
				if (nested != null) {
					out.addAll(decodeDataElement(nested));
				}
			}
		}
		return out;
	}

	private static List<Stack> parseSacks(JsonObject member, JsonObject inventory) {
		JsonObject counts = sackCountsObject(member, inventory);
		if (counts == null) {
			return List.of();
		}
		List<Stack> out = new ArrayList<>();
		for (var entry : counts.entrySet()) {
			if (!entry.getValue().isJsonPrimitive()) {
				continue;
			}
			int amount = entry.getValue().getAsInt();
			if (amount > 0) {
				out.add(new Stack(entry.getKey(), amount, new CompoundTag(), List.of(), false));
			}
		}
		return out;
	}

	private static JsonObject sackCountsObject(JsonObject member, JsonObject inventory) {
		JsonObject counts = obj(member.get("sacks_counts"));
		if (counts == null && inventory != null) {
			counts = obj(inventory.get("sacks_counts"));
		}
		return counts;
	}

	/**
	 * One page per NEU sack type with every holdable item (count may be 0).
	 * NEU's Rune sack has an empty contents list — real runes go on {@code Rune Sack}.
	 * Gemstone sack is expanded with Flawless/Perfect (NEU only lists Rough–Fine).
	 */
	private static List<InventorySnapshot.Page> parseSackPages(JsonObject member, JsonObject inventory) {
		JsonObject countsJson = sackCountsObject(member, inventory);
		Map<String, Integer> counts = new LinkedHashMap<>();
		if (countsJson != null) {
			for (var entry : countsJson.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					counts.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue().getAsInt());
				}
			}
		}
		List<InventorySnapshot.Page> pages = new ArrayList<>();
		Set<String> claimed = new HashSet<>();
		int gemstonePageIndex = -1;
		for (var sack : NeuRepoCache.sackDefinitions().entrySet()) {
			String sackName = sack.getKey();
			List<String> contents = sack.getValue();
			// NEU leaves Rune.contents empty - filled from leftovers below.
			if (contents.isEmpty() || "Rune".equalsIgnoreCase(sackName)) {
				continue;
			}
			if ("Gemstone".equalsIgnoreCase(sackName)) {
				contents = expandGemstoneContents(contents);
			}
			List<InventorySnapshot.Slot> slots = new ArrayList<>(contents.size());
			for (String itemId : contents) {
				String key = itemId.toUpperCase(Locale.ROOT);
				int amount = sackAmount(counts, key);
				slots.add(toUiSlot(new Stack(itemId, amount, new CompoundTag(), List.of(), false)));
				claimSackId(claimed, key);
			}
			if ("Gemstone".equalsIgnoreCase(sackName)) {
				gemstonePageIndex = pages.size();
			}
			pages.add(new InventorySnapshot.Page(sackName, slots, 9));
		}

		List<InventorySnapshot.Slot> gemExtras = new ArrayList<>();
		List<InventorySnapshot.Slot> runes = new ArrayList<>();
		List<InventorySnapshot.Slot> other = new ArrayList<>();
		for (var entry : counts.entrySet()) {
			String id = entry.getKey();
			if (isClaimedSackId(claimed, id)) {
				continue;
			}
			InventorySnapshot.Slot slot = toUiSlot(new Stack(id, entry.getValue(), new CompoundTag(), List.of(), false));
			if (isGemstoneSackId(id)) {
				gemExtras.add(slot);
				claimSackId(claimed, id);
			} else if (isRuneSackId(id)) {
				runes.add(slot);
			} else {
				other.add(slot);
			}
		}
		if (!gemExtras.isEmpty()) {
			if (gemstonePageIndex >= 0) {
				List<InventorySnapshot.Slot> merged = new ArrayList<>(pages.get(gemstonePageIndex).slots());
				merged.addAll(gemExtras);
				InventorySnapshot.Page prev = pages.get(gemstonePageIndex);
				pages.set(gemstonePageIndex, new InventorySnapshot.Page(prev.title(), merged, prev.columns(), prev.equippedColumn()));
			} else {
				pages.add(new InventorySnapshot.Page("Gemstone", gemExtras, 9));
			}
		}
		// Always include Rune so the menu has a stable entry (empty if no runes).
		pages.add(new InventorySnapshot.Page("Rune", runes, 9));
		if (!other.isEmpty()) {
			pages.add(new InventorySnapshot.Page("Other", other, 9));
		}
		return pages.isEmpty() ? List.of(InventorySnapshot.emptyPage("Sacks", 9)) : pages;
	}

	/** NEU lists Rough/Flawed/Fine only — add Flawless/Perfect for each gem type. */
	private static List<String> expandGemstoneContents(List<String> contents) {
		if (contents == null || contents.isEmpty()) {
			return List.of();
		}
		String[] tiers = {"ROUGH_", "FLAWED_", "FINE_", "FLAWLESS_", "PERFECT_"};
		LinkedHashSet<String> seenGems = new LinkedHashSet<>();
		List<String> out = new ArrayList<>();
		for (String itemId : contents) {
			if (itemId == null || itemId.isBlank()) {
				continue;
			}
			String upper = itemId.toUpperCase(Locale.ROOT);
			String gem = null;
			for (String tier : tiers) {
				if (upper.startsWith(tier) && upper.endsWith("_GEM")) {
					gem = upper.substring(tier.length());
					break;
				}
			}
			if (gem == null) {
				if (seenGems.add(upper)) {
					out.add(itemId);
				}
				continue;
			}
			if (!seenGems.add(gem)) {
				continue;
			}
			for (String tier : tiers) {
				out.add(tier + gem);
			}
		}
		return out;
	}

	private static boolean isGemstoneSackId(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		String key = id.toUpperCase(Locale.ROOT);
		return key.endsWith("_GEM") || key.endsWith("_GEMSTONE");
	}

	private static boolean isRuneSackId(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		String key = id.toUpperCase(Locale.ROOT);
		return key.startsWith("RUNE_") || key.contains("_RUNE");
	}

	private static int sackAmount(Map<String, Integer> counts, String itemId) {
		String key = itemId.toUpperCase(Locale.ROOT);
		Integer amount = counts.get(key);
		if (amount == null) {
			amount = counts.get(key.replace('-', ':'));
		}
		if (amount == null) {
			amount = counts.get(key.replace(':', '-'));
		}
		return amount == null ? 0 : amount;
	}

	private static void claimSackId(Set<String> claimed, String id) {
		String key = id.toUpperCase(Locale.ROOT);
		claimed.add(key);
		claimed.add(key.replace('-', ':'));
		claimed.add(key.replace(':', '-'));
	}

	private static boolean isClaimedSackId(Set<String> claimed, String id) {
		String key = id.toUpperCase(Locale.ROOT);
		return claimed.contains(key)
			|| claimed.contains(key.replace('-', ':'))
			|| claimed.contains(key.replace(':', '-'));
	}

	private static List<Stack> parseEssence(JsonObject member) {
		JsonObject currencies = obj(member.get("currencies"));
		JsonObject essence = currencies == null ? null : obj(currencies.get("essence"));
		if (essence == null) {
			return List.of();
		}
		List<Stack> out = new ArrayList<>();
		for (var entry : essence.entrySet()) {
			JsonObject data = obj(entry.getValue());
			if (data == null || !data.has("current")) {
				continue;
			}
			int amount = data.get("current").getAsInt();
			if (amount > 0) {
				out.add(new Stack("ESSENCE_" + entry.getKey().toUpperCase(Locale.ROOT), amount, new CompoundTag(), List.of(), false));
			}
		}
		return out;
	}

	/** Hypixel inv_armor order: 0 boots, 1 leggings, 2 chestplate, 3 helmet (empties kept). */
	public static List<Stack> readArmorSlots(JsonObject member) {
		JsonObject inventory = obj(member.get("inventory"));
		if (inventory == null) {
			inventory = obj(member.get("inventories"));
		}
		String data = "";
		if (inventory != null) {
			data = extractData(inventory.get("inv_armor"));
			if (data.isBlank()) {
				data = extractData(inventory.get("armor"));
			}
		}
		if (data.isBlank()) {
			data = extractData(member.get("inv_armor"));
		}
		if (data.isBlank()) {
			return List.of();
		}
		try {
			return decodeKeepingEmpty(data, 4);
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private static List<Stack> decodeKeepingEmpty(String encoded, int minSlots) throws IOException {
		byte[] bytes = Base64.getDecoder().decode(encoded);
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
			CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
			if (root == null) {
				return List.of();
			}
			Tag itemsTag = root.get("i");
			if (!(itemsTag instanceof ListTag items)) {
				return List.of();
			}
			List<Stack> out = new ArrayList<>();
			int size = Math.max(minSlots, items.size());
			for (int i = 0; i < size; i++) {
				if (i >= items.size()) {
					out.add(null);
					continue;
				}
				Tag child = items.get(i);
				if (!(child instanceof CompoundTag compound) || compound.isEmpty()) {
					out.add(null);
					continue;
				}
				out.add(fromSlot(compound));
			}
			return out;
		}
	}

	private static void put(Map<String, List<Stack>> map, String key, List<Stack> stacks) {
		map.put(key, stacks);
	}

	private static List<Stack> decodeField(JsonObject container, String field) {
		if (container == null || field == null) {
			return List.of();
		}
		return decodeDataElement(container.get(field));
	}

	private static List<Stack> decodeDataElement(JsonElement element) {
		String data = extractData(element);
		if (data.isBlank()) {
			return List.of();
		}
		try {
			return decode(data);
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private static String extractData(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return "";
		}
		if (element.isJsonPrimitive()) {
			return element.getAsString();
		}
		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			if (object.has("data") && object.get("data").isJsonPrimitive()) {
				return object.get("data").getAsString();
			}
		}
		return "";
	}

	private static List<Stack> decode(String encoded) throws IOException {
		byte[] bytes = Base64.getDecoder().decode(encoded);
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
			CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
			if (root == null) {
				return List.of();
			}
			Tag itemsTag = root.get("i");
			if (!(itemsTag instanceof ListTag items)) {
				return List.of();
			}
			List<Stack> out = new ArrayList<>();
			for (int i = 0; i < items.size(); i++) {
				Tag child = items.get(i);
				if (!(child instanceof CompoundTag compound) || compound.isEmpty()) {
					continue;
				}
				Stack stack = fromSlot(compound);
				if (stack != null) {
					out.add(stack);
				}
			}
			return out;
		}
	}

	private static Stack fromSlot(CompoundTag slot) {
		CompoundTag tag = compound(slot.get("tag"));
		if (tag == null) {
			tag = slot;
		}
		CompoundTag attrs = compound(tag.get("ExtraAttributes"));
		if (attrs == null) {
			attrs = compound(tag.get("extra_attributes"));
		}
		if (attrs == null) {
			return null;
		}
		String id = string(attrs, "id");
		if (id == null) {
			id = string(attrs, "ID");
		}
		if (id == null || id.isBlank()) {
			return null;
		}
		int count = Math.max(1, intOr(slot, "Count", intOr(slot, "count", 1)));
		CompoundTag display = compound(tag.get("display"));
		List<String> lore = new ArrayList<>();
		String displayName = null;
		Integer dyeColor = null;
		if (display != null) {
			displayName = cleanJsonText(tagText(display.get("Name")));
			Tag loreTag = display.get("Lore");
			if (loreTag instanceof ListTag loreList) {
				for (Tag line : loreList) {
					// Preserve blank lore entries as tooltip spacers.
					lore.add(cleanJsonText(tagText(line)));
				}
			}
			if (display.contains("color")) {
				int color = intOr(display, "color", Integer.MIN_VALUE);
				if (color != Integer.MIN_VALUE) {
					dyeColor = color & 0xFFFFFF;
				}
			}
		}
		String skullValue = null;
		String skullSignature = null;
		CompoundTag skullOwner = compound(tag.get("SkullOwner"));
		if (skullOwner == null) {
			skullOwner = compound(tag.get("skull_owner"));
		}
		if (skullOwner != null) {
			CompoundTag props = compound(skullOwner.get("Properties"));
			if (props == null) {
				props = compound(skullOwner.get("properties"));
			}
			Tag textures = props == null ? null : props.get("textures");
			if (textures instanceof ListTag list && !list.isEmpty()) {
				CompoundTag first = compound(list.get(0));
				if (first != null) {
					skullValue = string(first, "Value");
					if (skullValue == null) {
						skullValue = string(first, "value");
					}
					skullSignature = string(first, "Signature");
					if (skullSignature == null) {
						skullSignature = string(first, "signature");
					}
				}
			}
		}
		boolean soulbound = attrs.contains("donated_museum")
			|| lore.stream().anyMatch(line -> line.contains("Soulbound"));
		return new Stack(id, count, attrs, lore, soulbound, displayName, dyeColor, skullValue, skullSignature);
	}

	private static String tagText(Tag tag) {
		if (tag == null) {
			return "";
		}
		if (tag instanceof net.minecraft.nbt.StringTag stringTag) {
			try {
				return stringTag.value();
			} catch (Throwable ignored) {
				try {
					return stringTag.toString().replaceAll("^\"|\"$", "");
				} catch (Throwable ignored2) {
				}
			}
		}
		String raw = tag.toString();
		if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
			return unescapeSnbtString(raw.substring(1, raw.length() - 1));
		}
		return raw;
	}

	private static String unescapeSnbtString(String value) {
		return value
			.replace("\\\"", "\"")
			.replace("\\n", "\n")
			.replace("\\u00a7", "§")
			.replace("\\u00A7", "§");
	}

	/** Flatten Hypixel JSON text components / quoted SNBT into a §-legacy string. */
	private static String cleanJsonText(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String line = raw.trim();
		if (line.startsWith("\"") && line.endsWith("\"") && line.length() >= 2) {
			line = unescapeSnbtString(line.substring(1, line.length() - 1));
		}
		if (line.startsWith("{") && line.contains("text")) {
			StringBuilder out = new StringBuilder();
			int idx = 0;
			while (true) {
				int textIdx = line.indexOf("\"text\"", idx);
				if (textIdx < 0) {
					break;
				}
				int colon = line.indexOf(':', textIdx);
				int firstQuote = line.indexOf('"', colon + 1);
				int secondQuote = firstQuote >= 0 ? line.indexOf('"', firstQuote + 1) : -1;
				while (secondQuote > firstQuote && line.charAt(secondQuote - 1) == '\\') {
					secondQuote = line.indexOf('"', secondQuote + 1);
				}
				if (firstQuote >= 0 && secondQuote > firstQuote) {
					out.append(unescapeSnbtString(line.substring(firstQuote + 1, secondQuote)));
				}
				idx = secondQuote > 0 ? secondQuote + 1 : textIdx + 6;
			}
			if (!out.isEmpty()) {
				return SkyBlockSymbols.replace(out.toString());
			}
		}
		return SkyBlockSymbols.replace(line.replace("\\u00a7", "§").replace("\\u00A7", "§"));
	}

	private static JsonObject obj(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static CompoundTag compound(Tag tag) {
		return tag instanceof CompoundTag c ? c : null;
	}

	private static String string(CompoundTag tag, String key) {
		if (tag == null || !tag.contains(key)) {
			return null;
		}
		try {
			return tag.getStringOr(key, null);
		} catch (Throwable ignored) {
			Tag value = tag.get(key);
			return value == null ? null : value.toString().replaceAll("^\"|\"$", "");
		}
	}

	private static int intOr(CompoundTag tag, String key, int fallback) {
		if (tag == null || !tag.contains(key)) {
			return fallback;
		}
		try {
			return tag.getIntOr(key, fallback);
		} catch (Throwable ignored) {
			try {
				return tag.getByteOr(key, (byte) fallback);
			} catch (Throwable ignored2) {
				return fallback;
			}
		}
	}
}
