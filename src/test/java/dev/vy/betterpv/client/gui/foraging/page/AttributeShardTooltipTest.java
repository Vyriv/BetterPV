package dev.vy.betterpv.client.gui.foraging.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vy.betterpv.client.data.AttributeShardsData;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

public final class AttributeShardTooltipTest {
	private static final int LEGENDARY = 0xFFFFAA00;

	// NEU constants/attribute_shards.json entries.
	private static final AttributeShardsData.Def DAEMON = new AttributeShardsData.Def(
		"pity", "Daemon", "Pity", "LEGENDARY", "ATTRIBUTE_SHARD_PITY;1", "SHARD_DAEMON");
	private static final AttributeShardsData.Def GROVE = new AttributeShardsData.Def(
		"nature_elemental", "Grove", "Nature Elemental", "COMMON", "ATTRIBUTE_SHARD_NATURE_ELEMENTAL;1", "SHARD_GROVE");

	// NEU items/ATTRIBUTE_SHARD_PITY;1.json (display name + lore).
	private static final JsonObject DAEMON_ITEM = JsonParser.parseString("""
		{"itemid":"minecraft:skull","displayname":"\\u00a76Daemon Shard","lore":[
		"\\u00a76Pity I",
		"\\u00a77Your \\u00a7dRNG Meter \\u00a77fills \\u00a7a+1% \\u00a77faster.",
		"",
		"\\u00a77You can Syphon this shard from",
		"\\u00a77your \\u00a7aHunting Box\\u00a77.",
		"",
		"\\u00a7eRight-click to send to Hunting Box!",
		"\\u00a7eShift Right-click to move all!",
		"",
		"\\u00a74\\u2763 \\u00a7cRequires \\u00a7aHunting Skill 20\\u00a7c.",
		"\\u00a76\\u00a7lLEGENDARY COMBAT SHARD \\u00a78(ID L18)"]}
		""").getAsJsonObject();

	// NEU items/ATTRIBUTE_SHARD_NATURE_ELEMENTAL;1.json.
	private static final JsonObject GROVE_ITEM = JsonParser.parseString("""
		{"itemid":"minecraft:skull","displayname":"\\u00a7fGrove Shard","lore":[
		"\\u00a76Nature Elemental I",
		"\\u00a77Grants \\u00a7c+2 \\u2764 Health\\u00a77.",
		"",
		"\\u00a77You can Syphon this shard from",
		"\\u00a77your \\u00a7aHunting Box\\u00a77.",
		"",
		"\\u00a7f\\u00a7lCOMMON FOREST SHARD \\u00a78(ID C1)"]}
		""").getAsJsonObject();

	@BeforeClass
	public static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	public void maxedLegendaryShardUsesItemNameAttributeAndDescription() {
		AttributeShardTooltip.Info info = AttributeShardTooltip.info(DAEMON, DAEMON_ITEM, "Pity");
		ForagingSnapshot.AttributeShardRow row = row(DAEMON, 10, 10, 24, 24);
		List<String> text = text(AttributeShardTooltip.lines(row, info, 1_000_000D, LEGENDARY));
		assertEquals(List.of(
			"Daemon Shard",
			"Shard (Pity)",
			"Your RNG Meter fills +1% faster.",
			"Level 10/10 (24/24)",
			"Shards to max: 0 (0m)",
			"Legendary"
		), text);
	}

	@Test
	public void titleAndRarityLineUseTheRarityColour() {
		List<PvTooltip.Line> lines = AttributeShardTooltip.lines(
			row(DAEMON, 10, 10, 24, 24), AttributeShardTooltip.info(DAEMON, DAEMON_ITEM, "Pity"), 1D, LEGENDARY);
		assertEquals(LEGENDARY, lines.get(0).spans().get(0).color());
		assertTrue(lines.get(0).spans().get(0).bold());
		assertEquals(LEGENDARY, lines.get(lines.size() - 1).spans().get(0).color());
	}

	@Test
	public void descriptionKeepsLoreColours() {
		List<PvTooltip.Line> lines = AttributeShardTooltip.lines(
			row(DAEMON, 1, 10, 1, 24), AttributeShardTooltip.info(DAEMON, DAEMON_ITEM, "Pity"), 1D, LEGENDARY);
		List<PvTooltip.Span> spans = lines.get(2).spans();
		assertEquals("RNG Meter ", spans.get(1).text());
		assertEquals(0xFFFF55FF, spans.get(1).color());
	}

