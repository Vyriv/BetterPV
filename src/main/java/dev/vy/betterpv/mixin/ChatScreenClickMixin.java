package dev.vy.betterpv.mixin;

import dev.vy.betterpv.client.ProfileViewerOpener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercept chat component clicks before vanilla routes them.
 * Hypixel profile names may use RunCommand, SuggestCommand, Custom, or hover-only
 * "Click to view X's profile" text. {@link ScreenClickCommandMixin} only sees RunCommand.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenClickMixin {
	@Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
	private void betterpv$styleClick(Style style, boolean insertionClick, CallbackInfoReturnable<Boolean> cir) {
		if (insertionClick) {
			return;
		}
		if (ProfileViewerOpener.tryHandleStyle(style)) {
			cir.setReturnValue(true);
		}
	}
}
