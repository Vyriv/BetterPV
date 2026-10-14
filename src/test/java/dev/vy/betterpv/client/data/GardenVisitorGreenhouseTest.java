package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class GardenVisitorGreenhouseTest {
	@Test
	public void visitorStates() {
		assertEquals(GardenSnapshot.VisitorState.ACCEPTED,
			new GardenSnapshot.VisitorRow("jacob", "Jacob", "", 5, 1, 4).state());
		assertEquals(GardenSnapshot.VisitorState.VISITED,
			new GardenSnapshot.VisitorRow("jacob", "Jacob", "", 3, 0, 3).state());
		assertEquals(GardenSnapshot.VisitorState.NEVER,
			new GardenSnapshot.VisitorRow("jacob", "Jacob", "", 0, 0, 0).state());
	}

	@Test
	public void deadPlantIsNotAMutation() {
		List<GardenSnapshot.GreenhouseRow> rows = GardenSnapshot.buildGreenhouse(
			List.of("veilshroom", "dead_plant", "devourer"),
			List.of("veilshroom", "devourer")
		);
		assertEquals(2, rows.size());
		assertFalse(rows.stream().anyMatch(r -> r.id().equals("DEAD_PLANT")));
		assertTrue(rows.stream().allMatch(GardenSnapshot.GreenhouseRow::analyzed));
	}
}
