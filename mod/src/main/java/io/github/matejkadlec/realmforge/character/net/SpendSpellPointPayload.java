package io.github.matejkadlec.realmforge.character.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;

/**
 * Client to server: the player clicked a spell in the spellbook's Spell Tree tab, to spend one spell point on its next
 * rank (architecture 10.1, 17.3; RLM-333). An intent: {@code SpellTrees} checks it again on the server.
 */
public record SpendSpellPointPayload(ResourceKey<Spell> spell) implements CustomPacketPayload {
    public static final Type<SpendSpellPointPayload> TYPE = new Type<>(Realmforge.id("spend_spell_point"));
    public static final StreamCodec<ByteBuf, SpendSpellPointPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(SpellRegistries.SPELL), SpendSpellPointPayload::spell,
            SpendSpellPointPayload::new);

    @Override
    public Type<SpendSpellPointPayload> type() {
        return TYPE;
    }
}
