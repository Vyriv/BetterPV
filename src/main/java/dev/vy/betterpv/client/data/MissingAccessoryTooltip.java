package dev.vy.betterpv.client.data;

import dev.vy.betterpv.client.data.MissingAccessories.CostBasis;
import dev.vy.betterpv.client.data.MissingAccessories.Entry;
import dev.vy.betterpv.client.data.MissingAccessories.PriceStatus;
import dev.vy.betterpv.client.data.MissingAccessories.UpgradeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tooltip text for a Missing Accessories step, kept free of Minecraft types so it can be tested. */
public final class MissingAccessoryTooltip {
	public enum Tone {
		MUTED, TEXT, GOLD, AQUA
	}

	/** A {@code label: value} line, a plain line when {@code label} is null, or a spacer when both are null. */
	public record Line(String label, String value, Tone tone) {
		static final Line SPACER = new Line(null, null, Tone.MUTED);

		public boolean spacer() {
			return this.label == null && this.value == null;
		}
	}

	private MissingAccessoryTooltip() {
	}

	/** Lines shown under the title (the entry name). */
	public static List<Line> lines(Entry entry) {
		List<Line> lines = new ArrayList<>();
		String action = actionLabel(entry);
		// The title already names most actions, and an unknown entry's state line explains itself.
		if (entry.type() != UpgradeType.UNKNOWN_STATE && !titleNamesAction(entry.name(), action)) {
			lines.add(new Line(null, action, Tone.MUTED));
		}
		if (entry.currentState() != null && !entry.currentState().isBlank()) {
			lines.add(new Line(null, entry.currentState(), Tone.TEXT));
		}
		if (entry.unknownState()) {
			return lines;
		}
		lines.add(Line.SPACER);
		addCost(lines, entry);
		lines.add(new Line("Magical Power", "+" + entry.mpGain(), Tone.AQUA));
		if (entry.priced() && entry.mpGain() > 0 && entry.type() != UpgradeType.MOVE_TO_BAG) {
			lines.add(new Line("Coins per MP", FormatUtil.shortCoins(entry.coinsPerMp()), Tone.GOLD));
		}
		return lines;
	}

	public static String actionLabel(Entry entry) {
		return switch (entry.type()) {
			case PURCHASE -> entry.costBasis() == CostBasis.UNKNOWN ? "Upgrade" : "Buy";
			case TIER_UPGRADE -> entry.costBasis() == CostBasis.CRAFT ? "Craft upgrade" : "Upgrade";
			case MOVE_TO_BAG -> "Move to bag";
			case RECOMBOBULATE -> "Recombobulate";
			case PANDORA_PROGRESSION -> "Gameplay progress";
			case RIFT_PRISM_IMBUEMENT -> "Imbue";
			case UNKNOWN_STATE -> "Unknown state";
		};
	}

	private static void addCost(List<Line> lines, Entry entry) {
		if (entry.type() == UpgradeType.MOVE_TO_BAG) {
			lines.add(new Line("Cost", "Free, already owned", Tone.GOLD));
			return;
		}
		if (entry.priceStatus() == PriceStatus.NOT_PRICED) {
			lines.add(new Line("Cost", "Gameplay only", Tone.MUTED));
			return;
		}
		boolean craft = entry.costBasis() == CostBasis.CRAFT;
		String label = craft ? "Craft cost" : "Cost";
		if (entry.priced()) {
			lines.add(new Line(label, FormatUtil.commas(Math.round(entry.netCost())), Tone.GOLD));
		} else {
			String reason = entry.priceStatus() == PriceStatus.STALE ? "Stale price"
				: entry.costBasis() == CostBasis.UNKNOWN ? "Unknown upgrade method"
				: craft ? "Material prices unavailable"
				: entry.soulbound() ? "Soulbound, cannot be bought" : "No price available";
			lines.add(new Line(label, reason, Tone.MUTED));
		}
		if (entry.soldName() != null) {
			lines.add(new Line(null, (craft ? "Uses your " : "Replaces your ") + entry.soldName(), Tone.MUTED));
		}
		if (entry.priced() && entry.type() == UpgradeType.RIFT_PRISM_IMBUEMENT) {
			lines.add(new Line(
				null, "Includes " + FormatUtil.commas(Math.round(MissingAccessories.RIFT_PRISM_IMBUE_FEE)) + " imbue fee",
				Tone.MUTED
			));
		}
	}

	private static boolean titleNamesAction(String title, String action) {
		if (title == null || action == null) {
			return false;
		}
		String verb = action.split(" ")[0].toLowerCase(Locale.ROOT);
		for (String word : title.toLowerCase(Locale.ROOT).split("[^a-z']+")) {
			if (word.equals(verb)) {
				return true;
			}
		}
		return false;
	}
}
