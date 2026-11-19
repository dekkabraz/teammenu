package dev.teammenu;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeamMenu implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("teammenu");

    @Override
    public void onInitialize() {
        Net.register();
        ServerPlayNetworking.registerGlobalReceiver(Net.ActionPayload.TYPE,
                (payload, ctx) -> TeamManager.handle(ctx.player(), payload));
        ServerLifecycleEvents.SERVER_STARTED.register(TeamManager::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> TeamManager.save());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> TeamManager.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TeamManager.onLeave(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> TeamCommands.register(dispatcher));
    }
}