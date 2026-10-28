package io.github.matejkadlec.realmforge.spell.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: where the target circle of the player's own cast-time or channelled spell goes (aim assistance,
 * RLM-345, Q4), sent when it changes during the cast: under the cast's target, or under the mob a bolt that follows
 * the crosshair would be assisted to now ({@code spell/Casting}). The client draws it until its cast ends.
 *
 * @param target the entity the circle goes under, or empty for none
 */
public record CastTargetPayload(Optional<Integer> target) implements CustomPacketPayload {
    public static final Type<CastTargetPayload> TYPE = new Type<>(Realmforge.id("cast_target"));
    public static final StreamCodec<ByteBuf, CastTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), CastTargetPayload::target,
            CastTargetPayload::new);

    @Override
    public Type<CastTargetPayload> type() {
        return TYPE;
    }
}
