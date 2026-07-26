package dev.vy.betterpv.client.gui.nav;

import dev.vy.betterpv.BetterPV;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Side subtabs for pages that need mode switching. */
public enum PvSubTab {
	DUNGEON_NORMAL(Items.STONE_BRICKS, "betterpv.sub.dungeon_normal"),
	DUNGEON_MASTER(Items.NETHER_BRICKS, "betterpv.sub.dungeon_master"),
	AUCTION_STATS(Items.GOLD_BLOCK, "betterpv.sub.auction_stats"),
	AUCTION_SOLD(Items.GOLD_INGOT, "betterpv.sub.auction_sold"),
	AUCTION_BOUGHT(Items.EMERALD, "betterpv.sub.auction_bought"),
	COLLECTIONS_LIST(Items.ITEM_FRAME, "betterpv.sub.collections"),
	COLLECTIONS_MINIONS(Items.HOPPER, "betterpv.sub.minions"),
	GARDEN_OVERVIEW(Items.GRASS_BLOCK, "betterpv.sub.garden_overview"),
	GARDEN_VISITORS(Items.VILLAGER_SPAWN_EGG, "betterpv.sub.visitors"),
	GARDEN_CROPS(Items.WHEAT, "betterpv.sub.crops"),
	GARDEN_COMPOSTER(Items.COMPOSTER, "betterpv.sub.composter"),
	GARDEN_GREENHOUSE(Items.GLASS, "betterpv.sub.greenhouse"),
	GARDEN_JACOB(Items.GOLDEN_CARROT, "betterpv.sub.jacob"),
	MINING_OVERVIEW(Items.IRON_PICKAXE, "betterpv.sub.mining_overview"),
	MINING_HOTM(Items.PLAYER_HEAD, "betterpv.sub.mining_hotm", "textures/gui/nav/hotm.png", 64),
	MINING_GLACITE(Items.BLUE_ICE, "betterpv.sub.mining_glacite"),
	FORAGING_MAIN(Items.IRON_AXE, "betterpv.sub.foraging"),
	FORAGING_SHARDS(Items.AMETHYST_SHARD, "betterpv.sub.shards"),
	CRIMSON_MAIN(Items.NETHERRACK, "betterpv.sub.crimson"),
	CRIMSON_TROPHY(Items.TROPICAL_FISH, "betterpv.sub.trophy_fish"),
	CRIMSON_ABIPHONE(Items.NOTE_BLOCK, "betterpv.sub.abiphone"),
	RIFT_OVERVIEW(Items.ENDER_EYE, "betterpv.sub.rift_overview"),
	RIFT_INVENTORY(Items.CHEST, "betterpv.sub.rift_inventory");

	public static final PvSubTab[] NONE = new PvSubTab[0];

	private final ItemStack icon;
	private final String langKey;
	private final Identifier textureIcon;
	private final int textureSize;

	PvSubTab(Item item, String langKey) {
		this(item, langKey, null, 16);
	}

	PvSubTab(Item item, String langKey, String texturePath, int textureSize) {
		this.icon = new ItemStack(item);
		this.langKey = langKey;
		this.textureIcon = texturePath == null
			? null
			: Identifier.fromNamespaceAndPath(BetterPV.MOD_ID, texturePath);
		this.textureSize = Math.max(1, textureSize);
	}

	public ItemStack icon() {
		return this.icon;
	}

	/** Bundled GUI texture when set (e.g. HotM skull icon). */
	public Identifier textureIcon() {
		return this.textureIcon;
	}

	/** Pixel size of {@link #textureIcon()} (square). */
	public int textureSize() {
		return this.textureSize;
	}

	public Component label() {
		return Component.translatable(this.langKey);
	}
}
