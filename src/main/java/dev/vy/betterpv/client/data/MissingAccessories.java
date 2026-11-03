package dev.vy.betterpv.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.BetterPV;
import dev.vy.betterpv.client.networth.NbtAttrs;
import dev.vy.betterpv.client.neu.NeuRepoCache;
import dev.vy.betterpv.client.price.HypixelItemsCache;
import dev.vy.betterpv.client.price.ItemPricer;
import dev.vy.betterpv.client.price.ItemPricer.PriceAvailability;
import dev.vy.betterpv.client.price.ItemPricer.PriceQuote;
import dev.vy.betterpv.client.price.ItemPricer.PriceSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cost-ranked Magical Power upgrades for the accessory bag.
 *
 * <p>Each family is planned to its highest reachable MP (top tier, recombobulated where allowed), and every
 * step on that path becomes one entry. "Cost to max" and "MP to max" are sums over those same entries.
 * Ownership comes from every storage location, but only accessories in the bag contribute MP.
 */
public final class MissingAccessories {
	public static final String ACCESSORY_BAG = "Accessory Bag";
	static final double RIFT_PRISM_IMBUE_FEE = 200_000D;
	private static final int MAX_CRAFT_DEPTH = 8;
	private static final int PANDORA_MAX_MP = 22;
	private static final int RIFT_PRISM_IMBUED_MP = 11;
	private static final String RECOMBOBULATOR = "RECOMBOBULATOR_3000";
	private static final Pattern PANDORA_CHALLENGES = Pattern.compile(
		"challenges? completed\\s*:?\\s*(\\d+)\\s*/\\s*20",
		Pattern.CASE_INSENSITIVE
	);

	public enum UpgradeType {
		PURCHASE, TIER_UPGRADE, MOVE_TO_BAG, RECOMBOBULATE, PANDORA_PROGRESSION, RIFT_PRISM_IMBUEMENT, UNKNOWN_STATE
	}

	public enum PriceStatus {
		AVAILABLE, MISSING, STALE, NOT_PRICED, UNKNOWN_STATE
	}

	public enum PricingSource {
		VYRIV, SKYHELPER, VYRIV_AND_SKYHELPER, VYRIV_AND_NPC, SKYHELPER_AND_NPC, NPC, GAMEPLAY, OWNED, NONE
	}

	/**
	 * {@code CRAFT} prices the materials of an in-game upgrade (crafting, Forge or NPC) that consumes the owned
	 * lower tier. {@code MARKET} is a separate purchase, from the market or a recorded NPC price or trade.
	 * {@code UNKNOWN} is an upgrade with no recorded method, so its cost is unavailable.
	 */
	public enum CostBasis {
		MARKET, CRAFT, UNKNOWN
	}

	@FunctionalInterface
	interface PriceLookup {
		PriceQuote quote(String itemId);
	}

	/** Recorded ways to obtain an item; empty when none are known. */
	@FunctionalInterface
	interface RecipeLookup {
		List<AccessoryUpgradeMethods.Method> methods(String itemId);
	}

	/** One owned accessory with its profile-derived rarity, upgrade state, and decoded location. */
	public record OwnedAccessory(String id, String tier, boolean recombobulated, List<String> lore, String location) {
		public OwnedAccessory {
			id = normalizeId(id);
			tier = tier == null ? "" : tier.trim().toUpperCase(Locale.ROOT);
			lore = lore == null ? List.of() : List.copyOf(lore);
			location = location == null || location.isBlank() ? "Unknown" : location;
		}

		public OwnedAccessory(String id, String tier, boolean recombobulated, List<String> lore) {
			this(id, tier, recombobulated, lore, ACCESSORY_BAG);
		}

		public static OwnedAccessory fromSlot(InventorySnapshot.Slot slot) {
			return fromSlot(slot, ACCESSORY_BAG);
		}

		public static OwnedAccessory fromSlot(InventorySnapshot.Slot slot, String location) {
			if (slot == null || slot.isEmpty()) {
				return null;
			}
			String id = normalizeId(slot.id());
			if (!looksLikeAccessory(slot.lore())) {
				return null;
			}
			boolean recombobulated = NbtAttrs.intValue(slot.extraAttributes(), "rarity_upgrades", 0) > 0
				&& !NbtAttrs.has(slot.extraAttributes(), "item_tier");
			return new OwnedAccessory(
				id, MagicalPowerCalculator.effectiveTier(id, slot), recombobulated, slot.lore(), location
			);
		}

		/** Hypixel Magical Power only reads the Accessory Bag; other locations are owned but inactive. */
		public boolean inBag() {
			return ACCESSORY_BAG.equals(this.location);
		}
	}

	/** Tri-state prism status uses null when the profile field is absent. */
	public record Profile(Collection<OwnedAccessory> accessories, boolean inventoryKnown, Boolean riftPrismConsumed) {
		public Profile {
			accessories = accessories == null ? List.of() : List.copyOf(accessories);
		}
	}

	/**
	 * {@code soldId} is the owned accessory this step replaces (consumed by the recipe for {@link CostBasis#CRAFT}).
	 * {@code soldPrice} is never credited by the planner and stays 0. {@code prerequisite} names the step that
	 * must happen first (buying, upgrading or moving the accessory), or null when the step stands alone.
	 */
	public record Entry(
		UpgradeType type, String id, String name, String tier, String currentState,
		double price, int mpGain, String soldId, String soldName, double soldPrice, boolean soulbound,
		PricingSource pricingSource, PriceStatus priceStatus, String conflictGroup, String prerequisite,
		CostBasis costBasis
	) {
		public Entry {
			costBasis = costBasis == null ? CostBasis.MARKET : costBasis;
		}

		/** Moving an owned accessory into the bag is a known, free step. */
		public boolean priced() {
			return this.priceStatus == PriceStatus.AVAILABLE
				&& (this.price > 0D || this.type == UpgradeType.MOVE_TO_BAG);
		}

		public double netCost() {
			return Math.max(0D, this.price - Math.max(0D, this.soldPrice));
		}

		public double coinsPerMp() {
			return priced() && this.mpGain > 0 ? netCost() / this.mpGain : Double.MAX_VALUE;
		}

		public boolean unknownState() {
			return this.priceStatus == PriceStatus.UNKNOWN_STATE || this.type == UpgradeType.UNKNOWN_STATE;
		}
	}

