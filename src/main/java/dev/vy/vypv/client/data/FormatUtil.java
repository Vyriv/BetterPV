package dev.vy.vypv.client.data;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class FormatUtil {
	private static final DecimalFormat COMMAS = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat ONE_DEC = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat WEIGHT = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.US));

	private FormatUtil() {
	}

	public static String commas(long value) {
		return COMMAS.format(value);
	}

	public static String oneDecimal(double value) {
		return ONE_DEC.format(value);
	}

	public static String weight(double value) {
		return WEIGHT.format(value);
	}

	/** Compact coins for networth display (e.g. 29.167b). */
	public static String shortCoins(double value) {
		return shortCompact(value, false);
	}

	/** Compact XP for overflow tooltips (e.g. 617M, 16.7M). */
	public static String shortXp(double value) {
		return shortCompact(value, true);
	}

	/** Format a dungeon time stored in milliseconds. */
	public static String prettyTime(long ms) {
		if (ms <= 0L) {
			return "N/A";
		}
		long totalSec = ms / 1000L;
		long h = totalSec / 3600L;
		long m = (totalSec % 3600L) / 60L;
		long s = totalSec % 60L;
		if (h > 0L) {
			return h + ":" + pad2(m) + ":" + pad2(s);
		}
		return m + ":" + pad2(s);
	}

	private static String pad2(long value) {
		return value < 10L ? "0" + value : Long.toString(value);
	}

	private static String shortCompact(double value, boolean upperSuffix) {
		double abs = Math.abs(value);
		if (abs >= 1_000_000_000_000L) {
			return trim(value / 1_000_000_000_000L) + (upperSuffix ? "T" : "t");
		}
		if (abs >= 1_000_000_000L) {
			return trim(value / 1_000_000_000L) + (upperSuffix ? "B" : "b");
		}
		if (abs >= 1_000_000L) {
			return trim(value / 1_000_000L) + (upperSuffix ? "M" : "m");
		}
		if (abs >= 1_000L) {
			return trim(value / 1_000L) + (upperSuffix ? "K" : "k");
		}
		return COMMAS.format(Math.round(value));
	}

	private static String trim(double value) {
		String s = String.format(Locale.US, "%.3f", value);
		while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}
}
