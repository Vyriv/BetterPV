package dev.vy.betterpv.client.gui;

import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Hypixel-style SkyBlock stat symbols / colours and power-stone name colours. */
public final class SkyBlockStats {
	private static final Map<String, StatStyle> STATS = Map.ofEntries(
		Map.entry("health", new StatStyle("❤", 0xFFFF5555)),
		Map.entry("defense", new StatStyle("❈", 0xFF55FF55)),
		Map.entry("speed", new StatStyle("✦", 0xFFFFFFFF)),
		Map.entry("walk_speed", new StatStyle("✦", 0xFFFFFFFF)),
		Map.entry("strength", new StatStyle("❁", 0xFFFF5555)),
		Map.entry("critical_damage", new StatStyle("☠", 0xFF5555FF)),
		Map.entry("critical_chance", new StatStyle("☣", 0xFF5555FF)),
		Map.entry("attack_speed", new StatStyle("⚔", 0xFFFFFF55)),
		Map.entry("intelligence", new StatStyle("✎", 0xFF55FFFF)),
		Map.entry("ferocity", new StatStyle("⫽", 0xFFFF5555)),
		Map.entry("ability_damage", new StatStyle("๑", 0xFFFF5555)),
		Map.entry("health_regen", new StatStyle("❣", 0xFFFF5555)),
		Map.entry("vitality", new StatStyle("♨", 0xFFFF5555)),
		Map.entry("mending", new StatStyle("☄", 0xFFFF5555)),
		Map.entry("swing_range", new StatStyle("Ⓢ", 0xFFFFAA00)),
		Map.entry("magic_find", new StatStyle("✯", 0xFF55FFFF)),
		Map.entry("pet_luck", new StatStyle("♣", 0xFFFF55FF)),
		Map.entry("true_defense", new StatStyle("❂", 0xFFFFFFFF)),
		Map.entry("mining_fortune", new StatStyle("☘", 0xFFFFAA00)),
		Map.entry("farming_fortune", new StatStyle("☘", 0xFFFFAA00)),
		Map.entry("foraging_fortune", new StatStyle("☘", 0xFFFFAA00)),
		Map.entry("mining_speed", new StatStyle("⸕", 0xFFFFAA00)),
		Map.entry("pristine", new StatStyle("✧", 0xFFAA00AA)),
		Map.entry("sea_creature_chance", new StatStyle("α", 0xFF00AAAA)),
		Map.entry("fishing_speed", new StatStyle("☂", 0xFF55FFFF))
	);

	/** Rough Hypixel/Maxwell power name colours. */
	private static final Map<String, Integer> POWER_COLORS = Map.ofEntries(
		Map.entry("silky", 0xFF55FF55),
		Map.entry("hurtful", 0xFFFF5555),
		Map.entry("sighted", 0xFF55FFFF),
		Map.entry("fortuitous", 0xFFFFFF55),
		Map.entry("bloody", 0xFFFF5555),
		Map.entry("shaded", 0xFFAAAAAA),
		Map.entry("forceful", 0xFFFF5555),
		Map.entry("demonic", 0xFFFF5555),
		Map.entry("pleasant", 0xFF55FF55),
		Map.entry("itchy", 0xFFFFFF55),
		Map.entry("adept", 0xFF55FFFF),
		Map.entry("sweet", 0xFFFF55FF),
		Map.entry("crumbly", 0xFF00AA00),
		Map.entry("frozen", 0xFF55FFFF),
		Map.entry("healthy", 0xFFFF5555),
		Map.entry("scorching", 0xFFFFAA00),
		Map.entry("sanguisuge", 0xFFAA0000),
		Map.entry("bubba", 0xFFFF55FF),
		Map.entry("slender", 0xFFAAAAAA)
	);

	private SkyBlockStats() {
	}

	public static StatStyle stat(String id) {
		if (id == null) {
			return new StatStyle("•", PvDraw.COLOR_TEXT);
		}
		return STATS.getOrDefault(id.toLowerCase(Locale.ROOT), new StatStyle("•", PvDraw.COLOR_TEXT));
	}

	public static int powerColor(String power) {
		if (power == null || power.isBlank()) {
			return PvDraw.COLOR_MUTED;
		}
		return POWER_COLORS.getOrDefault(power.toLowerCase(Locale.ROOT), 0xFFFFAA00);
	}

	public static Component powerName(String power) {
		if (power == null || power.isBlank()) {
			return PvDraw.styled("-", PvDraw.COLOR_MUTED, false);
		}
		return PvDraw.styled(InventoryDecoder.prettyWords(power), powerColor(power), true);
	}

	public static Component loadoutName(String name) {
		return PvDraw.styled(name == null || name.isBlank() ? "Loadout" : name, 0xFFFFAA00, true);
	}

	public static Component statChip(InventorySnapshot.StatPoint point) {
		StatStyle style = stat(point.id());
		MutableComponent out = Component.empty();
		out.append(PvDraw.styled(style.symbol(), style.color(), false));
		out.append(PvDraw.styled(String.valueOf(point.value()), style.color(), false));
		return out;
	}

	public static Component tuningLine(Integer slot, java.util.List<InventorySnapshot.StatPoint> stats) {
		return tuningStats(stats);
	}

	/** Stat chips only (no preset index) - used by loadouts. */
	public static Component tuningStats(java.util.List<InventorySnapshot.StatPoint> stats) {
		MutableComponent out = Component.empty();
		if (stats == null || stats.isEmpty()) {
			out.append(PvDraw.styled("-", PvDraw.COLOR_MUTED, false));
			return out;
		}
		boolean first = true;
		for (InventorySnapshot.StatPoint point : stats) {
			if (!first) {
				out.append(PvDraw.styled(" ", PvDraw.COLOR_MUTED, false));
			}
			first = false;
			out.append(statChip(point));
		}
		return out;
	}

	public record StatStyle(String symbol, int color) {
	}
}
