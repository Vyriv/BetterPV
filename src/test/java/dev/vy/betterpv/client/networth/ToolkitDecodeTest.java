package dev.vy.betterpv.client.networth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.ToolkitFixture;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.Test;

public class ToolkitDecodeTest {
	private static CompoundTag attrs(String id) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		tag.putInt("levelable_lvl", 17);
		return tag;
	}

	@Test
	public void decodesBareAttributeRootsInCropSlotObjects() throws Exception {
		Map<String, CompoundTag> tools = new LinkedHashMap<>();
		tools.put("CACTUS", attrs("CACTUS_KNIFE_3"));
		tools.put("MELON", attrs("MELON_DICER_3"));
		List<InventoryDecoder.ToolkitItem> items = InventoryDecoder.parseToolkit(ToolkitFixture.toolkit(tools, Set.of("MELON")));

		assertEquals(2, items.size());
		InventoryDecoder.ToolkitItem cactus = items.get(0);
		assertEquals("CACTUS", cactus.category());
		assertEquals("CACTUS_KNIFE_3", cactus.stack().id());
		assertEquals(17, NbtAttrs.intValue(cactus.stack().extraAttributes(), "levelable_lvl", 0));
		assertTrue("toolkit items carry no lore", cactus.stack().lore().isEmpty());
		assertFalse(cactus.inUse());
		assertTrue(items.get(1).inUse());
	}

	@Test
	public void readsOlderArrayAndSingleObjectShapes() throws Exception {
		JsonObject toolkit = new JsonObject();
		JsonArray arr = new JsonArray();
		arr.add(ToolkitFixture.encode(attrs("THEORETICAL_HOE_WARTS_3")));
		toolkit.add("NETHER_WART", arr);
		JsonObject single = new JsonObject();
		single.addProperty("data", ToolkitFixture.encode(attrs("THEORETICAL_HOE_SUNFLOWER_3")));
		toolkit.add("SUNFLOWER", single);

		List<InventoryDecoder.ToolkitItem> items = InventoryDecoder.parseToolkit(toolkit);
		assertEquals("THEORETICAL_HOE_WARTS_3", items.get(0).stack().id());
		assertEquals("THEORETICAL_HOE_SUNFLOWER_3", items.get(1).stack().id());
	}

	@Test
	public void unreadableSlotsKeepTheirCategoryWithNoStack() {
		JsonObject toolkit = new JsonObject();
		JsonObject slots = new JsonObject();
		JsonObject data = new JsonObject();
		data.addProperty("data", "not base64 gzip");
		slots.add("0", data);
		toolkit.add("CACTUS", slots);
		List<InventoryDecoder.ToolkitItem> items = InventoryDecoder.parseToolkit(toolkit);
		assertEquals(1, items.size());
		assertNull(items.get(0).stack());
		assertTrue(InventoryDecoder.parseToolkit(null).isEmpty());
	}
}
