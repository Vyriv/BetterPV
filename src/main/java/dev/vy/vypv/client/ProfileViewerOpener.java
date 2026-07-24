package dev.vy.vypv.client;

import dev.vy.vypv.client.gui.ProfileViewerScreen;
import net.minecraft.client.Minecraft;

public final class ProfileViewerOpener {
	private static volatile String pendingName;
	private static volatile int openDelayTicks;

	private ProfileViewerOpener() {
	}

	public static void openSelfOr(String name) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			return;
		}
		String target = name == null || name.isBlank()
			? client.player.getGameProfile().name()
			: name.trim();
		open(target);
	}

	public static void open(String name) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		pendingName = name == null || name.isBlank() ? null : name.trim();
		if (pendingName == null && client.player != null) {
			pendingName = client.player.getGameProfile().name();
		}
		// Defer past chat close so ChatScreen does not immediately replace us.
		openDelayTicks = 2;
	}

	public static void tick(Minecraft client) {
		if (openDelayTicks <= 0 || pendingName == null || client == null) {
			return;
		}
		openDelayTicks--;
		if (openDelayTicks > 0) {
			return;
		}
		String name = pendingName;
		pendingName = null;
		if (name == null || name.isBlank()) {
			return;
		}
		client.setScreen(new ProfileViewerScreen(name));
	}
}
