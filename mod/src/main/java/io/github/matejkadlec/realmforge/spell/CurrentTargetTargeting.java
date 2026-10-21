package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * {@code realmforge:current_target}, NPC only: the caster's current target, the one a mob attacks
 * ({@code Mob#getTarget}), as {@link EnemyTargeting} checks an enemy: not its ally, attackable, within {@code range}
 * blocks and in line of sight. A cast that aims at an entity (a cast time's check before its effects) checks that one.
 *
 * @param range the range in blocks, at most 128
 */
public record CurrentTargetTargeting(double range) implements SpellTargeting {
    public static final MapCodec<CurrentTargetTargeting> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.doubleRange(0, Targets.MAX_RANGE).fieldOf("range").forGetter(CurrentTargetTargeting::range))
            .apply(instance, CurrentTargetTargeting::new));

    @Override
    public MapCodec<CurrentTargetTargeting> codec() {
        return MAP_CODEC;
    }

    @Override
    public Either<SpellTarget, CastFailure> resolve(LivingEntity caster, SpellAim aim) {
        LivingEntity target = aim.entity().orElseGet(() -> caster instanceof Mob mob ? mob.getTarget() : null);
        if (target == null) {
            return Either.right(CastFailure.NO_TARGET);
        }
        return new EnemyTargeting(range).resolve(caster, SpellAim.at(target));
    }

    @Override
    public double reach() {
        return range;
    }
}
