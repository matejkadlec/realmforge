package io.github.matejkadlec.realmforge.spell.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the player charges at an entity (RLM-543, {@code spell/Charges}). Their client moves them there
 * itself ({@code client/spell/ClientCharges}), as only it knows where they really are.
 *
 * @param targetId the entity the charge is at
 * @param perTick the rush's speed, in blocks per tick
 * @param maxTicks how long the rush may last
 */
public record ChargePayload(int targetId, double perTick, int maxTicks) implements CustomPacketPayload {
    public static final Type<ChargePayload> TYPE = new Type<>(Realmforge.id("charge"));
    public static final StreamCodec<ByteBuf, ChargePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChargePayload::targetId,
            ByteBufCodecs.DOUBLE, ChargePayload::perTick,
            ByteBufCodecs.VAR_INT, ChargePayload::maxTicks,
            ChargePayload::new);

    @Override
    public Type<ChargePayload> type() {
        return TYPE;
    }
}
