package dev.vy.betterpv.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.List;

/** Member {@code rift.*} + motes + Vampire slayer for the Rift tab. */
public final class RiftSnapshot {
	public static final int ENIGMA_MAX = 52;
	public static final int TIMECHARM_MAX = 8;
	public static final int BURGER_MAX = 5;
	public static final int MONTEZUMA_CATS_MAX = 9;
	public static final int EYES_MAX = 7;

	/**
	 * Canonical gallery timecharms. API {@code type} matches {@link #apiType()};
	 * {@code visits} on secured trophies is the gallery visit requirement (“Visits to get”).
	 */
	public enum TimecharmDef {
		WYLDLY_SUPREME("wyldly_supreme", "RIFT_TROPHY_WYLDLY_SUPREME", "Supreme Timecharm", 0xFF55FF55),
		CHICKEN_N_EGG("chicken_n_egg", "RIFT_TROPHY_CHICKEN_N_EGG", "Chicken N Egg Timecharm", 0xFFD2A679),
		MIRRORED("mirrored", "RIFT_TROPHY_MIRRORED", "mrahcemiT esrevrorriM", 0xFF55FFFF),
		CITIZEN("citizen", "RIFT_TROPHY_CITIZEN", "SkyBlock Citizen Timecharm", 0xFFAA55FF),
		LAZY_LIVING("lazy_living", "RIFT_TROPHY_LAZY_LIVING", "Living Timecharm", 0xFF5555FF),
		SLIME("slime", "RIFT_TROPHY_SLIME", "Globulate Timecharm", 0xFF55FF55),
		VAMPIRIC("vampiric", "RIFT_TROPHY_VAMPIRIC", "Vampiric Timecharm", 0xFFFF5555),
		MOUNTAIN("mountain", "RIFT_TROPHY_MOUNTAIN", "Celestial Timecharm", 0xFF5555FF);

		private final String apiType;
		private final String itemId;
		private final String displayName;
		private final int color;

		TimecharmDef(String apiType, String itemId, String displayName, int color) {
			this.apiType = apiType;
			this.itemId = itemId;
			this.displayName = displayName;
			this.color = color;
		}

		public String apiType() {
			return this.apiType;
		}

		public String itemId() {
			return this.itemId;
		}

		public String displayName() {
			return this.displayName;
		}

		public int color() {
			return this.color;
		}

		public static TimecharmDef fromApiType(String raw) {
			if (raw == null || raw.isBlank()) {
				return null;
			}
			String key = raw.trim().toLowerCase(java.util.Locale.ROOT)
				.replace("rift_trophy_", "")
				.replace('-', '_');
			if ("celestial".equals(key)) {
				return MOUNTAIN;
			}
			for (TimecharmDef def : values()) {
				if (def.apiType.equals(key) || def.itemId.equalsIgnoreCase(raw.trim())) {
					return def;
				}
			}
			return null;
		}
	}

	public record Timecharm(
		String id,
		String itemId,
		String name,
		int color,
		boolean secured,
		long securedAtMs,
		int visitsToGet
	) {
		public Timecharm {
			id = id == null ? "" : id;
			itemId = itemId == null || itemId.isBlank() ? "RIFT_TROPHY_" + id.toUpperCase(java.util.Locale.ROOT) : itemId;
			name = name == null || name.isBlank() ? InventoryDecoder.prettyWords(id) : name;
			color = color == 0 ? 0xFFFFAA00 : color;
		}
	}

	public record VampireProgress(
		int level,
		float fill,
		boolean maxed,
		String hover,
		float xp
	) {
		public static VampireProgress empty() {
			return new VampireProgress(0, 0f, false, "", 0f);
		}
	}

	private final long motesPurse;
	private final long lifetimeMotes;
	private final long visits;
	private final long lastFreeAccessMs;

	private final int enigmaFound;
	private final boolean enigmaCloakBought;
	private final int enigmaBonusIndex;
	private final List<String> enigmaSouls;

	private final int timecharmsSecured;
	private final int eliseStep;
	private final List<Timecharm> timecharms;

