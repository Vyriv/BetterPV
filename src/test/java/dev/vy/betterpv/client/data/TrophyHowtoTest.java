package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class TrophyHowtoTest {
	@Test
	public void obfuscatedTwoSkipsBaitHeader() {
		List<String> lore = List.of(
			"§8Fishing Bait",
			"§8Consumes on Cast",
			"",
			"§7Caught with §5§kObfuscated 1§r§7 Bait.",
			"",
			"§7Bring this to §aOdger §7at the top of the"
		);
		assertEquals("Caught with Obfuscated 1 Bait.", TrophyFishData.howtoFromLore(lore));
	}

	@Test
	public void fallsBackToFirstLine() {
		assertEquals("Lives in lava.", TrophyFishData.howtoFromLore(List.of("", "§7Lives in lava.", "§7Odger")));
		assertNull(TrophyFishData.howtoFromLore(List.of("", " ")));
	}
}
