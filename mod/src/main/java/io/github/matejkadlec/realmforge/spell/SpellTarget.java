package io.github.matejkadlec.realmforge.spell;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What a cast acts on, as its targeting resolved it (architecture 11.2): an entity (the caster, an enemy or an ally),
 * a point on the ground, or a direction from the caster. Effects that act on an entity ({@link EntityEffect}) need
 * one; a projectile flies along the direction, an area lies at the position, a teleport or a dash goes to it.
 *
 * @param kind which of the three it is
 * @param entity the entity, for {@link Kind#ENTITY}
 * @param position where the spell lands: the entity's feet, the ground point, or the end of the direction's ray (its
 *     range, or the first block in the way), at the height of the caster's eyes
 * @param direction the unit vector from the caster's eyes toward the target, or the direction itself
 * @param followsLook whether it is a direction the caster's look gave, aimed at nothing else: a cast time's effects
 *     and a channel's ticks then go where the caster looks when they run (RLM-344, where the crosshair points)
 */
public record SpellTarget(Kind kind, Optional<LivingEntity> entity, Vec3 position, Vec3 direction,
        boolean followsLook) {
    /** What a target is. */
    public enum Kind {
        ENTITY,
        POINT,
        DIRECTION
    }

    public SpellTarget {
        if (entity.isPresent() != (kind == Kind.ENTITY)) {
            throw new IllegalArgumentException("Only an entity target has an entity: " + kind);
        }
        if (followsLook && kind != Kind.DIRECTION) {
            throw new IllegalArgumentException("Only a direction follows the caster's look: " + kind);
        }
    }

    /** {@code entity}, as {@code caster} sees it: from the caster's eyes to the middle of its box. */
    public static SpellTarget of(LivingEntity caster, LivingEntity entity) {
        return hit(entity, toward(caster, entity.getBoundingBox().getCenter()));
    }

    /**
     * {@code entity} reached along {@code direction}: what a projectile hits as it flies, or what an area holds,
     * seen from its middle. A knockback pushes it along the direction.
     */
    public static SpellTarget hit(LivingEntity entity, Vec3 direction) {
        return new SpellTarget(Kind.ENTITY, Optional.of(entity), entity.position(), direction, false);
    }

    /** The ground point {@code point}, as {@code caster} sees it. */
    public static SpellTarget point(LivingEntity caster, Vec3 point) {
        return new SpellTarget(Kind.POINT, Optional.empty(), point, toward(caster, point), false);
    }

    /** The direction {@code direction} (a unit vector), whose ray ends at {@code end}. */
    public static SpellTarget direction(Vec3 end, Vec3 direction) {
        return new SpellTarget(Kind.DIRECTION, Optional.empty(), end, direction, false);
    }

    /**
     * The direction {@code direction} (a unit vector) that the caster's look gave, whose ray ends at {@code end}: it
     * follows the look until the spell's effects run ({@link #now}).
     */
    public static SpellTarget look(Vec3 end, Vec3 direction) {
        return new SpellTarget(Kind.DIRECTION, Optional.empty(), end, direction, true);
    }

    /**
     * This target as {@code caster} sees it now, for the spell whose {@code targeting} resolved it: an entity target is
     * seen afresh (where it and the caster stand now), so a cast time's effects and a channel's ticks go where the
     * target is when they run, however far either moved during the cast. A direction that follows the caster's look is
     * resolved again from where the caster looks now (RLM-344: a bolt flies where the crosshair points when it
     * leaves). A point, or a direction aimed at a point or an entity, stays as it was resolved when the cast began.
     */
    public SpellTarget now(LivingEntity caster, SpellTargeting targeting) {
        if (followsLook) {
            return targeting.resolve(caster, SpellAim.NONE).left().orElse(this);
        }
        return entity.map(target -> of(caster, target)).orElse(this);
    }

    /** The entity, or null for a point or a direction. */
    @Nullable
    public LivingEntity entityOrNull() {
        return entity.orElse(null);
    }

    /** The unit vector from {@code caster}'s eyes to {@code point}; its look when the point is at its eyes. */
    static Vec3 toward(LivingEntity caster, Vec3 point) {
        Vec3 offset = point.subtract(caster.getEyePosition());
        return offset.lengthSqr() < 1e-8 ? caster.getLookAngle() : offset.normalize();
    }
}
