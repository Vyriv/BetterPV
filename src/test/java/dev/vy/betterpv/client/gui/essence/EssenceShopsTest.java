package dev.vy.betterpv.client.gui.essence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import dev.vy.betterpv.client.data.CrimsonSnapshot;
import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.data.MiningSnapshot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public final class EssenceShopsTest {
	private static final List<String> ORDER = List.of(
		"wither", "undead", "ice", "spider", "dragon",
		"gold", "diamond", "fossil",
		"forest", "safari", "crimson"
	);

	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void listsAllElevenShopsInOrder() {
		assertEquals(11, EssenceShops.ALL.size());
		for (int i = 0; i < ORDER.size(); i++) {
			assertEquals(ORDER.get(i), EssenceShops.ALL.get(i).id());
			assertEquals(i, EssenceShops.indexOf(ORDER.get(i)));
		}
		assertEquals(-1, EssenceShops.indexOf("nope"));
	}

	@Test
	public void groupsAreDungeonsMiningOther() {
		Map<EssenceShops.Group, Integer> sizes = new HashMap<>();
		for (EssenceShops.Def def : EssenceShops.ALL) {
			sizes.merge(def.group(), 1, Integer::sum);
		}
		assertEquals(Integer.valueOf(5), sizes.get(EssenceShops.Group.DUNGEONS));
		assertEquals(Integer.valueOf(3), sizes.get(EssenceShops.Group.MINING));
		assertEquals(Integer.valueOf(3), sizes.get(EssenceShops.Group.OTHER));
		EssenceShops.Group last = EssenceShops.Group.DUNGEONS;
		for (EssenceShops.Def def : EssenceShops.ALL) {
			assertTrue("groups must be contiguous", def.group().ordinal() >= last.ordinal());
			last = def.group();
		}
	}

	@Test
	public void emptySnapshotsResolveEveryShopById() {
		List<EssenceShops.Entry> entries = EssenceShops.resolve(EssenceShops.lookup(
			DungeonSnapshot.empty(), MiningSnapshot.empty(), ForagingSnapshot.empty(), CrimsonSnapshot.empty()));
		assertEquals(11, entries.size());
		Set<String> icons = new HashSet<>();
		for (int i = 0; i < entries.size(); i++) {
			EssenceShops.Entry entry = entries.get(i);
			assertEquals(ORDER.get(i), entry.def().id());
			assertEquals(entry.def().id(), entry.shop().id());
			assertTrue(entry.shop().perks().isEmpty());
			icons.add(entry.shop().iconId());
		}
		assertEquals("each shop keeps its own icon", 11, icons.size());
	}

	@Test
	public void nullSnapshotsFallBackToEmptyShops() {
		List<EssenceShops.Entry> entries = EssenceShops.resolve(EssenceShops.lookup(null, null, null, null));
		assertEquals(11, entries.size());
		for (EssenceShops.Entry entry : entries) {
			assertEquals(entry.def().id(), entry.shop().id());
		}
		assertEquals(11, EssenceShops.resolve(null).size());
	}

	@Test
	public void resolveKeepsTheShopObjectFromTheLookup() {
		DungeonSnapshot.EssenceShop safari = new DungeonSnapshot.EssenceShop("safari", "Safari", 1234L, "ESSENCE_SAFARI", List.of(
			new DungeonSnapshot.EssencePerk("a", "A", 1, 2)
		));
		List<EssenceShops.Entry> entries = EssenceShops.resolve(id -> id.equals("safari") ? safari : null);
		assertSame(safari, entries.get(EssenceShops.indexOf("safari")).shop());
		assertTrue(entries.get(EssenceShops.indexOf("wither")).shop().perks().isEmpty());
	}
}
