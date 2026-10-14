package dev.vy.betterpv.client.data;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Builds {@code garden_player_data.farming_toolkit} the way Hypixel sends it. */
public final class ToolkitFixture {
	private ToolkitFixture() {
	}

	public static JsonObject toolkit(Map<String, CompoundTag> tools, Set<String> inUse) throws IOException {
		JsonObject toolkit = new JsonObject();
		toolkit.addProperty("IS_UNLOCKED", true);
		JsonObject used = new JsonObject();
		for (var e : tools.entrySet()) {
			JsonObject flag = new JsonObject();
			flag.addProperty("0", inUse.contains(e.getKey()));
			used.add(e.getKey(), flag);
			JsonObject data = new JsonObject();
			data.addProperty("data", encode(e.getValue()));
			JsonObject slots = new JsonObject();
			slots.add("0", data);
			toolkit.add(e.getKey(), slots);
		}
		toolkit.add("IN_USE", used);
		return toolkit;
	}

	public static String encode(CompoundTag root) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		return Base64.getEncoder().encodeToString(out.toByteArray());
	}
}
