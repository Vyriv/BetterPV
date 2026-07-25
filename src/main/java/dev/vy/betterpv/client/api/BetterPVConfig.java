package dev.vy.betterpv.client.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.BetterPV;
import net.fabricmc.loader.api.FabricLoader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Local gitignored config.
 * <p>
 * Hypixel access defaults to the Cloudflare worker proxy (no key in the published mod).
 * An optional {@code hypixelApiKey} enables direct Hypixel calls for local debugging.
 */
public final class BetterPVConfig {
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("betterpv.json");
	private static String hypixelApiKey = "";

	private BetterPVConfig() {
	}

	public static void load() {
		hypixelApiKey = "";
		try {
			if (Files.isRegularFile(CONFIG_PATH)) {
				try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
					JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
					if (root.has("hypixelApiKey") && root.get("hypixelApiKey").isJsonPrimitive()) {
						hypixelApiKey = root.get("hypixelApiKey").getAsString().trim();
					}
				}
			}
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to load betterpv.json", exception);
		}
		if (hypixelApiKey.isBlank()) {
			hypixelApiKey = readVyAddonsKey();
			if (!hypixelApiKey.isBlank()) {
				save();
				BetterPV.LOGGER.info("Loaded optional direct Hypixel API key from VyAddons (debug override)");
			}
		}
		HypixelApiClient.setApiKey(hypixelApiKey);
		if (HypixelApiClient.hasLocalApiKey()) {
			BetterPV.LOGGER.info("Hypixel: direct API key override enabled");
		} else {
			BetterPV.LOGGER.info("Hypixel: using Cloudflare worker proxy (no local API key)");
		}
	}

	public static String hypixelApiKey() {
		return hypixelApiKey;
	}

	public static boolean hasLocalApiKey() {
		return !hypixelApiKey.isBlank();
	}

	public static void setHypixelApiKey(String key) {
		hypixelApiKey = key == null ? "" : key.trim();
		HypixelApiClient.setApiKey(hypixelApiKey);
		save();
	}

	private static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			JsonObject root = new JsonObject();
			if (!hypixelApiKey.isBlank()) {
				root.addProperty("hypixelApiKey", hypixelApiKey);
			}
			Files.writeString(CONFIG_PATH, root.toString(), StandardCharsets.UTF_8);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Failed to save vypv.json", exception);
		}
	}

	private static String readVyAddonsKey() {
		List<Path> candidates = new ArrayList<>();
		candidates.add(FabricLoader.getInstance().getConfigDir().resolve("vyaddons.json"));
		Path userDir = Path.of(System.getProperty("user.dir", "."));
		candidates.add(userDir.resolve("run/config/vyaddons.json"));
		candidates.add(userDir.resolveSibling("VyAddons").resolve("run/config/vyaddons.json"));
		String appData = System.getenv("APPDATA");
		if (appData != null && !appData.isBlank()) {
			Path prism = Path.of(appData, "PrismLauncher", "instances");
			if (Files.isDirectory(prism)) {
				try (var stream = Files.walk(prism, 4)) {
					stream.filter(path -> path.getFileName().toString().equals("vyaddons.json"))
						.limit(8)
						.forEach(candidates::add);
				} catch (Exception ignored) {
				}
			}
		}
		for (Path path : candidates) {
			String key = readKeyFrom(path);
			if (!key.isBlank()) {
				return key;
			}
		}
		return "";
	}

	private static String readKeyFrom(Path path) {
		try {
			if (!Files.isRegularFile(path)) {
				return "";
			}
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("hypixelApiKey") && root.get("hypixelApiKey").isJsonPrimitive()) {
					return root.get("hypixelApiKey").getAsString().trim();
				}
			}
		} catch (Exception ignored) {
		}
		return "";
	}
}