	/**
	 * {@code mpToMax} is the summed MP of every planned step and {@code costToMax} the summed net cost of the
	 * priced ones, so both describe the same final bag. {@code unpricedTop} counts steps without a usable price.
	 */
	public record Result(List<Entry> entries, double costToMax, int mpToMax, int unpricedTop, int unknownStates) {
		public static Result empty() {
			return new Result(List.of(), 0D, 0, 0, 0);
		}
	}

	private MissingAccessories() {
	}

	/** Compatibility entry point for callers that only know ids. Their rarity state remains unknown. */
	public static Result compute(Collection<String> ownedIds) {
		List<OwnedAccessory> owned = new ArrayList<>();
		if (ownedIds != null) {
			for (String id : ownedIds) {
				JsonObject def = HypixelItemsCache.get(id);
				owned.add(new OwnedAccessory(id, tierOf(def == null ? "" : str(def.get("tier"))), false, List.of()));
			}
		}
		return compute(new Profile(owned, true, null));
	}

	public static Result compute(Profile profile) {
		Map<String, List<AccessoryUpgradeMethods.Method>> methods = repoMethods();
		return computeQuoted(
			profile, HypixelItemsCache.allItems(), NeuRepoCache.talismanUpgrades(),
			NeuRepoCache.ignoredTalismans(), ItemPricer::quote, id -> methods.getOrDefault(id, List.of())
		);
	}

	private static volatile Map<String, List<AccessoryUpgradeMethods.Method>> repoMethods = Map.of();
	private static volatile int repoMethodsSize = -1;

	private static Map<String, List<AccessoryUpgradeMethods.Method>> repoMethods() {
		int size = NeuRepoCache.size();
		if (size != repoMethodsSize) {
			repoMethods = AccessoryUpgradeMethods.index(List.copyOf(NeuRepoCache.items()));
			repoMethodsSize = size;
		}
		return repoMethods;
	}

	static Result compute(
		Profile profile, Collection<JsonObject> itemDefinitions, Map<String, List<String>> upgrades,
		Set<String> ignored, Function<String, Double> prices
	) {
		return compute(profile, itemDefinitions, upgrades, ignored, prices, Map.of());
	}

	static Result compute(
		Profile profile, Collection<JsonObject> itemDefinitions, Map<String, List<String>> upgrades,
		Set<String> ignored, Function<String, Double> prices, Map<String, JsonObject> recipes
	) {
		Map<String, JsonObject> safeRecipes = recipes == null ? Map.of() : recipes;
		return computeQuoted(profile, itemDefinitions, upgrades, ignored, quotes(prices), id -> {
			AccessoryUpgradeMethods.Method method = AccessoryUpgradeMethods.grid(safeRecipes.get(id));
			return method == null ? List.of() : List.of(method);
		});
	}

	static PriceLookup quotes(Function<String, Double> prices) {
		Function<String, Double> safePrices = prices == null ? id -> 0D : prices;
		return id -> {
			double price = positive(safePrices.apply(id));
			return new PriceQuote(
				price, price > 0D ? PriceSource.VYRIV : PriceSource.NONE,
				price > 0D ? PriceAvailability.AVAILABLE : PriceAvailability.MISSING
			);
		};
	}

	static Result computeQuoted(
		Profile profile, Collection<JsonObject> itemDefinitions, Map<String, List<String>> upgrades,
		Set<String> ignored, PriceLookup prices
	) {
		return computeQuoted(profile, itemDefinitions, upgrades, ignored, prices, id -> List.of());
	}

	static Result computeQuoted(
		Profile profile, Collection<JsonObject> itemDefinitions, Map<String, List<String>> upgrades,
		Set<String> ignored, PriceLookup prices, RecipeLookup recipes
	) {
		if (profile == null || itemDefinitions == null || itemDefinitions.isEmpty()) {
			return Result.empty();
		}
		Map<String, List<String>> upgradeMap = upgrades == null ? Map.of() : upgrades;
		Set<String> ignoredIds = ignored == null ? Set.of() : ignored;
		PriceLookup priceLookup = prices == null ? id -> missingQuote() : prices;
		RecipeLookup recipeLookup = recipes == null ? id -> List.of() : recipes;

		Map<String, CatalogItem> catalog = accessoryCatalog(itemDefinitions, ignoredIds);
		Chains chains = Chains.build(upgradeMap);
		Map<String, List<CatalogItem>> membersByChain = new LinkedHashMap<>();
		for (CatalogItem item : catalog.values()) {
			membersByChain.computeIfAbsent(chains.key(item.id()), key -> new ArrayList<>()).add(item);
		}
		Map<String, List<OwnedAccessory>> ownedByChain = new LinkedHashMap<>();
		for (OwnedAccessory accessory : profile.accessories()) {
			if (accessory != null && !accessory.id().isBlank()) {
				ownedByChain.computeIfAbsent(chains.key(accessory.id()), key -> new ArrayList<>()).add(accessory);
			}
		}

		String pandoraChain = chains.key("PANDORAS_BOX");
		String riftChain = chains.key("RIFT_PRISM");
		List<Entry> entries = new ArrayList<>();
		if (profile.inventoryKnown()) {
			for (Map.Entry<String, List<CatalogItem>> chain : membersByChain.entrySet()) {
				String key = chain.getKey();
				if (key.equals(pandoraChain) || key.equals(riftChain)) {
					continue;
				}
				entries.addAll(planChain(
					key, chain.getValue(), ownedByChain.getOrDefault(key, List.of()), chains, catalog, priceLookup,
					recipeLookup
				));
			}
		}
		planPandora(entries, profile, ownedByChain.getOrDefault(pandoraChain, List.of()), catalog, priceLookup);
		planRiftPrism(entries, profile, ownedByChain.getOrDefault(riftChain, List.of()), catalog, priceLookup);

		entries = ordered(entries, byCoinsPerMp());
		int mpToMax = entries.stream().filter(entry -> !entry.unknownState()).mapToInt(Entry::mpGain).sum();
		double costToMax = entries.stream().filter(Entry::priced).mapToDouble(Entry::netCost).sum();
		int unpriced = (int) entries.stream()
			.filter(entry -> entry.priceStatus() == PriceStatus.MISSING
				|| entry.priceStatus() == PriceStatus.STALE || entry.priceStatus() == PriceStatus.NOT_PRICED)
			.count();
		int unknown = (int) entries.stream().filter(Entry::unknownState).count();
		return new Result(List.copyOf(entries), costToMax, mpToMax, unpriced, unknown);
	}

