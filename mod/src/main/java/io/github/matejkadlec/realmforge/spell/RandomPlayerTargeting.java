package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code realmforge:random_player}, NPC only: a random player within {@code range} blocks and in line of sight that the
 * caster may attack ({@link Targets#playerTarget}). A cast that aims at an entity (a cast time's check before its
 * effects) checks that one instead of choosing again.
 *
 * @param range the range in blocks, at most 128
 */
public record RandomPlayerTargeting(double range) implements SpellTargeting {
    public static final MapCodec<RandomPlayerTargeting> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.doubleRange(0, Targets.MAX_RANGE).fieldOf("range").forGetter(RandomPlayerTargeting::range))
            .apply(instance, RandomPlayerTargeting::new));

    @Override
    public MapCodec<RandomPlayerTargeting> codec() {
        return MAP_CODEC;
    }

    @Override
    public Either<SpellTarget, CastFailure> resolve(LivingEntity caster, SpellAim aim) {
        if (aim.entity().isPresent()) {
            LivingEntity target = aim.entity().get();
            return Targets.playerTarget(caster, target, range).<Either<SpellTarget, CastFailure>>map(Either::right)
                    .orElseGet(() -> Either.left(SpellTarget.of(caster, target)));
        }
        List<ServerPlayer> players = Targets.playerTargets(caster, range);
        if (players.isEmpty()) {
            return Either.right(CastFailure.NO_TARGET);
        }
        return Either.left(SpellTarget.of(caster, players.get(caster.getRandom().nextInt(players.size()))));
    }
}
