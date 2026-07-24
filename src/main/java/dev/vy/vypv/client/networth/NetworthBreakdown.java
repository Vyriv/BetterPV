package dev.vy.vypv.client.networth;

import dev.vy.vypv.client.data.FormatUtil;
import dev.vy.vypv.client.gui.PvDraw;
import dev.vy.vypv.client.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NetworthBreakdown {
	public record Line(String name, double value) {
	}

	private final double total;
	private final List<Line> categories;
	private final String note;

	public NetworthBreakdown(double total, List<Line> categories, String note) {
		this.total = total;
		this.categories = List.copyOf(categories);
		this.note = note == null ? "" : note;
	}

	public static NetworthBreakdown empty(String note) {
		return new NetworthBreakdown(0, List.of(), note);
	}

	public double total() {
		return this.total;
	}

	public List<Line> categories() {
		return this.categories;
	}

	public String note() {
		return this.note;
	}

	public List<String> tooltipLines() {
		return tooltipStyledLines().stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).reduce("", String::concat))
			.toList();
	}

	public List<PvTooltip.Line> tooltipStyledLines() {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(new PvTooltip.Line(List.of(
			PvTooltip.Span.of("Networth: ", PvDraw.COLOR_WHITE),
			PvTooltip.Span.bold(FormatUtil.commas(Math.round(this.total)), PvDraw.COLOR_GOLD)
		)));
		for (Line line : this.categories) {
			if (line.value() <= 0) {
				continue;
			}
			String prefix = title(line.name()) + ": ";
			String value = FormatUtil.commas(Math.round(line.value()));
			lines.add(new PvTooltip.Line(List.of(
				PvTooltip.Span.of(prefix, PvDraw.COLOR_WHITE),
				PvTooltip.Span.bold(value, PvDraw.COLOR_GOLD)
			)));
		}
		if (!this.note.isBlank()) {
			lines.add(PvTooltip.Line.of(this.note, PvDraw.COLOR_MUTED));
		}
		return lines;
	}

	private static String title(String id) {
		String[] parts = id.split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return out.toString();
	}
}
