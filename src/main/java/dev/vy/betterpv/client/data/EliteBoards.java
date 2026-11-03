package dev.vy.betterpv.client.data;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Presentation metadata for Elite leaderboard ids (from {@code GET /leaderboards}): category colour,
 * icon source and the statistic the score measures. Unknown boards are classified from their title.
 */
public final class EliteBoards {
	/** Vanilla item drawn for boards with no reliable icon. */
	public static final String FALLBACK_ITEM = "map";

	/** Minecraft chat palette colours, chosen per category. */
	public enum Category {
		COLLECTION(0xFF55FF55),
		MILESTONE(0xFF00AA00),
		SKILL(0xFF55FFFF),
		DUNGEONS(0xFFFF5555),
		SLAYER(0xFFFF55FF),
		PESTS(0xFF00AAAA),
		JACOB(0xFFFFAA00),
		WEIGHT(0xFFFFFF55),
		NETWORTH(0xFFFFAA00),
		GENERAL(0xFFFFFFFF);

		private final int color;

		Category(int color) {
			this.color = color;
		}

		public int color() {
			return this.color;
		}
	}

	/**
	 * @param collectionId Hypixel collection id for the icon, or empty
	 * @param skyblockItem NEU item id for the icon (e.g. a pest head), or empty
	 * @param vanillaItem  vanilla item registry path used when neither of the above applies or resolves
	 * @param valueLabel   tooltip label for the score, e.g. {@code Collection} / {@code XP}
	 */
	public record Board(Category category, String collectionId, String skyblockItem, String vanillaItem, String valueLabel) {
		public boolean hasCollectionIcon() {
			return !this.collectionId.isEmpty();
		}

		public boolean hasSkyBlockIcon() {
			return !this.skyblockItem.isEmpty();
		}
	}

	private static final Map<String, Board> BOARDS = new HashMap<>();

