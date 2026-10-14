package dev.vy.betterpv.client.gui.garden.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.FarmingFortuneEstimate;
import dev.vy.betterpv.client.data.PlayerStatsSnapshot;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import org.junit.Test;

public class FarmingFortuneTipTest {
	private static PlayerStatsSnapshot stats(double value) {
		return new PlayerStatsSnapshot(List.of(new PlayerStatsSnapshot.Entry("farming_fortune", "FaF", OptionalDouble.of(value))));
	}

	@Test
	public void noEstimateFallsBackToWornGearRow() {
		assertNull(FarmingFortuneTip.row(stats(751.2D), null));
	}

	@Test
	public void labelSaysEstimate() {
		assertTrue(FarmingFortuneTip.LABEL.contains("est."));
	}

	@Test
	public void formatsWholeAndDecimalValues() {
		assertEquals("1,234", FarmingFortuneTip.format(1234.001D));
		assertEquals("751.2", FarmingFortuneTip.format(751.2D));
		assertEquals("33.33", FarmingFortuneTip.format(33.33D));
	}

	@Test
	public void tooltipCallsOutEstimateAndMissingGarden() {
		FarmingFortuneEstimate est = FarmingFortuneEstimate.attach(stats(100D), new JsonObject(), Map.of()).farmingFortune();
		String text = FarmingFortuneTip.tooltip(est, FarmingFortuneEstimate.Garden.NONE, "0").stream()
			.map(FarmingFortuneTipTest::plain).collect(Collectors.joining("\n"));
		assertTrue(text.contains("Estimate"));
		assertTrue(text.contains("not a live stat"));
		assertTrue(text.contains("Garden not loaded yet."));
		assertTrue(text.contains("Permanent bonuses"));
	}

	private static String plain(PvTooltip.Line line) {
		StringBuilder sb = new StringBuilder();
		for (List<PvTooltip.Span> list : List.of(line.left(), line.value(), line.meta())) {
			for (PvTooltip.Span span : list) {
				sb.append(span.text()).append(' ');
			}
		}
		return sb.toString();
	}
}
