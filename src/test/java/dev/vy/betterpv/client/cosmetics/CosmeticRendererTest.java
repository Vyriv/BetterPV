package dev.vy.betterpv.client.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Set;
import org.junit.Test;

public class CosmeticRendererTest {
	@Test
	public void preservesAutoPriorityOffAndExplicitOwnership() {
		assertEquals("betterpv", CosmeticRenderer.resolveOwner("auto", Set.of("betterpv", "drt", "skylist")::contains));
		assertEquals("drt", CosmeticRenderer.resolveOwner("auto", Set.of("drt", "skylist")::contains));
		assertEquals("skylist", CosmeticRenderer.resolveOwner("auto", Set.of("skylist")::contains));
		assertNull(CosmeticRenderer.resolveOwner("auto", ignored -> false));
		assertNull(CosmeticRenderer.resolveOwner("off", ignored -> true));
		assertEquals("betterpv", CosmeticRenderer.resolveOwner("betterpv", ignored -> false));
	}

	@Test
	public void preservesCommandAliases() {
		assertEquals("auto", CosmeticRenderer.normalize(" AUTO "));
		assertEquals("betterpv", CosmeticRenderer.normalize("pv"));
		assertEquals("off", CosmeticRenderer.normalize("none"));
		assertNull(CosmeticRenderer.normalize("unknown"));
	}
}
