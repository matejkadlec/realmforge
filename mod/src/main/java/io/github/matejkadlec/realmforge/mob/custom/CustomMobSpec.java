package io.github.matejkadlec.realmforge.mob.custom;

import io.github.matejkadlec.realmforge.mob.ai.KiteRules;
import io.github.matejkadlec.realmforge.spell.Spell;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * What a custom mob's YAML says beyond its entity type, size and attributes (architecture 14.2,
 * {@code content/mobs/}): how it idles, hits and sounds, how it breaks apart, its variants and, for a caster, its
 * spells and kiting. {@code GeneratedMobs} builds one per mob and hands it to the archetype's constructor.
 *
 * @param idle what it does while it has no target
 * @param sounds the sounds it makes
 * @param deathParticles the block whose break particles it bursts into when it dies, its body gone at once; empty
 *                       for vanilla's death (it falls over and vanishes in smoke)
 * @param melee how a melee mob hits; vanilla's defaults for every other archetype
 * @param spells a caster's spells, most important first; empty for every other archetype
 * @param kiting a caster's step between two casts; empty when it stands where it cast
 * @param variants its kinds, one rolled when it spawns; empty for a mob of one kind
 */
public record CustomMobSpec(Idle idle, Sounds sounds, Optional<Identifier> deathParticles, Melee melee,
        List<ResourceKey<Spell>> spells, Optional<KiteRules> kiting, List<Variant> variants) {
    /** What a custom mob does while it has no target. */
    public enum Idle {
        /** Wanders around, as vanilla's hostile mobs do. */
        STROLL,
        /** Stands at the spot where it first stood on the ground, and walks back to it once it loses its target. */
        GUARD
    }

    /**
     * The sounds of a custom mob, by sound event id.
     *
     * @param ambient now and then while it lives; vanilla's none when empty
     * @param hurt when it is hurt; vanilla's for hostile mobs when empty
     * @param death when it dies; vanilla's for hostile mobs when empty
     * @param step each step; the block's own when empty, unless {@code silentSteps}
     * @param silentSteps whether it walks without a sound
     */
    public record Sounds(Optional<Identifier> ambient, Optional<Identifier> hurt, Optional<Identifier> death,
            Optional<Identifier> step, boolean silentSteps) {
    }

    /**
     * How a melee mob hits.
     *
     * @param attackIntervalTicks the ticks between two of its hits; vanilla's monsters hit every 20
     * @param knockUpBlocks how high each hit throws its target, in blocks; 0 for vanilla's knockback alone
     */
    public record Melee(int attackIntervalTicks, double knockUpBlocks) {
        /** Vanilla's melee monsters: a hit a second, no throw. */
        public static final Melee VANILLA = new Melee(20, 0);
    }

    /**
     * One kind of a mob, rolled when it spawns and kept for its life: its texture
     * ({@code textures/entity/<mob>_<id>.png}) and its loot table ({@code realmforge:entities/<mob>/<id>}).
     *
     * @param id the variant's id, e.g. {@code sapphire}
     * @param weight how likely it is against the other variants
     */
    public record Variant(String id, int weight) {
    }

    public CustomMobSpec {
        spells = List.copyOf(spells);
        variants = List.copyOf(variants);
    }

    /** An id from the generated registration, e.g. {@code realmforge:entity.hewn_sentinel.hurt}. */
    public static Optional<Identifier> id(String id) {
        return Optional.of(Identifier.parse(id));
    }
}
