package dev.vy.betterpv.client.networth;

/**
 * Networth display mode cycled on the home page (left = next, right = prev).
 * <ol>
 *   <li>Normal - full NW</li>
 *   <li>Non-cosmetic - full NW without cosmetics</li>
 *   <li>Unsoulbound - tradeable NW only</li>
 *   <li>Unsoulbound Non-Cosmetic - tradeable NW without cosmetics</li>
 * </ol>
 */
public enum NetworthMode {
	NORMAL("Normal", true, SoulboundFilter.ALL),
	NON_COSMETIC("Non-cosmetic", false, SoulboundFilter.ALL),
	UNSOULBOUND("Unsoulbound", true, SoulboundFilter.ONLY_UNSOULBOUND),
	UNSOULBOUND_NON_COSMETIC("Unsoulbound Non-Cosmetic", false, SoulboundFilter.ONLY_UNSOULBOUND);

	public enum SoulboundFilter {
		ALL,
		ONLY_SOULBOUND,
		ONLY_UNSOULBOUND;

		public boolean accepts(boolean soulbound) {
			return switch (this) {
				case ALL -> true;
				case ONLY_SOULBOUND -> soulbound;
				case ONLY_UNSOULBOUND -> !soulbound;
			};
		}

		/** Purse / bank are liquid coins - only in full and un-soulbound views. */
		public boolean includesLiquid() {
			return this != ONLY_SOULBOUND;
		}
	}

	private final String display;
	private final boolean includeCosmetics;
	private final SoulboundFilter soulboundFilter;

	NetworthMode(String display, boolean includeCosmetics, SoulboundFilter soulboundFilter) {
		this.display = display;
		this.includeCosmetics = includeCosmetics;
		this.soulboundFilter = soulboundFilter;
	}

	public String display() {
		return this.display;
	}

	public boolean includeCosmetics() {
		return this.includeCosmetics;
	}

	public SoulboundFilter soulboundFilter() {
		return this.soulboundFilter;
	}

	public NetworthMode next() {
		NetworthMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public NetworthMode prev() {
		NetworthMode[] values = values();
		return values[(ordinal() + values.length - 1) % values.length];
	}
}