	static {
		general("farmingweight", Category.WEIGHT, "wheat", "Weight");
		general("garden", Category.GENERAL, "grass_block", "XP");
		general("participations", Category.JACOB, "clock", "Contests");
		general("firstplace", Category.JACOB, "golden_helmet", "First Places");
		general("bronzemedals", Category.JACOB, "copper_ingot", "Medals");
		general("silvermedals", Category.JACOB, "iron_ingot", "Medals");
		general("goldmedals", Category.JACOB, "gold_ingot", "Medals");
		general("platinummedals", Category.JACOB, "quartz", "Medals");
		general("diamondmedals", Category.JACOB, "diamond", "Medals");
		general("skyblockxp", Category.GENERAL, "experience_bottle", "Level");
		general("chocolate", Category.GENERAL, "cocoa_beans", "Chocolate");
		general("visitors-accepted", Category.GENERAL, "villager_spawn_egg", "Visitors");
		general("networth-normal", Category.NETWORTH, "gold_block", "Networth");
		general("networth-functional", Category.NETWORTH, "gold_block", "Networth");
		general("networth-liquid", Category.NETWORTH, "gold_block", "Networth");
		general("networth-functional-liquid", Category.NETWORTH, "gold_block", "Networth");
		general("bestiary", Category.GENERAL, "book", "Milestone");
		general("fairy-souls", Category.GENERAL, FALLBACK_ITEM, "Fairy Souls");
		general("magical-power", Category.GENERAL, "nether_star", "Magical Power");

		crop("cactus", "CACTUS");
		crop("carrot", "CARROT_ITEM");
		crop("cocoa", "INK_SACK:3");
		crop("melon", "MELON");
		crop("mushroom", "MUSHROOM_COLLECTION");
		crop("netherwart", "NETHER_STALK");
		crop("potato", "POTATO_ITEM");
		crop("pumpkin", "PUMPKIN");
		crop("sugarcane", "SUGAR_CANE");
		crop("wheat", "WHEAT");
		crop("sunflower", "DOUBLE_PLANT");
		crop("moonflower", "MOONFLOWER");
		crop("wildrose", "WILD_ROSE");

		general("pests", Category.PESTS, FALLBACK_ITEM, "Kills");
		pest("mite", "MITE");
		pest("cricket", "CRICKET");
		pest("moth", "MOTH");
		pest("earthworm", "EARTHWORM");
		pest("slug", "SLUG");
		pest("beetle", "BEETLE");
		pest("locust", "LOCUST");
		pest("rat", "RAT");
		pest("mosquito", "MOSQUITO");
		pest("fly", "FLY");
		pest("mouse", "FIELD_MOUSE");
		pest("lunar-moth", "LUNAR_MOTH");
		pest("dragonfly", "DRAGONFLY");
		pest("firefly", "FIREFLY");
		pest("mantis", "PRAYING_MANTIS");

		skill("alchemy", "brewing_stand");
		skill("carpentry", "crafting_table");
		skill("combat", "stone_sword");
		skill("enchanting", "enchanting_table");
		skill("farming", "golden_hoe");
		skill("fishing", "fishing_rod");
		skill("foraging", "jungle_sapling");
		skill("mining", "stone_pickaxe");
		skill("runecrafting", "magma_cream");
		skill("social", "emerald");
		skill("taming", "bone");
		skill("coop-social", "emerald");
		skill("hunting", "lead");

		collection("coal", "COAL");
		collection("cobblestone", "COBBLESTONE");
		collection("diamond", "DIAMOND");
		collection("emerald", "EMERALD");
		collection("end-stone", "ENDER_STONE");
		collection("gemstone", "GEMSTONE_COLLECTION");
		collection("glacite", "GLACITE");
		collection("glowstone", "GLOWSTONE_DUST");
		collection("gold", "GOLD_INGOT");
		collection("gravel", "GRAVEL");
		collection("hard-stone", "HARD_STONE");
		collection("ice", "ICE");
		collection("iron", "IRON_INGOT");
		collection("lapis", "INK_SACK:4");
		collection("mithril", "MITHRIL_ORE");
		collection("mycelium", "MYCEL");
		collection("nether-quartz", "QUARTZ");
		collection("netherrack", "NETHERRACK");
		collection("obsidian", "OBSIDIAN");
		collection("red-sand", "SAND:1");
		collection("redstone", "REDSTONE");
		collection("sand", "SAND");
		collection("sulphur", "SULPHUR_ORE");
		collection("tungsten", "TUNGSTEN");
		collection("umber", "UMBER");

		collection("seeds", "SEEDS");
		collection("raw-chicken", "RAW_CHICKEN");
		collection("raw-rabbit", "RABBIT");
		collection("mutton", "MUTTON");
		collection("leather", "LEATHER");
		collection("feather", "FEATHER");
		collection("raw-porkchop", "PORK");

		collection("blaze-rod", "BLAZE_ROD");
		collection("bone", "BONE");
		collection("chili-pepper", "CHILI_PEPPER");
		collection("ender-pearl", "ENDER_PEARL");
		collection("ghast-tear", "GHAST_TEAR");
		collection("gunpowder", "SULPHUR");
		collection("magma-cream", "MAGMA_CREAM");
		collection("rotten-flesh", "ROTTEN_FLESH");
		collection("slimeball", "SLIME_BALL");
		collection("spider-eye", "SPIDER_EYE");
		collection("string", "STRING");

		collection("acacia", "LOG_2");
		collection("birch", "LOG:2");
		collection("dark-oak", "LOG_2:1");
		collection("jungle", "LOG:3");
		collection("oak", "LOG");
		collection("spruce", "LOG:1");
		collection("mangrove", "MANGROVE_LOG");
		collection("fig", "FIG_LOG");
		collection("sea-lumies", "SEA_LUMIES");
		collection("vinesap", "VINESAP");
		collection("lushlilac", "LUSHLILAC");
		collection("tender-wood", "TENDER_WOOD");
		collection("helix-log", "HELIX_LOG");
		collection("ruby-veilshroom", "RUBY_VEILSHROOM");
		collection("honeycomb", "HONEYCOMB");

		collection("clay", "CLAY_BALL");
		collection("clownfish", "RAW_FISH:2");
		collection("ink-sac", "INK_SACK");
		collection("lily-pad", "WATER_LILY");
		collection("magmafish", "MAGMA_FISH");
		collection("prismarine-crystals", "PRISMARINE_CRYSTALS");
		collection("prismarine-shard", "PRISMARINE_SHARD");
		collection("pufferfish", "RAW_FISH:3");
		collection("raw-fish", "RAW_FISH");
		collection("raw-salmon", "RAW_FISH:1");
		collection("sponge", "SPONGE");
		collection("lotus", "LOTUS");

		collection("agaricus-cap", "AGARICUS_CAP");
		collection("caducous-stem", "CADUCOUS_STEM");
		collection("half-eaten-carrot", "HALF_EATEN_CARROT");
		collection("hemovibe", "HEMOVIBE");
		collection("living-metal-heart", "METAL_HEART");
		collection("wilted-berberis", "WILTED_BERBERIS");
		collection("timite", "TIMITE");

		general("catacombs", Category.DUNGEONS, "wither_skeleton_skull", "XP");
		general("archer-xp", Category.DUNGEONS, "bow", "XP");
		general("berserk-xp", Category.DUNGEONS, "iron_sword", "XP");
		general("healer-xp", Category.DUNGEONS, "splash_potion", "XP");
		general("mage-xp", Category.DUNGEONS, "blaze_rod", "XP");
		general("tank-xp", Category.DUNGEONS, "leather_chestplate", "XP");
		general("dungeon-class-average", Category.DUNGEONS, "skeleton_skull", "Average Level");
		general("dungeon-secrets", Category.DUNGEONS, "chest", "Secrets");

		general("zombie-slayer", Category.SLAYER, "rotten_flesh", "XP");
		general("spider-slayer", Category.SLAYER, "cobweb", "XP");
		general("wolf-slayer", Category.SLAYER, "mutton", "XP");
		general("enderman-slayer", Category.SLAYER, "ender_pearl", "XP");
		general("blaze-slayer", Category.SLAYER, "blaze_powder", "XP");
		general("vampire-slayer", Category.SLAYER, "redstone", "XP");
		general("slayer-xp", Category.SLAYER, "diamond_sword", "XP");
	}

