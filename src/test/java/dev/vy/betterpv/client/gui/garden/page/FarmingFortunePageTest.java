package dev.vy.betterpv.client.gui.garden.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.FarmingFortuneEstimate;
import dev.vy.betterpv.client.data.FarmingFortuneTemporary;
import dev.vy.betterpv.client.data.GardenData;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class FarmingFortunePageTest {
	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void everyCropMapsBackToItsGardenName() {
		for (String crop : FarmingFortuneEstimate.CROPS) {
			assertEquals(crop, GardenData.prettyCrop(FarmingFortunePage.cropId(crop)));
		}
	}

	@Test
	public void opensOnWheatAndClosesCleanly() {
		FarmingFortunePage page = new FarmingFortunePage();
		assertFalse(page.isOpen());
		page.open();
		assertTrue(page.isOpen());
		assertEquals("Wheat", page.selectedCrop());
		assertFalse("no clicks are handled before the page has drawn", page.mouseClicked(-100, -100));
		page.close();
		assertFalse(page.isOpen());
		assertFalse(page.mouseClicked(0, 0));
	}

	@Test
	public void blossomEquipmentRowsDrawTheirIconById() {
		// NEU only has these as paper; the real texture comes from the item model looked up by id.
		for (String id : new String[] {"BLOSSOM_NECKLACE", "BLOSSOM_CLOAK", "BLOSSOM_BELT", "BLOSSOM_BRACELET"}) {
			PvTooltip.Line row = FarmingFortunePage.withIcon(
				PvTooltip.Line.row("Thorny Blossom Piece", PvDraw.COLOR_TEXT, "+46.2", PvDraw.COLOR_GOLD), id);
			assertEquals(id, row.iconId());
		}
	}

	@Test
	public void temporaryShardSourcesMapBackToTheirShard() {
		assertEquals("infiltration", FarmingFortunePage.shardKeyOf("ATTRIBUTE_SHARD_INFILTRATION;1"));
		assertEquals("pest_fortune", FarmingFortunePage.shardKeyOf("ATTRIBUTE_SHARD_PEST_FORTUNE;1"));
		assertNull(FarmingFortunePage.shardKeyOf("MAGIC_8_BALL"));
		assertNull(FarmingFortunePage.shardKeyOf(null));
	}

	@Test
	public void statusLabelsNeverCallAnUnknownBuffActive() {
		assertEquals("Active", FarmingFortunePage.statusText(FarmingFortuneTemporary.Status.ACTIVE));
		assertEquals("Unconfirmed", FarmingFortunePage.statusText(FarmingFortuneTemporary.Status.UNKNOWN));
		assertEquals("Inactive", FarmingFortunePage.statusText(FarmingFortuneTemporary.Status.INACTIVE));
	}

	@Test
	public void rowsWithoutAnIdStayIconless() {
		PvTooltip.Line row = PvTooltip.Line.row("Accessories", PvDraw.COLOR_TEXT, "+5", PvDraw.COLOR_GOLD);
		assertNull(FarmingFortunePage.withIcon(row, null).iconId());
		assertNull(FarmingFortunePage.withIcon(row, " ").iconId());
		assertNull(FarmingFortunePage.withIcon(row, " ").icon());
	}
}
