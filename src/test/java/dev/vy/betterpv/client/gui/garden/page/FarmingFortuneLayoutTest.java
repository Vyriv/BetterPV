package dev.vy.betterpv.client.gui.garden.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.gui.garden.page.FarmingFortuneLayout.Rect;
import dev.vy.betterpv.client.gui.garden.page.FarmingFortuneLayout.Section;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class FarmingFortuneLayoutTest {
	private static final int CROPS = 13;

	private static void assertNoOverlap(List<Rect> rects) {
		for (int i = 0; i < rects.size(); i++) {
			for (int j = i + 1; j < rects.size(); j++) {
				assertFalse(rects.get(i) + " overlaps " + rects.get(j), rects.get(i).overlaps(rects.get(j)));
			}
		}
	}

	@Test
	public void cropSlotsStayInsideTheBodyAtEveryWidth() {
		for (int w = 120; w <= 760; w += 7) {
			for (int heroW : new int[] {120, 180, 240}) {
				List<Rect> slots = FarmingFortuneLayout.cropSlots(10, 40, w, heroW, CROPS);
				assertEquals(CROPS, slots.size());
				assertNoOverlap(slots);
				for (Rect r : slots) {
					assertTrue("w=" + w + " " + r, r.x() >= 10 && r.right() <= 10 + w);
					assertTrue(r.y() >= 40);
				}
				Rect hero = new Rect(10, 40, Math.min(heroW, w), FarmingFortuneLayout.HERO_H);
				for (Rect r : slots) {
					assertFalse("crop slot under the hero text at w=" + w, r.overlaps(hero));
				}
				assertTrue(FarmingFortuneLayout.heroBottom(40, slots) >= 40 + FarmingFortuneLayout.HERO_H);
			}
		}
	}

	@Test
	public void typicalPanelKeepsCropsBesideTheHero() {
		// The profile viewer body is about 496px wide once padding is taken off.
		assertTrue(FarmingFortuneLayout.cropsBesideHero(496, 180, CROPS));
		List<Rect> slots = FarmingFortuneLayout.cropSlots(0, 0, 496, 180, CROPS);
		assertEquals("one row", slots.get(0).y(), slots.get(CROPS - 1).y());
		assertEquals("right aligned", 496, slots.get(CROPS - 1).right());
	}

	@Test
	public void columnsFillTheWidthWithoutOverlap() {
		for (int w = 100; w <= 760; w++) {
			int n = FarmingFortuneLayout.columnCount(w);
			List<Rect> cols = FarmingFortuneLayout.columns(5, w, n);
			assertEquals(n, cols.size());
			assertEquals(5, cols.get(0).x());
			assertEquals("w=" + w, 5 + w, cols.get(n - 1).right());
			List<Rect> spans = new ArrayList<>();
			for (Rect c : cols) {
				spans.add(new Rect(c.x(), 0, c.w(), 1));
				if (n > 1) {
					assertTrue("w=" + w + " column too narrow: " + c.w(), c.w() >= FarmingFortuneLayout.MIN_COL_W);
				}
			}
			assertNoOverlap(spans);
		}
		assertEquals(3, FarmingFortuneLayout.columnCount(496));
	}

	@Test
	public void everySectionIsPlacedExactlyOnce() {
		for (int n = 1; n <= 3; n++) {
			List<List<Section>> plan = FarmingFortuneLayout.sections(n);
			assertEquals(n, plan.size());
			Set<Section> seen = EnumSet.noneOf(Section.class);
			int count = 0;
			for (List<Section> col : plan) {
				seen.addAll(col);
				count += col.size();
			}
			assertEquals(EnumSet.allOf(Section.class), seen);
			assertEquals(Section.values().length, count);
		}
	}

	@Test
	public void temporaryChipsWrapInsideTheRow() {
		int[][] sets = {
			{48, 60, 52, 75, 40, 300},
			// Every temporary source at once, "+400" to "+1,026.5" wide.
			{42, 36, 42, 36, 36, 42, 36, 36, 36, 42, 36, 42, 36, 62}
		};
		for (int[] widths : sets) {
			for (int w = 60; w <= 760; w += 11) {
				List<Rect> chips = FarmingFortuneLayout.chips(20, 100, w, widths);
				assertEquals(widths.length, chips.size());
				assertNoOverlap(chips);
				for (Rect r : chips) {
					assertTrue("w=" + w + " " + r, r.x() >= 20 && r.right() <= 20 + w);
					assertEquals(FarmingFortuneLayout.COND_H, r.h());
				}
			}
		}
	}
}