	private final int burgers;
	private final int catsFound;
	private final boolean montezumaUnlocked;
	private final String montezumaTier;
	private final List<String> foundCats;

	private final int eyesKilled;
	private final List<String> killedEyes;

	private final List<String> purchasedBoundaries;
	private final VampireProgress vampire;

	private final InventorySnapshot.Page inventory;
	private final List<InventorySnapshot.Page> enderPages;

	private RiftSnapshot(
		long motesPurse,
		long lifetimeMotes,
		long visits,
		long lastFreeAccessMs,
		int enigmaFound,
		boolean enigmaCloakBought,
		int enigmaBonusIndex,
		List<String> enigmaSouls,
		int timecharmsSecured,
		int eliseStep,
		List<Timecharm> timecharms,
		int burgers,
		int catsFound,
		boolean montezumaUnlocked,
		String montezumaTier,
		List<String> foundCats,
		int eyesKilled,
		List<String> killedEyes,
		List<String> purchasedBoundaries,
		VampireProgress vampire,
		InventorySnapshot.Page inventory,
		List<InventorySnapshot.Page> enderPages
	) {
		this.motesPurse = Math.max(0L, motesPurse);
		this.lifetimeMotes = Math.max(0L, lifetimeMotes);
		this.visits = Math.max(0L, visits);
		this.lastFreeAccessMs = Math.max(0L, lastFreeAccessMs);
		this.enigmaFound = Math.max(0, enigmaFound);
		this.enigmaCloakBought = enigmaCloakBought;
		this.enigmaBonusIndex = Math.max(0, enigmaBonusIndex);
		this.enigmaSouls = List.copyOf(enigmaSouls == null ? List.of() : enigmaSouls);
		this.timecharmsSecured = Math.max(0, timecharmsSecured);
		this.eliseStep = Math.max(0, eliseStep);
		this.timecharms = List.copyOf(timecharms == null ? List.of() : timecharms);
		this.burgers = Math.max(0, Math.min(BURGER_MAX, burgers));
		this.catsFound = Math.max(0, catsFound);
		this.montezumaUnlocked = montezumaUnlocked;
		this.montezumaTier = montezumaTier == null ? "" : montezumaTier;
		this.foundCats = List.copyOf(foundCats == null ? List.of() : foundCats);
		this.eyesKilled = Math.max(0, eyesKilled);
		this.killedEyes = List.copyOf(killedEyes == null ? List.of() : killedEyes);
		this.purchasedBoundaries = List.copyOf(purchasedBoundaries == null ? List.of() : purchasedBoundaries);
		this.vampire = vampire == null ? VampireProgress.empty() : vampire;
		this.inventory = inventory == null ? InventorySnapshot.emptyPage("Inventory", 9) : inventory;
		this.enderPages = enderPages == null || enderPages.isEmpty()
			? List.of(InventorySnapshot.emptyPage("Ender Chest", 9))
			: List.copyOf(enderPages);
	}

	public static RiftSnapshot empty() {
		return new RiftSnapshot(
			0, 0, 0, 0,
			0, false, 0, List.of(),
			0, 0, parseTimecharms(null),
			0, 0, false, "", List.of(),
			0, List.of(),
			List.of(),
			VampireProgress.empty(),
			InventorySnapshot.emptyPage("Inventory", 9),
			List.of(InventorySnapshot.emptyPage("Ender Chest", 9))
		);
	}

