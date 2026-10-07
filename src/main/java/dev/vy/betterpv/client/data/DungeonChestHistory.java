package dev.vy.betterpv.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import dev.vy.betterpv.client.price.HypixelItemsCache;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code dungeons.treasures}: Hypixel only keeps the most recent runs and their reward chests. */
public record DungeonChestHistory(List<Run> runs) {
	public static final long EXPIRY_MS = 72L * 60L * 60L * 1000L;

	public record Chest(String type, boolean paid, int rerolls, List<String> rewards) {
		public Chest {
			type = type == null ? "" : type;
			rewards = List.copyOf(rewards == null ? List.of() : rewards);
		}
	}

	public record Run(String floor, long completedMs, List<String> party, List<Chest> chests) {
		public Run {
			floor = floor == null ? "" : floor;
			party = List.copyOf(party == null ? List.of() : party);
			chests = List.copyOf(chests == null ? List.of() : chests);
		}

		/** Croesus drops runs 72h after completion; the API can keep them longer until the list is rewritten. */
		public long expiresMs() {
			return completedMs <= 0L ? 0L : completedMs + EXPIRY_MS;
		}

		public boolean expired(long nowMs) {
			return completedMs > 0L && nowMs >= expiresMs();
		}

		public Chest opened() {
			Chest best = null;
			for (Chest chest : chests) {
				if (chest.paid() && (best == null || typeRank(chest.type()) > typeRank(best.type()))) {
					best = chest;
				}
			}
			return best;
		}
	}

	public DungeonChestHistory {
		runs = List.copyOf(runs == null ? List.of() : runs);
	}

	public static DungeonChestHistory empty() {
		return new DungeonChestHistory(List.of());
	}

	public boolean present() {
		return !runs.isEmpty();
	}

	public static DungeonChestHistory from(JsonObject member) {
		JsonObject dungeons = Leveling.obj(member == null ? null : member.get("dungeons"));
		JsonObject treasures = Leveling.obj(dungeons == null ? null : dungeons.get("treasures"));
		if (treasures == null) {
			return empty();
		}
		Map<String, List<Chest>> chestsByRun = new LinkedHashMap<>();
		if (treasures.get("chests") != null && treasures.get("chests").isJsonArray()) {
			for (JsonElement el : treasures.getAsJsonArray("chests")) {
				JsonObject c = Leveling.obj(el);
				if (c == null) {
					continue;
				}
				List<String> rewards = new ArrayList<>();
				JsonObject rewardObj = Leveling.obj(c.get("rewards"));
				if (rewardObj != null && rewardObj.get("rewards") != null && rewardObj.get("rewards").isJsonArray()) {
					for (JsonElement r : rewardObj.getAsJsonArray("rewards")) {
						String raw = str(r);
						if (!raw.isBlank()) {
							rewards.add(raw);
						}
					}
				}
				// Kuudra chests use chest_type / is_opened / is_rerolled and carry no reward list.
				String type = str(c.get("treasure_type"));
				if (type.isBlank()) {
					type = str(c.get("chest_type"));
				}
				int rerolls = (int) num(c.get("rerolls"));
				if (rerolls == 0 && bool(c.get("is_rerolled"))) {
					rerolls = 1;
				}
				chestsByRun.computeIfAbsent(str(c.get("run_id")), k -> new ArrayList<>()).add(new Chest(
					type,
					bool(c.get("paid")) || bool(c.get("is_opened")),
					rerolls,
					rewards
				));
			}
		}
		List<Run> runs = new ArrayList<>();
		if (treasures.get("runs") != null && treasures.get("runs").isJsonArray()) {
			for (JsonElement el : treasures.getAsJsonArray("runs")) {
				JsonObject r = Leveling.obj(el);
				if (r == null) {
					continue;
				}
				List<String> party = new ArrayList<>();
				if (r.get("participants") != null && r.get("participants").isJsonArray()) {
					for (JsonElement p : r.getAsJsonArray("participants")) {
						JsonObject po = Leveling.obj(p);
						// Keeps Hypixel's § codes so the UI can show rank colours.
						String name = po == null ? "" : str(po.get("display_name")).trim();
						if (!name.replaceAll("§.", "").isBlank()) {
							party.add(name);
						}
					}
				}
				List<Chest> chests = new ArrayList<>(chestsByRun.getOrDefault(str(r.get("run_id")), List.of()));
				chests.sort(Comparator.comparingInt((Chest c) -> typeRank(c.type())).reversed());
				runs.add(new Run(
					floorLabel(str(r.get("type")), str(r.get("dungeon_type")),
						r.has("dungeon_tier") ? r.get("dungeon_tier") : r.get("tier_id")),
					num(r.get("completion_ts")),
					party,
					chests
				));
			}
		}
		runs.sort(Comparator.comparingLong(Run::completedMs).reversed());
		return new DungeonChestHistory(runs);
	}

