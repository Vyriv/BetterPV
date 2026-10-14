package dev.vy.betterpv.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.gui.nav.PvTab;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public final class EssenceNavigationTest {
	@BeforeClass
	public static void bootstrap() {
		// PvTab / PvSubTab icons touch the item registry.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void essenceAliasesOpenHomeEssence() {
		PvTab.Route expected = new PvTab.Route(PvTab.HOME, PvSubTab.HOME_ESSENCE);
		assertEquals(expected, PvTab.routeFromCommandArg("essence"));
		assertEquals(expected, PvTab.routeFromCommandArg("essences"));
		assertEquals(expected, PvTab.routeFromCommandArg(" ESSENCE "));
		assertEquals(PvTab.HOME, PvTab.fromCommandArg("essence"));
		assertEquals(expected, ProfileViewerOpener.routeFor("essences"));
	}

	@Test
	public void otherPagesKeepDefaultSubtab() {
		assertEquals(new PvTab.Route(PvTab.DUNGEONS, null), PvTab.routeFromCommandArg("dungeon"));
		assertEquals(new PvTab.Route(PvTab.INVENTORIES, null), PvTab.routeFromCommandArg("storage"));
		assertEquals(new PvTab.Route(PvTab.MINING, null), ProfileViewerOpener.routeFor("mining"));
		assertNull(PvTab.routeFromCommandArg("notapage"));
		assertNull(PvTab.routeFromCommandArg(" "));
		assertNull(PvTab.routeFromCommandArg(null));
		assertEquals(new PvTab.Route(PvTab.HOME, null), ProfileViewerOpener.routeFor("notapage"));
	}

	@Test
	public void everySuggestedAliasResolves() {
		String[] aliases = PvTab.commandAliases();
		assertTrue(Arrays.asList(aliases).contains("essence"));
		assertEquals(aliases.length, new HashSet<>(Arrays.asList(aliases)).size());
		Set<PvTab> reached = new HashSet<>();
		for (String alias : aliases) {
			PvTab.Route route = PvTab.routeFromCommandArg(alias);
			assertNotNull(alias, route);
			assertTrue(alias, route.valid());
			reached.add(route.tab());
		}
		assertEquals("every tab has a suggested alias", PvTab.values().length, reached.size());
	}

	@Test
	public void homeKeepsItsSubtabsAndAddsEssenceLast() {
		assertArrayEquals(
			new PvSubTab[] { PvSubTab.HOME_OVERVIEW, PvSubTab.HOME_MISC, PvSubTab.HOME_RECORDS, PvSubTab.HOME_ESSENCE },
			PvTab.HOME.subTabs()
		);
	}

	@Test
	public void essenceMainTabIsGone() {
		for (PvTab tab : PvTab.values()) {
			assertFalse(tab.name().contains("ESSENCE"));
		}
		for (PvSubTab sub : PvSubTab.values()) {
			assertFalse(sub.name(), sub.name().startsWith("ESSENCE_"));
		}
	}

	@Test
	public void routeValidityChecksTheTabsSubtabs() {
		assertTrue(new PvTab.Route(PvTab.HOME, PvSubTab.HOME_ESSENCE).valid());
		assertTrue(new PvTab.Route(PvTab.HOME, null).valid());
		assertFalse(new PvTab.Route(PvTab.DUNGEONS, PvSubTab.HOME_ESSENCE).valid());
		assertFalse(new PvTab.Route(null, PvSubTab.HOME_ESSENCE).valid());
	}

	@Test
	public void langHasEssenceSubtabAndNoTrialKeys() throws Exception {
		try (InputStream in = EssenceNavigationTest.class.getResourceAsStream("/assets/betterpv/lang/en_us.json")) {
			assertNotNull(in);
			JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			assertEquals("Essence", lang.get("betterpv.sub.home_essence").getAsString());
			assertFalse(lang.has("betterpv.tab.essence"));
			for (String key : lang.keySet()) {
				assertFalse(key, key.startsWith("betterpv.sub.essence_"));
			}
		}
	}
}
