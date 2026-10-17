package io.github.matejkadlec.realmforge.spell;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What a cast asks to aim at (architecture 11.2 step 1): an entity, a point, or nothing, which leaves the aim to the
 * spell's targeting (the caster itself, or where it looks). The client's {@code CastSpellPayload} (C3b) and a mob's AI
 * (C3a-4) both name one; the targeting decides what the cast then acts on ({@link SpellTarget}).
 *
 * @param entity the entity aimed at
 * @param point the point aimed at, for a ground point or a direction
 */
public record SpellAim(Optional<LivingEntity> entity, Optional<Vec3> point) {
    /** No aim: a self or ally spell takes the caster, a ground point or a direction follows the caster's look. */
    public static final SpellAim NONE = new SpellAim(Optional.empty(), Optional.empty());

    public SpellAim {
        if (entity.isPresent() && point.isPresent()) {
            throw new IllegalArgumentException("A cast aims at an entity or a point, not both");
        }
    }

    /** Aims at {@code entity}, or at nothing when it is null. */
    public static SpellAim at(@Nullable LivingEntity entity) {
        return entity == null ? NONE : new SpellAim(Optional.of(entity), Optional.empty());
    }

    /** Aims at {@code point}. */
    public static SpellAim at(Vec3 point) {
        return new SpellAim(Optional.empty(), Optional.of(point));
    }
}
