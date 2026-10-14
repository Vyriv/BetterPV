package dev.vy.betterpv.client.gui.home;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.PlayerStatus;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class HomeStatusTooltipTest {
	private static final long NOW = 1_790_000_000_000L;
	private static final long HOUR = 3_600_000L;

	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void offlineTooltipLeadsWithTheButtonText() {
		PlayerStatus status = PlayerStatus.offline(NOW - 3 * HOUR);
		List<String> lines = HomeTooltips.offlineLines(status, NOW);
		assertEquals(status.buttonLabel(NOW), lines.get(0));
		assertEquals("Last online 3 hours ago", lines.get(0));
		assertEquals(2, lines.size());
	}

	@Test
	public void offlineTooltipWithoutTimeSaysWhy() {
		List<String> unknown = HomeTooltips.offlineLines(PlayerStatus.offline(), NOW);
		assertEquals(List.of("Offline", "Hypixel did not share a last online time"), unknown);

		JsonObject hidden = new JsonObject();
		JsonObject settings = new JsonObject();
		settings.addProperty("apiSession", false);
		hidden.add("settings", settings);
		List<String> hiddenLines = HomeTooltips.offlineLines(PlayerStatus.offlineFromPlayer(hidden, NOW), NOW);
		assertEquals(List.of("Offline", "Online status hidden in Hypixel API settings"), hiddenLines);
	}
}
