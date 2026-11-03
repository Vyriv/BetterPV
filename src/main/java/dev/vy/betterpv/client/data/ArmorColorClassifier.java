package dev.vy.betterpv.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;

/**
 * Classifies a leather armour piece's colour from decoded profile NBT. The shown colour is
 * {@code display.color}; {@code ExtraAttributes.color} only adds information (the Seymour roll, or the base
 * colour kept under some Hypixel dyes). When the origin is uncertain the result carries no label.
 */
public final class ArmorColorClassifier {
	public enum Kind {
		ORIGINAL, EXOTIC, CRYSTAL, FAIRY, SPOOK, GLITCHED, DYED, UNLABELLED
	}

	/** Decoded slot fields; {@code eaColor} is the raw {@code "R:G:B"} string. */
	public record Input(String id, Integer displayColor, String eaColor, String dyeItem, long timestampMs) {
	}

	/**
	 * {@code baseRgb}/{@code baseKind} describe the colour under a Hypixel dye when NBT kept it.
	 * {@code seymour} is set for Seymour pieces, analysed on the undyed colour.
	 */
	public record Result(
		int rgb, Kind kind, String dyeName, Integer baseRgb, Kind baseKind, SeymourColors.Match seymour,
		List<SeymourColors.Pattern> patterns
	) {
	}

	public interface Lookups {
		/** Hypixel's default colour for an item id, or null when it has none. */
		Integer originalColor(String id);

		/** Readable dye name ("Holly") for a {@code dye_item} id. */
		String dyeName(String dyeId);

		Collection<SeymourColors.Target> seymourTargets();
	}

	private static final String[] PIECE_WORDS = {
		" Helmet", " Chestplate", " Leggings", " Boots", " Hat", " Jacket", " Trousers", " Shoes", " Hood", " Cap",
		" Tunic", " Pants", " Sneakers"
	};
	/** Minecraft 1.8 leather armour dye colours (vanilla game data). */
	private static final Map<String, Integer> VANILLA_DYES = vanillaDyes();
	private static final Map<String, Integer> PURE_COLORS = pureColors();

	private ArmorColorClassifier() {
	}

	public static Result classify(Input input, ArmorColorData data, Lookups lookups) {
		if (input == null || input.displayColor() == null || input.id() == null || input.id().isBlank()) {
			return null;
		}
		String id = input.id().toUpperCase(Locale.ROOT);
		int shown = input.displayColor() & 0xFFFFFF;
		Integer stored = parseRgb(input.eaColor());
		boolean dyed = input.dyeItem() != null && !input.dyeItem().isBlank();
		boolean seymour = SeymourColors.isSeymour(id);

		Kind kind;
		String dyeName = null;
		Integer base = null;
		Kind baseKind = null;
		if (dyed) {
			kind = Kind.DYED;
			dyeName = lookups.dyeName(input.dyeItem());
			if (stored != null && stored != shown) {
				base = stored;
				baseKind = seymour ? null : undyed(id, stored, input.timestampMs(), data, lookups);
			}
		} else {
			kind = seymour ? Kind.UNLABELLED : undyed(id, shown, input.timestampMs(), data, lookups);
		}

		SeymourColors.Match match = null;
		List<SeymourColors.Pattern> patterns = List.of();
		Integer seymourRgb = dyed ? base : Integer.valueOf(shown);
		if (seymour && seymourRgb != null) {
			match = SeymourColors.closest(seymourRgb, SeymourColors.pieceOf(id), lookups.seymourTargets());
			patterns = SeymourColors.patterns(seymourRgb);
		}
		return new Result(shown, kind, dyeName, base, baseKind, match, patterns);
	}

	/** Undyed colour against Hypixel's default, following the community's exotic rules. */
	static Kind undyed(String id, int rgb, long timestampMs, ArmorColorData data, Lookups lookups) {
		for (String prefix : data.unpredictable()) {
			if (id.startsWith(prefix)) {
				return Kind.UNLABELLED;
			}
		}
		Integer original = lookups.originalColor(id);
		if (original == null) {
			return Kind.UNLABELLED;
		}
		if ((original & 0xFFFFFF) == rgb) {
			return Kind.ORIGINAL;
		}
		String hex = hex(rgb);
		if (rolledVariant(id, hex, data)) {
			return Kind.ORIGINAL;
		}
		if (data.crystal().contains(hex)) {
			return Kind.CRYSTAL;
		}
		if (data.fairy().contains(hex)) {
			return Kind.FAIRY;
		}
		if (data.spook().contains(hex) && id.startsWith("FAIRY_")) {
			return Kind.SPOOK;
		}
		if (id.contains("WITHER") && hex.equals("000000") && (timestampMs <= 0L || timestampMs < data.blackWitherAfterMs())) {
			return Kind.UNLABELLED;
		}
		if (glitched(id, hex, data)) {
			return Kind.GLITCHED;
		}
		return Kind.EXOTIC;
	}

