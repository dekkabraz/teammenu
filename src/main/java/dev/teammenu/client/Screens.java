package dev.teammenu.client;

import dev.teammenu.Model.*;
import dev.teammenu.Net.Action;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import static dev.teammenu.client.TeamMenuClient.find;
import static dev.teammenu.client.TeamMenuClient.send;

public final class Screens {
    private Screens() {}

    private static View view() { return TeamMenuClient.view; }

    // ───────────────────────── Главное меню (игрок без команды)
    public static class Main extends TmScreen {
        public Main() { super("Меню команд", null); }
        @Override protected int panelWidth() { return 240; }
        @Override protected int panelHeight() { return 172; }
        @Override protected String sig() { return "" + view().invites.size(); }

        @Override
        protected void build() {
            int x = left + 20, w = pw - 40, y = top + 32, n = view().invites.size();
            btn("Команды", x, y, w, () -> open(new Teams(this)));
            btn("Приглашения" + (n > 0 ? " (" + n + ")" : ""), x, y + 24, w, () -> open(new Invites(this)));
            btn("Создать команду", x, y + 48, w, () -> open(new Create(this)));
            btn("Доступные команды", x, y + 72, w, () -> open(new Help(this, false, false)));
            btn("Закрыть", x, top + ph - 28, w, this::onClose);
        }
    }

    // ───────────────────────── Создание команды
    public static class Create extends TmScreen {
        private String name = "";
        private Access access = Access.PRIVATE;
        private Settings settings = new Settings();

        public Create(Screen parent) { super("Создать команду", parent); }
        @Override protected int panelWidth() { return 280; }
        @Override protected int panelHeight() { return 172; }

        @Override
        protected void build() {
            int x = left + 20, w = pw - 40, y = top + 32;
            label("Название команды", x, y, DIM);
            setInitialFocus(box(x, y + 11, w, name, 20, s -> name = s));
            btn("Доступ: " + access.title, x, y + 37, w, () -> { access = access.next(); rebuildWidgets(); });
            labelCentered(access.hint, left + pw / 2, y + 61, DIM);
            btn("Настроить", x, y + 76, w, () -> open(new SettingsScreen(this, settings, s -> settings = s)));
            row(top + ph - 28, List.of(
                    new Act("Назад", this::onClose),
                    new Act("Создать", () -> send(Action.CREATE, "", access.name(), name, settings.bits()))));
        }
    }

    // ───────────────────────── Настройки команды
    public static class SettingsScreen extends TmScreen {
        private final Settings s;
        private final Consumer<Settings> done;

        public SettingsScreen(Screen parent, Settings initial, Consumer<Settings> done) {
            super("Настройки команды", parent);
            this.s = Settings.of(initial.bits());
            this.done = done;
        }
        @Override protected int panelWidth() { return 280; }
        @Override protected int panelHeight() { return 178; }

        @Override
        protected void build() {
            int y = top + 30;
            toggle(y, "Чат команды (/tc)", () -> s.chat, v -> s.chat = v);
            toggle(y + 22, "Координаты участников", () -> s.coords, v -> s.coords = v);
            toggle(y + 44, "Ники видны только своей команде", () -> s.nametags, v -> s.nametags = v);
            toggle(y + 66, "Урон по своим", () -> s.friendlyFire, v -> s.friendlyFire = v);
            toggle(y + 88, "Видеть невидимых союзников", () -> s.seeInvisible, v -> s.seeInvisible = v);
            btn("Готово", left + 8, top + ph - 28, pw - 16, () -> { done.accept(s); onClose(); });
        }

        private void toggle(int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
            Button[] b = new Button[1];
            b[0] = btn(toggleText(name, get.getAsBoolean()), left + 8, y, pw - 16, () -> {
                set.accept(!get.getAsBoolean());
                b[0].setMessage(toggleText(name, get.getAsBoolean()));
            });
        }
    }

