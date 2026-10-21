package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code realmforge:ground_point}: a point within {@code range} blocks of the caster's eyes and in its line of sight
 * (C3a-3). It is the point the cast aimed at, or the feet of the entity it aimed at, or else the first block the
 * caster looks at within range; looking at the sky finds none. The target has no entity, so its effects are the ones
 * that need only a place: an area, a projectile flying there, a teleport or a dash.
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
            BlockHitResult looked = Targets.clip(caster, caster.getLookAngle(), range);
            return looked.getType() == HitResult.Type.MISS
                    ? Either.right(CastFailure.NO_TARGET)
                    : Either.left(SpellTarget.point(caster, looked.getLocation()));
        }
        if (caster.getEyePosition().distanceTo(point) > range) {
            return Either.right(CastFailure.OUT_OF_RANGE);
        }
        if (!Targets.canSee(caster, point)) {
            return Either.right(CastFailure.NO_LINE_OF_SIGHT);
        }
        return Either.left(SpellTarget.point(caster, point));
    }

    @Override
    public double reach() {
        return range;
    }
}
