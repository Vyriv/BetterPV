package dev.vy.betterpv.client.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.UUID;
import org.junit.Test;

public class SocialLinksTest {
	static final UUID UUID_TECHNO = UUID.fromString("b876ec32-e396-476b-a115-8438d83c67d4");
	static final String UNDASHED = "b876ec32e396476ba1158438d83c67d4";
	static final String PROFILE_ID = "3564b910-b00c-4069-8b6c-f652a344a41e";
	static final SocialLinks.Target FULL = new SocialLinks.Target(UUID_TECHNO, "Technoblade", PROFILE_ID, "Watermelon");

	private static String url(SocialLinks.Site site, SocialLinks.Target target) {
		return SocialLinks.url(site, target);
	}

	@Test
	public void siteOrderAndNames() {
		SocialLinks.Site[] sites = SocialLinks.Site.values();
		assertEquals(5, sites.length);
		assertEquals("Plancke", sites[0].label());
		assertEquals("SkyCrypt", sites[1].label());
		assertEquals("EliteBot", sites[2].label());
		assertEquals("Coflnet", sites[3].label());
		assertEquals("NameMC", sites[4].label());
	}

	@Test
	public void uuidUrlsForViewedPlayer() {
		assertEquals("https://plancke.io/hypixel/player/stats/" + UNDASHED, url(SocialLinks.Site.PLANCKE, FULL));
		assertEquals("https://sky.shiiyu.moe/stats/" + UNDASHED + "/Watermelon", url(SocialLinks.Site.SKYCRYPT, FULL));
		assertEquals(
			"https://eliteskyblock.com/@" + UNDASHED + "/3564b910b00c40698b6cf652a344a41e",
			url(SocialLinks.Site.ELITEBOT, FULL)
		);
		assertEquals("https://sky.coflnet.com/player/" + UNDASHED, url(SocialLinks.Site.COFLNET, FULL));
		assertEquals("https://namemc.com/profile/b876ec32-e396-476b-a115-8438d83c67d4", url(SocialLinks.Site.NAMEMC, FULL));
	}

	@Test
	public void nameFallbackWithoutUuid() {
		SocialLinks.Target byName = new SocialLinks.Target(null, "Technoblade", PROFILE_ID, "Watermelon");
		assertEquals("https://plancke.io/hypixel/player/stats/Technoblade", url(SocialLinks.Site.PLANCKE, byName));
		assertEquals("https://sky.shiiyu.moe/stats/Technoblade/Watermelon", url(SocialLinks.Site.SKYCRYPT, byName));
		assertEquals(
			"https://eliteskyblock.com/@Technoblade/3564b910b00c40698b6cf652a344a41e",
			url(SocialLinks.Site.ELITEBOT, byName)
		);
		assertNull(url(SocialLinks.Site.COFLNET, byName));
		assertEquals("https://namemc.com/profile/Technoblade", url(SocialLinks.Site.NAMEMC, byName));
	}

	@Test
	public void invalidNameWithoutUuidHasNoLinks() {
		SocialLinks.Target bad = new SocialLinks.Target(null, "../evil?x=1", PROFILE_ID, "Watermelon");
		for (SocialLinks.Site site : SocialLinks.Site.values()) {
			assertNull(site.name(), url(site, bad));
		}
		SocialLinks.Target empty = new SocialLinks.Target(null, null, null, null);
		for (SocialLinks.Site site : SocialLinks.Site.values()) {
			assertNull(site.name(), url(site, empty));
		}
		assertNull(SocialLinks.url(SocialLinks.Site.PLANCKE, null));
		assertNull(SocialLinks.url(null, FULL));
	}

	@Test
	public void noProfileFallsBackToPlayerPage() {
		SocialLinks.Target noProfile = new SocialLinks.Target(UUID_TECHNO, "Technoblade", null, "");
		assertEquals("https://sky.shiiyu.moe/stats/" + UNDASHED, url(SocialLinks.Site.SKYCRYPT, noProfile));
		assertEquals("https://eliteskyblock.com/@" + UNDASHED, url(SocialLinks.Site.ELITEBOT, noProfile));
	}

	@Test
	public void eliteUsesProfileNameWhenIdIsUnusable() {
		SocialLinks.Target badId = new SocialLinks.Target(UUID_TECHNO, "Technoblade", "not-a-uuid", "Watermelon");
		assertEquals("https://eliteskyblock.com/@" + UNDASHED + "/Watermelon", url(SocialLinks.Site.ELITEBOT, badId));
		SocialLinks.Target undashedId = new SocialLinks.Target(
			UUID_TECHNO, "Technoblade", "3564B910B00C40698B6CF652A344A41E", "Watermelon"
		);
		assertEquals(
			"https://eliteskyblock.com/@" + UNDASHED + "/3564b910b00c40698b6cf652a344a41e",
			url(SocialLinks.Site.ELITEBOT, undashedId)
		);
	}

	@Test
	public void profileNamesAreEncoded() {
		SocialLinks.Target odd = new SocialLinks.Target(UUID_TECHNO, "Technoblade", "x", "Kiwi & Co/1");
		assertEquals(
			"https://sky.shiiyu.moe/stats/" + UNDASHED + "/Kiwi%20%26%20Co%2F1",
			url(SocialLinks.Site.SKYCRYPT, odd)
		);
		assertEquals("Caf%C3%A9%20%2B%201", SocialLinks.segment("Caf\u00e9 + 1"));
	}

	@Test
	public void uuidWinsOverName() {
		SocialLinks.Target renamed = new SocialLinks.Target(UUID_TECHNO, "SomeoneElse", PROFILE_ID, "Watermelon");
		assertEquals("https://plancke.io/hypixel/player/stats/" + UNDASHED, url(SocialLinks.Site.PLANCKE, renamed));
	}
}
