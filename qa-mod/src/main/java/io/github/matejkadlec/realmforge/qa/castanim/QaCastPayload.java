package io.github.matejkadlec.realmforge.qa.castanim;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the player with {@code entityId} casts for {@code ticks} more ticks, or stopped casting when
 * {@code ticks} is 0. Sent to the caster and to every player tracking them, like the planned {@code CastStartedPayload}
 * (architecture 11.2).
 */
public record QaCastPayload(int entityId, int ticks) implements CustomPacketPayload {
    public static final Type<QaCastPayload> TYPE = new Type<>(RealmforgeQa.id("cast"));
    public static final StreamCodec<ByteBuf, QaCastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QaCastPayload::entityId,
            ByteBufCodecs.VAR_INT, QaCastPayload::ticks,
            QaCastPayload::new);

    public static QaCastPayload stop(int entityId) {
        return new QaCastPayload(entityId, 0);
    }

    public boolean isStart() {
        return ticks > 0;
    }

    @Override
    public Type<QaCastPayload> type() {
        return TYPE;
    }
}