	/** Items whose standard colour legitimately varies (random Fairy/Crystal rolls, class or mode colours). */
	private static boolean rolledVariant(String id, String hex, ArmorColorData data) {
		if (id.contains("FAIRY")) {
			return data.fairy().contains(hex);
		}
		if (id.contains("CRYSTAL")) {
			return data.crystal().contains(hex);
		}
		if (id.contains("SPOOK")) {
			return data.spook().contains(hex);
		}
		String key = variableKey(id, data);
		return key != null && data.variable().get(key).contains(hex);
	}

	private static boolean glitched(String id, String hex, ArmorColorData data) {
		if (id.contains("WITHER")) {
			for (Map.Entry<String, Map<String, String>> piece : data.witherColors().entrySet()) {
				if (!id.contains(piece.getKey())) {
					continue;
				}
				Map<String, String> colors = piece.getValue();
				String owner = colors.get(hex);
				return owner != null && colors.containsValue(id) && !owner.equals(id);
			}
			return false;
		}
		String family = data.glitchedFamilies().get(hex);
		return family != null && id.startsWith(family);
	}

	/**
	 * Seymour reference colours from data BetterPV may use: pure colours, Minecraft's vanilla dye colours,
	 * Hypixel dyes (NEU lore), the Fairy/Crystal/Spook lists, Hypixel's default armour colours and the class or
	 * mode colours of Adaptive, Rancher's and Reaper armour. Armour colours keep the slots Hypixel lists for
	 * them. The first source to name a hex keeps it.
	 *
	 * @param modernDyes current Minecraft dye colours by readable name ("Light Blue"), read from the game
	 */
	public static List<SeymourColors.Target> buildTargets(
		Collection<JsonObject> hypixelItems, Collection<JsonObject> neuDyes, Map<String, Integer> modernDyes,
		ArmorColorData data
	) {
		Map<Integer, SeymourColors.Target> byRgb = new LinkedHashMap<>();
		PURE_COLORS.forEach((name, rgb) -> put(byRgb, name, rgb, Set.of(), false));
		VANILLA_DYES.forEach((name, rgb) -> put(byRgb, "Vanilla " + name + " Dye", rgb, Set.of(), false));
		if (modernDyes != null) {
			modernDyes.forEach((name, rgb) -> put(byRgb, "Modern " + name + " Dye", rgb, Set.of(), false));
		}
		if (neuDyes != null) {
			for (JsonObject dye : neuDyes) {
				addDye(byRgb, dye);
			}
		}
		for (String hex : data.fairy()) {
			put(byRgb, "Fairy", Integer.parseInt(hex, 16), Set.of(), false);
		}
		for (String hex : data.crystal()) {
			put(byRgb, "Crystal", Integer.parseInt(hex, 16), Set.of(), false);
		}
		for (String hex : data.spook()) {
			put(byRgb, "Spook", Integer.parseInt(hex, 16), Set.of(), false);
		}
		Map<Integer, List<NamedPiece>> originals = new LinkedHashMap<>();
		Map<String, List<NamedPiece>> variables = new LinkedHashMap<>();
		if (hypixelItems != null) {
			for (JsonObject item : hypixelItems) {
				Integer rgb = parseCommaRgb(string(item, "color"));
				String name = string(item, "name");
				String id = string(item, "id");
				if (rgb == null || name == null || id == null || SeymourColors.isSeymour(id) || id.startsWith("LEATHER_")) {
					continue;
				}
				NamedPiece named = NamedPiece.of(name, piece(string(item, "category")));
				originals.computeIfAbsent(rgb, key -> new ArrayList<>()).add(named);
				String variable = variableKey(id.toUpperCase(Locale.ROOT), data);
				if (variable != null) {
					variables.computeIfAbsent(variable, key -> new ArrayList<>()).add(named);
				}
			}
		}
		for (Map.Entry<Integer, List<NamedPiece>> entry : originals.entrySet()) {
			put(byRgb, label(entry.getValue()), entry.getKey(), pieces(entry.getValue()), false);
		}
		for (Map.Entry<String, Set<String>> entry : data.variable().entrySet()) {
			List<NamedPiece> named = variables.get(entry.getKey());
			String name = named == null ? prettyDyeId(entry.getKey()) : label(named);
			Set<SeymourColors.Piece> pieces = named == null ? Set.of() : pieces(named);
			for (String hex : entry.getValue()) {
				put(byRgb, name, Integer.parseInt(hex, 16), pieces, false);
			}
		}
		return List.copyOf(byRgb.values());
	}

