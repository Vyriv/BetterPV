package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.SeymourColors.Pattern;
import dev.vy.betterpv.client.data.SeymourColors.Target;
import dev.vy.betterpv.client.data.SeymourColors.Tier;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class SeymourColorsTest {
	@Test
	public void tierBoundariesAreStrict() {
		assertEquals(Tier.T1_PLUS, Tier.of(0.0D));
		assertEquals(Tier.T1_PLUS, Tier.of(0.9999D));
		assertEquals(Tier.T1, Tier.of(1.0D));
		assertEquals(Tier.T1, Tier.of(1.9999D));
		assertEquals(Tier.T2, Tier.of(2.0D));
		assertEquals(Tier.T2, Tier.of(4.9999D));
		assertEquals(Tier.T3, Tier.of(5.0D));
		assertEquals(Tier.T3, Tier.of(100.0D));
	}

	@Test
	public void tierLabelsMatchSeymourAnalyzer() {
		assertEquals("T1<", Tier.T1_PLUS.label());
		assertEquals("T1", Tier.T1.label());
		assertEquals("T2", Tier.T2.label());
		assertEquals("T3", Tier.T3.label());
	}

	@Test
	public void labMatchesReferenceValues() {
		double[] red = SeymourColors.lab(0xFF0000);
		assertEquals(53.24D, red[0], 0.05D);
		assertEquals(80.09D, red[1], 0.05D);
		assertEquals(67.20D, red[2], 0.05D);
		double[] white = SeymourColors.lab(0xFFFFFF);
		assertEquals(100.0D, white[0], 0.01D);
		assertEquals(0.0D, white[1], 0.01D);
		assertEquals(0.0D, white[2], 0.01D);
	}

	@Test
	public void deltaEIsSymmetricEuclideanLab() {
		assertEquals(100.0D, SeymourColors.deltaE(0x000000, 0xFFFFFF), 0.01D);
		assertEquals(SeymourColors.deltaE(0x3C6746, 0x123456), SeymourColors.deltaE(0x123456, 0x3C6746), 1e-12);
		assertEquals(0.0D, SeymourColors.deltaE(0xABCDEF, 0xABCDEF), 1e-12);
	}

	@Test
	public void bestPrefersTierThenSolidThenDelta() {
		Target solid = new Target("Solid", 0x808080, Set.of(), false);
		Target fadeExact = new Target("Fade", 0x818181, Set.of(), true);
		SeymourColors.Match match = SeymourColors.closest(0x818181, null, List.of(fadeExact, solid));
		assertEquals("Solid", match.target().name());
		assertEquals(Tier.T1_PLUS, match.tier());

		Target far = new Target("Far", 0x000000, Set.of(), false);
		SeymourColors.Match t3 = SeymourColors.closest(0xFFFFFF, null, List.of(far));
		assertEquals(Tier.T3, t3.tier());
		assertEquals("Far", t3.target().name());
		assertNull(SeymourColors.closest(0xFFFFFF, null, List.of()));
	}

	@Test
	public void wrongSlotReferencesAreNotFilteredOut() {
		Target leggings = new Target("Storm's Leggings", 0x17A8C4, Set.of(SeymourColors.Piece.LEGGINGS), false);
		Target any = new Target("Any", 0x3A83DA, Set.of(), false);
		SeymourColors.Match helmet = SeymourColors.closest(0x11A8C5, SeymourColors.Piece.HELMET, List.of(any, leggings));
		assertEquals("Storm's Leggings", helmet.target().name());
		assertEquals(Tier.T1_PLUS, helmet.tier());
		assertTrue(helmet.fake());

		SeymourColors.Match onLeggings = SeymourColors.closest(0x11A8C5, SeymourColors.Piece.LEGGINGS, List.of(any, leggings));
		assertFalse(onLeggings.fake());
		assertFalse(SeymourColors.closest(0x11A8C5, null, List.of(leggings)).fake());
	}

	@Test
	public void patterns() {
		assertEquals(List.of(Pattern.PAIRED), SeymourColors.patterns(0xAABBCC));
		assertEquals(List.of(Pattern.REPEATING), SeymourColors.patterns(0x12A12A));
		assertEquals(List.of(Pattern.PALINDROME), SeymourColors.patterns(0x12AA21));
		assertEquals(List.of(Pattern.ALIGNED), SeymourColors.patterns(0x1A1B1C));
		assertEquals(List.of(Pattern.PAIRED, Pattern.REPEATING, Pattern.PALINDROME, Pattern.ALIGNED), SeymourColors.patterns(0xFFFFFF));
		assertTrue(SeymourColors.patterns(0x123456).isEmpty());
	}

	@Test
	public void seymourIdsAndPieces() {
		assertTrue(SeymourColors.isSeymour("VELVET_TOP_HAT"));
		assertFalse(SeymourColors.isSeymour("LEATHER_HELMET"));
		assertEquals(SeymourColors.Piece.HELMET, SeymourColors.pieceOf("VELVET_TOP_HAT"));
		assertEquals(SeymourColors.Piece.CHESTPLATE, SeymourColors.pieceOf("CASHMERE_JACKET"));
		assertEquals(SeymourColors.Piece.LEGGINGS, SeymourColors.pieceOf("SATIN_TROUSERS"));
		assertEquals(SeymourColors.Piece.BOOTS, SeymourColors.pieceOf("OXFORD_SHOES"));
	}
}
