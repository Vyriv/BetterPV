package dev.vy.vypv.client.price;

/**
 * Athen first, SkyHelper pricesV2 fallback.
 */
public final class ItemPricer {
	private ItemPricer() {
	}

	public static void start() {
		AthenPriceCache.start();
		SkyHelperPriceCache.start();
		HypixelItemsCache.start();
	}

	public static boolean isReady() {
		return AthenPriceCache.isReady() || SkyHelperPriceCache.isReady();
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
		Double athen = AthenPriceCache.get(itemId);
		if (athen != null && athen > 0) {
			return athen;
		}
		Double skyHelper = SkyHelperPriceCache.get(itemId);
		return skyHelper == null ? 0 : skyHelper;
	}
}
