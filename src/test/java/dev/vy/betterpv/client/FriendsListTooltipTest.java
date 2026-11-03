package dev.vy.betterpv.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.Test;

public class FriendsListTooltipTest {
	private static HoverEvent hover(String text) {
		return new HoverEvent.ShowText(Component.literal(text));
	}

	private static List<String> lines(Component tooltip) {
		return List.of(tooltip.getString().split("\n", -1));
	}

	@Test
	public void offlineFriendKeepsLastSeenAndFriendship() {
		Component tip = Component.translatable("betterpv.chat.click_pv", "AgroMC");
		Component out = FriendsListTooltip.tooltip(
			hover("\u00a77Last seen 8 days ago\n\u00a77Friends for a month and 28 days\n\n\u00a7eClick to view \u00a7bAgroMC\u00a7e's profile"),
			tip
		);
		assertEquals(List.of(
			"\u00a77Last seen 8 days ago",
			"\u00a77Friends for a month and 28 days",
			"",
			tip.getString()
		), lines(out));
		List<Component> parts = out.getSiblings();
		assertSame(tip, parts.get(parts.size() - 1));
	}

	@Test
	public void onlineFriendHasOnlyFriendship() {
		Component tip = Component.translatable("betterpv.chat.click_pv", "Slime");
		Component out = FriendsListTooltip.tooltip(
			hover("\u00a77Friends for 3 years and 6 months\n\n\u00a7eClick to view \u00a7bSlime\u00a7e's profile"),
			tip
		);
		assertEquals(List.of("\u00a77Friends for 3 years and 6 months", "", tip.getString()), lines(out));
	}

	@Test
	public void durationsPassThroughVerbatim() {
		Component tip = Component.translatable("betterpv.chat.click_pv", "Name");
		for (String duration : List.of("a day", "8 days", "a month and 28 days", "1 year, 2 months and 3 days", "3 years and 6 months")) {
			Component out = FriendsListTooltip.tooltip(hover("Friends for " + duration + "\n\nClick to view Name's profile"), tip);
			assertEquals("Friends for " + duration, lines(out).get(0));
		}
	}

	@Test
	public void missingDetailsKeepTheClickLineOnly() {
		Component tip = Component.translatable("betterpv.chat.click_pv", "Name");
		assertSame(tip, FriendsListTooltip.tooltip(null, tip));
		assertSame(tip, FriendsListTooltip.tooltip(hover(""), tip));
		assertSame(tip, FriendsListTooltip.tooltip(hover("\u00a7eClick to view \u00a7bName\u00a7e's profile"), tip));
		assertSame(tip, FriendsListTooltip.tooltip(hover("\n\n\u00a7eClick to view Name's profile\n"), tip));
	}

	@Test
	public void colourCarriesAcrossLinesInOneLiteral() {
		List<MutableComponent> lines = FriendsListTooltip.detailLines(
			Component.literal("\u00a77Last seen 2 hours ago\nFriends for a year\n\nClick to view X's profile")
		);
		assertEquals(2, lines.size());
		assertEquals("\u00a77Friends for a year", lines.get(1).getString());
		assertEquals("\u00a77\u00a7l", FriendsListTooltip.activeLegacyCodes("\u00a7cA\u00a77B\u00a7lC"));
		assertEquals("", FriendsListTooltip.activeLegacyCodes("\u00a77A\u00a7rB"));
	}

	@Test
	public void detectsTheFriendsListHeader() {
		assertTrue(FriendsListTooltip.isFriendsList("\u00a76Friends (Page 1 of 10)\nAgroMC is currently offline"));
		assertTrue(FriendsListTooltip.isFriendsList("-----\n        << Friends (Page 2 of 3) >>\nSlime is in SkyBlock"));
		assertFalse(FriendsListTooltip.isFriendsList("Guild > Slime: my friends (page 1 of 2)"));
		assertFalse(FriendsListTooltip.isFriendsList("Party Members (3)"));
		assertFalse(FriendsListTooltip.isFriendsList(null));
	}

	private static Component friendsMessage(String header, String name, String hover) {
		MutableComponent message = Component.literal(header + "\n");
		message.append(Component.literal("\u00a7b" + name).withStyle(Style.EMPTY
			.withClickEvent(new ClickEvent.RunCommand("/viewprofile " + name))
			.withHoverEvent(hover(hover))));
		message.append(Component.literal(" \u00a7cis currently offline"));
		return message;
	}

	private static Style nameStyle(Component processed) {
		return processed.getSiblings().get(0).getStyle();
	}

	@Test
	public void friendsListNameKeepsDetailsAndOpensPv() {
		Component processed = ChatClickProcessor.process(friendsMessage(
			"\u00a76Friends (Page 1 of 10)",
			"AgroMC",
			"\u00a77Last seen 8 days ago\n\u00a77Friends for a month and 28 days\n\n\u00a7eClick to view \u00a7bAgroMC\u00a7e's profile"
		));
		Style style = nameStyle(processed);
		assertEquals("/pv AgroMC", ((ClickEvent.RunCommand) style.getClickEvent()).command());
		Component tooltip = ((HoverEvent.ShowText) style.getHoverEvent()).value();
		Component tip = Component.translatable("betterpv.chat.click_pv", "AgroMC");
		assertEquals(List.of(
			"\u00a77Last seen 8 days ago",
			"\u00a77Friends for a month and 28 days",
			"",
			tip.getString()
		), lines(tooltip));
		assertEquals("\u00a7bAgroMC \u00a7cis currently offline", processed.getString().split("\n")[1]);
	}

	@Test
	public void otherMessagesKeepTheClickLineOnly() {
		Component processed = ChatClickProcessor.process(friendsMessage(
			"\u00a76Party Members (2)",
			"AgroMC",
			"\u00a77Last seen 8 days ago\n\n\u00a7eClick to view \u00a7bAgroMC\u00a7e's profile"
		));
		Style style = nameStyle(processed);
		assertEquals("/pv AgroMC", ((ClickEvent.RunCommand) style.getClickEvent()).command());
		Component tooltip = ((HoverEvent.ShowText) style.getHoverEvent()).value();
		assertEquals(Component.translatable("betterpv.chat.click_pv", "AgroMC").getString(), tooltip.getString());
	}
}
