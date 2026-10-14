package dev.vy.betterpv.client.gui.dungeons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public final class DungeonFloorTipTest {
	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void missingSPlusShowsNotAvailable() {
		List<String> text = text(DungeonPage.floorStatsTip("Floor 7",
			floor("7", "F7", 125_000L, 190_000L, 0L)));
		assertTrue(text.contains("Fastest Time: 2:05"));
		assertTrue(text.contains("Fastest Time S: 3:10"));
		assertTrue(text.contains("Fastest Time S+: N/A"));
		assertFalse(text.stream().anyMatch(line -> line.startsWith("Fastest Time S+: 0")));
	}

	@Test
	public void presentSPlusKeepsItsOwnTime() {
		List<String> text = text(DungeonPage.floorStatsTip("Master Mode 7",
			floor("7", "M7", 300_000L, 330_000L, 362_000L)));
		assertTrue(text.contains("Fastest Time: 5:00"));
		assertTrue(text.contains("Fastest Time S: 5:30"));
		assertTrue(text.contains("Fastest Time S+: 6:02"));
	}

	@Test
	public void eachGradeIsIndependent() {
		List<String> text = text(DungeonPage.floorStatsTip("Floor 1",
			floor("1", "F1", 0L, 0L, 61_000L)));
		assertTrue(text.contains("Fastest Time: N/A"));
		assertTrue(text.contains("Fastest Time S: N/A"));
		assertTrue(text.contains("Fastest Time S+: 1:01"));
		assertEquals(1, text.stream().filter(line -> line.startsWith("Fastest Time S+")).count());
	}

	@Test
	public void missingTimesKeepTheirGradeColour() {
		List<PvTooltip.Line> missing = DungeonPage.floorStatsTip("Floor 7", floor("7", "F7", 1L, 1L, 0L));
		List<PvTooltip.Line> present = DungeonPage.floorStatsTip("Floor 7", floor("7", "F7", 1L, 1L, 1L));
		PvTooltip.Line missingSPlus = line(missing, "Fastest Time S+");
		PvTooltip.Line presentSPlus = line(present, "Fastest Time S+");
		int sPlusColor = presentSPlus.spans().get(0).color();
		assertEquals(sPlusColor, missingSPlus.spans().get(0).color());
		assertEquals(sPlusColor, missingSPlus.spans().get(1).color());
		assertTrue(missingSPlus.spans().get(0).bold());
		assertFalse(missingSPlus.spans().get(1).bold());
		assertTrue(sPlusColor != line(present, "Fastest Time S:").spans().get(0).color());
	}

	private static PvTooltip.Line line(List<PvTooltip.Line> lines, String prefix) {
		return lines.stream()
			.filter(line -> line.spans().get(0).text().startsWith(prefix))
			.findFirst().orElseThrow();
	}

	private static DungeonSnapshot.FloorEntry floor(String id, String label, long fastest, long s, long sPlus) {
		return new DungeonSnapshot.FloorEntry(id, label, 10L, 10L, 100L, 300L, 50L, fastest, s, sPlus, 0D, null, 0D);
	}

	private static List<String> text(List<PvTooltip.Line> lines) {
		return lines.stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).collect(Collectors.joining()))
			.toList();
	}
}