	public static int typeRank(String type) {
		return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
			case "free" -> 1;
			case "paid" -> 2;
			case "wood" -> 1;
			case "gold" -> 2;
			case "diamond" -> 3;
			case "emerald" -> 4;
			case "obsidian" -> 5;
			case "bedrock" -> 6;
			default -> 0;
		};
	}

	/** {@code ESSENCE:UNDEAD:22} → "22x Undead Essence", {@code wise_2} → "Wise II". */
	public static String prettyReward(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		if (raw.startsWith("ESSENCE:")) {
			String[] parts = raw.split(":");
			String type = parts.length > 1 ? InventoryDecoder.prettyWords(parts[1]) : "";
			return parts.length > 2 ? parts[2] + "x " + type + " Essence" : type + " Essence";
		}
		if (raw.startsWith("SHARD:")) {
			String[] parts = raw.split(":");
			String type = parts.length > 1 ? InventoryDecoder.prettyWords(parts[1]) : "";
			int count = parts.length > 2 ? (int) Math.max(1L, parseLongOr(parts[2], 1L)) : 1;
			return (count > 1 ? count + "x " : "") + type + " Shard";
		}
		String id = raw.toLowerCase(Locale.ROOT);
		String itemName = rewardItemName(id);
		if (itemName != null) {
			return itemName;
		}
		if (isBook(raw)) {
			int us = id.lastIndexOf('_');
			int level = Integer.parseInt(id.substring(us + 1));
			String base = id.substring(0, us);
			if ("master_jerry".equals(base)) {
				base = "ultimate_jerry";
			}
			return InventoryDecoder.prettyWords(base) + " " + roman(level);
		}
		return InventoryDecoder.prettyWords(id);
	}

	public static boolean isEssence(String raw) {
		return raw != null && raw.startsWith("ESSENCE:");
	}

	/** Enchanted-book style rewards end in a level, e.g. {@code feather_falling_6}. */
	public static boolean isBook(String raw) {
		if (raw == null || isEssence(raw) || raw.contains("tier")) {
			return false;
		}
		int us = raw.lastIndexOf('_');
		if (us <= 0 || us >= raw.length() - 1 || !raw.substring(us + 1).chars().allMatch(Character::isDigit)) {
			return false;
		}
		// Enchant levels stop at 10; keeps items like recombobulator_3000 out.
		return parseLongOr(raw.substring(us + 1), 0L) <= 10L;
	}

	// Croesus reward ids that aren't the item's SkyBlock id.
	private static final Map<String, String> REWARD_ITEM_IDS = Map.of(
		"shadow_helmet", "SHADOW_ASSASSIN_HELMET",
		"shadow_chestplate", "SHADOW_ASSASSIN_CHESTPLATE",
		"shadow_leggings", "SHADOW_ASSASSIN_LEGGINGS",
		"shadow_boots", "SHADOW_ASSASSIN_BOOTS",
		"necro_brooch", "NECROMANCER_BROOCH"
	);

	// NEU constants/enchants.json ultimate pool, without the ultimate_ prefix. Croesus ids drop it too.
	private static final java.util.Set<String> ULTIMATE_ENCHANTS = java.util.Set.of(
		"chimera", "combo", "swarm", "wise", "rend", "bank", "last_stand", "legion", "no_pain_no_gain",
		"wisdom", "one_for_all", "soul_eater", "reiterate", "inferno", "fatal_tempo", "flash",
		"habanero_tactics", "bobbin_time", "refrigerate", "flowstate", "missile", "first_impression",
		"sunset", "master_jerry", "jerry", "duplex", "the_one"
	);

	/**
	 * SkyBlock rarity for a reward ({@code EPIC}, {@code LEGENDARY}, ...), or {@code ESSENCE},
	 * {@code ULTIMATE} / {@code BOOK} for enchanted books. Empty when unknown.
	 */
	public static String rewardTier(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		if (isEssence(raw)) {
			return "ESSENCE";
		}
		String itemId;
		if (raw.startsWith("SHARD:")) {
			String[] parts = raw.split(":");
			itemId = parts.length > 1 ? "SHARD_" + parts[1].toUpperCase(Locale.ROOT) : "";
		} else {
			String id = raw.toLowerCase(Locale.ROOT);
			String alias = REWARD_ITEM_IDS.get(id);
			itemId = alias != null ? alias : id.toUpperCase(Locale.ROOT);
		}
		JsonObject def = HypixelItemsCache.get(itemId);
		if (def != null && def.has("tier") && def.get("tier").isJsonPrimitive()) {
			return def.get("tier").getAsString().toUpperCase(Locale.ROOT);
		}
		if (isBook(raw)) {
			String base = raw.toLowerCase(Locale.ROOT).substring(0, raw.lastIndexOf('_'));
			return ULTIMATE_ENCHANTS.contains(base) ? "ULTIMATE" : "BOOK";
		}
		return "";
	}

	private static String rewardItemName(String id) {
		String alias = REWARD_ITEM_IDS.get(id);
		String itemId = alias != null ? alias : id.toUpperCase(Locale.ROOT);
		JsonObject def = HypixelItemsCache.get(itemId);
		if (def != null && def.has("name") && def.get("name").isJsonPrimitive()) {
			String name = def.get("name").getAsString().replaceAll("§.", "").trim();
			if (!name.isBlank()) {
				return name;
			}
		}
		return alias != null ? InventoryDecoder.prettyWords(alias.toLowerCase(Locale.ROOT)) : null;
	}

	private static long parseLongOr(String s, long fallback) {
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static final String[] KUUDRA_TIERS = { "Basic", "Hot", "Burning", "Fiery", "Infernal" };

	// Kuudra runs have type KUUDRA and tier_id (e.g. "INFERNAL") instead of dungeon_type / dungeon_tier.
	private static String floorLabel(String runType, String dungeonType, JsonElement tierEl) {
		String type = dungeonType == null ? "" : dungeonType.toLowerCase(Locale.ROOT);
		String tierName = str(tierEl).toLowerCase(Locale.ROOT);
		int tier = (int) num(tierEl);
		if ("kuudra".equalsIgnoreCase(runType) || type.contains("kuudra")) {
			for (String name : KUUDRA_TIERS) {
				if (tierName.equals(name.toLowerCase(Locale.ROOT)) || type.contains(name.toLowerCase(Locale.ROOT))) {
					return "Kuudra " + name;
				}
			}
			if ("none".equals(tierName) || "kuudra".equals(type) && tier == 0) {
				return "Kuudra Basic";
			}
			return tier >= 1 && tier <= KUUDRA_TIERS.length ? "Kuudra " + KUUDRA_TIERS[tier - 1] : "Kuudra";
		}
		boolean master = type.startsWith("master");
		if (tier <= 0) {
			return master ? "M?" : "Entrance";
		}
		return (master ? "M" : "F") + tier;
	}

	public static boolean isKuudra(String floor) {
		return floor != null && floor.startsWith("Kuudra");
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> String.valueOf(n);
		};
	}

	private static String str(JsonElement el) {
		if (el == null || !el.isJsonPrimitive()) {
			return "";
		}
		try {
			return el.getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static long num(JsonElement el) {
		if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
			return 0L;
		}
		return Math.max(0L, (long) el.getAsDouble());
	}

	private static boolean bool(JsonElement el) {
		if (el == null || !el.isJsonPrimitive()) {
			return false;
		}
		try {
			return el.getAsBoolean();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return false;
		}
	}
}
