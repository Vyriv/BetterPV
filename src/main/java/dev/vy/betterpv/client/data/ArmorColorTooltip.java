package dev.vy.betterpv.client.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Text model for armour colour tooltip lines; kept free of Minecraft types so it can be tested. */
public final class ArmorColorTooltip {
	public record Segment(String text, int rgb) {
	}

	public record Line(List<Segment> segments) {
		public String plain() {
			StringBuilder out = new StringBuilder();
			for (Segment segment : this.segments) {
				out.append(segment.text());
			}
			return out.toString();
		}
	}

	public static final int GRAY = 0xAAAAAA;
	public static final int DARK_GRAY = 0x555555;
	public static final int WHITE = 0xFFFFFF;
	public static final int GOLD = 0xFFAA00;
	public static final int AQUA = 0x55FFFF;
	public static final int LIGHT_PURPLE = 0xFF55FF;
	public static final int RED = 0xFF5555;
	public static final int BLUE = 0x5555FF;
	public static final int YELLOW = 0xFFFF55;
	public static final String SWATCH = "\u25A0";
	private static final String SEPARATOR = " | ";
	/** BetterPV tooltip panel colour ({@code PvTooltip}). */
	private static final int TOOLTIP_BACKGROUND = 0x101018;
	private static final double MIN_CONTRAST = 3.0D;

	private ArmorColorTooltip() {
	}

	public static List<Line> lines(ArmorColorClassifier.Result result) {
		List<Line> out = new ArrayList<>();
		if (result == null) {
			return out;
		}
		if (result.seymour() != null) {
			if (result.kind() == ArmorColorClassifier.Kind.DYED) {
				out.add(colorLine("Color: ", result.rgb(), result.kind(), result.dyeName()));
			}
			out.addAll(seymourLines(result.baseRgb() != null ? result.baseRgb() : result.rgb(), result.seymour()));
		} else {
			out.add(colorLine("Color: ", result.rgb(), result.kind(), result.dyeName()));
			if (result.baseRgb() != null) {
				out.add(colorLine("Base: ", result.baseRgb(), result.baseKind(), null));
			}
		}
		if (!result.patterns().isEmpty()) {
			List<String> names = new ArrayList<>();
			for (SeymourColors.Pattern pattern : result.patterns()) {
				names.add(pattern.label());
			}
			out.add(new Line(List.of(new Segment("Pattern: ", GRAY), new Segment(String.join(", ", names), LIGHT_PURPLE))));
		}
		return out;
	}

	/** Classification tag shown after the hex, or null when the colour stays unlabelled. */
	public static Segment tag(ArmorColorClassifier.Kind kind, String dyeName) {
		if (kind == null) {
			return null;
		}
		return switch (kind) {
			case ORIGINAL -> new Segment("Original", GRAY);
			case EXOTIC -> new Segment("Exotic", GOLD);
			case CRYSTAL -> new Segment("Crystal", AQUA);
			case FAIRY -> new Segment("Fairy", LIGHT_PURPLE);
			case SPOOK -> new Segment("Spook", RED);
			case GLITCHED -> new Segment("Glitched", BLUE);
			case DYED -> new Segment(dyeName == null || dyeName.isBlank() ? "Dyed" : dyeName + " Dye", GRAY);
			case UNLABELLED -> null;
		};
	}

	private static Line colorLine(String label, int rgb, ArmorColorClassifier.Kind kind, String dyeName) {
		List<Segment> segments = new ArrayList<>();
		segments.add(new Segment(label, GRAY));
		segments.add(new Segment(SWATCH + " ", rgb & 0xFFFFFF));
		segments.add(hexSegment(rgb));
		Segment tag = tag(kind, dyeName);
		if (tag != null) {
			segments.add(new Segment(" ", GRAY));
			segments.add(tag);
		}
		return new Line(List.copyOf(segments));
	}

