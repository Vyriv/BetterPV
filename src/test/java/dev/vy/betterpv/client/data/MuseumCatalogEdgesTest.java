package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class MuseumCatalogEdgesTest {
	@Test
	public void pieceKeyedParentFoldsOntoSet() {
		Map<String, String> upgradeTo = new LinkedHashMap<>();
		upgradeTo.put("MANGROVE_GRIPPERS", "VEILSHROOM_BRACELET");
		upgradeTo.put("NECRON_BLADE", "HYPERION");
		Map<String, String> pieceToSet = Map.of(
			"MANGROVE_GRIPPERS", "MANGROVE",
			"MANGROVE_LOCKET", "MANGROVE"
		);

		Map<String, List<String>> children = MuseumCatalog.childrenOf(upgradeTo, pieceToSet);

		assertEquals(List.of("MANGROVE"), children.get("VEILSHROOM_BRACELET"));
		assertEquals(List.of("NECRON_BLADE"), children.get("HYPERION"));
		assertFalse(children.values().stream().anyMatch(l -> l.contains("MANGROVE_GRIPPERS")));
	}

	@Test
	public void edgesWithinOneSetAreDropped() {
		Map<String, String> upgradeTo = Map.of("MANGROVE_GRIPPERS", "MANGROVE_LOCKET");
		Map<String, String> pieceToSet = Map.of(
			"MANGROVE_GRIPPERS", "MANGROVE",
			"MANGROVE_LOCKET", "MANGROVE"
		);

		assertEquals(Map.of(), MuseumCatalog.childrenOf(upgradeTo, pieceToSet));
	}

	@Test
	public void coveredSlotsDoNotCountTowardValue() {
		InventoryDecoder.Stack hyperion = stack("HYPERION");

		assertEquals("HYPERION", MuseumCatalog.valueId("HYPERION", null, hyperion));
		assertNull(MuseumCatalog.valueId("NECRON_BLADE", "HYPERION", hyperion));
	}

	@Test
	public void specialSlotsPriceTheDonatedItem() {
		assertEquals("EASTER_EGG_2020", MuseumCatalog.valueId("SPECIAL:0", null, stack("EASTER_EGG_2020")));
		assertNull(MuseumCatalog.valueId("SPECIAL:1", null, null));
	}

	private static InventoryDecoder.Stack stack(String id) {
		return new InventoryDecoder.Stack(id, 1, null, List.of(), false);
	}
}
