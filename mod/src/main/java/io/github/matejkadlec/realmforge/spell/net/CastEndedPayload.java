package io.github.matejkadlec.realmforge.spell.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;

/**
 * Server to client: an entity's cast-time or channelled spell ended (architecture 11.2, 17.3), sent to the caster and
 * every player tracking it. The client drops the cast {@link CastStartedPayload} began, and C3b's cast bar shows how it
 * ended.
 *
 * @param casterId the casting entity
 * @param spell the spell that was cast
 * @param end how it ended: completed, interrupted or failed
 */
public record CastEndedPayload(int casterId, ResourceKey<Spell> spell, CastEnd end) implements CustomPacketPayload {
    public static final Type<CastEndedPayload> TYPE = new Type<>(Realmforge.id("cast_ended"));
    private static final StreamCodec<ByteBuf, CastEnd> END_CODEC = ByteBufCodecs.idMapper(
            id -> CastEnd.values()[id], CastEnd::ordinal);
    public static final StreamCodec<ByteBuf, CastEndedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CastEndedPayload::casterId,
            ResourceKey.streamCodec(SpellRegistries.SPELL), CastEndedPayload::spell,
            END_CODEC, CastEndedPayload::end,
            CastEndedPayload::new);

    @Override
    public Type<CastEndedPayload> type() {
        return TYPE;
    }
}
