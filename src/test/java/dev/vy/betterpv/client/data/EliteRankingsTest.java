package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Test;

public final class EliteRankingsTest {
	private static final UUID PLAYER = UUID.fromString("23842b15-c379-4848-9261-863692a3d757");
	private static final String PROFILE = "23842b15-c379-4848-9261-863692a3d757";
	private static final String KEY = EliteRankings.key(PLAYER, PROFILE);
	private static final String OTHER_KEY = EliteRankings.key(PLAYER, "14eb0350-216e-45e4-9de8-fec6ec7cb43d");

	// Same shape as GET /leaderboards/{player}/{profile} from api.eliteskyblock.com.
	private static JsonObject response(String ranks) {
		return JsonParser.parseString("{\"ranks\":{" + ranks + "}}").getAsJsonObject();
	}

	private static String entry(String id, String title, int rank, String extra) {
		return "\"" + id + "\":{\"title\":\"" + title + "\",\"short\":\"" + title + "\",\"slug\":\"" + id
			+ "\",\"rank\":" + rank + ",\"amount\":1234.5,\"type\":\"Long\"" + extra + "}";
	}

	private static List<String> ids(EliteRankings rankings) {
		return rankings.entries().stream().map(EliteRankings.Entry::id).collect(Collectors.toList());
	}

	private static EliteRankings ready(String ranks) {
		return EliteRankings.fromResponse(KEY, Optional.of(response(ranks)), null);
	}

	@Test
	public void keepsEveryPositiveRankInOneAscendingList() {
		EliteRankings rankings = ready(String.join(",",
			entry("a", "A", 1000, ""),
			entry("b", "B", 1001, ""),
			entry("c", "C", 0, ""),
			entry("d", "D", -1, ""),
			entry("e", "E", 1, ""),
			entry("f", "F", 50_000, "")
		));
		assertEquals(EliteRankings.State.READY, rankings.state());
		assertEquals(List.of("e", "a", "b", "f"), ids(rankings));
		assertEquals(1, rankings.bestRank());
		assertEquals(50_000, rankings.worstRank());
	}

	@Test
	public void worstRankOfASinglePlacementIsThatRank() {
		EliteRankings rankings = ready(entry("melon", "Melon Collection", 4_200, ""));
		assertEquals(4_200, rankings.worstRank());
		assertEquals(0, EliteRankings.ready(KEY, List.of()).worstRank());
	}

	@Test
	public void sortsBestRankFirst() {
		EliteRankings rankings = ready(String.join(",",
			entry("netherwart", "Nether Wart Collection", 992, ""),
			entry("cocoa", "Cocoa Bean Collection", 3, ""),
			entry("garden", "Garden XP", 12, ",\"profile\":true"),
			entry("sugarcane", "Sugar Cane Collection", 6, "")
		));
		assertEquals(List.of("cocoa", "sugarcane", "garden", "netherwart"), ids(rankings));
		assertEquals(3, rankings.bestRank());
	}

	@Test
	public void tiedRanksPutAllTimeBeforeMonthlyBeforeWeekly() {
		EliteRankings rankings = ready(String.join(",",
			entry("farmingweight-weekly", "Farming Weight", 1, ",\"intervalIdentifier\":\"2026-W45\""),
			entry("melon-monthly", "Melon Collection", 1, ",\"intervalIdentifier\":\"2026-11\""),
			entry("melon", "Melon Collection", 1, ""),
			entry("earthworm", "Earthworm Kills", 1, "")
		));
		assertEquals(List.of("earthworm", "melon", "melon-monthly", "farmingweight-weekly"), ids(rankings));
		List<EliteRankings.Entry> entries = rankings.entries();
		assertEquals(EliteRankings.Interval.CURRENT, entries.get(1).interval());
		assertEquals("", entries.get(1).intervalLabel());
		assertEquals(EliteRankings.Interval.MONTHLY, entries.get(2).interval());
		assertEquals("Monthly", entries.get(2).intervalLabel());
		assertEquals("November 2026", entries.get(2).intervalDetail());
		assertEquals(EliteRankings.Interval.WEEKLY, entries.get(3).interval());
		assertEquals("Week 45, 2026", entries.get(3).intervalDetail());
	}

	@Test
	public void intervalFallsBackToSlugSuffix() {
		assertEquals(EliteRankings.Interval.MONTHLY, EliteRankings.intervalOf("pests-monthly", ""));
		assertEquals(EliteRankings.Interval.WEEKLY, EliteRankings.intervalOf("farmingweight-weekly", null));
		assertEquals(EliteRankings.Interval.CURRENT, EliteRankings.intervalOf("pests", ""));
		assertEquals(EliteRankings.Interval.OTHER, EliteRankings.intervalOf("event", "season-3"));
	}

	@Test
	public void emptyRanksIsReadyWithNoEntries() {
		EliteRankings rankings = EliteRankings.fromResponse(KEY, Optional.of(response("")), null);
		assertEquals(EliteRankings.State.READY, rankings.state());
		assertTrue(rankings.entries().isEmpty());
		assertEquals(0, rankings.bestRank());
	}

	@Test
	public void onlyInvalidRanksIsReadyWithNoEntries() {
		EliteRankings rankings = ready(entry("wheat", "Wheat Collection", 0, ""));
		assertEquals(EliteRankings.State.READY, rankings.state());
		assertTrue(rankings.entries().isEmpty());
	}

