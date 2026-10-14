package dev.vy.betterpv.client.dungeons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public final class EssencePerkTipsTest {
	@Test
	public void echoesOfTheLostTipShowsNameLevelAndCharges() {
		List<String> text = text(EssencePerkTips.tip(
			new DungeonSnapshot.EssencePerk("dungeonbreaker_secrets", "Echoes of the Lost", 5, 5)));
		assertEquals("Echoes of the Lost", text.get(0));
		assertTrue(text.contains("Level5/5"));
		assertTrue(text.contains("Restore 5 charges on your Dungeonbreaker after collecting a secret."));
	}

	@Test
	public void echoesOfTheLostFirstTierUsesSingularCharge() {
		List<String> text = text(EssencePerkTips.tip(
			new DungeonSnapshot.EssencePerk("dungeonbreaker_secrets", "Echoes of the Lost", 1, 5)));
		assertTrue(text.contains("Restore 1 charge on your Dungeonbreaker after collecting a secret."));
	}

	private static List<String> text(List<PvTooltip.Line> lines) {
		return lines.stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).collect(Collectors.joining()))
			.toList();
	}
}
