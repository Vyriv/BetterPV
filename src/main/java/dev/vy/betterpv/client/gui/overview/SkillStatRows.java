package dev.vy.betterpv.client.gui.overview;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.PlayerStatsSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.SkyBlockStats;
import java.util.ArrayList;
import java.util.List;

/** Calculated stat rows for skill overviews, with what the estimate leaves out spelled out on hover. */
public final class SkillStatRows {
	private SkillStatRows() {
	}

	public static SummaryCards.Stat row(PlayerStatsSnapshot stats, String id, List<String> exclusions) {
		SkyBlockStats.StatStyle style = SkyBlockStats.stat(id);
		PlayerStatsSnapshot.Entry entry = find(stats, id);
		boolean present = entry != null && entry.present();
		String value = present ? format(entry.value().getAsDouble()) : "-";
		List<PvTooltip.Line> tip = new ArrayList<>(SkyBlockStats.tooltipLines(id, value));
		tip.add(PvTooltip.Line.blank());
		if (present) {
			for (String line : exclusions) {
				tip.add(PvTooltip.Line.meta(line));
			}
		} else {
			tip.add(PvTooltip.Line.meta("Not in the API"));
		}
		return new SummaryCards.Stat(style.fullName(), value, style.color(), present ? PvDraw.COLOR_WHITE : PvDraw.COLOR_MUTED, tip);
	}

	private static PlayerStatsSnapshot.Entry find(PlayerStatsSnapshot stats, String id) {
		if (stats == null) {
			return null;
		}
		for (PlayerStatsSnapshot.Entry entry : stats.entries()) {
			if (id.equals(entry.id())) {
				return entry;
			}
		}
		return null;
	}

	private static String format(double value) {
		if (Math.abs(value - Math.rint(value)) < 0.05) {
			return FormatUtil.commas(Math.round(value));
		}
		return FormatUtil.oneDecimal(value);
	}
}
