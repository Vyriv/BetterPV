package dev.vy.betterpv.client.gui.nav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class SkillSubTabsTest {
	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void gardenAndMiningOpenOnOverview() {
		assertEquals(PvSubTab.GARDEN_OVERVIEW, PvTab.GARDEN.subTabs()[0]);
		assertEquals(PvSubTab.MINING_OVERVIEW, PvTab.MINING.subTabs()[0]);
	}

	@Test
	public void noSummarySubTabs() {
		for (PvTab tab : PvTab.values()) {
			assertFalse(tab + " still lists a summary page",
				Arrays.stream(tab.subTabs()).anyMatch(sub -> sub.name().endsWith("_SUMMARY")));
		}
	}

	@Test
	public void fishingAlwaysHasTrophiesTab() {
		assertEquals(Arrays.asList(PvSubTab.FISHING_MAIN, PvSubTab.FISHING_TROPHIES),
			Arrays.asList(PvTab.FISHING.subTabs()));
	}

	@Test
	public void safariIsPartOfHunting() {
		assertEquals(
			Arrays.asList(PvSubTab.FORAGING_OVERVIEW, PvSubTab.FORAGING_HOTF, PvSubTab.FORAGING_HUNTING,
				PvSubTab.FORAGING_ATTRIBUTE_SHARDS),
			Arrays.asList(PvTab.FORAGING.subTabs()));
	}
}
