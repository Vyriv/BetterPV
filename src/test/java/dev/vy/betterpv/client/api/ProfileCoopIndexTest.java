package dev.vy.betterpv.client.api;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

public final class ProfileCoopIndexTest {
	private static JsonObject profile(String id, boolean selected) {
		JsonObject p = new JsonObject();
		p.addProperty("profile_id", id);
		p.addProperty("selected", selected);
		return p;
	}

	private static JsonArray memberProfiles() {
		JsonArray profiles = new JsonArray();
		profiles.add(profile("ironman", false));
		profiles.add(profile("normal", true));
		return profiles;
	}

	@Test
	public void coopSwitchStaysOnTheViewedProfile() {
		assertEquals("ironman", ProfileCoopIndex.pickProfile(memberProfiles(), "ironman").get("profile_id").getAsString());
	}

	@Test
	public void plainLookupUsesTheMembersSelectedProfile() {
		assertEquals("normal", ProfileCoopIndex.pickProfile(memberProfiles(), null).get("profile_id").getAsString());
	}

	@Test
	public void formerMemberFallsBackToTheirSelectedProfile() {
		assertEquals("normal", ProfileCoopIndex.pickProfile(memberProfiles(), "left-this-one").get("profile_id").getAsString());
	}
}