	/**
	 * Steps from the family's current bag MP to its best reachable MP: optionally move an owned copy into the
	 * bag or buy/craft the target, then recombobulate it when that adds MP.
	 */
	private static List<Entry> planChain(
		String key, List<CatalogItem> members, List<OwnedAccessory> ownedAnywhere, Chains chains,
		Map<String, CatalogItem> catalog, PriceLookup prices, RecipeLookup recipes
	) {
		// Ignored or Rift-only ids give no MP, so they neither count as active nor get moved.
		List<OwnedAccessory> owned = ownedAnywhere.stream().filter(copy -> catalog.containsKey(copy.id())).toList();
		OwnedAccessory active = null;
		for (OwnedAccessory copy : owned) {
			if (copy.inBag() && (active == null || mp(copy) > mp(active)
				|| (mp(copy) == mp(active) && chains.rank(copy.id()) > chains.rank(active.id())))) {
				active = copy;
			}
		}
		int currentMp = active == null ? 0 : mp(active);

		Option best = null;
		Set<String> ownedIds = new HashSet<>();
		for (OwnedAccessory copy : owned) {
			ownedIds.add(copy.id());
			best = better(best, Option.owned(copy, catalog.get(copy.id()), chains.rank(copy.id())));
		}
		// Every abicase shares one family; owning any of them already covers it.
		boolean abicaseCovered = !owned.isEmpty() && members.stream().anyMatch(item -> item.id().contains("ABICASE"));
		if (!abicaseCovered) {
			for (CatalogItem item : members) {
				if (!ownedIds.contains(item.id())) {
					best = better(best, Option.purchase(item, chains.rank(item.id())));
				}
			}
		}
		if (best == null || best.finalMp() <= currentMp) {
			logPlan(key, active, best, "nothing to do");
			return List.of();
		}

		String group = "family:" + key;
		String name = best.item() == null ? nameOf(best.id(), catalog) : best.item().name();
		List<Entry> out = new ArrayList<>();
		if (best.copy() != null) {
			OwnedAccessory copy = best.copy();
			Entry move = null;
			int level = currentMp;
			if (!copy.inBag() && best.baseMp() > currentMp) {
				move = moveEntry(copy, name, best.baseMp() - currentMp, group, best.item(), null, catalog);
				out.add(move);
				level = best.baseMp();
			}
			if (best.recombobulate()) {
				String recombName;
				String state;
				if (copy.inBag()) {
					recombName = "Recombobulate " + name;
					state = ownedLabel(copy, catalog);
				} else if (move != null) {
					recombName = "Then recombobulate " + name;
					state = "Move " + name + " to bag first";
				} else {
					recombName = "Recombobulate " + name + ", then move to bag";
					state = ownedLabel(copy, catalog);
				}
				out.add(recombEntry(
					recombName, best.finalTier(), best.finalMp() - level, state, prices, group,
					move == null ? null : move.name()
				));
			}
		} else {
			CatalogItem item = best.item();
			UpgradeCost cost = upgradeCost(item, active, owned, key, chains, catalog, prices, recipes);
			OwnedAccessory replaced = cost.replaced();
			UpgradeType type = replaced == null ? UpgradeType.PURCHASE : UpgradeType.TIER_UPGRADE;
			OwnedAccessory shown = replaced != null ? replaced : bestCopy(owned, false);
			String state = shown == null ? "Not owned" : ownedLabel(shown, catalog);
			String soldId = replaced == null ? null : replaced.id();
			String soldName = replaced == null ? null : nameOf(replaced.id(), catalog);
			int purchaseGain = best.baseMp() - currentMp;
			if (purchaseGain <= 0) {
				// Buying alone adds nothing over the bag tier; only the recombobulated copy does.
				PriceQuote recombQuote = quote(prices, RECOMBOBULATOR);
				out.add(new Entry(
					type, item.id(), item.name() + " + Recombobulator", best.finalTier(), state,
					cost.price() + recombQuote.price(), best.finalMp() - currentMp, soldId, soldName, 0D,
					item.soulbound(), cost.source(), combinedStatus(cost.status(), priceStatus(recombQuote)),
					group, null, cost.basis()
				));
			} else {
				Entry purchase = new Entry(
					type, item.id(), item.name(), item.tier(), state, cost.price(), purchaseGain,
					soldId, soldName, 0D, item.soulbound(), cost.source(), cost.status(), group, null,
					cost.basis()
				);
				out.add(purchase);
				if (best.recombobulate()) {
					String firstStep = type == UpgradeType.PURCHASE ? "Buy " + name + " first"
						: cost.basis() == CostBasis.CRAFT ? "Craft " + name + " first"
						: "Upgrade to " + name + " first";
					out.add(recombEntry(
						"Then recombobulate " + name, best.finalTier(), best.finalMp() - best.baseMp(), firstStep,
						prices, group, purchase.name()
					));
				}
			}
		}
		logPlan(key, active, best, out.size() + " step(s)");
		return out;
	}

