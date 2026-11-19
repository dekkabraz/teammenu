package dev.teammenu;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import dev.teammenu.Model.Access;
import dev.teammenu.Model.TeamData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Загружается только самим Simple Voice Chat (entrypoint "voicechat"). */
public class VoiceCompat implements VoicechatPlugin {
    private static VoicechatServerApi api;
    private static final Map<UUID, UUID> groups = new HashMap<>(); // команда -> голосовая группа

    @Override public String getPluginId() { return "teammenu"; }

    @Override
    public void registerEvents(EventRegistration reg) {
        reg.registerEvent(VoicechatServerStartedEvent.class, e -> api = e.getVoicechat());
        reg.registerEvent(VoicechatServerStoppedEvent.class, e -> { api = null; groups.clear(); });
        reg.registerEvent(JoinGroupEvent.class, VoiceCompat::onJoin);
    }

    /** Не пускает посторонних в группы приватных и скрытых команд. */
    private static void onJoin(JoinGroupEvent e) {
        Group g = e.getGroup();
        VoicechatConnection c = e.getConnection();
        if (g == null || c == null) return;
        for (Map.Entry<UUID, UUID> en : groups.entrySet()) {
            if (!en.getValue().equals(g.getId())) continue;
            TeamData t = TeamManager.teams.get(en.getKey());
            if (t != null && !TeamManager.mayVoice(c.getPlayer().getUuid(), t)) e.cancel();
            return;
        }
    }

    static String join(UUID player, TeamData t) {
        if (api == null) return "Голосовой чат ещё не запущен.";
        VoicechatConnection c = api.getConnectionOf(player);
        if (c == null) return "У вас не подключён Simple Voice Chat.";
        UUID gid = groups.get(t.id);
        Group g = gid == null ? null : api.getGroup(gid);
        if (g == null) {
            g = api.groupBuilder()
                    .setPersistent(true)
                    .setName(t.name)
                    .setType(Group.Type.NORMAL)
                    .setHidden(t.access == Access.HIDDEN) // скрытую группу не видно в списке групп
                    .build();
            groups.put(t.id, g.getId());
        }
        c.setGroup(g);
        return null;
    }

    static void leave(UUID player, UUID team) {
        UUID gid = groups.get(team);
        if (api != null && gid != null) kick(player, gid);
    }

    static void drop(UUID team, Collection<UUID> online) {
        UUID gid = groups.remove(team);
        if (api == null || gid == null) return;
        for (UUID p : online) kick(p, gid);
        api.removeGroup(gid);
    }

    private static void kick(UUID player, UUID gid) {
        VoicechatConnection c = api.getConnectionOf(player);
        if (c != null && c.getGroup() != null && gid.equals(c.getGroup().getId())) c.setGroup(null);
    }
}