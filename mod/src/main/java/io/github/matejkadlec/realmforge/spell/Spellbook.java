package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.core.Codecs;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/**
 * The value of a player's {@code realmforge:spellbook} attachment (architecture 7 and 11): the spells they know
 * with their levels, and the spell bar layout. Spells are named by their keys in {@code realmforge:spell}, saved as
 * their ids; a known spell whose entry is gone (content removed) simply cannot be cast.
 *
 * @param spells known spell to its level (1 or more)
 * @param bar    spell bar slot (0 or more) to the spell in it; empty slots are absent. The number of slots comes
 *               with the key scheme (decision D4)
 */
public record Spellbook(Map<ResourceKey<Spell>, Integer> spells, Map<Integer, ResourceKey<Spell>> bar) {
    public static final Spellbook EMPTY = new Spellbook(Map.of(), Map.of());

    public static final Codec<Spellbook> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceKey.codec(SpellRegistries.SPELL), ExtraCodecs.POSITIVE_INT)
                    .optionalFieldOf("spells", Map.of()).forGetter(Spellbook::spells),
            Codec.unboundedMap(Codecs.INDEX_KEY, ResourceKey.codec(SpellRegistries.SPELL))
                    .optionalFieldOf("bar", Map.of()).forGetter(Spellbook::bar)
    ).apply(instance, Spellbook::new));

    public static final StreamCodec<ByteBuf, Spellbook> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, ResourceKey<Spell>, Integer, Map<ResourceKey<Spell>, Integer>>map(HashMap::new,
                    ResourceKey.streamCodec(SpellRegistries.SPELL), ByteBufCodecs.VAR_INT), Spellbook::spells,
            ByteBufCodecs.<ByteBuf, Integer, ResourceKey<Spell>, Map<Integer, ResourceKey<Spell>>>map(HashMap::new,
                    ByteBufCodecs.VAR_INT, ResourceKey.streamCodec(SpellRegistries.SPELL)), Spellbook::bar,
            Spellbook::new);

    public Spellbook {
        spells = Map.copyOf(spells);
        bar = Map.copyOf(bar);
        spells.forEach((spell, level) -> {
            if (level < 1) {
                throw new IllegalArgumentException("A known spell has level 1 or more: " + spell + " = " + level);
            }
        });
        bar.keySet().forEach(slot -> {
            if (slot < 0) {
                throw new IllegalArgumentException("Spell bar slots start at 0: " + slot);
            }
        });
    }

    /** The level of a known spell, empty when the player does not know it. */
    public Optional<Integer> level(ResourceKey<Spell> spell) {
        return Optional.ofNullable(spells.get(spell));
    }

    /** This book with {@code spell} known at {@code level} (1 or more), replacing the level it had. */
    public Spellbook withSpell(ResourceKey<Spell> spell, int level) {
        Map<ResourceKey<Spell>, Integer> known = new HashMap<>(spells);
        known.put(spell, level);
        return new Spellbook(known, bar);
    }
}
