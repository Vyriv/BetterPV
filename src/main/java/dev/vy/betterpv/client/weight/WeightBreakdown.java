package dev.vy.betterpv.client.weight;

import java.util.ArrayList;
import java.util.List;

public final class WeightBreakdown {
	public record Line(String label, double value, double overflow) {
		public double total() {
			return value + overflow;
		}
	}

	public record Category(String name, double value, double overflow, List<Line> lines) {
		public double total() {
			return value + overflow;
		}
	}

	private final WeightSystem system;
	private final double base;
	private final double overflow;
	private final String stageOrRank;
	private final List<Category> categories;

	public WeightBreakdown(WeightSystem system, double base, double overflow, String stageOrRank, List<Category> categories) {
		this.system = system;
		this.base = base;
		this.overflow = overflow;
		this.stageOrRank = stageOrRank;
		this.categories = List.copyOf(categories);
	}

	public static WeightBreakdown empty(WeightSystem system) {
		return new WeightBreakdown(system, 0, 0, "—", List.of());
	}

	public WeightSystem system() {
		return this.system;
	}

	public double base() {
		return this.base;
	}

	public double overflow() {
		return this.overflow;
	}

	public double total() {
		return this.base + this.overflow;
	}

	public String stageOrRank() {
		return this.stageOrRank;
	}

	public List<Category> categories() {
		return this.categories;
	}

	public List<String> tooltipLines() {
		List<String> lines = new ArrayList<>();
		String kind = this.system == WeightSystem.SENITHER ? "Stage" : "Rank";
		lines.add(this.system.display() + " Weight");
		lines.add("Total: " + format(total()) + " (" + format(this.base) + " without Overflow)");
		lines.add(kind + ": " + this.stageOrRank);
		for (Category category : this.categories) {
			lines.add("");
			lines.add(category.name() + ": " + format(category.total())
				+ (category.overflow() > 0.05 ? " (+" + format(category.overflow()) + ")" : ""));
			for (Line line : category.lines()) {
				String overflow = line.overflow() > 0.05 ? " (+" + format(line.overflow()) + ")" : "";
				lines.add("  " + line.label() + ": " + format(line.total()) + overflow);
			}
		}
		lines.add("");
		lines.add("Click to switch to " + this.system.other().display());
		return lines;
	}

	private static String format(double value) {
		return dev.vy.betterpv.client.data.FormatUtil.weight(value);
	}
}
