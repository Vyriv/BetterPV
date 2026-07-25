package dev.vy.betterpv.client.api;

import dev.vy.betterpv.BetterPV;

/**
 * Client config bootstrap. Hypixel traffic always goes through the Cloudflare worker;
 * there is no local API-key path.
 */
public final class BetterPVConfig {
	private BetterPVConfig() {
	}

	public static void load() {
		BetterPV.LOGGER.info("Hypixel: Cloudflare worker proxy only (no local API key)");
	}
}
