package io.github.matejkadlec.realmforge.character.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * Client to server: the player clicked the {@code +} beside a primary stat on the character sheet, to spend one stat
 * point on it (architecture 10.2, 17.3; RLM-196). An intent: {@code StatPoints} checks it again on the server.
 */
public record SpendStatPointPayload(ResourceKey<Attribute> stat) implements CustomPacketPayload {
    public static final Type<SpendStatPointPayload> TYPE = new Type<>(Realmforge.id("spend_stat_point"));
    public static final StreamCodec<ByteBuf, SpendStatPointPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.ATTRIBUTE), SpendStatPointPayload::stat,
            SpendStatPointPayload::new);

    @Override
    public Type<SpendStatPointPayload> type() {
        return TYPE;
    }
}