	/** An armour item name split into its set name and piece word ("Storm's" and "Leggings"). */
	private record NamedPiece(String full, String base, String word, SeymourColors.Piece piece) {
		static NamedPiece of(String name, SeymourColors.Piece piece) {
			String clean = name.replaceAll("\u00a7.", "").trim();
			for (String word : PIECE_WORDS) {
				if (clean.endsWith(word)) {
					return new NamedPiece(clean, clean.substring(0, clean.length() - word.length()).trim(), word.trim(), piece);
				}
			}
			return new NamedPiece(clean, clean, "", piece);
		}
	}

	/**
	 * "Storm's Leggings" for one item, "Superior Dragon Chestplate+Leggings" for pieces of one set,
	 * "Holy Dragon 3p" for a chestplate, leggings and boots set, and "First (+N)" when several sets share a hex.
	 */
	private static String label(List<NamedPiece> named) {
		Map<String, List<NamedPiece>> byBase = new TreeMap<>();
		for (NamedPiece piece : named) {
			byBase.computeIfAbsent(piece.base(), key -> new ArrayList<>()).add(piece);
		}
		Map.Entry<String, List<NamedPiece>> first = byBase.entrySet().iterator().next();
		String label = setLabel(first.getKey(), first.getValue());
		return byBase.size() == 1 ? label : label + " (+" + (byBase.size() - 1) + ")";
	}

	private static String setLabel(String base, List<NamedPiece> pieces) {
		if (pieces.size() == 1) {
			return pieces.get(0).full();
		}
		if (pieces(pieces).equals(EnumSet.of(SeymourColors.Piece.CHESTPLATE, SeymourColors.Piece.LEGGINGS, SeymourColors.Piece.BOOTS))) {
			return base + " 3p";
		}
		List<NamedPiece> ordered = new ArrayList<>(pieces);
		ordered.sort(java.util.Comparator.comparingInt(piece -> piece.piece() == null ? Integer.MAX_VALUE : piece.piece().ordinal()));
		java.util.LinkedHashSet<String> words = new java.util.LinkedHashSet<>();
		for (NamedPiece piece : ordered) {
			if (!piece.word().isEmpty()) {
				words.add(piece.word());
			}
		}
		return words.isEmpty() ? base : base + " " + String.join("+", words);
	}

	private static Set<SeymourColors.Piece> pieces(List<NamedPiece> named) {
		Set<SeymourColors.Piece> out = EnumSet.noneOf(SeymourColors.Piece.class);
		for (NamedPiece piece : named) {
			if (piece.piece() != null) {
				out.add(piece.piece());
			}
		}
		return out;
	}

	/** First {@code variable} key the id contains, matching how legitimate colour variants are recognised. */
	private static String variableKey(String id, ArmorColorData data) {
		for (String key : data.variable().keySet()) {
			if (id.contains(key)) {
				return key;
			}
		}
		return null;
	}

	private static void addDye(Map<Integer, SeymourColors.Target> byRgb, JsonObject dye) {
		String name = dyeDisplayName(dye);
		StringBuilder lore = new StringBuilder();
		if (dye.has("lore") && dye.get("lore").isJsonArray()) {
			for (JsonElement line : dye.getAsJsonArray("lore")) {
				lore.append(' ').append(line.getAsString().replaceAll("\u00a7.", ""));
			}
		}
		List<Integer> hexes = new ArrayList<>();
		Matcher matcher = java.util.regex.Pattern.compile("#([0-9A-Fa-f]{6})").matcher(lore);
		while (matcher.find()) {
			hexes.add(Integer.parseInt(matcher.group(1), 16));
		}
		if (name == null || hexes.isEmpty()) {
			return;
		}
		boolean fade = lore.toString().contains("Animates");
		if (!fade) {
			put(byRgb, name + " Dye", hexes.get(0), Set.of(), false);
			return;
		}
		put(byRgb, name + " Dye (first frame)", hexes.get(0), Set.of(), true);
		if (hexes.size() > 1) {
			put(byRgb, name + " Dye (last frame)", hexes.get(hexes.size() - 1), Set.of(), true);
		}
	}

