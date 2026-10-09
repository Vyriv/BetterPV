package dev.vy.betterpv.client.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.BetterPV;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

public final class BetterPVConfig {
	private static final String FILE_NAME = "betterpv.json";
	private static final String PROFILE_SCALE_KEY = "profileViewerScalePercent";
	private static volatile int profileViewerScalePercent = 100;
	private static JsonObject document = new JsonObject();

	private BetterPVConfig() {
	}

	public static synchronized void load() {
		Path path = configPath();
		if (Files.isRegularFile(path)) {
			try {
				JsonObject loaded = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
				document = loaded;
				if (loaded.has(PROFILE_SCALE_KEY) && loaded.get(PROFILE_SCALE_KEY).isJsonPrimitive()) {
					profileViewerScalePercent = normalizeProfileScale(loaded.get(PROFILE_SCALE_KEY).getAsInt());
				}
			} catch (Exception exception) {
				BetterPV.LOGGER.warn("Could not load BetterPV config", exception);
				document = new JsonObject();
				profileViewerScalePercent = 100;
			}
		}
		BetterPV.LOGGER.info("Hypixel: api.vyriv.dev via Minecraft session JWT");
	}

	public static int profileViewerScalePercent() {
		return profileViewerScalePercent;
	}

	public static synchronized void setProfileViewerScalePercent(int percent) {
		int normalized = normalizeProfileScale(percent);
		if (normalized == profileViewerScalePercent) {
			return;
		}
		profileViewerScalePercent = normalized;
		document.addProperty(PROFILE_SCALE_KEY, normalized);
		save();
	}

	static int normalizeProfileScale(int percent) {
		int clamped = Math.max(75, Math.min(150, percent));
		return Math.round(clamped / 5.0F) * 5;
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	private static void save() {
		Path path = configPath();
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(temporary, document.toString(), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException ignored) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			BetterPV.LOGGER.warn("Could not save BetterPV config", exception);
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException ignored) {
			}
		}
	}
}
