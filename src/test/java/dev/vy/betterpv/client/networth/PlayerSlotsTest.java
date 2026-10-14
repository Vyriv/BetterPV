package dev.vy.betterpv.client.networth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import dev.vy.betterpv.client.data.InventorySnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PlayerSlotsTest {
	private static InventorySnapshot.Slot slot(String id) {
		return new InventorySnapshot.Slot(id, 1, List.of(), id, null, null, null);
	}

	@Test
	public void riftLayoutMatchesMainInventory() {
		List<InventorySnapshot.Slot> inv = new ArrayList<>();
		for (int i = 0; i < 36; i++) {
			inv.add(i == 0 ? slot("HOTBAR_0") : i == 9 ? slot("MAIN_0") : null);
		}
		List<InventorySnapshot.Slot> armor = List.of(slot("BOOTS"), slot("LEGS"), slot("CHEST"), slot("HELMET"));
		List<InventorySnapshot.Slot> equip = List.of(slot("NECKLACE"), slot("CLOAK"), slot("BELT"), slot("GLOVES"));

		List<InventorySnapshot.Slot> out = InventoryDecoder.playerSlots(equip, armor, inv);

		assertEquals(44, out.size());
		assertEquals("NECKLACE", out.get(0).id());
		assertEquals("HELMET", out.get(4).id());
		assertEquals("BOOTS", out.get(7).id());
		assertEquals("HOTBAR_0", out.get(8).id());
		assertEquals("MAIN_0", out.get(17).id());
	}

	@Test
	public void missingPiecesStayEmpty() {
		List<InventorySnapshot.Slot> out = InventoryDecoder.playerSlots(List.of(), null, List.of(slot("A")));
		assertEquals(44, out.size());
		for (int i = 0; i < 8; i++) {
			assertNull(out.get(i));
		}
		assertEquals("A", out.get(8).id());
		assertNull(out.get(43));
	}
}
