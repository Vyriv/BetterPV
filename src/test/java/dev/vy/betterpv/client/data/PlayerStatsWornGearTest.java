package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import org.junit.Test;

public class PlayerStatsWornGearTest {
	@Test
	public void savedEquipmentLoadoutsDoNotAddStats() throws IOException {
		double none = farmingFortune(member(false, false));
		double worn = farmingFortune(member(true, false));
		double withLoadouts = farmingFortune(member(true, true));

		assertEquals(5D, worn - none, 0.001D);
		assertEquals(worn, withLoadouts, 0.001D);
	}

	private static double farmingFortune(JsonObject member) {
		return PlayerStatsCalculator.fromMember(member).entries().stream()
			.filter(e -> e.id().equals("farming_fortune"))
			.findFirst().orElseThrow().value().orElse(0D);
	}

	private static JsonObject member(boolean wornEquipment, boolean savedLoadout) throws IOException {
		JsonObject inventory = new JsonObject();
		if (wornEquipment) {
			inventory.add("equipment_contents", data(encode("LOTUS_NECKLACE", "\u00a77Farming Fortune: \u00a76+5")));
		}
		JsonObject member = new JsonObject();
		member.add("inventory", inventory);
		if (savedLoadout) {
			JsonObject layout = new JsonObject();
			layout.add("EQUIPMENT_SLOT_1", data(encode("LOTUS_NECKLACE", "\u00a77Farming Fortune: \u00a76+20")));
			JsonObject equipment = new JsonObject();
			equipment.add("1", layout);
			JsonObject loadout = new JsonObject();
			loadout.add("equipment", equipment);
			member.add("loadout", loadout);
		}
		return member;
	}

	private static JsonObject data(String encoded) {
		JsonObject o = new JsonObject();
		o.addProperty("type", 0);
		o.addProperty("data", encoded);
		return o;
	}

	private static String encode(String id, String loreLine) throws IOException {
		CompoundTag extra = new CompoundTag();
		extra.putString("id", id);
		ListTag lore = new ListTag();
		lore.add(StringTag.valueOf(loreLine));
		CompoundTag display = new CompoundTag();
		display.put("Lore", lore);
		CompoundTag tag = new CompoundTag();
		tag.put("ExtraAttributes", extra);
		tag.put("display", display);
		CompoundTag slot = new CompoundTag();
		slot.putInt("Count", 1);
		slot.put("tag", tag);
		ListTag items = new ListTag();
		items.add(slot);
		CompoundTag root = new CompoundTag();
		root.put("i", items);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		return Base64.getEncoder().encodeToString(out.toByteArray());
	}
}
