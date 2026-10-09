package dev.vy.betterpv.client.gui.auctions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.AuctionSnapshot;
import org.junit.Test;

public final class AuctionPageTest {
	@Test
	public void waitsForTheAuctionsTabBeforeRequestingDetails() {
		AuctionPage page = new AuctionPage();
		page.apply(AuctionSnapshot.empty());

		assertFalse(page.detailRequestsEnabled());
		page.requestDetailsIfNeeded();
		assertTrue(page.detailRequestsEnabled());
	}
}
