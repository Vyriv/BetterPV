package dev.vy.vypv.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.vypv.client.networth.InventoryDecoder;
import dev.vy.vypv.client.networth.PetWorth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pets menu data from Hypixel {@code pets_data}. */
public final class PetSnapshot {
	public record Entry(
		String type,
		String displayName,
		String tier,
		int tierIndex,
		String neuId,
		int level,
		int maxLevel,
		double exp,
		double xpMax,
		float progressToNext,
		boolean active,
		String heldItem,
		int candyUsed,
		String skin,
		String uuid,
		double networth
	) {
		public Entry {
			type = type == null ? "" : type;
			displayName = displayName == null || displayName.isBlank() ? type : displayName;
			tier = tier == null || tier.isBlank() ? "COMMON" : tier;
			neuId = neuId == null ? "" : neuId;
			heldItem = heldItem == null ? "" : heldItem;
			skin = skin == null ? "" : skin;
			uuid = uuid == null ? "" : uuid;
			level = Math.max(1, level);
			maxLevel = Math.max(level, maxLevel);
			candyUsed = Math.max(0, candyUsed);
			progressToNext = Math.max(0f, Math.min(1f, progressToNext));
			networth = Math.max(0, networth);
		}

		public boolean hasHeldItem() {
			return heldItem != null && !heldItem.isBlank();
		}

		public boolean hasSkin() {
			return skin != null && !skin.isBlank();
		}
	}

	private final List<Entry> pets;

	public PetSnapshot(List<Entry> pets) {
		this.pets = pets == null ? List.of() : List.copyOf(pets);
	}

	public static PetSnapshot empty() {
		return new PetSnapshot(List.of());
	}

	/** Parse {@code pets_data.pets[]} (fallback {@code pets[]}), sorted active → level → name. */
	public static PetSnapshot fromMember(JsonObject member) {
		if (member == null) {
			return empty();
		}
		JsonArray pets = null;
		JsonObject petsData = member.has("pets_data") && member.get("pets_data").isJsonObject()
			? member.getAsJsonObject("pets_data")
			: null;
		if (petsData != null && petsData.has("pets") && petsData.get("pets").isJsonArray()) {
			pets = petsData.getAsJsonArray("pets");
		} else if (member.has("pets") && member.get("pets").isJsonArray()) {
			pets = member.getAsJsonArray("pets");
		}
		if (pets == null || pets.isEmpty()) {
			return empty();
		}
		List<Entry> entries = new ArrayList<>();
		for (JsonElement element : pets) {
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			Entry entry = fromPet(element.getAsJsonObject());
			if (entry != null) {
				entries.add(entry);
			}
		}
		entries.sort(Comparator
			.comparingDouble(Entry::exp).reversed()
			.thenComparing(e -> e.displayName().toLowerCase(Locale.ROOT)));
		return new PetSnapshot(entries);
	}

	private static Entry fromPet(JsonObject pet) {
		if (pet == null || !pet.has("type") || !pet.get("type").isJsonPrimitive()) {
			return null;
		}
		String type = pet.get("type").getAsString();
		String tier = pet.has("tier") && pet.get("tier").isJsonPrimitive() ? pet.get("tier").getAsString() : "COMMON";
		int tierIndex = petTierIndex(tier);
		String neuId = type.toUpperCase(Locale.ROOT) + ";" + tierIndex;
		PetWorth.LevelInfo info = PetWorth.levelInfo(pet);
		boolean active = pet.has("active") && pet.get("active").isJsonPrimitive() && pet.get("active").getAsBoolean();
		String heldItem = pet.has("heldItem") && pet.get("heldItem").isJsonPrimitive() ? pet.get("heldItem").getAsString() : "";
		int candyUsed = pet.has("candyUsed") && pet.get("candyUsed").isJsonPrimitive() ? pet.get("candyUsed").getAsInt() : 0;
		String skin = pet.has("skin") && pet.get("skin").isJsonPrimitive() ? pet.get("skin").getAsString() : "";
		String uuid = "";
		if (pet.has("uniqueId") && pet.get("uniqueId").isJsonPrimitive()) {
			uuid = pet.get("uniqueId").getAsString();
		} else if (pet.has("uuid") && pet.get("uuid").isJsonPrimitive()) {
			uuid = pet.get("uuid").getAsString();
		}
		double nw = PetWorth.value(pet);
		return new Entry(
			type,
			InventoryDecoder.prettyWords(type),
			tier,
			tierIndex,
			neuId,
			info.level(),
			info.maxLevel(),
			info.exp(),
			info.xpMax(),
			info.progressToNext(),
			active,
			heldItem,
			candyUsed,
			skin,
			uuid,
			nw
		);
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

	public List<Entry> pets() {
		return this.pets;
	}

	public boolean isEmpty() {
		return this.pets.isEmpty();
	}
}
