package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class ShardSortAndFormatTest {
	private static ForagingSnapshot.AttributeShardRow row(String name, String rarity, boolean unlocked) {
		return new ForagingSnapshot.AttributeShardRow(name, name, name, rarity, name, 0, 10, 0, 100, unlocked);
	}

	@Test
	public void rarityThenName() {
		List<ForagingSnapshot.AttributeShardRow> sorted = ForagingSnapshot.AttributeShardRow.byRarity(List.of(
			row("zeta", "COMMON", true),
			row("Beta", "LEGENDARY", false),
			row("alpha", "LEGENDARY", true),
			row("odd", "WEIRD", true),
			row("gamma", "rare", true)
		));
		assertEquals(List.of("alpha", "Beta", "gamma", "zeta", "odd"),
			sorted.stream().map(ForagingSnapshot.AttributeShardRow::name).toList());
	}

	@Test
	public void compactCoinsKeepsThreeSignificantFigures() {
		assertEquals("950", FormatUtil.compactCoins(950));
		assertEquals("1.23k", FormatUtil.compactCoins(1_234));
		assertEquals("12.3m", FormatUtil.compactCoins(12_345_678));
		assertEquals("123m", FormatUtil.compactCoins(123_456_789));
		assertEquals("1.5b", FormatUtil.compactCoins(1_500_000_000));
		assertEquals("1m", FormatUtil.compactCoins(999_999));
		assertEquals("2k", FormatUtil.compactCoins(2_000));
	}
}
