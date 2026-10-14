package dev.vy.betterpv.client.networth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.Test;

public class MuseumSpecialTest {
	@Test
	public void specialDonationsAreKeyedByIndex() throws IOException {
		JsonObject member = new JsonObject();
		JsonObject items = new JsonObject();
		JsonObject hyperion = new JsonObject();
		hyperion.addProperty("donated_time", 1L);
		hyperion.add("items", data("HYPERION"));
		items.add("HYPERION", hyperion);
		member.add("items", items);

		JsonArray special = new JsonArray();
		JsonObject first = new JsonObject();
		first.addProperty("donated_time", 2L);
		first.add("items", data("EASTER_EGG_2020"));
		special.add(first);
		JsonObject second = new JsonObject();
		second.add("items", data("CAKE_SOUL", "NEW_YEAR_CAKE"));
		special.add(second);
		member.add("special", special);

		Map<String, InventoryDecoder.Stack> out = MuseumInventoryDecoder.parseMuseumById(member);

		assertEquals("HYPERION", out.get("HYPERION").id());
		assertEquals("EASTER_EGG_2020", out.get("SPECIAL:0").id());
		assertEquals("CAKE_SOUL", out.get("SPECIAL:1").id());
		assertEquals("NEW_YEAR_CAKE", out.get("SPECIAL:1:1").id());
		assertEquals(4, out.size());
	}

	@Test
	public void missingSpecialArrayIsFine() throws IOException {
		JsonObject member = new JsonObject();
		JsonObject items = new JsonObject();
		JsonObject entry = new JsonObject();
		entry.add("items", data("ASPECT_OF_THE_END"));
		items.add("ASPECT_OF_THE_END", entry);
		member.add("items", items);

		Map<String, InventoryDecoder.Stack> out = MuseumInventoryDecoder.parseMuseumById(member);

		assertEquals(1, out.size());
		assertTrue(out.containsKey("ASPECT_OF_THE_END"));
		assertFalse(out.keySet().stream().anyMatch(k -> k.startsWith("SPECIAL:")));
	}

	private static JsonObject data(String... ids) throws IOException {
		ListTag list = new ListTag();
		for (String id : ids) {
			CompoundTag attrs = new CompoundTag();
			attrs.putString("id", id);
			CompoundTag tag = new CompoundTag();
			tag.put("ExtraAttributes", attrs);
			CompoundTag slot = new CompoundTag();
			slot.putByte("Count", (byte) 1);
			slot.put("tag", tag);
			list.add(slot);
		}
		CompoundTag root = new CompoundTag();
		root.put("i", list);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, bytes);
		JsonObject out = new JsonObject();
		out.addProperty("type", 0);
		out.addProperty("data", Base64.getEncoder().encodeToString(bytes.toByteArray()));
		return out;
	}
}
