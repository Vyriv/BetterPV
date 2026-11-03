package dev.vy.betterpv.client.gui.home;

import static org.junit.Assert.assertEquals;

import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public final class HomeCoinsTooltipTest {
	private static List<String> text(List<PvTooltip.Line> lines) {
		return lines.stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).collect(Collectors.joining()))
			.collect(Collectors.toList());
	}

	@Test
	public void tooltipShowsOnlyFullPurseAndBankValues() {
		List<String> text = text(HomeTooltips.coins(125_400_000D, 2_300_000_000D));
		assertEquals(List.of("Coins", "", "Purse125,400,000", "Bank2,300,000,000"), text);
	}

	@Test
	public void coinsLineUsesOneDecimal() {
		assertEquals("86.2m", HomeTooltips.coinsShort(86_188_000D));
		assertEquals("1.9b", HomeTooltips.coinsShort(1_929_000_000D));
		assertEquals("125.4m", HomeTooltips.coinsShort(125_400_000D));
		assertEquals("2.3b", HomeTooltips.coinsShort(2_300_000_000D));
		assertEquals("1.0m", HomeTooltips.coinsShort(999_960D));
		assertEquals("950", HomeTooltips.coinsShort(950D));
	}
}