	@Test
	public void failedRequestsAreErrorsForTheirKey() {
		EliteRankings failed = EliteRankings.fromResponse(KEY, Optional.empty(), new RuntimeException("timeout"));
		assertEquals(EliteRankings.State.ERROR, failed.state());
		assertEquals(KEY, failed.key());
		assertFalse(failed.error().isBlank());

		EliteRankings unavailable = EliteRankings.fromResponse(KEY, Optional.empty(), null);
		assertEquals(EliteRankings.State.ERROR, unavailable.state());

		EliteRankings nullBody = EliteRankings.fromResponse(KEY, null, null);
		assertEquals(EliteRankings.State.ERROR, nullBody.state());
	}

	@Test
	public void unexpectedShapesAreErrorsNotEmpty() {
		JsonObject noRanks = JsonParser.parseString("{\"leaderboards\":{}}").getAsJsonObject();
		assertEquals(EliteRankings.State.ERROR, EliteRankings.fromResponse(KEY, Optional.of(noRanks), null).state());
		JsonObject ranksArray = JsonParser.parseString("{\"ranks\":[]}").getAsJsonObject();
		assertEquals(EliteRankings.State.ERROR, EliteRankings.fromResponse(KEY, Optional.of(ranksArray), null).state());
	}

	@Test
	public void malformedEntriesAreSkipped() {
		EliteRankings rankings = ready(String.join(",",
			"\"bad\":5",
			"\"norank\":{\"title\":\"No Rank\"}",
			"\"textrank\":{\"title\":\"Text\",\"rank\":\"abc\"}",
			"\"untitled\":{\"rank\":7,\"amount\":3}",
			entry("ok", "Okay", 2, "")
		));
		assertEquals(List.of("ok", "untitled"), ids(rankings));
		assertEquals("untitled", rankings.entries().get(1).title());
	}

	@Test
	public void keyIgnoresDashesAndCase() {
		String dashed = EliteRankings.key(PLAYER, PROFILE.toUpperCase());
		assertEquals("23842b15c37948489261863692a3d757|23842b15c37948489261863692a3d757", dashed);
		assertEquals(dashed, EliteRankings.key(PLAYER, PROFILE.replace("-", "")));
		assertEquals("", EliteRankings.key(null, PROFILE));
		assertEquals("", EliteRankings.key(PLAYER, " "));
		assertEquals("", EliteRankings.key(PLAYER, null));
	}

	@Test
	public void responseForAnotherProfileIsDropped() {
		EliteRankings shown = EliteRankings.loading(OTHER_KEY);
		EliteRankings late = ready(entry("melon", "Melon Collection", 1, ""));
		assertSame(shown, EliteRankings.resolve(shown, late));
	}

	@Test
	public void responseAfterResetIsDropped() {
		EliteRankings late = ready(entry("melon", "Melon Collection", 1, ""));
		assertSame(EliteRankings.idle(), EliteRankings.resolve(EliteRankings.idle(), late));
	}

	@Test
	public void responseFillsItsOwnLoadingSlot() {
		EliteRankings result = ready(entry("melon", "Melon Collection", 1, ""));
		assertSame(result, EliteRankings.resolve(EliteRankings.loading(KEY), result));
		EliteRankings failed = EliteRankings.fromResponse(KEY, Optional.empty(), null);
		assertSame(failed, EliteRankings.resolve(EliteRankings.loading(KEY), failed));
	}

	@Test
	public void lateDuplicateDoesNotReplaceSettledState() {
		EliteRankings shown = ready(entry("melon", "Melon Collection", 1, ""));
		EliteRankings lateError = EliteRankings.fromResponse(KEY, Optional.empty(), null);
		assertSame(shown, EliteRankings.resolve(shown, lateError));
		EliteRankings loading = EliteRankings.loading(KEY);
		assertSame(loading, EliteRankings.resolve(loading, null));
	}

	@Test
	public void noProfileErrorNeverAcceptsResults() {
		EliteRankings noProfile = EliteRankings.error("", "No profile selected");
		EliteRankings loadingWithoutKey = EliteRankings.loading("");
		EliteRankings result = EliteRankings.fromResponse("", Optional.of(response("")), null);
		assertSame(noProfile, EliteRankings.resolve(noProfile, result));
		assertSame(loadingWithoutKey, EliteRankings.resolve(loadingWithoutKey, result));
	}

	@Test
	public void formatsRanksAndScores() {
		assertEquals("#1", EliteRankings.rankLabel(1));
		assertEquals("#1,000", EliteRankings.rankLabel(1000));

		EliteRankings.Entry weight = new EliteRankings.Entry(
			"farmingweight", "Farming Weight", "Farming Weight", 1, 306071.1386, true,
			EliteRankings.Interval.CURRENT, "", false
		);
		assertEquals("306k", weight.compactAmount());
		assertEquals("306,071.14", weight.exactAmount());

		EliteRankings.Entry contests = new EliteRankings.Entry(
			"participations", "Jacob Contest Participations", "Jacob Contests", 1, 19213, false,
			EliteRankings.Interval.CURRENT, "", false
		);
		assertEquals("19.2k", contests.compactAmount());
		assertEquals("19,213", contests.exactAmount());
		assertEquals("Jacob Contests", contests.label());

		EliteRankings.Entry average = new EliteRankings.Entry(
			"dungeon-class-average", "Dungeon Class Average", "", 40, 48.25, true,
			EliteRankings.Interval.CURRENT, "", false
		);
		assertEquals("48.25", average.compactAmount());
		assertEquals("Dungeon Class Average", average.label());
	}
}
