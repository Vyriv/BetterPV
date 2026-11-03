package dev.vy.betterpv.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.BetterPV;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Hex lists behind armour colour classification, from {@code assets/betterpv/data/armor_colors.json}. */
public record ArmorColorData(
	Set<String> crystal,
	Set<String> fairy,
	Set<String> spook,
	Map<String, String> glitchedFamilies,
	Map<String, Map<String, String>> witherColors,
	long blackWitherAfterMs,
	Map<String, Set<String>> variable,
	List<String> unpredictable
) {
	private static final String RESOURCE = "assets/betterpv/data/armor_colors.json";
	private static volatile ArmorColorData bundled;

	public static ArmorColorData bundled() {
		ArmorColorData data = bundled;
		if (data != null) {
			return data;
		}
		synchronized (ArmorColorData.class) {
			if (bundled == null) {
				bundled = load();
			}
			return bundled;
		}
	}

	private static ArmorColorData load() {
		try (InputStream in = ArmorColorData.class.getClassLoader().getResourceAsStream(RESOURCE)) {
			if (in == null) {
				BetterPV.LOGGER.warn("Missing armor_colors.json");
				return parse(new JsonObject());
			}
			return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed loading armor_colors.json", exception);
			return parse(new JsonObject());
		}
	}

	public static ArmorColorData parse(JsonObject root) {
		JsonObject glitched = object(root, "glitched");
		Map<String, Map<String, String>> wither = new LinkedHashMap<>();
		JsonObject witherRoot = object(glitched, "wither");
		for (String piece : witherRoot.keySet()) {
			wither.put(piece.toUpperCase(Locale.ROOT), Map.copyOf(stringMap(object(witherRoot, piece))));
		}
		Map<String, Set<String>> variable = new LinkedHashMap<>();
		JsonObject variableRoot = object(root, "variable");
		for (String key : variableRoot.keySet()) {
			variable.put(key.toUpperCase(Locale.ROOT), hexSet(variableRoot.get(key)));
		}
		long blackAfter = glitched.has("black_wither_after_ms") ? glitched.get("black_wither_after_ms").getAsLong() : 0L;
		return new ArmorColorData(
			hexSet(root.get("crystal")), hexSet(root.get("fairy")), hexSet(root.get("spook")),
			Map.copyOf(stringMap(object(glitched, "family"))), Map.copyOf(wither), blackAfter,
			java.util.Collections.unmodifiableMap(variable), List.copyOf(strings(root.get("unpredictable")))
		);
	}

	private static JsonObject object(JsonObject parent, String key) {
		return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
	}

	private static Map<String, String> stringMap(JsonObject object) {
		Map<String, String> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (entry.getValue().isJsonPrimitive()) {
				out.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue().getAsString().toUpperCase(Locale.ROOT));
			}
		}
		return out;
	}

	private static Set<String> hexSet(JsonElement element) {
		Set<String> out = new LinkedHashSet<>();
		for (String value : strings(element)) {
			out.add(value.toUpperCase(Locale.ROOT));
		}
		return java.util.Collections.unmodifiableSet(out);
	}

	private static List<String> strings(JsonElement element) {
		List<String> out = new java.util.ArrayList<>();
		if (element instanceof JsonArray array) {
			for (JsonElement value : array) {
				if (value.isJsonPrimitive()) {
					out.add(value.getAsString());
				}
			}
		}
		return out;
	}
}
