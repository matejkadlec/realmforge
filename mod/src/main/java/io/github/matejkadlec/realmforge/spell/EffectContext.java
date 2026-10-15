package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * What an effect runs with (architecture 11.2 step 4). Instant casts read the caster's stats as the effects run; the
 * stat snapshot that effects running after the cast need (a projectile's hit) comes with the spell entities (C3a-3),
 * the proc origin with procs (C3a-4).
 *
 * @param level the level the cast happens in
 * @param caster who cast the spell
 * @param spell the spell
 * @param spellLevel the level the spell was cast at, 1 to its {@code max_level}
 * @param random the source of the crit rolls
 */
public record EffectContext(ServerLevel level, LivingEntity caster, Holder<Spell> spell, int spellLevel,
        RandomSource random) {
    public EffectContext {
        if (spellLevel < 1 || spellLevel > spell.value().maxLevel()) {
            throw new IllegalArgumentException("Spell level " + spellLevel + " is outside 1 to "
                    + spell.value().maxLevel());
        }
    }

    /** One of the caster's stats, 0 when it lacks the attribute (most mobs lack ours). */
    public double casterStat(Holder<Attribute> stat) {
        return DamagePipeline.stat(caster, stat);
    }
}
