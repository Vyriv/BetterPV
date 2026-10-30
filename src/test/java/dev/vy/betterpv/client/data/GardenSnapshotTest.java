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

	@Test
	public void contestKeyTimestampMatchesSkyBlockEpoch() {
		// Key year is 0-indexed → SkyBlock year = keyYear + 1 (farming-weight from(year + 1, …)).
		// 0:1_1 → Year 1 Early Spring 1 = SkyBlock epoch.
		assertEquals(1_560_275_700L, GardenSnapshot.timestampFromContestKey("0:1_1:WHEAT"));
		// farming-weight SkyBlockTime.fromContestKey("467:4_2:WHEAT") / Elite /contests/1768857300
		assertEquals(1_768_857_300L, GardenSnapshot.timestampFromContestKey("467:4_2:WHEAT"));
		assertEquals(1_768_857_300L, GardenSnapshot.timestampFromContestKey("467:4_2:INK_SACK:3"));
	}
}
