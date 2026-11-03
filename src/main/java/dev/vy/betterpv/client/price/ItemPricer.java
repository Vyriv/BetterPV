package dev.vy.betterpv.client.price;

import java.util.Locale;

public final class ItemPricer {
	private static final long VYRIV_MAX_AGE_MILLIS = 30L * 60L * 1000L;
	private static final long SKYHELPER_MAX_AGE_MILLIS = 90L * 60L * 1000L;

	public enum PriceSource {
		VYRIV, SKYHELPER, NONE
	}

	public enum PriceAvailability {
		AVAILABLE, MISSING, STALE
	}

	public record PriceQuote(double price, PriceSource source, PriceAvailability availability) {
		public boolean available() {
			return availability == PriceAvailability.AVAILABLE && price > 0D;
		}
	}

	private ItemPricer() {
	}

	public static void start() {
		VyrivPriceCache.start();
		SkyHelperPriceCache.start();
		HypixelItemsCache.start();
		HypixelCollectionsCache.start();
	}

	public static boolean isReady() {
		return VyrivPriceCache.isReady() || SkyHelperPriceCache.isReady();
	}

	public static void awaitReady(long timeoutMillis) {
		long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
		while (!isReady() && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(100L);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	public static double price(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return 0;
		}
		String id = itemId.toUpperCase(Locale.ROOT);
		Double vyriv = VyrivPriceCache.get(id);
		if (vyriv != null && vyriv > 0) {
			return vyriv;
		}
		Double skyHelper = SkyHelperPriceCache.get(id);
		return skyHelper == null ? 0 : skyHelper;
	}

	/** A recommendation-safe price with its source and cache freshness. */
	public static PriceQuote quote(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return new PriceQuote(0D, PriceSource.NONE, PriceAvailability.MISSING);
		}
		String id = itemId.toUpperCase(Locale.ROOT);
		long now = System.currentTimeMillis();
		Double vyriv = VyrivPriceCache.get(id);
		if (positive(vyriv) && fresh(now, VyrivPriceCache.lastSuccessfulRefreshMillis(), VYRIV_MAX_AGE_MILLIS)) {
			return new PriceQuote(vyriv, PriceSource.VYRIV, PriceAvailability.AVAILABLE);
		}
		Double skyHelper = SkyHelperPriceCache.get(id);
		if (positive(skyHelper)
			&& fresh(now, SkyHelperPriceCache.lastSuccessfulRefreshMillis(), SKYHELPER_MAX_AGE_MILLIS)) {
			return new PriceQuote(skyHelper, PriceSource.SKYHELPER, PriceAvailability.AVAILABLE);
		}
		if (positive(vyriv)) {
			return new PriceQuote(vyriv, PriceSource.VYRIV, PriceAvailability.STALE);
		}
		if (positive(skyHelper)) {
			return new PriceQuote(skyHelper, PriceSource.SKYHELPER, PriceAvailability.STALE);
		}
		return new PriceQuote(0D, PriceSource.NONE, PriceAvailability.MISSING);
	}

	private static boolean fresh(long now, long refreshedAt, long maxAge) {
		return refreshedAt > 0L && now - refreshedAt <= maxAge;
	}

	private static boolean positive(Double value) {
		return value != null && value > 0D && Double.isFinite(value);
	}

	/**
	 * Sack / material valuation: bazaar only (never AH LBIN), then SkyHelper.
	 * Prevents inflated sack totals when the Vyriv feed has a rare AH listing for a material id.
	 */
	public static double materialPrice(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return 0;
		}
		String id = itemId.toUpperCase(Locale.ROOT);
		Double bazaar = VyrivPriceCache.getBazaar(id);
		if (bazaar != null && bazaar > 0) {
			return bazaar;
		}
		Double skyHelper = SkyHelperPriceCache.get(id);
		return skyHelper == null ? 0 : skyHelper;
	}

	/** Bazaar first, then any market price; null when no source prices the item (unlike the 0 above). */
	public static Double knownPrice(String itemId) {
		double bazaar = materialPrice(itemId);
		if (bazaar > 0) {
			return bazaar;
		}
		double any = price(itemId);
		return any > 0 ? any : null;
	}
}
