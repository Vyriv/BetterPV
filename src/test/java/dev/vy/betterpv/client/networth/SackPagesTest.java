package dev.vy.betterpv.client.networth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.InventorySnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class SackPagesTest {
	private static final Map<String, List<String>> DEFS = new LinkedHashMap<>();
	private static final Map<String, String> ITEMS = new LinkedHashMap<>();

	static {
		DEFS.put("Mining", List.of("COBBLESTONE", "ICE"));
		DEFS.put("Nether", List.of("NETHERRACK", "MAGMA_CREAM"));
		DEFS.put("Events", List.of("WINTER_FRAGMENT", "GREEN_CANDY", "SHARK_FIN"));
		DEFS.put("Winter", List.of("WINTER_FRAGMENT", "ICE"));
		DEFS.put("Spooky", List.of("GREEN_CANDY"));
		DEFS.put("Rune", List.of());
		ITEMS.put("Mining", "LARGE_MINING_SACK");
		ITEMS.put("Nether", "LARGE_NETHER_SACK");
		ITEMS.put("Events", "LARGE_EVENTS_SACK");
		ITEMS.put("Winter", "LARGE_WINTER_SACK");
		ITEMS.put("Spooky", "LARGE_CANDY_SACK");
		ITEMS.put("Rune", "RUNE_SACK");
	}

	private static List<String> titles(List<InventorySnapshot.Page> pages) {
		return pages.stream().map(InventorySnapshot.Page::title).collect(Collectors.toList());
	}

	private static InventorySnapshot.Page page(List<InventorySnapshot.Page> pages, String title) {
		return pages.stream().filter(p -> p.title().equals(title)).findFirst().orElseThrow();
	}

	private static List<String> ids(InventorySnapshot.Page page) {
		return page.slots().stream().map(InventorySnapshot.Slot::id).collect(Collectors.toList());
	}

	@Test
	public void onlyOwnedSacksShownIncludingEmptyOnes() {
		Map<String, Integer> counts = Map.of("COBBLESTONE", 10);
		Set<String> owned = Set.of(SackDecoder.sackKey("LARGE_MINING_SACK"), SackDecoder.sackKey("SMALL_NETHER_SACK"));

		List<InventorySnapshot.Page> pages = SackDecoder.buildSackPages(DEFS, ITEMS, counts, owned);

		assertTrue(titles(pages).contains("Mining"));
		assertTrue("small variant counts as owning the sack", titles(pages).contains("Nether"));
		assertFalse(titles(pages).contains("Winter"));
		assertFalse(titles(pages).contains("Events"));
		assertFalse("no Rune page unless the Rune Sack is owned", titles(pages).contains("Rune"));
	}

	@Test
	public void eventsDoesNotRepeatItemsShownInOtherOwnedSacks() {
		Map<String, Integer> counts = Map.of("WINTER_FRAGMENT", 3, "GREEN_CANDY", 5, "SHARK_FIN", 2);
		Set<String> owned = Set.of("EVENTS_SACK", "WINTER_SACK");

		List<InventorySnapshot.Page> pages = SackDecoder.buildSackPages(DEFS, ITEMS, counts, owned);

		assertEquals(List.of("WINTER_FRAGMENT", "ICE"), ids(page(pages, "Winter")));
		assertEquals("Spooky not owned, so candy stays in Events", List.of("GREEN_CANDY", "SHARK_FIN"), ids(page(pages, "Events")));
	}

	@Test
	public void unknownOwnershipShowsOnlySacksHoldingSomething() {
		Map<String, Integer> counts = Map.of("NETHERRACK", 4);

		List<InventorySnapshot.Page> pages = SackDecoder.buildSackPages(DEFS, ITEMS, counts, null);

		assertEquals(List.of("Nether"), titles(pages));
	}

	@Test
	public void countedItemInUnownedSackGetsItsRealSackPage() {
		Map<String, Integer> counts = Map.of("MAGMA_CREAM", 7, "RUNE_ZAP_1", 1);
		Set<String> owned = Set.of("MINING_SACK");

		List<InventorySnapshot.Page> pages = SackDecoder.buildSackPages(DEFS, ITEMS, counts, owned);

		assertEquals(List.of("Mining", "Nether", "Rune"), titles(pages));
		assertTrue(ids(page(pages, "Nether")).contains("MAGMA_CREAM"));
		assertEquals(List.of("RUNE_ZAP_1"), ids(page(pages, "Rune")));
	}

	@Test
	public void sharedItemAlreadyInOwnedSackDoesNotPullInUnownedSack() {
		Map<String, Integer> counts = Map.of("WINTER_FRAGMENT", 3);
		Set<String> owned = Set.of("EVENTS_SACK");

		List<InventorySnapshot.Page> pages = SackDecoder.buildSackPages(DEFS, ITEMS, counts, owned);

		assertEquals(List.of("Events"), titles(pages));
		assertTrue(ids(page(pages, "Events")).contains("WINTER_FRAGMENT"));
	}

	@Test
	public void unownedSacksSharingAnItemOnlyShowItOnce() {
		Map<String, Integer> counts = Map.of("ICE", 9);
		Set<String> owned = Set.of("NETHER_SACK");

		List<InventorySnapshot.Page> pages = SackDecoder.buildSackPages(DEFS, ITEMS, counts, owned);

		assertEquals("first sack listing ICE claims it", List.of("Mining", "Nether"), titles(pages));
	}

	@Test
	public void onlyUnknownIdsLandInUnsorted() {
		Map<String, Integer> counts = Map.of("MAGMA_CREAM", 7, "MYSTERY_THING", 2);
		Set<String> owned = Set.of("NETHER_SACK");

		List<InventorySnapshot.Page> pages = SackDecoder.buildSackPages(DEFS, ITEMS, counts, owned);

		assertEquals(List.of("Nether", SackDecoder.UNSORTED_PAGE), titles(pages));
		assertEquals(List.of("MYSTERY_THING"), ids(page(pages, SackDecoder.UNSORTED_PAGE)));
		assertFalse(titles(pages).contains("Other"));
	}

	@Test
	public void runesGetTheRuneSackEvenWhenNeuDropsIt() {
		Map<String, List<String>> defs = new LinkedHashMap<>(DEFS);
		defs.remove("Rune");
		Map<String, Integer> counts = Map.of("RUNE_ZAP_1", 3, "RUNE_MUSIC_2", 1, "MYSTERY_THING", 2);
		Set<String> owned = Set.of("RUNE_SACK");

		List<InventorySnapshot.Page> pages = SackDecoder.buildSackPages(defs, Map.of(), counts, owned);

		assertEquals(List.of("Rune", SackDecoder.UNSORTED_PAGE), titles(pages));
		assertEquals(Set.of("RUNE_ZAP_1", "RUNE_MUSIC_2"), Set.copyOf(ids(page(pages, "Rune"))));
		assertEquals("runes are not counted twice", List.of("MYSTERY_THING"), ids(page(pages, SackDecoder.UNSORTED_PAGE)));
	}

	@Test
	public void noRunePageWithoutRunesOrOwnership() {
		Map<String, List<String>> defs = new LinkedHashMap<>(DEFS);
		defs.remove("Rune");
		List<InventorySnapshot.Page> pages = SackDecoder.buildSackPages(defs, Map.of(), Map.of("NETHERRACK", 1), Set.of("NETHER_SACK"));
		assertEquals(List.of("Nether"), titles(pages));
	}
}
