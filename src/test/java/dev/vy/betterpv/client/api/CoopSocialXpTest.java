package dev.vy.betterpv.client.api;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import org.junit.Test;

public final class CoopSocialXpTest {
	@Test
	public void sumsSocialXpAcrossCoopMembers() {
		JsonObject first = memberWithSocialXp(200_000D);
		JsonObject second = memberWithSocialXp(5_000D);
		JsonObject members = new JsonObject();
		members.add("first", first);
		members.add("second", second);

		assertEquals(205_000D, ProfileHomeParser.coopSocialXp(members, first), 0D);
		assertEquals(205_000D, ProfileHomeParser.coopSocialXp(members, second), 0D);
	}

	@Test
	public void fallsBackToSelectedMemberWhenMembersAreUnavailable() {
		JsonObject member = memberWithSocialXp(12_345D);

		assertEquals(12_345D, ProfileHomeParser.coopSocialXp(null, member), 0D);
	}

	private static JsonObject memberWithSocialXp(double xp) {
		JsonObject experience = new JsonObject();
		experience.addProperty("SKILL_SOCIAL", xp);
		JsonObject playerData = new JsonObject();
		playerData.add("experience", experience);
		JsonObject member = new JsonObject();
		member.add("player_data", playerData);
		return member;
	}
}
