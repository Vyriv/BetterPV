package dev.vy.vypv.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.vy.vypv.VyPV;
import dev.vy.vypv.client.api.VyPVConfig;
import dev.vy.vypv.client.cosmetics.VyPvCosmetics;
import dev.vy.vypv.client.price.ItemPricer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class VyPVClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VyPVConfig.load();
		ItemPricer.start();
		VyPvCosmetics.initialize();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommands.literal("pv")
					.executes(ctx -> {
						ProfileViewerOpener.openSelfOr(null);
						return 1;
					})
					.then(
						ClientCommands.argument("player", StringArgumentType.word())
							.executes(ctx -> {
								ProfileViewerOpener.openSelfOr(StringArgumentType.getString(ctx, "player"));
								return 1;
							})
					)
			);
			dispatcher.register(
				ClientCommands.literal("vypv")
					.then(
						ClientCommands.literal("pv")
							.executes(ctx -> {
								ProfileViewerOpener.openSelfOr(null);
								return 1;
							})
							.then(
								ClientCommands.argument("player", StringArgumentType.word())
									.executes(ctx -> {
										ProfileViewerOpener.openSelfOr(StringArgumentType.getString(ctx, "player"));
										return 1;
									})
							)
					)
			);
		});

		ClientTickEvents.END_CLIENT_TICK.register(ProfileViewerOpener::tick);
		VyPV.LOGGER.info("VyPV client ready — /pv");
	}
}
