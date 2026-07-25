package dev.vy.betterpv.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.vy.betterpv.BetterPV;
import dev.vy.betterpv.client.api.BetterPVConfig;
import dev.vy.betterpv.client.cosmetics.BetterPvCosmetics;
import dev.vy.betterpv.client.neu.NeuRepoCache;
import dev.vy.betterpv.client.neu.SkyBlockPackCache;
import dev.vy.betterpv.client.price.ItemPricer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class BetterPVClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BetterPVConfig.load();
		ItemPricer.start();
		NeuRepoCache.start();
		SkyBlockPackCache.start();
		BetterPvCosmetics.initialize();
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
				ClientCommands.literal("betterpv")
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
		BetterPV.LOGGER.info("BetterPV client ready — /pv");
	}
}
