package dev.teammenu;

import java.util.*;

public final class Model {
    private Model() {}

    public enum Access {
        HIDDEN("Скрытая", "Видна только участникам и админам"),
        PRIVATE("Приватная", "Видна всем, вступление по заявке или приглашению"),
        PUBLIC("Публичная", "Вступить может любой игрок");

        public final String title, hint;
        Access(String title, String hint) { this.title = title; this.hint = hint; }
        public Access next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static class Settings {
        public boolean chat = true, coords = true, nametags = false, friendlyFire = false, seeInvisible = true;

        public int bits() {
            return (chat ? 1 : 0) | (coords ? 2 : 0) | (nametags ? 4 : 0) | (friendlyFire ? 8 : 0) | (seeInvisible ? 16 : 0);
        }
        public static Settings of(int b) {
            Settings s = new Settings();
            s.chat = (b & 1) != 0; s.coords = (b & 2) != 0; s.nametags = (b & 4) != 0;
            s.friendlyFire = (b & 8) != 0; s.seeInvisible = (b & 16) != 0;
            return s;
        }
    }

    // ---------- данные сервера (сохраняются в teammenu.json в папке мира)
    public static class MemberData {
        public UUID id; public String name; public String role = "Участник"; public boolean muted;
    }

    public static class TeamData {
        public UUID id, owner;
        public String name;
        public Access access = Access.PRIVATE;
        public Settings settings = new Settings();
        public List<MemberData> members = new ArrayList<>();
        public Map<UUID, String> invited = new LinkedHashMap<>(), requests = new LinkedHashMap<>();
        public transient Set<UUID> observers = new HashSet<>(); // админы, вошедшие скрытно

        public MemberData member(UUID id) {
            for (MemberData m : members) if (m.id.equals(id)) return m;
            return null;
        }
        public MemberData memberByName(String n) {
            for (MemberData m : members) if (m.name.equalsIgnoreCase(n)) return m;
            return null;
        }
    }

    // ---------- то, что сервер отправляет клиенту
    public static class Ref {
        public String id, name;
        public Ref() {}
        public Ref(String id, String name) { this.id = id; this.name = name; }
    }

    public static class MemberView {
        public String id, name, role, dim;
        public boolean online, muted, owner, hasPos;
        public int x, y, z;
    }

    public static class TeamView {
        public String id, name;
        public Access access = Access.PRIVATE;
        public Settings settings = new Settings();
        public boolean manage, member, observing, requested;
        public List<MemberView> members = new ArrayList<>();
        public List<Ref> invited = new ArrayList<>(), requests = new ArrayList<>();
    }

    public static class View {
        public boolean open, admin, voice;
        public String notice;
        public TeamView my;
        public List<TeamView> teams = new ArrayList<>(), invites = new ArrayList<>();
        public List<Ref> solo = new ArrayList<>();
    }
}