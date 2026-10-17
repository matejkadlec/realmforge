package io.github.matejkadlec.realmforge.spell;

import net.minecraft.world.entity.LivingEntity;

/**
 * An effect that acts on an entity: the spell's target, the enemy a projectile hits or an entity inside an area. On a
 * target without one (a point, a direction) it does nothing; the pipeline refuses such a spell.
 */
public interface EntityEffect extends SpellEffect {
    @Override
    default void apply(EffectContext context, SpellTarget target) {
        target.entity().ifPresent(entity -> applyTo(context, entity, target));
    }

    /** Runs the effect on {@code entity}, the entity of {@code target}. Logical server only. */
    void applyTo(EffectContext context, LivingEntity entity, SpellTarget target);
}
