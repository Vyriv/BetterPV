package dev.vy.betterpv.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class PvDraw {
	public static final int COLOR_PANEL = 0xD0101018;
	/** Darker shell behind bars / panels. */
	public static final int COLOR_PANEL_OUTER = 0xE00A0A10;
	/** Slightly lighter row / content area. */
	public static final int COLOR_PANEL_INNER = 0xC0181824;
	public static final int COLOR_BORDER = 0xFF3A3A4A;
	public static final int COLOR_TEXT = 0xFFE8E8F0;
	public static final int COLOR_MUTED = 0xFF9A9AAC;
	public static final int COLOR_GOLD = 0xFFFFAA00;
	public static final int COLOR_WHITE = 0xFFFFFFFF;
	public static final int COLOR_ACCENT = 0xFF5B8CFF;
	/** Visible empty track (must read against inner panel). */
	public static final int COLOR_BAR_BG = 0xFF363646;
	public static final int COLOR_BAR_FILL = 0xFF3D8B5A;
	public static final int COLOR_BAR_FILL_SLAYER = 0xFF8B4A3D;
	/** Matches catgirllivid cosmetics name gradient. */
	public static final int COLOR_MAXED_BAR_LEFT = 0xFF9278C5;
	public static final int COLOR_MAXED_BAR_RIGHT = 0xFFF3E6FD;
	public static final int BAR_HEIGHT = 6;

	private PvDraw() {
	}

	public static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
		g.fill(x, y, x + w, y + h, argb);
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		fill(g, x, y, w, h, COLOR_PANEL);
		g.outline(x, y, w, h, COLOR_BORDER);
	}

	public static void innerPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		fill(g, x, y, w, h, COLOR_PANEL_INNER);
		g.outline(x, y, w, h, COLOR_BORDER);
	}

	/** Content panel with equal padding; no dark outer frame. */
	public static void layeredPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int pad) {
		fill(g, x, y, w, h, COLOR_PANEL_INNER);
		g.outline(x, y, w, h, COLOR_BORDER);
	}

	public static void text(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color) {
		g.text(font, value, x, y, color, true);
	}

	public static void text(GuiGraphicsExtractor g, Font font, Component value, int x, int y) {
		g.text(font, value, x, y, COLOR_WHITE, true);
	}

	public static Component styled(String value, int rgb, boolean bold) {
		Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF));
		if (bold) {
			style = style.withBold(true);
		}
		return Component.literal(value).setStyle(style);
	}

	public static void textBold(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color) {
		text(g, font, styled(value, color, true), x, y);
	}

	public static int width(Font font, Component value) {
		return font.width(value);
	}

	public static int widthBold(Font font, String value) {
		return font.width(styled(value, 0xFFFFFF, true));
	}

	public static void textCentered(GuiGraphicsExtractor g, Font font, String value, int cx, int y, int color) {
		int x = cx - font.width(value) / 2;
		text(g, font, value, x, y, color);
	}

	public static void textCentered(GuiGraphicsExtractor g, Font font, Component value, int cx, int y) {
		int x = cx - font.width(value) / 2;
		text(g, font, value, x, y);
	}

	public static void textRight(GuiGraphicsExtractor g, Font font, String value, int rightX, int y, int color) {
		text(g, font, value, rightX - font.width(value), y, color);
	}

	/** Section label + 1px horizontal divider to the right edge. */
	public static int sectionHeader(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w) {
		text(g, font, label, x, y, COLOR_MUTED);
		int lineX = x + font.width(label) + 4;
		int lineY = y + font.lineHeight / 2;
		int lineW = x + w - lineX;
		if (lineW > 0) {
			fill(g, lineX, lineY, lineW, 1, COLOR_BORDER);
		}
		return font.lineHeight + 3;
	}

	public static void progressBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int fillColor) {
		progressBar(g, x, y, w, h, progress, fillColor, false);
	}

	public static void progressBar(
		GuiGraphicsExtractor g,
		int x,
		int y,
		int w,
		int h,
		float progress,
		int fillColor,
		boolean maxedShiny
	) {
		float clamped = Math.max(0.0f, Math.min(1.0f, progress));
		drawXpBarTrack(g, x, y, w, h, COLOR_BAR_BG);
		int filled = Math.max(0, Math.round(w * clamped));
		if (filled <= 0) {
			return;
		}
		if (maxedShiny) {
			drawXpBarGradient(g, x, y, filled, h, COLOR_MAXED_BAR_LEFT, COLOR_MAXED_BAR_RIGHT);
		} else {
			drawXpBarTrack(g, x, y, filled, h, fillColor);
		}
	}

	private static void drawXpBarTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (w <= 2 || h <= 2) {
			fill(g, x, y, w, h, argb);
			return;
		}
		fill(g, x + 1, y, w - 2, h, argb);
		fill(g, x, y + 1, 1, h - 2, argb);
		fill(g, x + w - 1, y + 1, 1, h - 2, argb);
	}

	private static void drawXpBarGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int from, int to) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (w <= 2 || h <= 2) {
			fill(g, x, y, w, h, from);
			return;
		}
		// Segmented fill instead of 1px columns - much cheaper with many maxed bars.
		int segments = Math.min(w - 2, 12);
		int inner = w - 2;
		for (int s = 0; s < segments; s++) {
			int x0 = x + 1 + s * inner / segments;
			int x1 = x + 1 + (s + 1) * inner / segments;
			float t = segments <= 1 ? 0F : (float) s / (float) (segments - 1);
			fill(g, x0, y, Math.max(1, x1 - x0), h, lerpArgb(from, to, t));
		}
		fill(g, x, y + 1, 1, h - 2, from);
		fill(g, x + w - 1, y + 1, 1, h - 2, to);
	}

	private static int lerpArgb(int from, int to, float t) {
		t = Math.max(0F, Math.min(1F, t));
		int a1 = (from >>> 24) & 0xFF;
		int r1 = (from >>> 16) & 0xFF;
		int g1 = (from >>> 8) & 0xFF;
		int b1 = from & 0xFF;
		int a2 = (to >>> 24) & 0xFF;
		int r2 = (to >>> 16) & 0xFF;
		int g2 = (to >>> 8) & 0xFF;
		int b2 = to & 0xFF;
		int a = Math.round(a1 + (a2 - a1) * t);
		int r = Math.round(r1 + (r2 - r1) * t);
		int g = Math.round(g1 + (g2 - g1) * t);
		int b = Math.round(b1 + (b2 - b1) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static void labeledBar(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		float progress,
		int x,
		int y,
		int w,
		int fillColor
	) {
		labeledBar(g, font, label, value, progress, x, y, w, fillColor, false);
	}

	public static void labeledBar(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		float progress,
		int x,
		int y,
		int w,
		int fillColor,
		boolean maxedShiny
	) {
		text(g, font, label, x, y, COLOR_TEXT);
		textRight(g, font, value, x + w, y, COLOR_MUTED);
		int barY = y + font.lineHeight + 2;
		progressBar(g, x, barY, w, BAR_HEIGHT, progress, fillColor, maxedShiny);
	}
}
