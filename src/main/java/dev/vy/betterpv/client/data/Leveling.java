package dev.vy.betterpv.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

public final class Leveling {
	public record Progress(
		float level,
		float maxXpForLevel,
		boolean maxed,
		int maxLevel,
		float totalXp,
		float xpIntoLevel,
		float overflowXp
	) {
		public float fill() {
			if (maxed || maxXpForLevel <= 0F) {
				return 1.0f;
			}
			return Math.max(0F, Math.min(1F, xpIntoLevel / maxXpForLevel));
		}

		public String hoverText() {
			if (maxed) {
				return "Overflow: " + FormatUtil.shortXp(Math.max(0F, overflowXp));
			}
			long into = Math.round(xpIntoLevel);
			long need = Math.round(maxXpForLevel);
			if (need <= 0L) {
				return FormatUtil.commas(Math.round(totalXp)) + "/" + FormatUtil.commas(Math.round(totalXp)) + " (100%)";
			}
			double pct = fill() * 100.0;
			return FormatUtil.commas(into) + "/" + FormatUtil.commas(need) + " (" + FormatUtil.oneDecimal(pct) + "%)";
		}

		/** e.g. {@code Combat 58 - 62.4% to Level 59} or overflow when maxed. */
		public String skillHover(String name) {
			int lvl = (int) Math.floor(level);
			if (maxed) {
				return name + " " + lvl + " - Overflow: " + FormatUtil.shortXp(Math.max(0F, overflowXp));
			}
			int next = Math.min(maxLevel, lvl + 1);
			return name + " " + lvl + " - " + FormatUtil.oneDecimal(fill() * 100.0) + "% to Level " + next;
		}

		/** e.g. {@code Revenant 9 - 1,240,000 / 2,000,000 XP} */
		public String slayerHover(String name) {
			int tier = (int) Math.floor(level);
			if (maxed) {
				return name + " " + tier + " - MAX";
			}
			long into = Math.round(xpIntoLevel);
			long need = Math.round(maxXpForLevel);
			return name + " " + tier + " - " + FormatUtil.commas(into) + " / " + FormatUtil.commas(need) + " XP";
		}
	}

	private Leveling() {
	}

	/** Non-cumulative table (skills / cata): each entry is XP required for that level step. */
	public static Progress getLevel(JsonArray table, float xp, int levelCap, boolean cumulative) {
		if (table == null || table.isEmpty()) {
			return new Progress(0, 0, false, levelCap, xp, 0, 0);
		}
		float xpToCap = xpRequiredForLevel(table, levelCap, cumulative);
		float remaining = Math.max(0F, xp);
		for (int level = 0; level < table.size(); level++) {
			float levelXp = table.get(level).getAsFloat();
			if (levelXp > remaining) {
				float maxXpForLevel;
				float resultLevel;
				float into;
				if (cumulative) {
					float previous = level > 0 ? table.get(level - 1).getAsFloat() : 0F;
					maxXpForLevel = levelXp - previous;
					into = remaining - previous;
					resultLevel = level + into / Math.max(1F, maxXpForLevel);
				} else {
					maxXpForLevel = levelXp;
					into = remaining;
					resultLevel = level + remaining / Math.max(1F, levelXp);
				}
				boolean maxed = resultLevel >= levelCap;
				if (maxed) {
					return new Progress(levelCap, maxXpForLevel, true, levelCap, xp, maxXpForLevel, Math.max(0F, xp - xpToCap));
				}
				return new Progress(resultLevel, maxXpForLevel, false, levelCap, xp, into, 0);
			}
			if (!cumulative) {
				remaining -= levelXp;
			}
		}
		int capped = Math.min(table.size(), levelCap);
		return new Progress(capped, 0, true, levelCap, xp, 0, Math.max(0F, xp - xpToCap));
	}

	/** XP required to reach {@code levelCap} (non-cumulative = sum of steps; cumulative = table value). */
	public static float xpRequiredForLevel(JsonArray table, int levelCap, boolean cumulative) {
		if (table == null || table.isEmpty() || levelCap <= 0) {
			return 0F;
		}
		int capped = Math.min(levelCap, table.size());
		if (cumulative) {
			return table.get(capped - 1).getAsFloat();
		}
		float sum = 0F;
		for (int i = 0; i < capped; i++) {
			sum += table.get(i).getAsFloat();
		}
		return sum;
	}