	public static RiftSnapshot fromMember(JsonObject member) {
		if (member == null) {
			return empty();
		}

		JsonObject currencies = Leveling.obj(member.get("currencies"));
		long motes = longVal(currencies, "motes_purse");

		JsonObject stats = Leveling.obj(member.get("player_stats"));
		JsonObject riftStats = Leveling.obj(stats == null ? null : stats.get("rift"));
		long lifetime = longVal(riftStats, "lifetime_motes_earned");
		long visits = longVal(riftStats, "visits");

		JsonObject rift = Leveling.obj(member.get("rift"));
		JsonObject access = Leveling.obj(rift == null ? null : rift.get("access"));
		long lastFree = longVal(access, "last_free");

		JsonObject enigma = Leveling.obj(rift == null ? null : rift.get("enigma"));
		List<String> souls = stringList(enigma == null ? null : enigma.get("found_souls"));
		boolean cloak = bool(enigma, "bought_cloak");
		int bonus = intVal(enigma, "claimed_bonus_index");

		JsonObject gallery = Leveling.obj(rift == null ? null : rift.get("gallery"));
		int elise = intVal(gallery, "elise_step");
		List<Timecharm> charms = parseTimecharms(gallery == null ? null : gallery.get("secured_trophies"));
		int securedCount = 0;
		for (Timecharm charm : charms) {
			if (charm.secured()) {
				securedCount++;
			}
		}

		JsonObject castle = Leveling.obj(rift == null ? null : rift.get("castle"));
		int burgers = intVal(castle, "grubber_stacks");

		JsonObject deadCats = Leveling.obj(rift == null ? null : rift.get("dead_cats"));
		List<String> cats = stringList(deadCats == null ? null : deadCats.get("found_cats"));
		boolean petUnlocked = bool(deadCats, "unlocked_pet");
		JsonObject montezuma = Leveling.obj(deadCats == null ? null : deadCats.get("montezuma"));
		String tier = montezuma == null ? "" : str(montezuma.get("tier"));

		JsonObject witherCage = Leveling.obj(rift == null ? null : rift.get("wither_cage"));
		List<String> eyes = stringList(witherCage == null ? null : witherCage.get("killed_eyes"));

		List<String> boundaries = stringList(rift == null ? null : rift.get("lifetime_purchased_boundaries"));
		boundaries = new ArrayList<>(boundaries);
		boundaries.sort(String.CASE_INSENSITIVE_ORDER);

		float vampireXp = Leveling.readSlayerXp(member, "vampire");
		JsonArray table = RepoData.slayerXp("vampire");
		int cap = table == null || table.isEmpty() ? 5 : table.size();
		Leveling.Progress vp = Leveling.getLevel(table, vampireXp, cap, true);
		VampireProgress vampire = new VampireProgress(
			(int) Math.floor(vp.level()),
			vp.fill(),
			vp.maxed(),
			vp.slayerHover("Vampire"),
			vampireXp
		);

		InventoryDecoder.RiftInventories inv = InventoryDecoder.parseRiftUi(member);

		return new RiftSnapshot(
			motes,
			lifetime,
			visits,
			lastFree,
			souls.size(),
			cloak,
			bonus,
			souls,
			securedCount,
			elise,
			charms,
			burgers,
			cats.size(),
			petUnlocked,
			tier,
			cats,
			eyes.size(),
			eyes,
			boundaries,
			vampire,
			inv.inventory(),
			inv.enderPages()
		);
	}

	public long motesPurse() {
		return this.motesPurse;
	}

	public long lifetimeMotes() {
		return this.lifetimeMotes;
	}

	public long visits() {
		return this.visits;
	}

	public long lastFreeAccessMs() {
		return this.lastFreeAccessMs;
	}

	public int enigmaFound() {
		return this.enigmaFound;
	}

	public boolean enigmaCloakBought() {
		return this.enigmaCloakBought;
	}

	public int enigmaBonusIndex() {
		return this.enigmaBonusIndex;
	}

	public List<String> enigmaSouls() {
		return this.enigmaSouls;
	}

	public float enigmaFill() {
		return Math.min(1f, this.enigmaFound / (float) ENIGMA_MAX);
	}

	public int timecharmsSecured() {
		return this.timecharmsSecured;
	}

	public int eliseStep() {
		return this.eliseStep;
	}

	public List<Timecharm> timecharms() {
		return this.timecharms;
	}

	public float timecharmFill() {
		return Math.min(1f, this.timecharmsSecured / (float) TIMECHARM_MAX);
	}

	public int burgers() {
		return this.burgers;
	}

