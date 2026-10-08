package dev.vy.betterpv.mixin;

import dev.vy.betterpv.client.api.BetterPvSessionAuth;
import net.minecraft.client.gui.screens.ConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers manual reconnects, quick play, and the vanilla server-transfer packet path. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenSessionAuthMixin {
	@Inject(method = "startConnecting", at = @At("HEAD"))
	private static void betterpv$connectionStarting(CallbackInfo ci) {
		BetterPvSessionAuth.onConnectionStarting();
	}
}
