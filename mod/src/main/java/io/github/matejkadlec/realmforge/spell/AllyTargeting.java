package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code realmforge:ally}: the caster or one of its allies ({@link Targets#allied}), alive, within {@code range}
 * blocks and in line of sight. Without a target, the caster itself, as a WoW heal with no friendly target.
 *
 * @param range the range in blocks, at most 128 (vanilla's line of sight check gives up beyond)
 */
public record AllyTargeting(double range) implements SpellTargeting {
    public static final MapCodec<AllyTargeting> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.doubleRange(0, Targets.MAX_RANGE).fieldOf("range").forGetter(AllyTargeting::range))
            .apply(instance, AllyTargeting::new));

    @Override
    public MapCodec<AllyTargeting> codec() {
        return MAP_CODEC;
    }

    @Override
    public Either<LivingEntity, CastFailure> resolve(LivingEntity caster, @Nullable LivingEntity requested) {
        if (requested == null || requested == caster) {
            return Either.left(caster);
        }
        if (!Targets.allied(caster, requested) || !requested.canBeSeenByAnyone()) {
            return Either.right(CastFailure.INVALID_TARGET);
        }
        return Targets.reach(caster, requested, range).<Either<LivingEntity, CastFailure>>map(Either::right)
                .orElseGet(() -> Either.left(requested));
    }
}
