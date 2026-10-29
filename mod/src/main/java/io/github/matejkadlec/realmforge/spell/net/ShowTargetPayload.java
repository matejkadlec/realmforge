package io.github.matejkadlec.realmforge.spell.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player's Show target key went down or up (RLM-468; architecture 11.6, 17.3). While it is
 * down, the server names the mob an enemy spell of theirs would go to now ({@code spell/ShowTarget}).
 *
 * @param held whether the key is down
 */
public record ShowTargetPayload(boolean held) implements CustomPacketPayload {
    public static final Type<ShowTargetPayload> TYPE = new Type<>(Realmforge.id("show_target"));
    public static final StreamCodec<ByteBuf, ShowTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ShowTargetPayload::held,
            ShowTargetPayload::new);

    @Override
    public Type<ShowTargetPayload> type() {
        return TYPE;
    }
}
