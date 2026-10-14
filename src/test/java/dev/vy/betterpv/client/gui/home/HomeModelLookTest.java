package dev.vy.betterpv.client.gui.home;

import static org.junit.Assert.assertEquals;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public class HomeModelLookTest {
	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void modelFollowsRealCursorWhileHoverIsMasked() {
		HomeLeftColumn column = new HomeLeftColumn();
		column.setModelLook(240, 130);
		assertEquals(240, column.modelLookX(-10_000));
		assertEquals(130, column.modelLookY(-10_000));
	}

	@Test
	public void modelUsesHoverMouseWhenNoLookIsSet() {
		HomeLeftColumn column = new HomeLeftColumn();
		assertEquals(55, column.modelLookX(55));
		assertEquals(66, column.modelLookY(66));
	}
}
