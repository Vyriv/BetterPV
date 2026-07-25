package dev.vy.vypv.client.gui.nav;

import dev.vy.vypv.VyPV;
import dev.vy.vypv.client.gui.inventories.SkyBlockItemFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Right-side container pickers for the Inventories page. */
public enum InventoryPane {
	INVENTORY(null, null, Items.CHEST, "vypv.inv.inventory"),
	ENDER_CHEST(null, null, Items.ENDER_CHEST, "vypv.inv.ender_chest"),
	BACKPACKS("JUMBO_BACKPACK", null, Items.SHULKER_BOX, "vypv.inv.backpacks"),
	WARDROBE(null, null, Items.ARMOR_STAND, "vypv.inv.wardrobe"),
	EQUIPMENT_WARDROBE(null, null, Items.IRON_CHESTPLATE, "vypv.inv.equipment_wardrobe"),
	LOADOUTS(null, null, Items.NAME_TAG, "vypv.inv.loadouts"),
	SACKS("POCKET_SACK_IN_A_SACK", null, Items.BUNDLE, "vypv.inv.sacks"),
	/** No dedicated Fishing Bag skull in the Hypixel items API. */
	FISHING_BAG(null, null, Items.FISHING_ROD, "vypv.inv.fishing_bag"),
	/**
	 * Hypixel items API ships a broken shared placeholder skin for potion bags -
	 * use the bundled SkyCrypt render instead.
	 */
	POTION_BAG(null, "textures/gui/inventories/potion_bag.png", Items.POTION, "vypv.inv.potion_bag"),
	QUIVER(null, null, Items.ARROW, "vypv.inv.quiver"),
	/**
	 * Same API placeholder issue as potion bags (talisman bag = accessory bag).
	 */
	ACCESSORY_BAG(null, "textures/gui/inventories/accessory_bag.png", Items.GOLDEN_APPLE, "vypv.inv.accessory_bag"),
	TIME_POCKET(null, null, Items.CLOCK, "vypv.inv.time_pocket"),
	PERSONAL_VAULT(null, null, Items.ENDER_EYE, "vypv.inv.personal_vault");

	private final String skyblockIconId;
	private final Identifier textureIcon;
	private final Item fallback;
	private final String langKey;

	InventoryPane(
		String skyblockIconId,
		String texturePath,
		Item fallback,
		String langKey
	) {
		this.skyblockIconId = skyblockIconId;
		this.textureIcon = texturePath == null
			? null
			: Identifier.fromNamespaceAndPath(VyPV.MOD_ID, texturePath);
		this.fallback = fallback;
		this.langKey = langKey;
	}

	/** Bundled 16×16 GUI texture when Hypixel/NEU skulls are missing or wrong. */
	public Identifier textureIcon() {
		return this.textureIcon;
	}

	public ItemStack icon() {
		if (this.skyblockIconId != null) {
			ItemStack sky = SkyBlockItemFactory.iconStack(this.skyblockIconId);
			if (sky != null && !sky.isEmpty() && sky.is(Items.PLAYER_HEAD)) {
				return sky;
			}
		}
		return new ItemStack(this.fallback);
	}

	public Component label() {
		return Component.translatable(this.langKey);
	}
}
