package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

public class KuudraFollowerTest {
	@Test
	public void readsLeggingsQuestWithoutLoremaster() {
		JsonObject quests = JsonParser.parseString(
			"{\"found_kuudra_book\":true,\"found_kuudra_leggings\":true,\"last_kuudra_relic\":1757270119600}"
		).getAsJsonObject();

		CrimsonSnapshot.KuudraFollower f = CrimsonSnapshot.KuudraFollower.from(quests);

		assertFalse(f.loremaster());
		assertTrue(f.cathedralBook());
		assertTrue(f.leggings());
		assertEquals(1757270119600L, f.relicClaimedMs());
	}

	@Test
	public void missingQuestsIsNone() {
		assertEquals(CrimsonSnapshot.KuudraFollower.NONE, CrimsonSnapshot.KuudraFollower.from(null));
		assertTrue(CrimsonSnapshot.KuudraFollower.from(
			JsonParser.parseString("{\"kuudra_loremaster\":true}").getAsJsonObject()
		).loremaster());
	}
}
