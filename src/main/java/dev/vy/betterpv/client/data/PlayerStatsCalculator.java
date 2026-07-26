package dev.vy.betterpv.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.BetterPV;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds overview stats from Hypixel member data when inventories/constants are available.
 * Values come from base constants, skill/fairy/HOTM fields, and item lore on armor/equipment/accessories.
 */
public final class PlayerStatsCalculator {
	private static final String STAT_END = ": ((?:\\+|-)([0-9]+(?:\\.[0-9]+)?))%?";
	private static final List<StatDef> DISPLAY = List.of(
		new StatDef("health", "HP", Pattern.compile("^Health" + STAT_END)),
		new StatDef("defense", "Def", Pattern.compile("^(?:Defence|Defense)" + STAT_END)),
		new StatDef("strength", "STR", Pattern.compile("^Strength" + STAT_END)),
		new StatDef("speed", "SPD", Pattern.compile("^Speed" + STAT_END)),
		new StatDef("critical_chance", "CC", Pattern.compile("^Crit Chance" + STAT_END)),
		new StatDef("critical_damage", "CD", Pattern.compile("^Crit Damage" + STAT_END)),
		new StatDef("attack_speed", "AS", Pattern.compile("^(?:Bonus Attack Speed|Attack Speed)" + STAT_END)),
		new StatDef("intelligence", "INT", Pattern.compile("^Intelligence" + STAT_END)),
		new StatDef("ferocity", "Fero", Pattern.compile("^Ferocity" + STAT_END)),
		new StatDef("ability_damage", "AD", Pattern.compile("^Ability Damage" + STAT_END)),
		new StatDef("magic_find", "MF", Pattern.compile("^Magic Find" + STAT_END)),
		new StatDef("pet_luck", "PL", Pattern.compile("^Pet Luck" + STAT_END)),
		new StatDef("true_defense", "TD", Pattern.compile("^(?:True Defence|True Defense)" + STAT_END)),
		new StatDef("health_regen", "Regen", Pattern.compile("^Health Regen" + STAT_END)),
		new StatDef("vitality", "VIT", Pattern.compile("^Vitality" + STAT_END)),
		new StatDef("mining_fortune", "MiF", Pattern.compile("^Mining Fortune" + STAT_END)),
		new StatDef("farming_fortune", "FaF", Pattern.compile("^Farming Fortune" + STAT_END)),
		new StatDef("foraging_fortune", "FoF", Pattern.compile("^Foraging Fortune" + STAT_END)),
		new StatDef("mining_speed", "MS", Pattern.compile("^Mining Speed" + STAT_END)),
		new StatDef("pristine", "Pris", Pattern.compile("^Pristine" + STAT_END)),
		new StatDef("sea_creature_chance", "SCC", Pattern.compile("^Sea Creature Chance" + STAT_END)),
		new StatDef("fishing_speed", "FS", Pattern.compile("^Fishing Speed" + STAT_END))
	);

	private static volatile JsonObject miscCache;
	private static volatile JsonObject bonusesCache;

	private PlayerStatsCalculator() {
	}

