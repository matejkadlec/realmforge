package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * What an effect runs with (architecture 11.2 step 4). Effects read the caster's stats as they run, those a
 * projectile's hit or an area's tick runs later included (architecture 11.3); the proc origin comes with procs
 * (C3a-4).
 *
 * @param level the level the effect happens in
 * @param caster who cast the spell
 * @param direct what delivers it: the caster, or its projectile or area (a damage source's direct entity)
 * @param spell the spell
 * @param spellLevel the level the spell was cast at, 1 to its {@code max_level}
 * @param random the source of the crit rolls
 */
public record EffectContext(ServerLevel level, LivingEntity caster, Entity direct, Holder<Spell> spell, int spellLevel,
        RandomSource random) {
    public EffectContext {
        if (spellLevel < 1 || spellLevel > spell.value().maxLevel()) {
            throw new IllegalArgumentException("Spell level " + spellLevel + " is outside 1 to "
                    + spell.value().maxLevel());
        }
    }

    /** The context of {@code caster}'s own cast in {@code level}: it delivers the effects itself. */
    public static EffectContext of(ServerLevel level, LivingEntity caster, Holder<Spell> spell, int spellLevel) {
        return new EffectContext(level, caster, caster, spell, spellLevel, caster.getRandom());
    }

    /** The same cast, delivered by {@code entity}: a projectile or an area in {@code level}. */
    public EffectContext through(ServerLevel entityLevel, Entity entity) {
        return new EffectContext(entityLevel, caster, entity, spell, spellLevel, caster.getRandom());
    }

    /** One of the caster's stats, 0 when it lacks the attribute (most mobs lack ours). */
    public double casterStat(Holder<Attribute> stat) {
        return DamagePipeline.stat(caster, stat);
    }
}
