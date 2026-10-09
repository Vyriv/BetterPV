package dev.vy.betterpv.client.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.BetterPV;
import dev.vy.betterpv.client.api.ProfileFetcher;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Publishes BetterPV's selected local profile through SkyBlockAPI's public IMC channel. */
public final class PvProfileImcPublisher {
	static final String CHANNEL = "skyblockapi:imc/pv-profile";

	private PvProfileImcPublisher() {
	}

	public static void publish(ProfileFetcher.LoadedProfile loaded) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null || loaded == null || loaded.snapshot() == null) {
			return;
		}
		JsonObject profile = findPublishableProfile(
			loaded.profilesRoot(),
			loaded.profileId(),
			loaded.snapshot().playerUuid(),
			client.player.getUUID()
		);
		if (profile == null) {
			return;
		}

		List<Consumer<JsonObject>> listeners;
		try {
			listeners = loadListeners();
		} catch (RuntimeException | LinkageError error) {
			BetterPV.LOGGER.warn("Failed to load PV profile compatibility listeners", error);
			return;
		}
		int delivered = dispatch(profile, listeners);
		if (delivered > 0) {
			BetterPV.LOGGER.info("Published selected local profile to {} PV compatibility listener(s)", delivered);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static List<Consumer<JsonObject>> loadListeners() {
		return (List) FabricLoader.getInstance().getEntrypoints(CHANNEL, Consumer.class);
	}

	static JsonObject findPublishableProfile(
		JsonObject root,
		String profileId,
		UUID viewedUuid,
		UUID localUuid
	) {
		if (root == null || profileId == null || viewedUuid == null || !viewedUuid.equals(localUuid)) {
			return null;
		}
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		if (profiles == null) {
			return null;
		}
		String localMemberId = localUuid.toString().replace("-", "");
		for (JsonElement element : profiles) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject candidate = element.getAsJsonObject();
			if (!profileId.equals(string(candidate, "profile_id")) || !bool(candidate, "selected")) {
				continue;
			}
			JsonObject members = candidate.has("members") && candidate.get("members").isJsonObject()
				? candidate.getAsJsonObject("members")
				: null;
			return members != null && members.has(localMemberId) ? candidate : null;
		}
		return null;
	}

	static int dispatch(JsonObject profile, List<Consumer<JsonObject>> listeners) {
		if (profile == null || listeners == null || listeners.isEmpty()) {
			return 0;
		}
		int delivered = 0;
		for (Consumer<JsonObject> listener : listeners) {
			if (listener == null) {
				continue;
			}
			try {
				listener.accept(profile.deepCopy());
				delivered++;
			} catch (RuntimeException | LinkageError error) {
				BetterPV.LOGGER.warn("PV profile compatibility listener failed", error);
			}
		}
		return delivered;
	}

	private static String string(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive()
			? object.get(key).getAsString()
			: null;
	}

	private static boolean bool(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
	}
}
