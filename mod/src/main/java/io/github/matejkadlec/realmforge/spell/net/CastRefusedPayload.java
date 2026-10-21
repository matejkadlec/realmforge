package io.github.matejkadlec.realmforge.spell.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;

/**
 * Server to client: the server refused a cast from the spell bar, and why (research SP-3; C3b, RLM-25). The client
 * shows the reason's text over the spell bar ({@code spell/CastTexts}).
 *
 * @param spell the spell that was refused
 * @param failure why
 */
public record CastRefusedPayload(ResourceKey<Spell> spell, CastFailure failure) implements CustomPacketPayload {
    public static final Type<CastRefusedPayload> TYPE = new Type<>(Realmforge.id("cast_refused"));
    private static final StreamCodec<ByteBuf, CastFailure> FAILURE_CODEC = ByteBufCodecs.idMapper(
            id -> CastFailure.values()[id], CastFailure::ordinal);
    public static final StreamCodec<ByteBuf, CastRefusedPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(SpellRegistries.SPELL), CastRefusedPayload::spell,
            FAILURE_CODEC, CastRefusedPayload::failure,
            CastRefusedPayload::new);

    @Override
    public Type<CastRefusedPayload> type() {
        return TYPE;
    }
}