	public static PlayerStatsSnapshot fromMember(
		JsonObject member,
		Map<String, List<InventoryDecoder.Stack>> categories
	) {
		if (member == null) {
			return PlayerStatsSnapshot.empty();
		}
		Map<String, Double> totals = new LinkedHashMap<>();
		boolean anySource = false;

		JsonObject misc = misc();
		if (misc != null && misc.has("base_stats") && misc.get("base_stats").isJsonObject()) {
			JsonObject base = misc.getAsJsonObject("base_stats");
			for (StatDef def : DISPLAY) {
				Double baseValue = readBaseStat(base, def.id);
				if (baseValue != null) {
					add(totals, def.id, baseValue);
					anySource = true;
				}
			}
		}

		Map<String, Integer> skillLevels = skillLevels(member);
		JsonObject bonuses = bonuses();
		if (bonuses != null && bonuses.has("bonus_stats") && bonuses.get("bonus_stats").isJsonObject()) {
			JsonObject bonusStats = bonuses.getAsJsonObject("bonus_stats");
			for (var skillEntry : skillLevels.entrySet()) {
				if (!bonusStats.has(skillEntry.getKey()) || !bonusStats.get(skillEntry.getKey()).isJsonObject()) {
					continue;
				}
				JsonObject perLevel = bonusStats.getAsJsonObject(skillEntry.getKey());
				Map<String, Double> current = Map.of();
				for (int level = 1; level <= skillEntry.getValue(); level++) {
					String key = String.valueOf(level);
					if (perLevel.has(key) && perLevel.get(key).isJsonObject()) {
						current = readStatMap(perLevel.getAsJsonObject(key));
					}
					for (var e : current.entrySet()) {
						if (wanted(e.getKey())) {
							add(totals, e.getKey(), e.getValue());
							anySource = true;
						}
					}
				}
			}
		}

		int fairyExchanges = intPath(member, "fairy_soul", "fairy_exchanges");
		if (fairyExchanges > 0) {
			add(totals, "speed", fairyExchanges / 10.0);
			for (int i = 0; i < fairyExchanges; i++) {
				add(totals, "strength", (i + 1) % 5 == 0 ? 2 : 1);
				add(totals, "defense", (i + 1) % 5 == 0 ? 2 : 1);
				add(totals, "health", 3 + i / 2);
			}
			anySource = true;
		}

		int miningLevel = skillLevels.getOrDefault("mining", 0);
		JsonObject miningCore = obj(member.get("mining_core"));
		JsonObject nodes = miningCore == null ? null : obj(miningCore.get("nodes"));
		if (nodes != null || miningLevel > 0) {
			double fortune = 4.0 * miningLevel
				+ 5.0 * intOr(nodes, "mining_fortune")
				+ 5.0 * intOr(nodes, "mining_fortune_2");
			if (fortune > 0) {
				add(totals, "mining_fortune", fortune);
				anySource = true;
			}
			double miningSpeed = 20.0 * intOr(nodes, "mining_speed")
				+ 40.0 * intOr(nodes, "mining_speed_2");
			if (miningSpeed > 0) {
				add(totals, "mining_speed", miningSpeed);
				anySource = true;
			}
		}

		if (categories != null) {
			anySource |= addItemLore(totals, categories.get("armor"), false);
			anySource |= addItemLore(totals, categories.get("equipment"), false);
			anySource |= addItemLore(totals, categories.get("accessories"), true);
		}

		List<PlayerStatsSnapshot.Entry> entries = new ArrayList<>(DISPLAY.size());
		for (StatDef def : DISPLAY) {
			OptionalDouble value = OptionalDouble.empty();
			if (anySource && totals.containsKey(def.id)) {
				value = OptionalDouble.of(totals.get(def.id));
			}
			entries.add(new PlayerStatsSnapshot.Entry(def.id, def.label, value));
		}
		return new PlayerStatsSnapshot(entries);
	}

	private static boolean addItemLore(
		Map<String, Double> totals,
		List<InventoryDecoder.Stack> stacks,
		boolean uniqueById
	) {
		if (stacks == null || stacks.isEmpty()) {
			return false;
		}
		Set<String> seen = uniqueById ? new HashSet<>() : null;
		boolean added = false;
		for (InventoryDecoder.Stack stack : stacks) {
			if (stack == null || stack.id() == null || stack.id().isBlank()) {
				continue;
			}
			if (seen != null && !seen.add(stack.id())) {
				continue;
			}
			List<String> lore = stack.lore();
			if (lore == null || lore.isEmpty()) {
				continue;
			}
			for (String line : lore) {
				String clean = stripFormatting(line);
				if (clean.isBlank()) {
					continue;
				}
				for (StatDef def : DISPLAY) {
					Matcher matcher = def.pattern.matcher(clean);
					if (matcher.find()) {
						try {
							add(totals, def.id, Double.parseDouble(matcher.group(1)));
							added = true;
						} catch (NumberFormatException ignored) {
						}
					}
				}
			}
		}
		return added;
	}

