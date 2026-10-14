package dev.vy.betterpv.client.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

public class SkillTreePresetsTest {
	// Shape from a real profile: each preset repeats the core node, presets share node ids.
	private static JsonObject tree(String selected) {
		return JsonParser.parseString("""
			{"nodes": {
				"foraging": {"timber": 10, "center_of_the_forest": 5, "tree_whisperer": 1},
				"foraging_2": {"timber": 1, "center_of_the_forest": 5, "beekeeper": 1},
				"foraging_3": {"center_of_the_forest": 5},
				"mining": {"mining_speed": 10, "core_of_the_mountain": 10},
				"mining_2": {"core_of_the_mountain": 10}
			},
			"tokens_spent": {"mountain": 7, "forest": 17, "forest_2": 16}
			%s}
			""".formatted(selected)).getAsJsonObject();
	}

	@Test
	public void defaultsToSlotOneWithoutMerging() {
		JsonObject t = tree("");
		assertEquals(1, SkillTreePresets.selectedSlot(t, "foraging"));
		JsonObject nodes = SkillTreePresets.selectedNodes(t, "foraging");
		assertEquals(10, nodes.get("timber").getAsInt());
		assertNull(nodes.get("beekeeper"));
		assertEquals(Integer.valueOf(17), SkillTreePresets.selectedTokensSpent(t, "foraging"));
		assertEquals(Integer.valueOf(7), SkillTreePresets.selectedTokensSpent(t, "mining"));
	}

	@Test
	public void selectedSlotTwoReadsSuffixedPreset() {
		JsonObject t = tree(", \"selected_skill_tree_slot\": {\"foraging\": 2, \"mining\": 2}");
		JsonObject nodes = SkillTreePresets.selectedNodes(t, "foraging");
		assertEquals(1, nodes.get("timber").getAsInt());
		assertEquals(1, nodes.get("beekeeper").getAsInt());
		assertEquals(Integer.valueOf(16), SkillTreePresets.selectedTokensSpent(t, "foraging"));
		assertNull(SkillTreePresets.selectedNodes(t, "mining").get("mining_speed"));
		// No mountain_2 key stored: keep the slot 1 value rather than inventing 0.
		assertEquals(Integer.valueOf(7), SkillTreePresets.selectedTokensSpent(t, "mining"));
	}

	@Test
	public void missingPresetFallsBackToSlotOne() {
		JsonObject t = tree(", \"selected_skill_tree_slot\": {\"foraging\": 4}");
		assertEquals(10, SkillTreePresets.selectedNodes(t, "foraging").get("timber").getAsInt());
	}
}
