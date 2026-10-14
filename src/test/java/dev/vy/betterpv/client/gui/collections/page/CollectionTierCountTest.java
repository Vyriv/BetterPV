package dev.vy.betterpv.client.gui.collections.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.CollectionSnapshot;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.price.HypixelCollectionsCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class CollectionTierCountTest {
	// Live Hypixel tiers after the 0.26.1 Redstone rework.
	private static final long[] REDSTONE_AMOUNTS = {100, 250, 750, 1500, 2500, 5000, 25000, 100000, 250000};
	private static final HypixelCollectionsCache.Item REDSTONE = item("REDSTONE", "Redstone Dust", REDSTONE_AMOUNTS);

	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static HypixelCollectionsCache.Item item(String id, String name, long[] amounts) {
		List<HypixelCollectionsCache.Tier> tiers = new ArrayList<>();
		for (int i = 0; i < amounts.length; i++) {
			tiers.add(new HypixelCollectionsCache.Tier(i + 1, amounts[i], List.of("Reward " + (i + 1), "+4 SkyBlock XP")));
		}
		return new HypixelCollectionsCache.Item(id, name, amounts.length, tiers);
	}

	private static HypixelCollectionsCache.Item tiers(int count) {
		long[] amounts = new long[count];
		for (int i = 0; i < count; i++) {
			amounts[i] = (i + 1) * 1000L;
		}
		return item("TIERS_" + count, "Tiers " + count, amounts);
	}

	private static CollectionSnapshot snapshot(String id, long amount, int unlocked) {
		CollectionSnapshot.Member member = new CollectionSnapshot.Member("abc", "Me", Map.of(id, amount), Map.of(),
			unlocked > 0 ? Map.of(id, unlocked) : Map.of());
		return new CollectionSnapshot(List.of(), List.of(member), "abc", List.of());
	}

	private static List<String> text(List<PvTooltip.Line> lines) {
		return lines.stream()
			.map(l -> l.spans().stream().map(PvTooltip.Span::text).collect(Collectors.joining()))
			.collect(Collectors.toList());
	}

	@Test
	public void redstoneLevel16FromOldTiersShowsAsMaxedNineWithTheOldTierNoted() {
		CollectionSnapshot snapshot = snapshot("REDSTONE", 1_400_000L, 16);
		long amount = snapshot.progressAmount(REDSTONE);
		int tier = snapshot.displayTier(REDSTONE);

		assertEquals(9, tier);
		assertEquals(16, snapshot.legacyTier(REDSTONE));
		assertEquals("Tier 9 / 9", CollectionDetailView.tierText(REDSTONE, tier));
		assertEquals("Maxed (old tier XVI)", CollectionDetailView.nextText(REDSTONE, amount, tier, 16));
		for (HypixelCollectionsCache.Tier t : REDSTONE.tiers()) {
			assertEquals(CollectionDetailView.TierState.UNLOCKED, CollectionDetailView.tierState(REDSTONE, t, amount, tier));
		}
		assertEquals(8, CollectionDetailView.defaultSelection(REDSTONE, tier));
		List<String> tip = text(CollectionDetailView.legacyTip(REDSTONE, 16));
		assertEquals("Redstone Dust XVI", tip.get(0));
		assertTrue(tip.contains("Tiers above IX no longer exist."));
	}

	@Test
	public void oldUnlockStillCountsWhenTheAmountIsBelowTheNewMax() {
		CollectionSnapshot snapshot = snapshot("REDSTONE", 120_000L, 16);

		assertEquals(9, snapshot.displayTier(REDSTONE));
		assertEquals(16, snapshot.legacyTier(REDSTONE));
	}

	@Test
	public void redstoneWithinTheCurrentTiersHasNoOldTier() {
		CollectionSnapshot snapshot = snapshot("REDSTONE", 30_000L, 7);
		long amount = snapshot.progressAmount(REDSTONE);
		int tier = snapshot.displayTier(REDSTONE);

		assertEquals(7, tier);
		assertEquals(0, snapshot.legacyTier(REDSTONE));
		assertEquals("Tier 7 / 9", CollectionDetailView.tierText(REDSTONE, tier));
		assertTrue(CollectionDetailView.nextText(REDSTONE, amount, tier, 0).startsWith("Next tier: "));
		assertEquals(CollectionDetailView.TierState.NEXT,
			CollectionDetailView.tierState(REDSTONE, REDSTONE.tiers().get(7), amount, tier));
	}

	@Test
	public void collectionsWithFewTiersUseTheirOwnCount() {
		HypixelCollectionsCache.Item four = tiers(4);
		CollectionSnapshot snapshot = snapshot(four.id(), 3000L, 0);

		assertEquals(3, snapshot.displayTier(four));
		assertEquals("Tier 3 / 4", CollectionDetailView.tierText(four, 3));
		assertEquals("Maxed", CollectionDetailView.nextText(four, 4000L, 4, 0));
	}

	@Test
	public void collectionsWithMoreThanTenTiersKeepEveryTierInOrder() {
		for (int count : new int[] {11, 12}) {
			HypixelCollectionsCache.Item item = tiers(count);
			CollectionSnapshot snapshot = snapshot(item.id(), 11_500L, 0);

			assertEquals(11, snapshot.displayTier(item));
			assertEquals("Tier 11 / " + count, CollectionDetailView.tierText(item, 11));
			assertEquals(count, item.tiers().size());
			for (int i = 0; i < count; i++) {
				assertEquals(i + 1, item.tiers().get(i).tier());
			}
		}
	}

	/** Every tier shows exactly once across pages, in order, and every slot stays inside the track. */
	private static void assertTrackCoversAllTiers(HypixelCollectionsCache.Item item, int width, int displayTier) {
		CollectionDetailView view = new CollectionDetailView();
		view.open(item);
		int x = 20;
		view.layoutTrack(x, 40, width, displayTier);
		int selected = view.selected();
		int startPage = view.page();
		List<int[]> current = view.visibleSlots();
		boolean selectedVisible = current.stream().anyMatch(s -> s[4] == selected);
		assertTrue("selected tier " + selected + " in view at width " + width, selectedVisible);

		while (view.page() > 0) {
			view.mouseScrolled(x + 1, 45, 1);
			view.layoutTrack(x, 40, width, displayTier);
		}
		List<Integer> seen = new ArrayList<>();
		int pages = view.pageCount();
		for (int p = 0; p < pages; p++) {
			assertEquals(p, view.page());
			int prevRight = Integer.MIN_VALUE;
			for (int[] s : view.visibleSlots()) {
				assertTrue("slot inside track at width " + width, s[0] >= x && s[0] + s[2] <= x + width);
				assertTrue("slots don't overlap at width " + width, s[0] >= prevRight);
				prevRight = s[0] + s[2];
				seen.add(s[4]);
			}
			view.mouseScrolled(x + 1, 45, -1);
			view.layoutTrack(x, 40, width, displayTier);
		}
		List<Integer> expected = new ArrayList<>();
		for (int i = 0; i < item.tiers().size(); i++) {
			expected.add(i);
		}
		assertEquals("width " + width, expected, seen);
		assertTrue(startPage < pages);
	}

	@Test
	public void trackNavigatesEveryTierAtEachBoardWidth() {
		// GUI scale doesn't change layout units, only the board size the popup gets, so cover the
		// narrowest popup up to the widest plus forced paging.
		for (int width : new int[] {150, 220, 300, 335, 444}) {
			for (int count : new int[] {4, 9, 11, 12}) {
				for (int tier : new int[] {0, count / 2, count}) {
					assertTrackCoversAllTiers(tiers(count), width, tier);
				}
			}
		}
		assertTrackCoversAllTiers(REDSTONE, 150, 9);
	}

	@Test
	public void wheelPagesBackAndForthAndStopsAtTheEnds() {
		CollectionDetailView view = new CollectionDetailView();
		view.open(tiers(12));
		view.layoutTrack(20, 40, 150, 12);

		assertEquals(2, view.page());
		view.mouseScrolled(25, 45, -1);
		assertEquals(2, view.page());
		view.mouseScrolled(25, 45, 1);
		assertEquals(1, view.page());
		view.layoutTrack(20, 40, 150, 12);
		view.mouseScrolled(25, 45, 1);
		assertEquals(0, view.page());
		view.layoutTrack(20, 40, 150, 12);
		view.mouseScrolled(25, 45, 1);
		assertEquals(0, view.page());
	}

	@Test
	public void popupTrackShowsEveryTierAtRealBoardSizes() {
		for (int[] board : new int[][] {{300, 184}, {504, 184}, {504, 240}, {700, 420}, {1200, 600}}) {
			for (int count : new int[] {4, 9, 12}) {
				CollectionPopupLayout l = CollectionPopupLayout.compute(0, 0, board[0], board[1], 9,
					new CollectionPopupLayout.Content(count, 3, true, 160, 220, 180, 120));
				assertTrue(l.x() >= 0 && l.x() + l.w() <= board[0]);
				assertTrackCoversAllTiers(tiers(count), l.innerW(), count / 2);
			}
		}
	}
}
