package dev.vy.betterpv.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.AuctionSnapshot;
import dev.vy.betterpv.client.data.BestiarySnapshot;
import dev.vy.betterpv.client.data.ColeWeight;
import dev.vy.betterpv.client.data.CollectionSnapshot;
import dev.vy.betterpv.client.data.CrimsonSnapshot;
import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.EventsSnapshot;
import dev.vy.betterpv.client.data.FishingSnapshot;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.GardenData;
import dev.vy.betterpv.client.data.GardenSnapshot;
import dev.vy.betterpv.client.data.MiningHotmData;
import dev.vy.betterpv.client.data.MiningSnapshot;
import dev.vy.betterpv.client.data.MiscStatsSnapshot;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.data.Leveling;
import dev.vy.betterpv.client.data.PetSnapshot;
import dev.vy.betterpv.client.data.PlayerStatsCalculator;
import dev.vy.betterpv.client.data.PlayerStatsSnapshot;
import dev.vy.betterpv.client.data.ProfileSnapshot;
import dev.vy.betterpv.client.data.RepoData;
import dev.vy.betterpv.client.data.RiftSnapshot;
import dev.vy.betterpv.client.dungeons.CataXpMath;
import dev.vy.betterpv.client.dungeons.DungeonModifierScanner;
import dev.vy.betterpv.client.dungeons.DungeonXpData;
import dev.vy.betterpv.client.dungeons.EssenceShopData;
import dev.vy.betterpv.client.gui.ArmorStacks;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import dev.vy.betterpv.client.networth.NetworthBreakdown;
import dev.vy.betterpv.client.networth.NetworthCalculator;
import dev.vy.betterpv.client.networth.NetworthMode;
import dev.vy.betterpv.client.price.ItemPricer;
import dev.vy.betterpv.client.weight.WeightBreakdown;
import dev.vy.betterpv.client.weight.WeightCalculator;
import dev.vy.betterpv.BetterPV;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

public final class ProfileFetcher {
	/** Match worker profiles cache TTL (5 minutes). */
	private static final long CACHE_TTL_MS = 5L * 60L * 1000L;
	private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

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

	/**
	 * @param gameMode raw {@code profiles[].game_mode} (blank when normal / absent)
	 * @param createdAtMs {@code profiles[].created_at} epoch ms, or {@code 0} when absent
	 */
	public record ProfileChoice(
		String cuteName,
		String profileId,
		boolean selected,
		String gameMode,
		long createdAtMs
	) {
		public ProfileChoice {
			cuteName = cuteName == null || cuteName.isBlank() ? "Unknown" : cuteName;
			profileId = profileId == null ? "" : profileId;
			gameMode = gameMode == null ? "" : gameMode.trim();
			createdAtMs = Math.max(0L, createdAtMs);
		}

		/** Display label for non-normal modes only (Ironman / Stranded / Bingo / …). */
		public String gameModeLabel() {
			if (gameMode.isBlank()) {
				return "";
			}
			return switch (gameMode.toLowerCase(java.util.Locale.ROOT)) {
				case "ironman" -> "Ironman";
				case "stranded" -> "Stranded";
				case "bingo" -> "Bingo";
				default -> {
					String lower = gameMode.toLowerCase(java.util.Locale.ROOT);
					yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
				}
			};
		}
	}

