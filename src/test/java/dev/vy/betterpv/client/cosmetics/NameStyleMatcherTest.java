package dev.vy.betterpv.client.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.List;
import java.util.Random;
import org.junit.Test;

public class NameStyleMatcherTest {
	@Test
	public void handlesLongTextAndSeveralMatches() {
		NameStyleMatcher.Index index = index("Ann", "Anna", "Vyriv");
		assertNull(NameStyleMatcher.findFirstNameMatch("meow".repeat(50_000), index, 0));
		String text = "meow".repeat(50_000) + " Anna met ANN and vYrIv";
		NameStyleMatcher.MatchedCustomization first = NameStyleMatcher.findFirstNameMatch(text, index, 0);
		NameStyleMatcher.MatchedCustomization second = NameStyleMatcher.findFirstNameMatch(text, index, first.index() + first.matchedName().length());
		NameStyleMatcher.MatchedCustomization third = NameStyleMatcher.findFirstNameMatch(text, index, second.index() + second.matchedName().length());
		assertEquals("Anna", first.matchedName());
		assertEquals("ANN", second.matchedName());
		assertEquals("vYrIv", third.matchedName());
	}

	@Test
	public void choosesEarliestThenLongestAndPreservesCandidatePrecedence() {
		PlayerCustomizationRegistry.NameCandidate shortName = CosmeticsTestFixtures.candidate("Ann", false);
		PlayerCustomizationRegistry.NameCandidate longName = CosmeticsTestFixtures.candidate("Anna", false);
		NameStyleMatcher.MatchedCustomization match = NameStyleMatcher.findFirstNameMatch("x aNnA Ann",
			NameStyleMatcher.compile(List.of(shortName, longName)), 0);
		assertEquals(2, match.index());
		assertEquals("aNnA", match.matchedName());
		assertSame(longName.customization(), match.customization());
	}

	@Test
	public void enforcesBoundariesAndContinuesAfterInvalidOccurrences() {
		NameStyleMatcher.Index index = NameStyleMatcher.compile(List.of(CosmeticsTestFixtures.candidate("Runner", true)));
		assertEquals(9, NameStyleMatcher.findFirstNameMatch("xRunner_ RUNNER!", index, 0).index());
		assertNull(NameStyleMatcher.findFirstNameMatch("xRunner_", index, 0));
	}

	@Test
	public void agreesWithLegacyMatcherAcrossRandomInputs() {
		List<PlayerCustomizationRegistry.NameCandidate> candidates = List.of(
			CosmeticsTestFixtures.candidate("Ann", false), CosmeticsTestFixtures.candidate("Anna", false),
			CosmeticsTestFixtures.candidate("Runner", true), CosmeticsTestFixtures.candidate("Vyriv", false),
			CosmeticsTestFixtures.candidate("MEOWS", true));
		NameStyleMatcher.Index index = NameStyleMatcher.compile(candidates);
		Random random = new Random(0xB3773FL);
		String alphabet = " aAnNrRuUnNeErRvVyYiImMeEoOwWsS_!";
		for (int iteration = 0; iteration < 2_000; iteration++) {
			int length = random.nextInt(100);
			StringBuilder text = new StringBuilder(length);
			for (int character = 0; character < length; character++) text.append(alphabet.charAt(random.nextInt(alphabet.length())));
			int start = length == 0 ? 0 : random.nextInt(length + 1);
			NameStyleMatcher.MatchedCustomization expected = reference(text.toString(), candidates, start);
			NameStyleMatcher.MatchedCustomization actual = NameStyleMatcher.findFirstNameMatch(text.toString(), index, start);
			assertEquals(expected == null ? null : expected.index(), actual == null ? null : actual.index());
			assertEquals(expected == null ? null : expected.matchedName(), actual == null ? null : actual.matchedName());
		}
	}

	private static NameStyleMatcher.MatchedCustomization reference(String text,
	                                                               List<PlayerCustomizationRegistry.NameCandidate> candidates,
	                                                               int startIndex) {
		int bestIndex = Integer.MAX_VALUE;
		PlayerCustomizationRegistry.NameCandidate best = null;
		for (PlayerCustomizationRegistry.NameCandidate candidate : candidates) {
			int search = startIndex;
			while (search < text.length()) {
				int found = -1;
				for (int offset = Math.max(0, search); offset <= text.length() - candidate.value().length(); offset++) {
					if (text.regionMatches(true, offset, candidate.value(), 0, candidate.value().length())) { found = offset; break; }
				}
				if (found == -1 || found > bestIndex) break;
				int end = found + candidate.value().length();
				if (!candidate.requiresBoundary() || (boundary(text, found - 1) && boundary(text, end))) {
					if (found < bestIndex || (found == bestIndex && (best == null || candidate.value().length() > best.value().length()))) {
						bestIndex = found;
						best = candidate;
					}
					break;
				}
				search = found + 1;
			}
		}
		return best == null ? null : new NameStyleMatcher.MatchedCustomization(bestIndex,
			text.substring(bestIndex, bestIndex + best.value().length()), best.customization());
	}

	private static boolean boundary(String text, int index) {
		return index < 0 || index >= text.length() || (!Character.isLetterOrDigit(text.charAt(index)) && text.charAt(index) != '_');
	}

	private static NameStyleMatcher.Index index(String... names) {
		return NameStyleMatcher.compile(java.util.Arrays.stream(names)
			.map(name -> CosmeticsTestFixtures.candidate(name, false)).toList());
	}
}
