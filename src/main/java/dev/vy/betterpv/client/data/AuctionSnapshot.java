package dev.vy.betterpv.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.api.CoflnetApiClient;
import dev.vy.betterpv.client.api.HypixelApiClient;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Player auction listings for Active / Sold / Bought + lifetime AH stats. */
public final class AuctionSnapshot {
	private static final String[] RARITY_ORDER = {
		"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL", "ULTIMATE"
	};

	public enum Bucket {
		ACTIVE,
		SOLD,
		BOUGHT
	}

	public record Listing(
		String auctionId,
		String tag,
		String itemName,
		String tier,
		boolean bin,
		long startingBid,
		long highestBid,
		long endMs,
		InventorySnapshot.Slot slot
	) {
		public long price() {
			return this.highestBid > 0L ? this.highestBid : this.startingBid;
		}

		public boolean ended(long nowMs) {
			return this.endMs > 0L && this.endMs <= nowMs;
		}

		public Listing withTier(String newTier) {
			String t = newTier == null ? "" : newTier;
			if (t.equals(this.tier == null ? "" : this.tier)) {
				return this;
			}
			return new Listing(
				this.auctionId,
				this.tag,
				this.itemName,
				t,
				this.bin,
				this.startingBid,
				this.highestBid,
				this.endMs,
				this.slot
			);
		}
	}

	/** Lifetime Auction House stats from {@code player_stats.auctions}. */
	public record Stats(
		long bids,
		long highestBid,
		long won,
		long created,
		long goldSpent,
		long goldEarned,
		long fees,
		Map<String, Long> totalSold,
		Map<String, Long> totalBought
	) {
		public Stats {
			// LinkedHashMap — Map.copyOf does not preserve encounter order.
			totalSold = Collections.unmodifiableMap(new LinkedHashMap<>(totalSold == null ? Map.of() : totalSold));
			totalBought = Collections.unmodifiableMap(new LinkedHashMap<>(totalBought == null ? Map.of() : totalBought));
		}

		public static Stats empty() {
			return new Stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, Map.of(), Map.of());
		}

		public static Stats fromMember(JsonObject member) {
			if (member == null) {
				return empty();
			}
			JsonObject playerStats = member.has("player_stats") && member.get("player_stats").isJsonObject()
				? member.getAsJsonObject("player_stats")
				: null;
			if (playerStats == null || !playerStats.has("auctions") || !playerStats.get("auctions").isJsonObject()) {
				return empty();
			}
			JsonObject a = playerStats.getAsJsonObject("auctions");
			return new Stats(
				longVal(a, "bids"),
				longVal(a, "highest_bid"),
				longVal(a, "won"),
				longVal(a, "created"),
				longVal(a, "gold_spent"),
				longVal(a, "gold_earned"),
				longVal(a, "fees"),
				rarityMap(a, "total_sold"),
				rarityMap(a, "total_bought")
			);
		}

