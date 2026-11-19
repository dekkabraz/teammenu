package dev.teammenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.teammenu.Model.*;
import dev.teammenu.Net.Action;
import dev.teammenu.Net.ActionPayload;
import dev.teammenu.Net.SyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class TeamManager {
    private TeamManager() {}

    private static final Gson GSON = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final String SOLO = "tm_solo";
    static final boolean VOICE = FabricLoader.getInstance().isModLoaded("voicechat");

    static MinecraftServer server;
    static final Map<UUID, TeamData> teams = new LinkedHashMap<>();

    // ------------------------------------------------------------ хранение
    private static Path file() { return server.getWorldPath(LevelResource.ROOT).resolve("teammenu.json"); }

    static void load(MinecraftServer s) {
        server = s;
        teams.clear();
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f)) {
                TeamData[] arr = GSON.fromJson(r, TeamData[].class);
                if (arr != null) for (TeamData t : arr) teams.put(t.id, t);
            } catch (Exception e) {
                TeamMenu.LOG.error("Не удалось прочитать teammenu.json", e);
            }
        }
        applyScoreboard();
    }

    static void save() {
        if (server == null) return;
        try {
            Files.writeString(file(), PRETTY.toJson(teams.values().toArray(new TeamData[0])));
        } catch (Exception e) {
            TeamMenu.LOG.error("Не удалось сохранить teammenu.json", e);
        }
    }

    // ------------------------------------------------------------ вспомогательное
    /** Админ = оператор сервера. Если имя метода в вашей версии другое, правится только эта строка. */
    static boolean isAdmin(ServerPlayer p) { return server.getPlayerList().isOp(p.nameAndId()); }

    static TeamData teamOf(UUID id) {
        for (TeamData t : teams.values()) if (t.member(id) != null) return t;
        return null;
    }
    static TeamData byId(String s) { UUID id = uuid(s); return id == null ? null : teams.get(id); }
    static TeamData byName(String name) {
        for (TeamData t : teams.values()) if (t.name.equalsIgnoreCase(name.strip())) return t;
        return null;
    }
    static boolean sees(ServerPlayer p, TeamData t) {
        return t.access != Access.HIDDEN || t.member(p.getUUID()) != null || t.invited.containsKey(p.getUUID()) || isAdmin(p);
    }
    static boolean manage(ServerPlayer p, TeamData t) {
        if (t.owner.equals(p.getUUID()) || isAdmin(p)) return true;
        err(p, "Это может делать только создатель команды.");
        return false;
    }
    static boolean mayVoice(UUID id, TeamData t) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return t.access == Access.PUBLIC || t.member(id) != null || (p != null && isAdmin(p));
    }

    private static UUID uuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
    private static Access access(String s) {
        try { return Access.valueOf(s); } catch (Exception e) { return Access.PRIVATE; }
    }
    private static String clean(String s, int max) {
        s = s == null ? "" : s.replaceAll("[\\p{Cntrl}§]", "").strip();
        return s.length() > max ? s.substring(0, max) : s;
    }
    private static List<UUID> onlineIds() {
        return server.getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).toList();
    }
    private static Component line(String s, ChatFormatting color) {
        return Component.literal("[Команды] ").withStyle(ChatFormatting.AQUA).append(Component.literal(s).withStyle(color));
    }
    static void ok(ServerPlayer p, String s) { p.sendSystemMessage(line(s, ChatFormatting.WHITE)); }
    static void err(ServerPlayer p, String s) { p.sendSystemMessage(line(s, ChatFormatting.RED)); sync(p, false, s); }
    static void tell(UUID id, String s) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) ok(p, s);
    }
    static void broadcast(TeamData t, Component c) {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (t.member(p.getUUID()) != null || t.observers.contains(p.getUUID())) p.sendSystemMessage(c);
    }
    static void broadcast(TeamData t, String s) { broadcast(t, line(s, ChatFormatting.GRAY)); }

    static void changed() {
        save();
        applyScoreboard();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p, false, null);
    }

    // ------------------------------------------------------------ ники, урон по своим (ванильные scoreboard-команды)
    static void applyScoreboard() {
        ServerScoreboard sb = server.getScoreboard();
        for (PlayerTeam pt : List.copyOf(sb.getPlayerTeams()))
            if (pt.getName().startsWith("tm_")) sb.removePlayerTeam(pt);
        for (TeamData t : teams.values()) {
            PlayerTeam pt = sb.addPlayerTeam("tm_" + t.id.toString().replace("-", "").substring(0, 12));
            pt.setDisplayName(Component.literal(t.name));
            pt.setAllowFriendlyFire(t.settings.friendlyFire);
            pt.setSeeFriendlyInvisibles(t.settings.seeInvisible);
            pt.setNameTagVisibility(t.settings.nametags ? Team.Visibility.HIDE_FOR_OTHER_TEAMS : Team.Visibility.ALWAYS);
            for (MemberData m : t.members) sb.addPlayerToTeam(m.name, pt);
        }
        // Соло-игроки сидят в общей "пустой" команде, иначе ваниль всё равно покажет им скрытые ники.
        PlayerTeam solo = sb.addPlayerTeam(SOLO);
        solo.setSeeFriendlyInvisibles(false);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (teamOf(p.getUUID()) == null) sb.addPlayerToTeam(p.getScoreboardName(), solo);
    }

    static void onJoin(ServerPlayer p) {
        UUID id = p.getUUID();
        TeamData t = teamOf(id);
        if (t != null) t.member(id).name = p.getScoreboardName();
        applyScoreboard();
        PlayerTeam solo = server.getScoreboard().getPlayerTeam(SOLO);
        if (t == null && solo != null) server.getScoreboard().addPlayerToTeam(p.getScoreboardName(), solo);
        long n = teams.values().stream().filter(x -> x.invited.containsKey(id)).count();
        if (n > 0) ok(p, "У вас есть приглашения в команды: " + n + ". Откройте меню команд.");
    }

    static void onLeave(ServerPlayer p) {
        for (TeamData t : teams.values()) t.observers.remove(p.getUUID());
    }

    // ------------------------------------------------------------ синхронизация с клиентом
    static void sync(ServerPlayer p, boolean open, String notice) {
        if (!ServerPlayNetworking.canSend(p, SyncPayload.TYPE)) return;
        View v = view(p);
        v.open = open;
        v.notice = notice;
        ServerPlayNetworking.send(p, new SyncPayload(GSON.toJson(v)));
    }

    private static View view(ServerPlayer p) {
        View v = new View();
        UUID id = p.getUUID();
        v.admin = isAdmin(p);
        v.voice = VOICE;
        TeamData my = teamOf(id);
        if (my != null) v.my = teamView(my, id, v.admin);
        for (TeamData t : teams.values()) {
            if (t.invited.containsKey(id)) v.invites.add(teamView(t, id, v.admin));
            if (t.access != Access.HIDDEN || t == my || v.admin) v.teams.add(teamView(t, id, v.admin));
        }
        for (ServerPlayer o : server.getPlayerList().getPlayers())
            if (teamOf(o.getUUID()) == null) v.solo.add(new Ref(o.getUUID().toString(), o.getScoreboardName()));
        return v;
    }

    private static TeamView teamView(TeamData t, UUID viewer, boolean admin) {
        TeamView tv = new TeamView();
        tv.id = t.id.toString();
        tv.name = t.name;
        tv.access = t.access;
        tv.settings = t.settings;
        tv.member = t.member(viewer) != null;
        tv.manage = admin || t.owner.equals(viewer);
        tv.observing = t.observers.contains(viewer);
        tv.requested = t.requests.containsKey(viewer);
        boolean coords = admin || (tv.member && t.settings.coords);
        for (MemberData m : t.members) {
            MemberView mv = new MemberView();
            mv.id = m.id.toString();
            mv.name = m.name;
            mv.role = m.role;
            mv.muted = m.muted;
            mv.owner = m.id.equals(t.owner);
            ServerPlayer sp = server.getPlayerList().getPlayer(m.id);
            mv.online = sp != null;
            if (sp != null && coords) {
                BlockPos b = sp.blockPosition();
                mv.hasPos = true;
                mv.x = b.getX(); mv.y = b.getY(); mv.z = b.getZ();
                var d = sp.level().dimension();
                mv.dim = d == Level.OVERWORLD ? "Мир" : d == Level.NETHER ? "Ад" : d == Level.END ? "Энд" : "Другое";
            }
            tv.members.add(mv);
        }
        if (tv.manage) {
            t.invited.forEach((k, n) -> tv.invited.add(new Ref(k.toString(), n)));
            t.requests.forEach((k, n) -> tv.requests.add(new Ref(k.toString(), n)));
        }
        return tv;
    }

    // ------------------------------------------------------------ действия из меню
    static void handle(ServerPlayer p, ActionPayload a) {
        Action act;
        try { act = Action.valueOf(a.action()); } catch (IllegalArgumentException e) { return; }
        switch (act) {
            case OPEN -> { sync(p, true, null); return; }
            case REFRESH -> { sync(p, false, null); return; }
            case CREATE -> { create(p, a.text(), access(a.target()), a.num()); return; }
            case LEAVE -> { leave(p); return; }
            default -> { }
        }
        TeamData t = byId(a.team());
        if (t == null || !sees(p, t)) { err(p, "Команда не найдена."); return; }
        UUID target = uuid(a.target());
        switch (act) {
            case RENAME -> rename(p, t, a.text());
            case ACCESS -> setAccess(p, t, access(a.target()));
            case SETTINGS -> setSettings(p, t, a.num());
            case INVITE -> invite(p, t, a.text());
            case UNINVITE -> { if (manage(p, t) && t.invited.remove(target) != null) changed(); }
            case ACCEPT, JOIN -> join(p, t);
            case DECLINE -> decline(p, t);
            case REQ_ACCEPT -> acceptRequest(p, t, target);
            case REQ_DECLINE -> {
                if (manage(p, t) && t.requests.remove(target) != null) {
                    tell(target, "Ваш запрос в команду «" + t.name + "» отклонён.");
                    changed();
                }
            }
            case KICK -> kick(p, t, target);
            case ROLE -> setRole(p, t, target, a.text(), a.num());
            case MUTE -> mute(p, t, target);
            case DELETE -> delete(p, t);
            case VOICE -> voice(p, t);
            case OBSERVE -> observe(p, t);
            default -> { }
        }
    }

    private static void add(TeamData t, UUID id, String name, String role) {
        MemberData m = new MemberData();
        m.id = id; m.name = name; m.role = role;
        t.members.add(m);
        t.invited.remove(id);
        for (TeamData o : teams.values()) o.requests.remove(id);
    }

    private static void remove(TeamData t) {
        teams.remove(t.id);
        if (VOICE) VoiceCompat.drop(t.id, onlineIds());
    }

    static void create(ServerPlayer p, String rawName, Access access, int bits) {
        if (teamOf(p.getUUID()) != null) { err(p, "Вы уже состоите в команде."); return; }
        String name = clean(rawName, 20);
        if (name.length() < 3) { err(p, "Название должно быть от 3 до 20 символов."); return; }
        if (byName(name) != null) { err(p, "Команда с таким названием уже есть."); return; }
        TeamData t = new TeamData();
        t.id = UUID.randomUUID();
        t.name = name;
        t.access = access;
        t.owner = p.getUUID();
        t.settings = Settings.of(bits);
        teams.put(t.id, t);
        add(t, p.getUUID(), p.getScoreboardName(), "Создатель");
        ok(p, "Команда «" + name + "» создана.");
        changed();
    }

    static void rename(ServerPlayer p, TeamData t, String rawName) {
        if (!manage(p, t)) return;
        String name = clean(rawName, 20);
        if (name.length() < 3) { err(p, "Название должно быть от 3 до 20 символов."); return; }
        TeamData other = byName(name);
        if (other != null && other != t) { err(p, "Команда с таким названием уже есть."); return; }
        t.name = name;
        if (VOICE) VoiceCompat.drop(t.id, onlineIds()); // группа пересоздастся с новым именем
        changed();
    }

    static void setAccess(ServerPlayer p, TeamData t, Access a) {
        if (!manage(p, t)) return;
        t.access = a;
        if (a != Access.PRIVATE) t.requests.clear();
        if (VOICE) VoiceCompat.drop(t.id, onlineIds());
        changed();
    }

    static void setSettings(ServerPlayer p, TeamData t, int bits) {
        if (!manage(p, t)) return;
        t.settings = Settings.of(bits);
        changed();
    }

    static void invite(ServerPlayer p, TeamData t, String nick) {
        if (!manage(p, t)) return;
        ServerPlayer target = server.getPlayerList().getPlayerByName(nick.strip());
        if (target == null) { err(p, "Игрок " + nick.strip() + " не в сети."); return; }
        UUID id = target.getUUID();
        String name = target.getScoreboardName();
        if (teamOf(id) != null) { err(p, name + " уже состоит в команде."); return; }
        if (t.requests.containsKey(id)) { acceptRequest(p, t, id); return; }
        if (t.invited.putIfAbsent(id, name) != null) { err(p, name + " уже приглашён."); return; }
        ok(p, "Приглашение отправлено: " + name);
        ok(target, "Вас пригласили в команду «" + t.name + "». Откройте меню команд или введите /tm accept " + t.name);
        changed();
    }

    static void join(ServerPlayer p, TeamData t) {
        UUID id = p.getUUID();
        String name = p.getScoreboardName();
        if (teamOf(id) != null) { err(p, "Сначала выйдите из своей команды."); return; }
        if (t.invited.containsKey(id) || t.access == Access.PUBLIC) {
            add(t, id, name, "Участник");
            broadcast(t, name + " вступил в команду.");
            changed();
        } else if (t.access == Access.HIDDEN) {
            err(p, "Команда не найдена.");
        } else if (t.requests.putIfAbsent(id, name) == null) {
            ok(p, "Запрос на вступление в «" + t.name + "» отправлен.");
            tell(t.owner, name + " просит принять его в команду (меню -> Приглашения).");
            changed();
        } else {
            err(p, "Запрос уже отправлен.");
        }
    }

    static void decline(ServerPlayer p, TeamData t) {
        if (t.invited.remove(p.getUUID()) == null) return;
        tell(t.owner, p.getScoreboardName() + " отклонил приглашение.");
        changed();
    }

    static void acceptRequest(ServerPlayer p, TeamData t, UUID id) {
        if (!manage(p, t)) return;
        String name = t.requests.remove(id);
        if (name == null) return;
        if (teamOf(id) != null) { err(p, name + " уже состоит в другой команде."); changed(); return; }
        add(t, id, name, "Участник");
        broadcast(t, name + " вступил в команду.");
        changed();
    }

    static void kick(ServerPlayer p, TeamData t, UUID id) {
        if (!manage(p, t)) return;
        MemberData m = t.member(id);
        if (m == null) return;
        if (id.equals(t.owner)) { err(p, "Создателя команды удалить нельзя."); return; }
        t.members.remove(m);
        if (VOICE) VoiceCompat.leave(id, t.id);
        tell(id, "Вас удалили из команды «" + t.name + "».");
        broadcast(t, m.name + " удалён из команды.");
        changed();
    }

    static void setRole(ServerPlayer p, TeamData t, UUID id, String rawRole, int pos) {
        if (!manage(p, t)) return;
        MemberData m = t.member(id);
        if (m == null) return;
        String role = clean(rawRole, 16);
        m.role = role.isEmpty() ? "Участник" : role;
        t.members.remove(m);
        t.members.add(Math.max(0, Math.min(t.members.size(), pos - 1)), m);
        changed();
    }

    static void mute(ServerPlayer p, TeamData t, UUID id) {
        if (!manage(p, t)) return;
        MemberData m = t.member(id);
        if (m == null) return;
        if (id.equals(t.owner)) { err(p, "Создателя команды замьютить нельзя."); return; }
        m.muted = !m.muted;
        tell(id, m.muted ? "Вам отключили чат команды." : "Вам снова доступен чат команды.");
        ok(p, m.name + (m.muted ? " замьючен в чате команды." : " размьючен."));
        changed();
    }

    static void transfer(ServerPlayer p, TeamData t, UUID id) {
        if (!manage(p, t)) return;
        MemberData m = t.member(id);
        if (m == null || id.equals(t.owner)) return;
        t.owner = id;
        broadcast(t, "Новый создатель команды: " + m.name);
        changed();
    }

    static void leave(ServerPlayer p) {
        UUID id = p.getUUID();
        TeamData t = teamOf(id);
        if (t == null) { err(p, "Вы не состоите в команде."); return; }
        if (t.members.size() == 1) {
            remove(t);
            ok(p, "Вы вышли, команда «" + t.name + "» распущена.");
            changed();
            return;
        }
        t.members.remove(t.member(id));
        if (t.owner.equals(id)) {
            MemberData next = t.members.get(0);
            t.owner = next.id;
            tell(next.id, "Теперь вы создатель команды «" + t.name + "».");
        }
        if (VOICE) VoiceCompat.leave(id, t.id);
        ok(p, "Вы вышли из команды «" + t.name + "».");
        broadcast(t, p.getScoreboardName() + " вышел из команды.");
        changed();
    }

    static void delete(ServerPlayer p, TeamData t) {
        if (!manage(p, t)) return;
        broadcast(t, "Команда «" + t.name + "» удалена.");
        if (t.member(p.getUUID()) == null) ok(p, "Команда «" + t.name + "» удалена.");
        remove(t);
        changed();
    }

    static void observe(ServerPlayer p, TeamData t) {
        if (!isAdmin(p)) return;
        UUID id = p.getUUID();
        boolean on = !t.observers.remove(id);
        if (on) t.observers.add(id);
        ok(p, on ? "Вы скрытно вошли в «" + t.name + "»: видите чат команды, вас никто не видит."
                 : "Вы скрытно вышли из «" + t.name + "».");
        sync(p, false, null);
    }

    static void voice(ServerPlayer p, TeamData t) {
        if (!VOICE) { err(p, "На сервере не установлен Simple Voice Chat."); return; }
        if (!mayVoice(p.getUUID(), t)) { err(p, "В голосовую группу этой команды могут зайти только её участники."); return; }
        String error = VoiceCompat.join(p.getUUID(), t);
        if (error != null) err(p, error);
        else ok(p, "Вы в голосовой группе команды «" + t.name + "».");
    }

    static void chat(ServerPlayer p, String text) {
        UUID id = p.getUUID();
        TeamData t = teamOf(id);
        boolean stealth = false;
        if (t == null) {
            for (TeamData o : teams.values()) if (o.observers.contains(id)) { t = o; stealth = true; break; }
        }
        if (t == null) { err(p, "Вы не состоите в команде."); return; }
        if (!t.settings.chat) { err(p, "Чат команды выключен в настройках."); return; }
        MemberData m = t.member(id);
        if (m != null && m.muted) { err(p, "Вы замьючены в чате команды."); return; }
        broadcast(t, Component.literal("[" + t.name + "] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(p.getScoreboardName() + (stealth ? " (админ)" : "")).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": " + clean(text, 256)).withStyle(ChatFormatting.GRAY)));
    }

    static void list(ServerPlayer p) {
        ok(p, "Команды на сервере:");
        for (TeamData t : teams.values())
            if (sees(p, t)) p.sendSystemMessage(Component.literal(" • " + t.name + " — " + t.access.title
                    + ", участников: " + t.members.size()).withStyle(ChatFormatting.GRAY));
    }
}