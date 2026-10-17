package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who or where a spell can target, an entry of the type registry {@code realmforge:targeting_type} (architecture 5.3,
 * 11.2): {@code self}, {@code enemy}, {@code ally}, {@code ground_point} and {@code direction}
 * ({@link TargetingTypes}); the NPC types come with C3a-4.
 */
public interface SpellTargeting {
    /** A targeting by its {@code type}, e.g. {@code {"type": "realmforge:enemy", "range": 24}}. */
    Codec<SpellTargeting> CODEC = SpellRegistries.TARGETING_TYPES.byNameCodec().dispatch(SpellTargeting::codec, c -> c);

    MapCodec<? extends SpellTargeting> codec();

    /** What the cast acts on, given the caster and what the cast aimed at, or why the cast cannot go ahead. */
    Either<SpellTarget, CastFailure> resolve(LivingEntity caster, SpellAim aim);
}