	public record LoadedProfile(
		ProfileSnapshot snapshot,
		DungeonSnapshot dungeons,
		InventorySnapshot inventories,
		PetSnapshot pets,
		AuctionSnapshot auctions,
		CollectionSnapshot collections,
		GardenSnapshot garden,
		MiningSnapshot mining,
		ForagingSnapshot foraging,
		FishingSnapshot fishing,
		CrimsonSnapshot crimson,
		RiftSnapshot rift,
		BestiarySnapshot bestiary,
		EventsSnapshot events,
		MiscStatsSnapshot misc,
		JsonObject museumMember,
		String profileId,
		JsonObject profilesRoot,
		List<ProfileChoice> profiles,
		WeightBreakdown senither,
		WeightBreakdown lily,
		NetworthBreakdown networthNormal,
		NetworthBreakdown networthNonCosmetic,
		NetworthBreakdown networthUnsoulbound,
		NetworthBreakdown networthUnsoulboundNonCosmetic,
		ItemStack[] armor,
		PlayerStatsSnapshot playerStats,
		String error
	) {
		public LoadedProfile {
			profiles = profiles == null ? List.of() : List.copyOf(profiles);
		}

		public boolean ok() {
			return error == null || error.isBlank();
		}

		public NetworthBreakdown networth(NetworthMode mode) {
			if (mode == null) {
				return networthNormal;
			}
			return switch (mode) {
				case NORMAL -> networthNormal;
				case NON_COSMETIC -> networthNonCosmetic;
				case UNSOULBOUND -> networthUnsoulbound;
				case UNSOULBOUND_NON_COSMETIC -> networthUnsoulboundNonCosmetic;
			};
		}
	}

	public static CompletableFuture<LoadedProfile> fetch(String playerName) {
		return fetch(playerName, null);
	}

	/**
	 * Loads the home/core profile first, then publishes a fully enriched profile through {@code onUpdate}.
	 */
	public static CompletableFuture<LoadedProfile> fetch(String playerName, Consumer<LoadedProfile> onUpdate) {
		RepoData.ensureLoaded();
		DungeonXpData.ensureLoaded();
		GardenData.ensureLoaded();
		MiningHotmData.ensureLoaded();
		if (!HypixelApiClient.canFetch()) {
			return CompletableFuture.completedFuture(new LoadedProfile(
				ProfileSnapshot.loading(playerName),
				DungeonSnapshot.empty(),
				InventorySnapshot.empty(),
				PetSnapshot.empty(),
				AuctionSnapshot.empty(),
				CollectionSnapshot.empty(),
				GardenSnapshot.empty(),
				MiningSnapshot.empty(),
				ForagingSnapshot.empty(),
				FishingSnapshot.empty(),
				CrimsonSnapshot.empty(),
				RiftSnapshot.empty(),
				BestiarySnapshot.empty(),
				EventsSnapshot.empty(),
				MiscStatsSnapshot.empty(),
				null,
				null,
				null,
				List.of(),
				WeightBreakdown.empty(dev.vy.betterpv.client.weight.WeightSystem.SENITHER),
				WeightBreakdown.empty(dev.vy.betterpv.client.weight.WeightSystem.LILY),
				NetworthBreakdown.empty("API unavailable"),
				NetworthBreakdown.empty("API unavailable"),
				NetworthBreakdown.empty("API unavailable"),
				NetworthBreakdown.empty("API unavailable"),
				emptyArmor(),
				PlayerStatsSnapshot.empty(),
				"API unavailable"
			));
		}
		String cleaned = playerName == null ? "" : playerName.trim();
		LoadedProfile cached = getCached(nameKey(cleaned));
		if (cached != null) {
			BetterPV.LOGGER.info("Profile cache hit for {}", cleaned);
			return CompletableFuture.completedFuture(cached);
		}
		return HypixelApiClient.resolveUuid(cleaned).thenCompose(uuidOpt -> {
			if (uuidOpt.isEmpty()) {
				return CompletableFuture.completedFuture(fail(cleaned, "Player not found"));
			}
			HypixelApiClient.UuidName id = uuidOpt.get();
			LoadedProfile byUuid = getCached(uuidKey(id.uuid()));
			if (byUuid != null) {
				BetterPV.LOGGER.info("Profile cache hit for {} ({})", id.name(), id.uuid());
				putCache(nameKey(cleaned), byUuid);
				putCache(nameKey(id.name()), byUuid);
				return CompletableFuture.completedFuture(byUuid);
			}
			return HypixelApiClient.skyblockProfiles(id.uuid()).thenCompose(profilesOpt -> {
				if (profilesOpt.isEmpty()) {
					return CompletableFuture.completedFuture(fail(id.name(), "Profiles request failed"));
				}
				JsonObject root = profilesOpt.get();
				JsonObject best = selectedProfile(root);
				String profileId = best != null && best.has("profile_id") ? best.get("profile_id").getAsString() : null;
				CompletableFuture<Optional<JsonObject>> museumFut = HypixelApiClient.skyblockMuseum(id.uuid(), profileId);
				CompletableFuture<Optional<JsonObject>> electionFut = HypixelApiClient.skyblockElection();
				CompletableFuture<Optional<JsonObject>> auctionFut = HypixelApiClient.skyblockAuction(id.uuid());
				CompletableFuture<Optional<JsonArray>> soldFut = CoflnetApiClient.playerAuctions(id.uuid(), 0);
				CompletableFuture<Optional<JsonArray>> bidsFut = CoflnetApiClient.playerBids(id.uuid(), 0);

				CompletableFuture<LoadedProfile> coreFuture = CompletableFuture.supplyAsync(
					() -> parseHomeCore(id.name(), id.uuid(), root),
					HypixelApiClient.parseExecutor()
				);
				coreFuture.thenCompose(core -> CompletableFuture.allOf(museumFut, electionFut, auctionFut, soldFut, bidsFut)
					.thenApplyAsync(ignored -> {
						LoadedProfile loaded = parse(
							id.name(),
							id.uuid(),
							root,
							museumFut.join().orElse(null),
							electionFut.join().orElse(null),
							AuctionSnapshot.build(
								id.uuid(),
								auctionFut.join().orElse(null),
								soldFut.join().orElse(null),
								bidsFut.join().orElse(null)
							)
						);
						if (loaded.ok()) {
							putCache(uuidKey(id.uuid()), loaded);
							putCache(nameKey(id.name()), loaded);
							putCache(nameKey(cleaned), loaded);
						}
						return loaded;
					}, HypixelApiClient.parseExecutor())
				).whenComplete((loaded, error) -> {
					if (error != null) {
						BetterPV.LOGGER.warn("Background profile enrichment failed for {}", id.name(), error);
					} else if (loaded != null && onUpdate != null) {
						onUpdate.accept(loaded);
					}
				});
				return coreFuture;
			});
		});
	}