	private static Map<String, Integer> skillLevels(JsonObject member) {
		Map<String, Integer> out = new HashMap<>();
		for (String skill : List.of(
			"combat", "mining", "farming", "foraging", "fishing", "enchanting", "alchemy", "taming", "carpentry"
		)) {
			float xp = Leveling.readSkillXp(member, skill);
			if (xp <= 0F) {
				continue;
			}
			int cap = Leveling.skillCap(skill, member);
			Leveling.Progress progress = Leveling.getLevel(RepoData.levelingXp(), xp, cap, false);
			int level = progress == null ? 0 : (int) Math.floor(progress.level());
			if (level > 0) {
				out.put(skill, level);
			}
		}
		return out;
	}

	private static Map<String, Double> readStatMap(JsonObject object) {
		Map<String, Double> out = new HashMap<>();
		for (var entry : object.entrySet()) {
			if (!entry.getValue().isJsonPrimitive()) {
				continue;
			}
			String key = normalizeStatKey(entry.getKey());
			if (wanted(key)) {
				out.put(key, entry.getValue().getAsDouble());
			}
		}
		return out;
	}

	private static String normalizeStatKey(String key) {
		if (key == null) {
			return "";
		}
		String k = key.toLowerCase(Locale.ROOT);
		return switch (k) {
			case "defence" -> "defense";
			case "walk_speed" -> "speed";
			case "crit_chance", "critchance" -> "critical_chance";
			case "crit_damage", "critdamage" -> "critical_damage";
			case "true_defence" -> "true_defense";
			case "bonus_attack_speed" -> "attack_speed";
			default -> k;
		};
	}

	/** NEU misc.base_stats uses defence / crit_chance naming. */
	private static Double readBaseStat(JsonObject base, String id) {
		for (String key : baseStatKeys(id)) {
			if (base.has(key) && base.get(key).isJsonPrimitive()) {
				return base.get(key).getAsDouble();
			}
		}
		return null;
	}

	private static List<String> baseStatKeys(String id) {
		return switch (id) {
			case "defense" -> List.of("defense", "defence");
			case "speed" -> List.of("speed", "walk_speed");
			case "critical_chance" -> List.of("critical_chance", "crit_chance");
			case "critical_damage" -> List.of("critical_damage", "crit_damage");
			case "true_defense" -> List.of("true_defense", "true_defence");
			case "attack_speed" -> List.of("attack_speed", "bonus_attack_speed");
			default -> List.of(id);
		};
	}

	private static boolean wanted(String id) {
		for (StatDef def : DISPLAY) {
			if (def.id.equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static void add(Map<String, Double> totals, String id, double value) {
		totals.merge(id, value, Double::sum);
	}

	private static String stripFormatting(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); ) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				i += 2;
				continue;
			}
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (cp >= 0xE000 && cp <= 0xF8FF) {
				continue;
			}
			out.appendCodePoint(cp);
		}
		return out.toString().trim();
	}

	private static JsonObject misc() {
		JsonObject cached = miscCache;
		if (cached != null) {
			return cached;
		}
		miscCache = loadConstant("misc.json");
		return miscCache;
	}

	private static JsonObject bonuses() {
		JsonObject cached = bonusesCache;
		if (cached != null) {
			return cached;
		}
		bonusesCache = loadConstant("bonuses.json");
		return bonusesCache;
	}

	private static JsonObject loadConstant(String fileName) {
		Path path = Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo", "repo", "constants", fileName);
		if (!Files.isRegularFile(path)) {
			path = Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo", "constants", fileName);
		}
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement el = JsonParser.parseReader(reader);
			return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (Exception exception) {
			BetterPV.LOGGER.debug("Failed loading NEU {}", fileName, exception);
			return null;
		}
	}

	private static JsonObject obj(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static int intPath(JsonObject root, String a, String b) {
		JsonObject nested = obj(root.get(a));
		return intOr(nested, b);
	}

	private static int intOr(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return 0;
		}
		try {
			return object.get(key).getAsInt();
		} catch (Exception ignored) {
			return 0;
		}
	}

	private record StatDef(String id, String label, Pattern pattern) {
	}
}
