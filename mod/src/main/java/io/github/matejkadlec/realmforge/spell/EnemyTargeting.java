package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code realmforge:enemy}: a living entity that is not the caster's ally ({@link Targets#allied}), that can be
 * attacked (not invulnerable, not a spectator), within {@code range} blocks and in line of sight. A cast needs one.
 *
 * @param range the range in blocks, at most 128 (vanilla's line of sight check gives up beyond)
 */
public record EnemyTargeting(double range) implements SpellTargeting {
    public static final MapCodec<EnemyTargeting> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.doubleRange(0, Targets.MAX_RANGE).fieldOf("range").forGetter(EnemyTargeting::range))
            .apply(instance, EnemyTargeting::new));

    @Override
    public MapCodec<EnemyTargeting> codec() {
        return MAP_CODEC;
    }

    @Override
    public Either<SpellTarget, CastFailure> resolve(LivingEntity caster, SpellAim aim) {
        LivingEntity requested = aim.entity().orElse(null);
        if (requested == null) {
            return Either.right(CastFailure.NO_TARGET);
        }
        if (requested == caster || Targets.allied(caster, requested) || !requested.canBeSeenAsEnemy()) {
            return Either.right(CastFailure.INVALID_TARGET);
        }
        return Targets.reach(caster, requested, range).<Either<SpellTarget, CastFailure>>map(Either::right)
                .orElseGet(() -> Either.left(SpellTarget.of(caster, requested)));
    }
}
