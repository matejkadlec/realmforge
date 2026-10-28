package io.github.matejkadlec.realmforge.spell.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;

/**
 * Client to server: the player pressed or held a spell bar key (architecture 11.2 step 1, 17.3; C3b, RLM-25). The
 * server checks everything ({@code spell/SpellBar}) and answers a refusal with {@link CastRefusedPayload}.
 *
 * @param spell the spell in the slot
 * @param target the entity under the crosshair within the spell's range, if any; without one the server's aim
 *               assistance picks a targeted spell's target near the crosshair, and the targeting decides the rest (the
 *               caster for an ally spell, where the player looks for a ground point)
 * @param replaceCast whether the spell may cancel another spell's cast in progress (a fresh press with spell queueing
 *                    off); a held key never does
 */
public record CastSpellPayload(ResourceKey<Spell> spell, Optional<Integer> target, boolean replaceCast)
        implements CustomPacketPayload {
    public static final Type<CastSpellPayload> TYPE = new Type<>(Realmforge.id("cast_spell"));
    public static final StreamCodec<ByteBuf, CastSpellPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(SpellRegistries.SPELL), CastSpellPayload::spell,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), CastSpellPayload::target,
            ByteBufCodecs.BOOL, CastSpellPayload::replaceCast,
            CastSpellPayload::new);

    @Override
    public Type<CastSpellPayload> type() {
        return TYPE;
    }
}
