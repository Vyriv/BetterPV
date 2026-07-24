package dev.vy.vypv.client.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.vypv.client.data.Leveling;
import java.util.Locale;

/** Catacombs XP needed to reach a target integer level. */
public final class CataXpMath {
	private CataXpMath() {
	}

	public static float xpForLevel(int level) {
		if (level <= 0) {
			return 0F;
		}
		return Leveling.xpRequiredForLevel(dev.vy.vypv.client.data.RepoData.catacombsXp(), Math.min(level, 50), false);
	}

	public static float xpNeeded(float currentXp, int targetLevel) {
		float need = xpForLevel(targetLevel) - Math.max(0F, currentXp);
		return Math.max(0F, need);
	}

	/** Pretty mayor display from election JSON. */
	public static String mayorName(JsonObject electionRoot) {
		if (electionRoot == null) {
			return "";
		}
		JsonObject mayor = Leveling.obj(electionRoot.get("mayor"));
		if (mayor == null) {
			return "";
		}
		if (mayor.has("name") && mayor.get("name").isJsonPrimitive()) {
			return mayor.get("name").getAsString();
		}
		if (mayor.has("key") && mayor.get("key").isJsonPrimitive()) {
			return mayor.get("key").getAsString();
		}
		return "";
	}

	public static double mayorXpFactor(JsonObject electionRoot) {
		String name = mayorName(electionRoot).toLowerCase(Locale.ROOT);
		String key = "";
		JsonObject mayor = electionRoot == null ? null : Leveling.obj(electionRoot.get("mayor"));
		if (mayor != null && mayor.has("key") && mayor.get("key").isJsonPrimitive()) {
			key = mayor.get("key").getAsString().toLowerCase(Locale.ROOT);
		}
		String id = key.isBlank() ? name : key;
		if (id.contains("derpy")) {
			return 1.5;
		}
		if (id.contains("aura")) {
			return 1.59;
		}
		if (mayor != null && mayor.has("perks") && mayor.get("perks").isJsonArray()) {
			JsonArray perks = mayor.getAsJsonArray("perks");
			for (JsonElement el : perks) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject perk = el.getAsJsonObject();
				String desc = "";
				if (perk.has("description") && perk.get("description").isJsonPrimitive()) {
					desc = perk.get("description").getAsString().toLowerCase(Locale.ROOT);
				}
				String perkName = perk.has("name") && perk.get("name").isJsonPrimitive()
					? perk.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
				boolean xpPerk = desc.contains("catacombs") && desc.contains("experi")
					|| desc.contains("dungeon") && desc.contains("experi")
					|| perkName.contains("derpy")
					|| perkName.contains("aura");
				if (!xpPerk) {
					continue;
				}
				if (desc.contains("59%") || id.contains("aura") || perkName.contains("aura")) {
					return 1.59;
				}
				if (desc.contains("50%") || id.contains("derpy") || perkName.contains("derpy")) {
					return 1.5;
				}
			}
		}
		return 1.0;
	}
}
