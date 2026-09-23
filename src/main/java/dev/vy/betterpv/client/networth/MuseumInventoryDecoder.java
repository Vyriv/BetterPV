package dev.vy.betterpv.client.networth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Museum donation inventory decoding. */
final class MuseumInventoryDecoder {
	private MuseumInventoryDecoder() {
	}

	static List<InventoryDecoder.Stack> parseMuseum(JsonObject museumMember) {
		if (museumMember == null) {
			return List.of();
		}
		List<InventoryDecoder.Stack> out = new ArrayList<>();
		JsonObject items = InventoryDecodeSupport.obj(museumMember.get("items"));
		if (items != null) {
			for (var entry : items.entrySet()) {
				JsonObject value = InventoryDecodeSupport.obj(entry.getValue());
				if (value == null) {
					continue;
				}
				if (value.has("borrowing") && value.get("borrowing").getAsBoolean()) {
					continue;
				}
				JsonObject nested = InventoryDecodeSupport.obj(value.get("items"));
				if (nested != null && nested.has("data")) {
					out.addAll(InventoryDecodeCache.decodeDataElement(nested));
				} else if (value.has("items") && value.get("items").isJsonArray()) {
					// already-decoded style - skip
				}
			}
		}
		JsonArray special = museumMember.has("special") && museumMember.get("special").isJsonArray()
			? museumMember.getAsJsonArray("special")
			: null;
		if (special != null) {
			for (JsonElement element : special) {
				JsonObject value = InventoryDecodeSupport.obj(element);
				if (value == null) {
					continue;
				}
				JsonObject nested = InventoryDecodeSupport.obj(value.get("items"));
				if (nested != null) {
					out.addAll(InventoryDecodeCache.decodeDataElement(nested));
				}
			}
		}
		return out;
	}

	static Map<String, InventoryDecoder.Stack> parseMuseumById(JsonObject museumMember) {
		Map<String, InventoryDecoder.Stack> out = new LinkedHashMap<>();
		if (museumMember == null) {
			return out;
		}
		JsonObject items = InventoryDecodeSupport.obj(museumMember.get("items"));
		if (items != null) {
			for (var entry : items.entrySet()) {
				JsonObject value = InventoryDecodeSupport.obj(entry.getValue());
				if (value == null) {
					continue;
				}
				JsonObject nested = InventoryDecodeSupport.obj(value.get("items"));
				if (nested != null && nested.has("data")) {
					List<InventoryDecoder.Stack> decoded = InventoryDecodeCache.decodeDataElement(nested);
					if (!decoded.isEmpty()) {
						out.put(entry.getKey().toUpperCase(Locale.ROOT), decoded.get(0));
					}
				}
			}
		}
		return out;
	}
}
