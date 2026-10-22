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
 * @param bar    spell bar slot to the spell in it; empty slots are absent. Slots 0 to 7 are the bar's (C3b, RLM-25:
 *               R, T, F, G, H and C, then two the player binds; Matěj, 2026-10-15 and 2026-10-16), slot
 *               {@value #RACIAL_SLOT} the racial ability's beside it (X), which the race fills, not the player
 */
public record Spellbook(Map<ResourceKey<Spell>, Integer> spells, Map<Integer, ResourceKey<Spell>> bar) {
    public static final Spellbook EMPTY = new Spellbook(Map.of(), Map.of());
    /** The spell bar's slots for class spells: 0 to 7. */
    public static final int BAR_SLOTS = 8;
    /** The racial ability's slot beside the bar. */
    public static final int RACIAL_SLOT = BAR_SLOTS;
    /** Every slot, the bar's and the racial one. */
    public static final int SLOT_COUNT = BAR_SLOTS + 1;

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
            if (slot < 0 || slot >= SLOT_COUNT) {
                throw new IllegalArgumentException("Spell bar slots are 0 to " + (SLOT_COUNT - 1) + ": " + slot);
            }
        });
    }

    /** The level of a known spell, empty when the player does not know it. */
    public Optional<Integer> level(ResourceKey<Spell> spell) {
        return Optional.ofNullable(spells.get(spell));
    }

    /** The spell in {@code slot}, if any. */
    public Optional<ResourceKey<Spell>> slot(int slot) {
        return Optional.ofNullable(bar.get(slot));
    }

    /** The first of the bar's slots (0 to 7) that holds {@code spell}, or -1. */
    public int barSlotOf(ResourceKey<Spell> spell) {
        for (int slot = 0; slot < BAR_SLOTS; slot++) {
            if (spell.equals(bar.get(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /** This book with {@code slot} holding {@code spell}, or emptied. */
    public Spellbook withSlot(int slot, Optional<ResourceKey<Spell>> spell) {
        Map<Integer, ResourceKey<Spell>> changed = new HashMap<>(bar);
        spell.ifPresentOrElse(value -> changed.put(slot, value), () -> changed.remove(slot));
        return new Spellbook(spells, changed);
    }

    /**
     * This book with {@code spell} put into the bar's {@code slot} (0 to 7). A spell already elsewhere on the bar moves,
     * and the spell it displaces takes its old slot, so dragging one spell onto another swaps them.
     */
    public Spellbook placed(int slot, ResourceKey<Spell> spell) {
        if (slot < 0 || slot >= BAR_SLOTS) {
            throw new IllegalArgumentException("The bar's slots are 0 to " + (BAR_SLOTS - 1) + ": " + slot);
        }
        int from = barSlotOf(spell);
        Optional<ResourceKey<Spell>> displaced = slot(slot);
        Spellbook book = withSlot(slot, Optional.of(spell));
        return from >= 0 && from != slot ? book.withSlot(from, displaced) : book;
    }

    /** This book with {@code spell} in the bar's first empty slot, unless it is on the bar or the bar is full. */
    public Spellbook withSpellOnBar(ResourceKey<Spell> spell) {
        if (barSlotOf(spell) >= 0) {
            return this;
        }
        for (int slot = 0; slot < BAR_SLOTS; slot++) {
            if (!bar.containsKey(slot)) {
                return withSlot(slot, Optional.of(spell));
            }
        }
        return this;
    }

    /** This book with {@code spell} known at {@code level} (1 or more), replacing the level it had. */
    public Spellbook withSpell(ResourceKey<Spell> spell, int level) {
        Map<ResourceKey<Spell>, Integer> known = new HashMap<>(spells);
        known.put(spell, level);
        return new Spellbook(known, bar);
    }
}
