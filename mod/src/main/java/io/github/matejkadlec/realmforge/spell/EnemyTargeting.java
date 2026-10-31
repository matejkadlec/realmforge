package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * {@code realmforge:enemy}: a living entity that is not the caster's ally ({@link Targets#allied}), that can be
 * attacked (not invulnerable, not a spectator), within {@code range} blocks and in line of sight. A melee spell's
 * ({@code melee}) is instead within the caster's default melee hit shape: Better Combat's swing at its melee reach
 * ({@link MeleeEffect#reach}), so it reaches as far as a melee attack and grows with the reach (Matěj's playtest of
 * 2026-10-31). A cast needs one.
 *
 * @param range the range in blocks, at most 128 (vanilla's line of sight check gives up beyond); empty for melee
 * @param melee whether the target must be in the caster's melee swing instead of within a range
 */
public record EnemyTargeting(Optional<Double> range, boolean melee) implements SpellTargeting {
    public static final MapCodec<EnemyTargeting> MAP_CODEC = RecordCodecBuilder.<EnemyTargeting>mapCodec(
                    instance -> instance.group(Codec.doubleRange(0, Targets.MAX_RANGE).optionalFieldOf("range")
                                    .forGetter(EnemyTargeting::range),
                            Codec.BOOL.optionalFieldOf("melee", false).forGetter(EnemyTargeting::melee))
                    .apply(instance, EnemyTargeting::new))
            .validate(targeting -> targeting.range().isPresent() != targeting.melee() ? DataResult.success(targeting)
                    : DataResult.error(() -> "An enemy targeting has either a range or melee: true: " + targeting));

    /** An enemy within {@code range} blocks. */
    public EnemyTargeting(double range) {
        this(Optional.of(range), false);
    }

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
        Optional<CastFailure> failure = melee ? inSwing(caster, requested) : Targets.reach(caster, requested, reach());
        return failure.<Either<SpellTarget, CastFailure>>map(Either::right)
                .orElseGet(() -> Either.left(SpellTarget.of(caster, requested)));
    }

    /** Why {@code target} is not in {@code caster}'s melee swing, if it is not: out of it, or behind blocks. */
    private static Optional<CastFailure> inSwing(LivingEntity caster, LivingEntity target) {
        MeleeShape swing = new MeleeShape(MeleeShape.Kind.SWING, MeleeEffect.reach(caster));
        if (target.level() != caster.level()
                || !swing.strikes(MeleeEffect.frame(caster), target.getBoundingBox().inflate(target.getPickRadius()))) {
            return Optional.of(CastFailure.OUT_OF_RANGE);
        }
        return caster.hasLineOfSight(target) ? Optional.empty() : Optional.of(CastFailure.NO_LINE_OF_SIGHT);
    }

    /** The range in blocks; a melee spell's is vanilla's default melee reach, 3, as no caster is at hand. */
    @Override
    public double reach() {
        return range.orElse((double) Player.DEFAULT_ENTITY_INTERACTION_RANGE);
    }

    /** How far {@code caster} reaches with the spell: its range, or for a melee spell its melee reach. */
    @Override
    public double reach(LivingEntity caster) {
        return melee ? MeleeEffect.reach(caster) : reach();
    }
}
