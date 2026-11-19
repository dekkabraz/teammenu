package dev.teammenu;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.teammenu.Model.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class TeamCommands {
    private TeamCommands() {}

    private static final Map<UUID, Long> confirm = new HashMap<>();

    private interface Body { void run(ServerPlayer p, CommandContext<CommandSourceStack> c); }

    private static Command<CommandSourceStack> run(Body b) {
        return c -> { b.run(c.getSource().getPlayerOrException(), c); return 1; };
    }
    private static RequiredArgumentBuilder<CommandSourceStack, String> nick() {
        return Commands.argument("nick", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b));
    }
    private static RequiredArgumentBuilder<CommandSourceStack, String> text(String name) {
        return Commands.argument(name, StringArgumentType.greedyString());
    }
    private static String str(CommandContext<CommandSourceStack> c, String name) {
        return StringArgumentType.getString(c, name);
    }

    private static void own(ServerPlayer p, Consumer<TeamData> f) {
        TeamData t = TeamManager.teamOf(p.getUUID());
        if (t == null) TeamManager.err(p, "Вы не состоите в команде.");
        else f.accept(t);
    }
    private static void member(ServerPlayer p, String nick, BiConsumer<TeamData, UUID> f) {
        own(p, t -> {
            MemberData m = t.memberByName(nick);
            if (m == null) TeamManager.err(p, "В команде нет игрока " + nick + ".");
            else f.accept(t, m.id);
        });
    }
    private static void named(ServerPlayer p, String name, Consumer<TeamData> f) {
        TeamData t = TeamManager.byName(name);
        if (t == null || !TeamManager.sees(p, t)) TeamManager.err(p, "Команда не найдена.");
        else f.accept(t);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tm")
                .then(Commands.literal("list").executes(run((p, c) -> TeamManager.list(p))))
                .then(Commands.literal("create").then(text("name").executes(run((p, c) ->
                        TeamManager.create(p, str(c, "name"), Access.PRIVATE, new Settings().bits())))))
                .then(Commands.literal("join").then(text("team").executes(run((p, c) ->
                        named(p, str(c, "team"), t -> TeamManager.join(p, t))))))
                .then(Commands.literal("accept").then(text("team").executes(run((p, c) ->
                        named(p, str(c, "team"), t -> TeamManager.join(p, t))))))
                .then(Commands.literal("deny").then(text("team").executes(run((p, c) ->
                        named(p, str(c, "team"), t -> TeamManager.decline(p, t))))))
                .then(Commands.literal("leave").executes(run((p, c) -> TeamManager.leave(p))))
                .then(Commands.literal("chat").then(text("message").executes(run((p, c) ->
                        TeamManager.chat(p, str(c, "message"))))))
                .then(Commands.literal("invite").then(nick().executes(run((p, c) ->
                        own(p, t -> TeamManager.invite(p, t, str(c, "nick")))))))
                .then(Commands.literal("kick").then(nick().executes(run((p, c) ->
                        member(p, str(c, "nick"), (t, id) -> TeamManager.kick(p, t, id))))))
                .then(Commands.literal("mute").then(nick().executes(run((p, c) ->
                        member(p, str(c, "nick"), (t, id) -> TeamManager.mute(p, t, id))))))
                .then(Commands.literal("transfer").then(nick().executes(run((p, c) ->
                        member(p, str(c, "nick"), (t, id) -> TeamManager.transfer(p, t, id))))))
                .then(Commands.literal("delete")
                        .executes(run((p, c) -> own(p, t -> {
                            if (!TeamManager.manage(p, t)) return;
                            confirm.put(p.getUUID(), System.currentTimeMillis());
                            TeamManager.ok(p, "Вы уверены, что хотите удалить «" + t.name + "»? Введите /tm delete confirm в течение 30 секунд.");
                        })))
                        .then(Commands.literal("confirm").executes(run((p, c) -> own(p, t -> {
                            Long at = confirm.remove(p.getUUID());
                            if (at == null || System.currentTimeMillis() - at > 30_000) TeamManager.err(p, "Сначала введите /tm delete.");
                            else TeamManager.delete(p, t);
                        }))))));

        d.register(Commands.literal("tc").then(text("message").executes(run((p, c) ->
                TeamManager.chat(p, str(c, "message"))))));
    }
}