	private static String nameKey(String name) {
		return "n:" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
	}

	private static String uuidKey(UUID uuid) {
		return "u:" + HypixelApiClient.undashed(uuid);
	}

	private static LoadedProfile getCached(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		CacheEntry entry = CACHE.get(key);
		if (entry == null) {
			return null;
		}
		if (!entry.fresh()) {
			CACHE.remove(key, entry);
			return null;
		}
		return entry.profile();
	}

	private static void putCache(String key, LoadedProfile profile) {
		if (key == null || key.isBlank() || profile == null || !profile.ok()) {
			return;
		}
		CACHE.put(key, new CacheEntry(profile, System.currentTimeMillis() + CACHE_TTL_MS));
	}

	private record CacheEntry(LoadedProfile profile, long expiresAtMs) {
		boolean fresh() {
			return System.currentTimeMillis() < this.expiresAtMs;
		}
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

	public static LoadedProfile failed(String name, String error) {
		return new LoadedProfile(
			ProfileSnapshot.loading(name),
			DungeonSnapshot.empty(),
			InventorySnapshot.empty(),
			PetSnapshot.empty(),
			AuctionSnapshot.empty(),
			CollectionSnapshot.empty(),
			GardenSnapshot.empty(),
			MiningSnapshot.empty(),
			ForagingSnapshot.empty(),
			FishingSnapshot.empty(),
			CrimsonSnapshot.empty(),
			RiftSnapshot.empty(),
			BestiarySnapshot.empty(),
			EventsSnapshot.empty(),
			MiscStatsSnapshot.empty(),
			null,
			null,
			null,
			List.of(),
			WeightBreakdown.empty(dev.vy.betterpv.client.weight.WeightSystem.SENITHER),
			WeightBreakdown.empty(dev.vy.betterpv.client.weight.WeightSystem.LILY),
			NetworthBreakdown.empty(error),
			NetworthBreakdown.empty(error),
			NetworthBreakdown.empty(error),
			NetworthBreakdown.empty(error),
			emptyArmor(),
			PlayerStatsSnapshot.empty(),
			error
		);
	}