    // ───────────────────────── Список команд и соло-игроков
    public static class Teams extends TmScreen {
        public Teams(Screen parent) { super("Команды и игроки", parent); }
        @Override protected int panelWidth() { return 380; }
        @Override protected int panelHeight() { return 232; }

        @Override
        protected String sig() {
            View v = view();
            StringBuilder s = new StringBuilder().append(v.admin).append(v.my == null ? "" : v.my.id + v.my.invited.size());
            for (TeamView t : v.teams) s.append(t.id).append(t.name).append(t.access).append(t.members.size()).append(t.requested);
            for (Ref r : v.solo) s.append(r.id);
            return s.toString();
        }

        @Override
        protected void build() {
            View v = view();
            TeamView my = v.my;
            int bx = left + pw - 96;
            List<IntConsumer> items = new ArrayList<>();
            if (v.teams.isEmpty()) items.add(y -> label("Команд пока нет", left + 12, y + 9, DIM));
            for (TeamView t : v.teams) items.add(y -> {
                stripe(y, 24);
                label(t.name, left + 12, y + 3, TEXT);
                label(t.access.title, left + 18 + font.width(t.name), y + 3,
                        t.access == Access.PUBLIC ? GREEN : t.access == Access.PRIVATE ? GOLD : RED);
                String names = t.members.stream().map(m -> m.name).collect(Collectors.joining(", "));
                label(fit("Участники (" + t.members.size() + "): " + names, pw - 120), left + 12, y + 14, DIM);
                if (v.admin || t.member) btn("Открыть", bx, y + 2, 84, () -> open(new TeamScreen(t.id, this)));
                else if (my == null && t.access == Access.PUBLIC) btn("Вступить", bx, y + 2, 84, () -> send(Action.JOIN, t.id, "", "", 0));
                else if (my == null && t.access == Access.PRIVATE)
                    btn(t.requested ? "Отправлено" : "Запрос", bx, y + 2, 84, () -> send(Action.JOIN, t.id, "", "", 0)).active = !t.requested;
            });
            items.add(y -> label("Игроки без команды: " + v.solo.size(), left + 12, y + 9, ACCENT));
            for (Ref r : v.solo) items.add(y -> {
                stripe(y, 24);
                face(r.id, left + 10, y + 4);
                label(r.name, left + 32, y + 8, TEXT);
                if (my != null && my.manage && my.member) {
                    boolean invited = my.invited.stream().anyMatch(i -> i.id.equals(r.id));
                    btn(invited ? "Приглашён" : "Пригласить", bx, y + 2, 84, () -> send(Action.INVITE, my.id, "", r.name, 0)).active = !invited;
                }
            });
            list(items, top + 30, top + ph - 32);
            row(top + ph - 28, List.of(new Act("Назад", this::onClose)));
        }
    }

    // ───────────────────────── Приглашения, пришедшие игроку
    public static class Invites extends TmScreen {
        public Invites(Screen parent) { super("Приглашения", parent); }
        @Override protected int panelWidth() { return 340; }
        @Override protected int panelHeight() { return 206; }
        @Override protected String sig() { return view().invites.stream().map(t -> t.id + t.name).collect(Collectors.joining()); }

        @Override
        protected void build() {
            List<IntConsumer> items = new ArrayList<>();
            if (view().invites.isEmpty()) items.add(y -> labelCentered("Приглашений пока нет", left + pw / 2, y + 9, DIM));
            for (TeamView t : view().invites) items.add(y -> {
                stripe(y, 24);
                label(t.name, left + 12, y + 3, TEXT);
                label(t.access.title + " · участников: " + t.members.size(), left + 12, y + 14, DIM);
                btn("Принять", left + pw - 168, y + 2, 76, () -> send(Action.ACCEPT, t.id, "", "", 0));
                btn("Отклонить", left + pw - 88, y + 2, 76, () -> send(Action.DECLINE, t.id, "", "", 0));
            });
            list(items, top + 30, top + ph - 32);
            row(top + ph - 28, List.of(new Act("Назад", this::onClose)));
        }
    }

