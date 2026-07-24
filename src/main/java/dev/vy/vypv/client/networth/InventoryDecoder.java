package dev.vy.vypv.client.networth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Decodes Hypixel inventory base64 → SkyBlock item stacks for networth. */
public final class InventoryDecoder {
	public record Stack(String id, int count, CompoundTag extraAttributes, List<String> lore, boolean soulbound) {
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
					// already-decoded style — skip
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
		JsonObject counts = obj(member.get("sacks_counts"));
		if (counts == null && inventory != null) {
			counts = obj(inventory.get("sacks_counts"));
		}
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
		if (display != null) {
			Tag loreTag = display.get("Lore");
			if (loreTag instanceof ListTag loreList) {
				for (Tag line : loreList) {
					lore.add(line.toString());
				}
			}
		}
		boolean soulbound = attrs.contains("donated_museum")
			|| lore.stream().anyMatch(line -> line.contains("Soulbound"));
		return new Stack(id, count, attrs, lore, soulbound);
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
