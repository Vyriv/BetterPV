package dev.vy.betterpv.client.api;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.client.data.ProfileSnapshot;
import java.util.List;
import org.junit.Test;

public final class BankTransactionParseTest {
	@Test
	public void sortsNewestFirstAndKeepsMillisecondTimestamps() {
		// Hypixel returns banking.transactions oldest first, timestamps in epoch milliseconds.
		JsonObject profile = JsonParser.parseString("{\"banking\":{\"balance\":1,\"transactions\":["
			+ "{\"amount\":100,\"timestamp\":1772409600000,\"action\":\"DEPOSIT\",\"initiator_name\":\"§bChaseCatlantic\"},"
			+ "{\"amount\":200,\"timestamp\":1793743200000,\"action\":\"WITHDRAW\",\"initiator_name\":\"ChaseCatlantic\"},"
			+ "{\"amount\":300,\"timestamp\":1780000000000,\"action\":\"DEPOSIT\",\"initiator_name\":\"Bank Interest\"}"
			+ "]}}").getAsJsonObject();
		List<ProfileSnapshot.BankTransaction> txs = ProfileHomeParser.parseBankTransactions(profile);
		assertEquals(3, txs.size());
		assertEquals(1_793_743_200_000L, txs.get(0).timestampMs());
		assertEquals(1_780_000_000_000L, txs.get(1).timestampMs());
		assertEquals(1_772_409_600_000L, txs.get(2).timestampMs());
		assertEquals("ChaseCatlantic", txs.get(2).initiatorName());
	}
}
