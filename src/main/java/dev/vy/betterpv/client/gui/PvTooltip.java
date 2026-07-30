package dev.vy.betterpv.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.ArrayList;
import java.util.List;

public final class PvTooltip {
	public record Span(String text, int color, boolean bold) {
		public static Span of(String text, int color) {
			return new Span(text, color, false);
		}

		public static Span bold(String text, int color) {
			return new Span(text, color, true);
		}

		public Component toComponent() {
			return PvDraw.styled(this.text, this.color, this.bold);
		}
	}

	public record Line(List<Span> spans) {
		public static Line of(String text, int color) {
			return new Line(List.of(Span.of(text, color)));
		}

		public static Line bold(String text, int color) {
			return new Line(List.of(Span.bold(text, color)));
		}

		public static Line plain(String text) {
			return of(text, PvDraw.COLOR_TEXT);
		}

		/** Empty row used as section spacing. */
		public static Line blank() {
			return new Line(List.of());
		}

		public Component toComponent() {
			MutableComponent out = Component.empty();
			for (Span span : this.spans) {
				out.append(span.toComponent());
			}
			return out;
		}

		public boolean isBlank() {
			if (this.spans == null || this.spans.isEmpty()) {
				return true;
			}
			for (Span span : this.spans) {
				if (span.text() != null && !span.text().isEmpty()) {
					return false;
				}
			}
			return true;
		}

		public static Line row(String k1, int v1, String k2, int v2) {
			return new Line(List.of(
				Span.of(k1 + ": ", PvDraw.COLOR_MUTED),
				Span.of(String.valueOf(v1), PvDraw.COLOR_TEXT),
				Span.of("  " + k2 + ": ", PvDraw.COLOR_MUTED),
				Span.of(String.valueOf(v2), PvDraw.COLOR_TEXT)
			));
		}

		public static Line meta(String text) {
			return of(text, PvDraw.COLOR_MUTED);
		}

		public static Line title(String text, int color) {
			return bold(text, color);
		}

		public static Line divider() {
			return blank();
		}
	}

	private PvTooltip() {
	}

	public static void draw(GuiGraphicsExtractor g, Font font, List<String> lines, int mouseX, int mouseY, int screenW, int screenH) {
		draw(g, font, lines, mouseX, mouseY, screenW, screenH, PvDraw.COLOR_TEXT);
	}

	public static void drawComponents(
		GuiGraphicsExtractor g,
		Font font,
		List<Component> lines,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		int pad = 6;
		int lineH = font.lineHeight + 2;
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, Math.max(8, font.width(line)));
		}
		int boxW = width + pad * 2;
		int boxH = lines.size() * lineH + pad * 2;
		int x = mouseX + 12;
		int y = mouseY - 12;
		if (x + boxW > screenW - 4) {
			x = mouseX - boxW - 8;
		}
		if (y + boxH > screenH - 4) {
			y = screenH - boxH - 4;
		}
		if (x < 4) {
			x = 4;
		}
		if (y < 4) {
			y = 4;
		}
		PvDraw.fill(g, x, y, boxW, boxH, 0xF0101018);
		g.outline(x, y, boxW, boxH, PvDraw.COLOR_BORDER);
		int ty = y + pad;
		for (Component line : lines) {
			if (!line.getString().isEmpty()) {
				PvDraw.text(g, font, line, x + pad, ty);
			}
			ty += lineH;
		}
	}

	public static void draw(
		GuiGraphicsExtractor g,
		Font font,
		List<String> lines,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH,
		int textColor
	) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		drawStyled(g, font, lines.stream().map(line -> Line.of(line, textColor)).toList(), mouseX, mouseY, screenW, screenH);
	}

	public static void drawStyled(
		GuiGraphicsExtractor g,
		Font font,
		List<Line> lines,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		int pad = 8;
		int lineH = font.lineHeight + 3;
		int blankH = Math.max(4, font.lineHeight / 2);
		List<Component> rendered = new ArrayList<>(lines.size());
		List<Integer> heights = new ArrayList<>(lines.size());
		int width = 0;
		int boxH = pad * 2;
		for (Line line : lines) {
			Component component = line.toComponent();
			rendered.add(component);
			int h = line.isBlank() ? blankH : lineH;
			heights.add(h);
			boxH += h;
			if (!line.isBlank()) {
				width = Math.max(width, Math.max(8, font.width(component)));
			}
		}
		int boxW = width + pad * 2;
		int x = mouseX + 12;
		int y = mouseY - 12;
		drawBox(g, font, rendered, heights, x, y, boxW, boxH, pad, screenW, screenH);
	}

	/** Center a single-line tip horizontally above {@code (cx, topY)}. */
	public static void drawCenteredAbove(
		GuiGraphicsExtractor g,
		Font font,
		Component line,
		int cx,
		int topY,
		int screenW,
		int screenH
	) {
		if (line == null) {
			return;
		}
		int pad = 6;
		int lineH = font.lineHeight + 2;
		int boxW = Math.max(8, font.width(line)) + pad * 2;
		int boxH = lineH + pad * 2;
		int x = cx - boxW / 2;
		int y = topY - boxH - 3;
		drawBox(g, font, List.of(line), x, y, boxW, boxH, pad, lineH, screenW, screenH);
	}

	/** Center a single-line tip vertically to the left of {@code (leftX, cy)}. */
	public static void drawCenteredLeft(
		GuiGraphicsExtractor g,
		Font font,
		Component line,
		int leftX,
		int cy,
		int screenW,
		int screenH
	) {
		if (line == null) {
			return;
		}
		int pad = 6;
		int lineH = font.lineHeight + 2;
		int boxW = Math.max(8, font.width(line)) + pad * 2;
		int boxH = lineH + pad * 2;
		int x = leftX - boxW - 3;
		int y = cy - boxH / 2;
		drawBox(g, font, List.of(line), x, y, boxW, boxH, pad, lineH, screenW, screenH);
	}

	private static void drawBox(
		GuiGraphicsExtractor g,
		Font font,
		List<Component> rendered,
		int x,
		int y,
		int boxW,
		int boxH,
		int pad,
		int lineH,
		int screenW,
		int screenH
	) {
		List<Integer> heights = new ArrayList<>(rendered.size());
		for (int i = 0; i < rendered.size(); i++) {
			heights.add(lineH);
		}
		drawBox(g, font, rendered, heights, x, y, boxW, boxH, pad, screenW, screenH);
	}

	private static void drawBox(
		GuiGraphicsExtractor g,
		Font font,
		List<Component> rendered,
		List<Integer> heights,
		int x,
		int y,
		int boxW,
		int boxH,
		int pad,
		int screenW,
		int screenH
	) {
		if (x + boxW > screenW - 4) {
			x = screenW - boxW - 4;
		}
		if (y + boxH > screenH - 4) {
			y = screenH - boxH - 4;
		}
		if (x < 4) {
			x = 4;
		}
		if (y < 4) {
			y = 4;
		}
		PvDraw.fill(g, x, y, boxW, boxH, 0xF0101018);
		g.outline(x, y, boxW, boxH, PvDraw.COLOR_BORDER);
		int ty = y + pad;
		for (int i = 0; i < rendered.size(); i++) {
			Component line = rendered.get(i);
			if (!line.getString().isEmpty()) {
				PvDraw.text(g, font, line, x + pad, ty);
			}
			ty += heights.get(i);
		}
	}
}