    // ───────────────────────── Меню команды
    public static class TeamScreen extends TmScreen {
        private final String id;

        public TeamScreen(String id, Screen parent) { super("Команда", parent); this.id = id; }
        @Override protected int panelWidth() { return 410; }
        @Override protected int panelHeight() { return 252; }

        private TeamView team() { return find(id); }

        @Override
        protected String heading() {
            TeamView t = team();
            return t == null ? "Команда" : t.name + "  ·  " + t.access.title;
        }

        @Override
        protected String sig() {
            TeamView t = team();
            if (t == null) return "-";
            StringBuilder s = new StringBuilder(t.name).append(t.access).append(t.manage).append(t.member)
                    .append(t.observing).append(view().voice).append(t.invited.size()).append(t.requests.size());
            for (MemberView m : t.members) s.append(m.id).append(m.role).append(m.muted).append(m.owner);
            return s.toString();
        }

        @Override
        public void onSync() {
            if (team() == null) minecraft.setScreen(TeamMenuClient.root());
            else super.onSync();
        }

        @Override
        protected void build() {
            TeamView t = team();
            if (t == null) { row(top + ph - 28, List.of(new Act("Назад", this::onClose))); return; }
            int y = top + 28;

            if (t.manage) { // управление: создатель и админ
                row(y, List.of(
                        new Act("Название", () -> open(new Name(this, t))),
                        new Act("Настройки", () -> open(new SettingsScreen(this, t.settings,
                                s -> send(Action.SETTINGS, t.id, "", "", s.bits())))),
                        new Act("Доступ: " + t.access.title, () -> send(Action.ACCESS, t.id, t.access.next().name(), "", 0)),
                        new Act("Удалить", () -> open(new Confirm(this, "Удалить команду «" + t.name + "»?",
                                () -> send(Action.DELETE, t.id, "", "", 0))))));
                y += 24;
            }

            List<Act> second = new ArrayList<>();
            if (view().voice && (t.member || t.manage || t.access == Access.PUBLIC))
                second.add(new Act("Войс команды", () -> send(Action.VOICE, t.id, "", "", 0)));
            second.add(new Act("Все команды", () -> open(new Teams(this))));
            if (t.member) second.add(new Act("Выйти", () -> open(new Confirm(this, "Выйти из команды «" + t.name + "»?",
                    () -> send(Action.LEAVE, "", "", "", 0)))));
            else if (view().admin) second.add(new Act(t.observing ? "Скрытно: в команде" : "Войти скрытно",
                    () -> send(Action.OBSERVE, t.id, "", "", 0)));
            row(y, second);
            y += 26;

            List<IntConsumer> items = new ArrayList<>();
            for (int i = 0; i < t.members.size(); i++) {
                MemberView m = t.members.get(i);
                int index = i;
                items.add(ry -> {
                    stripe(ry, 24);
                    face(m.id, left + 10, ry + 4);
                    paint(() -> memberText(m.id, ry)); // текст берётся из свежих данных каждый кадр
                    if (!t.manage) return;
                    int x = left + pw - 12;
                    if (!m.owner) {
                        x -= 20;
                        btn("✕", x, ry + 2, 20, () -> open(new Confirm(this, "Вы уверены, что хотите удалить " + m.name + "?",
                                () -> send(Action.KICK, t.id, m.id, "", 0))));
                        x -= 54;
                        btn(m.muted ? "Размут" : "Мут", x, ry + 2, 50, () -> send(Action.MUTE, t.id, m.id, "", 0));
                    }
                    x -= 54;
                    btn("+ Роль", x, ry + 2, 50, () -> open(new Role(this, t, m, index + 1)));
                });
            }
            list(items, y, top + ph - 32);

            List<Act> bottom = new ArrayList<>();
            bottom.add(new Act("Готово", () -> minecraft.setScreen(null)));
            if (t.manage) {
                int n = t.requests.size();
                bottom.add(new Act("Приглашения" + (n > 0 ? " (" + n + ")" : ""), () -> open(new TeamInvites(this, t.id))));
            }
            bottom.add(new Act("Доступные команды", () -> open(new Help(this, true, t.manage))));
            row(top + ph - 28, bottom);
        }

