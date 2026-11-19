package dev.teammenu.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import dev.teammenu.Model.TeamView;
import dev.teammenu.Model.View;
import dev.teammenu.Net.Action;
import dev.teammenu.Net.ActionPayload;
import dev.teammenu.Net.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public class TeamMenuClient implements ClientModInitializer {
    private static final Gson GSON = new Gson();
    public static View view = new View();
    private static KeyMapping key;
    private static int ticks;

    @Override
    public void onInitializeClient() {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.teammenu.open", InputConstants.KEY_G, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) {
                if (ClientPlayNetworking.canSend(ActionPayload.TYPE)) send(Action.OPEN, "", "", "", 0);
                else if (mc.player != null)
                    mc.player.sendOverlayMessage(Component.literal("На сервере не установлен мод Team Menu"));
            }
            // пока меню открыто, раз в секунду просим свежие данные (координаты)
            if (mc.gui.screen() instanceof TmScreen && ++ticks % 20 == 0) send(Action.REFRESH, "", "", "", 0);
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, ctx) -> {
            View old = view;
            view = GSON.fromJson(payload.json(), View.class);
            if (view.notice != null) TmScreen.showNotice(view.notice);
            Minecraft mc = ctx.client();
            if (view.open) mc.gui.setScreen(root());
            else if (mc.gui.screen() instanceof TmScreen s) {
                if (!Objects.equals(myId(old), myId(view))) mc.gui.setScreen(root()); // вступил / вышел / удалили
                else s.onSync();
            }
        });
    }

    private static String myId(View v) { return v.my == null ? null : v.my.id; }

    public static Screen root() {
        return view.my != null ? new Screens.TeamScreen(view.my.id, null) : new Screens.Main();
    }

    public static TeamView find(String id) {
        if (view.my != null && view.my.id.equals(id)) return view.my;
        for (TeamView t : view.teams) if (t.id.equals(id)) return t;
        return null;
    }

    public static void send(Action a, String team, String target, String text, int num) {
        ClientPlayNetworking.send(new ActionPayload(a.name(), team, target, text, num));
    }
}