	public float burgerFill() {
		return Math.min(1f, this.burgers / (float) BURGER_MAX);
	}

	public int catsFound() {
		return this.catsFound;
	}

	public boolean montezumaUnlocked() {
		return this.montezumaUnlocked;
	}

	public String montezumaTier() {
		return this.montezumaTier;
	}

	public List<String> foundCats() {
		return this.foundCats;
	}

	public float catsFill() {
		return Math.min(1f, this.catsFound / (float) MONTEZUMA_CATS_MAX);
	}

	public int eyesKilled() {
		return this.eyesKilled;
	}

	public List<String> killedEyes() {
		return this.killedEyes;
	}

	public float eyesFill() {
		return Math.min(1f, this.eyesKilled / (float) EYES_MAX);
	}

	public List<String> purchasedBoundaries() {
		return this.purchasedBoundaries;
	}

	public VampireProgress vampire() {
		return this.vampire;
	}

	public InventorySnapshot.Page inventory() {
		return this.inventory;
	}

	public List<InventorySnapshot.Page> enderPages() {
		return this.enderPages;
	}

	private static List<Timecharm> parseTimecharms(JsonElement element) {
		java.util.Map<String, JsonObject> secured = new java.util.HashMap<>();
		if (element != null && element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				JsonObject obj = Leveling.obj(child);
				if (obj == null) {
					continue;
				}
				String type = str(obj.get("type"));
				if (type.isBlank()) {
					continue;
				}
				TimecharmDef def = TimecharmDef.fromApiType(type);
				String key = def != null ? def.apiType() : type.trim().toLowerCase(java.util.Locale.ROOT);
				secured.put(key, obj);
			}
		}

		List<Timecharm> out = new ArrayList<>(TIMECHARM_MAX);
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (TimecharmDef def : TimecharmDef.values()) {
			JsonObject obj = secured.get(def.apiType());
			seen.add(def.apiType());
			if (obj != null) {
				out.add(new Timecharm(
					def.apiType(),
					def.itemId(),
					def.displayName(),
					def.color(),
					true,
					longVal(obj, "timestamp"),
					intVal(obj, "visits")
				));
			} else {
				out.add(new Timecharm(
					def.apiType(),
					def.itemId(),
					def.displayName(),
					def.color(),
					false,
					0L,
					0
				));
			}
		}
		// Unknown future trophies still show up after the known set.
		for (var entry : secured.entrySet()) {
			if (seen.contains(entry.getKey())) {
				continue;
			}
			JsonObject obj = entry.getValue();
			String type = str(obj.get("type"));
			out.add(new Timecharm(
				type,
				"RIFT_TROPHY_" + type.toUpperCase(java.util.Locale.ROOT),
				InventoryDecoder.prettyWords(type),
				0xFFFFAA00,
				true,
				longVal(obj, "timestamp"),
				intVal(obj, "visits")
			));
		}
		return out;
	}

	private static List<String> stringList(JsonElement element) {
		if (element == null || !element.isJsonArray()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement child : element.getAsJsonArray()) {
			if (child == null || child.isJsonNull() || !child.isJsonPrimitive()) {
				continue;
			}
			String value = child.getAsString();
			if (value != null && !value.isBlank()) {
				out.add(value);
			}
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	private static boolean bool(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) {
			return false;
		}
		try {
			return obj.get(key).getAsBoolean();
		} catch (Exception ignored) {
			return false;
		}
	}

	private static int intVal(JsonObject obj, String key) {
		Float n = Leveling.num(obj == null ? null : obj.get(key));
		return n == null ? 0 : Math.max(0, Math.round(n));
	}

	private static long longVal(JsonObject obj, String key) {
		Float n = Leveling.num(obj == null ? null : obj.get(key));
		return n == null ? 0L : Math.max(0L, Math.round(n.doubleValue()));
	}

	private static String str(JsonElement element) {
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return "";
		}
		try {
			return element.getAsString();
		} catch (Exception ignored) {
			return "";
		}
	}
}
