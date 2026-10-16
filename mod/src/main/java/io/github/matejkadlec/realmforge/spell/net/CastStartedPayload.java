package io.github.matejkadlec.realmforge.spell.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: an entity began a cast-time or channelled spell (architecture 11.2 step 3, 17.3), sent to the
 * caster and every player tracking it, and to a player who starts tracking the caster mid-cast, with the original start
 * time. The client keeps it until the matching {@link CastEndedPayload} ({@code client/spell/ClientCasts}) for the
 * cast bar (C3b) and the cast animation (C6), drawn from the start and the duration.
 *
 * @param casterId the casting entity
 * @param cast the spell, its start game time, its duration after Cast Time Reduction, and whether it is a channel
 */
public record CastStartedPayload(int casterId, CombatState.Cast cast) implements CustomPacketPayload {
    public static final Type<CastStartedPayload> TYPE = new Type<>(Realmforge.id("cast_started"));
    public static final StreamCodec<ByteBuf, CastStartedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CastStartedPayload::casterId,
            CombatState.Cast.STREAM_CODEC, CastStartedPayload::cast,
            CastStartedPayload::new);

    @Override
    public Type<CastStartedPayload> type() {
        return TYPE;
    }
}
