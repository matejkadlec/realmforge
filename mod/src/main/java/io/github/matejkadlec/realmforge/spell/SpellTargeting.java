package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who a spell can target, an entry of the type registry {@code realmforge:targeting_type} (architecture 5.3, 11.2):
 * {@code self}, {@code enemy} and {@code ally} so far ({@link TargetingTypes}); the ground point, the direction and the
 * NPC types come with C3a-3 and C3a-4.
 */
public interface SpellTargeting {
    /** A targeting by its {@code type}, e.g. {@code {"type": "realmforge:enemy", "range": 24}}. */
    Codec<SpellTargeting> CODEC = SpellRegistries.TARGETING_TYPES.byNameCodec().dispatch(SpellTargeting::codec, c -> c);

    MapCodec<? extends SpellTargeting> codec();

    /**
     * The entity the cast acts on, given the caster and the target it asked for (none, when nothing was aimed at), or
     * why the cast cannot go ahead.
     */
    Either<LivingEntity, CastFailure> resolve(LivingEntity caster, @Nullable LivingEntity requested);
}
