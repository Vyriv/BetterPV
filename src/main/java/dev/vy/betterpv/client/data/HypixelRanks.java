package dev.vy.betterpv.client.data;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;

/** Formats Hypixel rank-coloured display names for tooltips. */
public final class HypixelRanks {
	private HypixelRanks() {
	}

	public static List<PvTooltip.Span> nameSpans(String name, JsonObject player) {
		String safeName = name == null || name.isBlank() ? "?" : name;
		if (player == null) {
			return List.of(PvTooltip.Span.bold(safeName, PvDraw.COLOR_TEXT));
		}

		String rank = effectiveRank(player);
		int plusColor = namedColor(player, "rankPlusColor", ChatFormatting.RED);
		int monthlyPlus = namedColor(player, "monthlyRankColor", ChatFormatting.RED);

		return switch (rank) {
			case "VIP" -> rankSpans(safeName, ChatFormatting.GREEN, "VIP", "", color(ChatFormatting.GREEN));
			case "VIP_PLUS" -> rankSpans(safeName, ChatFormatting.GREEN, "VIP", "+", color(ChatFormatting.GOLD));
			case "MVP" -> rankSpans(safeName, ChatFormatting.AQUA, "MVP", "", color(ChatFormatting.AQUA));
			case "MVP_PLUS" -> rankSpans(safeName, ChatFormatting.AQUA, "MVP", "+", plusColor);
			case "SUPERSTAR", "MVP_PLUS_PLUS" -> rankSpans(safeName, ChatFormatting.GOLD, "MVP", "++", monthlyPlus);
			case "YOUTUBER" -> youtube(safeName);
			case "ADMIN" -> rankSpans(safeName, ChatFormatting.RED, "ADMIN", "", color(ChatFormatting.RED));
			case "GAME_MASTER", "MODERATOR" -> rankSpans(safeName, ChatFormatting.DARK_GREEN, "GM", "", color(ChatFormatting.DARK_GREEN));
			case "HELPER" -> rankSpans(safeName, ChatFormatting.BLUE, "HELPER", "", color(ChatFormatting.BLUE));
			default -> List.of(PvTooltip.Span.bold(safeName, color(ChatFormatting.GRAY)));
		};
	}

	private static String effectiveRank(JsonObject player) {
		String rank = text(player, "rank", "NONE");
		String monthly = text(player, "monthlyPackageRank", "NONE");
		String packageRank = text(player, "newPackageRank", "NONE");
		if (!"YOUTUBER".equals(rank) && !"NONE".equals(monthly)) {
			return monthly;
		}
		if ("NONE".equals(rank) || "NORMAL".equals(rank)) {
			return packageRank;
		}
		return rank;
	}

	private static List<PvTooltip.Span> rankSpans(
		String name,
		ChatFormatting bracketFmt,
		String tag,
		String plus,
		int plusRgb
	) {
		int bracket = color(bracketFmt);
		List<PvTooltip.Span> spans = new ArrayList<>(5);
		spans.add(PvTooltip.Span.of("[", bracket));
		spans.add(PvTooltip.Span.of(tag, bracket));
		if (plus != null && !plus.isEmpty()) {
			spans.add(PvTooltip.Span.of(plus, plusRgb));
		}
		spans.add(PvTooltip.Span.of("] ", bracket));
		spans.add(PvTooltip.Span.bold(name, bracket));
		return spans;
	}

	private static List<PvTooltip.Span> youtube(String name) {
		List<PvTooltip.Span> spans = new ArrayList<>(4);
		spans.add(PvTooltip.Span.of("[", color(ChatFormatting.RED)));
		spans.add(PvTooltip.Span.of("YOUTUBE", color(ChatFormatting.WHITE)));
		spans.add(PvTooltip.Span.of("] ", color(ChatFormatting.RED)));
		spans.add(PvTooltip.Span.bold(name, color(ChatFormatting.RED)));
		return spans;
	}

	private static int namedColor(JsonObject player, String key, ChatFormatting fallback) {
		String raw = text(player, key, fallback.getName());
		ChatFormatting formatting = ChatFormatting.getByName(raw);
		if (formatting == null || !formatting.isColor()) {
			return color(fallback);
		}
		return color(formatting);
	}

	private static int color(ChatFormatting formatting) {
		Integer rgb = formatting.getColor();
		return rgb == null ? PvDraw.COLOR_TEXT : 0xFF000000 | rgb;
	}

	private static String text(JsonObject obj, String key, String fallback) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			String value = obj.get(key).getAsString();
			return value == null || value.isBlank() ? fallback : value.toUpperCase(Locale.ROOT);
		} catch (Exception ignored) {
			return fallback;
		}
	}
}
