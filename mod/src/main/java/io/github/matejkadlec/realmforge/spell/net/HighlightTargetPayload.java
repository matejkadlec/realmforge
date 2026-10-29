package io.github.matejkadlec.realmforge.spell.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player's Highlight Spell Target went on or off, by the key's toggle or hold (RLM-468;
 * architecture 11.6, 17.3). While it is on, the server names the mob an enemy spell of theirs would go to now
 * ({@code spell/TargetHighlight}).
 *
 * @param on whether the highlight is on
 */
public record HighlightTargetPayload(boolean on) implements CustomPacketPayload {
    public static final Type<HighlightTargetPayload> TYPE = new Type<>(Realmforge.id("highlight_target"));
    public static final StreamCodec<ByteBuf, HighlightTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HighlightTargetPayload::on,
            HighlightTargetPayload::new);

    @Override
    public Type<HighlightTargetPayload> type() {
        return TYPE;
    }
}
