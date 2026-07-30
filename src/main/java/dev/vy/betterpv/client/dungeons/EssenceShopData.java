package dev.vy.betterpv.client.dungeons;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.Leveling;
import java.util.ArrayList;
import java.util.List;

/**
 * Wither / Undead essence shop progress from {@code player_data.perks}.
 * Perk lists match NEU {@code essenceshops.json} (class XP shops are separate).
 */
public final class EssenceShopData {
	private record Def(String id, String name, int maxLevel, String... aliasIds) {
	}

	/** Six Forbidden upgrades in the Wither Essence Shop. */
	private static final List<Def> WITHER = List.of(
		new Def("permanent_health", "Health", 5),
		new Def("permanent_defense", "Defense", 5),
		new Def("permanent_speed", "Speed", 2),
		new Def("permanent_intelligence", "Intelligence", 5),
		new Def("permanent_strength", "Strength", 5),
		new Def("forbidden_blessing", "Blessing", 10)
	);

	/** Undead Essence Shop (Catacombs-only). */
	private static final List<Def> UNDEAD = List.of(
		new Def("catacombs_boss_luck", "Boss Luck", 4),
		new Def("catacombs_looting", "Looting", 5),
		new Def("revive_stone", "Fairies", 1, "help_of_the_fairies"),
		new Def("catacombs_health", "Health", 5),
		new Def("catacombs_defense", "Defense", 5),
		new Def("catacombs_strength", "Strength", 5),
		new Def("catacombs_intelligence", "Intelligence", 5),
		new Def("catacombs_crit_damage", "Crit", 5)
	);

	/** Gold Essence Shop (Dwarven Mines / Crystal Hollows). */
	private static final List<Def> GOLD = List.of(
		new Def("heart_of_gold", "Heart of Gold", 5),
		new Def("treasures_of_the_earth", "Treasure of the Earth", 5),
		new Def("dwarven_training", "Dwarven Training", 3),
		new Def("unbreaking", "Unbreaking", 5),
		new Def("eager_miner", "Eager Miner", 10),
		new Def("midas_lure", "Midas Lure", 10)
	);
	
	/** Crimson Essence Shop. */
	private static final List<Def> CRIMSON = List.of(
		new Def("crimson_health", "Health", 5),
		new Def("crimson_defense", "Defense", 5),
		new Def("crimson_strength", "Strength", 5),
		new Def("crimson_intelligence", "Intelligence", 5),
		new Def("crimson_crit_damage", "Crit", 5)
	);

	/** Forest Essence Shop (Foraging). */
	private static final List<Def> FOREST = List.of(
		new Def("forest_health", "Health", 5),
		new Def("forest_defense", "Defense", 5),
		new Def("forest_strength", "Strength", 5),
		new Def("forest_intelligence", "Intelligence", 5),
		new Def("forest_crit_damage", "Crit", 5)
	);

	/** Diamond Essence Shop. */
	private static final List<Def> DIAMOND = List.of(
		new Def("radiant_fisher", "Radiant Fisher", 10),
		new Def("diamond_in_the_rough", "Diamond in the Rough", 5),
		new Def("rhinestone_infusion", "Rhinestone Infusion", 10),
		new Def("under_pressure", "Under Pressure", 5),
		new Def("high_roller", "High Roller", 1),
		new Def("return_to_sender", "Return to Sender", 10)
	);

	private EssenceShopData() {
	}

	public static DungeonSnapshot.EssenceShop wither(JsonObject member) {
		return shop("wither", "Wither", "WITHER", "ESSENCE_WITHER", WITHER, member);
	}

	public static DungeonSnapshot.EssenceShop undead(JsonObject member) {
		return shop("undead", "Undead", "UNDEAD", "ESSENCE_UNDEAD", UNDEAD, member);
	}

	public static DungeonSnapshot.EssenceShop gold(JsonObject member) {
		return shop("gold", "Gold", "GOLD", "ESSENCE_GOLD", GOLD, member);
	}

	public static DungeonSnapshot.EssenceShop crimson(JsonObject member) {
		return shop("crimson", "Crimson", "CRIMSON", "ESSENCE_CRIMSON", CRIMSON, member);
	}

	public static DungeonSnapshot.EssenceShop forest(JsonObject member) {
		return shop("forest", "Forest", "FOREST", "ESSENCE_FOREST", FOREST, member);
	}

	public static DungeonSnapshot.EssenceShop diamond(JsonObject member) {
		return shop("diamond", "Diamond", "DIAMOND", "ESSENCE_DIAMOND", DIAMOND, member);
	}

	private static DungeonSnapshot.EssenceShop shop(
		String id,
		String name,
		String currencyKey,
		String iconId,
		List<Def> defs,
		JsonObject member
	) {
		JsonObject perks = perks(member);
		List<DungeonSnapshot.EssencePerk> list = new ArrayList<>(defs.size());
		for (Def def : defs) {
			int level = Math.max(0, Math.min(def.maxLevel(), perkLevel(perks, def)));
			list.add(new DungeonSnapshot.EssencePerk(def.id(), def.name(), level, def.maxLevel()));
		}
		return new DungeonSnapshot.EssenceShop(
			id, name, essenceBalance(member, currencyKey), iconId, List.copyOf(list)
		);
	}

	private static JsonObject perks(JsonObject member) {
		JsonObject playerData = Leveling.obj(member == null ? null : member.get("player_data"));
		JsonObject fromPlayer = playerData == null ? null : Leveling.obj(playerData.get("perks"));
		if (fromPlayer != null) {
			return fromPlayer;
		}
		JsonObject dungeons = Leveling.obj(member == null ? null : member.get("dungeons"));
		return dungeons == null ? null : Leveling.obj(dungeons.get("perks"));
	}

	private static int perkLevel(JsonObject perks, Def def) {
		int level = perkLevel(perks, def.id());
		if (level > 0 || def.aliasIds() == null) {
			return level;
		}
		for (String alias : def.aliasIds()) {
			int alt = perkLevel(perks, alias);
			if (alt > 0) {
				return alt;
			}
		}
		return level;
	}

	private static int perkLevel(JsonObject perks, String id) {
		if (perks == null || id == null || !perks.has(id)) {
			return 0;
		}
		JsonElement el = perks.get(id);
		if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
			return el.getAsBoolean() ? 1 : 0;
		}
		Float n = Leveling.num(el);
		return n == null ? 0 : Math.round(n);
	}

	private static long essenceBalance(JsonObject member, String type) {
		JsonObject currencies = Leveling.obj(member == null ? null : member.get("currencies"));
		JsonObject essence = currencies == null ? null : Leveling.obj(currencies.get("essence"));
		JsonObject typed = essence == null ? null : Leveling.obj(essence.get(type));
		if (typed == null) {
			return 0L;
		}
		Float current = Leveling.num(typed.get("current"));
		return current == null ? 0L : Math.round(current);
	}
}
