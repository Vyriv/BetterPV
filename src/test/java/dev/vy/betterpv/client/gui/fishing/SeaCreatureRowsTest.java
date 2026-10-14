package dev.vy.betterpv.client.gui.fishing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.BestiaryData;
import dev.vy.betterpv.client.data.BestiarySnapshot;
import java.util.List;
import org.junit.Test;

public class SeaCreatureRowsTest {
	private static BestiarySnapshot.FamilyProgress fam(String name, long kills) {
		BestiaryData.Family def = new BestiaryData.Family(
			"fishing:" + name, name, "fishing", 1, "", 0, List.of(), "", "");
		return new BestiarySnapshot.FamilyProgress(def, kills, 0L, 0, 0, 0L, 0L, false);
	}

	@Test
	public void onlyKilledFamiliesMostKillsFirst() {
		BestiarySnapshot.CategoryProgress category = new BestiarySnapshot.CategoryProgress(
			null,
			List.of(fam("Squid", 40), fam("Sea Walker", 0), fam("Yeti", 3), fam("Agarimoo", 40)),
			0, 0, 0, 0);
		List<BestiarySnapshot.FamilyProgress> rows = FishingPage.seaCreatureRows(category);
		assertEquals(3, rows.size());
		assertEquals("Agarimoo", rows.get(0).family().name());
		assertEquals("Squid", rows.get(1).family().name());
		assertEquals("Yeti", rows.get(2).family().name());
	}

	@Test
	public void missingCategoryGivesNoRows() {
		assertTrue(FishingPage.seaCreatureRows(null).isEmpty());
	}
}
