package dev.vy.betterpv.client.networth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.client.data.InventorySnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class LoadoutDecoderTest {
	private static InventorySnapshot.Loadout loadout(String name, String power, Integer hotm, Integer hotf) {
		return new InventorySnapshot.Loadout(name, List.of(), List.of(), power, null, List.of(), null, "", hotm, hotf);
	}

	@Test
	public void keepsOneBasedKeysInOrderIncludingHighSlots() {
		Map<Integer, InventorySnapshot.Loadout> byIndex = new LinkedHashMap<>();
		byIndex.put(18, loadout("Eighteen", "bloody", null, null));
		byIndex.put(1, loadout("One", "silky", null, null));
		byIndex.put(7, loadout("Seven", "", null, null));
		byIndex.put(9, loadout("Nine", "sighted", null, null));

		List<InventorySnapshot.Loadout> out = LoadoutDecoder.padLoadouts(byIndex);

		assertEquals(List.of("One", "Nine", "Eighteen"), out.stream().map(InventorySnapshot.Loadout::name).toList());
	}

	@Test
	public void treePresetAloneCountsAsContent() {
		Map<Integer, InventorySnapshot.Loadout> byIndex = new LinkedHashMap<>();
		byIndex.put(3, loadout("Trees", "", null, 2));

		List<InventorySnapshot.Loadout> out = LoadoutDecoder.padLoadouts(byIndex);

		assertEquals(1, out.size());
		assertEquals(Integer.valueOf(2), out.get(0).hotfPreset());
	}

	@Test
	public void presetSlotAcceptsOneToFiveOnly() {
		JsonObject entry = JsonParser.parseString("""
			{"mining_core_selected_slot": 3, "foraging_core_selected_slot": 0, "bad": 6}
			""").getAsJsonObject();

		assertEquals(Integer.valueOf(3), LoadoutDecoder.presetSlot(entry, "mining_core_selected_slot"));
		assertNull(LoadoutDecoder.presetSlot(entry, "foraging_core_selected_slot"));
		assertNull(LoadoutDecoder.presetSlot(entry, "bad"));
		assertNull(LoadoutDecoder.presetSlot(entry, "missing"));
	}

	@Test
	public void namedLoadoutReadsPresetsAndOneBasedFallbackName() {
		JsonObject member = JsonParser.parseString("{}").getAsJsonObject();
		JsonObject loadoutJson = JsonParser.parseString("""
			{"loadouts": {"4": {"id": 4, "power_stone": "silky", "mining_core_selected_slot": 2, "foraging_core_selected_slot": 5}}}
			""").getAsJsonObject();

		List<InventorySnapshot.Loadout> out = LoadoutDecoder.parseNamedLoadouts(member, loadoutJson, null);

		assertEquals(1, out.size());
		assertEquals("Loadout 4", out.get(0).name());
		assertEquals(Integer.valueOf(2), out.get(0).hotmPreset());
		assertEquals(Integer.valueOf(5), out.get(0).hotfPreset());
	}
}
