package io.github.matejkadlec.realmforge.spell.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: while the player's Highlight Spell Target is on, the mob an enemy spell of theirs would go to now
 * (RLM-468; {@code spell/TargetHighlight}), sent when it changes. The client draws the target circle under it.
 *
 * @param target the entity the circle goes under, or empty for none
 */
public record HighlightedTargetPayload(Optional<Integer> target) implements CustomPacketPayload {
    public static final Type<HighlightedTargetPayload> TYPE = new Type<>(Realmforge.id("highlighted_target"));
    public static final StreamCodec<ByteBuf, HighlightedTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), HighlightedTargetPayload::target,
            HighlightedTargetPayload::new);

    @Override
    public Type<HighlightedTargetPayload> type() {
        return TYPE;
    }
}
