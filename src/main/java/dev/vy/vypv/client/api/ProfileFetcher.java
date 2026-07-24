package dev.vy.vypv.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.vypv.client.data.DungeonSnapshot;
import dev.vy.vypv.client.data.FormatUtil;
import dev.vy.vypv.client.data.Leveling;
import dev.vy.vypv.client.data.ProfileSnapshot;
import dev.vy.vypv.client.data.RepoData;
import dev.vy.vypv.client.dungeons.CataXpMath;
import dev.vy.vypv.client.dungeons.DungeonModifierScanner;
import dev.vy.vypv.client.dungeons.DungeonXpData;
import dev.vy.vypv.client.gui.ArmorStacks;
import dev.vy.vypv.client.networth.InventoryDecoder;
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

	private static final String[] DUNGEON_CLASSES = {
		"healer", "mage", "berserk", "archer", "tank"
	};

	private ProfileFetcher() {
	}

	public record LoadedProfile(
		ProfileSnapshot snapshot,
		DungeonSnapshot dungeons,
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
		DungeonXpData.ensureLoaded();
		if (!HypixelApiClient.canFetch()) {
			return CompletableFuture.completedFuture(new LoadedProfile(
				ProfileSnapshot.loading(playerName),
				DungeonSnapshot.empty(),
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
				CompletableFuture<Optional<JsonObject>> museumFut = HypixelApiClient.skyblockMuseum(id.uuid(), profileId);
				CompletableFuture<Optional<JsonObject>> electionFut = HypixelApiClient.skyblockElection();
				return museumFut.thenCombine(electionFut, (museumOpt, electionOpt) ->
					parse(id.name(), id.uuid(), root, museumOpt.orElse(null), electionOpt.orElse(null))
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
			DungeonSnapshot.empty(),
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

	private static LoadedProfile parse(String name, UUID uuid, JsonObject root, JsonObject museumRoot, JsonObject electionRoot) {
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

		float socialXp = Leveling.readSkillXp(member, "social");
		int socialCap = Leveling.skillCap("social", member);
		Leveling.Progress socialProgress = Leveling.getLevel(Leveling.skillTable("social"), socialXp, socialCap, false);
		ProfileSnapshot.SkillEntry social = new ProfileSnapshot.SkillEntry(
			"social",
			"Social",
			(int) Math.floor(socialProgress.level()),
			socialProgress.fill(),
			socialProgress.maxed(),
			socialProgress.skillHover("Social")
		);

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
			slayers,
			social
		);
		DungeonSnapshot dungeons = parseDungeons(member, museumRoot, electionRoot);
		return new LoadedProfile(snapshot, dungeons, senither, lily, networth, ArmorStacks.fromMember(member), null);
	}

	private static DungeonSnapshot parseDungeons(JsonObject member, JsonObject museumRoot, JsonObject electionRoot) {
		float cataXp = Leveling.readCatacombsXp(member);
		Leveling.Progress cata = Leveling.getLevel(RepoData.catacombsXp(), cataXp, 50, false);

		JsonObject dungeons = Leveling.obj(member.get("dungeons"));
		String selected = "";
		long secrets = 0L;
		if (dungeons != null) {
			if (dungeons.has("selected_dungeon_class") && dungeons.get("selected_dungeon_class").isJsonPrimitive()) {
				selected = dungeons.get("selected_dungeon_class").getAsString();
			}
			Float secretsF = Leveling.num(dungeons.get("secrets"));
			if (secretsF != null) {
				secrets = Math.round(secretsF);
			}
		}

		List<DungeonSnapshot.ClassEntry> classes = new ArrayList<>();
		float classSumCapped = 0F;
		float classSumOverflow = 0F;
		int maxedCount = 0;
		for (String classId : DUNGEON_CLASSES) {
			float classXp = Leveling.readClassXp(member, classId);
			Leveling.Progress progress = Leveling.getLevel(
				RepoData.catacombsXp(),
				classXp,
				50,
				false
			);
			int level = (int) Math.floor(progress.level());
			boolean selectedClass = classId.equalsIgnoreCase(selected);
			if (progress.maxed()) {
				maxedCount++;
				classSumCapped += 50F;
				classSumOverflow += progress.level();
			} else {
				classSumCapped += progress.level();
			}
			classes.add(new DungeonSnapshot.ClassEntry(
				classId,
				title(classId),
				level,
				classXp,
				progress.fill(),
				progress.maxed(),
				selectedClass,
				progress.skillHover(title(classId))
			));
		}

		float classAverage;
		float classAvgProgress;
		boolean classAvgMaxed;
		String classAvgHover;
		if (maxedCount == DUNGEON_CLASSES.length) {
			classAverage = classSumOverflow / DUNGEON_CLASSES.length;
			classAvgProgress = 1F;
			classAvgMaxed = true;
			classAvgHover = "Class Average " + FormatUtil.oneDecimal(classAverage) + " - MAX";
		} else {
			classAverage = classSumCapped / DUNGEON_CLASSES.length;
			classAvgProgress = Math.max(0F, Math.min(1F, classAverage / 50F));
			classAvgMaxed = false;
			classAvgHover = "Class Average " + FormatUtil.oneDecimal(classAverage) + " / 50";
		}

		DungeonSnapshot.ModeStats normal = parseMode(member, "catacombs", false);
		DungeonSnapshot.ModeStats master = parseMode(member, "master_catacombs", true);
		long allRuns = Math.max(1L, normal.totalRuns() + master.totalRuns());
		double secretsPerRun = secrets / (double) allRuns;

		DungeonModifierScanner.Mods mods = DungeonModifierScanner.Mods.none();
		try {
			mods = DungeonModifierScanner.scan(InventoryDecoder.parseCategories(member, museumRoot));
		} catch (Exception ignored) {
			// Inventory decode can fail on odd NBT; calc still works without gear mods.
		}

		double mayorFactor = CataXpMath.mayorXpFactor(electionRoot);
		String mayorName = CataXpMath.mayorName(electionRoot);

		java.util.Map<String, Double> essenceBonuses = DungeonModifierScanner.readEssenceClassBonuses(member);

		return new DungeonSnapshot(
			(int) Math.floor(cata.level()),
			cataXp,
			cata.fill(),
			cata.maxed(),
			cata.skillHover("Catacombs"),
			secrets,
			secretsPerRun,
			classAverage,
			classAvgProgress,
			classAvgMaxed,
			classAvgHover,
			classes,
			normal,
			master,
			mods.expertRing(),
			mods.hecatombLevel(),
			mods.scarfBonus(),
			essenceBonuses,
			mayorFactor,
			mayorName
		);
	}

	private static DungeonSnapshot.ModeStats parseMode(JsonObject member, String typeKey, boolean master) {
		JsonObject dungeons = Leveling.obj(member.get("dungeons"));
		JsonObject types = dungeons == null ? null : Leveling.obj(dungeons.get("dungeon_types"));
		JsonObject type = types == null ? null : Leveling.obj(types.get(typeKey));
		JsonObject completions = type == null ? null : Leveling.obj(type.get("tier_completions"));
		JsonObject milestones = type == null ? null : Leveling.obj(type.get("milestone_completions"));
		JsonObject mobsKilled = type == null ? null : Leveling.obj(type.get("mobs_killed"));
		JsonObject bestScore = type == null ? null : Leveling.obj(type.get("best_score"));
		JsonObject mostMobs = type == null ? null : Leveling.obj(type.get("most_mobs_killed"));
		JsonObject fastest = type == null ? null : Leveling.obj(type.get("fastest_time"));
		JsonObject fastestS = type == null ? null : Leveling.obj(type.get("fastest_time_s"));
		JsonObject sPlus = type == null ? null : Leveling.obj(type.get("fastest_time_s_plus"));
		JsonObject mostHealing = type == null ? null : Leveling.obj(type.get("most_healing"));

		List<DungeonSnapshot.FloorEntry> floors = new ArrayList<>();
		long total = 0L;
		int start = master ? 1 : 0;
		for (int floor = start; floor <= 7; floor++) {
			String key = String.valueOf(floor);
			long runs = readLong(completions, key);
			total += runs;
			String label = master ? "M" + floor : (floor == 0 ? "E" : "F" + floor);
			DamagePeak damage = bestDamage(type, key);
			floors.add(new DungeonSnapshot.FloorEntry(
				key,
				label,
				runs,
				readLong(milestones, key),
				readLong(mobsKilled, key),
				readLong(bestScore, key),
				readLong(mostMobs, key),
				readLong(fastest, key),
				readLong(fastestS, key),
				readLong(sPlus, key),
				readDouble(mostHealing, key),
				damage.classId(),
				damage.value()
			));
		}
		return new DungeonSnapshot.ModeStats(total, floors);
	}

	private record DamagePeak(String classId, double value) {
		static DamagePeak none() {
			return new DamagePeak(null, 0D);
		}
	}

	private static DamagePeak bestDamage(JsonObject type, String floorKey) {
		if (type == null) {
			return DamagePeak.none();
		}
		String bestClass = null;
		double best = 0D;
		for (String classId : new String[] {"mage", "berserk", "archer", "healer", "tank"}) {
			JsonObject map = Leveling.obj(type.get("most_damage_" + classId));
			double value = readDouble(map, floorKey);
			if (value > best) {
				best = value;
				bestClass = classId;
			}
		}
		return bestClass == null ? DamagePeak.none() : new DamagePeak(bestClass, best);
	}

	private static long readLong(JsonObject object, String key) {
		if (object == null || !object.has(key)) {
			return 0L;
		}
		Float num = Leveling.num(object.get(key));
		return num == null ? 0L : Math.round(num);
	}

	private static double readDouble(JsonObject object, String key) {
		if (object == null || !object.has(key)) {
			return 0D;
		}
		Float num = Leveling.num(object.get(key));
		return num == null ? 0D : num.doubleValue();
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
