package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.client.gui.nav.MuseumSort;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class MuseumLegacyPieceTest {
	@BeforeClass
	public static void bootstrap() {
		// MuseumSort icons touch the item registry.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Before
	public void buildCatalog() {
		MuseumCatalog.rebuild(List.of(
			item("""
				{"id":"PESTHUNTERS_BELT","museum_data":{"armor_set_donation_xp":{"PESTHUNTER":4},"category":"FARMING","game_stage":"AMATEUR"}}"""),
			item("""
				{"id":"PESTHUNTERS_GLOVES","museum_data":{"armor_set_donation_xp":{"PESTHUNTER":4},"category":"FARMING","game_stage":"AMATEUR"}}"""),
			item("""
				{"id":"PESTHUNTERS_NECKLACE","museum_data":{"armor_set_donation_xp":{"PESTHUNTER":4},"category":"FARMING","game_stage":"AMATEUR"}}"""),
			item("""
				{"id":"PESTHUNTERS_CLOAK","museum_data":{"armor_set_donation_xp":{"PESTHUNTER":4},"category":"FARMING","game_stage":"AMATEUR"}}"""),
			item("""
				{"id":"HYPERION","museum_data":{"donation_xp":20,"category":"COMBAT","game_stage":"MASTER"}}""")
		));
	}

	@After
	public void clearCatalog() {
		MuseumCatalog.invalidate();
	}

	@Test
	public void legacyPieceKeysAreNotSpecialDonations() {
		Map<String, InventoryDecoder.Stack> raw = new LinkedHashMap<>();
		raw.put("PESTHUNTERS_NECKLACE", stack("PESTHUNTERS_NECKLACE"));
		raw.put("PESTHUNTERS_BELT", stack("PESTHUNTERS_BELT"));
		raw.put("PESTHUNTERS_GLOVES", stack("PESTHUNTERS_GLOVES"));
		raw.put("PESTHUNTER", stack("PESTHUNTERS_CLOAK"));
		raw.put("HYPERION", stack("HYPERION"));

		Map<String, MuseumCatalog.ResolvedDonation> out = MuseumCatalog.resolveDonations(raw);

		assertEquals(List.of("PESTHUNTER", "HYPERION"), List.copyOf(out.keySet()));
		assertEquals("PESTHUNTERS_CLOAK", out.get("PESTHUNTER").stack().id());
		for (String key : out.keySet()) {
			assertTrue(key, MuseumCatalog.isCataloged(key));
		}
		assertEquals(MuseumSort.FARMING, MuseumCatalog.sortOfDonationId("PESTHUNTER"));
	}

	@Test
	public void legacyPiecesAloneDoNotDonateTheSet() {
		Map<String, InventoryDecoder.Stack> raw = new LinkedHashMap<>();
		raw.put("PESTHUNTERS_BELT", stack("PESTHUNTERS_BELT"));

		Map<String, MuseumCatalog.ResolvedDonation> out = MuseumCatalog.resolveDonations(raw);

		assertFalse(out.containsKey("PESTHUNTER"));
		assertFalse(out.containsKey("PESTHUNTERS_BELT"));
	}

	@Test
	public void realSpecialAndUnknownIdsStillResolve() {
		Map<String, InventoryDecoder.Stack> raw = new LinkedHashMap<>();
		raw.put("SPECIAL:0", stack("EASTER_EGG_2020"));
		raw.put("SOME_FUTURE_ITEM", stack("SOME_FUTURE_ITEM"));

		Map<String, MuseumCatalog.ResolvedDonation> out = MuseumCatalog.resolveDonations(raw);

		assertTrue(out.containsKey("SPECIAL:0"));
		assertTrue(out.containsKey("SOME_FUTURE_ITEM"));
		assertFalse(MuseumCatalog.isCataloged("SOME_FUTURE_ITEM"));
	}

	private static JsonObject item(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	private static InventoryDecoder.Stack stack(String id) {
		return new InventoryDecoder.Stack(id, 1, null, List.of(), false);
	}
}