	/**
	 * Cost of reaching {@code item}. With nothing in the family owned it is a plain purchase. Otherwise the
	 * in-game upgrade that consumes the owned tier is costed (crafting, Forge or NPC, recursing through tiers
	 * the player skips), or a recorded separate way to obtain it (an NPC price or trade). When neither is known
	 * the cost is unavailable; buying the new tier and selling the old one is never assumed.
	 */
	private static UpgradeCost upgradeCost(
		CatalogItem item, OwnedAccessory active, List<OwnedAccessory> owned, String chainKey, Chains chains,
		Map<String, CatalogItem> catalog, PriceLookup prices, RecipeLookup recipes
	) {
		if (owned.isEmpty()) {
			PriceQuote itemQuote = quote(prices, item.id());
			return new UpgradeCost(
				itemQuote.price(), priceStatus(itemQuote), pricingSource(itemQuote.source(), false), CostBasis.MARKET,
				null
			);
		}
		Map<String, OwnedAccessory> ownedById = new HashMap<>();
		for (OwnedAccessory copy : owned) {
			OwnedAccessory existing = ownedById.get(copy.id());
			if (existing == null || (copy.inBag() && !existing.inBag())) {
				ownedById.put(copy.id(), copy);
			}
		}
		Route upgrade = upgradeRoute(item.id(), ownedById.keySet(), chainKey, chains, prices, recipes, 0);
		if (upgrade != null && upgrade.available()) {
			return new UpgradeCost(
				upgrade.cost(), PriceStatus.AVAILABLE, routeSource(upgrade), CostBasis.CRAFT,
				ownedById.get(upgrade.consumedId())
			);
		}
		Route separate = null;
		for (AccessoryUpgradeMethods.Method method : methods(recipes, item.id())) {
			if (method.inputs().keySet().stream().noneMatch(id -> sameChain(id, item.id(), chainKey, chains))) {
				separate = cheaper(separate, methodCost(method, item.id(), chainKey, chains, prices, recipes, 1));
			}
		}
		if (separate != null && separate.available()) {
			return new UpgradeCost(separate.cost(), PriceStatus.AVAILABLE, routeSource(separate), CostBasis.MARKET, active);
		}
		if (upgrade != null) {
			return new UpgradeCost(
				0D, PriceStatus.MISSING, PricingSource.NONE, CostBasis.CRAFT, ownedById.get(upgrade.consumedId())
			);
		}
		return new UpgradeCost(0D, PriceStatus.MISSING, PricingSource.NONE, CostBasis.UNKNOWN, active);
	}

	/**
	 * Cheapest recorded method for {@code target} that consumes an owned family member, recursing through lower
	 * tiers the player does not own (talisman to ring to artifact). When a method needs several copies of the
	 * lower tier (four Emperor's Rings, four Master Skulls), one comes from the owned chain and the rest cost
	 * whichever is cheaper: buying them or making them. Null when no method reaches an owned copy.
	 */
	private static Route upgradeRoute(
		String target, Set<String> ownedIds, String chainKey, Chains chains, PriceLookup prices,
		RecipeLookup recipes, int depth
	) {
		if (depth > MAX_CRAFT_DEPTH) {
			return null;
		}
		Route best = null;
		for (AccessoryUpgradeMethods.Method method : methods(recipes, target)) {
			Route lower = null;
			Tally tally = new Tally(method.coins());
			for (Map.Entry<String, Integer> input : method.inputs().entrySet()) {
				String id = input.getKey();
				int qty = input.getValue();
				boolean family = sameChain(id, target, chainKey, chains);
				if (lower == null && family) {
					lower = ownedIds.contains(id)
						? new Route(id, 0D, true, false, Set.of())
						: upgradeRoute(id, ownedIds, chainKey, chains, prices, recipes, depth + 1);
					if (lower != null) {
						qty--;
					}
				}
				if (qty > 0) {
					tally.add(family ? unitCost(id, chainKey, chains, prices, recipes, depth + 1) : market(prices, id), qty);
				}
			}
			if (lower != null) {
				tally.add(lower, 1);
				best = cheaper(best, tally.route(lower.consumedId()));
			}
		}
		return best;
	}

	/** Cheaper of buying one family tier or making it from scratch; null when neither is priced. */
	private static Route unitCost(
		String id, String chainKey, Chains chains, PriceLookup prices, RecipeLookup recipes, int depth
	) {
		Route best = market(prices, id);
		if (depth <= MAX_CRAFT_DEPTH) {
			for (AccessoryUpgradeMethods.Method method : methods(recipes, id)) {
				best = cheaper(best, methodCost(method, id, chainKey, chains, prices, recipes, depth));
			}
		}
		return best != null && best.available() ? best : null;
	}

	/** Full cost of one method with every input bought or made; null when an input cannot be priced. */
	private static Route methodCost(
		AccessoryUpgradeMethods.Method method, String target, String chainKey, Chains chains, PriceLookup prices,
		RecipeLookup recipes, int depth
	) {
		Tally tally = new Tally(method.coins());
		for (Map.Entry<String, Integer> input : method.inputs().entrySet()) {
			String id = input.getKey();
			tally.add(
				sameChain(id, target, chainKey, chains) ? unitCost(id, chainKey, chains, prices, recipes, depth + 1)
					: market(prices, id),
				input.getValue()
			);
		}
		Route route = tally.route(null);
		return route.available() ? route : null;
	}

	private static Route market(PriceLookup prices, String id) {
		PriceQuote quote = quote(prices, id);
		return quote.available() ? new Route(null, quote.price(), true, false, Set.of(quote.source())) : null;
	}