	@Test
	public void partialCommonShardShowsOwnedRequiredAndCost() {
		AttributeShardTooltip.Info info = AttributeShardTooltip.info(GROVE, GROVE_ITEM, "Nature Elemental");
		List<String> text = text(AttributeShardTooltip.lines(row(GROVE, 3, 10, 9, 96), info, 12_500D, 0xFFFFFFFF));
		assertEquals("Grove Shard", text.get(0));
		assertEquals("Shard (Nature Elemental)", text.get(1));
		assertEquals("Grants +2 \u2764 Health.", text.get(2));
		assertEquals("Level 3/10 (9/96)", text.get(3));
		assertEquals("Shards to max: 87 (1.09m)", text.get(4));
		assertEquals("Common", text.get(5));
	}

	@Test
	public void descriptionSkipsGenericLoreAndRarityFooter() {
		List<String> lines = AttributeShardTooltip.info(DAEMON, DAEMON_ITEM, "Pity").description();
		assertEquals(1, lines.size());
		String joined = String.join("\n", lines);
		assertFalse(joined.contains("Syphon"));
		assertFalse(joined.contains("LEGENDARY"));
		assertFalse(joined.contains("Requires"));
		assertFalse(joined.contains("Pity I"));
	}

	@Test
	public void multiLineDescriptionsKeepTheirWrapping() {
		List<String> lore = List.of(
			"\u00a76Mystery I", "\u00a77First line of", "\u00a77the description.", "", "\u00a77You can Syphon this shard from");
		assertEquals(List.of("\u00a77First line of", "\u00a77the description."), AttributeShardTooltip.description(lore, "Mystery"));
	}

	@Test
	public void descriptionWithoutBlankLinesStopsAtRarityFooter() {
		List<String> lore = List.of("\u00a77Does a thing.", "\u00a7f\u00a7lCOMMON FOREST SHARD \u00a78(ID C1)");
		assertEquals(List.of("\u00a77Does a thing."), AttributeShardTooltip.description(lore, "Other"));
	}

	@Test
	public void missingNeuItemFallsBackToCatalogNames() {
		AttributeShardTooltip.Info info = AttributeShardTooltip.info(DAEMON, null, "Pity");
		assertEquals("Daemon Shard", info.shardName());
		assertEquals("Pity", info.attribute());
		assertTrue(info.description().isEmpty());
		List<String> text = text(AttributeShardTooltip.lines(row(DAEMON, 0, 10, 0, 24), info, 0D, LEGENDARY));
		assertEquals("No description available", text.get(2));
		assertEquals("Level 0/10 (0/24)", text.get(3));
	}

	@Test
	public void missingShardMappingStaysGraceful() {
		AttributeShardTooltip.Info info = AttributeShardTooltip.info(null, null, "Pity");
		assertEquals("Unknown Shard", info.shardName());
		assertEquals("Pity", info.attribute());
		AttributeShardTooltip.Info none = AttributeShardTooltip.info(null, null, " ");
		assertNull(none.attribute());
		assertEquals("Shard", text(AttributeShardTooltip.lines(row(DAEMON, 0, 10, 0, 24), none, 0D, LEGENDARY)).get(1));
	}

	@Test
	public void unknownPriceIsNeverShownAsZero() {
		List<String> text = text(AttributeShardTooltip.lines(
			row(DAEMON, 2, 10, 2, 24), AttributeShardTooltip.info(DAEMON, DAEMON_ITEM, "Pity"), 0D, LEGENDARY));
		assertEquals("Shards to max: 22 (price unknown)", text.get(4));
		assertNull(AttributeShardTooltip.coins(5, 0D));
		assertNull(AttributeShardTooltip.coins(5, -1D));
	}

	@Test
	public void coinFormatting() {
		assertEquals("0m", AttributeShardTooltip.coins(0, 0D));
		assertEquals("0m", AttributeShardTooltip.coins(-3, 50D));
		assertEquals("500k", AttributeShardTooltip.coins(5, 100_000D));
		assertEquals("2.5m", AttributeShardTooltip.coins(25, 100_000D));
		assertEquals("750", AttributeShardTooltip.coins(3, 250D));
	}

	@Test
	public void shardNameDoesNotDoubleTheSuffix() {
		AttributeShardsData.Def named = new AttributeShardsData.Def("x", "Odd Shard", "X", "RARE", "ATTRIBUTE_SHARD_X;1", "SHARD_ODD");
		assertEquals("Odd Shard", AttributeShardTooltip.shardName(named, null));
	}

	private static ForagingSnapshot.AttributeShardRow row(AttributeShardsData.Def def, int level, int max, int owned, int forMax) {
		return new ForagingSnapshot.AttributeShardRow(
			def.stackId(), def.abilityName(), def.iconId(), def.rarity(), def.priceId(),
			level, max, owned, forMax, owned > 0);
	}

	private static List<String> text(List<PvTooltip.Line> lines) {
		return lines.stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).collect(Collectors.joining()))
			.toList();
	}
}
