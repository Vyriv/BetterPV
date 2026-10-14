package dev.vy.betterpv.client.gui.home.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.DungeonSnapshot;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public final class EssencePageTest {
	private static final DungeonSnapshot.EssenceShop DRAGON = new DungeonSnapshot.EssenceShop(
		"dragon", "Dragon", 42_000L, "ESSENCE_DRAGON", List.of(
			new DungeonSnapshot.EssencePerk("fero", "Ferocity", 3, 5),
			new DungeonSnapshot.EssencePerk("dmg", "Damage", 5, 5)
		));
	private static final DungeonSnapshot.EssenceShop FOSSIL = new DungeonSnapshot.EssenceShop(
		"fossil", "Fossil", 7L, "ESSENCE_FOSSIL", List.of(
			new DungeonSnapshot.EssencePerk("dig", "Dig", 0, 3)
		));

	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void startsOnFirstShopWithAllElevenListed() {
		EssencePage page = new EssencePage();
		assertEquals(11, page.entries().size());
		assertEquals("wither", page.selectedEntry().def().id());
	}

	@Test
	public void selectingAShopShowsThatShopsData() {
		EssencePage page = new EssencePage();
		page.applyShops(id -> switch (id) {
			case "dragon" -> DRAGON;
			case "fossil" -> FOSSIL;
			default -> null;
		});
		assertTrue(page.select("dragon"));
		assertSame(DRAGON, page.selectedEntry().shop());
		assertEquals(2, page.selectedEntry().shop().perks().size());
		assertTrue(page.select("fossil"));
		assertSame(FOSSIL, page.selectedEntry().shop());
		assertFalse(page.select("nope"));
		assertSame(FOSSIL, page.selectedEntry().shop());
	}

	@Test
	public void selectionSurvivesReloads() {
		EssencePage page = new EssencePage();
		page.select("dragon");
		page.applyShops(id -> id.equals("dragon") ? DRAGON : null);
		assertEquals("dragon", page.selectedEntry().def().id());
		assertSame(DRAGON, page.selectedEntry().shop());
	}

	@Test
	public void upDownKeysMoveSelectionAndStopAtEnds() {
		EssencePage page = new EssencePage();
		assertTrue(page.keyPressed(265));
		assertEquals("wither", page.selectedEntry().def().id());
		assertTrue(page.keyPressed(264));
		assertEquals("undead", page.selectedEntry().def().id());
		page.select(10);
		assertTrue(page.keyPressed(264));
		assertEquals("crimson", page.selectedEntry().def().id());
		assertFalse(page.keyPressed(262));
	}

	@Test
	public void inputBeforeFirstRenderIsIgnored() {
		EssencePage page = new EssencePage();
		assertFalse(page.mouseClicked(10, 10));
		assertFalse(page.mouseScrolled(10, 10, -1));
	}
}