        private void memberText(String memberId, int y) {
            TeamView t = team();
            if (t == null) return;
            for (MemberView m : t.members) {
                if (!m.id.equals(memberId)) continue;
                text(m.name, left + 32, y + 3, m.online ? TEXT : DIM);
                String pos = m.hasPos ? m.x + " " + m.y + " " + m.z + " · " + m.dim : m.online ? "координаты скрыты" : "не в сети";
                text(pos, left + 38 + font.width(m.name), y + 3, m.hasPos ? GREEN : DIM);
                text((m.owner ? "★ " : "") + m.role + (m.muted ? "  (мут)" : ""), left + 32, y + 14, m.owner ? GOLD : ACCENT);
            }
        }
    }

    // ───────────────────────── Запросы и приглашения команды (для создателя)
    public static class TeamInvites extends TmScreen {
        private final String id;
        private String nick = "";

        public TeamInvites(Screen parent, String id) { super("Приглашения команды", parent); this.id = id; }
        @Override protected int panelWidth() { return 340; }
        @Override protected int panelHeight() { return 240; }

        @Override
        protected String sig() {
            TeamView t = find(id);
            if (t == null) return "-";
            return t.requests.stream().map(r -> r.id).collect(Collectors.joining()) + "|"
                    + t.invited.stream().map(r -> r.id).collect(Collectors.joining());
        }

        @Override
        public void onSync() {
            if (find(id) == null) minecraft.setScreen(TeamMenuClient.root());
            else super.onSync();
        }

        @Override
        protected void build() {
            TeamView t = find(id);
            if (t == null) { row(top + ph - 28, List.of(new Act("Назад", this::onClose))); return; }
            label("Пригласить игрока по нику", left + 12, top + 30, DIM);
            box(left + 12, top + 41, pw - 128, nick, 16, s -> nick = s);
            btn("Пригласить", left + pw - 112, top + 41, 100, () -> {
                if (nick.isBlank()) return;
                send(Action.INVITE, t.id, "", nick, 0);
                nick = "";
                rebuildWidgets();
            });

            List<IntConsumer> items = new ArrayList<>();
            items.add(y -> label("Запросы на вступление: " + t.requests.size(), left + 12, y + 9, ACCENT));
            for (Ref r : t.requests) items.add(y -> {
                stripe(y, 24);
                face(r.id, left + 10, y + 4);
                label(r.name, left + 32, y + 8, TEXT);
                btn("Принять", left + pw - 168, y + 2, 76, () -> send(Action.REQ_ACCEPT, t.id, r.id, "", 0));
                btn("Отклонить", left + pw - 88, y + 2, 76, () -> send(Action.REQ_DECLINE, t.id, r.id, "", 0));
            });
            items.add(y -> label("Уже приглашены: " + t.invited.size(), left + 12, y + 9, ACCENT));
            for (Ref r : t.invited) items.add(y -> {
                stripe(y, 24);
                face(r.id, left + 10, y + 4);
                label(r.name, left + 32, y + 8, TEXT);
                btn("Отменить", left + pw - 88, y + 2, 76, () -> send(Action.UNINVITE, t.id, r.id, "", 0));
            });
            list(items, top + 68, top + ph - 32);
            row(top + ph - 28, List.of(new Act("Назад", this::onClose)));
        }
    }

    // ───────────────────────── Роль и место участника в списке
    public static class Role extends TmScreen {
        private final TeamView t;
        private final MemberView m;
        private String role;
        private int pos;

        public Role(Screen parent, TeamView t, MemberView m, int pos) {
            super("Роль: " + m.name, parent);
            this.t = t; this.m = m; this.pos = pos; this.role = m.role;
        }
        @Override protected int panelWidth() { return 260; }
        @Override protected int panelHeight() { return 146; }

