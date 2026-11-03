package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.ArmorColorClassifier.Kind;
import dev.vy.betterpv.client.data.ArmorColorClassifier.Result;
import dev.vy.betterpv.client.data.ArmorColorTooltip.Line;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class ArmorColorTooltipTest {
	private static List<String> plain(Result result) {
		List<String> out = new ArrayList<>();
		for (Line line : ArmorColorTooltip.lines(result)) {
			out.add(line.plain());
		}
		return out;
	}

	private static Result result(int rgb, Kind kind) {
		return new Result(rgb, kind, null, null, null, null, List.of());
	}

	@Test
	public void labelledColourLine() {
		assertEquals(List.of("Color: \u25A0 #F2DF11 Original"), plain(result(0xF2DF11, Kind.ORIGINAL)));
		assertEquals(List.of("Color: \u25A0 #123456 Exotic"), plain(result(0x123456, Kind.EXOTIC)));
		assertEquals(List.of("Color: \u25A0 #FF66B2 Fairy"), plain(result(0xFF66B2, Kind.FAIRY)));
	}

	@Test
	public void unlabelledShowsHexOnly() {
		assertEquals(List.of("Color: \u25A0 #0B0B0B"), plain(result(0x0B0B0B, Kind.UNLABELLED)));
	}

	@Test
	public void swatchAndHexUseTheArmourColour() {
		Line line = ArmorColorTooltip.lines(result(0x3C9F46, Kind.EXOTIC)).get(0);
		assertEquals(0x3C9F46, line.segments().get(1).rgb());
		assertEquals(0x3C9F46, line.segments().get(2).rgb());
		assertEquals(ArmorColorTooltip.GOLD, line.segments().get(4).rgb());
	}

	@Test
	public void everyClassifiedHexIsColouredByItsValue() {
		for (Kind kind : Kind.values()) {
			Line line = ArmorColorTooltip.lines(new Result(0xF2DF11, kind, "Holly", null, null, null, List.of())).get(0);
			assertEquals(kind.name(), "#F2DF11", line.segments().get(2).text());
			assertEquals(kind.name(), 0xF2DF11, line.segments().get(2).rgb());
		}
	}

	@Test
	public void veryDarkHexesAreLightenedForContrast() {
		int black = ArmorColorTooltip.readable(0x000000);
		assertTrue(black != 0x000000);
		assertEquals((black >> 16) & 0xFF, black & 0xFF);
		int navy = ArmorColorTooltip.readable(0x0B1C5D);
		assertTrue((navy & 0xFF) > ((navy >> 16) & 0xFF));
		assertTrue((navy & 0xFF) >= 0x5D);
		assertEquals(0xF2DF11, ArmorColorTooltip.readable(0xF2DF11));
		assertEquals(0x11A8C5, ArmorColorTooltip.readable(0x11A8C5));

		Line dark = ArmorColorTooltip.lines(result(0x0B0B0B, Kind.UNLABELLED)).get(0);
		assertEquals(0x0B0B0B, dark.segments().get(1).rgb());
		assertEquals(ArmorColorTooltip.readable(0x0B0B0B), dark.segments().get(2).rgb());
	}

	@Test
	public void dyedWithBase() {
		Result dyed = new Result(0x3C6746, Kind.DYED, "Holly", 0xF2DF11, Kind.ORIGINAL, null, List.of());
		assertEquals(List.of("Color: \u25A0 #3C6746 Holly Dye", "Base: \u25A0 #F2DF11 Original"), plain(dyed));
		List<Line> lines = ArmorColorTooltip.lines(dyed);
		assertEquals(ArmorColorTooltip.readable(0x3C6746), lines.get(0).segments().get(2).rgb());
		assertEquals(0xF2DF11, lines.get(1).segments().get(2).rgb());
		Result unnamed = new Result(0x3C6746, Kind.DYED, null, null, null, null, List.of());
		assertEquals(List.of("Color: \u25A0 #3C6746 Dyed"), plain(unnamed));
	}

	private static final SeymourColors.Target STORMS_LEGGINGS =
		new SeymourColors.Target("Storm's Leggings", 0x17A8C4, Set.of(SeymourColors.Piece.LEGGINGS), false);

	@Test
	public void seymourUsesThreeCompactLines() {
		Result hat = new Result(
			0x11A8C5, Kind.UNLABELLED, null, null, null,
			new SeymourColors.Match(STORMS_LEGGINGS, 0.5812D, SeymourColors.Tier.T1_PLUS, true), List.of()
		);
		assertEquals(List.of(
			"Hex: \u25A0 #11A8C5 | T1<",
			"Closest: Storm's Leggings",
			"Target: #17A8C4 | \u0394E: 0.58"
		), plain(hat));
		List<Line> lines = ArmorColorTooltip.lines(hat);
		assertEquals(0x11A8C5, lines.get(0).segments().get(1).rgb());
		assertEquals(0x11A8C5, lines.get(0).segments().get(2).rgb());
		assertEquals(ArmorColorTooltip.LIGHT_PURPLE, lines.get(0).segments().get(4).rgb());
		assertEquals(0x17A8C4, lines.get(2).segments().get(1).rgb());
	}

	@Test
	public void seymourLinesStayShort() {
		Result hat = new Result(
			0x11A8C5, Kind.UNLABELLED, null, null, null,
			new SeymourColors.Match(STORMS_LEGGINGS, 12.3456D, SeymourColors.Tier.T3, true), List.of()
		);
		for (String line : plain(hat)) {
			assertTrue(line, line.length() <= 40);
		}
	}

	@Test
	public void seymourTooltipHasNoFakeOrNextLines() {
		Result hat = new Result(
			0x11A8C5, Kind.UNLABELLED, null, null, null,
			new SeymourColors.Match(STORMS_LEGGINGS, 0.58D, SeymourColors.Tier.T1_PLUS, true), List.of()
		);
		for (String line : plain(hat)) {
			assertFalse(line, line.contains("Fake") || line.startsWith("Next") || line.startsWith("Color"));
		}
	}

	@Test
	public void seymourTierLabels() {
		for (SeymourColors.Tier tier : SeymourColors.Tier.values()) {
			Result result = new Result(
				0x123456, Kind.UNLABELLED, null, null, null,
				new SeymourColors.Match(STORMS_LEGGINGS, 9D, tier, false), List.of()
			);
			assertEquals("Hex: \u25A0 #123456 | " + tier.label(), plain(result).get(0));
		}
	}

	@Test
	public void dyedSeymourShowsDyeThenStoredRoll() {
		Result dyed = new Result(
			0x3C6746, Kind.DYED, "Holly", 0x11A8C5, null,
			new SeymourColors.Match(STORMS_LEGGINGS, 0.58D, SeymourColors.Tier.T1_PLUS, true),
			List.of(SeymourColors.Pattern.PALINDROME)
		);
		assertEquals(List.of(
			"Color: \u25A0 #3C6746 Holly Dye",
			"Hex: \u25A0 #11A8C5 | T1<",
			"Closest: Storm's Leggings",
			"Target: #17A8C4 | \u0394E: 0.58",
			"Pattern: Palindrome"
		), plain(dyed));
	}

	@Test
	public void noDashesInAnyLine() {
		SeymourColors.Target holly = new SeymourColors.Target("Holly Dye", 0x3C6746, Set.of(), false);
		for (Kind kind : Kind.values()) {
			Result result = new Result(
				0x123456, kind, "Holly", 0xF2DF11, kind,
				new SeymourColors.Match(holly, 3D, SeymourColors.Tier.T2, true),
				List.of(SeymourColors.Pattern.PAIRED, SeymourColors.Pattern.ALIGNED)
			);
			for (String line : plain(result)) {
				assertFalse(line, line.contains("\u2014") || line.contains("\u2013"));
			}
		}
	}

	@Test
	public void colourLinesGoDirectlyBelowTheName() {
		List<String> lines = new ArrayList<>(List.of("Superior Dragon Chestplate", "Health: +150", "LEGENDARY CHESTPLATE"));
		ArmorColorTooltip.insertBelowName(lines, List.of("Color: #F2DF11 Original"), -1);
		assertEquals(List.of("Superior Dragon Chestplate", "Color: #F2DF11 Original", "Health: +150", "LEGENDARY CHESTPLATE"), lines);
	}

	@Test
	public void multipleColourLinesKeepTheirOrderUnderTheName() {
		List<String> lines = new ArrayList<>(List.of("Cashmere Jacket", "Health: +40"));
		ArmorColorTooltip.insertBelowName(lines, List.of("Color", "Seymour", "Pattern"), -1);
		assertEquals(List.of("Cashmere Jacket", "Color", "Seymour", "Pattern", "Health: +40"), lines);
	}

	@Test
	public void existingLoreColourLineIsReplacedNotDuplicated() {
		List<String> lines = new ArrayList<>(List.of("Cashmere Jacket", "Health: +40", "Color: #3C6746", "EPIC CHESTPLATE"));
		int existing = ArmorColorTooltip.existingColorLine(lines, 0x3C6746);
		ArmorColorTooltip.insertBelowName(lines, List.of("Color: \u25A0 #3C6746"), existing);
		assertEquals(List.of("Cashmere Jacket", "Color: \u25A0 #3C6746", "Health: +40", "EPIC CHESTPLATE"), lines);
		assertEquals(1, lines.stream().filter(line -> line.startsWith("Color")).count());
	}

	@Test
	public void insertingIntoNamelessOrEmptyTooltipIsSafe() {
		List<String> empty = new ArrayList<>();
		ArmorColorTooltip.insertBelowName(empty, List.of("Color"), -1);
		assertEquals(List.of("Color"), empty);
		List<String> nameOnly = new ArrayList<>(List.of("Name"));
		ArmorColorTooltip.insertBelowName(nameOnly, List.of(), -1);
		assertEquals(List.of("Name"), nameOnly);
		ArmorColorTooltip.insertBelowName(nameOnly, List.of("Color"), 0);
		assertEquals(List.of("Name", "Color"), nameOnly);
	}

	@Test
	public void existingLoreColourLineIsDetected() {
		List<String> lore = List.of("Superior Dragon Chestplate", "\u00a77Color: \u00a7f#3C6746", "LEGENDARY CHESTPLATE");
		assertEquals(1, ArmorColorTooltip.existingColorLine(lore, 0x3C6746));
		assertEquals(-1, ArmorColorTooltip.existingColorLine(lore, 0x123456));
		assertEquals(-1, ArmorColorTooltip.existingColorLine(List.of("Mentions #3C6746 in passing"), 0x3C6746));
	}
}
