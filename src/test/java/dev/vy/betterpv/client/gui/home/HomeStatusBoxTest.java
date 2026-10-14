package dev.vy.betterpv.client.gui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.PlayerStatus;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class HomeStatusBoxTest {
	private static final long NOW = 1_790_000_000_000L;
	private static final long DAY = 86_400_000L;

	// Uneven glyph widths like the vanilla font, so trimming has to measure pixels, not chars.
	private static final ToIntFunction<String> WIDTH = s -> {
		int w = 0;
		for (char c : s.toCharArray()) {
			w += switch (c) {
				case 'i', 'l', '.', '\'' -> 2;
				case ' ', 't' -> 4;
				default -> 6;
			};
		}
		return w;
	};

	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void boxWidthNeverDependsOnTheLabel() {
		assertEquals(72, HomeLeftColumn.statusBoxWidth(80));
		assertEquals(48, HomeLeftColumn.statusBoxWidth(50));
		assertEquals(30, HomeLeftColumn.statusBoxWidth(30));
	}

	@Test
	public void shortLabelsAreUnchanged() {
		assertEquals("Hub", HomeLeftColumn.statusText(WIDTH, "Hub", 72));
		assertEquals("Offline", HomeLeftColumn.statusText(WIDTH, "Offline", 72));
	}

	@Test
	public void longLabelGetsPixelAccurateEllipsis() {
		String label = PlayerStatus.offline(NOW - 136 * DAY).buttonLabel(NOW);
		assertEquals("Last online 136 days ago", label);
		int statusW = HomeLeftColumn.statusBoxWidth(80);
		int maxW = statusW - 8;
		String drawn = HomeLeftColumn.statusText(WIDTH, label, statusW);

		assertTrue(drawn.endsWith("..."));
		assertTrue(WIDTH.applyAsInt(drawn) <= maxW);
		String kept = drawn.substring(0, drawn.length() - 3);
		assertTrue(label.startsWith(kept));
		// One more character would overflow, so as much text as fits is shown.
		String longer = label.substring(0, kept.length() + 1).stripTrailing() + "...";
		assertTrue(WIDTH.applyAsInt(longer) > maxW || longer.equals(drawn));
	}

	@Test
	public void ellipsisDoesNotFollowASpace() {
		String drawn = HomeLeftColumn.statusText(WIDTH, "Last online 136 days ago", 62);
		assertTrue(drawn.endsWith("..."));
		assertTrue(!drawn.endsWith(" ..."));
	}

	@Test
	public void tooltipKeepsTheFullText() {
		PlayerStatus offline = PlayerStatus.offline(NOW - 136 * DAY);
		assertEquals("Last online 136 days ago", HomeTooltips.offlineLines(offline, NOW).get(0));

		PlayerStatus online = PlayerStatus.online("SKYBLOCK", "crystal_hollows", "");
		String location = online.buttonLabel(NOW);
		List<PvTooltip.Line> tip = HomeTooltips.status(online);
		boolean found = false;
		for (PvTooltip.Line line : tip) {
			for (PvTooltip.Span span : line.left()) {
				found |= location.equals(span.text());
			}
		}
		assertTrue("tooltip should contain " + location, found);
	}
}