	private static boolean sameChain(String id, String target, String chainKey, Chains chains) {
		return !id.equals(target) && chains.key(id).equals(chainKey);
	}

	private static List<AccessoryUpgradeMethods.Method> methods(RecipeLookup recipes, String id) {
		List<AccessoryUpgradeMethods.Method> methods = recipes.methods(id);
		return methods == null ? List.of() : methods;
	}

	/** Priced routes beat unpriced ones, then the cheaper wins. */
	private static Route cheaper(Route a, Route b) {
		if (a == null || b == null) {
			return a == null ? b : a;
		}
		if (a.priced() != b.priced()) {
			return a.priced() ? a : b;
		}
		return b.cost() < a.cost() ? b : a;
	}

	private static PricingSource routeSource(Route route) {
		boolean vyriv = route.sources().contains(PriceSource.VYRIV);
		boolean skyHelper = route.sources().contains(PriceSource.SKYHELPER);
		if (vyriv && skyHelper) {
			return PricingSource.VYRIV_AND_SKYHELPER;
		}
		if (vyriv || skyHelper) {
			PricingSource market = vyriv ? PricingSource.VYRIV : PricingSource.SKYHELPER;
			return route.npc() ? (vyriv ? PricingSource.VYRIV_AND_NPC : PricingSource.SKYHELPER_AND_NPC) : market;
		}
		return route.npc() ? PricingSource.NPC : PricingSource.NONE;
	}

	private record UpgradeCost(
		double price, PriceStatus status, PricingSource source, CostBasis basis, OwnedAccessory replaced
	) {
	}

	/** {@code consumedId} is the owned family member the route uses up, null for a full purchase. */
	private record Route(String consumedId, double cost, boolean priced, boolean npc, Set<PriceSource> sources) {
		boolean available() {
			return this.priced && this.cost > 0D;
		}
	}

	private static final class Tally {
		private double cost;
		private boolean priced = true;
		private boolean npc;
		private final Set<PriceSource> sources = new HashSet<>();

		Tally(double coins) {
			this.cost = coins;
			this.npc = coins > 0D;
		}

		void add(Route part, int qty) {
			if (part == null) {
				this.priced = false;
				return;
			}
			this.cost += part.cost() * qty;
			this.priced &= part.priced();
			this.npc |= part.npc();
			this.sources.addAll(part.sources());
		}

		Route route(String consumedId) {
			return new Route(consumedId, this.cost, this.priced, this.npc, Set.copyOf(this.sources));
		}
	}

	/** "Owned: Epic Speed Artifact", with the storage location when it is not in the bag. */
	private static String ownedLabel(OwnedAccessory copy, Map<String, CatalogItem> catalog) {
		String tier = tierLabel(copy.tier());
		String label = "Owned: " + (tier.isEmpty() ? "" : tier + " ") + nameOf(copy.id(), catalog);
		return copy.inBag() ? label : label + " (" + copy.location() + ")";
	}

