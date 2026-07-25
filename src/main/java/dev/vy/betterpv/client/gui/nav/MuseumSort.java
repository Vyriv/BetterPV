package dev.vy.betterpv.client.gui.nav;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Museum category sort chips (not page subtabs). */
public enum MuseumSort {
	COMBAT(Items.IRON_SWORD, "betterpv.museum.combat"),
	MINING(Items.IRON_PICKAXE, "betterpv.museum.mining"),
	FORAGING(Items.IRON_AXE, "betterpv.museum.foraging"),
	FARMING(Items.IRON_HOE, "betterpv.museum.farming"),
	FISHING(Items.FISHING_ROD, "betterpv.museum.fishing"),
	MISC(Items.CHEST, "betterpv.museum.misc");

	private final ItemStack icon;
	private final String langKey;

	MuseumSort(Item item, String langKey) {
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
