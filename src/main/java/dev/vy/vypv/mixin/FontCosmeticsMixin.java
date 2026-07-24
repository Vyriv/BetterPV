package dev.vy.vypv.mixin;

import dev.vy.vypv.client.cosmetics.NameStyler;
import dev.vy.vypv.client.gui.ProfileViewerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public abstract class FontCosmeticsMixin {
	@Unique
	private static final ThreadLocal<Integer> vypv$decorationDepth = ThreadLocal.withInitial(() -> 0);

	@Unique
	private static boolean vypv$cosmeticsActive() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.level == null || client.player == null) return false;
		if (client.screen instanceof ProfileViewerScreen) return false;
		return vypv$decorationDepth.get() == 0;
	}

	@Unique
	private static boolean vypv$shouldDecorateRenderedText() {
		return NameStyler.hasGradientStyles() && vypv$cosmeticsActive();
	}

	@Unique
	private static boolean vypv$shouldDecorateMeasuredText() {
		return (NameStyler.hasGradientStyles() || NameStyler.hasChatHeaderStyles()) && vypv$cosmeticsActive();
	}

	@Unique
	private static <T> T vypv$decorateSafely(java.util.function.Supplier<T> action) {
		vypv$decorationDepth.set(vypv$decorationDepth.get() + 1);
		try {
			return action.get();
		} finally {
			int depth = vypv$decorationDepth.get() - 1;
			if (depth <= 0) {
				vypv$decorationDepth.remove();
			} else {
				vypv$decorationDepth.set(depth);
			}
		}
	}

	@ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
	private String vypv$decoratePreparedString(String text) {
		if (!vypv$shouldDecorateRenderedText()) return text;
		return vypv$decorateSafely(() -> {
			String styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToString(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToString(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence vypv$decoratePreparedOrderedText(FormattedCharSequence text) {
		if (text == null || !vypv$shouldDecorateRenderedText()) return text;
		return vypv$decorateSafely(() -> {
			FormattedCharSequence styled = text;
			// Gradients first; only run chat-header pass when that feature has candidates.
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToOrderedText(styled);
			}
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToOrderedText(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
	private String vypv$decorateMeasuredString(String text) {
		if (!vypv$shouldDecorateMeasuredText()) return text;
		return vypv$decorateSafely(() -> {
			String styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToString(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToString(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedText vypv$decorateMeasuredText(FormattedText text) {
		if (text == null || !vypv$shouldDecorateMeasuredText()) return text;
		return vypv$decorateSafely(() -> {
			FormattedText styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToFormattedText(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToFormattedText(styled);
			}
			return styled;
		});
	}

	@ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence vypv$decorateMeasuredOrderedText(FormattedCharSequence text) {
		if (text == null || !vypv$shouldDecorateMeasuredText()) return text;
		return vypv$decorateSafely(() -> {
			FormattedCharSequence styled = text;
			if (NameStyler.hasChatHeaderStyles()) {
				styled = NameStyler.applyChatHeaderToOrderedText(styled);
			}
			if (NameStyler.hasGradientStyles()) {
				styled = NameStyler.applyGradientToOrderedText(styled);
			}
			return styled;
		});
	}
}
