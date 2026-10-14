package dev.vy.betterpv.client.gui.garden.page;

import dev.vy.betterpv.client.data.FarmingFortuneEstimate;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.GardenSnapshot;
import dev.vy.betterpv.client.data.PlayerStatsSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.SkyBlockStats;
import dev.vy.betterpv.client.gui.overview.SummaryCards;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.List;

/** Garden overview Farming Fortune row built from the best farming setup on the profile. */
final class FarmingFortuneTip {
	static final String LABEL = "Farming Fortune (est.)";

	private FarmingFortuneTip() {
	}

	/** Null until inventories are parsed; callers fall back to the worn-gear stat. */
	static SummaryCards.Stat row(PlayerStatsSnapshot stats, GardenSnapshot snap) {
		FarmingFortuneEstimate est = stats == null ? null : stats.farmingFortune();
		if (est == null) {
			return null;
		}
		FarmingFortuneEstimate.Garden garden = FarmingFortuneEstimate.Garden.of(snap);
		SkyBlockStats.StatStyle style = SkyBlockStats.stat("farming_fortune");
		String value = format(est.general(garden));
		return new SummaryCards.Stat(LABEL, value, style.color(), PvDraw.COLOR_WHITE, tooltip(est, garden, value));
	}

	static List<PvTooltip.Line> tooltip(FarmingFortuneEstimate est, FarmingFortuneEstimate.Garden garden, String value) {
		List<PvTooltip.Line> tip = new ArrayList<>(SkyBlockStats.tooltipLines("farming_fortune", value));
		tip.add(PvTooltip.Line.meta("Estimate from the best farming setup"));
		tip.add(PvTooltip.Line.meta("on the profile, not a live stat."));
		tip.add(PvTooltip.Line.blank());
		tip.add(row("Permanent bonuses", est.permanentTotal(garden)));
		tip.add(row("Armour and set bonus", est.armourTotal()));
		tip.add(row("Equipment", est.equipmentTotal()));
		FarmingFortuneEstimate.PetPick pet = est.bestPet(null, garden);
		tip.add(row(pet == null ? "Pet" : "Pet: " + pet.pet().displayName(), est.petTotal(null, garden)));
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row("General", PvDraw.COLOR_TEXT, value, PvDraw.COLOR_WHITE));
		tip.add(PvTooltip.Line.blank());
		tip.add(PvTooltip.Line.meta("Excludes tools and crop-specific"));
		tip.add(PvTooltip.Line.meta("fortune." + (garden.loaded() ? "" : " Garden not loaded yet.")));
		tip.add(PvTooltip.Line.of("Click for the full breakdown", PvDraw.COLOR_ACCENT));
		return tip;
	}

	private static PvTooltip.Line row(String label, double value) {
		return PvTooltip.Line.row(label, PvDraw.COLOR_MUTED, "+" + format(value), PvDraw.COLOR_GOLD);
	}

	static String name(InventoryDecoder.Stack stack) {
		String raw = stack.displayName() == null ? "" : stack.displayName().replaceAll("\u00a7.", "").trim();
		return raw.isEmpty() ? stack.id() : raw;
	}

	static String format(double value) {
		if (Math.abs(value - Math.rint(value)) < 0.005) {
			return FormatUtil.commas(Math.round(value));
		}
		double tenths = Math.round(value * 10D) / 10D;
		if (Math.abs(value - tenths) < 0.005) {
			return FormatUtil.oneDecimal(value);
		}
		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}
}
