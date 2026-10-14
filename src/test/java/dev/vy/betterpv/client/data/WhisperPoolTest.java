package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;

public class WhisperPoolTest {
	private static JsonObject core(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@Test
	public void leftUsesSelectedLoadoutOnly() {
		JsonObject core = core("""
			{"whispers": {
			  "forest": {"total": 50000, "1": {"spent": 30000}, "2": {"spent": 12000}},
			  "desert": {"total": 8000, "1": {"spent": 8000}}
			}}
			""");

		List<ForagingSnapshot.WhisperPool> slot2 = ForagingSnapshot.parseWhisperPools(core, 0L, 0L, 2);
		ForagingSnapshot.WhisperPool forest = slot2.get(0);
		assertEquals("forest", forest.id());
		assertEquals(50000L, forest.total());
		assertEquals(12000L, forest.spent());
		assertEquals(38000L, forest.balance());
		ForagingSnapshot.WhisperPool desert = slot2.get(1);
		assertEquals(0L, desert.spent());
		assertEquals(8000L, desert.balance());

		ForagingSnapshot.WhisperPool forest1 = ForagingSnapshot.parseWhisperPools(core, 0L, 0L, 1).get(0);
		assertEquals(20000L, forest1.balance());
	}

	@Test
	public void legacyFieldsStillWork() {
		ForagingSnapshot.WhisperPool pool = ForagingSnapshot.parseWhisperPools(core("{}"), 500L, 1500L, 1).get(0);
		assertEquals(2000L, pool.total());
		assertEquals(500L, pool.balance());
	}
}
