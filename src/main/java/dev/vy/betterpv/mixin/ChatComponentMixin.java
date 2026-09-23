package dev.vy.betterpv.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Idle placeholder. Chat click injection uses Fabric {@code MODIFY_GAME} plus
 * {@link ChatScreenClickMixin} / {@link ScreenClickCommandMixin}. Do not re-hook
 * {@code addMessage} here: flattening Components bleached Hypixel colors.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
}
