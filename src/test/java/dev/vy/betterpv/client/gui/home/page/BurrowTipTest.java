package dev.vy.betterpv.client.gui.home.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.MiscStatsSnapshot;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public final class BurrowTipTest {
	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void arrowTipExplainsRarityBreakdown() {
		Map<String, Long> rarity = new LinkedHashMap<>();
		rarity.put("Epic", 40L);
		rarity.put("Legendary", 220L);
		List<String> text = text(RecordsPage.burrowTip(
			new MiscStatsSnapshot.BurrowCount("burrows_dug_next", "Arrow", 260L, rarity)));

		assertEquals("Arrow Burrows", text.get(0));
		assertTrue(text.contains("First burrow of each chain. Points to the next one."));
		assertTrue(text.contains("By Griffin rarity used"));
		assertTrue(text.contains("Legendary220"));
		assertTrue(text.contains("Total260"));
		assertTrue(text.get(text.size() - 1).startsWith("Lifetime"));
	}

	@Test
	public void chainsTipHasOwnTitle() {
		List<String> text = text(RecordsPage.burrowTip(
			new MiscStatsSnapshot.BurrowCount("burrows_chains_complete", "Chains Completed", 5L, Map.of())));

		assertEquals("Chains Completed", text.get(0));
		assertTrue(text.contains("Griffin burrow chains finished."));
		assertTrue(text.stream().noneMatch(line -> line.equals("By Griffin rarity used")));
	}

	private static List<String> text(List<PvTooltip.Line> lines) {
		return lines.stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).collect(Collectors.joining()))
			.toList();
	}
}
