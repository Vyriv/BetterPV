package dev.vy.betterpv.client.gui.collections.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.price.HypixelCollectionsCache;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class CollectionDetailTest {
	private static final HypixelCollectionsCache.Tier T1 = new HypixelCollectionsCache.Tier(1, 50,
		List.of("Cobblestone Minion Recipes", "+4 SkyBlock XP"));
	private static final HypixelCollectionsCache.Tier T2 = new HypixelCollectionsCache.Tier(2, 100,
		List.of("Stone Platform Recipe", "+4 SkyBlock XP"));
	private static final HypixelCollectionsCache.Tier T3 = new HypixelCollectionsCache.Tier(3, 250,
		List.of("[Lvl 1] Silverfish Recipe", "Auto Smelter Recipe", "+4 SkyBlock XP"));
	private static final HypixelCollectionsCache.Tier T4 = new HypixelCollectionsCache.Tier(4, 5000,
		List.of("+4 SkyBlock XP"));
	private static final HypixelCollectionsCache.Item COBBLE = new HypixelCollectionsCache.Item(
		"COBBLESTONE", "Cobblestone", 4, List.of(T1, T2, T3, T4));

	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static List<String> text(List<PvTooltip.Line> lines) {
		return lines.stream()
			.map(l -> l.spans().stream().map(PvTooltip.Span::text).collect(Collectors.joining()))
			.collect(Collectors.toList());
	}

	@Test
	public void tiersSplitIntoUnlockedNextAndUpcoming() {
		assertEquals(CollectionDetailView.TierState.UNLOCKED, CollectionDetailView.tierState(COBBLE, T1, 120, 2));
		assertEquals(CollectionDetailView.TierState.UNLOCKED, CollectionDetailView.tierState(COBBLE, T2, 120, 2));
		assertEquals(CollectionDetailView.TierState.NEXT, CollectionDetailView.tierState(COBBLE, T3, 120, 2));
		assertEquals(CollectionDetailView.TierState.UPCOMING, CollectionDetailView.tierState(COBBLE, T4, 120, 2));
	}

	@Test
	public void unlockedTierFromApiCountsEvenWhenAmountLags() {
		assertEquals(CollectionDetailView.TierState.UNLOCKED, CollectionDetailView.tierState(COBBLE, T3, 10, 3));
	}

	@Test
	public void rewardSummaryPrefersRecipesOverXp() {
		assertEquals("Cobblestone Minion Recipes", CollectionDetailView.rewardSummary(T1));
		assertEquals("[Lvl 1] Silverfish Recipe +1 more", CollectionDetailView.rewardSummary(T3));
		assertEquals("+4 SkyBlock XP", CollectionDetailView.rewardSummary(T4));
		assertEquals("-", CollectionDetailView.rewardSummary(new HypixelCollectionsCache.Tier(1, 10, List.of())));
	}

	@Test
	public void upcomingTierTipShowsProgressAndAllRewards() {
		List<String> tip = text(CollectionDetailView.tierTip(COBBLE, T3, 120, 2));

		assertEquals("Cobblestone III", tip.get(0));
		assertTrue(tip.contains("Required250"));
		assertTrue(tip.contains("Progress120 / 250 (48%)"));
		assertTrue(tip.contains("Remaining130"));
		assertTrue(tip.contains("[Lvl 1] Silverfish Recipe"));
		assertTrue(tip.contains("Auto Smelter Recipe"));
		assertTrue(tip.contains("+4 SkyBlock XP"));
	}

	@Test
	public void unlockedTierTipSaysUnlockedWithoutProgress() {
		List<String> tip = text(CollectionDetailView.tierTip(COBBLE, T1, 120, 2));

		assertTrue(tip.contains("Unlocked"));
		assertFalse(tip.stream().anyMatch(l -> l.startsWith("Progress")));
	}

	@Test
	public void bossTierWithoutRewardsSaysSo() {
		HypixelCollectionsCache.Tier boss = new HypixelCollectionsCache.Tier(1, 10, List.of());
		HypixelCollectionsCache.Item item = new HypixelCollectionsCache.Item("BONZO", "Bonzo", 1, List.of(boss));

		assertTrue(text(CollectionDetailView.tierTip(item, boss, 4, 0)).contains("Rewards not in the API"));
	}
	private static HypixelCollectionsCache.Item manyTiers(int count) {
		List<HypixelCollectionsCache.Tier> tiers = new java.util.ArrayList<>();
		for (int i = 1; i <= count; i++) {
			tiers.add(new HypixelCollectionsCache.Tier(i, i * 100L, List.of("Reward " + i)));
		}
		return new HypixelCollectionsCache.Item("WHEAT", "Wheat", count, tiers);
	}

	@Test
	public void trackFitsEveryTierWhenThereIsRoom() {
		assertEquals(4, CollectionDetailView.perPage(4, 200));
		assertEquals(1, CollectionDetailView.pageCount(4, 4));
	}

	@Test
	public void narrowTrackPagesInsteadOfSquashing() {
		int per = CollectionDetailView.perPage(12, 150);
		assertEquals(4, per);
		assertEquals(3, CollectionDetailView.pageCount(12, per));
		assertEquals(1, CollectionDetailView.perPage(12, 10));
		assertEquals(12, CollectionDetailView.pageCount(12, 1));
	}

	@Test
	public void defaultSelectionIsTheTierBeingWorkedOn() {
		assertEquals(2, CollectionDetailView.defaultSelection(COBBLE, 2));
		assertEquals(0, CollectionDetailView.defaultSelection(COBBLE, 0));
		assertEquals(3, CollectionDetailView.defaultSelection(COBBLE, 4));
	}

	@Test
	public void trackOpensOnThePageOfTheCurrentTier() {
		CollectionDetailView view = new CollectionDetailView();
		view.open(manyTiers(12));
		view.layoutTrack(20, 40, 150, 5);

		assertEquals(5, view.selected());
		assertEquals(1, view.page());
		assertEquals(3, view.pageCount());
	}

	@Test
	public void arrowsPageThroughTiersAndStopAtTheEnds() {
		CollectionDetailView view = new CollectionDetailView();
		view.open(manyTiers(12));
		view.layoutTrack(20, 40, 150, 5);
		int nextX = 20 + 150 - 6;

		assertTrue(view.mouseClicked(nextX, 45));
		assertEquals(2, view.page());
		assertEquals(8, view.selected());
		view.layoutTrack(20, 40, 150, 5);
		assertTrue(view.mouseClicked(nextX, 45));
		assertEquals(2, view.page());

		view.mouseClicked(25, 45);
		view.layoutTrack(20, 40, 150, 5);
		view.mouseClicked(25, 45);
		view.layoutTrack(20, 40, 150, 5);
		view.mouseClicked(25, 45);
		assertEquals(0, view.page());
		assertEquals(0, view.selected());
	}

	@Test
	public void clickingASlotSelectsThatTier() {
		CollectionDetailView view = new CollectionDetailView();
		view.open(manyTiers(12));
		view.layoutTrack(20, 40, 150, 5);
		int thirdSlotX = 20 + 12 + 4 + 2 * (20 + 6) + 5;

		assertTrue(view.mouseClicked(thirdSlotX, 50));
		assertEquals(6, view.selected());
	}

	private static final CollectionPopupLayout.Content CONTENT =
		new CollectionPopupLayout.Content(4, 3, true, 120, 200, 150, 90);

	@Test
	public void popupAbsorbsInsideClicksAndClosesOnOutsideClick() {
		CollectionDetailView view = new CollectionDetailView();
		view.open(manyTiers(12));
		view.layoutPanel(0, 0, 480, 320, 9, CONTENT);

		assertTrue(view.mouseClicked(240, 160));
		assertTrue(view.isOpen());
		assertTrue("the board margin is outside the popup", view.mouseClicked(5, 5));
		assertFalse(view.isOpen());
		assertFalse(view.mouseClicked(240, 160));
	}

	@Test
	public void closeButtonAndEscapeCloseThePopup() {
		CollectionDetailView view = new CollectionDetailView();
		view.open(COBBLE);
		view.layoutPanel(0, 0, 480, 320, 9, CONTENT);
		CollectionPopupLayout l = CollectionPopupLayout.compute(0, 0, 480, 320, 9, CONTENT);
		int closeX = l.innerX() + l.innerW() - 6;
		int closeY = l.headerY() + CollectionPopupLayout.HEADER_H / 2;

		assertTrue(view.mouseClicked(closeX, closeY));
		assertFalse(view.isOpen());

		view.open(COBBLE);
		assertFalse(view.keyPressed(65));
		assertTrue(view.keyPressed(256));
		assertFalse(view.isOpen());
	}

	@Test
	public void popupFitsSmallBoards() {
		CollectionDetailView view = new CollectionDetailView();
		view.open(COBBLE);
		view.layoutPanel(0, 0, 300, 200, 9, CONTENT);

		assertTrue(view.mouseClicked(150, 100));
		assertTrue(view.isOpen());
		assertTrue(view.mouseClicked(150, 195));
		assertFalse(view.isOpen());
	}

	@Test
	public void singlePageTrackStartsOnTheContentEdgeAndFillsTheWidth() {
		CollectionDetailView view = new CollectionDetailView();
		view.open(manyTiers(9));
		view.layoutTrack(20, 40, 384, 3);

		assertEquals(1, view.pageCount());
		assertTrue(view.mouseClicked(21, 45));
		assertEquals(0, view.selected());
		int lastX = 20 + 8 * (20 + 25);
		assertTrue(view.mouseClicked(lastX + 10, 45));
		assertEquals(8, view.selected());
		assertTrue(lastX + 20 <= 20 + 384);
	}

	@Test
	public void defaultRewardPrefersOneWithARecipe() {
		List<CollectionRewards.Reward> rewards = List.of(
			CollectionRewards.parse("+4 SkyBlock XP", n -> null),
			CollectionRewards.parse("Farmhand Helmet Recipe", n -> "FARMHAND_HELMET"),
			CollectionRewards.parse("Enchanted Bread Recipe", n -> "ENCHANTED_BREAD")
		);

		assertEquals(2, CollectionDetailView.defaultReward(rewards, "ENCHANTED_BREAD"::equals));
		assertEquals(1, CollectionDetailView.defaultReward(rewards, id -> false));
		assertEquals(-1, CollectionDetailView.defaultReward(rewards.subList(0, 1), id -> true));
	}

	@Test
	public void romanNumeralsMatchHypixelTierNames() {
		assertEquals("IV", CollectionDetailView.roman(4));
		assertEquals("IX", CollectionDetailView.roman(9));
		assertEquals("XXV", CollectionDetailView.roman(25));
	}
}
