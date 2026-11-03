package dev.vy.betterpv.client.gui.rankings;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.client.data.EliteBoards;
import dev.vy.betterpv.client.data.EliteRankings;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.gui.nav.PvTab;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;

public final class RankingsPageTest {
	private static final UUID PLAYER = UUID.fromString("23842b15-c379-4848-9261-863692a3d757");
	private static final String PROFILE_A = "23842b15c37948489261863692a3d757";
	private static final String PROFILE_B = "14eb0350216e45e49de8fec6ec7cb43d";

	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static EliteRankings result(String key, int rank) {
		String body = "{\"ranks\":{\"melon\":{\"title\":\"Melon Collection\",\"slug\":\"melon\",\"rank\":" + rank
			+ ",\"amount\":1,\"type\":\"Long\"}}}";
		return EliteRankings.fromResponse(key, Optional.of(JsonParser.parseString(body).getAsJsonObject()), null);
	}

	// Mirrors ProfileScreenDataLoader#ensureRankings + ProfileViewerScreen#applyLoadedProfile.
	private static void land(RankingsPage page, EliteRankings incoming) {
		page.apply(EliteRankings.resolve(page.rankings(), incoming));
	}

	@Test
	public void switchingProfilesNeverShowsTheOldProfilesRankings() {
		RankingsPage page = new RankingsPage();
		String keyA = EliteRankings.key(PLAYER, PROFILE_A);
		String keyB = EliteRankings.key(PLAYER, PROFILE_B);

		page.apply(EliteRankings.loading(keyA));
		page.reset();
		page.apply(EliteRankings.loading(keyB));

		land(page, result(keyA, 1));
		assertEquals(EliteRankings.State.LOADING, page.rankings().state());
		assertEquals(keyB, page.rankings().key());

		land(page, result(keyB, 42));
		assertEquals(EliteRankings.State.READY, page.rankings().state());
		assertEquals(keyB, page.rankings().key());
		assertEquals(42, page.rankings().bestRank());
	}

	@Test
	public void lateResponseAfterLeavingTheProfileIsIgnored() {
		RankingsPage page = new RankingsPage();
		String keyA = EliteRankings.key(PLAYER, PROFILE_A);
		page.apply(EliteRankings.loading(keyA));
		page.reset();
		land(page, result(keyA, 1));
		assertEquals(EliteRankings.State.IDLE, page.rankings().state());
		assertTrue(page.rankings().entries().isEmpty());
	}

	@Test
	public void resetClearsReadyRankings() {
		RankingsPage page = new RankingsPage();
		String keyA = EliteRankings.key(PLAYER, PROFILE_A);
		page.apply(result(keyA, 3));
		assertEquals(EliteRankings.State.READY, page.rankings().state());
		page.reset();
		assertEquals(EliteRankings.State.IDLE, page.rankings().state());
		assertFalse(page.rankings().isFor(keyA));
	}

	@Test
	public void nothingIsClickableBeforeFirstRender() {
		RankingsPage page = new RankingsPage();
		assertFalse(page.clickRetry(0, 0));
		assertFalse(page.clickCredit(0, 0));
		assertFalse(page.mouseScrolled(0, 0, 1));
	}

	private static EliteRankings.Entry entry(String id, String title, int rank) {
		return new EliteRankings.Entry(id, title, title, rank, 29_000, false, EliteRankings.Interval.CURRENT, "", false);
	}

