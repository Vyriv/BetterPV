package dev.vy.vypv.client.gui.nav;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Side subtabs for pages that need mode switching. */
public enum PvSubTab {
	DUNGEON_NORMAL(Items.STONE_BRICKS, "vypv.sub.dungeon_normal"),
	DUNGEON_MASTER(Items.NETHER_BRICKS, "vypv.sub.dungeon_master"),
	AUCTION_SOLD(Items.GOLD_INGOT, "vypv.sub.auction_sold"),
	AUCTION_BOUGHT(Items.EMERALD, "vypv.sub.auction_bought"),
	COLLECTIONS_LIST(Items.ITEM_FRAME, "vypv.sub.collections"),
	COLLECTIONS_MINIONS(Items.HOPPER, "vypv.sub.minions"),
	GARDEN_VISITORS(Items.VILLAGER_SPAWN_EGG, "vypv.sub.visitors"),
	GARDEN_GREENHOUSE(Items.GLASS, "vypv.sub.greenhouse"),
	FORAGING_MAIN(Items.IRON_AXE, "vypv.sub.foraging"),
	FORAGING_SHARDS(Items.AMETHYST_SHARD, "vypv.sub.shards"),
	CRIMSON_MAIN(Items.NETHERRACK, "vypv.sub.crimson"),
	CRIMSON_TROPHY(Items.TROPICAL_FISH, "vypv.sub.trophy_fish"),
	CRIMSON_ABIPHONE(Items.NOTE_BLOCK, "vypv.sub.abiphone"),
	RIFT_OVERVIEW(Items.ENDER_EYE, "vypv.sub.rift_overview"),
	RIFT_INVENTORY(Items.CHEST, "vypv.sub.rift_inventory");

	public static final PvSubTab[] NONE = new PvSubTab[0];

	private final ItemStack icon;
	private final String langKey;

	PvSubTab(Item item, String langKey) {
		this.icon = new ItemStack(item);
		this.langKey = langKey;
	}

	public ItemStack icon() {
		return this.icon;
	}

	public Component label() {
		return Component.translatable(this.langKey);
	}
}
