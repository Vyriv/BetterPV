package dev.vy.betterpv.client.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Seymour collector maths: CIE76 colour difference in CIELAB (sRGB, D65 white), the collector tiers
 * (T1&lt; below ΔE 1, T1 below 2, T2 below 5, T3 otherwise) and hex patterns collectors look for.
 */
public final class SeymourColors {
	public enum Piece {
		HELMET, CHESTPLATE, LEGGINGS, BOOTS
	}

	/** Lower ranks are better. Each tier holds ΔE values strictly below its upper bound. */
	public enum Tier {
		T1_PLUS("T1<", 1.0D), T1("T1", 2.0D), T2("T2", 5.0D), T3("T3", Double.POSITIVE_INFINITY);

		private final String label;
		private final double upperBound;

		Tier(String label, double upperBound) {
			this.label = label;
			this.upperBound = upperBound;
		}

		public String label() {
			return this.label;
		}

		public static Tier of(double deltaE) {
			for (Tier tier : values()) {
				if (deltaE < tier.upperBound) {
					return tier;
				}
			}
			return T3;
		}
	}

	public enum Pattern {
		PAIRED("Paired"), REPEATING("Repeating"), PALINDROME("Palindrome"), ALIGNED("AxBxCx");

		private final String label;

		Pattern(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}

	/** A named reference colour. {@code pieces} limits it to those armour slots; empty means any piece. */
	public record Target(String name, int rgb, Set<Piece> pieces, boolean fade, double[] lab) {
		public Target(String name, int rgb, Set<Piece> pieces, boolean fade) {
			this(name, rgb & 0xFFFFFF, Set.copyOf(pieces), fade, SeymourColors.lab(rgb));
		}

		boolean fits(Piece piece) {
			return this.pieces.isEmpty() || piece == null || this.pieces.contains(piece);
		}
	}

	/** {@code fake} means the reference belongs to a different armour slot than the piece being checked. */
	public record Match(Target target, double deltaE, Tier tier, boolean fake) {
	}

	private static final Set<String> IDS = Set.of("VELVET_TOP_HAT", "CASHMERE_JACKET", "SATIN_TROUSERS", "OXFORD_SHOES");

	private SeymourColors() {
	}

	public static boolean isSeymour(String id) {
		return id != null && IDS.contains(id.toUpperCase(Locale.ROOT));
	}

	public static Piece pieceOf(String id) {
		if (id == null) {
			return null;
		}
		return switch (id.toUpperCase(Locale.ROOT)) {
			case "VELVET_TOP_HAT" -> Piece.HELMET;
			case "CASHMERE_JACKET" -> Piece.CHESTPLATE;
			case "SATIN_TROUSERS" -> Piece.LEGGINGS;
			case "OXFORD_SHOES" -> Piece.BOOTS;
			default -> null;
		};
	}

	/**
	 * Closest reference ranked by tier, then solid before fade frame, then ΔE. References for another slot are
	 * not filtered out; they are flagged fake. Null when there are no targets.
	 */
	public static Match closest(int rgb, Piece piece, Collection<Target> targets) {
		double[] lab = lab(rgb);
		Match best = null;
		for (Target target : targets) {
			double delta = deltaE(lab, target.lab());
			Match match = new Match(target, delta, Tier.of(delta), !target.fits(piece));
			if (best == null || better(match, best)) {
				best = match;
			}
		}
		return best;
	}

	private static boolean better(Match a, Match b) {
		if (a.tier() != b.tier()) {
			return a.tier().ordinal() < b.tier().ordinal();
		}
		if (a.target().fade() != b.target().fade()) {
			return !a.target().fade();
		}
		return a.deltaE() < b.deltaE();
	}

	public static double deltaE(int rgbA, int rgbB) {
		return deltaE(lab(rgbA), lab(rgbB));
	}

	static double deltaE(double[] a, double[] b) {
		double dl = a[0] - b[0];
		double da = a[1] - b[1];
		double db = a[2] - b[2];
		return Math.sqrt(dl * dl + da * da + db * db);
	}

	static double[] lab(int rgb) {
		double r = linear((rgb >> 16) & 0xFF);
		double g = linear((rgb >> 8) & 0xFF);
		double b = linear(rgb & 0xFF);
		double x = (r * 0.4124564D + g * 0.3575761D + b * 0.1804375D) / 0.95047D;
		double y = r * 0.2126729D + g * 0.7151522D + b * 0.0721750D;
		double z = (r * 0.0193339D + g * 0.1191920D + b * 0.9503041D) / 1.08883D;
		double fx = labCurve(x);
		double fy = labCurve(y);
		double fz = labCurve(z);
		return new double[] {116D * fy - 16D, 500D * (fx - fy), 200D * (fy - fz)};
	}

	private static double linear(int channel) {
		double c = channel / 255D;
		return c > 0.04045D ? Math.pow((c + 0.055D) / 1.055D, 2.4D) : c / 12.92D;
	}

	private static double labCurve(double t) {
		return t > 0.008856D ? Math.cbrt(t) : 7.787D * t + 16D / 116D;
	}

	/** Every pattern the hex shows, in a fixed order. */
	public static List<Pattern> patterns(int rgb) {
		String hex = ArmorColorClassifier.hex(rgb);
		List<Pattern> out = new ArrayList<>();
		if (hex.charAt(0) == hex.charAt(1) && hex.charAt(2) == hex.charAt(3) && hex.charAt(4) == hex.charAt(5)) {
			out.add(Pattern.PAIRED);
		}
		if (hex.regionMatches(0, hex, 3, 3)) {
			out.add(Pattern.REPEATING);
		}
		if (hex.charAt(0) == hex.charAt(5) && hex.charAt(1) == hex.charAt(4) && hex.charAt(2) == hex.charAt(3)) {
			out.add(Pattern.PALINDROME);
		}
		if (hex.charAt(0) == hex.charAt(2) && hex.charAt(2) == hex.charAt(4)) {
			out.add(Pattern.ALIGNED);
		}
		return out;
	}
}