	private static List<String> tooltipText(EliteRankings.Entry entry) {
		return RankingsPage.entryTooltip(entry).stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).collect(Collectors.joining()))
			.collect(Collectors.toList());
	}

	@Test
	public void tooltipShowsOnlyNameRankAndStatistic() {
		EliteRankings.Entry profileBoard = new EliteRankings.Entry(
			"garden", "Garden XP", "Garden XP", 114, 1320890, true, EliteRankings.Interval.CURRENT, "", true
		);
		List<String> text = tooltipText(profileBoard);
		assertEquals(List.of("Garden XP", "", "Rank#114", "XP1,320,890.00"), text);
		assertTrue(RankingsPage.entryTooltip(profileBoard).get(2).spans().stream()
			.anyMatch(span -> span.color() == RankingsPage.COLOR_RANK_TOP));
	}

	@Test
	public void tooltipNamesTheStatisticAndNeverSaysScore() {
		assertTrue(tooltipText(entry("agaricus-cap", "Agaricus Cap Collection", 1)).contains("Collection29,000"));
		assertTrue(tooltipText(entry("farming", "Farming Experience", 5)).contains("XP29,000"));
		assertTrue(tooltipText(entry("farmingweight", "Farming Weight", 5)).contains("Weight29,000"));
		assertTrue(tooltipText(entry("mite", "Mite Kills", 5)).contains("Kills29,000"));
		assertTrue(tooltipText(entry("mystery", "Mystery", 5)).contains("Value29,000"));
		for (String id : EliteBoards.all().keySet()) {
			for (String line : tooltipText(entry(id, id, 1))) {
				assertFalse(id, line.startsWith("Score"));
			}
		}
	}

	@Test
	public void rankColorsUseChatColours() {
		assertEquals(0xFF55FFFF, RankingsPage.rankColor(1));
		assertEquals(0xFFFFAA00, RankingsPage.rankColor(2));
		assertEquals(0xFFFF55FF, RankingsPage.rankColor(3));
		assertEquals(0xFFFFFFFF, RankingsPage.rankColor(4));
		assertEquals(0xFFFFFFFF, RankingsPage.rankColor(1000));
		assertEquals(0xFFAAAAAA, RankingsPage.rankColor(1001));
		assertEquals(0xFFAAAAAA, RankingsPage.rankColor(49_154));
	}

	@Test
	public void namesKeepTheirCategoryColourAtAnyRank() {
		assertEquals(EliteBoards.Category.COLLECTION.color(), RankingsPage.nameColor(entry("agaricus-cap", "Agaricus Cap", 1)));
		assertEquals(EliteBoards.Category.SKILL.color(), RankingsPage.nameColor(entry("farming", "Farming XP", 1000)));
		assertEquals(EliteBoards.Category.COLLECTION.color(), RankingsPage.nameColor(entry("agaricus-cap", "Agaricus Cap", 49_154)));
	}

	@Test
	public void fewPlacementsGetTheSpaciousSingleColumn() {
		assertEquals(RankingsPage.Layout.SPACIOUS, RankingsPage.layoutFor(3, 100));
		assertEquals(RankingsPage.Layout.SPACIOUS, RankingsPage.layoutFor(RankingsPage.SPACIOUS_MAX_ENTRIES, 100));
		assertEquals(1, RankingsPage.columnsFor(RankingsPage.Layout.SPACIOUS, 1000));
		assertEquals(RankingsPage.Layout.SPACIOUS, RankingsPage.layoutFor(20, 20 * RankingsPage.Layout.SPACIOUS.rowH));
	}

	@Test
	public void manyPlacementsUseTheCompactTwoColumnGridWhenWideEnough() {
		assertEquals(RankingsPage.Layout.COMPACT, RankingsPage.layoutFor(167, 240));
		assertEquals(2, RankingsPage.columnsFor(RankingsPage.Layout.COMPACT, RankingsPage.MIN_COL_W * 2 + RankingsPage.COL_GAP));
		assertEquals(1, RankingsPage.columnsFor(RankingsPage.Layout.COMPACT, RankingsPage.MIN_COL_W * 2));
	}

	@Test
	public void headerShowsTheReturnedRankRange() {
		RankingsPage page = new RankingsPage();
		assertEquals("Leaderboards", page.title());
		String key = EliteRankings.key(PLAYER, PROFILE_A);
		String body = "{\"ranks\":{"
			+ "\"melon\":{\"title\":\"Melon Collection\",\"slug\":\"melon\",\"rank\":3,\"amount\":1,\"type\":\"Long\"},"
			+ "\"mite\":{\"title\":\"Mite Kills\",\"slug\":\"mite\",\"rank\":49126,\"amount\":1,\"type\":\"Long\"}}}";
		page.apply(EliteRankings.fromResponse(key, Optional.of(JsonParser.parseString(body).getAsJsonObject()), null));
		assertEquals("Leaderboards · #3 to #49,126", page.title());
		page.apply(result(key, 7));
		assertEquals("Leaderboards · #7", page.title());
	}

	@Test
	public void everyVanillaIconResolvesInTheItemRegistry() {
		for (var board : EliteBoards.all().entrySet()) {
			String item = board.getValue().vanillaItem();
			if (!item.equals(EliteBoards.FALLBACK_ITEM)) {
				assertFalse(board.getKey() + " -> " + item, RankingsPage.vanillaItem(item) == Items.MAP);
			}
		}
		assertEquals(Items.MAP, RankingsPage.vanillaItem(EliteBoards.FALLBACK_ITEM));
		assertEquals(Items.MAP, RankingsPage.vanillaItem("definitely_not_an_item"));
	}

	@Test
	public void rankingsIsAHomeSubTabNotATopLevelTab() {
		assertEquals(PvSubTab.HOME_RANKINGS, PvTab.HOME.subTabs()[PvTab.HOME.subTabs().length - 1]);
		for (PvTab tab : PvTab.values()) {
			assertFalse(tab.name(), tab.name().contains("RANKING"));
			if (tab != PvTab.HOME) {
				assertFalse(tab.name(), Arrays.asList(tab.subTabs()).contains(PvSubTab.HOME_RANKINGS));
			}
		}
		assertArrayEquals(
			new String[] {
				"HOME", "DUNGEONS", "INVENTORIES", "PETS", "AUCTIONS", "COLLECTIONS", "GARDEN", "MINING",
				"FORAGING", "FISHING", "CRIMSON", "RIFT", "MUSEUM", "BESTIARY", "EVENTS"
			},
			Arrays.stream(PvTab.values()).map(Enum::name).toArray(String[]::new)
		);
	}

	@Test
	public void langLabelsTheSubTabOnly() throws Exception {
		try (InputStream in = RankingsPageTest.class.getResourceAsStream("/assets/betterpv/lang/en_us.json")) {
			assertNotNull(in);
			JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			assertEquals("Rankings", lang.get("betterpv.sub.home_rankings").getAsString());
			assertFalse(lang.has("betterpv.tab.rankings"));
		}
	}

	@Test
	public void rankingsCommandOpensTheHomeSubTab() {
		PvTab.Route expected = new PvTab.Route(PvTab.HOME, PvSubTab.HOME_RANKINGS);
		assertEquals(expected, PvTab.routeFromCommandArg("rankings"));
		assertEquals(expected, PvTab.routeFromCommandArg(" Leaderboards "));
		assertTrue(expected.valid());
		assertEquals(PvTab.HOME, PvTab.fromCommandArg("ranking"));
		assertTrue(Arrays.asList(PvTab.commandAliases()).contains("rankings"));
	}
}
