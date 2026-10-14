package dev.vy.betterpv.client.networth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.Test;

public class WardrobePresetsTest {
	@Test
	public void readsLoadoutSetsHelmetFirstWithLiveArmourForEquippedSet() throws IOException {
		JsonObject inventory = new JsonObject();
		inventory.add("inv_contents", data(encode("ASPECT_OF_THE_END")));
		inventory.add("inv_armor", data(encode("BOOTS_X", "LEGS_X", "CHEST_X", "HELM_X")));
		JsonObject set1 = new JsonObject();
		set1.addProperty("id", 1);
		set1.add("HELMET", data(encode("CROWN_OF_AVARICE")));
		set1.add("CHESTPLATE", data(encode("INFERNAL_TERROR_CHESTPLATE")));
		JsonObject set2 = new JsonObject();
		set2.addProperty("id", 2);
		JsonObject armor = new JsonObject();
		armor.addProperty("equipped_set", 2);
		armor.add("2", set2);
		armor.add("1", set1);
		JsonObject loadout = new JsonObject();
		loadout.add("armor", armor);
		JsonObject member = new JsonObject();
		member.add("inventory", inventory);
		member.add("loadout", loadout);

		assertTrue(InventoryDecoder.hasInventoryApi(member));
		List<InventoryDecoder.WardrobePreset> presets = InventoryDecoder.wardrobePresets(member);
		assertEquals(2, presets.size());
		InventoryDecoder.WardrobePreset first = presets.get(0);
		assertEquals(1, first.slot());
		assertFalse(first.equipped());
		assertEquals("CROWN_OF_AVARICE", first.pieces().get(0).id());
		assertEquals("INFERNAL_TERROR_CHESTPLATE", first.pieces().get(1).id());
		assertNull(first.pieces().get(2));
		InventoryDecoder.WardrobePreset equipped = presets.get(1);
		assertTrue(equipped.equipped());
		assertEquals("HELM_X", equipped.pieces().get(0).id());
		assertEquals("BOOTS_X", equipped.pieces().get(3).id());
	}

	@Test
	public void missingInventoryApiAndLegacyWardrobeGiveNothing() throws IOException {
		JsonObject inventory = new JsonObject();
		inventory.add("wardrobe_contents", data(encode("CROWN_OF_AVARICE")));
		JsonObject member = new JsonObject();
		member.add("inventory", inventory);
		assertFalse(InventoryDecoder.hasInventoryApi(member));
		assertTrue(InventoryDecoder.wardrobePresets(member).isEmpty());
	}

	private static JsonObject data(String encoded) {
		JsonObject o = new JsonObject();
		o.addProperty("type", 0);
		o.addProperty("data", encoded);
		return o;
	}

	private static String encode(String... ids) throws IOException {
		ListTag items = new ListTag();
		for (String id : ids) {
			CompoundTag extra = new CompoundTag();
			extra.putString("id", id);
			CompoundTag tag = new CompoundTag();
			tag.put("ExtraAttributes", extra);
			CompoundTag slot = new CompoundTag();
			slot.putInt("Count", 1);
			slot.put("tag", tag);
			items.add(slot);
		}
		CompoundTag root = new CompoundTag();
		root.put("i", items);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		return Base64.getEncoder().encodeToString(out.toByteArray());
	}
}
