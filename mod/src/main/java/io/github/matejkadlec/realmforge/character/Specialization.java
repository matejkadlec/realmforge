package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;

/**
 * A specialization of a class and its spell tree (C5, RLM-333, architecture 10.1), from the class's
 * {@code specializations} in {@code content/classes/}. One without spells comes later: the spell trees window names it,
 * and nobody can pick it yet.
 *
 * @param id          its id within the class, which a character saves
 * @param name        translation key of its name
 * @param description translation key of how it plays
 * @param firstSpell  the tree's first spell, free at rank 1 when the player picks the specialization (decision D4)
 * @param spells      the tree's spells
 */
public record Specialization(String id, String name, String description, Optional<Holder<Spell>> firstSpell,
        List<TreeSpell> spells) {
    public static final Codec<Specialization> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("id").forGetter(Specialization::id),
                    Codec.STRING.fieldOf("name").forGetter(Specialization::name),
                    Codec.STRING.fieldOf("description").forGetter(Specialization::description),
                    RegistryFixedCodec.create(SpellRegistries.SPELL).optionalFieldOf("first_spell")
                            .forGetter(Specialization::firstSpell),
                    TreeSpell.CODEC.listOf().optionalFieldOf("spells", List.of()).forGetter(Specialization::spells))
            .apply(instance, Specialization::new));

    public Specialization {
        spells = List.copyOf(spells);
    }

    /** Whether a player can pick it: its tree has spells. */
    public boolean playable() {
        return !spells.isEmpty();
    }

    /** The tree's entry for {@code spell}, if the tree holds it. */
    public Optional<TreeSpell> spell(ResourceKey<Spell> spell) {
        return spells.stream().filter(taught -> taught.spell().is(spell)).findFirst();
    }

    /** The first spell's key, if the tree has one. */
    public Optional<ResourceKey<Spell>> firstSpellKey() {
        return firstSpell.flatMap(Holder::unwrapKey);
    }
}
