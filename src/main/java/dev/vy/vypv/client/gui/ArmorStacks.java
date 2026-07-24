package dev.vy.vypv.client.gui;

import com.google.gson.JsonObject;
import dev.vy.vypv.client.networth.InventoryDecoder;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Best-effort armor stacks for the mannequin from SkyBlock {@code inv_armor}.
 * Without a full item repo this maps common IDs to vanilla stand-ins.
 */
public final class ArmorStacks {
	private ArmorStacks() {
	}

	/** @return boots, leggings, chestplate, helmet (Hypixel slot order) */
	public static ItemStack[] fromMember(JsonObject member) {
		ItemStack[] armor = new ItemStack[] {
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
		};
		if (member == null) {
			return armor;
		}
		List<InventoryDecoder.Stack> slots = InventoryDecoder.readArmorSlots(member);
		for (int i = 0; i < Math.min(4, slots.size()); i++) {
			InventoryDecoder.Stack stack = slots.get(i);
			if (stack != null && stack.id() != null) {
				armor[i] = approximate(stack.id());
			}
		}
		return armor;
	}

	private static ItemStack approximate(String id) {
		String upper = id.toUpperCase(Locale.ROOT);
		boolean helmet = upper.contains("HELMET") || upper.contains("HOOD") || upper.endsWith("_HAT");
		boolean chest = upper.contains("CHESTPLATE") || upper.contains("TUNIC");
		boolean legs = upper.contains("LEGGINGS") || upper.contains("PANTS");
		boolean boots = upper.contains("BOOTS") || upper.contains("SHOES");

		if (upper.contains("NETHERITE") || upper.contains("WITHER") || upper.contains("NECRON")
			|| upper.contains("STORM") || upper.contains("GOLDOR") || upper.contains("MAXOR")
			|| upper.contains("TERROR") || upper.contains("CRIMSON") || upper.contains("AURORA")
			|| upper.contains("HOLLOW") || upper.contains("FERVOR")) {
			if (helmet) return new ItemStack(Items.NETHERITE_HELMET);
			if (chest) return new ItemStack(Items.NETHERITE_CHESTPLATE);
			if (legs) return new ItemStack(Items.NETHERITE_LEGGINGS);
			if (boots) return new ItemStack(Items.NETHERITE_BOOTS);
		}
		if (upper.contains("DIAMOND") || upper.contains("SUPERIOR") || upper.contains("STRONG")
			|| upper.contains("UNSTABLE") || upper.contains("WISE") || upper.contains("YOUNG")
			|| upper.contains("PROTECTOR") || upper.contains("DIVAN") || upper.contains("SHADOW")) {
			if (helmet) return new ItemStack(Items.DIAMOND_HELMET);
			if (chest) return new ItemStack(Items.DIAMOND_CHESTPLATE);
			if (legs) return new ItemStack(Items.DIAMOND_LEGGINGS);
			if (boots) return new ItemStack(Items.DIAMOND_BOOTS);
		}
		if (upper.contains("IRON") || upper.contains("MITHRIL") || upper.contains("TITANIUM")) {
			if (helmet) return new ItemStack(Items.IRON_HELMET);
			if (chest) return new ItemStack(Items.IRON_CHESTPLATE);
			if (legs) return new ItemStack(Items.IRON_LEGGINGS);
			if (boots) return new ItemStack(Items.IRON_BOOTS);
		}
		if (upper.contains("GOLD") || upper.contains("GOLDEN")) {
			if (helmet) return new ItemStack(Items.GOLDEN_HELMET);
			if (chest) return new ItemStack(Items.GOLDEN_CHESTPLATE);
			if (legs) return new ItemStack(Items.GOLDEN_LEGGINGS);
			if (boots) return new ItemStack(Items.GOLDEN_BOOTS);
		}
		if (helmet) return new ItemStack(Items.LEATHER_HELMET);
		if (chest) return new ItemStack(Items.LEATHER_CHESTPLATE);
		if (legs) return new ItemStack(Items.LEATHER_LEGGINGS);
		if (boots) return new ItemStack(Items.LEATHER_BOOTS);
		return ItemStack.EMPTY;
	}
}
