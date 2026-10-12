package io.github.matejkadlec.realmforge.world.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player pressed the Dark Vision key. An intent without fields: the server flips the sender's
 * own switch and nobody else's (architecture 17.3).
 */
public record ToggleDarkVisionPayload() implements CustomPacketPayload {
    public static final ToggleDarkVisionPayload INSTANCE = new ToggleDarkVisionPayload();
    public static final Type<ToggleDarkVisionPayload> TYPE = new Type<>(Realmforge.id("toggle_dark_vision"));
    public static final StreamCodec<ByteBuf, ToggleDarkVisionPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ToggleDarkVisionPayload> type() {
        return TYPE;
    }
}