	/** {@code VERY_SPECIAL} to {@code Very Special}. */
	static String tierLabel(String tier) {
		if (tier == null || tier.isBlank()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (String word : tier.trim().toLowerCase(Locale.ROOT).split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	private static void planPandora(
		List<Entry> entries, Profile profile, List<OwnedAccessory> owned,
		Map<String, CatalogItem> catalog, PriceLookup prices
	) {
		if (!profile.inventoryKnown()) {
			entries.add(unknownEntry("PANDORAS_BOX", "Pandora's Box progression", "Inventory data unavailable"));
			return;
		}
		OwnedAccessory inBag = bestCopy(owned, true);
		OwnedAccessory best = bestCopy(owned, false);
		String group = "family:" + MagicalPowerCalculator.accessoryFamily("PANDORAS_BOX");
		if (best == null) {
			CatalogItem item = catalog.get("PANDORAS_BOX");
			if (item == null) {
				return;
			}
			int baseMp = MagicalPowerCalculator.magicalPower(item.id(), item.tier());
			PriceQuote itemQuote = quote(prices, item.id());
			Entry purchase = new Entry(
				UpgradeType.PURCHASE, item.id(), item.name(), item.tier(), "Not owned", itemQuote.price(), baseMp,
				null, null, 0D, item.soulbound(), pricingSource(itemQuote.source(), false), priceStatus(itemQuote),
				group, null, CostBasis.MARKET
			);
			entries.add(purchase);
			entries.add(progressionEntry("Buy " + item.name() + " first", PANDORA_MAX_MP - baseMp, purchase.name()));
			return;
		}
		if (best.tier().isBlank()) {
			entries.add(unknownEntry("PANDORAS_BOX", "Pandora's Box progression", "Current rarity unavailable"));
			return;
		}
		int level = inBag == null ? 0 : mp(inBag);
		Entry move = null;
		if (!best.inBag() && mp(best) > level) {
			move = moveEntry(
				best, nameOf(best.id(), catalog), mp(best) - level, group, catalog.get(best.id()), null, catalog
			);
			entries.add(move);
			level = mp(best);
		}
		int gain = PANDORA_MAX_MP - level;
		if (gain <= 0) {
			return;
		}
		Integer challenges = pandoraChallenges(best.lore());
		String progress = challenges == null ? "challenge count unknown" : challenges + "/20 challenges";
		String state = move == null
			? ownedLabel(best, catalog) + ", " + progress
			: "Move " + nameOf(best.id(), catalog) + " to bag first, " + progress;
		entries.add(progressionEntry(state, gain, move == null ? null : move.name()));
	}

	/**
	 * Imbuing consumes the prism for 11 permanent MP, replacing its 8 MP as a bag accessory. The imbue step is
	 * priced at the prism's value plus the fee whether the prism is bought or already owned, so buying and
	 * imbuing is one step rather than a separate purchase.
	 */
	private static void planRiftPrism(
		List<Entry> entries, Profile profile, List<OwnedAccessory> owned,
		Map<String, CatalogItem> catalog, PriceLookup prices
	) {
		if (profile.riftPrismConsumed() == null) {
			entries.add(unknownEntry("RIFT_PRISM", "Rift Prism imbuement", "Imbuement state unavailable"));
			return;
		}
		if (profile.riftPrismConsumed()) {
			return;
		}
		if (!profile.inventoryKnown()) {
			entries.add(unknownEntry("RIFT_PRISM", "Rift Prism imbuement", "Inventory data unavailable"));
			return;
		}
		String group = "family:" + MagicalPowerCalculator.accessoryFamily("RIFT_PRISM");
		OwnedAccessory inBag = bestCopy(owned, true);
		OwnedAccessory best = bestCopy(owned, false);
		if (best == null && !catalog.containsKey("RIFT_PRISM")) {
			return;
		}
		int level = inBag == null ? 0 : mp(inBag);
		Entry move = null;
		if (best != null && !best.inBag() && mp(best) > level) {
			move = moveEntry(
				best, nameOf(best.id(), catalog), mp(best) - level, group, catalog.get(best.id()), null, catalog
			);
			entries.add(move);
			level = mp(best);
		}
		int gain = RIFT_PRISM_IMBUED_MP - level;
		if (gain <= 0) {
			return;
		}
		PriceQuote itemQuote = quote(prices, "RIFT_PRISM");
		double totalCost = itemQuote.available() ? itemQuote.price() + RIFT_PRISM_IMBUE_FEE : itemQuote.price();
		String state = best == null ? "Not owned"
			: move != null ? "Move " + nameOf(best.id(), catalog) + " to bag first"
			: ownedLabel(best, catalog);
		entries.add(new Entry(
			UpgradeType.RIFT_PRISM_IMBUEMENT, "RIFT_PRISM", best != null ? "Imbue Rift Prism" : "Buy and imbue Rift Prism",
			"RARE", state, totalCost, gain,
			null, null, 0D, false, pricingSource(itemQuote.source(), true), priceStatus(itemQuote), group,
			move == null ? null : move.name(), CostBasis.MARKET
		));
	}

	private static Entry moveEntry(
		OwnedAccessory copy, String name, int gain, String group, CatalogItem item, String prerequisite,
		Map<String, CatalogItem> catalog
	) {
		String tier = copy.tier().isBlank() && item != null ? item.tier() : copy.tier();
		return new Entry(
			UpgradeType.MOVE_TO_BAG, copy.id(), "Move " + name + " to bag", tier, ownedLabel(copy, catalog),
			0D, gain, null, null, 0D, false, PricingSource.OWNED, PriceStatus.AVAILABLE, group, prerequisite,
			CostBasis.MARKET
		);
	}

	private static Entry recombEntry(
		String name, String tier, int gain, String state, PriceLookup prices, String group, String prerequisite
	) {
		PriceQuote recombQuote = quote(prices, RECOMBOBULATOR);
		return new Entry(
			UpgradeType.RECOMBOBULATE, RECOMBOBULATOR, name, tier, state, recombQuote.price(), gain,
			null, null, 0D, false, pricingSource(recombQuote.source(), false), priceStatus(recombQuote), group,
			prerequisite, CostBasis.MARKET
		);
	}

	private static Entry progressionEntry(String state, int gain, String prerequisite) {
		return new Entry(
			UpgradeType.PANDORA_PROGRESSION, "PANDORAS_BOX", "Complete Pandora's Box challenges", "MYTHIC",
			state, 0D, gain, null, null, 0D, false, PricingSource.GAMEPLAY, PriceStatus.NOT_PRICED,
			"pandora:progression", prerequisite, CostBasis.MARKET
		);
	}

	private static Entry unknownEntry(String id, String name, String state) {
		return new Entry(
			UpgradeType.UNKNOWN_STATE, id, name, "SPECIAL", state, 0D, 0,
			null, null, 0D, false, PricingSource.NONE, PriceStatus.UNKNOWN_STATE, "unknown:" + id, null,
			CostBasis.MARKET
		);
	}

	/** Highest-MP copy, optionally restricted to the bag; ties prefer a copy already in the bag. */
	private static OwnedAccessory bestCopy(List<OwnedAccessory> owned, boolean bagOnly) {
		OwnedAccessory best = null;
		for (OwnedAccessory copy : owned) {
			if (bagOnly && !copy.inBag()) {
				continue;
			}
			if (best == null || mp(copy) > mp(best) || (mp(copy) == mp(best) && copy.inBag() && !best.inBag())) {
				best = copy;
			}
		}
		return best;
	}

	private static int mp(OwnedAccessory accessory) {
		return MagicalPowerCalculator.magicalPower(accessory.id(), accessory.tier());
	}

	/**
	 * Highest final MP wins; ties prefer the path needing the least spending (owned copies before purchases,
	 * no recombobulator before one, bag before storage), then the higher family tier.
	 */
	private static Option better(Option current, Option candidate) {
		if (current == null) {
			return candidate;
		}
		if (candidate.finalMp() != current.finalMp()) {
			return candidate.finalMp() > current.finalMp() ? candidate : current;
		}
		if (candidate.priority() != current.priority()) {
			return candidate.priority() < current.priority() ? candidate : current;
		}
		return candidate.rank() > current.rank() ? candidate : current;
	}

	/** A way to end a family: an owned copy (moved and/or recombobulated) or a catalog item to buy. */
	private record Option(
		String id, CatalogItem item, OwnedAccessory copy, int baseMp,
		String finalTier, int finalMp, boolean recombobulate, int priority, int rank
	) {
		static Option owned(OwnedAccessory copy, CatalogItem item, int rank) {
			int baseMp = mp(copy);
			boolean eligible = item != null && item.canRecombobulate() && !copy.recombobulated() && !copy.tier().isBlank();
			return withRecomb(copy.id(), item, copy, copy.tier(), baseMp, eligible, copy.inBag() ? 0 : 1, rank);
		}

		static Option purchase(CatalogItem item, int rank) {
			int baseMp = MagicalPowerCalculator.magicalPower(item.id(), item.tier());
			return withRecomb(item.id(), item, null, item.tier(), baseMp, item.canRecombobulate(), 4, rank);
		}

		private static Option withRecomb(
			String id, CatalogItem item, OwnedAccessory copy, String tier, int baseMp,
			boolean eligible, int priority, int rank
		) {
			if (eligible) {
				String recombTier = MagicalPowerCalculator.recombobulatedTier(tier);
				int recombMp = MagicalPowerCalculator.magicalPower(id, recombTier);
				if (recombMp > baseMp) {
					return new Option(id, item, copy, baseMp, recombTier, recombMp, true, priority + 2, rank);
				}
			}
			return new Option(id, item, copy, baseMp, tier, baseMp, false, priority, rank);
		}
	}

	/**
	 * Groups accessories into upgrade chains. BetterPV families (JSON or abicases) take precedence over NEU,
	 * whose powder chain is listed in reverse; other items are joined through NEU {@code talisman_upgrades}
	 * so one chain is planned once instead of once per tier.
	 */
	private static final class Chains {
		private final Map<String, String> parent = new HashMap<>();
		private final Map<String, Set<String>> lowerOf = new HashMap<>();
		private final Map<String, Integer> neuRanks = new HashMap<>();

		static Chains build(Map<String, List<String>> upgrades) {
			Chains chains = new Chains();
			for (Map.Entry<String, List<String>> entry : upgrades.entrySet()) {
				String lower = normalizeId(entry.getKey());
				if (lower.isBlank() || entry.getValue() == null || inAccessoryFamily(lower)) {
					continue;
				}
				for (String raw : entry.getValue()) {
					String higher = normalizeId(raw);
					if (higher.isBlank() || higher.equals(lower) || inAccessoryFamily(higher)) {
						continue;
					}
					chains.union(lower, higher);
					chains.lowerOf.computeIfAbsent(higher, id -> new HashSet<>()).add(lower);
				}
			}
			return chains;
		}

		String key(String id) {
			String normalized = normalizeId(id);
			return inAccessoryFamily(normalized) ? MagicalPowerCalculator.accessoryFamily(normalized) : find(normalized);
		}

		int rank(String id) {
			String normalized = normalizeId(id);
			return inAccessoryFamily(normalized)
				? MagicalPowerCalculator.accessoryFamilyRank(normalized)
				: neuRank(normalized, new HashSet<>());
		}

		private int neuRank(String id, Set<String> visiting) {
			Integer cached = this.neuRanks.get(id);
			if (cached != null) {
				return cached;
			}
			if (!visiting.add(id)) {
				return 0;
			}
			int rank = 0;
			for (String lower : this.lowerOf.getOrDefault(id, Set.of())) {
				rank = Math.max(rank, neuRank(lower, visiting) + 1);
			}
			visiting.remove(id);
			this.neuRanks.put(id, rank);
			return rank;
		}

		private String find(String id) {
			String root = id;
			while (true) {
				String next = this.parent.get(root);
				if (next == null || next.equals(root)) {
					break;
				}
				root = next;
			}
			String cursor = id;
			while (!cursor.equals(root)) {
				String next = this.parent.get(cursor);
				this.parent.put(cursor, root);
				cursor = next;
			}
			return root;
		}

		private void union(String a, String b) {
			String rootA = find(a);
			String rootB = find(b);
			if (rootA.equals(rootB)) {
				return;
			}
			if (rootA.compareTo(rootB) <= 0) {
				this.parent.put(rootB, rootA);
			} else {
				this.parent.put(rootA, rootB);
			}
		}
	}

	private static Map<String, CatalogItem> accessoryCatalog(Collection<JsonObject> definitions, Set<String> ignored) {
		Map<String, CatalogItem> out = new LinkedHashMap<>();
		for (JsonObject item : definitions) {
			if (item == null || !"ACCESSORY".equals(str(item.get("category")))) {
				continue;
			}
			String id = normalizeId(str(item.get("id")));
			if (id.isBlank() || ignored.contains(id)) {
				continue;
			}
			if ("RIFT".equals(str(item.get("origin"))) && !bool(item.get("rift_transferrable"))) {
				continue;
			}
			String name = str(item.get("name"));
			boolean canRecomb = !item.has("can_recombobulate") || bool(item.get("can_recombobulate"));
			out.put(id, new CatalogItem(
				id, name.isBlank() ? id : name, tierOf(str(item.get("tier"))),
				!str(item.get("soulbound")).isBlank(), canRecomb
			));
		}
		return out;
	}

	/**
	 * Sorts standalone steps by {@code order} and places each follow-up directly after the step it depends on,
	 * so a recombobulation never ranks above the purchase or move it requires.
	 */
	public static List<Entry> ordered(Collection<Entry> entries, Comparator<Entry> order) {
		Set<String> names = new HashSet<>();
		for (Entry entry : entries) {
			names.add(entry.name());
		}
		Map<String, List<Entry>> followUps = new HashMap<>();
		List<Entry> roots = new ArrayList<>();
		for (Entry entry : entries) {
			String prerequisite = entry.prerequisite();
			if (prerequisite != null && !prerequisite.equals(entry.name()) && names.contains(prerequisite)) {
				followUps.computeIfAbsent(prerequisite, key -> new ArrayList<>()).add(entry);
			} else {
				roots.add(entry);
			}
		}
		roots.sort(order);
		List<Entry> out = new ArrayList<>(entries.size());
		for (Entry root : roots) {
			appendWithFollowUps(root, followUps, order, out);
		}
		for (List<Entry> orphans : followUps.values()) {
			out.addAll(orphans);
		}
		return out;
	}

	private static void appendWithFollowUps(
		Entry entry, Map<String, List<Entry>> followUps, Comparator<Entry> order, List<Entry> out
	) {
		out.add(entry);
		List<Entry> next = followUps.remove(entry.name());
		if (next == null) {
			return;
		}
		next.sort(order);
		for (Entry followUp : next) {
			appendWithFollowUps(followUp, followUps, order, out);
		}
	}

	/** Cheapest coins per Magical Power first, then progression, unpriced and unknown entries. */
	public static Comparator<Entry> byCoinsPerMp() {
		return Comparator
			.comparingInt((Entry entry) -> priceRank(entry.priceStatus()))
			.thenComparingDouble(entry -> entry.priced() ? entry.coinsPerMp() : Double.MAX_VALUE)
			.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
	}

	/** Cheapest net cost first (price after selling the owned lower tier), same status ordering as above. */
	public static Comparator<Entry> byNetCost() {
		return Comparator
			.comparingInt((Entry entry) -> priceRank(entry.priceStatus()))
			.thenComparingDouble(entry -> entry.priced() ? entry.netCost() : Double.MAX_VALUE)
			.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
	}

	/**
	 * True when upgrade order should follow BetterPV family ranks (JSON families or abicases),
	 * not NEU {@code talisman_upgrades}.
	 */
	private static boolean inAccessoryFamily(String id) {
		if (MagicalPowerCalculator.hasAccessoryFamily(id)) {
			return true;
		}
		// Abicases share the synthetic {@code ABICASE} family without a JSON entry.
		String family = MagicalPowerCalculator.accessoryFamily(id);
		return !family.equals(id);
	}

	private static Integer pandoraChallenges(List<String> lore) {
		for (String line : lore) {
			if (line == null) {
				continue;
			}
			Matcher matcher = PANDORA_CHALLENGES.matcher(line.replaceAll("§.", ""));
			if (matcher.find()) {
				return Math.max(0, Math.min(20, Integer.parseInt(matcher.group(1))));
			}
		}
		return null;
	}

	private static String nameOf(String id, Map<String, CatalogItem> catalog) {
		CatalogItem item = catalog.get(id);
		return item == null ? id : item.name();
	}

	private static int priceRank(PriceStatus status) {
		return switch (status) {
			case AVAILABLE -> 0;
			case NOT_PRICED -> 1;
			case MISSING, STALE -> 2;
			case UNKNOWN_STATE -> 3;
		};
	}

	private static PriceQuote quote(PriceLookup lookup, String id) {
		PriceQuote quote = lookup.quote(id);
		if (quote == null || quote.price() < 0D || !Double.isFinite(quote.price())) {
			return missingQuote();
		}
		return quote;
	}

	private static PriceQuote missingQuote() {
		return new PriceQuote(0D, PriceSource.NONE, PriceAvailability.MISSING);
	}

	private static PriceStatus priceStatus(PriceQuote quote) {
		return switch (quote.availability()) {
			case AVAILABLE -> quote.price() > 0D ? PriceStatus.AVAILABLE : PriceStatus.MISSING;
			case STALE -> PriceStatus.STALE;
			case MISSING -> PriceStatus.MISSING;
		};
	}

	private static PriceStatus combinedStatus(PriceStatus first, PriceStatus second) {
		if (first == PriceStatus.MISSING || second == PriceStatus.MISSING) {
			return PriceStatus.MISSING;
		}
		if (first == PriceStatus.STALE || second == PriceStatus.STALE) {
			return PriceStatus.STALE;
		}
		return PriceStatus.AVAILABLE;
	}

	private static PricingSource pricingSource(PriceSource source, boolean includesNpc) {
		return switch (source) {
			case VYRIV -> includesNpc ? PricingSource.VYRIV_AND_NPC : PricingSource.VYRIV;
			case SKYHELPER -> includesNpc ? PricingSource.SKYHELPER_AND_NPC : PricingSource.SKYHELPER;
			case NONE -> PricingSource.NONE;
		};
	}

	private static double positive(Double value) {
		return value == null || value <= 0D || !Double.isFinite(value) ? 0D : value;
	}

	private static String normalizeId(String id) {
		return id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
	}

	/** Hypixel omits {@code tier} for common items. */
	private static String tierOf(String tier) {
		return tier == null || tier.isBlank() ? "COMMON" : tier.trim().toUpperCase(Locale.ROOT);
	}

	private static String str(JsonElement element) {
		return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
	}

	private static boolean bool(JsonElement element) {
		return element != null && element.isJsonPrimitive() && "true".equalsIgnoreCase(element.getAsString());
	}

	private static boolean looksLikeAccessory(List<String> lore) {
		if (lore == null) {
			return false;
		}
		for (String line : lore) {
			String plain = line == null ? "" : line.replaceAll("§.", "").toUpperCase(Locale.ROOT);
			if (plain.contains("ACCESSORY") || plain.contains("HATCESSORY")) {
				return true;
			}
		}
		return false;
	}

	private static void logPlan(String chain, OwnedAccessory active, Option best, String decision) {
		if (!BetterPV.LOGGER.isDebugEnabled() || best == null || !diagnosticId(best.id())) {
			return;
		}
		BetterPV.LOGGER.debug(
			"[Missing Accessories] chain={} active={} target={}@{} final={}/{}MP {}",
			chain, active == null ? "none" : active.id() + "/" + active.tier() + "@" + active.location(),
			best.id(), best.copy() == null ? "buy" : best.copy().location(), best.finalTier(), best.finalMp(), decision
		);
	}

	private static boolean diagnosticId(String id) {
		return id != null && (id.contains("PERSONAL_COMPACTOR") || id.startsWith("POWDER_OF_") || "RIFT_PRISM".equals(id));
	}

	private record CatalogItem(String id, String name, String tier, boolean soulbound, boolean canRecombobulate) {
	}
}
