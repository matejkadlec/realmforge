package io.github.matejkadlec.realmforge.combat.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: something attacked the receiving player, a hit that landed or one the player dodged (architecture
 * 9.1, 17.3). The client points an arc around the crosshair at it for a moment ({@code client/hud/HitDirectionHud}),
 * following the attacker while the client has it, from where the attack came otherwise.
 *
 * @param attackerId the attacking entity, or -1 when the attack had only a position (an explosion)
 * @param x where the attack came from, east-west
 * @param z where the attack came from, north-south
 */
public record HitDirectionPayload(int attackerId, double x, double z) implements CustomPacketPayload {
    public static final Type<HitDirectionPayload> TYPE = new Type<>(Realmforge.id("hit_direction"));
    public static final StreamCodec<ByteBuf, HitDirectionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HitDirectionPayload::attackerId,
            ByteBufCodecs.DOUBLE, HitDirectionPayload::x,
            ByteBufCodecs.DOUBLE, HitDirectionPayload::z,
            HitDirectionPayload::new);

    @Override
    public Type<HitDirectionPayload> type() {
        return TYPE;
    }
}