	/** "Holly" from an NEU dye item ({@code §6Holly Dye}). */
	public static String dyeDisplayName(JsonObject dye) {
		String raw = dye == null ? null : string(dye, "displayname");
		if (raw == null) {
			return null;
		}
		String name = raw.replaceAll("\u00a7.", "").trim();
		return name.endsWith(" Dye") ? name.substring(0, name.length() - 4).trim() : name;
	}

	/** "Holly" from {@code DYE_HOLLY} when NEU data is unavailable. */
	public static String prettyDyeId(String dyeId) {
		if (dyeId == null || dyeId.isBlank()) {
			return null;
		}
		String body = dyeId.toUpperCase(Locale.ROOT).startsWith("DYE_") ? dyeId.substring(4) : dyeId;
		StringBuilder out = new StringBuilder();
		for (String word : body.toLowerCase(Locale.ROOT).split("_")) {
			if (!word.isEmpty()) {
				out.append(out.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return out.toString();
	}

	private static void put(Map<Integer, SeymourColors.Target> byRgb, String name, int rgb, Set<SeymourColors.Piece> pieces, boolean fade) {
		byRgb.putIfAbsent(rgb & 0xFFFFFF, new SeymourColors.Target(name, rgb, pieces, fade));
	}

	private static SeymourColors.Piece piece(String category) {
		if (category == null) {
			return null;
		}
		try {
			return SeymourColors.Piece.valueOf(category.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public static String hex(int rgb) {
		return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
	}

	/** {@code "R:G:B"} from ExtraAttributes; null when malformed. */
	public static Integer parseRgb(String value) {
		return parseChannels(value, ":");
	}

	/** {@code "R,G,B"} from the Hypixel items API; null when absent or malformed. */
	public static Integer parseCommaRgb(String value) {
		return parseChannels(value, ",");
	}

	private static Integer parseChannels(String value, String separator) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String[] parts = value.trim().split(separator);
		if (parts.length != 3) {
			return null;
		}
		int rgb = 0;
		for (String part : parts) {
			try {
				int channel = Integer.parseInt(part.trim());
				if (channel < 0 || channel > 255) {
					return null;
				}
				rgb = (rgb << 8) | channel;
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return rgb;
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		if (value == null || !value.isJsonPrimitive()) {
			return null;
		}
		String text = value.getAsString().trim();
		return text.isEmpty() ? null : text;
	}

	private static Map<String, Integer> pureColors() {
		Map<String, Integer> out = new LinkedHashMap<>();
		out.put("Pure Red", 0xFF0000);
		out.put("Pure Green", 0x00FF00);
		out.put("Pure Blue", 0x0000FF);
		out.put("Pure Yellow", 0xFFFF00);
		out.put("Pure Cyan", 0x00FFFF);
		out.put("Pure Magenta", 0xFF00FF);
		out.put("Pure White", 0xFFFFFF);
		out.put("Pure Black", 0x000000);
		return out;
	}

	private static Map<String, Integer> vanillaDyes() {
		Map<String, Integer> out = new LinkedHashMap<>();
		out.put("White", 0xFFFFFF);
		out.put("Orange", 0xD87F33);
		out.put("Magenta", 0xB24CD8);
		out.put("Light Blue", 0x6699D8);
		out.put("Yellow", 0xE5E533);
		out.put("Lime", 0x7FCC19);
		out.put("Pink", 0xF27FA5);
		out.put("Gray", 0x4C4C4C);
		out.put("Light Gray", 0x999999);
		out.put("Cyan", 0x4C7F99);
		out.put("Purple", 0x7F3FB2);
		out.put("Blue", 0x334CB2);
		out.put("Brown", 0x664C33);
		out.put("Green", 0x667F33);
		out.put("Red", 0x993333);
		out.put("Black", 0x191919);
		return out;
	}

	/** Lookups backed by fixed maps, for tests and callers with preloaded data. */
	public static Lookups lookups(
		Function<String, Integer> originals, Function<String, String> dyeNames, Collection<SeymourColors.Target> targets
	) {
		return new Lookups() {
			@Override
			public Integer originalColor(String id) {
				return originals.apply(id);
			}

			@Override
			public String dyeName(String dyeId) {
				String name = dyeNames.apply(dyeId);
				return name != null ? name : prettyDyeId(dyeId);
			}

			@Override
			public Collection<SeymourColors.Target> seymourTargets() {
				return targets;
			}
		};
	}
}
