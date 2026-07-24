package dev.vy.vypv.client.gui.nav;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Right-side container pickers for the Inventories page. */
public enum InventoryPane {
	INVENTORY(Items.CHEST, "vypv.inv.inventory"),
	BACKPACKS(Items.SHULKER_BOX, "vypv.inv.backpacks"),
	ENDER_CHEST(Items.ENDER_CHEST, "vypv.inv.ender_chest"),
	WARDROBE(Items.ARMOR_STAND, "vypv.inv.wardrobe"),
	EQUIPMENT(Items.IRON_CHESTPLATE, "vypv.inv.equipment"),
	ACCESSORY_BAG(Items.GOLDEN_APPLE, "vypv.inv.accessory_bag"),
	SACKS(Items.BUNDLE, "vypv.inv.sacks"),
	LOADOUTS(Items.NAME_TAG, "vypv.inv.loadouts"),
	BAGS(Items.LEATHER, "vypv.inv.bags"),
	MISC(Items.PAINTING, "vypv.inv.misc");

	private final ItemStack icon;
	private final String langKey;

	InventoryPane(Item item, String langKey) {
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
