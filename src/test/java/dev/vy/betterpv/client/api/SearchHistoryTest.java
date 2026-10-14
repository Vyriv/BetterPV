package dev.vy.betterpv.client.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class SearchHistoryTest {
	private static final UUID A = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
	private static final UUID B = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
	private static final UUID C = UUID.fromString("61699b2e-d327-4a01-9f1e-0ea8c3f06bc6");
	private static final UUID D = UUID.fromString("f7c77d99-9f15-4a66-a87d-c4a51ef30d19");

	private static List<String> names(SearchHistory history) {
		return history.entries().stream().map(SearchHistory.Entry::name).toList();
	}

	@Test
	public void newestFirstAndCappedAtThree() {
		SearchHistory history = SearchHistory.empty()
			.record(A, "Notch")
			.record(B, "jeb_")
			.record(C, "Dinnerbone");
		assertEquals(List.of("Dinnerbone", "jeb_", "Notch"), names(history));

		history = history.record(D, "Grumm");
		assertEquals(SearchHistory.MAX, history.entries().size());
		assertEquals(List.of("Grumm", "Dinnerbone", "jeb_"), names(history));
	}

	@Test
	public void repeatSearchMovesToTopWithoutDuplicate() {
		SearchHistory history = SearchHistory.empty()
			.record(A, "Notch")
			.record(B, "jeb_")
			.record(C, "Dinnerbone")
			.record(A, "Notch");
		assertEquals(List.of("Notch", "Dinnerbone", "jeb_"), names(history));
	}

	@Test
	public void dedupesByUuidAndKeepsLatestName() {
		SearchHistory history = SearchHistory.empty()
			.record(A, "OldName")
			.record(B, "jeb_")
			.record(A, "NewName");
		assertEquals(List.of("NewName", "jeb_"), names(history));
		assertEquals(A, history.entries().get(0).uuid());
	}

	@Test
	public void dedupesByNameIgnoringCaseWhenUuidMissing() {
		SearchHistory history = SearchHistory.empty()
			.record(null, "notch")
			.record(B, "jeb_")
			.record(null, "Notch");
		assertEquals(List.of("Notch", "jeb_"), names(history));
	}

	@Test
	public void ignoresInvalidNames() {
		SearchHistory history = SearchHistory.empty().record(A, "Notch");
		assertSame(history, history.record(B, ""));
		assertSame(history, history.record(B, null));
		assertSame(history, history.record(B, "not a name"));
		assertSame(history, history.record(B, "waytoolongusername123"));
	}

	@Test
	public void filtersByUsernameIgnoringCase() {
		SearchHistory history = SearchHistory.empty()
			.record(A, "Notch")
			.record(B, "jeb_")
			.record(C, "Dinnerbone");
		assertEquals(3, history.filter("").size());
		assertEquals(3, history.filter("  ").size());
		assertEquals(List.of("Notch"), history.filter("NOT").stream().map(SearchHistory.Entry::name).toList());
		assertEquals(List.of("Dinnerbone", "jeb_"),
			history.filter("e").stream().map(SearchHistory.Entry::name).toList());
		assertTrue(history.filter("xyz").isEmpty());
	}

	@Test
	public void survivesRestartThroughConfigDocument() {
		SearchHistory history = SearchHistory.empty()
			.record(A, "Notch")
			.record(null, "jeb_")
			.record(C, "Dinnerbone");
		JsonObject doc = new JsonObject();
		doc.addProperty("profileViewerScalePercent", 110);
		BetterPVConfig.writeSearchHistory(doc, history);

		JsonObject reloaded = JsonParser.parseString(doc.toString()).getAsJsonObject();
		SearchHistory restored = BetterPVConfig.readSearchHistory(reloaded);
		assertEquals(history.entries(), restored.entries());
		assertNull(restored.entries().get(1).uuid());
		assertEquals(110, reloaded.get("profileViewerScalePercent").getAsInt());
	}

	@Test
	public void loadingSkipsJunkAndCapsOversizedLists() {
		JsonArray array = new JsonArray();
		array.add("junk");
		for (String name : List.of("one", "two", "bad name", "three", "four")) {
			JsonObject entry = new JsonObject();
			entry.addProperty("name", name);
			entry.addProperty("uuid", "not-a-uuid");
			array.add(entry);
		}
		JsonObject doc = new JsonObject();
		doc.add("searchHistory", array);
		SearchHistory restored = BetterPVConfig.readSearchHistory(doc);
		assertEquals(List.of("one", "two", "three"), names(restored));
		assertTrue(BetterPVConfig.readSearchHistory(new JsonObject()).entries().isEmpty());
	}
}