		private static Map<String, Long> rarityMap(JsonObject root, String key) {
			Map<String, Long> out = new LinkedHashMap<>();
			// Highest rarity first (matches in-game AH feel / descending).
			for (int i = RARITY_ORDER.length - 1; i >= 0; i--) {
				out.put(RARITY_ORDER[i], 0L);
			}
			if (root == null || !root.has(key) || !root.get(key).isJsonObject()) {
				return out;
			}
			JsonObject obj = root.getAsJsonObject(key);
			for (String rarity : RARITY_ORDER) {
				long n = longVal(obj, rarity);
				if (n <= 0L) {
					n = longVal(obj, rarity.toLowerCase(Locale.ROOT));
				}
				if (n > 0L) {
					out.put(rarity, n);
				}
			}
			for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
				String k = entry.getKey() == null ? "" : entry.getKey().toUpperCase(Locale.ROOT);
				if (k.isBlank() || out.containsKey(k)) {
					continue;
				}
				long n = longVal(obj, entry.getKey());
				if (n > 0L) {
					out.put(k, n);
				}
			}
			return out;
		}
	}

	private final UUID playerUuid;
	private final List<Listing> active;
	private final List<Listing> sold;
	private final List<Listing> bought;
	private final Stats stats;
	private final int soldPage;
	private final int boughtPage;
	private final boolean soldHasMore;
	private final boolean boughtHasMore;
	private final String creditUrl;

	public AuctionSnapshot(
		UUID playerUuid,
		List<Listing> active,
		List<Listing> sold,
		List<Listing> bought,
		Stats stats,
		int soldPage,
		int boughtPage,
		boolean soldHasMore,
		boolean boughtHasMore
	) {
		this.playerUuid = playerUuid;
		this.active = List.copyOf(active == null ? List.of() : active);
		this.sold = List.copyOf(sold == null ? List.of() : sold);
		this.bought = List.copyOf(bought == null ? List.of() : bought);
		this.stats = stats == null ? Stats.empty() : stats;
		this.soldPage = Math.max(0, soldPage);
		this.boughtPage = Math.max(0, boughtPage);
		this.soldHasMore = soldHasMore;
		this.boughtHasMore = boughtHasMore;
		String id = playerUuid == null ? "" : HypixelApiClient.undashed(playerUuid);
		this.creditUrl = id.isBlank() ? "https://sky.coflnet.com/" : "https://sky.coflnet.com/player/" + id;
	}

	public static AuctionSnapshot empty() {
		return new AuctionSnapshot(null, List.of(), List.of(), List.of(), Stats.empty(), 0, 0, false, false);
	}

	public UUID playerUuid() {
		return this.playerUuid;
	}

	public List<Listing> active() {
		return this.active;
	}

	public List<Listing> sold() {
		return this.sold;
	}

	public List<Listing> bought() {
		return this.bought;
	}

	public Stats stats() {
		return this.stats;
	}

	public List<Listing> forBucket(Bucket bucket) {
		return switch (bucket == null ? Bucket.ACTIVE : bucket) {
			case ACTIVE -> this.active;
			case SOLD -> this.sold;
			case BOUGHT -> this.bought;
		};
	}

	public int soldPage() {
		return this.soldPage;
	}

	public int boughtPage() {
		return this.boughtPage;
	}

	public boolean soldHasMore() {
		return this.soldHasMore;
	}

	public boolean boughtHasMore() {
		return this.boughtHasMore;
	}

	public String creditUrl() {
		return this.creditUrl;
	}

	public long totalCoins(Bucket bucket) {
		long sum = 0L;
		for (Listing listing : forBucket(bucket)) {
			sum += listing.price();
		}
		return sum;
	}

	public AuctionSnapshot withStats(Stats stats) {
		return new AuctionSnapshot(
			this.playerUuid,
			this.active,
			this.sold,
			this.bought,
			stats,
			this.soldPage,
			this.boughtPage,
			this.soldHasMore,
			this.boughtHasMore
		);
	}

	public AuctionSnapshot withMoreSold(List<Listing> extra, int page, boolean hasMore) {
		List<Listing> merged = mergeById(this.sold, extra);
		return new AuctionSnapshot(
			this.playerUuid,
			this.active,
			merged,
			this.bought,
			this.stats,
			page,
			this.boughtPage,
			hasMore,
			this.boughtHasMore
		);
	}

	public AuctionSnapshot withMoreBought(List<Listing> extra, int page, boolean hasMore) {
		List<Listing> merged = mergeById(this.bought, extra);
		return new AuctionSnapshot(
			this.playerUuid,
			this.active,
			this.sold,
			merged,
			this.stats,
			this.soldPage,
			page,
			this.soldHasMore,
			hasMore
		);
	}

	/** Patch rarity on listings keyed by auction id (Cofl summaries omit {@code tier}). */
	public AuctionSnapshot withTiers(Map<String, String> tiersByAuctionId) {
		if (tiersByAuctionId == null || tiersByAuctionId.isEmpty()) {
			return this;
		}
		Map<String, String> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : tiersByAuctionId.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()) {
				continue;
			}
			String tier = SkyBlockItemFactory.normalizeTier(entry.getValue());
			if (tier.isBlank()) {
				continue;
			}
			normalized.put(entry.getKey().replace("-", "").toLowerCase(Locale.ROOT), tier);
		}
		if (normalized.isEmpty()) {
			return this;
		}
		List<Listing> nextActive = applyTiers(this.active, normalized);
		List<Listing> nextSold = applyTiers(this.sold, normalized);
		List<Listing> nextBought = applyTiers(this.bought, normalized);
		if (nextActive == this.active && nextSold == this.sold && nextBought == this.bought) {
			return this;
		}
		return new AuctionSnapshot(
			this.playerUuid,
			nextActive,
			nextSold,
			nextBought,
			this.stats,
			this.soldPage,
			this.boughtPage,
			this.soldHasMore,
			this.boughtHasMore
		);
	}

	private static List<Listing> applyTiers(List<Listing> listings, Map<String, String> tiersByAuctionId) {
		if (listings == null || listings.isEmpty()) {
			return listings == null ? List.of() : listings;
		}
		boolean changed = false;
		List<Listing> out = new ArrayList<>(listings.size());
		for (Listing listing : listings) {
			String id = listing.auctionId() == null ? "" : listing.auctionId().replace("-", "").toLowerCase(Locale.ROOT);
			String tier = id.isEmpty() ? null : tiersByAuctionId.get(id);
			if (tier != null && !tier.isBlank() && (listing.tier() == null || listing.tier().isBlank())) {
				out.add(listing.withTier(tier));
				changed = true;
			} else {
				out.add(listing);
			}
		}
		return changed ? out : listings;
	}

	public static AuctionSnapshot build(
		UUID playerUuid,
		JsonObject hypixelAuctionRoot,
		JsonArray coflSoldPage0,
		JsonArray coflBidsPage0
	) {
		String player = playerUuid == null ? "" : HypixelApiClient.undashed(playerUuid).toLowerCase(Locale.ROOT);
		long now = System.currentTimeMillis();
		List<Listing> active = parseHypixelActive(hypixelAuctionRoot, player, now);
		List<Listing> sold = parseCoflSold(coflSoldPage0);
		List<Listing> bought = parseCoflBought(coflBidsPage0);
		boolean soldMore = coflSoldPage0 != null && coflSoldPage0.size() >= 10;
		boolean boughtMore = coflBidsPage0 != null && coflBidsPage0.size() >= 10;
		return new AuctionSnapshot(
			playerUuid, active, sold, bought, Stats.empty(), 0, 0, soldMore, boughtMore
		);
	}

	public static List<Listing> parseCoflSold(JsonArray array) {
		List<Listing> out = new ArrayList<>();
		for (JsonObject obj : CoflnetApiClient.objects(array)) {
			Listing listing = fromCoflSummary(obj, false);
			if (listing != null) {
				out.add(listing);
			}
		}
		return out;
	}

	public static List<Listing> parseCoflBought(JsonArray array) {
		List<Listing> out = new ArrayList<>();
		for (JsonObject obj : CoflnetApiClient.objects(array)) {
			long own = longVal(obj, "highestOwnBid");
			long high = longVal(obj, "highestBid");
			if (own <= 0L || high <= 0L || own < high) {
				continue; // not a win
			}
			Listing listing = fromCoflSummary(obj, true);
			if (listing != null) {
				out.add(listing);
			}
		}
		return out;
	}

	private static List<Listing> parseHypixelActive(JsonObject root, String player, long nowMs) {
		List<Listing> out = new ArrayList<>();
		if (root == null || !root.has("auctions") || !root.get("auctions").isJsonArray()) {
			return out;
		}
		// Hypixel ?player= returns auctions you created OR bid on — Active is only
		// your own listings that are still running (not expired / claimed / bid-only).
		for (JsonElement el : root.getAsJsonArray("auctions")) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject a = el.getAsJsonObject();
			String auctioneer = str(a, "auctioneer").replace("-", "").toLowerCase(Locale.ROOT);
			boolean isSeller = !player.isBlank() && player.equals(auctioneer);
			if (!isSeller) {
				continue;
			}
			long end = longVal(a, "end");
			if (end <= nowMs) {
				continue;
			}
			boolean claimed = a.has("claimed") && a.get("claimed").getAsBoolean();
			if (claimed) {
				continue;
			}

			Listing listing = fromHypixel(a);
			if (listing != null) {
				out.add(listing);
			}
		}
		out.sort((a, b) -> Long.compare(a.endMs(), b.endMs()));
		return out;
	}

	private static Listing fromHypixel(JsonObject a) {
		String id = str(a, "uuid");
		if (id.isBlank()) {
			id = str(a, "auction_id");
		}
		String name = str(a, "item_name");
		String tier = str(a, "tier");
		boolean bin = a.has("bin") && a.get("bin").getAsBoolean();
		long startBid = longVal(a, "starting_bid");
		long high = longVal(a, "highest_bid_amount");
		long end = longVal(a, "end");
		InventorySnapshot.Slot slot = null;
		if (a.has("item_bytes")) {
			slot = InventoryDecoder.slotFromItemBytes(a.get("item_bytes"));
		}
		String tag = slot == null ? "" : slot.id();
		if (slot == null && !name.isBlank()) {
			slot = InventoryDecoder.slotFromTag("PAPER", name);
		}
		return new Listing(id, tag, name.isBlank() ? "Unknown" : name, SkyBlockItemFactory.normalizeTier(tier), bin, startBid, high, end, slot);
	}

	private static Listing fromCoflSummary(JsonObject obj, boolean bought) {
		String id = str(obj, "auctionId");
		if (id.isBlank()) {
			id = str(obj, "uuid");
		}
		String tag = str(obj, "tag");
		String name = str(obj, "itemName");
		boolean bin = obj.has("bin") && obj.get("bin").getAsBoolean();
		long startBid = longVal(obj, "startingBid");
		long high = bought ? longVal(obj, "highestOwnBid") : longVal(obj, "highestBid");
		if (high <= 0L) {
			high = longVal(obj, "highestBid");
		}
		long end = parseCoflTime(str(obj, "end"));
		InventorySnapshot.Slot slot = InventoryDecoder.slotFromTag(tag, name);
		String tier = SkyBlockItemFactory.resolveTier(tag);
		return new Listing(
			id,
			tag,
			name.isBlank() ? (tag.isBlank() ? "Unknown" : tag) : name,
			SkyBlockItemFactory.normalizeTier(tier),
			bin,
			startBid,
			high,
			end,
			slot
		);
	}

	private static List<Listing> mergeById(List<Listing> existing, List<Listing> extra) {
		Map<String, Listing> map = new LinkedHashMap<>();
		for (Listing listing : existing) {
			map.put(key(listing), listing);
		}
		if (extra != null) {
			for (Listing listing : extra) {
				map.putIfAbsent(key(listing), listing);
			}
		}
		return new ArrayList<>(map.values());
	}

	private static String key(Listing listing) {
		if (listing.auctionId() != null && !listing.auctionId().isBlank()) {
			return listing.auctionId().toLowerCase(Locale.ROOT);
		}
		return listing.itemName() + "|" + listing.endMs() + "|" + listing.price();
	}

	private static long parseCoflTime(String raw) {
		if (raw == null || raw.isBlank()) {
			return 0L;
		}
		try {
			String normalized = raw.endsWith("Z") ? raw : raw + "Z";
			return Instant.parse(normalized).toEpochMilli();
		} catch (DateTimeParseException ignored) {
			try {
				return Long.parseLong(raw);
			} catch (NumberFormatException ignored2) {
				return 0L;
			}
		}
	}

	private static String str(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return "";
		}
		try {
			return obj.get(key).getAsString();
		} catch (Exception ignored) {
			return "";
		}
	}

	private static long longVal(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return 0L;
		}
		try {
			return obj.get(key).getAsLong();
		} catch (Exception ignored) {
			try {
				return (long) obj.get(key).getAsDouble();
			} catch (Exception ignored2) {
				return 0L;
			}
		}
	}
}
