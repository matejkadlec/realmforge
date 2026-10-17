package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;

/** {@code realmforge:self}: the caster itself, whatever it aimed at. */
public enum SelfTargeting implements SpellTargeting {
    INSTANCE;

    public static final MapCodec<SelfTargeting> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<SelfTargeting> codec() {
        return MAP_CODEC;
    }

    @Override
    public Either<SpellTarget, CastFailure> resolve(LivingEntity caster, SpellAim aim) {
        return Either.left(SpellTarget.of(caster, caster));
    }
}
