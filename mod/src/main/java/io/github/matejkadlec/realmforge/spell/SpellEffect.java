package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;

/**
 * One thing a spell does, an entry of the type registry {@code realmforge:spell_effect_type} (architecture 5.3, 11):
 * {@code damage} and {@code heal} so far ({@link SpellEffectTypes}); the rest of the first set comes with C3a-3. A new
 * kind of behaviour is a new effect type; a new spell is data.
 */
public interface SpellEffect {
    /** An effect by its {@code type}, e.g. {@code {"type": "realmforge:damage", "amount": 12}}. */
    Codec<SpellEffect> CODEC = SpellRegistries.EFFECT_TYPES.byNameCodec().dispatch(SpellEffect::codec, c -> c);

    MapCodec<? extends SpellEffect> codec();

    /** Runs the effect on {@code target}, a target the spell's targeting picked. Logical server only. */
    void apply(EffectContext context, LivingEntity target);
}
