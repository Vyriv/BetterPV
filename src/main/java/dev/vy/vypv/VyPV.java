package dev.vy.vypv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.api.ModInitializer;

public final class VyPV implements ModInitializer {
	public static final String MOD_ID = "vypv";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("VyPV loaded");
	}
}
