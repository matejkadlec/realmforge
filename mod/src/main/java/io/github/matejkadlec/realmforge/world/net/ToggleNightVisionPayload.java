package io.github.matejkadlec.realmforge.world.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player pressed the Night Vision key. An intent without fields: the server flips the sender's
 * own switch and nobody else's (architecture 17.3).
 */
public record ToggleNightVisionPayload() implements CustomPacketPayload {
    public static final ToggleNightVisionPayload INSTANCE = new ToggleNightVisionPayload();
    public static final Type<ToggleNightVisionPayload> TYPE = new Type<>(Realmforge.id("toggle_night_vision"));
    public static final StreamCodec<ByteBuf, ToggleNightVisionPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ToggleNightVisionPayload> type() {
        return TYPE;
    }
}
