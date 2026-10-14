package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.Test;

public final class PetListRulesTest {
	private static PetSnapshot.Entry pet(String type, int tierIndex, double exp, String uuid) {
		return new PetSnapshot.Entry(type, type, "COMMON", tierIndex, type + ";" + tierIndex, 1, 100, exp, 0, 0, 0, 0f,
			false, "", 0, "", uuid, 0);
	}

	@Test
	public void hidesBingoPet() {
		List<PetSnapshot.Entry> out = PetListRules.visibleOwned(List.of(pet("BINGO", 4, 10, "a"), pet("WOLF", 4, 5, "b")));
		assertEquals(1, out.size());
		assertEquals("WOLF", out.get(0).type());
		assertFalse(PetListRules.showAsMissing("BINGO", Set.of()));
	}

	@Test
	public void keepsOnlyHighestWispType() {
		List<PetSnapshot.Entry> out = PetListRules.visibleOwned(List.of(
			pet("DROPLET_WISP", 1, 9_000_000, "a"),
			pet("GLACIAL_WISP", 3, 10, "b"),
			pet("FROST_WISP", 2, 5_000_000, "c"),
			pet("WOLF", 4, 1, "d")
		));
		assertEquals(2, out.size());
		assertTrue(out.stream().anyMatch(p -> p.type().equals("GLACIAL_WISP")));
		assertTrue(out.stream().noneMatch(p -> p.type().equals("DROPLET_WISP") || p.type().equals("FROST_WISP")));
	}

	@Test
	public void duplicateWispsPickHighestXpThenUuid() {
		List<PetSnapshot.Entry> out = PetListRules.visibleOwned(List.of(
			pet("SUBZERO_WISP", 4, 100, "b"),
			pet("SUBZERO_WISP", 4, 200, "c"),
			pet("SUBZERO_WISP", 4, 200, "a")
		));
		assertEquals(1, out.size());
		assertEquals("a", out.get(0).uuid());

		List<PetSnapshot.Entry> reversed = PetListRules.visibleOwned(List.of(
			pet("SUBZERO_WISP", 4, 200, "a"),
			pet("SUBZERO_WISP", 4, 200, "c"),
			pet("SUBZERO_WISP", 4, 100, "b")
		));
		assertEquals("a", reversed.get(0).uuid());
	}

	@Test
	public void missingWispsCollapseToOnePlaceholder() {
		assertTrue(PetListRules.showAsMissing("DROPLET_WISP", Set.of()));
		assertFalse(PetListRules.showAsMissing("SUBZERO_WISP", Set.of()));
		assertFalse(PetListRules.showAsMissing("SUBZERO_WISP", Set.of("FROST_WISP")));
		assertFalse(PetListRules.showAsMissing("DROPLET_WISP", Set.of("FROST_WISP")));
		assertTrue(PetListRules.showAsMissing("WOLF", Set.of("FROST_WISP")));
	}
}
