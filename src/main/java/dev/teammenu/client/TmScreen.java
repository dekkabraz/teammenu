package dev.teammenu.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public abstract class TmScreen extends Screen {
    protected static final int BORDER = 0xFF2F4A63, PANEL = 0xF0121A24, HEADER = 0xFF1A2836, ACCENT = 0xFF4FC3F7,
            ROW = 0x30FFFFFF, TEXT = 0xFFFFFFFF, DIM = 0xFF9AA7B4, GOLD = 0xFFFFC857, GREEN = 0xFF7CE38B, RED = 0xFFFF7B72;

    protected record Act(String label, Runnable run) {}

    private static String notice;
    private static long noticeUntil;

    protected final Screen parent;
    protected int pw, ph, left, top, scroll;
    private int maxScroll;
    private String lastSig = "";
    private GuiGraphics g;
    private final List<Runnable> back = new ArrayList<>(), front = new ArrayList<>();

    protected TmScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    protected int panelWidth() { return 300; }
    protected int panelHeight() { return 200; }
    protected abstract void build();
    /** Строка-«отпечаток» содержимого: если после синхронизации изменилась, экран перестраивается. */
    protected String sig() { return ""; }
    protected String heading() { return title.getString(); }

    public static void showNotice(String s) { notice = s; noticeUntil = System.currentTimeMillis() + 4000; }

    public void onSync() { if (!sig().equals(lastSig)) rebuildWidgets(); }

    @Override
    protected void init() {
        back.clear();
        front.clear();
        maxScroll = 0;
        pw = Math.min(panelWidth(), width - 16);
        ph = Math.min(panelHeight(), height - 16);
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        lastSig = sig();
        build();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        this.g = g;
        g.fill(left - 1, top - 1, left + pw + 1, top + ph + 1, BORDER);
        g.fill(left, top, left + pw, top + ph, PANEL);
        g.fill(left, top, left + pw, top + 22, HEADER);
        g.fill(left, top + 22, left + pw, top + 23, ACCENT);
        back.forEach(Runnable::run);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.g = g;
        g.drawCenteredString(font, heading(), left + pw / 2, top + 7, TEXT);
        front.forEach(Runnable::run);
        if (notice != null && System.currentTimeMillis() < noticeUntil)
            g.drawCenteredString(font, notice, width / 2, Math.min(height - 10, top + ph + 5), RED);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int next = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(dy)));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    protected void open(Screen s) { minecraft.setScreen(s); }

    // ---------- рисование
    protected void text(String s, int x, int y, int color) { g.drawString(font, s, x, y, color); }
    protected void label(String s, int x, int y, int color) { front.add(() -> g.drawString(font, s, x, y, color)); }
    protected void labelCentered(String s, int x, int y, int color) { front.add(() -> g.drawCenteredString(font, s, x, y, color)); }
    protected void paint(Runnable r) { front.add(r); }
    protected void stripe(int y, int h) { back.add(() -> g.fill(left + 6, y, left + pw - 6, y + h, ROW)); }
    protected String fit(String s, int w) { return font.width(s) <= w ? s : font.plainSubstrByWidth(s, w - 6) + "…"; }

    /** Голова скина игрока 16x16. */
    protected void face(String uuid, int x, int y) {
        front.add(() -> {
            UUID id = UUID.fromString(uuid);
            PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
            var skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
            PlayerFaceRenderer.draw(g, skin, x, y, 16);
        });
    }

    // ---------- виджеты
    protected Button btn(Component label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, w, 20).build());
    }
    protected Button btn(String label, int x, int y, int w, Runnable action) {
        return btn(Component.literal(label), x, y, w, action);
    }
    protected EditBox box(int x, int y, int w, String value, int max, Consumer<String> onChange) {
        EditBox b = new EditBox(font, x, y, w, 20, Component.empty());
        b.setMaxLength(max);
        b.setValue(value);
        b.setResponder(onChange);
        return addRenderableWidget(b);
    }
    /** Ряд кнопок одинаковой ширины на всю панель. */
    protected void row(int y, List<Act> acts) {
        int n = acts.size(), gap = 4, w = (pw - 16 - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) btn(acts.get(i).label(), left + 8 + i * (w + gap), y, w, acts.get(i).run());
    }
    protected static Component toggleText(String name, boolean on) {
        return Component.literal(name + ": ").append(
                Component.literal(on ? "ВКЛ" : "ВЫКЛ").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    /** Прокручиваемый колесом список строк высотой 26. */
    protected void list(List<IntConsumer> items, int y0, int y1) {
        int rows = Math.max(1, (y1 - y0) / 26);
        maxScroll = Math.max(0, items.size() - rows);
        scroll = Math.min(scroll, maxScroll);
        for (int i = 0; i < rows && i + scroll < items.size(); i++) items.get(i + scroll).accept(y0 + i * 26);
        if (maxScroll > 0) {
            int h = y1 - y0, th = Math.max(8, h * rows / items.size()), ty = y0 + (h - th) * scroll / maxScroll, x = left + pw - 4;
            back.add(() -> { g.fill(x, y0, x + 2, y1, ROW); g.fill(x, ty, x + 2, ty + th, ACCENT); });
        }
    }
}