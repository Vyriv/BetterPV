package dev.vy.vypv.client.gui;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

/** 3D player mannequin for the Home PV panel (NEU / VyAddons approach). */
public final class PlayerModelRenderer {
	private GuiPlayer mannequin;
	private UUID boundUuid;
	private final ItemStack[] equipped = new ItemStack[] {
		ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
	};

	public void draw(
		GuiGraphicsExtractor graphics,
		UUID uuid,
		String name,
		int x0,
		int y0,
		int x1,
		int y1,
		int mouseX,
		int mouseY,
		ItemStack helmet,
		ItemStack chest,
		ItemStack legs,
		ItemStack boots
	) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (uuid == null || level == null) {
			return;
		}
		LivingEntity entity = resolveEntity(mc, level, uuid, name);
		if (entity == null) {
			return;
		}
		equipIfChanged(entity, EquipmentSlot.HEAD, 0, helmet);
		equipIfChanged(entity, EquipmentSlot.CHEST, 1, chest);
		equipIfChanged(entity, EquipmentSlot.LEGS, 2, legs);
		equipIfChanged(entity, EquipmentSlot.FEET, 3, boots);
		graphics.enableScissor(x0, y0, x1, y1);
		try {
			InventoryScreen.extractEntityInInventoryFollowsMouse(
				graphics,
				x0,
				y0,
				x1,
				y1,
				Math.max(20, Math.min(x1 - x0, y1 - y0) / 2),
				0.0625F,
				mouseX,
				mouseY,
				entity
			);
		} finally {
			graphics.disableScissor();
		}
	}

	private LivingEntity resolveEntity(Minecraft mc, ClientLevel level, UUID uuid, String name) {
		if (this.mannequin == null || !uuid.equals(this.boundUuid) || this.mannequin.level() != level) {
			this.mannequin = new GuiPlayer(level, uuid, name);
			this.boundUuid = uuid;
			for (int i = 0; i < this.equipped.length; i++) {
				this.equipped[i] = ItemStack.EMPTY;
			}
		}
		return this.mannequin;
	}

	private void equipIfChanged(LivingEntity entity, EquipmentSlot slot, int index, ItemStack stack) {
		ItemStack next = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
		ItemStack current = this.equipped[index];
		if (ItemStack.isSameItemSameComponents(current, next) && current.getCount() == next.getCount()) {
			return;
		}
		this.equipped[index] = next.isEmpty() ? ItemStack.EMPTY : next.copy();
		try {
			entity.setItemSlot(slot, this.equipped[index]);
		} catch (Throwable ignored) {
		}
	}

	private static final class GuiPlayer extends ClientMannequin {
		private final Supplier<PlayerSkin> skinLookup;
		private PlayerSkin skin;

		private GuiPlayer(ClientLevel level, UUID uuid, String name) {
			super(level, Minecraft.getInstance().playerSkinRenderCache());
			GameProfile profile = new GameProfile(uuid, name == null || name.isBlank() ? "Player" : name);
			this.skinLookup = Minecraft.getInstance().getSkinManager().createLookup(profile, true);
			this.skin = DefaultPlayerSkin.get(uuid);
			Minecraft.getInstance().getSkinManager().get(profile).thenAccept(optional -> {
				Optional<PlayerSkin> resolved = optional;
				if (resolved != null && resolved.isPresent()) {
					this.skin = resolved.get();
				}
			});
		}

		@Override
		public PlayerSkin getSkin() {
			PlayerSkin looked = this.skinLookup == null ? null : this.skinLookup.get();
			return looked != null ? looked : (this.skin != null ? this.skin : DefaultPlayerSkin.get(getUUID()));
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean shouldShowName() {
			return false;
		}
	}
}
