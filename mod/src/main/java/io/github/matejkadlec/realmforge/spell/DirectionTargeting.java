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
 * {@code realmforge:direction}: a direction from the caster's eyes (C3a-3), toward the middle of the entity or the
 * point the cast aimed at, or else where the caster looks. Its point is {@code range} blocks along it, or the first
 * block in the way: where a teleport goes. It never fails: a projectile flies along it whatever is there.
 *
 * @param range how far its point lies, in blocks, at most 128
 */
public record DirectionTargeting(double range) implements SpellTargeting {
    public static final MapCodec<DirectionTargeting> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.doubleRange(0, Targets.MAX_RANGE).fieldOf("range").forGetter(DirectionTargeting::range))
            .apply(instance, DirectionTargeting::new));

    @Override
    public MapCodec<DirectionTargeting> codec() {
        return MAP_CODEC;
    }

    @Override
    public Either<SpellTarget, CastFailure> resolve(LivingEntity caster, SpellAim aim) {
        if (aim.entity().isPresent() && aim.entity().get().level() != caster.level()) {
            return Either.right(CastFailure.OUT_OF_RANGE);
        }
        Vec3 direction = aim.point().or(() -> aim.entity().map(entity -> entity.getBoundingBox().getCenter()))
                .map(point -> SpellTarget.toward(caster, point))
                .orElseGet(caster::getLookAngle);
        BlockHitResult hit = Targets.clip(caster, direction, range);
        Vec3 end = hit.getType() == HitResult.Type.MISS
                ? caster.getEyePosition().add(direction.scale(range))
                : hit.getLocation();
        return Either.left(SpellTarget.direction(end, direction));
    }
}
