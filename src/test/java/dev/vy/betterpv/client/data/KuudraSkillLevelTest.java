package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import org.junit.Test;

public class KuudraSkillLevelTest {
	@Test
	public void foragingUsesItsOwnCapNotTheFarmingCap() {
		JsonObject member = member(xpFor(60), xpFor(60));
		JsonObject jacob = new JsonObject();
		JsonObject perks = new JsonObject();
		perks.addProperty("farming_level_cap", 10);
		jacob.add("perks", perks);
		member.add("jacobs_contest", jacob);

		assertEquals(60, Leveling.skillLevel(member, "farming"));
		assertEquals(50, Leveling.skillLevel(member, "foraging"));
		assertEquals(50, CrimsonKuudraCard.from(member, Map.of()).foragingLevel());
	}

	@Test
	public void kuudraForagingMatchesSkillPageBelowCap() {
		JsonObject member = member(xpFor(60), xpFor(27) + 10);
		int expected = (int) Math.floor(Leveling.getLevel(Leveling.skillTable("foraging"),
			Leveling.readSkillXpDouble(member, "foraging"), Leveling.skillCap("foraging", member), false).level());

		assertEquals(27, expected);
		assertEquals(expected, CrimsonKuudraCard.from(member, Map.of()).foragingLevel());
	}

	private static JsonObject member(double farmingXp, double foragingXp) {
		JsonObject experience = new JsonObject();
		experience.addProperty("SKILL_FARMING", farmingXp);
		experience.addProperty("SKILL_FORAGING", foragingXp);
		JsonObject playerData = new JsonObject();
		playerData.add("experience", experience);
		JsonObject member = new JsonObject();
		member.add("player_data", playerData);
		return member;
	}

	private static double xpFor(int level) {
		JsonArray table = RepoData.levelingXp();
		assertNotNull(table);
		double total = 0;
		for (int i = 0; i < level; i++) {
			total += table.get(i).getAsDouble();
		}
		return total;
	}
}
