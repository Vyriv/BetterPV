package dev.vy.betterpv.client.cosmetics;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class NameStyleMatcherBenchmark {
	private static final int WARMUP_ITERATIONS = 8;
	private static final int MEASURED_ITERATIONS = 15;
	private static volatile Object blackhole;

	private NameStyleMatcherBenchmark() {
	}

	public static void main(String[] args) {
		List<PlayerCustomizationRegistry.NameCandidate> candidates = candidates(96);
		NameStyleMatcher.Index index = NameStyleMatcher.compile(candidates);
		benchmark("no-match-200k", "meow".repeat(50_000), candidates, index);
		benchmark("match-near-end-200k", "meow".repeat(50_000) + " CosmeticUser095", candidates, index);
	}

	private static void benchmark(String label, String text, List<PlayerCustomizationRegistry.NameCandidate> candidates,
	                              NameStyleMatcher.Index index) {
		for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
			blackhole = legacyFindFirstNameMatch(text, candidates, 0);
			blackhole = NameStyleMatcher.findFirstNameMatch(text, index, 0);
		}
		Measurement legacy = measure(() -> legacyFindFirstNameMatch(text, candidates, 0));
		Measurement indexed = measure(() -> NameStyleMatcher.findFirstNameMatch(text, index, 0));
		System.out.printf("%s legacy median %.3f ms, indexed median %.3f ms, speedup %.2fx, legacy allocation %d B/op, indexed allocation %d B/op%n",
			label, legacy.medianNanos / 1_000_000.0D, indexed.medianNanos / 1_000_000.0D,
			(double) legacy.medianNanos / indexed.medianNanos,
			legacy.allocatedBytesPerOperation, indexed.allocatedBytesPerOperation);
	}

	private static NameStyleMatcher.MatchedCustomization legacyFindFirstNameMatch(String text,
	                                                                             List<PlayerCustomizationRegistry.NameCandidate> candidates,
	                                                                             int startIndex) {
		if (text == null || text.isEmpty() || candidates == null || candidates.isEmpty()) return null;
		int from = Math.max(0, startIndex);
		if (from >= text.length()) return null;
		String lowerText = text.toLowerCase(java.util.Locale.ROOT);
		int bestIndex = Integer.MAX_VALUE;
		PlayerCustomizationRegistry.NameCandidate bestCandidate = null;
		for (PlayerCustomizationRegistry.NameCandidate candidate : candidates) {
			String needle = candidate.value().toLowerCase(java.util.Locale.ROOT);
			int searchIndex = from;
			while (searchIndex <= lowerText.length() - needle.length()) {
				int candidateIndex = lowerText.indexOf(needle, searchIndex);
				if (candidateIndex == -1 || candidateIndex > bestIndex) break;
				if (!candidate.requiresBoundary() || boundary(text, candidateIndex, candidateIndex + needle.length())) {
					if (candidateIndex < bestIndex || (candidateIndex == bestIndex
						&& (bestCandidate == null || needle.length() > bestCandidate.value().length()))) {
						bestIndex = candidateIndex;
						bestCandidate = candidate;
					}
					break;
				}
				searchIndex = candidateIndex + 1;
			}
		}
		return bestCandidate == null ? null : new NameStyleMatcher.MatchedCustomization(bestIndex,
			text.substring(bestIndex, bestIndex + bestCandidate.value().length()), bestCandidate.customization());
	}

	private static boolean boundary(String text, int start, int end) {
		return boundaryCharacter(start > 0 ? text.charAt(start - 1) : null)
			&& boundaryCharacter(end < text.length() ? text.charAt(end) : null);
	}

	private static boolean boundaryCharacter(Character character) {
		return character == null || (!Character.isLetterOrDigit(character) && character != '_');
	}

	private static Measurement measure(Operation operation) {
		long[] times = new long[MEASURED_ITERATIONS];
		ThreadMXBean allocationBean = allocationBean();
		long threadId = Thread.currentThread().threadId();
		long allocationBefore = allocationBean == null ? -1L : allocationBean.getThreadAllocatedBytes(threadId);
		for (int iteration = 0; iteration < MEASURED_ITERATIONS; iteration++) {
			long start = System.nanoTime();
			blackhole = operation.run();
			times[iteration] = System.nanoTime() - start;
		}
		long allocationAfter = allocationBean == null ? -1L : allocationBean.getThreadAllocatedBytes(threadId);
		Arrays.sort(times);
		long allocated = allocationBefore < 0L ? -1L : (allocationAfter - allocationBefore) / MEASURED_ITERATIONS;
		return new Measurement(times[times.length / 2], allocated);
	}

	private static ThreadMXBean allocationBean() {
		if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)) return null;
		if (!bean.isThreadAllocatedMemorySupported()) return null;
		if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
		return bean;
	}

	private static List<PlayerCustomizationRegistry.NameCandidate> candidates(int count) {
		List<PlayerCustomizationRegistry.NameCandidate> candidates = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			candidates.add(CosmeticsTestFixtures.candidate("CosmeticUser%03d".formatted(index), false));
		}
		return List.copyOf(candidates);
	}

	@FunctionalInterface
	private interface Operation {
		Object run();
	}

	private record Measurement(long medianNanos, long allocatedBytesPerOperation) {
	}
}
