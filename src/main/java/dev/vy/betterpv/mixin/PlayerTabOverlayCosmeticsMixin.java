package dev.vy.betterpv.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vy.betterpv.client.cosmetics.BetterPvCosmetics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayCosmeticsMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void vypv$styleTabName(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
		if (playerInfo == null) return;
		Component current = cir.getReturnValue();
		Component styled = BetterPvCosmetics.styleTabDisplayName(current, playerInfo.getProfile());
		if (styled != current) {
			cir.setReturnValue(styled);
		}
	}

	// Styled at the draw call, not in getNameForDisplay: SkyHanni parses tab lines through that
	// method and decorates its compact tab separately, so changing it there would double-style.
	@WrapOperation(
		method = "extractRenderState",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"
		)
	)
	private Component vypv$styleRenderedTabName(PlayerTabOverlay overlay, PlayerInfo playerInfo, Operation<Component> original) {
		Component current = original.call(overlay, playerInfo);
		return BetterPvCosmetics.styleRenderedTabName(current, playerInfo == null ? null : playerInfo.getProfile());
	}
}