	private static LoadedProfile fail(String name, String error) {
		return failed(name, error);
	}

	private static ItemStack[] emptyArmor() {
		return new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
	}

	private static LoadedProfile parse(
		String name,
		UUID uuid,
		JsonObject root,
		JsonObject museumRoot,
		JsonObject electionRoot,
		AuctionSnapshot auctions
	) {
		try {
			return parseUnsafe(name, uuid, root, museumRoot, electionRoot, auctions, true, null);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Profile parse failed for {}", name, exception);
			return fail(name, exception.getMessage() == null ? "Parse failed" : exception.getMessage());
		}
	}

	public static LoadedProfile parseByProfileId(String name, UUID uuid, JsonObject root, String profileId) {
		if (root == null || profileId == null || profileId.isBlank()) {
			return fail(name, "Missing profile");
		}
		try {
			return parseUnsafe(name, uuid, root, null, null, AuctionSnapshot.empty(), true, profileId);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Profile re-parse failed for {} ({})", name, profileId, exception);
			return fail(name, exception.getMessage() == null ? "Parse failed" : exception.getMessage());
		}
	}

	private static LoadedProfile parseHomeCore(String name, UUID uuid, JsonObject root) {
		try {
			return parseUnsafe(
				name,
				uuid,
				root,
				null,
				null,
				AuctionSnapshot.empty(),
				false,
				null
			);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Core profile parse failed for {}", name, exception);
			return fail(name, exception.getMessage() == null ? "Parse failed" : exception.getMessage());
		}
	}

