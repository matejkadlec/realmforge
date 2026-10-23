package io.github.matejkadlec.realmforge.mob.custom;

import io.github.matejkadlec.realmforge.spell.Spell;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * What a custom mob's YAML says beyond its entity type, size and attributes (architecture 14.2,
 * {@code content/mobs/}): how it idles, its sounds and, for a caster, its spells and the distance it keeps.
 * {@code GeneratedMobs} builds one per mob and hands it to the archetype's constructor.
 *
 * @param idle what it does while it has no target
 * @param ambientSound the sound it makes now and then while it lives; vanilla's none when empty
 * @param hurtSound the sound when it is hurt; vanilla's for hostile mobs when empty
 * @param deathSound the sound when it dies; vanilla's for hostile mobs when empty
 * @param stepSound the sound of each step; the block's own when empty
 * @param spells a caster's spells, most important first; empty for every other archetype
 * @param keepDistance a caster backs away from a target nearer than this many blocks; 0 never backs away
 */
public record CustomMobSpec(Idle idle, Optional<Identifier> ambientSound, Optional<Identifier> hurtSound,
        Optional<Identifier> deathSound, Optional<Identifier> stepSound, List<ResourceKey<Spell>> spells,
        double keepDistance) {
    /** What a custom mob does while it has no target. */
    public enum Idle {
        /** Wanders around, as vanilla's hostile mobs do. */
        STROLL,
        /** Stands at the spot where it first stood on the ground, and walks back to it once it loses its target. */
        GUARD
    }

    public CustomMobSpec {
        spells = List.copyOf(spells);
    }

    /** A sound event id from the generated registration, e.g. {@code minecraft:block.stone.hit}. */
    public static Optional<Identifier> sound(String id) {
        return Optional.of(Identifier.parse(id));
    }
}
