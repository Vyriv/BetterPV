package dev.vy.betterpv.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.Leveling;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coop member name cache and profile-list / coop summary parsing.
 * Public coop display APIs stay on {@link ProfileFetcher} as thin delegates.
 */
final class ProfileCoopIndex {
	private static final ConcurrentHashMap<String, String> COOP_MEMBER_NAMES = new ConcurrentHashMap<>();

	private ProfileCoopIndex() {
	}

	static JsonObject selectedProfile(JsonObject root) {
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

	/** Prefer {@code preferredProfileId}, else Hypixel-selected, else first usable profile. */
	static JsonObject pickProfile(JsonArray profiles, String preferredProfileId) {
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

	static List<ProfileFetcher.ProfileChoice> listProfileChoices(
		JsonArray profiles,
		String activeProfileId,
		String viewedUuidUndashed
	) {
		if (profiles == null || profiles.isEmpty()) {
			return List.of();
		}
		String viewed = viewedUuidUndashed == null ? "" : viewedUuidUndashed.replace("-", "").toLowerCase(Locale.ROOT);
		List<ProfileFetcher.ProfileChoice> out = new ArrayList<>(profiles.size());
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
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					created = 0L;
				}
			}
			out.add(new ProfileFetcher.ProfileChoice(
				cute, id, selected, mode, created, parseCoopSummary(profile, viewed)
			));
		}
		if (activeProfileId == null || activeProfileId.isBlank()) {
			boolean any = false;
			for (ProfileFetcher.ProfileChoice choice : out) {
				if (choice.selected()) {
					any = true;
					break;
				}
			}
			if (!any && !out.isEmpty()) {
				ProfileFetcher.ProfileChoice first = out.get(0);
				out.set(0, new ProfileFetcher.ProfileChoice(
					first.cuteName(),
					first.profileId(),
					true,
					first.gameMode(),
					first.createdAtMs(),
					first.coop()
				));
			}
		}
		return out;
	}

	static ProfileFetcher.CoopSummary parseCoopSummary(JsonObject profile, String viewedUuidUndashed) {
		JsonObject members = Leveling.obj(profile == null ? null : profile.get("members"));
		if (members == null || members.isEmpty()) {
			return ProfileFetcher.CoopSummary.solo();
		}
		List<ProfileFetcher.CoopMemberRef> current = new ArrayList<>();
		List<ProfileFetcher.CoopMemberRef> former = new ArrayList<>();
		for (var entry : members.entrySet()) {
			if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
				continue;
			}
			String uuid = entry.getKey() == null ? "" : entry.getKey().replace("-", "").toLowerCase(Locale.ROOT);
			if (uuid.isBlank()) {
				continue;
			}
			JsonObject memberObj = entry.getValue().getAsJsonObject();
			ProfileFetcher.CoopMemberRef ref = new ProfileFetcher.CoopMemberRef(uuid, shortCoopUuid(uuid));
			if (isDeletedCoopMember(memberObj)) {
				former.add(ref);
			} else if (!uuid.equals(viewedUuidUndashed)) {
				current.add(ref);
			}
		}
		current.sort(Comparator.comparing(ProfileFetcher.CoopMemberRef::fallbackName, String.CASE_INSENSITIVE_ORDER));
		former.sort(Comparator.comparing(ProfileFetcher.CoopMemberRef::fallbackName, String.CASE_INSENSITIVE_ORDER));
		return new ProfileFetcher.CoopSummary(current.size(), former.size(), List.copyOf(current), List.copyOf(former));
	}

	static boolean isDeletedCoopMember(JsonObject memberObj) {
		JsonObject profileNode = Leveling.obj(memberObj.get("profile"));
		if (profileNode == null) {
			return false;
		}
		JsonElement notice = profileNode.get("deletion_notice");
		return notice != null && !notice.isJsonNull();
	}

	static String shortCoopUuid(String uuid) {
		if (uuid == null || uuid.length() < 8) {
			return uuid == null ? "?" : uuid;
		}
		return uuid.substring(0, 8);
	}

	static String coopMemberDisplayName(ProfileFetcher.CoopMemberRef member) {
		if (member == null) {
			return "?";
		}
		String resolved = COOP_MEMBER_NAMES.get(member.uuid());
		return resolved != null && !resolved.isBlank() ? resolved : member.fallbackName();
	}

	static boolean coopNameResolved(ProfileFetcher.CoopMemberRef member) {
		if (member == null || member.uuid().isBlank()) {
			return false;
		}
		String name = coopMemberDisplayName(member);
		if (name == null || name.isBlank() || "?".equals(name)) {
			return false;
		}
		return !name.equals(shortCoopUuid(member.uuid()));
	}

	static void warmCoopMemberNames(
		List<ProfileFetcher.ProfileChoice> choices,
		String viewedUuidUndashed,
		String viewedName,
		Runnable onResolved
	) {
		if (choices == null || choices.isEmpty()) {
			return;
		}
		String viewed = viewedUuidUndashed == null ? "" : viewedUuidUndashed.replace("-", "").toLowerCase(Locale.ROOT);
		if (!viewed.isBlank() && viewedName != null && !viewedName.isBlank()) {
			COOP_MEMBER_NAMES.put(viewed, viewedName);
		}
		Set<String> pending = new HashSet<>();
		for (ProfileFetcher.ProfileChoice choice : choices) {
			collectCoopNameLookups(choice.coop().currentMembers(), pending);
			collectCoopNameLookups(choice.coop().formerMembers(), pending);
		}
		for (String uuidKey : pending) {
			UUID uuid = HypixelApiClient.parseUndashedUuid(uuidKey);
			if (uuid == null) {
				continue;
			}
			HypixelApiClient.resolveName(uuid).thenAccept(opt -> {
				opt.ifPresent(id -> {
					COOP_MEMBER_NAMES.put(uuidKey, id.name());
					if (onResolved != null) {
						onResolved.run();
					}
				});
			});
		}
	}

	static void collectCoopNameLookups(List<ProfileFetcher.CoopMemberRef> members, Set<String> pending) {
		for (ProfileFetcher.CoopMemberRef member : members) {
			if (member.uuid().isBlank()) {
				continue;
			}
			if (COOP_MEMBER_NAMES.containsKey(member.uuid())) {
				continue;
			}
			if (!member.fallbackName().equals(shortCoopUuid(member.uuid()))) {
				COOP_MEMBER_NAMES.put(member.uuid(), member.fallbackName());
				continue;
			}
			pending.add(member.uuid());
		}
	}
}
