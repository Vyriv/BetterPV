package dev.vy.betterpv.client.gui;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 3D player mannequin for the Home PV panel (NEU / VyAddons approach). */
public final class PlayerModelRenderer {
	private static final long SPIN_PERIOD_MS = 4_000L;
	private static final float OFFSET_Y = 0.0625F;

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
		applyDinnerboneFlip(entity);
		graphics.enableScissor(x0, y0, x1, y1);
		try {
			int scale = Math.max(16, Math.round(Math.min(x1 - x0, y1 - y0) / 2f * 0.8f));
			if (MoulberryMode.isActive()) {
				extractSpinning(graphics, x0, y0, x1, y1, scale, entity);
			} else {
				InventoryScreen.extractEntityInInventoryFollowsMouse(
					graphics,
					x0,
					y0,
					x1,
					y1,
					scale,
					OFFSET_Y,
					mouseX,
					mouseY,
					entity
				);
			}
		} finally {
			graphics.disableScissor();
		}
	}

	/**
	 * Full 360° yaw spin. {@link LivingEntityRenderState#bodyRot} turns the whole model;
	 * {@link LivingEntityRenderState#yRot} is head-relative-to-body only (keep 0 so head stays locked).
	 */
	private static void extractSpinning(
		GuiGraphicsExtractor graphics,
		int x0,
		int y0,
		int x1,
		int y1,
		int size,
		LivingEntity entity
	) {
		float spin = (System.currentTimeMillis() % SPIN_PERIOD_MS) / (float) SPIN_PERIOD_MS * 360.0F;
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf();
		EntityRenderState renderState = createRenderState(entity);
		if (renderState instanceof LivingEntityRenderState living) {
			// Inventory resting pose is bodyRot=180, yRot=0; add spin to body only.
			living.bodyRot = 180.0F + spin;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
			living.isUpsideDown = true;
		}
		Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + OFFSET_Y, 0.0F);
		graphics.entity(renderState, size, translation, rotation, camera, x0, y0, x1, y1);
	}

	private static EntityRenderState createRenderState(LivingEntity entity) {
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
		EntityRenderState state = renderer.createRenderState(entity, 1.0F);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		return state;
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

	/** Dinnerbone / Grumm upside-down render when the Moulberry Konami mode is on. */
	private static void applyDinnerboneFlip(LivingEntity entity) {
		try {
			if (MoulberryMode.isActive()) {
				entity.setCustomName(Component.literal("Dinnerbone"));
			} else {
				entity.setCustomName(null);
			}
		} catch (Throwable ignored) {
		}
	}

	private static final class GuiPlayer extends ClientMannequin {
		private final Supplier<PlayerSkin> skinLookup;
		private PlayerSkin skin;

		private GuiPlayer(ClientLevel level, UUID uuid, String name) {
			super(level, Minecraft.getInstance().playerSkinRenderCache());
			String safeName = name == null || name.isBlank() ? "Player" : name;
			GameProfile profile = new GameProfile(uuid, safeName);
			try {
				setComponent(DataComponents.PROFILE, ResolvableProfile.createUnresolved(uuid));
			} catch (Throwable ignored) {
				try {
					setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
				} catch (Throwable ignored2) {
				}
			}
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
		public Component getName() {
			if (MoulberryMode.isActive()) {
				return Component.literal("Dinnerbone");
			}
			return super.getName();
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
