package dev.vy.betterpv.client.api;

import com.google.gson.JsonObject;
import dev.vy.betterpv.client.data.Leveling;

/** Resolves museum member JSON from Hypixel museum responses. */
final class ProfileMuseumLookup {
	private ProfileMuseumLookup() {
	}

	static JsonObject findMuseumMember(JsonObject museumRoot, String profileId, String undashed) {
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

	static JsonObject findMember(JsonObject members, String undashed) {
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
}
