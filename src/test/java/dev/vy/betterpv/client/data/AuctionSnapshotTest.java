package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public final class AuctionSnapshotTest {
	// 2026-01-01T00:00:00Z
	private static final long NOW = 1_767_225_600_000L;
	private static final UUID PLAYER = UUID.fromString("b046873b-b2aa-4995-b6e9-bd3b8a833072");

	private static JsonObject summary(String id, long highestBid, String end) {
		JsonObject o = new JsonObject();
		o.addProperty("auctionId", id);
		o.addProperty("itemName", "Jungle Axe");
		o.addProperty("tag", "JUNGLE_AXE");
		o.addProperty("startingBid", 480);
		o.addProperty("highestBid", highestBid);
		o.addProperty("end", end);
		o.addProperty("bin", false);
		return o;
	}

	@Test
	public void soldOnlyKeepsEndedAuctionsThatGotABid() {
		JsonArray arr = new JsonArray();
		arr.add(summary("sold", 2573, "2025-05-20T01:46:53"));
		arr.add(summary("expired-unsold", 0, "2025-05-20T01:46:53"));
		arr.add(summary("still-running", 9000, "2026-06-01T00:00:00"));
		List<AuctionSnapshot.Listing> sold = AuctionSnapshot.parseCoflSold(arr, NOW);
		assertEquals(1, sold.size());
		assertEquals("sold", sold.get(0).auctionId());
		assertEquals(2573L, sold.get(0).price());
	}

	@Test
	public void boughtSkipsLiveAuctionsAndOutbids() {
		JsonArray arr = new JsonArray();
		JsonObject won = summary("won", 500_000, "2025-06-22T18:36:24");
		won.addProperty("highestOwnBid", 500_000);
		JsonObject outbid = summary("outbid", 463_877_369, "2025-06-22T18:36:24");
		outbid.addProperty("highestOwnBid", 500_000);
		JsonObject leadingLive = summary("leading-live", 700_000, "2026-06-22T18:36:24");
		leadingLive.addProperty("highestOwnBid", 700_000);
		arr.add(won);
		arr.add(outbid);
		arr.add(leadingLive);
		List<AuctionSnapshot.Listing> bought = AuctionSnapshot.parseCoflBought(arr, NOW);
		assertEquals(1, bought.size());
		assertEquals("won", bought.get(0).auctionId());
	}

	@Test
	public void coflDetailIdentifiesSellerProfile() {
		JsonObject detail = JsonParser.parseString("""
			{"auctioneerId":"b046873bb2aa4995b6e9bd3b8a833072","profileId":"CE74071E-A6AA-4C42-8B2D-695E91530653",
			 "bids":[{"bidder":"adda78fea30a46a9bdc0507b8e72b818","profileId":"98b161be725a4565aa41b01d2a856be3","amount":2573}]}
			""").getAsJsonObject();
		assertEquals("CE74071E-A6AA-4C42-8B2D-695E91530653", AuctionSnapshot.coflProfileFor(detail, PLAYER));
	}

	@Test
	public void coflDetailUsesThePlayersHighestBidProfile() {
		JsonObject detail = JsonParser.parseString("""
			{"auctioneerId":"265f985cb74445fbac8984acc3332aa0","profileId":"seller-profile",
			 "bids":[
			  {"bidder":"b046873bb2aa4995b6e9bd3b8a833072","profileId":"old-low-bid","amount":100},
			  {"bidder":"adda78fea30a46a9bdc0507b8e72b818","profileId":"someone-else","amount":900},
			  {"bidder":"b046873bb2aa4995b6e9bd3b8a833072","profileId":"ironman","amount":1000}
			 ]}
			""").getAsJsonObject();
		assertEquals("ironman", AuctionSnapshot.coflProfileFor(detail, PLAYER));
	}

	@Test
	public void unidentifiedHistoryIsNotClaimedForAnyProfile() {
		JsonObject detail = JsonParser.parseString("""
			{"auctioneerId":"265f985cb74445fbac8984acc3332aa0","profileId":"seller-profile","bids":[]}
			""").getAsJsonObject();
		assertEquals("", AuctionSnapshot.coflProfileFor(detail, PLAYER));

		AuctionSnapshot.Listing listing = new AuctionSnapshot.Listing("a", "T", "n", "", false, 1, 2, 3, null);
		assertFalse(listing.profileKnown());
		assertFalse(listing.onProfile("ironman"));
		AuctionSnapshot.Listing tagged = listing.withEnrichment("n", "", null, List.of(), "CE74071E-A6AA-4C42-8B2D-695E91530653");
		assertTrue(tagged.onProfile("ce74071ea6aa4c428b2d695e91530653"));
		assertFalse(tagged.onProfile("other"));
	}
}