	private EliteBoards() {
	}

	/** Board metadata for an Elite leaderboard id (interval suffixes ignored), classifying unknown ids by title. */
	public static Board of(String id, String title) {
		Board known = BOARDS.get(baseId(id));
		return known != null ? known : infer(title);
	}

	/** Whether {@code id} is one of the boards mapped explicitly (not classified from its title). */
	public static boolean isKnown(String id) {
		return BOARDS.containsKey(baseId(id));
	}

	public static Map<String, Board> all() {
		return Map.copyOf(BOARDS);
	}

	static String baseId(String id) {
		String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
		for (String suffix : new String[] { "-monthly", "-weekly" }) {
			if (key.endsWith(suffix)) {
				return key.substring(0, key.length() - suffix.length());
			}
		}
		return key;
	}

	private static Board infer(String title) {
		String t = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
		if (t.endsWith("milestone collection")) {
			return fallback(Category.MILESTONE, "Milestone");
		}
		if (t.endsWith(" collection")) {
			return fallback(Category.COLLECTION, "Collection");
		}
		if (t.endsWith(" experience") || t.endsWith(" xp")) {
			return fallback(Category.GENERAL, "XP");
		}
		if (t.endsWith(" kills")) {
			return fallback(Category.GENERAL, "Kills");
		}
		return fallback(Category.GENERAL, "Value");
	}

	private static Board fallback(Category category, String label) {
		return new Board(category, "", "", FALLBACK_ITEM, label);
	}

	private static void general(String id, Category category, String item, String label) {
		BOARDS.put(id, new Board(category, "", "", item, label));
	}

	/** Pest head from the NEU repo ({@code PEST_<NAME>_MONSTER}). */
	private static void pest(String id, String neuName) {
		BOARDS.put(id, new Board(Category.PESTS, "", "PEST_" + neuName + "_MONSTER", FALLBACK_ITEM, "Kills"));
	}

	private static void skill(String id, String item) {
		general(id, Category.SKILL, item, "XP");
	}

	private static void collection(String id, String collectionId) {
		BOARDS.put(id, new Board(Category.COLLECTION, collectionId, "", FALLBACK_ITEM, "Collection"));
	}

	private static void crop(String id, String collectionId) {
		collection(id, collectionId);
		BOARDS.put(id + "-milestone", new Board(Category.MILESTONE, collectionId, "", FALLBACK_ITEM, "Milestone"));
	}
}
