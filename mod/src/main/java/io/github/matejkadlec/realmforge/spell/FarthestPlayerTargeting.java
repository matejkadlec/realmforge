package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code realmforge:farthest_player}, NPC only: the farthest player within {@code range} blocks and in line of sight
 * that the caster may attack ({@link Targets#playerTarget}), measured from the caster's eyes to the player's box. A
 * cast that aims at an entity (a cast time's check before its effects) checks that one instead of choosing again.
 *
 * @param range the range in blocks, at most 128
 */
public record FarthestPlayerTargeting(double range) implements SpellTargeting {
    public static final MapCodec<FarthestPlayerTargeting> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.doubleRange(0, Targets.MAX_RANGE).fieldOf("range").forGetter(FarthestPlayerTargeting::range))
            .apply(instance, FarthestPlayerTargeting::new));

    @Override
    public MapCodec<FarthestPlayerTargeting> codec() {
        return MAP_CODEC;
    }

    @Override
    public Either<SpellTarget, CastFailure> resolve(LivingEntity caster, SpellAim aim) {
        if (aim.entity().isPresent()) {
            LivingEntity target = aim.entity().get();
            return Targets.playerTarget(caster, target, range).<Either<SpellTarget, CastFailure>>map(Either::right)
                    .orElseGet(() -> Either.left(SpellTarget.of(caster, target)));
        }
        Optional<ServerPlayer> farthest = Targets.playerTargets(caster, range).stream()
                .max(Comparator.comparingDouble(player -> Targets.distance(caster, player)));
        return farthest.<Either<SpellTarget, CastFailure>>map(player -> Either.left(SpellTarget.of(caster, player)))
                .orElseGet(() -> Either.right(CastFailure.NO_TARGET));
    }
}