	private static LoadedProfile parseUnsafe(
		String name,
		UUID uuid,
		JsonObject root,
		JsonObject museumRoot,
		JsonObject electionRoot,
		AuctionSnapshot auctions,
		boolean includeNetworth,
		String preferredProfileId
	) {
		JsonArray profiles = root.has("profiles") && root.get("profiles").isJsonArray()
			? root.getAsJsonArray("profiles")
			: null;
		if (profiles == null || profiles.isEmpty()) {
			return fail(name, "No SkyBlock profiles");
		}
		List<ProfileChoice> choices;
		JsonObject best = pickProfile(profiles, preferredProfileId);
		String cuteName = "Unknown";
		String undashed = HypixelApiClient.undashed(uuid);
		String profileId = null;
		if (best != null) {
			cuteName = best.has("cute_name") ? best.get("cute_name").getAsString() : cuteName;
			profileId = best.has("profile_id") ? best.get("profile_id").getAsString() : profileId;
		}
		choices = listProfileChoices(profiles, profileId);
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

		if (includeNetworth && !ItemPricer.isReady()) {
			ItemPricer.awaitReady(12_000L);
		}
		JsonObject museumMember = includeNetworth ? findMuseumMember(museumRoot, profileId, undashed) : null;
		BetterPV.LOGGER.info("Parsing profile {} ({})", name, cuteName);
		Map<String, List<InventoryDecoder.Stack>> inventoryCategories =
			InventoryDecoder.parseCategories(member, museumMember);
		NetworthBreakdown networthNormal = includeNetworth
			? NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, NetworthMode.NORMAL)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown networthNonCosmetic = includeNetworth
			? NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, NetworthMode.NON_COSMETIC)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown networthUnsoulbound = includeNetworth
			? NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, NetworthMode.UNSOULBOUND)
			: NetworthBreakdown.empty("Loading networth");
		NetworthBreakdown networthUnsoulboundNonCosmetic = includeNetworth
			? NetworthCalculator.calculate(member, best, museumMember, inventoryCategories, NetworthMode.UNSOULBOUND_NON_COSMETIC)
			: NetworthBreakdown.empty("Loading networth");

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
				progress.skillHover(title(skill)),
				progress.skillHoverLines(title(skill))
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
			socialProgress.skillHover("Social"),
			socialProgress.skillHoverLines("Social")
		);

		List<ProfileSnapshot.SlayerEntry> slayers = new ArrayList<>();
		for (String[] pair : HOME_SLAYERS) {
			float xp = Leveling.readSlayerXp(member, pair[0]);
			JsonArray slayerTable = RepoData.slayerXp(pair[0]);
			int slayerCap = slayerTable == null || slayerTable.isEmpty() ? 9 : slayerTable.size();
			Leveling.Progress progress = Leveling.getLevel(slayerTable, xp, slayerCap, true);
			int[] kills = Leveling.readSlayerBossKills(member, pair[0]);
			List<Integer> killList = new ArrayList<>(kills.length);
			for (int kill : kills) {
				killList.add(kill);
			}
			slayers.add(new ProfileSnapshot.SlayerEntry(
				pair[0],
				pair[1],
				(int) Math.floor(progress.level()),
				progress.fill(),
				progress.maxed(),
				progress.slayerHoverWithKills(pair[1], pair[0], kills),
				killList,
				progress.slayerHoverLinesWithKills(pair[1], pair[0], kills)
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

		String nwText = !includeNetworth
			? "…"
			: networthNormal.total() > 0
			? FormatUtil.shortCoins(networthNormal.total())
			: "-";

		double purseCoins = NetworthCalculator.purse(member);
		double bankCoins = NetworthCalculator.bank(best, member);
		List<ProfileSnapshot.BankTransaction> bankTransactions = parseBankTransactions(best);

		ProfileSnapshot snapshot = new ProfileSnapshot(
			name,
			uuid,
			cuteName,
			sbLevel,
			sbXp,
			FormatUtil.weight(senither.total()),
			nwText,
			purseCoins,
			bankCoins,
			bankTransactions,
			skills,
			slayers,
			social
		);
		DungeonSnapshot dungeons = parseDungeons(member, museumMember, electionRoot, inventoryCategories);
		InventorySnapshot inventories;
		try {
			inventories = InventoryDecoder.parseUi(member);
			dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory.warmAsync(inventories);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Inventory decode failed for {}", name, exception);
			inventories = InventorySnapshot.empty();
		}
		PetSnapshot pets;
		try {
			pets = PetSnapshot.fromMember(member);
			dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory.warmPetsAsync(pets);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Pets decode failed for {}", name, exception);
			pets = PetSnapshot.empty();
		}
		CollectionSnapshot collections;
		try {
			collections = CollectionSnapshot.fromProfile(members, uuid, name);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Collections decode failed for {}", name, exception);
			collections = CollectionSnapshot.empty();
		}
		GardenSnapshot garden;
		try {
			garden = GardenSnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Garden member parse failed for {}", name, exception);
			garden = GardenSnapshot.empty();
		}
		MiningSnapshot mining;
		try {
			mining = MiningSnapshot.fromMember(member);
			mining = mining.withColeWeight(ColeWeight.calculate(mining, collections, member));
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Mining member parse failed for {}", name, exception);
			mining = MiningSnapshot.empty();
		}
		ForagingSnapshot foraging;
		try {
			foraging = ForagingSnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Foraging member parse failed for {}", name, exception);
			foraging = ForagingSnapshot.empty();
		}
		FishingSnapshot fishing;
		try {
			fishing = FishingSnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Fishing member parse failed for {}", name, exception);
			fishing = FishingSnapshot.empty();
		}
		CrimsonSnapshot crimson;
		try {
			crimson = CrimsonSnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Crimson member parse failed for {}", name, exception);
			crimson = CrimsonSnapshot.empty();
		}
		RiftSnapshot rift;
		try {
			rift = RiftSnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Rift member parse failed for {}", name, exception);
			rift = RiftSnapshot.empty();
		}
		BestiarySnapshot bestiary;
		try {
			bestiary = BestiarySnapshot.fromMember(member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Bestiary member parse failed for {}", name, exception);
			bestiary = BestiarySnapshot.empty();
		}
		EventsSnapshot events;
		try {
			events = EventsSnapshot.fromMember(member, root, uuid);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Events member parse failed for {}", name, exception);
			events = EventsSnapshot.empty();
		}
		MiscStatsSnapshot misc;
		try {
			misc = MiscStatsSnapshot.from(best, member);
		} catch (Exception exception) {
			BetterPV.LOGGER.warn("Misc stats parse failed for {}", name, exception);
			misc = MiscStatsSnapshot.empty();
		}
		BetterPV.LOGGER.info("Profile ready for {} (nw={})", name, nwText);
		AuctionSnapshot auctionSnapshot = auctions == null ? AuctionSnapshot.empty() : auctions;
		auctionSnapshot = auctionSnapshot.withStats(AuctionSnapshot.Stats.fromMember(member));
		PlayerStatsSnapshot playerStats = PlayerStatsCalculator.fromMember(member, inventoryCategories);
		crimson = crimson.withPlayerStats(playerStats);
		return new LoadedProfile(
			snapshot,
			dungeons,
			inventories,
			pets,
			auctionSnapshot,
			collections,
			garden,
			mining,
			foraging,
			fishing,
			crimson,
			rift,
			bestiary,
			events,
			misc,
			museumMember,
			profileId,
			root,
			choices,
			senither,
			lily,
			networthNormal,
			networthNonCosmetic,
			networthUnsoulbound,
			networthUnsoulboundNonCosmetic,
			ArmorStacks.fromMember(member),
			playerStats,
			null
		);
	}

	/** Prefer {@code preferredProfileId}, else Hypixel-selected, else first usable profile. */
	private static JsonObject pickProfile(JsonArray profiles, String preferredProfileId) {
		if (profiles == null) {
			return null;
		}
		JsonObject preferred = null;
		JsonObject selected = null;
		JsonObject first = null;
		for (JsonElement element : profiles) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject profile = element.getAsJsonObject();
			if (first == null) {
				first = profile;
			}
			String id = profile.has("profile_id") ? profile.get("profile_id").getAsString() : null;
			if (preferredProfileId != null && preferredProfileId.equals(id)) {
				preferred = profile;
			}
			if (profile.has("selected") && profile.get("selected").getAsBoolean()) {
				selected = profile;
			}
		}
		if (preferred != null) {
			return preferred;
		}
		return selected != null ? selected : first;
	}

	private static List<ProfileChoice> listProfileChoices(JsonArray profiles, String activeProfileId) {
		if (profiles == null || profiles.isEmpty()) {
			return List.of();
		}
		List<ProfileChoice> out = new ArrayList<>(profiles.size());
		for (JsonElement element : profiles) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject profile = element.getAsJsonObject();
			String id = profile.has("profile_id") ? profile.get("profile_id").getAsString() : "";
			String cute = profile.has("cute_name") ? profile.get("cute_name").getAsString() : "Unknown";
			boolean hypixelSelected = profile.has("selected") && profile.get("selected").getAsBoolean();
			boolean selected = activeProfileId != null && !activeProfileId.isBlank()
				? activeProfileId.equals(id)
				: hypixelSelected;
			String mode = "";
			if (profile.has("game_mode") && profile.get("game_mode").isJsonPrimitive()
				&& !profile.get("game_mode").isJsonNull()) {
				mode = profile.get("game_mode").getAsString();
			}
			long created = 0L;
			if (profile.has("created_at") && profile.get("created_at").isJsonPrimitive()) {
				try {
					created = profile.get("created_at").getAsLong();
				} catch (Exception ignored) {
					created = 0L;
				}
			}
			out.add(new ProfileChoice(cute, id, selected, mode, created));
		}
		if (activeProfileId == null || activeProfileId.isBlank()) {
			boolean any = false;
			for (ProfileChoice choice : out) {
				if (choice.selected()) {
					any = true;
					break;
				}
			}
			if (!any && !out.isEmpty()) {
				ProfileChoice first = out.get(0);
				out.set(0, new ProfileChoice(
					first.cuteName(), first.profileId(), true, first.gameMode(), first.createdAtMs()
				));
			}
		}
		return out;
	}

	private static List<ProfileSnapshot.BankTransaction> parseBankTransactions(JsonObject profileRoot) {
		if (profileRoot == null) {
			return List.of();
		}
		JsonObject banking = Leveling.obj(profileRoot.get("banking"));
		if (banking == null || !(banking.get("transactions") instanceof JsonArray arr) || arr.isEmpty()) {
			return List.of();
		}
		List<ProfileSnapshot.BankTransaction> out = new ArrayList<>(arr.size());
		for (JsonElement el : arr) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject tx = el.getAsJsonObject();
			String action = tx.has("action") && tx.get("action").isJsonPrimitive()
				? tx.get("action").getAsString()
				: "";
			double amount = 0D;
			if (tx.has("amount") && tx.get("amount").isJsonPrimitive()) {
				try {
					amount = tx.get("amount").getAsDouble();
				} catch (Exception ignored) {
					amount = 0D;
				}
			}
			String initiator = "";
			if (tx.has("initiator_name") && tx.get("initiator_name").isJsonPrimitive()) {
				initiator = tx.get("initiator_name").getAsString();
				if (initiator != null) {
					initiator = initiator.replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "").trim();
				} else {
					initiator = "";
				}
			}
			long ts = 0L;
			if (tx.has("timestamp") && tx.get("timestamp").isJsonPrimitive()) {
				try {
					ts = tx.get("timestamp").getAsLong();
				} catch (Exception ignored) {
					ts = 0L;
				}
			}
			if (action.isBlank() && amount <= 0D && ts <= 0L) {
				continue;
			}
			out.add(new ProfileSnapshot.BankTransaction(action, amount, initiator, ts));
		}
		out.sort((a, b) -> Long.compare(b.timestampMs(), a.timestampMs()));
		if (out.size() > 24) {
			return List.copyOf(out.subList(0, 24));
		}
		return List.copyOf(out);
	}

	private static DungeonSnapshot parseDungeons(
		JsonObject member,
		JsonObject museumMember,
		JsonObject electionRoot,
		Map<String, List<InventoryDecoder.Stack>> inventoryCategories
	) {
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
			Map<String, List<InventoryDecoder.Stack>> categories = inventoryCategories != null
				? inventoryCategories
				: InventoryDecoder.parseCategories(member, museumMember);
			mods = DungeonModifierScanner.scan(categories);
		} catch (Exception ignored) {
		}

		double mayorFactor = CataXpMath.mayorXpFactor(electionRoot);
		String mayorName = CataXpMath.mayorName(electionRoot);

		java.util.Map<String, Double> essenceBonuses = DungeonModifierScanner.readEssenceClassBonuses(member);
		double graduateBonus = DungeonModifierScanner.readCatacombsGraduateBonus(member);

		JsonObject dailyRuns = Leveling.obj(dungeons == null ? null : dungeons.get("daily_runs"));
		int dailyCount = 0;
		if (dailyRuns != null) {
			Float n = Leveling.num(dailyRuns.get("completed_runs_count"));
			if (n != null) {
				dailyCount = Math.max(0, Math.round(n));
			}
		}
		JsonObject journal = Leveling.obj(dungeons == null ? null : dungeons.get("dungeon_journal"));
		int journals = 0;
		if (journal != null && journal.has("unlocked_journals") && journal.get("unlocked_journals").isJsonArray()) {
			journals = journal.getAsJsonArray("unlocked_journals").size();
		}

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
			graduateBonus,
			essenceBonuses,
			mayorFactor,
			mayorName,
			EssenceShopData.wither(member),
			EssenceShopData.undead(member),
			EssenceShopData.ice(member),
			EssenceShopData.spider(member),
			EssenceShopData.dragon(member),
			dailyCount,
			journals
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

	public static JsonObject findMuseumMember(JsonObject museumRoot, String profileId, String undashed) {
		if (museumRoot == null) {
			return null;
		}
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
