package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;

public class ChocobitsTest {
	@Test
	public void missingYearsStayZeroAndSortLast() {
		JsonObject bits = JsonParser.parseString("""
			{"total_found": 4, "owned": [
				{"id": 3},
				{"id": 1, "owned_year": 410, "expiry_year": 420},
				{"id": 2, "owned_year": 400, "expiry_year": 405},
				{"id": 0, "owned_year": 1}
			]}
			""").getAsJsonObject();
		List<EventsSnapshot.Chocobit> parsed = EventsSnapshot.parseChocobits(bits);
		assertEquals(List.of(2, 1, 3), parsed.stream().map(EventsSnapshot.Chocobit::id).toList());
		EventsSnapshot.Chocobit undated = parsed.get(2);
		assertEquals(0, undated.ownedYear());
		assertEquals(0, undated.expiryYear());
	}

	@Test
	public void undatedBitsNeverCountAsExpired() {
		EventsSnapshot.Chocobit undated = new EventsSnapshot.Chocobit(1, 0, 0);
		EventsSnapshot.Chocobit old = new EventsSnapshot.Chocobit(2, 400, 405);
		EventsSnapshot.Chocobit live = new EventsSnapshot.Chocobit(3, 410, 420);
		assertFalse(undated.expired(500));
		assertTrue(old.expired(410));
		assertFalse(live.expired(410));
		assertEquals(2, EventsSnapshot.Chocobit.active(List.of(undated, old, live), 410));
	}

	@Test
	public void absentFieldParsesEmpty() {
		assertTrue(EventsSnapshot.parseChocobits(null).isEmpty());
	}
}
