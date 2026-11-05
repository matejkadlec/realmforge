package io.github.matejkadlec.realmforge.character.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player chose a specialization in the spellbook's Spell Tree tab (architecture 10.1, 17.3;
 * RLM-333). An intent: {@code SpellTrees} checks it again on the server.
 */
public record PickSpecializationPayload(String specialization) implements CustomPacketPayload {
    public static final Type<PickSpecializationPayload> TYPE = new Type<>(Realmforge.id("pick_specialization"));
    public static final StreamCodec<ByteBuf, PickSpecializationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PickSpecializationPayload::specialization, PickSpecializationPayload::new);

    @Override
    public Type<PickSpecializationPayload> type() {
        return TYPE;
    }
}