        @Override
        protected void build() {
            int x = left + 20, w = pw - 40, y = top + 32, n = t.members.size();
            label("Название роли", x, y, DIM);
            setInitialFocus(box(x, y + 11, w, role, 16, s -> role = s));
            label("Место в списке участников", x, y + 38, DIM);
            btn("▲", x, y + 49, 30, () -> { pos = Math.max(1, pos - 1); rebuildWidgets(); });
            btn("▼", x + w - 30, y + 49, 30, () -> { pos = Math.min(n, pos + 1); rebuildWidgets(); });
            labelCentered(pos + " из " + n, left + pw / 2, y + 55, TEXT);
            row(top + ph - 28, List.of(
                    new Act("Отмена", this::onClose),
                    new Act("Готово", () -> { send(Action.ROLE, t.id, m.id, role, pos); onClose(); })));
        }
    }

    // ───────────────────────── Переименование
    public static class Name extends TmScreen {
        private final TeamView t;
        private String name;

        public Name(Screen parent, TeamView t) { super("Название команды", parent); this.t = t; this.name = t.name; }
        @Override protected int panelWidth() { return 260; }
        @Override protected int panelHeight() { return 100; }

        @Override
        protected void build() {
            setInitialFocus(box(left + 20, top + 34, pw - 40, name, 20, s -> name = s));
            row(top + ph - 28, List.of(
                    new Act("Отмена", this::onClose),
                    new Act("Сохранить", () -> { send(Action.RENAME, t.id, "", name, 0); onClose(); })));
        }
    }

    // ───────────────────────── Подтверждение
    public static class Confirm extends TmScreen {
        private final String message;
        private final Runnable yes;

        public Confirm(Screen parent, String message, Runnable yes) {
            super("Подтверждение", parent);
            this.message = message; this.yes = yes;
        }
        @Override protected int panelWidth() { return 300; }
        @Override protected int panelHeight() { return 96; }

        @Override
        protected void build() {
            labelCentered(fit(message, pw - 16), left + pw / 2, top + 40, TEXT);
            row(top + ph - 28, List.of(
                    new Act("Да", () -> { yes.run(); onClose(); }),
                    new Act("Отмена", this::onClose)));
        }
    }

    // ───────────────────────── Доступные чат-команды
    public static class Help extends TmScreen {
        private final boolean member, manage;

        public Help(Screen parent, boolean member, boolean manage) {
            super("Доступные команды", parent);
            this.member = member; this.manage = manage;
        }
        @Override protected int panelWidth() { return 390; }
        @Override protected int panelHeight() { return 190; }

        @Override
        protected void build() {
            List<String[]> rows = new ArrayList<>();
            if (member) {
                rows.add(new String[]{"/tc <текст>", "написать в чат команды"});
                rows.add(new String[]{"/tm leave", "выйти из команды"});
            } else {
                rows.add(new String[]{"/tm create <название>", "создать приватную команду"});
                rows.add(new String[]{"/tm join <команда>", "вступить или отправить запрос"});
                rows.add(new String[]{"/tm accept <команда>", "принять приглашение"});
                rows.add(new String[]{"/tm deny <команда>", "отклонить приглашение"});
            }
            rows.add(new String[]{"/tm list", "список команд"});
            if (manage) {
                rows.add(new String[]{"/tm invite <ник>", "пригласить игрока"});
                rows.add(new String[]{"/tm kick <ник>", "удалить участника"});
                rows.add(new String[]{"/tm mute <ник>", "мут / размут в чате команды"});
                rows.add(new String[]{"/tm transfer <ник>", "передать права создателя"});
                rows.add(new String[]{"/tm delete", "удалить команду (с подтверждением)"});
            }
            int y = top + 32;
            for (String[] r : rows) {
                label(r[0], left + 12, y, ACCENT);
                label(r[1], left + 150, y, DIM);
                y += 13;
            }
            row(top + ph - 28, List.of(new Act("Назад", this::onClose)));
        }
    }
}