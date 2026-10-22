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
 * Client to server: the spellbook puts a known spell into a spell bar slot, or empties one (C3b, RLM-25). The server
 * refuses another slot or an unknown spell ({@code Spells#placeOnBar}); the spellbook attachment's sync shows the result.
 *
 * @param slot the bar's slot, 0 to 7
 * @param spell the spell, or empty to empty the slot
 */
public record SetSpellBarSlotPayload(int slot, Optional<ResourceKey<Spell>> spell) implements CustomPacketPayload {
    public static final Type<SetSpellBarSlotPayload> TYPE = new Type<>(Realmforge.id("set_spell_bar_slot"));
    public static final StreamCodec<ByteBuf, SetSpellBarSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetSpellBarSlotPayload::slot,
            ByteBufCodecs.optional(ResourceKey.streamCodec(SpellRegistries.SPELL)), SetSpellBarSlotPayload::spell,
            SetSpellBarSlotPayload::new);

    @Override
    public Type<SetSpellBarSlotPayload> type() {
        return TYPE;
    }
}
