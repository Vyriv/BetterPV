package dev.vy.betterpv.client.gui.nav;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Top-level PV pages. */
public enum PvTab {
	HOME(Items.PAPER, "betterpv.tab.home"),
	DUNGEONS(Items.SKELETON_SKULL, "betterpv.tab.dungeons"),
	INVENTORIES(Items.CHEST, "betterpv.tab.inventories"),
	PETS(Items.BONE, "betterpv.tab.pets"),
	AUCTIONS(Items.GOLD_BLOCK, "betterpv.tab.auctions"),
	COLLECTIONS(Items.ITEM_FRAME, "betterpv.tab.collections"),
	GARDEN(Items.IRON_HOE, "betterpv.tab.garden"),
	MINING(Items.IRON_PICKAXE, "betterpv.tab.mining"),
	FORAGING(Items.IRON_AXE, "betterpv.tab.foraging"),
	CRIMSON(Items.NETHERRACK, "betterpv.tab.crimson"),
	RIFT(Items.ENDER_EYE, "betterpv.tab.rift"),
	MUSEUM(Items.EMERALD, "betterpv.tab.museum"),
	BESTIARY(Items.IRON_SWORD, "betterpv.tab.bestiary"),
	BINGO(Items.FILLED_MAP, "betterpv.tab.bingo"),
	CHOCOLATE(Items.COOKIE, "betterpv.tab.chocolate");

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
			case AUCTIONS -> new PvSubTab[] { PvSubTab.AUCTION_STATS, PvSubTab.AUCTION_SOLD, PvSubTab.AUCTION_BOUGHT };
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
