package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class MythosStatsTest {
	@Test
	public void burrowsUseConfirmedLabelsInDigOrder() {
		JsonObject mythos = JsonParser.parseString("""
			{"kills": 4210,
			 "burrows_dug_treasure": {"total": 300, "LEGENDARY": 300},
			 "burrows_chains_complete": {"total": 250, "EPIC": 50, "LEGENDARY": 200},
			 "burrows_dug_combat": {"total": 900, "none": 10, "LEGENDARY": 890},
			 "burrows_dug_next": {"total": 260, "LEGENDARY": 260}}
			""").getAsJsonObject();

		MiscStatsSnapshot.MythosStats m = MiscStatsSnapshot.parseMythos(mythos);

		assertEquals(4210L, m.kills());
		assertEquals(List.of("Arrow", "Mob", "Treasure", "Chains Completed"),
			m.burrows().stream().map(MiscStatsSnapshot.BurrowCount::label).toList());
		MiscStatsSnapshot.BurrowCount mob = m.burrows().get(1);
		assertEquals("burrows_dug_combat", mob.key());
		assertEquals(Map.of("No Griffin", 10L, "Legendary", 890L), mob.byRarity());
		assertEquals(900L, mob.total());
	}

	@Test
	public void missingTotalFallsBackToRaritySum() {
		JsonObject mythos = JsonParser.parseString("""
			{"burrows_dug_next": {"RARE": 4, "EPIC": 6}}
			""").getAsJsonObject();

		assertEquals(10L, MiscStatsSnapshot.parseMythos(mythos).burrows().get(0).total());
	}
}
