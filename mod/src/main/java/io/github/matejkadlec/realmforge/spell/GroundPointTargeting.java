package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * {@code realmforge:ground_point}: where a ground-targeted spell lands (C3a-3; the crosshair and clamp cast since
 * RLM-346, {@link GroundPoints}). It needs no target and is never refused for range:
 * <ul>
 *   <li>aimed at nothing (the spell bar without an entity under the crosshair): on the block the caster looks at
 *   within {@code range} blocks of its eyes, on the ground below it; with none in range (farther, or the sky), at the
 *   farthest point in range along the look, on the ground below it;</li>
 *   <li>aimed at an entity within range: its feet, if the caster sees the entity (its feet may be behind a low
 *   wall);</li>
 *   <li>aimed at a point within range: the point itself, which must be in sight;</li>
 *   <li>aimed at one beyond range: clamped along the line toward it, as the look is.</li>
 * </ul>
 * The landing may lie behind cover: the spell comes down from above. The target has no entity, so its effects are the
 * ones that need only a place: an area, a projectile flying there, a teleport or a dash.
 *
 * @param range the range in blocks, at most 128 (vanilla's line of sight check gives up beyond)
 */
public record GroundPointTargeting(double range) implements SpellTargeting {
    public static final MapCodec<GroundPointTargeting> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.doubleRange(0, Targets.MAX_RANGE).fieldOf("range").forGetter(GroundPointTargeting::range))
            .apply(instance, GroundPointTargeting::new));

    @Override
    public MapCodec<GroundPointTargeting> codec() {
        return MAP_CODEC;
    }

    @Override
    public Either<SpellTarget, CastFailure> resolve(LivingEntity caster, SpellAim aim) {
        if (aim.entity().isPresent() && aim.entity().get().level() != caster.level()) {
            return Either.right(CastFailure.OUT_OF_RANGE);
        }
        Vec3 point = aim.point().orElseGet(() -> aim.entity().map(LivingEntity::position).orElse(null));
        if (point == null) {
            return Either.left(SpellTarget.point(caster, GroundPoints.along(caster, caster.getLookAngle(), range)));
        }
        if (caster.getEyePosition().distanceTo(point) > range) {
            Vec3 toward = SpellTarget.toward(caster, point);
            return Either.left(SpellTarget.point(caster, GroundPoints.along(caster, toward, range)));
        }
        boolean seen = aim.entity().map(caster::hasLineOfSight).orElseGet(() -> Targets.canSee(caster, point));
        return seen ? Either.left(SpellTarget.point(caster, point)) : Either.right(CastFailure.NO_LINE_OF_SIGHT);
    }

    @Override
    public double reach() {
        return range;
    }
}
