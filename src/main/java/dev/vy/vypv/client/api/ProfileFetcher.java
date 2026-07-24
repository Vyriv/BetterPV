package dev.vy.vypv.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.vypv.client.data.FormatUtil;
import dev.vy.vypv.client.data.Leveling;
import dev.vy.vypv.client.data.ProfileSnapshot;
import dev.vy.vypv.client.data.RepoData;
import dev.vy.vypv.client.gui.ArmorStacks;
import dev.vy.vypv.client.networth.NetworthBreakdown;
import dev.vy.vypv.client.networth.NetworthCalculator;
import dev.vy.vypv.client.price.ItemPricer;
import dev.vy.vypv.client.weight.WeightBreakdown;
import dev.vy.vypv.client.weight.WeightCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.item.ItemStack;

public final class ProfileFetcher {
	private static final String[] HOME_SKILLS = {
		"combat", "foraging", "farming", "enchanting", "mining", "alchemy", "fishing", "carpentry", "taming", "hunting"
	};
	private static final String[][] HOME_SLAYERS = {
		{"zombie", "Revenant"},
		{"enderman", "Enderman"},
		{"spider", "Tarantula"},
		{"blaze", "Blaze"},
		{"wolf", "Sven"},
		{"vampire", "Vampire"}
	};

	private ProfileFetcher() {
	}

	public record LoadedProfile(
		ProfileSnapshot snapshot,
		WeightBreakdown senither,
		WeightBreakdown lily,
		NetworthBreakdown networth,
		ItemStack[] armor,
		String error
	) {
		public boolean ok() {
			return error == null || error.isBlank();
		}
	}

	public static CompletableFuture<LoadedProfile> fetch(String playerName) {
		RepoData.ensureLoaded();
		if (!HypixelApiClient.canFetch()) {
			return CompletableFuture.completedFuture(new LoadedProfile(
				ProfileSnapshot.loading(playerName),
				WeightBreakdown.empty(dev.vy.vypv.client.weight.WeightSystem.SENITHER),
				WeightBreakdown.empty(dev.vy.vypv.client.weight.WeightSystem.LILY),
				NetworthBreakdown.empty("API unavailable"),
				emptyArmor(),
				"API unavailable"
			));
		}
		return HypixelApiClient.resolveUuid(playerName).thenCompose(uuidOpt -> {
			if (uuidOpt.isEmpty()) {
				return CompletableFuture.completedFuture(fail(playerName, "Player not found"));
			}
			HypixelApiClient.UuidName id = uuidOpt.get();
			return HypixelApiClient.skyblockProfiles(id.uuid()).thenCompose(profilesOpt -> {
				if (profilesOpt.isEmpty()) {
					return CompletableFuture.completedFuture(fail(id.name(), "Profiles request failed"));
				}
				JsonObject root = profilesOpt.get();
				JsonObject best = selectedProfile(root);
				String profileId = best != null && best.has("profile_id") ? best.get("profile_id").getAsString() : null;
				return HypixelApiClient.skyblockMuseum(id.uuid(), profileId).thenApply(museumOpt ->
					parse(id.name(), id.uuid(), root, museumOpt.orElse(null))
				);
			});
		});
	}

