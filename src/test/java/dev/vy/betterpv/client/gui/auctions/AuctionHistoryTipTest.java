package dev.vy.betterpv.client.gui.auctions;

import static org.junit.Assert.assertEquals;

import dev.vy.betterpv.client.data.AuctionSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public final class AuctionHistoryTipTest {
	private static final long NOW = 1_800_000_000_000L;

	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static AuctionSnapshot.Listing listing(boolean bin, long start, long high, long endMs) {
		return new AuctionSnapshot.Listing("id", "HYPERION", "Hyperion", "LEGENDARY", bin, start, high, endMs, null);
	}

	private static List<String> plain(List<String> lines) {
		return lines.stream().map(l -> l.replaceAll("\u00a7.", "")).toList();
	}

	@Test
	public void soldBinShowsPriceAndWhen() {
		List<String> lines = plain(AuctionPage.transactionLines(
			listing(true, 950_000_000L, 0L, NOW - 3 * 3_600_000L), AuctionSnapshot.Bucket.SOLD, NOW));
		assertEquals("Sold for: " + FormatUtil.shortCoins(950_000_000L) + " coins (BIN)", lines.get(0));
		assertEquals("Ended " + FormatUtil.prettySpan(3 * 3_600_000L) + " ago", lines.get(1));
	}

	@Test
	public void boughtAuctionUsesWinningBid() {
		List<String> lines = plain(AuctionPage.transactionLines(
			listing(false, 1_000_000L, 4_200_000L, NOW - 10_000L), AuctionSnapshot.Bucket.BOUGHT, NOW));
		assertEquals("Bought for: " + FormatUtil.shortCoins(4_200_000L) + " coins (Auction)", lines.get(0));
		assertEquals("Ended just now", lines.get(1));
	}

	@Test
	public void unknownPriceIsNotZero() {
		List<String> lines = plain(AuctionPage.transactionLines(listing(true, 0L, 0L, 0L), AuctionSnapshot.Bucket.SOLD, NOW));
		assertEquals(List.of("Sold for: Unknown (BIN)"), lines);
	}
}
