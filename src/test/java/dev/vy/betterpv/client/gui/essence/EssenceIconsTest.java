package dev.vy.betterpv.client.gui.essence;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;

public class EssenceIconsTest {
	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void placeholderPetIconIsNotTreatedAsResolved() {
		assertFalse(EssenceIcons.isResolved(Items.PAPER));
		assertFalse(EssenceIcons.isResolved(null));
		assertTrue(EssenceIcons.isResolved(Items.PLAYER_HEAD));
	}
}