	public static float readSkillXp(JsonObject member, String skill) {
		String apiSkill = skill.equals("social") ? "SOCIAL" : skill.toUpperCase(Locale.ROOT);
		JsonObject playerData = obj(member.get("player_data"));
		JsonObject experience = playerData == null ? null : obj(playerData.get("experience"));
		if (experience != null) {
			String[] keys = {
				"SKILL_" + apiSkill,
				"SKILL_" + skill.toUpperCase(Locale.ROOT),
				skill.toUpperCase(Locale.ROOT)
			};
			for (String key : keys) {
				Float value = num(experience.get(key));
				if (value != null && value > 0F) {
					return value;
				}
			}
			for (var entry : experience.entrySet()) {
				if (entry.getKey() != null && entry.getKey().toUpperCase(Locale.ROOT).contains(apiSkill)) {
					Float value = num(entry.getValue());
					if (value != null && value > 0F) {
						return value;
					}
				}
			}
		}
		Float legacy = num(member.get("experience_skill_" + (skill.equals("social") ? "social2" : skill)));
		return legacy == null ? 0F : legacy;
	}

	public static float readSlayerXp(JsonObject member, String slayer) {
		JsonObject slayerRoot = obj(member.get("slayer"));
		JsonObject bosses = slayerRoot == null ? null : obj(slayerRoot.get("slayer_bosses"));
		if (bosses == null) {
			bosses = obj(member.get("slayer_bosses"));
		}
		JsonObject boss = bosses == null ? null : obj(bosses.get(slayer));
		Float xp = boss == null ? null : num(boss.get("xp"));
		return xp == null ? 0F : xp;
	}

	public static float readClassXp(JsonObject member, String className) {
		JsonObject dungeons = obj(member.get("dungeons"));
		JsonObject classes = dungeons == null ? null : obj(dungeons.get("player_classes"));
		JsonObject clazz = classes == null ? null : obj(classes.get(className));
		if (clazz == null) {
			return 0F;
		}
		Float xp = num(clazz.get("experience"));
		if (xp == null) {
			xp = num(clazz.get("xp"));
		}
		return xp == null ? 0F : xp;
	}

	public static float readCatacombsXp(JsonObject member) {
		JsonObject dungeons = obj(member.get("dungeons"));
		JsonObject types = dungeons == null ? null : obj(dungeons.get("dungeon_types"));
		JsonObject type = types == null ? null : obj(types.get("catacombs"));
		Float xp = type == null ? null : num(type.get("experience"));
		return xp == null ? 0F : xp;
	}

	public static int skillCap(String skill, JsonObject member) {
		int base = RepoData.skillCap(skill);
		if ("farming".equals(skill)) {
			JsonObject jacobs = obj(member.get("jacobs_contest"));
			if (jacobs == null) {
				jacobs = obj(member.get("jacob2"));
			}
			JsonObject perks = jacobs == null ? null : obj(jacobs.get("perks"));
			if (perks != null && perks.has("farming_level_cap")) {
				base += perks.get("farming_level_cap").getAsInt();
			}
		}
		if ("runecrafting".equals(skill) || "social".equals(skill)) {
			return Math.min(base, 25);
		}
		return base;
	}

	public static JsonArray skillTable(String skill) {
		JsonObject leveling = RepoData.leveling();
		if (leveling == null) {
			return null;
		}
		if ("runecrafting".equals(skill) && leveling.has("runecrafting_xp")) {
			return leveling.getAsJsonArray("runecrafting_xp");
		}
		if ("social".equals(skill) && leveling.has("social")) {
			return leveling.getAsJsonArray("social");
		}
		return RepoData.levelingXp();
	}

	public static Float num(JsonElement element) {
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return null;
		}
		try {
			return element.getAsFloat();
		} catch (Exception exception) {
			return null;
		}
	}

	public static JsonObject obj(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}
}
