package dev.vy.betterpv.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.vy.betterpv.BetterPV;
import dev.vy.betterpv.client.api.BetterPVConfig;
import dev.vy.betterpv.client.cosmetics.BetterPvCosmetics;
import dev.vy.betterpv.client.gui.LoadingEggFinale;
import dev.vy.betterpv.client.neu.NeuRepoCache;
import dev.vy.betterpv.client.neu.SkyBlockPackCache;
import dev.vy.betterpv.client.price.ItemPricer;
import java.lang.reflect.Field;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.Identifier;

public final class BetterPVClient implements ClientModInitializer {
	/** Runs after {@link Event#DEFAULT_PHASE} so we replace Skyblocker's {@code /pv}. */
	private static final Identifier PV_COMMAND_PHASE = Identifier.fromNamespaceAndPath("betterpv", "override_pv");

	@Override
	public void onInitializeClient() {
		BetterPVConfig.load();
		ItemPricer.start();
		NeuRepoCache.start();
		SkyBlockPackCache.start();
		BetterPvCosmetics.initialize();

		ClientCommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, PV_COMMAND_PHASE);
		ClientCommandRegistrationCallback.EVENT.register(PV_COMMAND_PHASE, (dispatcher, registryAccess) -> {
			removeLiteral(dispatcher, "pv");
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

		PlayerInteractPvOpener.register();
		HypixelProfileSpyButton.register();
		PartyJoinPvNotifier.register();
		ClientTickEvents.END_CLIENT_TICK.register(ProfileViewerOpener::tick);
		ClientTickEvents.END_CLIENT_TICK.register(LoadingEggFinale::tick);
		BetterPV.LOGGER.info("BetterPV client ready - /pv");
	}

	@SuppressWarnings("unchecked")
	private static void removeLiteral(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		try {
			CommandNode<FabricClientCommandSource> root = dispatcher.getRoot();
			Field childrenField = CommandNode.class.getDeclaredField("children");
			childrenField.setAccessible(true);
			Map<String, CommandNode<FabricClientCommandSource>> children =
				(Map<String, CommandNode<FabricClientCommandSource>>) childrenField.get(root);
			children.remove(name);

			Field literalsField = CommandNode.class.getDeclaredField("literals");
			literalsField.setAccessible(true);
			Map<String, LiteralCommandNode<FabricClientCommandSource>> literals =
				(Map<String, LiteralCommandNode<FabricClientCommandSource>>) literalsField.get(root);
			literals.remove(name);
		} catch (ReflectiveOperationException exception) {
			BetterPV.LOGGER.warn("Could not remove existing /{} before override", name, exception);
		}
	}
}
