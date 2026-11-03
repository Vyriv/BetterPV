package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.client.data.ArmorColorClassifier.Input;
import dev.vy.betterpv.client.data.ArmorColorClassifier.Kind;
import dev.vy.betterpv.client.data.ArmorColorClassifier.Result;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class ArmorColorClassifierTest {
	private static final ArmorColorData DATA = ArmorColorData.bundled();
	private static final long AFTER_WITHER_RECOLOR = 1_700_000_000_000L;
	private static final long BEFORE_WITHER_RECOLOR = 1_600_000_000_000L;

	/** Hypixel default colours used as fixtures (Superior is the real value). */
	private static final Map<String, Integer> ORIGINALS = Map.ofEntries(
		Map.entry("SUPERIOR_DRAGON_CHESTPLATE", 0xF2DF11),
		Map.entry("POWER_WITHER_CHESTPLATE", 0xE7413C),
		Map.entry("SPEED_WITHER_CHESTPLATE", 0x4A14B7),
		Map.entry("SHARK_SCALE_CHESTPLATE", 0x002CA6),
		Map.entry("FROZEN_BLAZE_BOOTS", 0xA0DAEF),
		Map.entry("RANCHERS_BOOTS", 0xCC5500),
		Map.entry("ADAPTIVE_CHESTPLATE", 0x3ABE78),
		Map.entry("ADAPTIVE_BOOTS", 0x169F57),
		Map.entry("REAPER_CHESTPLATE", 0x1B1B1B),
		Map.entry("CRYSTAL_CHESTPLATE", 0x1F0030),
		Map.entry("FAIRY_HELMET", 0x660033),
		Map.entry("FAIRY_CHESTPLATE", 0x660033),
		Map.entry("GHOST_BOOTS", 0xFFFFFF)
	);

	private static final List<SeymourColors.Target> TARGETS = List.of(
		new SeymourColors.Target("Holly Dye", 0x3C6746, Set.of(), false),
		new SeymourColors.Target("Pure Red", 0xFF0000, Set.of(), false),
		new SeymourColors.Target("Rabbit", 0x123456, Set.of(SeymourColors.Piece.BOOTS), false)
	);

	private static final ArmorColorClassifier.Lookups LOOKUPS = ArmorColorClassifier.lookups(
		ORIGINALS::get, id -> id.equals("DYE_HOLLY") ? "Holly" : null, TARGETS
	);

	private static Result classify(String id, int color) {
		return classify(id, color, null, null, AFTER_WITHER_RECOLOR);
	}

	private static Result classify(String id, int color, String eaColor, String dye, long timestamp) {
		return ArmorColorClassifier.classify(new Input(id, color, eaColor, dye, timestamp), DATA, LOOKUPS);
	}

	private static Kind kind(String id, int color) {
		return classify(id, color).kind();
	}

	@Test
	public void bundledListsLoad() {
		assertEquals(16, DATA.crystal().size());
		assertEquals(24, DATA.fairy().size());
		assertEquals(25, DATA.spook().size());
		assertEquals(1_605_830_400_000L, DATA.blackWitherAfterMs());
		assertEquals(3, DATA.witherColors().size());
	}

	@Test
	public void defaultColourIsOriginal() {
		assertEquals(Kind.ORIGINAL, kind("SUPERIOR_DRAGON_CHESTPLATE", 0xF2DF11));
	}

	@Test
	public void crystalAndFairyHexesOnOtherArmour() {
		assertEquals(Kind.CRYSTAL, kind("SUPERIOR_DRAGON_CHESTPLATE", 0x1F0030));
		assertEquals(Kind.FAIRY, kind("SUPERIOR_DRAGON_CHESTPLATE", 0xFF66B2));
	}

	@Test
	public void spookOnlyOnFairyPieces() {
		assertEquals(Kind.SPOOK, kind("FAIRY_CHESTPLATE", 0x9E00B2));
		assertEquals(Kind.EXOTIC, kind("SUPERIOR_DRAGON_CHESTPLATE", 0x9E00B2));
	}

	@Test
	public void otherColoursAreExotic() {
		assertEquals(Kind.EXOTIC, kind("SUPERIOR_DRAGON_CHESTPLATE", 0x123456));
	}

	@Test
	public void witherGlitchedColours() {
		assertEquals(Kind.GLITCHED, kind("SPEED_WITHER_CHESTPLATE", 0xE7413C));
		assertEquals(Kind.ORIGINAL, kind("POWER_WITHER_CHESTPLATE", 0xE7413C));
		assertEquals(Kind.EXOTIC, kind("SPEED_WITHER_CHESTPLATE", 0x123456));
		assertEquals(Kind.GLITCHED, classify("SPEED_WITHER_CHESTPLATE", 0x000000, null, null, AFTER_WITHER_RECOLOR).kind());
	}

	@Test
	public void blackWitherBeforeRecolorOrUnknownAgeStaysUnlabelled() {
		assertEquals(Kind.UNLABELLED, classify("SPEED_WITHER_CHESTPLATE", 0x000000, null, null, BEFORE_WITHER_RECOLOR).kind());
		assertEquals(Kind.UNLABELLED, classify("SPEED_WITHER_CHESTPLATE", 0x000000, null, null, 0L).kind());
	}

	@Test
	public void familyGlitchedColours() {
		assertEquals(Kind.GLITCHED, kind("SHARK_SCALE_CHESTPLATE", 0xFFDC51));
		assertEquals(Kind.EXOTIC, kind("FROZEN_BLAZE_BOOTS", 0xFFDC51));
	}

	@Test
	public void legitimateColourVariantsAreOriginal() {
		assertEquals(Kind.ORIGINAL, kind("RANCHERS_BOOTS", 0x000000));
		assertEquals(Kind.ORIGINAL, kind("ADAPTIVE_CHESTPLATE", 0xFF4242));
		assertEquals(Kind.ORIGINAL, kind("ADAPTIVE_BOOTS", 0xBB0000));
		assertEquals(Kind.ORIGINAL, kind("REAPER_CHESTPLATE", 0xFF0000));
		assertEquals(Kind.ORIGINAL, kind("CRYSTAL_CHESTPLATE", 0xFCF3FF));
		assertEquals(Kind.ORIGINAL, kind("FAIRY_HELMET", 0xFF99FF));
	}

	@Test
	public void variantListsArePieceSpecific() {
		assertEquals(Kind.EXOTIC, kind("ADAPTIVE_CHESTPLATE", 0x6E00A0));
		assertEquals(Kind.EXOTIC, kind("ADAPTIVE_BOOTS", 0xD579FF));
	}

	@Test
	public void unpredictableItemsAndUnknownDefaultsShowHexOnly() {
		assertEquals(Kind.UNLABELLED, kind("LEATHER_CHESTPLATE", 0x123456));
		assertEquals(Kind.UNLABELLED, kind("GHOST_BOOTS", 0x0B0B0B));
		Result unknown = classify("SOME_UNLISTED_BOOTS", 0x123456);
		assertEquals(Kind.UNLABELLED, unknown.kind());
		assertEquals(0x123456, unknown.rgb());
	}

	@Test
	public void dyedWithoutStoredBase() {
		Result result = classify("SUPERIOR_DRAGON_CHESTPLATE", 0x3C6746, null, "DYE_HOLLY", AFTER_WITHER_RECOLOR);
		assertEquals(Kind.DYED, result.kind());
		assertEquals("Holly", result.dyeName());
		assertNull(result.baseRgb());
		assertNull(result.baseKind());
	}

	@Test
	public void dyedWithStoredBaseClassifiesTheBase() {
		Result result = classify("SUPERIOR_DRAGON_CHESTPLATE", 0x3C6746, "242:223:17", "DYE_HOLLY", AFTER_WITHER_RECOLOR);
		assertEquals(Kind.DYED, result.kind());
		assertEquals(Integer.valueOf(0xF2DF11), result.baseRgb());
		assertEquals(Kind.ORIGINAL, result.baseKind());

		Result fairyBase = classify("SUPERIOR_DRAGON_CHESTPLATE", 0x3C6746, "255:102:178", "DYE_HOLLY", AFTER_WITHER_RECOLOR);
		assertEquals(Kind.FAIRY, fairyBase.baseKind());
	}

	@Test
	public void unknownDyeFallsBackToReadableId() {
		Result result = classify("SUPERIOR_DRAGON_CHESTPLATE", 0xFFF700, null, "DYE_PURE_YELLOW", AFTER_WITHER_RECOLOR);
		assertEquals("Pure Yellow", result.dyeName());
	}

	@Test
	public void storedColourMatchingDisplayIsNotABase() {
		Result result = classify("SUPERIOR_DRAGON_CHESTPLATE", 0x3C6746, "60:103:70", "DYE_HOLLY", AFTER_WITHER_RECOLOR);
		assertNull(result.baseRgb());
	}

	@Test
	public void seymourPiecesGetTierAndPatternsWithoutExoticLabel() {
		Result result = classify("CASHMERE_JACKET", 0x3C6746, "60:103:70", null, AFTER_WITHER_RECOLOR);
		assertEquals(Kind.UNLABELLED, result.kind());
		assertNotNull(result.seymour());
		assertEquals("Holly Dye", result.seymour().target().name());
		assertEquals(SeymourColors.Tier.T1_PLUS, result.seymour().tier());
	}

	@Test
	public void wrongSlotSeymourMatchIsFake() {
		Result jacket = classify("CASHMERE_JACKET", 0x123456, null, null, AFTER_WITHER_RECOLOR);
		assertEquals("Rabbit", jacket.seymour().target().name());
		assertTrue(jacket.seymour().fake());

		Result shoes = classify("OXFORD_SHOES", 0x123456, null, null, AFTER_WITHER_RECOLOR);
		assertEquals("Rabbit", shoes.seymour().target().name());
		assertFalse(shoes.seymour().fake());
		assertEquals(0.0D, shoes.seymour().deltaE(), 1e-9);
	}

	@Test
	public void dyedSeymourUsesStoredRoll() {
		Result result = classify("OXFORD_SHOES", 0x3C6746, "18:52:86", "DYE_HOLLY", AFTER_WITHER_RECOLOR);
		assertEquals(Kind.DYED, result.kind());
		assertEquals(Integer.valueOf(0x123456), result.baseRgb());
		assertNull(result.baseKind());
		assertEquals("Rabbit", result.seymour().target().name());

		Result hidden = classify("OXFORD_SHOES", 0x3C6746, null, "DYE_HOLLY", AFTER_WITHER_RECOLOR);
		assertNull(hidden.seymour());
		assertTrue(hidden.patterns().isEmpty());
	}

	@Test
	public void missingColourOrIdIsSkipped() {
		assertNull(ArmorColorClassifier.classify(new Input("SUPERIOR_DRAGON_CHESTPLATE", null, null, null, 0L), DATA, LOOKUPS));
		assertNull(ArmorColorClassifier.classify(new Input(" ", 0x123456, null, null, 0L), DATA, LOOKUPS));
	}

	@Test
	public void rgbParsing() {
		assertEquals(Integer.valueOf(0xF2DF11), ArmorColorClassifier.parseRgb("242:223:17"));
		assertEquals(Integer.valueOf(0xF2DF11), ArmorColorClassifier.parseCommaRgb("242,223,17"));
		assertNull(ArmorColorClassifier.parseRgb("256:0:0"));
		assertNull(ArmorColorClassifier.parseRgb("1:2"));
		assertNull(ArmorColorClassifier.parseRgb("a:b:c"));
		assertEquals("00FF0A", ArmorColorClassifier.hex(0xFF00FF0A));
	}

	@Test
	public void targetsComeFromPermittedSources() {
		JsonObject superior = JsonParser.parseString(
			"{\"id\":\"SUPERIOR_DRAGON_BOOTS\",\"name\":\"Superior Dragon Boots\",\"category\":\"BOOTS\",\"color\":\"242,93,24\"}"
		).getAsJsonObject();
		JsonObject leather = JsonParser.parseString(
			"{\"id\":\"LEATHER_BOOTS\",\"name\":\"Leather Boots\",\"category\":\"BOOTS\",\"color\":\"1,2,3\"}"
		).getAsJsonObject();
		JsonObject holly = JsonParser.parseString(
			"{\"internalname\":\"DYE_HOLLY\",\"displayname\":\"\u00a76Holly Dye\",\"lore\":[\"\u00a77Changes the color of an armor piece\",\"\u00a77to \u00a72#3C6746\u00a77!\"]}"
		).getAsJsonObject();
		JsonObject fade = JsonParser.parseString(
			"{\"internalname\":\"DYE_BLACK_ICE\",\"displayname\":\"\u00a76Black Ice Dye\",\"lore\":[\"\u00a77Animates the color of an armor piece\",\"\u00a77between #048F95 and #088D97!\"]}"
		).getAsJsonObject();
		List<SeymourColors.Target> targets = ArmorColorClassifier.buildTargets(
			List.of(superior, leather), List.of(holly, fade), Map.of("Lime", 0x80C71F), DATA
		);

		SeymourColors.Target boots = find(targets, 0xF25D18);
		assertEquals("Superior Dragon Boots", boots.name());
		assertEquals(Set.of(SeymourColors.Piece.BOOTS), boots.pieces());
		assertNull(find(targets, 0x010203));
		assertEquals("Holly Dye", find(targets, 0x3C6746).name());
		assertTrue(find(targets, 0x048F95).fade());
		assertTrue(find(targets, 0x088D97).fade());
		assertEquals("Pure Red", find(targets, 0xFF0000).name());
		assertEquals("Vanilla Lime Dye", find(targets, 0x7FCC19).name());
		assertEquals("Crystal", find(targets, 0x1F0030).name());
		assertEquals("Pure Magenta", find(targets, 0xFF00FF).name());
		assertEquals("Modern Lime Dye", find(targets, 0x80C71F).name());
	}

	@Test
	public void variableArmourColoursBecomeSlotRestrictedTargets() {
		List<JsonObject> hypixel = List.of(
			item("ADAPTIVE_CHESTPLATE", "Adaptive Chestplate", "CHESTPLATE", "191,188,178"),
			item("ADAPTIVE_LEGGINGS", "Adaptive Leggings", "LEGGINGS", "191,188,178"),
			item("ADAPTIVE_BOOTS", "Adaptive Boots", "BOOTS", "191,188,178"),
			item("RANCHERS_BOOTS", "Rancher's Boots", "BOOTS", "204,85,0"),
			item("REAPER_CHESTPLATE", "Reaper Chestplate", "CHESTPLATE", "27,27,27"),
			item("REAPER_LEGGINGS", "Reaper Leggings", "LEGGINGS", "27,27,27"),
			item("REAPER_BOOTS", "Reaper Boots", "BOOTS", "27,27,27")
		);
		List<SeymourColors.Target> targets = ArmorColorClassifier.buildTargets(hypixel, List.of(), Map.of(), DATA);

		SeymourColors.Target adaptiveChest = find(targets, 0x3ABE78);
		assertEquals("Adaptive Chestplate", adaptiveChest.name());
		assertEquals(Set.of(SeymourColors.Piece.CHESTPLATE), adaptiveChest.pieces());
		SeymourColors.Target adaptiveLower = find(targets, 0x169F57);
		assertEquals("Adaptive Leggings+Boots", adaptiveLower.name());
		assertEquals(Set.of(SeymourColors.Piece.LEGGINGS, SeymourColors.Piece.BOOTS), adaptiveLower.pieces());
		assertEquals(Set.of(SeymourColors.Piece.BOOTS), find(targets, 0xCC5500).pieces());
		assertEquals("Reaper 3p", find(targets, 0x1B1B1B).name());
		assertEquals("Pure Red", find(targets, 0xFF0000).name());
		assertEquals(
			Set.of(SeymourColors.Piece.CHESTPLATE, SeymourColors.Piece.LEGGINGS, SeymourColors.Piece.BOOTS),
			find(targets, 0xBFBCB2).pieces()
		);
	}

	@Test
	public void referenceNamesUseFullArmourNames() {
		List<JsonObject> hypixel = List.of(
			item("WISE_WITHER_LEGGINGS", "Storm's Leggings", "LEGGINGS", "23,168,196"),
			item("SUPERIOR_DRAGON_CHESTPLATE", "Superior Dragon Chestplate", "CHESTPLATE", "242,223,17"),
			item("SUPERIOR_DRAGON_LEGGINGS", "Superior Dragon Leggings", "LEGGINGS", "242,223,17"),
			item("HOLY_DRAGON_CHESTPLATE", "Holy Dragon Chestplate", "CHESTPLATE", "71,209,71"),
			item("HOLY_DRAGON_LEGGINGS", "Holy Dragon Leggings", "LEGGINGS", "71,209,71"),
			item("HOLY_DRAGON_BOOTS", "Holy Dragon Boots", "BOOTS", "71,209,71"),
			item("ROTTEN_CHESTPLATE", "Rotten Chestplate", "CHESTPLATE", "1,125,49"),
			item("BACKWATER_BOOTS", "Backwater Boots", "BOOTS", "1,125,49")
		);
		List<SeymourColors.Target> targets = ArmorColorClassifier.buildTargets(hypixel, List.of(), Map.of(), DATA);
		assertEquals("Storm's Leggings", find(targets, 0x17A8C4).name());
		assertEquals("Superior Dragon Chestplate+Leggings", find(targets, 0xF2DF11).name());
		assertEquals("Holy Dragon 3p", find(targets, 0x47D147).name());
		assertEquals("Backwater Boots (+1)", find(targets, 0x017D31).name());
	}

	@Test
	public void velvetTopHatMatchesStormsLeggingsAcrossSlots() {
		List<SeymourColors.Target> targets = ArmorColorClassifier.buildTargets(
			List.of(item("WISE_WITHER_LEGGINGS", "Storm's Leggings", "LEGGINGS", "23,168,196")), List.of(),
			Map.of("Light Blue", 0x3AB3DA), DATA
		);
		ArmorColorClassifier.Lookups lookups = ArmorColorClassifier.lookups(id -> null, id -> null, targets);
		Result result = ArmorColorClassifier.classify(new Input("VELVET_TOP_HAT", 0x11A8C5, "17:168:197", null, 0L), DATA, lookups);
		assertEquals("Storm's Leggings", result.seymour().target().name());
		assertEquals(0x17A8C4, result.seymour().target().rgb());
		assertEquals(SeymourColors.Tier.T1_PLUS, result.seymour().tier());
		assertEquals(0.58D, result.seymour().deltaE(), 0.005D);
	}

	@Test
	public void variableColoursStayAvailableBeforeHypixelDataLoads() {
		List<SeymourColors.Target> targets = ArmorColorClassifier.buildTargets(List.of(), List.of(), Map.of(), DATA);
		SeymourColors.Target adaptive = find(targets, 0x3ABE78);
		assertEquals("Adaptive Chestplate", adaptive.name());
		assertTrue(adaptive.pieces().isEmpty());
	}

	private static JsonObject item(String id, String name, String category, String color) {
		JsonObject object = new JsonObject();
		object.addProperty("id", id);
		object.addProperty("name", name);
		object.addProperty("category", category);
		object.addProperty("color", color);
		return object;
	}

	private static SeymourColors.Target find(List<SeymourColors.Target> targets, int rgb) {
		for (SeymourColors.Target target : targets) {
			if (target.rgb() == rgb) {
				return target;
			}
		}
		return null;
	}
}
