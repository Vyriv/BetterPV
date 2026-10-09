package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GardenSnapshotTest {
	@Test
	public void preservesLegacyCropMetadataInContestKeys() {
		assertEquals("INK_SACK:3", GardenSnapshot.cropFromContestKey("138:10_29:INK_SACK:3"));
		assertEquals("WHEAT", GardenSnapshot.cropFromContestKey("138:10_29:WHEAT"));
		assertEquals("WHEAT", GardenSnapshot.cropFromContestKey("10_29:WHEAT"));
		assertEquals("WHEAT", GardenSnapshot.cropFromContestKey("WHEAT"));
	}
}
