package dev.vy.vypv.client.gui.nav;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Top-level PV pages. */
public enum PvTab {
	HOME(Items.PAPER, "vypv.tab.home"),
	DUNGEONS(Items.SKELETON_SKULL, "vypv.tab.dungeons"),
	INVENTORIES(Items.CHEST, "vypv.tab.inventories"),
	PETS(Items.BONE, "vypv.tab.pets"),
	AUCTIONS(Items.GOLD_BLOCK, "vypv.tab.auctions"),
	COLLECTIONS(Items.ITEM_FRAME, "vypv.tab.collections"),
	GARDEN(Items.IRON_HOE, "vypv.tab.garden"),
	MINING(Items.IRON_PICKAXE, "vypv.tab.mining"),
	FORAGING(Items.IRON_AXE, "vypv.tab.foraging"),
	CRIMSON(Items.NETHERRACK, "vypv.tab.crimson"),
	RIFT(Items.ENDER_EYE, "vypv.tab.rift"),
	MUSEUM(Items.EMERALD, "vypv.tab.museum"),
	BESTIARY(Items.IRON_SWORD, "vypv.tab.bestiary"),
	BINGO(Items.FILLED_MAP, "vypv.tab.bingo"),
	CHOCOLATE(Items.COOKIE, "vypv.tab.chocolate");

	private final ItemStack icon;
	private final String langKey;

	PvTab(Item item, String langKey) {
		this.icon = new ItemStack(item);
		this.langKey = langKey;
	}

	public ItemStack icon() {
		return this.icon;
	}

	public Component label() {
		return Component.translatable(this.langKey);
	}

	public PvSubTab[] subTabs() {
		return switch (this) {
			case DUNGEONS -> PvSubTab.NONE;
			case AUCTIONS -> new PvSubTab[] { PvSubTab.AUCTION_SOLD, PvSubTab.AUCTION_BOUGHT };
			case COLLECTIONS -> new PvSubTab[] { PvSubTab.COLLECTIONS_LIST, PvSubTab.COLLECTIONS_MINIONS };
			case GARDEN -> new PvSubTab[] { PvSubTab.GARDEN_VISITORS, PvSubTab.GARDEN_GREENHOUSE };
			case FORAGING -> new PvSubTab[] { PvSubTab.FORAGING_MAIN, PvSubTab.FORAGING_SHARDS };
			case CRIMSON -> new PvSubTab[] { PvSubTab.CRIMSON_MAIN, PvSubTab.CRIMSON_TROPHY, PvSubTab.CRIMSON_ABIPHONE };
			case RIFT -> new PvSubTab[] { PvSubTab.RIFT_OVERVIEW, PvSubTab.RIFT_INVENTORY };
			default -> PvSubTab.NONE;
		};
	}

	public boolean hasSubTabs() {
		return subTabs().length > 0;
	}

	public boolean isInventorySplit() {
		return this == INVENTORIES;
	}

	public boolean hasMuseumSort() {
		return this == MUSEUM;
	}

	/** Left-edge tabs for this page (sub modes + museum sorts). */
	public Object[] leftTabs() {
		if (this == MUSEUM) {
			return MuseumSort.values();
		}
		return subTabs();
	}
}
