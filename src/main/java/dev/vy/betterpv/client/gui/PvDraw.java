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

	/** Cosmetics-inspired loop + vivid HSV rainbow for the Konami easter egg. */
	private static final float EASTER_EGG_SPEED = 0.12F;

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

	/** Draw text scaled about its top-left corner (e.g. footer credits). */
	public static void textScaled(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, float scale) {
		if (scale == 1.0F) {
			text(g, font, value, x, y, color);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, value, 0, 0, color, true);
		g.pose().popMatrix();
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
		// Round the progressing edge only when fill covers the track; otherwise a mid-bar
		// “pill tip” looks broken — especially on short fills like Social.
		boolean roundRight = filled >= w;
		if (MoulberryMode.isActive()) {
			drawXpBarAnimatedGradient(g, x, y, filled, h, roundRight);
		} else if (maxedShiny) {
			drawXpBarGradient(g, x, y, filled, h, COLOR_MAXED_BAR_LEFT, COLOR_MAXED_BAR_RIGHT, roundRight);
		} else {
			drawPillSolid(g, x, y, filled, h, fillColor, true, roundRight);
		}
	}

	private static void drawXpBarTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
		drawPillSolid(g, x, y, w, h, argb, true, true);
	}

	private static void drawXpBarGradient(
		GuiGraphicsExtractor g, int x, int y, int w, int h, int from, int to, boolean roundRight
	) {
		drawPillGradient(g, x, y, w, h, t -> lerpArgb(from, to, t), true, roundRight);
	}

	/** Scrolling HSV rainbow — tips sample the same t-map as the body so ends stay in sync. */
	private static void drawXpBarAnimatedGradient(
		GuiGraphicsExtractor g, int x, int y, int w, int h, boolean roundRight
	) {
		double time = System.currentTimeMillis() / 50.0D;
		float offset = (float) positiveModulo(time * EASTER_EGG_SPEED, 1.0D);
		final float span = 0.85F;
		drawPillGradient(
			g, x, y, w, h,
			t -> rainbowArgb(positiveModulo(t * span + offset, 1.0F)),
			true,
			roundRight
		);
	}

	@FunctionalInterface
	private interface BarColorAt {
		int at(float t);
	}

	/**
	 * Mild rounded-rect ends for short XP bars (h≈6).
	 * Only a 1px corner cut — a 2px “capsule tip” on a 6px bar reads as a point, not a curve.
	 * Low fill widths still keep a soft left tip; the right tip is optional (flat progress edge).
	 */
	private static void drawPillSolid(
		GuiGraphicsExtractor g, int x, int y, int w, int h, int argb, boolean roundLeft, boolean roundRight
	) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (h < 3) {
			fill(g, x, y, w, h, argb);
			return;
		}
		if (w == 1) {
			fill(g, x, y + 1, 1, h - 2, argb);
			return;
		}
		int leftInset = roundLeft ? 1 : 0;
		int rightInset = roundRight ? 1 : 0;
		int bodyW = w - leftInset - rightInset;
		if (bodyW > 0) {
			fill(g, x + leftInset, y, bodyW, h, argb);
		}
		if (roundLeft) {
			fill(g, x, y + 1, 1, h - 2, argb);
		}
		if (roundRight) {
			fill(g, x + w - 1, y + 1, 1, h - 2, argb);
		}
	}

	/**
	 * Gradient / rainbow with the same 1px rounded ends. Cap colours use the bar’s t at that
	 * pixel so rainbow tips stay in sync with the fill.
	 */
	private static void drawPillGradient(
		GuiGraphicsExtractor g,
		int x,
		int y,
		int w,
		int h,
		BarColorAt colorAt,
		boolean roundLeft,
		boolean roundRight
	) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (h < 3) {
			fill(g, x, y, w, h, colorAt.at(0.5F));
			return;
		}

		java.util.function.IntUnaryOperator atPx = px -> {
			float t = w <= 1 ? 0F : (float) px / (float) (w - 1);
			return colorAt.at(t);
		};

		if (w == 1) {
			fill(g, x, y + 1, 1, h - 2, atPx.applyAsInt(0));
			return;
		}

		int fromCol = roundLeft ? 1 : 0;
		int toCol = roundRight ? w - 1 : w;
		if (roundLeft) {
			fill(g, x, y + 1, 1, h - 2, atPx.applyAsInt(0));
		}
		if (roundRight) {
			fill(g, x + w - 1, y + 1, 1, h - 2, atPx.applyAsInt(w - 1));
		}
		fillGradientSpan(g, x, y, w, h, fromCol, toCol, colorAt);
	}

	/** Horizontal gradient across [x+fromCol, x+toCol). Plenty of segments, still far cheaper than per-pixel. */
	private static void fillGradientSpan(
		GuiGraphicsExtractor g,
		int barX,
		int y,
		int barW,
		int h,
		int fromCol,
		int toCol,
		BarColorAt colorAt
	) {
		int span = toCol - fromCol;
		if (span <= 0) {
			return;
		}
		int segments = Math.min(span, 64);
		for (int s = 0; s < segments; s++) {
			int x0 = barX + fromCol + s * span / segments;
			int x1 = barX + fromCol + (s + 1) * span / segments;
			int midPx = Math.max(0, Math.min(barW - 1, (x0 + x1 - 1) / 2 - barX));
			float t = barW <= 1 ? 0F : (float) midPx / (float) (barW - 1);
			fill(g, x0, y, Math.max(1, x1 - x0), h, colorAt.at(t));
		}
	}

	/** Hue wheel colour; blends toward cosmetics purple so it still nods to the name gradient. */
	private static int rainbowArgb(float hue) {
		float h = positiveModulo(hue, 1.0F);
		int rgb = java.awt.Color.HSBtoRGB(h, 0.85F, 1.0F) & 0xFFFFFF;
		// Soft pull toward cosmetics lilac so it isn't a totally foreign palette.
		int cosmetics = COLOR_MAXED_BAR_LEFT & 0xFFFFFF;
		return lerpArgb(rgb | 0xFF000000, cosmetics | 0xFF000000, 0.18F);
	}

	private static float positiveModulo(float value, float modulus) {
		float remainder = value % modulus;
		return remainder < 0.0F ? remainder + modulus : remainder;
	}

	private static double positiveModulo(double value, double modulus) {
		double remainder = value % modulus;
		return remainder < 0.0D ? remainder + modulus : remainder;
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
