package dev.teammenu;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class Net {
    private Net() {}

    public enum Action {
        OPEN, REFRESH, CREATE, RENAME, ACCESS, SETTINGS, INVITE, UNINVITE, ACCEPT, DECLINE, JOIN,
        REQ_ACCEPT, REQ_DECLINE, KICK, ROLE, MUTE, LEAVE, DELETE, VOICE, OBSERVE
    }

    /** Клиент -> сервер: любое действие из меню. */
    public record ActionPayload(String action, String team, String target, String text, int num) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teammenu", "action"));
        public static final StreamCodec<ByteBuf, ActionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ActionPayload::action,
                ByteBufCodecs.STRING_UTF8, ActionPayload::team,
                ByteBufCodecs.STRING_UTF8, ActionPayload::target,
                ByteBufCodecs.STRING_UTF8, ActionPayload::text,
                ByteBufCodecs.VAR_INT, ActionPayload::num,
                ActionPayload::new);
        @Override public Type<ActionPayload> type() { return TYPE; }
    }

    /** Сервер -> клиент: всё, что игроку можно видеть (JSON с Model.View). */
    public record SyncPayload(String json) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("teammenu", "sync"));
        public static final StreamCodec<ByteBuf, SyncPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(1 << 18), SyncPayload::json, SyncPayload::new);
        @Override public Type<SyncPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
    }
}
