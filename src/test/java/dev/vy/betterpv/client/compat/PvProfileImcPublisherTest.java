package dev.vy.betterpv.client.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class PvProfileImcPublisherTest {
	private static final UUID LOCAL_UUID = UUID.fromString("12345678-1234-4234-9234-123456789abc");

	@Test
	public void selectsOnlyTheSelectedLocalProfile() {
		JsonObject root = root(profile("selected", true, true), profile("other", false, true));

		JsonObject selected = PvProfileImcPublisher.findPublishableProfile(
			root, "selected", LOCAL_UUID, LOCAL_UUID
		);

		assertNotNull(selected);
		assertEquals("selected", selected.get("profile_id").getAsString());
		assertNull(PvProfileImcPublisher.findPublishableProfile(root, "other", LOCAL_UUID, LOCAL_UUID));
		assertNull(PvProfileImcPublisher.findPublishableProfile(
			root, "selected", UUID.randomUUID(), LOCAL_UUID
		));
	}

	@Test
	public void requiresTheLocalMemberInThePayload() {
		JsonObject root = root(profile("selected", true, false));

		assertNull(PvProfileImcPublisher.findPublishableProfile(
			root, "selected", LOCAL_UUID, LOCAL_UUID
		));
	}

	@Test
	public void isolatesListenersAndPayloadMutations() {
		JsonObject profile = profile("selected", true, true);
		AtomicReference<JsonObject> secondPayload = new AtomicReference<>();
		AtomicBoolean finalListenerCalled = new AtomicBoolean();

		int delivered = PvProfileImcPublisher.dispatch(profile, List.of(
			payload -> payload.addProperty("changed", true),
			payload -> secondPayload.set(payload),
			payload -> { throw new IllegalStateException("listener failure"); },
			payload -> finalListenerCalled.set(true)
		));

		assertEquals(3, delivered);
		assertNotNull(secondPayload.get());
		assertTrue(!secondPayload.get().has("changed"));
		assertTrue(!profile.has("changed"));
		assertTrue(finalListenerCalled.get());
	}

	private static JsonObject root(JsonObject... profiles) {
		JsonArray array = new JsonArray();
		for (JsonObject profile : profiles) {
			array.add(profile);
		}
		JsonObject root = new JsonObject();
		root.add("profiles", array);
		return root;
	}

	private static JsonObject profile(String id, boolean selected, boolean includeLocalMember) {
		JsonObject profile = new JsonObject();
		profile.addProperty("profile_id", id);
		profile.addProperty("selected", selected);
		JsonObject members = new JsonObject();
		if (includeLocalMember) {
			members.add(LOCAL_UUID.toString().replace("-", ""), new JsonObject());
		}
		profile.add("members", members);
		return profile;
	}
}