	private static JsonObject selectedProfile(JsonObject root) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		if (profiles == null) {
			return null;
		}
		JsonObject best = null;
		for (JsonElement element : profiles) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject profile = element.getAsJsonObject();
			boolean selected = profile.has("selected") && profile.get("selected").getAsBoolean();
			if (selected || best == null) {
				best = profile;
				if (selected) {
					break;
				}
			}
		}
		return best;
	}

	private static LoadedProfile fail(String name, String error) {
		return new LoadedProfile(
			ProfileSnapshot.loading(name),
			WeightBreakdown.empty(dev.vy.vypv.client.weight.WeightSystem.SENITHER),
			WeightBreakdown.empty(dev.vy.vypv.client.weight.WeightSystem.LILY),
			NetworthBreakdown.empty(error),
			emptyArmor(),
			error
		);
	}

	private static ItemStack[] emptyArmor() {
		return new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
	}

	private static LoadedProfile parse(String name, UUID uuid, JsonObject root, JsonObject museumRoot) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		if (profiles == null || profiles.isEmpty()) {
			return fail(name, "No SkyBlock profiles");
		}
		JsonObject best = null;
		String cuteName = "Unknown";
		String undashed = HypixelApiClient.undashed(uuid);
		String profileId = null;
		for (JsonElement element : profiles) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject profile = element.getAsJsonObject();
			boolean selected = profile.has("selected") && profile.get("selected").getAsBoolean();
			if (selected || best == null) {
				best = profile;
				cuteName = profile.has("cute_name") ? profile.get("cute_name").getAsString() : cuteName;
				profileId = profile.has("profile_id") ? profile.get("profile_id").getAsString() : profileId;
				if (selected) {
					break;
				}
			}
		}
		if (best == null) {
			return fail(name, "No usable profile");
		}
		JsonObject members = best.has("members") && best.get("members").isJsonObject()
			? best.getAsJsonObject("members")
			: null;
		JsonObject member = findMember(members, undashed);
		if (member == null) {
			return fail(name, "Member data missing");
		}

		Map<String, Leveling.Progress> weightLevels = WeightCalculator.buildLevels(member);
		WeightBreakdown senither = WeightCalculator.senither(member, weightLevels);
		WeightBreakdown lily = WeightCalculator.lily(member, weightLevels);

		ItemPricer.awaitReady(12_000L);
		JsonObject museumMember = findMuseumMember(museumRoot, profileId, undashed);
		NetworthBreakdown networth = NetworthCalculator.calculate(member, best, museumMember);

		List<ProfileSnapshot.SkillEntry> skills = new ArrayList<>();
		for (String skill : HOME_SKILLS) {
			float xp = Leveling.readSkillXp(member, skill);
			int cap = Leveling.skillCap(skill, member);
			Leveling.Progress progress = Leveling.getLevel(Leveling.skillTable(skill), xp, cap, false);
			skills.add(new ProfileSnapshot.SkillEntry(
				skill,
				title(skill),
				(int) Math.floor(progress.level()),
				progress.fill(),
				progress.maxed(),
				progress.skillHover(title(skill))
			));
		}

		List<ProfileSnapshot.SlayerEntry> slayers = new ArrayList<>();
		for (String[] pair : HOME_SLAYERS) {
			float xp = Leveling.readSlayerXp(member, pair[0]);
			Leveling.Progress progress = Leveling.getLevel(RepoData.slayerXp(pair[0]), xp, 9, true);
			slayers.add(new ProfileSnapshot.SlayerEntry(
				pair[0],
				pair[1],
				(int) Math.floor(progress.level()),
				progress.fill(),
				progress.maxed(),
				progress.slayerHover(pair[1])
			));
		}

		int sbLevel = 0;
		int sbXp = 0;
		JsonObject leveling = Leveling.obj(member.get("leveling"));
		if (leveling != null) {
			Float experience = Leveling.num(leveling.get("experience"));
			if (experience != null) {
				sbLevel = (int) Math.floor(experience / 100F);
				sbXp = Math.round(experience % 100F);
			}
		}

		String nwText = networth.total() > 0
			? FormatUtil.shortCoins(networth.total())
			: (networth.note().isBlank() ? "—" : "—");

		ProfileSnapshot snapshot = new ProfileSnapshot(
			name,
			uuid,
			cuteName,
			sbLevel,
			sbXp,
			FormatUtil.weight(senither.total()),
			nwText,
			skills,
			slayers
		);
		return new LoadedProfile(snapshot, senither, lily, networth, ArmorStacks.fromMember(member), null);
	}

	private static JsonObject findMuseumMember(JsonObject museumRoot, String profileId, String undashed) {
		if (museumRoot == null) {
			return null;
		}
		// Modern museum: { members: { uuid: {...} } } or per-profile nesting
		JsonObject members = Leveling.obj(museumRoot.get("members"));
		if (members != null) {
			JsonObject direct = findMember(members, undashed);
			if (direct != null) {
				return direct;
			}
		}
		if (profileId != null && museumRoot.has(profileId) && museumRoot.get(profileId).isJsonObject()) {
			JsonObject profileMuseum = museumRoot.getAsJsonObject(profileId);
			JsonObject nestedMembers = Leveling.obj(profileMuseum.get("members"));
			if (nestedMembers != null) {
				return findMember(nestedMembers, undashed);
			}
			return profileMuseum;
		}
		return findMember(museumRoot, undashed);
	}

	private static JsonObject findMember(JsonObject members, String undashed) {
		if (members == null) {
			return null;
		}
		if (members.has(undashed) && members.get(undashed).isJsonObject()) {
			return members.getAsJsonObject(undashed);
		}
		for (var entry : members.entrySet()) {
			if (entry.getKey() != null && entry.getKey().replace("-", "").equalsIgnoreCase(undashed)
				&& entry.getValue().isJsonObject()) {
				return entry.getValue().getAsJsonObject();
			}
		}
		return null;
	}

	private static String title(String id) {
		return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
	}
}
