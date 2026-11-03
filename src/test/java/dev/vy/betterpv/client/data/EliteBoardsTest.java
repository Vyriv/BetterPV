package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public final class EliteBoardsTest {
	// Every all-time board id from GET https://api.eliteskyblock.com/leaderboards (November 2026).
	private static final List<String> ELITE_BOARDS = Arrays.asList((
		"farmingweight garden participations firstplace bronzemedals silvermedals goldmedals platinummedals "
		+ "diamondmedals skyblockxp chocolate visitors-accepted networth-normal networth-functional networth-liquid "
		+ "networth-functional-liquid cactus carrot cocoa melon mushroom netherwart potato pumpkin sugarcane wheat "
		+ "sunflower moonflower wildrose cactus-milestone carrot-milestone cocoa-milestone melon-milestone "
		+ "mushroom-milestone netherwart-milestone potato-milestone pumpkin-milestone sugarcane-milestone "
		+ "wheat-milestone sunflower-milestone moonflower-milestone wildrose-milestone pests mite cricket moth "
		+ "earthworm slug beetle locust rat mosquito fly mouse lunar-moth dragonfly firefly mantis alchemy carpentry "
		+ "combat enchanting farming fishing foraging mining runecrafting social taming coop-social hunting coal "
		+ "cobblestone diamond emerald end-stone gemstone glacite glowstone gold gravel hard-stone ice iron lapis "
		+ "mithril mycelium nether-quartz netherrack obsidian red-sand redstone sand sulphur tungsten umber seeds "
		+ "raw-chicken raw-rabbit mutton leather feather raw-porkchop blaze-rod bone chili-pepper ender-pearl "
		+ "ghast-tear gunpowder magma-cream rotten-flesh slimeball spider-eye string acacia birch dark-oak jungle oak "
		+ "spruce mangrove fig sea-lumies vinesap lushlilac tender-wood helix-log ruby-veilshroom honeycomb clay "
		+ "clownfish ink-sac lily-pad magmafish prismarine-crystals prismarine-shard pufferfish raw-fish raw-salmon "
		+ "sponge lotus agaricus-cap caducous-stem half-eaten-carrot hemovibe living-metal-heart wilted-berberis "
		+ "timite catacombs archer-xp berserk-xp healer-xp mage-xp tank-xp dungeon-class-average dungeon-secrets "
		+ "zombie-slayer spider-slayer wolf-slayer enderman-slayer blaze-slayer vampire-slayer slayer-xp bestiary "
		+ "fairy-souls magical-power"
	).split(" "));

	// Collection ids from GET https://api.hypixel.net/v2/resources/skyblock/collections.
	private static final Set<String> HYPIXEL_COLLECTIONS = Set.of((
		"INK_SACK:3 MOONFLOWER CARROT_ITEM DOUBLE_PLANT WILD_ROSE CACTUS RAW_CHICKEN SUGAR_CANE PUMPKIN WHEAT SEEDS "
		+ "MUSHROOM_COLLECTION RABBIT NETHER_STALK MUTTON MELON POTATO_ITEM LEATHER PORK FEATHER INK_SACK:4 REDSTONE "
		+ "UMBER COAL MYCEL ENDER_STONE QUARTZ SAND IRON_INGOT GEMSTONE_COLLECTION TUNGSTEN OBSIDIAN DIAMOND "
		+ "COBBLESTONE GLOWSTONE_DUST GOLD_INGOT GRAVEL HARD_STONE MITHRIL_ORE EMERALD SAND:1 ICE GLACITE SULPHUR_ORE "
		+ "NETHERRACK ENDER_PEARL CHILI_PEPPER SLIME_BALL MAGMA_CREAM GHAST_TEAR SULPHUR ROTTEN_FLESH SPIDER_EYE BONE "
		+ "BLAZE_ROD STRING SEA_LUMIES LOG_2 RUBY_VEILSHROOM LOG:1 VINESAP LOG:3 LOG:2 LUSHLILAC MANGROVE_LOG HELIX_LOG "
		+ "LOG HONEYCOMB FIG_LOG TENDER_WOOD LOG_2:1 WATER_LILY PRISMARINE_SHARD INK_SACK RAW_FISH RAW_FISH:3 LOTUS "
		+ "RAW_FISH:2 RAW_FISH:1 MAGMA_FISH PRISMARINE_CRYSTALS CLAY_BALL SPONGE WILTED_BERBERIS METAL_HEART "
		+ "CADUCOUS_STEM AGARICUS_CAP HEMOVIBE HALF_EATEN_CARROT TIMITE"
	).split(" "));

	@Test
	public void everyEliteBoardIsMapped() {
		for (String id : ELITE_BOARDS) {
			assertTrue(id, EliteBoards.isKnown(id));
		}
		assertEquals(ELITE_BOARDS.size(), EliteBoards.all().size());
	}

	@Test
	public void collectionBoardsUseRealHypixelCollectionIds() {
		long collectionIcons = EliteBoards.all().values().stream().filter(EliteBoards.Board::hasCollectionIcon).count();
		assertEquals(103, collectionIcons);
		EliteBoards.all().forEach((id, board) -> {
			if (board.hasCollectionIcon()) {
				assertTrue(id + " -> " + board.collectionId(), HYPIXEL_COLLECTIONS.contains(board.collectionId()));
			}
		});
		assertEquals("AGARICUS_CAP", EliteBoards.of("agaricus-cap", "").collectionId());
		assertEquals("SULPHUR", EliteBoards.of("gunpowder", "").collectionId());
		assertEquals("SULPHUR_ORE", EliteBoards.of("sulphur", "").collectionId());
		assertEquals("NETHER_STALK", EliteBoards.of("netherwart-milestone", "").collectionId());
	}

	@Test
	public void pestBoardsUseTheirOwnNeuPestHeads() {
		// NEU repo items/PEST_*_MONSTER.json, each a textured player head.
		List<String> pests = List.of(
			"mite=PEST_MITE_MONSTER", "cricket=PEST_CRICKET_MONSTER", "moth=PEST_MOTH_MONSTER",
			"earthworm=PEST_EARTHWORM_MONSTER", "slug=PEST_SLUG_MONSTER", "beetle=PEST_BEETLE_MONSTER",
			"locust=PEST_LOCUST_MONSTER", "rat=PEST_RAT_MONSTER", "mosquito=PEST_MOSQUITO_MONSTER",
			"fly=PEST_FLY_MONSTER", "mouse=PEST_FIELD_MOUSE_MONSTER", "lunar-moth=PEST_LUNAR_MOTH_MONSTER",
			"dragonfly=PEST_DRAGONFLY_MONSTER", "firefly=PEST_FIREFLY_MONSTER", "mantis=PEST_PRAYING_MANTIS_MONSTER"
		);
		for (String pair : pests) {
			String[] parts = pair.split("=");
			EliteBoards.Board board = EliteBoards.of(parts[0], "");
			assertEquals(parts[0], EliteBoards.Category.PESTS, board.category());
			assertEquals(parts[0], parts[1], board.skyblockItem());
			assertEquals(EliteBoards.of(parts[0], ""), EliteBoards.of(parts[0] + "-monthly", ""));
		}
		EliteBoards.Board total = EliteBoards.of("pests", "Pest Kills");
		assertFalse(total.hasSkyBlockIcon());
		assertEquals(EliteBoards.FALLBACK_ITEM, total.vanillaItem());
		for (EliteBoards.Board board : EliteBoards.all().values()) {
			assertFalse(board.vanillaItem().equals("silverfish_spawn_egg"));
		}
	}

	@Test
	public void categoriesDriveColours() {
		assertEquals(EliteBoards.Category.COLLECTION, EliteBoards.of("agaricus-cap", "").category());
		assertEquals(0xFF55FF55, EliteBoards.Category.COLLECTION.color());
		assertEquals(EliteBoards.Category.SKILL, EliteBoards.of("farming", "").category());
		assertEquals(0xFF55FFFF, EliteBoards.Category.SKILL.color());
		assertEquals(EliteBoards.Category.JACOB, EliteBoards.of("goldmedals", "").category());
		assertEquals(0xFFFFAA00, EliteBoards.Category.JACOB.color());
		assertEquals(EliteBoards.Category.MILESTONE, EliteBoards.of("wheat-milestone", "").category());
		assertEquals(EliteBoards.Category.PESTS, EliteBoards.of("lunar-moth", "").category());
		assertEquals(EliteBoards.Category.SLAYER, EliteBoards.of("vampire-slayer", "").category());
		assertEquals(EliteBoards.Category.DUNGEONS, EliteBoards.of("catacombs", "").category());
	}

	@Test
	public void valueLabelsNameTheStatistic() {
		assertEquals("Collection", EliteBoards.of("agaricus-cap", "Agaricus Cap Collection").valueLabel());
		assertEquals("XP", EliteBoards.of("farming", "Farming Experience").valueLabel());
		assertEquals("XP", EliteBoards.of("zombie-slayer", "").valueLabel());
		assertEquals("Weight", EliteBoards.of("farmingweight", "Farming Weight").valueLabel());
		assertEquals("Kills", EliteBoards.of("mite", "Mite Kills").valueLabel());
		assertEquals("Medals", EliteBoards.of("diamondmedals", "").valueLabel());
		assertEquals("Networth", EliteBoards.of("networth-liquid", "").valueLabel());
		assertEquals("Milestone", EliteBoards.of("cactus-milestone", "").valueLabel());
		for (EliteBoards.Board board : EliteBoards.all().values()) {
			assertFalse(board.valueLabel().equalsIgnoreCase("Score"));
		}
	}

	@Test
	public void intervalBoardsShareTheirBaseBoard() {
		assertEquals(EliteBoards.of("farmingweight", ""), EliteBoards.of("farmingweight-monthly", ""));
		assertEquals(EliteBoards.of("melon", ""), EliteBoards.of("MELON-weekly", ""));
		assertEquals("farmingweight", EliteBoards.baseId(" FarmingWeight-Monthly "));
	}

	@Test
	public void unknownBoardsAreClassifiedFromTheirTitle() {
		assertFalse(EliteBoards.isKnown("new-crop"));
		EliteBoards.Board collection = EliteBoards.of("new-crop", "New Crop Collection");
		assertEquals(EliteBoards.Category.COLLECTION, collection.category());
		assertEquals("Collection", collection.valueLabel());
		assertEquals(EliteBoards.FALLBACK_ITEM, collection.vanillaItem());
		assertFalse(collection.hasCollectionIcon());
		assertEquals(EliteBoards.Category.MILESTONE, EliteBoards.of("x", "New Crop Milestone Collection").category());
		assertEquals("XP", EliteBoards.of("x", "Something Experience").valueLabel());
		assertEquals("Kills", EliteBoards.of("x", "Boss Kills").valueLabel());
		EliteBoards.Board unknown = EliteBoards.of("x", "Mystery");
		assertEquals(EliteBoards.Category.GENERAL, unknown.category());
		assertEquals("Value", unknown.valueLabel());
		assertEquals("Value", EliteBoards.of(null, null).valueLabel());
	}
}
