package dev.vy.betterpv.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import dev.vy.betterpv.client.networth.PetWorth;
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
		double xpIntoLevel,
		double xpToNext,
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
			xpIntoLevel = Math.max(0, xpIntoLevel);
			xpToNext = Math.max(0, xpToNext);
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
	private final int highestPetScore;
	private final List<String> sacrificedTypes;
	private final int autopetRuleCount;
	private final int autopetRulesLimit;
	private final List<String> autopetRuleNames;

	public PetSnapshot(
		List<Entry> pets,
		int highestPetScore,
		List<String> sacrificedTypes,
		int autopetRuleCount,
		int autopetRulesLimit,
		List<String> autopetRuleNames
	) {
		this.pets = pets == null ? List.of() : List.copyOf(pets);
		this.highestPetScore = Math.max(0, highestPetScore);
		this.sacrificedTypes = List.copyOf(sacrificedTypes == null ? List.of() : sacrificedTypes);
		this.autopetRuleCount = Math.max(0, autopetRuleCount);
		this.autopetRulesLimit = Math.max(0, autopetRulesLimit);
		this.autopetRuleNames = List.copyOf(autopetRuleNames == null ? List.of() : autopetRuleNames);
	}

	public PetSnapshot(List<Entry> pets) {
		this(pets, 0, List.of(), 0, 0, List.of());
	}

	public static PetSnapshot empty() {
		return new PetSnapshot(List.of(), 0, List.of(), 0, 0, List.of());
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

		List<Entry> entries = new ArrayList<>();
		if (pets != null) {
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
		}

		JsonObject leveling = Leveling.obj(member.get("leveling"));
		int highestScore = intOf(leveling, "highest_pet_score");

		List<String> sacrificed = new ArrayList<>();
		JsonObject petCare = Leveling.obj(petsData == null ? null : petsData.get("pet_care"));
		JsonElement sacrificedEl = petCare == null ? null : petCare.get("pet_types_sacrificed");
		if (sacrificedEl != null && sacrificedEl.isJsonArray()) {
			for (JsonElement el : sacrificedEl.getAsJsonArray()) {
				if (el != null && el.isJsonPrimitive()) {
					try {
						String type = el.getAsString();
						if (type != null && !type.isBlank()) {
							sacrificed.add(InventoryDecoder.prettyWords(type));
						}
					} catch (Exception ignored) {
					}
				}
			}
		}

		JsonObject autopet = Leveling.obj(petsData == null ? null : petsData.get("autopet"));
		int rulesLimit = intOf(autopet, "rules_limit");
		List<String> ruleNames = new ArrayList<>();
		JsonElement rulesEl = autopet == null ? null : autopet.get("rules");
		if (rulesEl != null && rulesEl.isJsonArray()) {
			for (JsonElement el : rulesEl.getAsJsonArray()) {
				if (el == null || !el.isJsonObject()) {
					continue;
				}
				JsonObject rule = el.getAsJsonObject();
				String name = "";
				if (rule.has("name") && rule.get("name").isJsonPrimitive()) {
					try {
						name = rule.get("name").getAsString();
					} catch (Exception ignored) {
					}
				}
				if (name != null && !name.isBlank()) {
					ruleNames.add(stripSectionCodes(name));
				}
			}
		}

		return new PetSnapshot(entries, highestScore, sacrificed, ruleNames.size(), rulesLimit, ruleNames);
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
			info.xpIntoLevel(),
			info.xpToNext(),
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

	private static int intOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return 0;
		}
		try {
			return Math.max(0, (int) obj.get(key).getAsDouble());
		} catch (Exception ignored) {
			return 0;
		}
	}

	private static String stripSectionCodes(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString().trim();
	}

	public List<Entry> pets() {
		return this.pets;
	}

	public int highestPetScore() {
		return this.highestPetScore;
	}

	public List<String> sacrificedTypes() {
		return this.sacrificedTypes;
	}

	public int autopetRuleCount() {
		return this.autopetRuleCount;
	}

	public int autopetRulesLimit() {
		return this.autopetRulesLimit;
	}

	public List<String> autopetRuleNames() {
		return this.autopetRuleNames;
	}

	public boolean isEmpty() {
		return this.pets.isEmpty();
	}
}
