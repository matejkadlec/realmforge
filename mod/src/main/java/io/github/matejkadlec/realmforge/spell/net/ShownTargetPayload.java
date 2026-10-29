package io.github.matejkadlec.realmforge.spell.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: while the player holds the Show target key, the mob an enemy spell of theirs would go to now
 * (RLM-468; {@code spell/ShowTarget}), sent when it changes. The client draws the target circle under it.
 *
 * @param target the entity the circle goes under, or empty for none
 */
public record ShownTargetPayload(Optional<Integer> target) implements CustomPacketPayload {
    public static final Type<ShownTargetPayload> TYPE = new Type<>(Realmforge.id("shown_target"));
    public static final StreamCodec<ByteBuf, ShownTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), ShownTargetPayload::target,
            ShownTargetPayload::new);

    @Override
    public Type<ShownTargetPayload> type() {
        return TYPE;
    }
}