	/** Seymour Analyzer fields on three lines: hex and tier, closest reference, target hex and ΔE. */
	private static List<Line> seymourLines(int rgb, SeymourColors.Match match) {
		int tierColor = switch (match.tier()) {
			case T1_PLUS -> LIGHT_PURPLE;
			case T1 -> GOLD;
			case T2 -> YELLOW;
			case T3 -> DARK_GRAY;
		};
		return List.of(
			new Line(List.of(
				new Segment("Hex: ", GRAY),
				new Segment(SWATCH + " ", rgb & 0xFFFFFF),
				hexSegment(rgb),
				new Segment(SEPARATOR, DARK_GRAY),
				new Segment(match.tier().label(), tierColor)
			)),
			new Line(List.of(new Segment("Closest: ", GRAY), new Segment(match.target().name(), WHITE))),
			new Line(List.of(
				new Segment("Target: ", GRAY),
				hexSegment(match.target().rgb()),
				new Segment(SEPARATOR, DARK_GRAY),
				new Segment("\u0394E: ", GRAY),
				new Segment(String.format(Locale.ROOT, "%.2f", match.deltaE()), WHITE)
			))
		);
	}

	private static Segment hexSegment(int rgb) {
		return new Segment("#" + ArmorColorClassifier.hex(rgb), readable(rgb));
	}

	/**
	 * The colour itself when it stands out against the tooltip background, otherwise the same hue lightened
	 * just enough to reach a 3:1 contrast ratio so very dark hexes stay legible.
	 */
	public static int readable(int rgb) {
		int color = rgb & 0xFFFFFF;
		double background = luminance(TOOLTIP_BACKGROUND);
		for (int step = 0; step <= 20; step++) {
			int mixed = mixWithWhite(color, step / 20D);
			if ((luminance(mixed) + 0.05D) / (background + 0.05D) >= MIN_CONTRAST) {
				return mixed;
			}
		}
		return WHITE;
	}

	private static int mixWithWhite(int rgb, double amount) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = (int) Math.round(r + (255 - r) * amount);
		g = (int) Math.round(g + (255 - g) * amount);
		b = (int) Math.round(b + (255 - b) * amount);
		return (r << 16) | (g << 8) | b;
	}

	private static double luminance(int rgb) {
		return 0.2126D * channel((rgb >> 16) & 0xFF) + 0.7152D * channel((rgb >> 8) & 0xFF) + 0.0722D * channel(rgb & 0xFF);
	}

	private static double channel(int value) {
		double c = value / 255D;
		return c <= 0.03928D ? c / 12.92D : Math.pow((c + 0.055D) / 1.055D, 2.4D);
	}

	/**
	 * Puts the colour lines directly under the item name (index 0). A lore line at {@code duplicateIndex} that
	 * already states the same hex is dropped so the colour appears once.
	 */
	public static <T> void insertBelowName(List<T> lines, List<T> colorLines, int duplicateIndex) {
		if (lines == null || colorLines == null || colorLines.isEmpty()) {
			return;
		}
		if (duplicateIndex >= 1 && duplicateIndex < lines.size()) {
			lines.remove(duplicateIndex);
		}
		lines.addAll(Math.min(1, lines.size()), colorLines);
	}

	/**
	 * Index of a lore line that already states this hex (for example "Color: #3C6746"), or -1. Matching is on
	 * the plain text so legacy formatting codes do not matter.
	 */
	public static int existingColorLine(List<String> plainLines, int rgb) {
		if (plainLines == null) {
			return -1;
		}
		String hex = "#" + ArmorColorClassifier.hex(rgb);
		for (int i = 0; i < plainLines.size(); i++) {
			String line = plainLines.get(i);
			if (line == null) {
				continue;
			}
			String upper = line.replaceAll("\u00a7.", "").trim().toUpperCase(Locale.ROOT);
			if ((upper.startsWith("COLOR") || upper.startsWith("COLOUR") || upper.startsWith("HEX")) && upper.contains(hex)) {
				return i;
			}
		}
		return -1;
	}
}
