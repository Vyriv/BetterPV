package dev.vy.betterpv.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every NEU recipe that names its inputs, indexed by the item it produces: crafting grids, Dwarven Forge
 * inputs, NPC shop costs (often kept on the NPC's own file) and trades. Drops and pet upgrades are skipped
 * because they have no fixed cost.
 */
public final class AccessoryUpgradeMethods {
	public enum Kind {
		CRAFTING, FORGE, NPC_SHOP, TRADE
	}

	/** Item inputs merged by id, plus any coins the NPC charges. */
	public record Method(Kind kind, Map<String, Integer> inputs, double coins) {
		public Method {
			inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
			coins = Math.max(0D, coins);
		}
	}

	static final String COINS = "SKYBLOCK_COIN";
	private static final String[] CELLS = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};

	private AccessoryUpgradeMethods() {
	}

	public static Map<String, List<Method>> index(Collection<JsonObject> items) {
		Map<String, List<Method>> out = new HashMap<>();
		if (items == null) {
			return out;
		}
		for (JsonObject item : items) {
			if (item == null) {
				continue;
			}
			String id = item.has("internalname") && item.get("internalname").isJsonPrimitive()
				? normalize(item.get("internalname").getAsString()) : null;
			if (item.has("recipe") && item.get("recipe").isJsonObject()) {
				add(out, outputOf(item.getAsJsonObject("recipe"), id), grid(item.getAsJsonObject("recipe")));
			}
			if (!item.has("recipes") || !item.get("recipes").isJsonArray()) {
				continue;
			}
			for (JsonElement element : item.getAsJsonArray("recipes")) {
				if (element.isJsonObject()) {
					JsonObject recipe = element.getAsJsonObject();
					add(out, outputOf(recipe, id), method(recipe));
				}
			}
		}
		return out;
	}

	/** A bare crafting grid ({@code A1}..{@code C3} to {@code ID:count}). */
	public static Method grid(JsonObject recipe) {
		if (recipe == null) {
			return null;
		}
		List<String> stacks = new ArrayList<>();
		for (String cell : CELLS) {
			JsonElement value = recipe.get(cell);
			if (value != null && value.isJsonPrimitive()) {
				stacks.add(value.getAsString());
			}
		}
		return build(Kind.CRAFTING, stacks);
	}

	static Method method(JsonObject recipe) {
		String type = string(recipe.get("type"));
		if (type == null || type.equalsIgnoreCase("crafting")) {
			return grid(recipe);
		}
		return switch (type.toLowerCase(Locale.ROOT)) {
			case "forge" -> build(Kind.FORGE, strings(recipe.get("inputs")));
			case "npc_shop" -> build(Kind.NPC_SHOP, strings(recipe.get("cost")));
			case "trade" -> build(Kind.TRADE, strings(recipe.get("cost")));
			default -> null;
		};
	}

	private static Method build(Kind kind, List<String> stacks) {
		Map<String, Integer> inputs = new LinkedHashMap<>();
		double coins = 0D;
		for (String raw : stacks) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String stack = raw.trim();
			int colon = stack.lastIndexOf(':');
			String id = stack;
			double qty = 1D;
			if (colon > 0) {
				try {
					qty = Double.parseDouble(stack.substring(colon + 1).trim());
					id = stack.substring(0, colon);
				} catch (NumberFormatException ignored) {
					qty = 1D;
				}
			}
			id = normalize(id);
			if (id.equals(COINS)) {
				coins += qty;
			} else if (qty > 0D) {
				inputs.merge(id, Math.max(1, (int) Math.round(qty)), Integer::sum);
			}
		}
		return inputs.isEmpty() && coins <= 0D ? null : new Method(kind, inputs, coins);
	}

	private static String outputOf(JsonObject recipe, String fallback) {
		String output = string(recipe.get("overrideOutputId"));
		if (output == null) {
			output = string(recipe.get("result"));
		}
		if (output == null) {
			return fallback;
		}
		int colon = output.lastIndexOf(':');
		if (colon > 0) {
			try {
				Double.parseDouble(output.substring(colon + 1));
				output = output.substring(0, colon);
			} catch (NumberFormatException ignored) {
				// Part of the id.
			}
		}
		return normalize(output);
	}

	private static void add(Map<String, List<Method>> out, String output, Method method) {
		if (output != null && !output.isBlank() && method != null) {
			out.computeIfAbsent(output, key -> new ArrayList<>()).add(method);
		}
	}

	private static List<String> strings(JsonElement element) {
		List<String> out = new ArrayList<>();
		if (element instanceof JsonArray array) {
			for (JsonElement value : array) {
				if (value.isJsonPrimitive()) {
					out.add(value.getAsString());
				}
			}
		} else if (element != null && element.isJsonPrimitive()) {
			out.add(element.getAsString());
		}
		return out;
	}

	private static String string(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			return null;
		}
		String value = element.getAsString().trim();
		return value.isEmpty() ? null : value;
	}

	private static String normalize(String id) {
		return id.trim().toUpperCase(Locale.ROOT).replace('-', ':');
	}
}
