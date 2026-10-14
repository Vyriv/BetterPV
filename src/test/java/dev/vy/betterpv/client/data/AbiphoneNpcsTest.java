package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

public class AbiphoneNpcsTest {
	private static final JsonObject CONTACTS = JsonParser.parseString("""
		{
		  "Maddox the Slayer": {"callNames": ["maddox", "slayer"]},
		  "Fear Mongerer": {"callNames": ["spooky"]},
		  "Maxwell": {"callNames": ["maxwell", "thaumaturgist"]},
		  "Trevor": {},
		  "Plumber Joe": {},
		  "Philip": {},
		  "Captain Ahone": {},
		  "St. Jerry": {}
		}
		""").getAsJsonObject();

	@Test
	public void resolvesCallNamesAndAliases() {
		assertEquals("Maddox the Slayer", AbiphoneNpcs.displayName("slayer", CONTACTS));
		assertEquals("Fear Mongerer", AbiphoneNpcs.displayName("spooky", CONTACTS));
		assertEquals("Maxwell", AbiphoneNpcs.displayName("thaumaturgist", CONTACTS));
		assertEquals("Trevor", AbiphoneNpcs.displayName("trevor_the_trapper", CONTACTS));
		assertEquals("Plumber Joe", AbiphoneNpcs.displayName("plumber", CONTACTS));
		assertEquals("Philip", AbiphoneNpcs.displayName("pesthunter_phillip", CONTACTS));
	}

	@Test
	public void matchesDirectNames() {
		assertEquals("Captain Ahone", AbiphoneNpcs.displayName("captain_ahone", CONTACTS));
		assertEquals("St. Jerry", AbiphoneNpcs.displayName("st_jerry", CONTACTS));
	}

	@Test
	public void unknownIdFallsBack() {
		assertNull(AbiphoneNpcs.displayName("pet_trainer", CONTACTS));
		assertNull(AbiphoneNpcs.displayName("", CONTACTS));
	}
}
